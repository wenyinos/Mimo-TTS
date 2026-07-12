package com.mimotts.app.ui.clone

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mimotts.app.BuildConfig
import com.mimotts.app.R
import com.mimotts.app.api.*
import com.mimotts.app.databinding.FragmentCloneBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CloneFragment : Fragment() {
    private var _binding: FragmentCloneBinding? = null
    private val binding get() = _binding!!
    private var mediaPlayer: MediaPlayer? = null
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var refAudioFile: File? = null
    private var isRecording = false

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) toggleRecording() else binding.tvRefStatus.text = "录音权限被拒绝"
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { copyUriToFile(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCloneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val langs = resources.getStringArray(R.array.languages)
        val formats = resources.getStringArray(R.array.formats)

        binding.spinnerLang.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, langs))
        binding.spinnerLang.setText(langs[0], false)
        binding.spinnerFormat.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, formats))
        binding.spinnerFormat.setText(formats[0], false)

        binding.rbMiMo.setOnClickListener { setBackend(false) }
        binding.rbConfucius.setOnClickListener { setBackend(true) }

        binding.btnRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else toggleRecording()
        }
        binding.btnPickAudio.setOnClickListener { pickAudio.launch("audio/*") }
        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnPlay.setOnClickListener { playAudio() }
        binding.btnStopPlay.setOnClickListener { stopAudio() }
    }

    private fun setBackend(isConfucius: Boolean) {
        binding.layoutLanguage.visibility = if (isConfucius) View.VISIBLE else View.GONE
        binding.layoutFormat.visibility = if (isConfucius) View.GONE else View.VISIBLE
    }

    private fun toggleRecording() {
        if (isRecording) stopRecording() else startRecording()
    }

    @Suppress("DEPRECATION")
    private fun startRecording() {
        val file = File(requireContext().cacheDir, "ref_${System.currentTimeMillis()}.m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        refAudioFile = file
        isRecording = true
        binding.btnRecord.text = getString(R.string.btn_stop)
        binding.tvRefStatus.text = "录音中..."
    }

    private fun stopRecording() {
        recorder?.apply { stop(); release() }
        recorder = null
        isRecording = false
        binding.btnRecord.text = getString(R.string.btn_record)
        binding.tvRefStatus.text = "录音完成：${refAudioFile?.name}"
    }

    private fun copyUriToFile(uri: Uri) {
        try {
            val mime = requireContext().contentResolver.getType(uri) ?: ""
            val ext = when {
                mime.contains("wav") -> "wav"
                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                mime.contains("mp4") || mime.contains("m4a") -> "m4a"
                mime.contains("ogg") -> "ogg"
                mime.contains("flac") -> "flac"
                else -> "m4a"  // 未知格式走转换
            }
            val file = File(requireContext().cacheDir, "ref_${System.currentTimeMillis()}.$ext")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            refAudioFile = file
            binding.tvRefStatus.text = "已选择：${file.name}"
        } catch (e: Exception) {
            binding.tvRefStatus.text = "读取失败：${e.message}"
        }
    }

    private fun generate() {
        val text = binding.etText.text?.toString()?.trim().orEmpty()
        val ref = refAudioFile
        if (text.isEmpty()) { binding.tvStatus.text = "请输入合成文本"; return }
        if (ref == null) { binding.tvStatus.text = "请上传或录制参考音频"; return }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.tvStatus.text = "正在生成..."

        lifecycleScope.launch {
            try {
                if (binding.rbConfucius.isChecked) generateConfucius(text, ref)
                else generateMiMo(text, ref)
            } catch (e: Exception) {
                binding.tvStatus.text = "错误：${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnGenerate.isEnabled = true
            }
        }
    }

    private suspend fun generateMiMo(text: String, ref: File) {
        val fmt = binding.spinnerFormat.text.toString()

        // wav/mp3 直接发送，其他格式转为 WAV
        val voiceFile = withContext(Dispatchers.IO) { convertToWav(ref) }
        val voiceBytes = voiceFile.readBytes()
        if (voiceFile.absolutePath != ref.absolutePath) voiceFile.delete()
        val mime = if (ref.extension.lowercase() == "mp3") "audio/mpeg" else "audio/wav"
        val b64 = android.util.Base64.encodeToString(voiceBytes, android.util.Base64.NO_WRAP)
        val voice = "data:$mime;base64,$b64"

        val messages = listOf(Message("user", ""), Message("assistant", text))
        val req = TtsRequest(model = "mimo-v2.5-tts-voiceclone", messages = messages, audio = AudioConfig(format = fmt, voice = voice))

        try {
            val resp = ApiClient.mimoService.mimoTts("Bearer ${BuildConfig.MIMO_API_KEY}", req)
            val audioB64 = resp.choices.firstOrNull()?.message?.audio?.data ?: throw Exception("未返回音频数据")
            val bytes = android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)
            val ext = if (fmt == "mp3") ".mp3" else ".wav"
            audioFile = File(requireContext().cacheDir, "clone_${System.currentTimeMillis()}$ext").apply { writeBytes(bytes) }
            binding.tvStatus.text = "成功！"
        } catch (e: HttpException) {
            val errBody = e.response()?.errorBody()?.string() ?: "无响应体"
            throw Exception("HTTP ${e.code()}: $errBody")
        }
    }

    private fun convertToWav(src: File): File {
        val ext = src.extension.lowercase()
        if (ext == "wav" || ext == "mp3") return src
        val outFile = File(requireContext().cacheDir, "conv_${System.currentTimeMillis()}.wav")
        val extractor = MediaExtractor()
        extractor.setDataSource(src.absolutePath)
        extractor.selectTrack(0)
        val srcFormat = extractor.getTrackFormat(0)
        val mime = srcFormat.getString(android.media.MediaFormat.KEY_MIME) ?: throw Exception("无法识别音频格式")
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(srcFormat, null, null, 0)
        decoder.start()

        // 检查解码器输出格式
        val outFormat = decoder.outputFormat
        val isFloat = if (android.os.Build.VERSION.SDK_INT >= 24) {
            outFormat.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING) &&
                outFormat.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
        } else false

        val pcmData = mutableListOf<Byte>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIdx >= 0) {
                val outBuf = decoder.getOutputBuffer(outIdx)!!
                val arr = ByteArray(bufferInfo.size)
                outBuf.get(arr)
                if (isFloat) {
                    // float32 → int16
                    val floatBuf = ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    val int16Arr = ByteArray(floatBuf.remaining() * 2)
                    val shortBuf = ByteBuffer.wrap(int16Arr).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (floatBuf.hasRemaining()) {
                        val s = (floatBuf.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        shortBuf.put(s)
                    }
                    pcmData.addAll(int16Arr.toList())
                } else {
                    pcmData.addAll(arr.toList())
                }
                decoder.releaseOutputBuffer(outIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        decoder.stop(); decoder.release()
        extractor.release()

        // 重采样到 24kHz 单声道
        val srcRate = srcFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
        val srcCh = srcFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
        val rawPcm = pcmData.toByteArray()
        val resampled = resamplePcm(rawPcm, srcRate, srcCh, 24000, 1)

        val byteRate = 24000 * 2
        FileOutputStream(outFile).use { fos ->
            fos.write("RIFF".toByteArray())
            fos.write(intLe(resampled.size + 36))
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(intLe(16))
            fos.write(shortLe(1))
            fos.write(shortLe(1))
            fos.write(intLe(24000))
            fos.write(intLe(byteRate))
            fos.write(shortLe(2))
            fos.write(shortLe(16))
            fos.write("data".toByteArray())
            fos.write(intLe(resampled.size))
            fos.write(resampled)
        }
        return outFile
    }

    private fun resamplePcm(pcm: ByteArray, srcRate: Int, srcCh: Int, dstRate: Int, dstCh: Int): ByteArray {
        val samples = ShortArray(pcm.size / 2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        val mono = if (srcCh > 1) {
            ShortArray(samples.size / srcCh) { i ->
                var sum = 0L
                for (c in 0 until srcCh) sum += samples[i * srcCh + c]
                (sum / srcCh).toShort()
            }
        } else samples
        if (mono.isEmpty()) return ByteArray(0)
        if (srcRate == dstRate || mono.size < 2) return shortsToBytes(mono)
        val ratio = srcRate.toDouble() / dstRate
        val outLen = (mono.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val idx = pos.toInt().coerceIn(0, mono.size - 2)
            val frac = (pos - idx).toFloat().coerceIn(0f, 1f)
            out[i] = (mono[idx] * (1 - frac) + mono[idx + 1] * frac).toInt().toShort()
        }
        return shortsToBytes(out)
    }

    private fun shortsToBytes(s: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.asShortBuffer().put(s)
        return buf.array()
    }

    private fun intLe(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun shortLe(v: Short) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()

    private suspend fun generateConfucius(text: String, ref: File) {
        val lang = binding.spinnerLang.text.toString()

        // 1. Upload
        val reqBody = ref.asRequestBody("audio/wav".toMediaType())
        val part = MultipartBody.Part.createFormData("files", ref.name, reqBody)
        val uploadResult = ApiClient.confuciusService.confuciusUpload("${ApiClient.CONFUCIUS_BASE}upload", part)
        val uploadedPath = uploadResult.firstOrNull() ?: throw Exception("上传失败")

        // 2. Register reference
        val fileData = mapOf("path" to uploadedPath, "meta" to mapOf("_type" to "gradio.FileData"))
        val refReq = ConfuciusRefRequest(data = listOf(fileData))
        val refResp = ApiClient.confuciusService.confuciusRef("${ApiClient.CONFUCIUS_BASE}api/_gradio_reference_uploaded", refReq)
        val state = (refResp.data[1] as? String) ?: throw Exception("注册参考音频失败")

        // 3. Predict
        val predReq = ConfuciusPredictRequest(data = listOf(text, lang, fileData, state))
        val predResp = ApiClient.confuciusService.confuciusPredict("${ApiClient.CONFUCIUS_BASE}api/_gradio_predict", predReq)

        @Suppress("UNCHECKED_CAST")
        val audioInfo = predResp.data[0] as? Map<String, Any> ?: throw Exception("合成失败")
        val audioPath = audioInfo["path"] as? String ?: throw Exception("未返回音频路径")
        val statusMsg = predResp.data[1] as? String ?: ""

        // 4. Download
        val audioBody = ApiClient.confuciusService.downloadFile("${ApiClient.CONFUCIUS_BASE}file=$audioPath")
        audioFile = File(requireContext().cacheDir, "confucius_${System.currentTimeMillis()}.wav").apply {
            writeBytes(audioBody.bytes())
        }
        binding.tvStatus.text = statusMsg
    }

    private fun playAudio() {
        val file = audioFile ?: run { binding.tvStatus.text = "请先生成音频"; return }
        stopAudio()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener { binding.tvStatus.text = "播放完成" }
        }
        binding.tvStatus.text = "正在播放..."
    }

    private fun stopAudio() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroyView() {
        stopAudio()
        recorder?.release()
        recorder = null
        _binding = null
        super.onDestroyView()
    }
}
