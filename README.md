# MiMo TTS Studio

基于小米 MiMo-V2.5-TTS 系列模型和 Confucius4-TTS 的语音合成应用，提供 Web 版（Gradio）和 Android 原生版。

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

## Web 版（Gradio）

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

## Android 版

原生 Android 应用，最低支持 Android 9.0（API 28），功能与 Web 版一致。

### 构建方式

通过 GitHub Actions 自动打包，无需本地开发环境：

1. **Fork 或 clone 本仓库**

2. **配置 Secrets**：在 GitHub 仓库 Settings → Secrets and variables → Actions 中添加：
   - `MIMO_API_KEY`：你的 MiMo API Key

3. **触发构建**：推送到 `main` 分支的 `android/` 目录变更会自动触发，也可在 Actions 页面手动运行

4. **下载 APK**：构建完成后在 Actions → 对应 workflow → Artifacts 中下载 `mimo-tts-apk`

### 项目结构

```
android/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/mimotts/app/
│   │   ├── MainActivity.kt          # 主界面（TabLayout + ViewPager2）
│   │   ├── api/
│   │   │   ├── ApiClient.kt         # Retrofit 网络客户端
│   │   │   ├── Models.kt            # API 数据模型
│   │   │   └── TtsService.kt        # API 接口定义
│   │   └── ui/
│   │       ├── preset/PresetFragment.kt   # 预置音色
│   │       ├── clone/CloneFragment.kt     # 音色克隆
│   │       └── design/DesignFragment.kt   # 音色设计
│   └── res/
│       ├── layout/                   # 布局文件
│       └── values/                   # 字符串、主题、颜色
├── build.gradle                      # 项目级 Gradle 配置
├── app/build.gradle                  # 应用级 Gradle 配置
└── local.properties.example          # API Key 配置模板
```

## API 参考

| 模型 | Endpoint | 认证 |
|------|----------|------|
| mimo-v2.5-tts | `https://api.xiaomimimo.com/v1/chat/completions` | API Key |
| mimo-v2.5-tts-voiceclone | 同上 | API Key |
| mimo-v2.5-tts-voicedesign | 同上 | API Key |
| Confucius4-TTS | `https://confucius4-tts.youdao.com/gradio/` | 无需认证 |
