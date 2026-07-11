package com.mimotts.app.ui.design

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
import com.mimotts.app.databinding.FragmentDesignBinding
import kotlinx.coroutines.launch
import java.io.File

class DesignFragment : Fragment() {
    private var _binding: FragmentDesignBinding? = null
    private val binding get() = _binding!!
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val formats = resources.getStringArray(R.array.formats)
        binding.spinnerFormat.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, formats))
        binding.spinnerFormat.setText(formats[0], false)

        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnPlay.setOnClickListener { playAudio() }
        binding.btnStopPlay.setOnClickListener { stopAudio() }
    }

    private fun generate() {
        val desc = binding.etVoiceDesc.text?.toString()?.trim().orEmpty()
        if (desc.isEmpty()) { binding.tvStatus.text = "请输入音色描述"; return }

        val text = binding.etText.text?.toString()?.trim().orEmpty()
        val optimize = binding.cbOptimize.isChecked
        if (!optimize && text.isEmpty()) { binding.tvStatus.text = "请输入合成文本（或勾选智能优化）"; return }

        val fmt = binding.spinnerFormat.text.toString()

        val messages = mutableListOf(Message("user", desc))
        if (!optimize) messages.add(Message("assistant", text))

        val audioCfg = AudioConfig(format = fmt, optimizeTextPreview = optimize)
        val req = TtsRequest(model = "mimo-v2.5-tts-voicedesign", messages = messages, audio = audioCfg)

        binding.progressBar.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.tvStatus.text = "正在生成..."

        lifecycleScope.launch {
            try {
                val resp = ApiClient.mimoService.mimoTts(BuildConfig.MIMO_API_KEY, req)
                val b64 = resp.choices.firstOrNull()?.message?.audio?.data
                    ?: throw Exception("未返回音频数据")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val ext = if (fmt == "mp3") ".mp3" else ".wav"
                audioFile = File(requireContext().cacheDir, "design_${System.currentTimeMillis()}$ext").apply {
                    writeBytes(bytes)
                }
                binding.tvStatus.text = "成功！"
            } catch (e: Exception) {
                binding.tvStatus.text = "错误：${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnGenerate.isEnabled = true
            }
        }
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
