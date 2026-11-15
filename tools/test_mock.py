"""自测 mock 服务的流式分支，绕开 shell 引号问题。"""
import json
import urllib.request

URL = "http://127.0.0.1:8999/v1/chat/completions"


def post(payload):
    req = urllib.request.Request(
        URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": "Bearer test"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        body = resp.read().decode("utf-8", "replace")
    print(body)


print("--- stream=True ---")
post({"model": "mock", "stream": True, "messages": [{"role": "user", "content": "hi"}]})

print("\n--- stream=False ---")
post({"model": "mock", "stream": False, "messages": [{"role": "user", "content": "hi"}]})
