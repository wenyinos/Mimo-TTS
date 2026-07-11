# MiMo TTS Studio

基于小米 MiMo-V2.5-TTS 系列模型和 Confucius4-TTS 的语音合成 Web 应用，使用 Gradio 构建。

## 功能

### 预置音色（mimo-v2.5-tts）
- 9 种内置音色：mimo_default、冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean
- 支持风格指令控制（语速、情绪、角色扮演等）
- 输出格式：wav / mp3

### 音色克隆
支持两个后端引擎：
- **MiMo VoiceClone**（mimo-v2.5-tts-voiceclone）— 上传参考音频，克隆音色
- **Confucius4-TTS** — 支持 14 种语言（中/英/日/韩/德/法/泰/印尼/越南/西/葡/意/俄/马来）

### 音色设计（mimo-v2.5-tts-voicedesign）
- 通过自然语言描述生成任意音色
- 支持智能文本优化（optimize_text_preview）

## 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 配置 API Key

```bash
cp .env.example .env
```

编辑 `.env`，填入你的 MiMo API Key：

```
MIMO_API_KEY=your_api_key_here
```

API Key 可在 [MiMo 开放平台](https://mimo.mi.com/) 控制台获取。

> Confucius4-TTS 后端无需 API Key，直接调用公开服务。

### 3. 启动

```bash
python app.py
```

浏览器访问 `http://127.0.0.1:7860`。

## 项目结构

```
├── app.py              # Gradio 前端界面
├── backend.py          # TTS API 调用逻辑
├── requirements.txt    # Python 依赖
├── .env.example        # 环境变量模板
└── .gitignore
```

## API 参考

| 模型 | Endpoint | 认证 |
|------|----------|------|
| mimo-v2.5-tts | `https://api.xiaomimimo.com/v1/chat/completions` | API Key |
| mimo-v2.5-tts-voiceclone | 同上 | API Key |
| mimo-v2.5-tts-voicedesign | 同上 | API Key |
| Confucius4-TTS | `https://confucius4-tts.youdao.com/gradio/` | 无需认证 |
