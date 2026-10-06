import hashlib
import os
from pathlib import Path

import gradio as gr
from dotenv import load_dotenv

from backend import CONFUCIUS_LANGUAGES, VOICES, QWEN_VOICES, QWEN_MODELS, tts_clone, tts_confucius, tts_design, tts_preset, tts_qwen, cleanup_audio_cache

load_dotenv()
APP_PASSWORD = os.environ.get("APP_PASSWORD", "").strip()
GRADIO_SHARE = os.environ.get("GRADIO_SHARE", "").strip().lower() in ("1", "true", "yes")

_major = int(gr.__version__.split(".")[0])

# ─── 本地字体（随项目分发，不引用任何在线资源）───
FONT_DIR = Path(__file__).resolve().parent / "static" / "fonts"
if hasattr(gr, "set_static_paths"):
    gr.set_static_paths([str(FONT_DIR)])
_file_prefix = "/gradio_api/file=" if _major >= 5 else "/file="


def _font_face(family: str, filename: str, weight: int) -> str:
    return (
        "@font-face {\n"
        f"  font-family: '{family}';\n"
        f"  src: url('{_file_prefix}{FONT_DIR / filename}') format('woff2');\n"
        f"  font-weight: {weight};\n"
        "  font-display: swap;\n"
        "}\n"
    )


FONT_FACES = (
    _font_face("Noto Sans SC", "noto-sans-sc-300.woff2", 300)
    + _font_face("Noto Sans SC", "noto-sans-sc-400.woff2", 400)
    + _font_face("Noto Sans SC", "noto-sans-sc-500.woff2", 500)
    + _font_face("Noto Sans SC", "noto-sans-sc-700.woff2", 700)
    + _font_face("Noto Serif SC", "noto-serif-sc-500.woff2", 500)
    + _font_face("Noto Serif SC", "noto-serif-sc-700.woff2", 700)
    + _font_face("Questrial", "questrial-latin-400.woff2", 400)
)

# ─── 主题：参考 wenyinos.com 紫色设计 ───
WENYIN_PURPLE = gr.themes.Color(
    name="wenyin-purple",
    c50="#faf8fd", c100="#f4eefb", c200="#e5ddf0", c300="#d3c4ea",
    c400="#b497de", c500="#8e63cc", c600="#6f42c1", c700="#5b36a0",
    c800="#4a2c82", c900="#3b2266", c950="#241443",
)

theme = gr.themes.Soft(
    primary_hue=WENYIN_PURPLE,
    secondary_hue=WENYIN_PURPLE,
    font=(gr.themes.Font("Noto Sans SC"), "ui-sans-serif", "system-ui", "sans-serif"),
    font_mono=("ui-monospace", "SFMono-Regular", "Consolas", "monospace"),
).set(
    # 浅色
    body_background_fill="#fcfafd",
    body_text_color="#2d2140",
    body_text_color_subdued="#6b5b8a",
    block_background_fill="#ffffff",
    block_border_color="#e5ddf0",
    block_shadow="0 4px 24px rgba(75, 44, 130, 0.06)",
    input_background_fill="#ffffff",
    input_border_color="#e5ddf0",
    panel_background_fill="#f8f4fc",
    button_large_radius="46px",
    button_medium_radius="46px",
    button_small_radius="46px",
    button_secondary_background_fill="#f6f2fa",
    button_secondary_background_fill_hover="#ede6f7",
    button_secondary_border_color="#e5ddf0",
    button_secondary_text_color="#4a2c82",
    # 深色
    body_background_fill_dark="#16101f",
    body_text_color_dark="#ece5f6",
    body_text_color_subdued_dark="#a99cc4",
    block_background_fill_dark="#1e1729",
    block_border_color_dark="#35294d",
    block_shadow_dark="0 4px 24px rgba(0, 0, 0, 0.3)",
    input_background_fill_dark="#191223",
    input_border_color_dark="#35294d",
    panel_background_fill_dark="#1a1324",
    button_secondary_background_fill_dark="#251c36",
    button_secondary_background_fill_hover_dark="#2e2344",
    button_secondary_border_color_dark="#3f3060",
    button_secondary_text_color_dark="#c9b8e8",
)

# ─── 页面样式 ───
css = FONT_FACES + """
:root {
    --wy-gradient: linear-gradient(160deg, #4a2c82 0%, #6f42c1 55%, #8b5fd6 100%);
    --wy-purple-deep: #3b2266;
    --wy-card-bg: #ffffff;
    --wy-card-border: #e5ddf0;
    --wy-card-shadow: 0 4px 24px rgba(75, 44, 130, 0.06);
    --wy-muted: #6b5b8a;
}
.dark {
    --wy-card-bg: #1e1729;
    --wy-card-border: #35294d;
    --wy-card-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
    --wy-muted: #a99cc4;
}

.gradio-container {
    width: 100% !important;
    max-width: 75vw !important;
    margin-left: auto !important;
    margin-right: auto !important;
}
.gradio-container .main { padding: 1.5rem 1.5rem 1rem !important; }

/* ── 顶栏 ── */
.wy-topbar {
    position: relative;
    background: var(--wy-gradient);
    border-radius: 12px;
    padding: 2.1rem 5rem 1.8rem;
    text-align: center;
    box-shadow: 0 12px 32px rgba(75, 44, 130, 0.16);
    margin-bottom: 2px;
}
.dark .wy-topbar { box-shadow: 0 12px 32px rgba(0, 0, 0, 0.4); }
.wy-title {
    font-family: 'Questrial', 'Noto Sans SC', sans-serif;
    font-weight: 400;
    font-size: 1.95rem;
    letter-spacing: 2.5px;
    color: #fff;
    margin: 0 0 0.35rem 0;
    text-shadow: 0 2px 24px rgba(0, 0, 0, 0.15);
}
.wy-subtitle {
    font-size: 0.875rem;
    font-weight: 300;
    letter-spacing: 1px;
    color: rgba(255, 255, 255, 0.82);
    margin: 0;
}
.wy-theme-toggle {
    position: absolute;
    top: 1rem;
    right: 1rem;
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.42rem 1.05rem;
    font-size: 0.82rem;
    font-weight: 500;
    font-family: inherit;
    color: #fff;
    background: rgba(255, 255, 255, 0.14);
    border: 1px solid rgba(255, 255, 255, 0.4);
    border-radius: 46px;
    cursor: pointer;
    backdrop-filter: blur(6px);
    transition: all 200ms ease;
}
.wy-theme-toggle:hover {
    background: #fff;
    border-color: #fff;
    color: var(--wy-purple-deep);
}
.wy-when-dark { display: none; }
.dark .wy-when-light { display: none; }
.dark .wy-when-dark { display: inline; }

/* ── 卡片面板 ── */
.wy-card {
    background: var(--wy-card-bg);
    border: 1px solid var(--wy-card-border);
    border-radius: 10px;
    padding: 1rem 1rem 0.9rem !important;
    box-shadow: var(--wy-card-shadow);
}

/* ── 生成按钮：宽屏适宽居中，手机全宽 ── */
.wy-generate { max-width: 420px; width: 100%; margin: 4px auto 0 !important; }

/* ── 清理缓存行：居中窄布局 ── */
.wy-cleanup-row { max-width: 640px; margin: 0.25rem auto 0; align-items: center; }
.wy-cleanup-btn { max-width: 150px; min-width: 110px; }
.wy-cleanup-msg { flex: 1; }

/* ── 状态框 ── */
.status-box textarea { font-size: 0.85em !important; }

@media (max-width: 640px) {
    .gradio-container { max-width: 100% !important; }
    .gradio-container .main { padding: 0.75rem 24px 1rem !important; }
    .wy-topbar { padding: 3.4rem 1.25rem 1.5rem; }
    .wy-theme-toggle { top: 0.75rem; right: 0.75rem; padding: 0.35rem 0.85rem; font-size: 0.78rem; }
    .wy-title { font-size: 1.6rem; }
}
"""

# Gradio 6.x 会对 css 参数做作用域改写（媒体查询内原版规则丢失），改用 head 原样注入
_head_html = f"<style>\n{css}\n</style>"

# 登录页由独立模板渲染，不加载 head 注入；样式经 theme_css 通道送达（含自带变量，不依赖上面的 css）
_LOGIN_CSS = """
:root {
    --wy-gradient: linear-gradient(160deg, #4a2c82 0%, #6f42c1 55%, #8b5fd6 100%);
    --wy-purple-deep: #3b2266;
    --wy-card-bg: #ffffff;
    --wy-card-border: #e5ddf0;
}
.dark {
    --wy-card-bg: #1e1729;
    --wy-card-border: #35294d;
}
.gradio-container:has(.auth) {
    align-items: center;
    justify-content: center;
    min-height: 100vh;
}
.gradio-container:has(.auth) .main,
.gradio-container:has(.auth) .wrap {
    align-items: center;
    justify-content: center;
    flex: 1;
}
.panel:has(> p.auth) {
    background: var(--wy-card-bg);
    border: 1px solid var(--wy-card-border);
    border-radius: 16px;
    box-shadow: 0 12px 32px rgba(75, 44, 130, 0.1);
    overflow: hidden;
    padding: 0 2rem 1.9rem !important;
}
.panel:has(> p.auth)::before {
    content: 'MiMo TTS Studio';
    display: block;
    font-family: 'Questrial', 'Noto Sans SC', sans-serif;
    font-size: 1.45rem;
    letter-spacing: 2.5px;
    text-align: center;
    color: #fff;
    background: var(--wy-gradient);
    margin: 0 -2rem 1.5rem;
    padding: 1.5rem 1rem 1.3rem;
}
.panel:has(> p.auth) h2 {
    font-family: 'Questrial', 'Noto Sans SC', sans-serif;
    color: var(--wy-purple-deep);
    letter-spacing: 0.5px;
    margin-bottom: 0.35rem;
}
.panel:has(> p.auth) .block {
    background: transparent !important;
    border: none !important;
    box-shadow: none !important;
    padding-left: 0 !important;
    padding-right: 0 !important;
}
.panel:has(> p.auth) .form { background: transparent !important; }
.panel:has(> p.auth) .form > .block:first-of-type { display: none !important; }
.panel:has(> p.auth) .form > .block:first-of-type + .block { margin-top: 0 !important; }
.panel:has(> p.auth) input {
    border: 1px solid var(--wy-card-border) !important;
    border-radius: 8px !important;
}
.dark .panel:has(> p.auth) { box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45); }
.dark .panel:has(> p.auth) h2 { color: #ece5f6; }
"""

# Gradio 6.x: theme head in launch(); 4.x~5.x: in Blocks()
_blocks_kwargs = dict(title="MiMo TTS Studio")
if _major < 6:
    _blocks_kwargs.update(theme=theme, css=css, head=_head_html)

with gr.Blocks(**_blocks_kwargs) as demo:
    with gr.Column():
        gr.HTML("""
        <div class="wy-topbar">
            <h1 class="wy-title">MiMo TTS Studio</h1>
            <p class="wy-subtitle">支持 MiMo TTS · Confucius4-TTS · Qwen TTS</p>
            <button class="wy-theme-toggle" type="button" title="切换日间 / 夜间模式"
                    onclick="var d=document.body.classList.contains('dark');document.body.classList.toggle('dark',!d);var u=new URL(location.href);u.searchParams.set('__theme',d?'light':'dark');history.replaceState(null,'',u.toString());">
                <span class="wy-when-light">⏾ 夜间</span>
                <span class="wy-when-dark">☀︎ 日间</span>
            </button>
        </div>
        """)

        with gr.Tabs():
            # ─── Tab 1: 预置音色 ───
            with gr.Tab("预置音色"):
                with gr.Row(equal_height=False):
                    with gr.Column(scale=1, elem_classes="wy-card"):
                        t1_text = gr.Textbox(label="合成文本", lines=4, placeholder="输入要转为语音的文本...")
                        t1_style = gr.Textbox(label="风格指令（可选）", lines=2, placeholder="如：用温柔的语气，语速稍慢")
                        with gr.Row():
                            t1_voice = gr.Dropdown(choices=VOICES, value="mimo_default", label="音色", scale=2)
                            t1_fmt = gr.Dropdown(choices=["mp3", "wav"], value="mp3", label="输出格式", scale=1)
                        t1_btn = gr.Button("生成语音", variant="primary", size="lg", elem_classes="wy-generate")
                    with gr.Column(scale=1, elem_classes="wy-card"):
                        t1_audio = gr.Audio(label="合成结果", type="filepath")
                        t1_status = gr.Textbox(label="状态", interactive=False, elem_classes="status-box")

            t1_btn.click(tts_preset, [t1_text, t1_style, t1_voice, t1_fmt], [t1_audio, t1_status])

            # ─── Tab 2: 音色克隆 ───
            with gr.Tab("音色克隆"):
                with gr.Row(equal_height=False):
                    with gr.Column(scale=1, elem_classes="wy-card"):
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
                        t2_btn = gr.Button("生成语音", variant="primary", size="lg", elem_classes="wy-generate")
                    with gr.Column(scale=1, elem_classes="wy-card"):
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
                    with gr.Column(scale=1, elem_classes="wy-card"):
                        t3_desc = gr.Textbox(label="音色描述", lines=3, placeholder="如：A warm, friendly female voice with a gentle tone")
                        t3_text = gr.Textbox(label="合成文本（可选，勾选智能优化时可省略）", lines=3, placeholder="输入要转为语音的文本...")
                        with gr.Row():
                            t3_opt = gr.Checkbox(label="智能优化文本", value=False)
                            t3_fmt = gr.Dropdown(choices=["mp3", "wav"], value="mp3", label="输出格式")
                        t3_btn = gr.Button("生成语音", variant="primary", size="lg", elem_classes="wy-generate")
                    with gr.Column(scale=1, elem_classes="wy-card"):
                        t3_audio = gr.Audio(label="合成结果", type="filepath")
                        t3_status = gr.Textbox(label="状态", interactive=False, elem_classes="status-box")

            t3_btn.click(tts_design, [t3_text, t3_desc, t3_opt, t3_fmt], [t3_audio, t3_status])

            # ─── Tab 4: Qwen TTS ───
            with gr.Tab("Qwen TTS"):
                with gr.Row(equal_height=False):
                    with gr.Column(scale=1, elem_classes="wy-card"):
                        t4_text = gr.Textbox(label="合成文本", lines=3, placeholder="输入要转为语音的文本...")
                        t4_instruction = gr.Textbox(
                            label="指令控制（可选）",
                            lines=2,
                            placeholder="如：语速较快，带有明显的上扬语调",
                        )
                        t4_model = gr.Dropdown(choices=QWEN_MODELS, value=QWEN_MODELS[0], label="模型")
                        t4_voice = gr.Dropdown(choices=QWEN_VOICES[QWEN_MODELS[0]], value=QWEN_VOICES[QWEN_MODELS[0]][0], label="音色")
                        t4_fmt = gr.Dropdown(choices=["mp3", "wav"], value="mp3", label="输出格式")
                        t4_btn = gr.Button("生成语音", variant="primary", size="lg", elem_classes="wy-generate")
                    with gr.Column(scale=1, elem_classes="wy-card"):
                        t4_audio = gr.Audio(label="合成结果", type="filepath")
                        t4_status = gr.Textbox(label="状态", interactive=False, elem_classes="status-box")
                        gr.Markdown("""
**使用说明：**
- **指令控制**：用自然语言描述语音风格（音调、语速、情感等），如「沉稳的中年男性，语速缓慢」
- **文本内嵌标签**：在合成文本中直接嵌入标签，如 `[excited]今天天气真好！[laughing]`
- **控制类标签**：`[happy]` `[sad]` `[angry]` `[excited]` `[crying]` 等，作用于后续文本
- **富语言标签**：`[laughing]` `[sighing]` `[breathing]` 等，在当前位置插入拟声效果
- **描述维度参考**：音调（低沉/清脆）、语速（偏快/缓慢）、情感（温柔/激昂）、年龄、性别
""")

            def _update_qwen_voices(model):
                return gr.update(choices=QWEN_VOICES[model], value=QWEN_VOICES[model][0])

            t4_model.change(_update_qwen_voices, [t4_model], [t4_voice])
            t4_btn.click(tts_qwen, [t4_text, t4_voice, t4_model, t4_fmt, t4_instruction], [t4_audio, t4_status])

        with gr.Row(elem_classes="wy-cleanup-row"):
            cleanup_btn = gr.Button("清理缓存", variant="secondary", size="sm", elem_classes="wy-cleanup-btn")
            cleanup_msg = gr.Textbox(show_label=False, interactive=False, scale=3, elem_classes=["status-box", "wy-cleanup-msg"])

        cleanup_btn.click(cleanup_audio_cache, [], [cleanup_msg])


def _check_auth(username, password):
    return password == APP_PASSWORD


# 登录表单要求用户名非空，注入脚本自动填充（该字段已被 CSS 隐藏，用户只需输密码）
# 登录页不加载 head 注入，包装模板渲染以送达这段脚本
import gradio.routes as _gr_routes  # noqa: E402

_AUTOFILL_JS = (
    "<script>(function(){"
    "function f(){var u=document.querySelector('input[type=text]');"
    "if(u&&!u.value){u.value='mimo';u.dispatchEvent(new Event('input',{bubbles:true}));}}"
    "var n=0,t=setInterval(function(){f();if(++n>60)clearInterval(t);},250);"
    "})();</script>"
)

_orig_template_response = _gr_routes.templates.TemplateResponse


def _template_response_with_autofill(*args, **kwargs):
    resp = _orig_template_response(*args, **kwargs)
    try:
        body = resp.body
        if b'"auth_required":true' in body and b"</body>" in body:
            new_body = body.replace(b"</body>", _AUTOFILL_JS.encode() + b"</body>", 1)
            resp.body = new_body
            resp.headers["content-length"] = str(len(new_body))
    except Exception:
        pass
    return resp


_gr_routes.templates.TemplateResponse = _template_response_with_autofill


# launch 时会重算 theme_css，包装后在末尾追加登录页样式（同步更新 hash 以刷新缓存键）
try:
    _orig_set_css = demo._set_html_css_theme_variables

    def _set_css_with_login():
        _orig_set_css()
        demo.theme_css += "\n" + _LOGIN_CSS
        demo.theme_hash = hashlib.sha256(demo.theme_css.encode("utf-8")).hexdigest()

    demo._set_html_css_theme_variables = _set_css_with_login
except AttributeError:
    pass


if __name__ == "__main__":
    launch_kwargs = dict(show_error=True, share=GRADIO_SHARE, server_name="0.0.0.0", allowed_paths=[str(FONT_DIR)])
    if _major >= 6:
        launch_kwargs.update(theme=theme, head=_head_html)
    if APP_PASSWORD:
        launch_kwargs.update(auth=_check_auth, auth_message="请输入访问密码")
    demo.launch(**launch_kwargs)
