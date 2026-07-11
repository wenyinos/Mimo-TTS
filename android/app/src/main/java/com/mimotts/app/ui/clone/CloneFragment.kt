package com.mimotts.app.ui.clone

import android.Manifest
import android.content.pm.PackageManager
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
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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
        val file = File(requireContext().cacheDir, "ref_${System.currentTimeMillis()}.wav")
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
            val file = File(requireContext().cacheDir, "ref_${System.currentTimeMillis()}.wav")
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
        val mime = if (ref.extension == "mp3") "audio/mpeg" else "audio/wav"
        val b64 = android.util.Base64.encodeToString(ref.readBytes(), android.util.Base64.NO_WRAP)
        val voice = "data:$mime;base64,$b64"

        val messages = listOf(Message("user", ""), Message("assistant", text))
        val req = TtsRequest(model = "mimo-v2.5-tts-voiceclone", messages = messages, audio = AudioConfig(format = fmt, voice = voice))

        val resp = ApiClient.mimoService.mimoTts("Bearer ${BuildConfig.MIMO_API_KEY}", req)
        val audioB64 = resp.choices.firstOrNull()?.message?.audio?.data ?: throw Exception("未返回音频数据")
        val bytes = android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)
        val ext = if (fmt == "mp3") ".mp3" else ".wav"
        audioFile = File(requireContext().cacheDir, "clone_${System.currentTimeMillis()}$ext").apply { writeBytes(bytes) }
        binding.tvStatus.text = "成功！"
    }

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
