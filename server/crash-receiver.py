#!/usr/bin/env python3
"""Приём отчётов о сбоях APEX VPN (POST /apexvpn/crash, text/plain).

Слушает только 127.0.0.1 — снаружи доступен через Caddy (https).
Отчёты: /var/lib/apexvpn/crashes/<время>.txt, хранятся последние 500.
Посмотреть: apex crashes
"""
import os
import time
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIR = os.environ.get("APEX_CRASH_DIR", "/var/lib/apexvpn/crashes")
PORT = int(os.environ.get("APEX_CRASH_PORT", "8787"))
MAX_BODY = 64 * 1024
KEEP = 500
PER_IP_PER_HOUR = 30

hits = defaultdict(deque)


def allowed(ip: str) -> bool:
    now = time.time()
    q = hits[ip]
    while q and now - q[0] > 3600:
        q.popleft()
    if len(q) >= PER_IP_PER_HOUR:
        return False
    q.append(now)
    return True


def cleanup() -> None:
    files = sorted(f for f in os.listdir(DIR) if f.endswith(".txt"))
    for f in files[:-KEEP]:
        try:
            os.remove(os.path.join(DIR, f))
        except OSError:
            pass


class Handler(BaseHTTPRequestHandler):
    server_version = "apex-crash"

    def _reply(self, code: int, text: str) -> None:
        body = text.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):  # noqa: N802
        if self.path.rstrip("/") not in ("/apexvpn/crash", "/crash"):
            return self._reply(404, "not found")
        ip = self.headers.get("X-Forwarded-For", self.client_address[0]).split(",")[0].strip()
        if not allowed(ip):
            return self._reply(429, "too many")
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY:
            return self._reply(400, "bad size")
        text = self.rfile.read(length).decode("utf-8", "replace").strip()
        if not text:
            return self._reply(400, "empty")
        name = time.strftime("%Y%m%d-%H%M%S") + f"-{int(time.time() * 1000) % 1000:03d}.txt"
        with open(os.path.join(DIR, name), "w", encoding="utf-8") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S %Z") + "\n" + text + "\n")
        cleanup()
        self._reply(200, "ok")

    def do_GET(self):  # noqa: N802
        self._reply(405, "POST only")

    def log_message(self, fmt, *args):  # тише в журнале
        pass


if __name__ == "__main__":
    os.makedirs(DIR, exist_ok=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
