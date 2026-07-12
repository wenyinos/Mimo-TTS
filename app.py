import os

import gradio as gr
from dotenv import load_dotenv

from backend import CONFUCIUS_LANGUAGES, VOICES, tts_clone, tts_confucius, tts_design, tts_preset, cleanup_audio_cache

load_dotenv()
APP_PASSWORD = os.environ.get("APP_PASSWORD", "").strip()

theme = gr.themes.Soft(
    primary_hue=gr.themes.colors.orange,
    secondary_hue=gr.themes.colors.amber,
    font=gr.themes.GoogleFont("Inter"),
)

css = """
.app-header { text-align: center; margin-bottom: 8px; }
.app-header h1 { margin-bottom: 4px; }
.app-header p { color: #666; font-size: 0.95em; }
.status-box textarea { font-size: 0.85em !important; }
"""

# Gradio 6.x: theme/css in Blocks(); 4.x~5.x: in launch()
_major = int(gr.__version__.split(".")[0])
_blocks_kwargs = dict(title="MiMo TTS Studio")
if _major < 6:
    _blocks_kwargs.update(theme=theme, css=css)

with gr.Blocks(**_blocks_kwargs) as demo:
    # ─── 密码验证层 ───
    with gr.Column(visible=bool(APP_PASSWORD)) as auth_gate:
        gr.Markdown("## 🔐 访问验证")
        pwd_input = gr.Textbox(label="请输入访问密码", type="password", placeholder="输入密码后回车或点击验证")
        pwd_btn = gr.Button("验证", variant="primary")
        pwd_msg = gr.Markdown("")

    # ─── 主内容层 ───
    with gr.Column(visible=not bool(APP_PASSWORD)) as main_content:
        gr.HTML("""
        <div class="app-header">
            <h1>MiMo TTS Studio</h1>
            <p>基于小米 MiMo-V2.5-TTS 系列模型 · 支持预置音色 / 音色克隆 / 音色设计</p>
        </div>
        """)

        with gr.Tabs():
            # ─── Tab 1: 预置音色 ───
            with gr.Tab("预置音色"):
                with gr.Row(equal_height=False):
                    with gr.Column(scale=1):
                        t1_text = gr.Textbox(label="合成文本", lines=4, placeholder="输入要转为语音的文本...")
                        t1_style = gr.Textbox(label="风格指令（可选）", lines=2, placeholder="如：用温柔的语气，语速稍慢")
                        with gr.Row():
                            t1_voice = gr.Dropdown(choices=VOICES, value="mimo_default", label="音色", scale=2)
                            t1_fmt = gr.Dropdown(choices=["mp3", "wav"], value="mp3", label="输出格式", scale=1)
                        t1_btn = gr.Button("生成语音", variant="primary", size="lg")
                    with gr.Column(scale=1):
                        t1_audio = gr.Audio(label="合成结果", type="filepath")
                        t1_status = gr.Textbox(label="状态", interactive=False, elem_classes="status-box")

            t1_btn.click(tts_preset, [t1_text, t1_style, t1_voice, t1_fmt], [t1_audio, t1_status])

            # ─── Tab 2: 音色克隆 ───
            with gr.Tab("音色克隆"):
                with gr.Row(equal_height=False):
                    with gr.Column(scale=1):
                        t2_text = gr.Textbox(label="合成文本", lines=4, placeholder="输入要转为语音的文本...")
                        t2_ref = gr.Audio(label="参考音频（3-10秒）", type="filepath", sources=["upload", "microphone"])
                        t2_backend = gr.Radio(
                            choices=["MiMo VoiceClone", "Confucius4-TTS"],
                            value="MiMo VoiceClone",
                            label="后端引擎",
                        )
                        t2_lang = gr.Dropdown(
                            choices=CONFUCIUS_LANGUAGES, value="zh",
                            label="语种（Confucius4）", visible=False,
                        )
                        t2_fmt = gr.Dropdown(choices=["mp3", "wav"], value="mp3", label="输出格式")
                        t2_btn = gr.Button("生成语音", variant="primary", size="lg")
                    with gr.Column(scale=1):
                        t2_audio = gr.Audio(label="合成结果", type="filepath")
                        t2_status = gr.Textbox(label="状态", interactive=False, elem_classes="status-box")

            def _toggle_confucius(backend):
                show = backend == "Confucius4-TTS"
                return gr.update(visible=show), gr.update(visible=not show)

            t2_backend.change(_toggle_confucius, [t2_backend], [t2_lang, t2_fmt])

            def _route_clone(text, ref_audio, backend, lang, fmt):
                if backend == "Confucius4-TTS":
                    return tts_confucius(text, ref_audio, lang)
                return tts_clone(text, ref_audio, fmt)

            t2_btn.click(_route_clone, [t2_text, t2_ref, t2_backend, t2_lang, t2_fmt], [t2_audio, t2_status])

            # ─── Tab 3: 音色设计 ───
            with gr.Tab("音色设计"):
                with gr.Row(equal_height=False):
                    with gr.Column(scale=1):
                        t3_desc = gr.Textbox(label="音色描述", lines=3, placeholder="如：A warm, friendly female voice with a gentle tone")
                        t3_text = gr.Textbox(label="合成文本（可选，勾选智能优化时可省略）", lines=3, placeholder="输入要转为语音的文本...")
                        with gr.Row():
                            t3_opt = gr.Checkbox(label="智能优化文本", value=False)
                            t3_fmt = gr.Dropdown(choices=["mp3", "wav"], value="mp3", label="输出格式")
                        t3_btn = gr.Button("生成语音", variant="primary", size="lg")
                    with gr.Column(scale=1):
                        t3_audio = gr.Audio(label="合成结果", type="filepath")
                        t3_status = gr.Textbox(label="状态", interactive=False, elem_classes="status-box")

            t3_btn.click(tts_design, [t3_text, t3_desc, t3_opt, t3_fmt], [t3_audio, t3_status])

        with gr.Row():
            cleanup_btn = gr.Button("清理缓存", variant="secondary", size="sm")
            cleanup_msg = gr.Textbox(show_label=False, interactive=False, elem_classes="status-box")

        cleanup_btn.click(cleanup_audio_cache, [], [cleanup_msg])

    # ─── 密码验证逻辑 ───
    def _check_password(pwd):
        if pwd.strip() == APP_PASSWORD:
            return gr.update(visible=False), gr.update(visible=True), ""
        return gr.update(), gr.update(), "❌ 密码错误，请重试"

    pwd_btn.click(_check_password, [pwd_input], [auth_gate, main_content, pwd_msg])
    pwd_input.submit(_check_password, [pwd_input], [auth_gate, main_content, pwd_msg])

if __name__ == "__main__":
    launch_kwargs = dict(show_error=True)
    if _major >= 6:
        launch_kwargs.update(theme=theme, css=css)
    demo.launch(**launch_kwargs)
