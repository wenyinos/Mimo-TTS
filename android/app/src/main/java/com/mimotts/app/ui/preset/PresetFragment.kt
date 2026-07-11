package com.mimotts.app.ui.preset

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mimotts.app.BuildConfig
import com.mimotts.app.R
import com.mimotts.app.api.*
import com.mimotts.app.databinding.FragmentPresetBinding
import kotlinx.coroutines.launch
import java.io.File

class PresetFragment : Fragment() {
    private var _binding: FragmentPresetBinding? = null
    private val binding get() = _binding!!
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPresetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val voices = resources.getStringArray(R.array.voices)
        val formats = resources.getStringArray(R.array.formats)

        binding.spinnerVoice.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, voices))
        binding.spinnerVoice.setText(voices[0], false)
        binding.spinnerFormat.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, formats))
        binding.spinnerFormat.setText(formats[0], false)

        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnPlay.setOnClickListener { playAudio() }
        binding.btnStopPlay.setOnClickListener { stopAudio() }
    }

    private fun generate() {
        val text = binding.etText.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { binding.tvStatus.text = "请输入合成文本"; return }

        val style = binding.etStyle.text?.toString()?.trim().orEmpty()
        val voice = binding.spinnerVoice.text.toString()
        val fmt = binding.spinnerFormat.text.toString()

        val messages = mutableListOf<Message>()
        if (style.isNotEmpty()) messages.add(Message("user", style))
        messages.add(Message("assistant", text))

        val audioCfg = AudioConfig(format = fmt, voice = voice)
        val req = TtsRequest(model = "mimo-v2.5-tts", messages = messages, audio = audioCfg)

        binding.progressBar.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.tvStatus.text = "正在生成..."

        lifecycleScope.launch {
            try {
                val resp = ApiClient.mimoService.mimoTts(BuildConfig.MIMO_API_KEY, req)
                val b64 = resp.choices.firstOrNull()?.message?.audio?.data
                    ?: throw Exception("未返回音频数据")
                audioFile = decodeAudio(b64, fmt)
                binding.tvStatus.text = "成功！"
            } catch (e: Exception) {
                binding.tvStatus.text = "错误：${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnGenerate.isEnabled = true
            }
        }
    }

    private fun decodeAudio(b64: String, fmt: String): File {
        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        val ext = if (fmt == "mp3") ".mp3" else ".wav"
        val file = File(requireContext().cacheDir, "preset_${System.currentTimeMillis()}$ext")
        file.writeBytes(bytes)
        return file
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
        _binding = null
        super.onDestroyView()
    }
}
