"""极简 OpenAI 兼容 mock 服务，用于本地验证。

两种应答：
- 请求里带图片 → 返回截图分析用的结构化 JSON（验证 M4 闭环）
- 纯文本请求 → 返回普通文本（验证 M3 对话）

模拟器里用 http://10.0.2.2:8999/v1 访问宿主机的这个端口。
"""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8999

REPLY_CHUNKS = ["收到", "你的消息了", "。这是一段", "流式返回的", "测试文本。"]

# 假装是模型对一张「设置界面截图」的分析结果
ANALYSIS = {
    "summary": "一张手机应用设置界面的截图，显示了截图监听开关与电池优化提示",
    "tags": ["设置", "界面", "教程"],
    "category": "教程",
    "todos": ["把应用加入电池优化白名单", "确认截图监听开关处于开启状态"],
    "keyText": "截图监听已开启 / 建议关闭电池优化",
}

# 纯文本请求的应答（日报叙事用）
TEXT_REPLY = (
    "今天你主要在处理这个笔记应用本身：几条截图都围绕设置界面，"
    "看得出注意力集中在把工具调顺手这件事上。日程里有一项提醒已经响过。"
    "整体节奏偏轻，明天可以挑一条截图里提取出来的待办真正做掉，"
    "比如把应用加进电池优化白名单——那件事拖久了会让截图监听掉线。"
)


def has_image(payload):
    for message in payload.get("messages", []):
        content = message.get("content")
        if isinstance(content, list):
            if any(part.get("type") == "image_url" for part in content if isinstance(part, dict)):
                return True
    return False


def is_screenshot_request(payload):
    """没有图片但带截图提示词的，也算截图分析（本地 OCR 兜底那条路）。"""
    for message in payload.get("messages", []):
        content = message.get("content")
        text = content if isinstance(content, str) else json.dumps(content, ensure_ascii=False)
        if "截图" in text:
            return True
    return False


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[mock] " + fmt % args, flush=True)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except Exception:
            payload = {}

        image_attached = has_image(payload)
        print(
            f"[mock] path={self.path} model={payload.get('model')} "
            f"stream={payload.get('stream')} image={image_attached}",
            flush=True,
        )

        if "chat/completions" not in self.path:
            self._json(404, {"error": {"message": "not found"}})
            return

        if payload.get("stream"):
            self._stream()
        elif image_attached or is_screenshot_request(payload):
            self._json(200, {
                "id": "mock-shot-1",
                "model": payload.get("model", "mock"),
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": json.dumps(ANALYSIS, ensure_ascii=False),
                    },
                    "finish_reason": "stop",
                }],
                "usage": {"prompt_tokens": 1120, "completion_tokens": 96, "total_tokens": 1216},
            })
        else:
            self._json(200, {
                "id": "mock-1",
                "model": payload.get("model", "mock"),
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": TEXT_REPLY},
                    "finish_reason": "stop",
                }],
                "usage": {"prompt_tokens": 210, "completion_tokens": 88, "total_tokens": 298},
            })

    def _stream(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        for piece in REPLY_CHUNKS:
            frame = {"choices": [{"index": 0, "delta": {"content": piece}}]}
            self.wfile.write(f"data: {json.dumps(frame, ensure_ascii=False)}\n\n".encode("utf-8"))
            self.wfile.flush()
            time.sleep(0.25)
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[mock] listening on 0.0.0.0:{PORT}", flush=True)
    server.serve_forever()
