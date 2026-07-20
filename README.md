# MiMo TTS Studio

多引擎语音合成应用，支持 MiMo TTS / Confucius4-TTS / Qwen TTS，提供 Web 版（Gradio）和 Android 原生版。

## 功能

### 预置音色（mimo-v2.5-tts）
- 9 种内置音色：mimo_default、冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean
- 支持风格指令控制（语速、情绪、角色扮演等）
- 输出格式：mp3 / wav

### 音色克隆
支持两个后端引擎：
- **MiMo VoiceClone**（mimo-v2.5-tts-voiceclone）— 上传参考音频，自动转换为 24kHz WAV 后克隆音色
- **Confucius4-TTS** — 支持 14 种语言（中/英/日/韩/德/法/泰/印尼/越南/西/葡/意/俄/马来）

### 音色设计（mimo-v2.5-tts-voicedesign）
- 通过自然语言描述生成任意音色
- 支持智能文本优化（optimize_text_preview）

### Qwen TTS（阿里云百炼）
- **qwen-audio-3.0-tts-plus** — 高音质专业版，适合有声书、影视配音等场景
  - 音色：longanlingxin、longanlufeng
- **qwen-audio-3.0-tts-flash** — 低延迟快速版，适合实时交互场景
  - 音色：longanhuan_v3.6、longjielidou_v3.6、loongeva_v3.6、loongjohn
- 详见 [音色列表](https://help.aliyun.com/zh/model-studio/qwen-audio-tts-voice-list)

## Web 版（Gradio）

### 1. 配置

```bash
cp .env.example .env
```

编辑 `.env`：

```
MIMO_API_KEY=your_api_key_here           # MiMo API Key
QWEN_API_KEY=your_dashscope_key_here     # 阿里云百炼 API Key（使用 Qwen TTS 时需要）
QWEN_WORKSPACE_ID=your_workspace_id      # 百炼业务空间 ID
APP_PASSWORD=your_password                # 访问密码（可选，为空则无密码）
GRADIO_SHARE=false                        # 是否生成临时公开链接
```

API Key 获取：
- MiMo：[MiMo 开放平台](https://mimo.mi.com/)
- Qwen：[阿里云百炼控制台](https://bailian.console.aliyun.com/)

> Confucius4-TTS 后端无需 API Key，直接调用公开服务。

### 2. 启动

#### 使用 uv（推荐）

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
uv venv
uv pip install -r requirements.txt
source .venv/bin/activate
python app.py
```

#### 使用 pip

```bash
pip install -r requirements.txt
python app.py
```

浏览器访问 `http://127.0.0.1:7860`。

> 需要系统安装 ffmpeg：`sudo apt install ffmpeg`（Ubuntu）或 `brew install ffmpeg`（macOS）

## Android 版

原生 Android 应用，最低支持 Android 9.0（API 28），功能与 Web 版一致。

### 下载

从 [Releases](https://github.com/wenyinos/Mimo-TTS/releases/latest) 下载最新 APK（`mimo-tts-v*.apk`）。

### 构建

通过 GitHub Actions 自动打包：

1. Fork 或 clone 本仓库
2. 在 GitHub 仓库 Settings → Secrets → Actions 中添加 `MIMO_API_KEY`
3. 推送代码或在 Actions 页面手动触发构建
4. 构建完成后在 Releases 页面下载 APK

### 版本历史

| 版本 | 说明 |
|------|------|
| v1.1.0 | 默认 mp3 格式、音频自动转换为 24kHz WAV、非流式合成 |
| v1.0.0 | 初始版本 |

## API 参考

| 模型 | Endpoint | 认证 |
|------|----------|------|
| mimo-v2.5-tts | `https://api.xiaomimimo.com/v1/chat/completions` | MiMo API Key |
| mimo-v2.5-tts-voiceclone | 同上 | MiMo API Key |
| mimo-v2.5-tts-voicedesign | 同上 | MiMo API Key |
| Confucius4-TTS | `https://confucius4-tts.youdao.com/gradio/` | 无需认证 |
| qwen-audio-3.0-tts-plus | `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer` | DashScope API Key |
| qwen-audio-3.0-tts-flash | 同上 | DashScope API Key |
