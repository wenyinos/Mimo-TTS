# MiMo TTS Studio

基于小米 MiMo-V2.5-TTS 系列模型和 Confucius4-TTS 的语音合成应用，提供 Web 版（Gradio）和 Android 原生版。

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

## Web 版（Gradio）

### 1. 配置

```bash
cp .env.example .env
```

编辑 `.env`：

```
MIMO_API_KEY=your_api_key_here    # MiMo API Key（必填）
APP_PASSWORD=your_password         # 访问密码（可选，为空则无密码）
```

API Key 可在 [MiMo 开放平台](https://mimo.mi.com/) 控制台获取。

> Confucius4-TTS 后端无需 API Key，直接调用公开服务。

### 2. 启动

#### 使用 uv（推荐）

```bash
# 安装 uv（如未安装）
curl -LsSf https://astral.sh/uv/install.sh | sh

# 创建虚拟环境并安装依赖
uv venv
uv pip install -r requirements.txt

# 激活虚拟环境并运行
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
| mimo-v2.5-tts | `https://api.xiaomimimo.com/v1/chat/completions` | API Key |
| mimo-v2.5-tts-voiceclone | 同上 | API Key |
| mimo-v2.5-tts-voicedesign | 同上 | API Key |
| Confucius4-TTS | `https://confucius4-tts.youdao.com/gradio/` | 无需认证 |
