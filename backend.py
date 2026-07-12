import base64
import os
import subprocess
import tempfile

import requests
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()

client = OpenAI(
    api_key=os.environ.get("MIMO_API_KEY"),
    base_url="https://api.xiaomimimo.com/v1",
)

VOICES = ["mimo_default", "冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean"]


def _convert_to_wav(src_path: str) -> str:
    """将任意音频格式转换为 WAV，返回转换后文件路径。已是 WAV 则直接返回。"""
    if src_path.lower().endswith(".wav"):
        return src_path
    tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    tmp.close()
    subprocess.run(
        ["ffmpeg", "-y", "-i", src_path, "-ar", "24000", "-ac", "1", tmp.name],
        capture_output=True, check=True,
    )
    return tmp.name


def _decode_audio(audio_data: str, fmt: str) -> str:
    """解码 base64 音频，写入临时文件并返回路径。"""
    audio_bytes = base64.b64decode(audio_data)
    ext = ".wav" if fmt == "wav" else ".mp3"
    tmp = tempfile.NamedTemporaryFile(suffix=ext, delete=False)
    tmp.write(audio_bytes)
    tmp.close()
    return tmp.name


def tts_preset(text: str, style: str, voice: str, fmt: str):
    """mimo-v2.5-tts 预置音色合成。"""
    if not text.strip():
        return None, "请输入要合成的文本"

    messages = []
    if style.strip():
        messages.append({"role": "user", "content": style.strip()})
    messages.append({"role": "assistant", "content": text.strip()})

    audio_cfg = {"voice": voice, "format": fmt}

    try:
        completion = client.chat.completions.create(
            model="mimo-v2.5-tts",
            messages=messages,
            audio=audio_cfg,
            stream=False,
        )
        msg = completion.choices[0].message
        path = _decode_audio(msg.audio.data, fmt)
        return path, f"成功！格式: {fmt}"
    except Exception as e:
        return None, f"错误：{e}"


def tts_clone(text: str, ref_audio: str, fmt: str):
    """mimo-v2.5-tts-voiceclone 音色克隆合成（始终非流式，API 流式暂未上线）。"""
    if not text.strip():
        return None, "请输入要合成的文本"
    if ref_audio is None:
        return None, "请上传参考音频文件"

    try:
        wav_path = _convert_to_wav(ref_audio)
        with open(wav_path, "rb") as f:
            voice_bytes = f.read()
        if wav_path != ref_audio:
            os.unlink(wav_path)
        voice_b64 = f"data:audio/wav;base64,{base64.b64encode(voice_bytes).decode()}"
    except Exception as e:
        return None, f"读取音频失败：{e}"

    messages = [
        {"role": "user", "content": ""},
        {"role": "assistant", "content": text.strip()},
    ]
    audio_cfg = {"voice": voice_b64, "format": fmt}

    try:
        completion = client.chat.completions.create(
            model="mimo-v2.5-tts-voiceclone",
            messages=messages,
            audio=audio_cfg,
            stream=False,
        )
        msg = completion.choices[0].message
        path = _decode_audio(msg.audio.data, fmt)
        return path, f"成功！格式: {fmt}"
    except Exception as e:
        return None, f"错误：{e}"


def tts_design(text: str, voice_desc: str, optimize: bool, fmt: str):
    """mimo-v2.5-tts-voicedesign 音色设计合成。"""
    if not voice_desc.strip():
        return None, "请输入音色描述"
    if not optimize and not text.strip():
        return None, "请输入要合成的文本（或勾选智能优化）"

    messages = [{"role": "user", "content": voice_desc.strip()}]
    if not optimize:
        messages.append({"role": "assistant", "content": text.strip()})

    audio_cfg = {"optimize_text_preview": optimize, "format": fmt}

    try:
        completion = client.chat.completions.create(
            model="mimo-v2.5-tts-voicedesign",
            messages=messages,
            audio=audio_cfg,
            stream=False,
        )
        msg = completion.choices[0].message
        path = _decode_audio(msg.audio.data, fmt)
        preview = getattr(msg, "final_text_preview", "")
        status = f"成功！格式: {fmt}"
        if preview:
            status += f"\n优化后文本: {preview}"
        return path, status
    except Exception as e:
        return None, f"错误：{e}"


CONFUCIUS_BASE = "https://confucius4-tts.youdao.com/gradio"
CONFUCIUS_LANGUAGES = [
    "zh", "en", "ja", "ko", "de", "fr", "th", "id", "vi", "es", "pt", "it", "ru", "ms",
]


def tts_confucius(text: str, ref_audio: str, language: str):
    """Confucius4-TTS 音色克隆合成。"""
    if not text.strip():
        return None, "请输入要合成的文本"
    if ref_audio is None:
        return None, "请上传参考音频文件"

    try:
        # 1. 上传参考音频（转换为 WAV）
        wav_path = _convert_to_wav(ref_audio)
        with open(wav_path, "rb") as f:
            resp = requests.post(
                f"{CONFUCIUS_BASE}/upload",
                files={"files": ("ref.wav", f, "audio/wav")},
                timeout=30,
            )
        if wav_path != ref_audio:
            os.unlink(wav_path)
        resp.raise_for_status()
        uploaded_path = resp.json()[0]

        file_data = {"path": uploaded_path, "meta": {"_type": "gradio.FileData"}}

        # 2. 注册参考音频，获取 state
        resp_ref = requests.post(
            f"{CONFUCIUS_BASE}/api/_gradio_reference_uploaded",
            json={"data": [file_data]},
            timeout=30,
        )
        resp_ref.raise_for_status()
        ref_result = resp_ref.json()
        state = ref_result["data"][1]

        # 3. 调用合成
        resp_pred = requests.post(
            f"{CONFUCIUS_BASE}/api/_gradio_predict",
            json={"data": [text.strip(), language, file_data, state]},
            timeout=120,
        )
        resp_pred.raise_for_status()
        pred = resp_pred.json()

        audio_info = pred["data"][0]
        status_msg = pred["data"][1]

        # 4. 下载音频文件
        audio_url = f"{CONFUCIUS_BASE}/file={audio_info['path']}"
        resp_audio = requests.get(audio_url, timeout=30)
        resp_audio.raise_for_status()

        tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
        tmp.write(resp_audio.content)
        tmp.close()
        return tmp.name, status_msg

    except Exception as e:
        return None, f"错误：{e}"


def cleanup_audio_cache():
    """清理临时目录中的所有音频文件，返回清理状态。"""
    tmp_dir = tempfile.gettempdir()
    audio_exts = (".wav", ".mp3", ".m4a", ".ogg", ".flac", ".pcm")
    removed = 0
    freed = 0

    for name in os.listdir(tmp_dir):
        if not name.lower().endswith(audio_exts):
            continue
        path = os.path.join(tmp_dir, name)
        if not os.path.isfile(path):
            continue
        try:
            size = os.path.getsize(path)
            os.unlink(path)
            removed += 1
            freed += size
        except OSError:
            continue

    size_str = f"{freed / 1024:.1f} KB" if freed < 1024 * 1024 else f"{freed / 1024 / 1024:.1f} MB"
    return f"已清理 {removed} 个文件，释放 {size_str}" if removed else "无需清理"
