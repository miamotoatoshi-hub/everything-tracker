#!/usr/bin/env python3
"""Browser test session server: shows the emulator's screen on a phone-friendly page and turns taps,
swipes and typed text into Android input. Used by live-session.sh; needs adb and Pillow.

    live-server.py <port> <token>      (the page is served at /?k=<token>)
"""
import base64
import hashlib
import io
import json
import os
import struct
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from PIL import Image

PORT, TOKEN = int(sys.argv[1]), sys.argv[2]
PKG = os.environ.get("LIVE_PACKAGE", "com.example.trackly")
RECEIVER = f"{PKG}/com.example.trackly.debug.LiveSessionReceiver"
OUT_WIDTH = 720
KEYS = {"back": 4, "home": 3, "recents": 187, "enter": 66, "delete": 67}

state = {"id": 0, "jpeg": b"", "w": 1080, "h": 2400, "last_seen": 0.0}
cond = threading.Condition()


def grab():
    """One frame: raw RGBA from screencap (faster than PNG on the device), scaled down to a JPEG."""
    raw = subprocess.run(["adb", "exec-out", "screencap"], capture_output=True, timeout=15).stdout
    if len(raw) < 12:
        return None
    w, h = struct.unpack_from("<II", raw, 0)
    size = w * h * 4
    if not w or not h or len(raw) < size + 12:
        return None
    img = Image.frombuffer("RGBA", (w, h), raw[len(raw) - size:], "raw", "RGBA", 0, 1).convert("RGB")
    img = img.resize((OUT_WIDTH, round(h * OUT_WIDTH / w)), Image.BILINEAR)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=72)
    return w, h, buf.getvalue()


def capture_loop():
    last = None
    while True:
        if time.time() - state["last_seen"] > 20:  # nobody is watching
            time.sleep(0.3)
            continue
        try:
            res = grab()
        except Exception as e:  # adb hiccup: try again
            print("capture failed:", e, flush=True)
            time.sleep(1)
            continue
        if res:
            w, h, jpeg = res
            digest = hashlib.md5(jpeg).digest()
            if digest != last:
                last = digest
                with cond:
                    state.update(id=state["id"] + 1, jpeg=jpeg, w=w, h=h)
                    cond.notify_all()
        time.sleep(0.12)


class Shell:
    """One long-lived `adb shell`: commands run in order without a new adb connection each time."""

    def __init__(self):
        self.proc = None
        self.lock = threading.Lock()

    def run(self, cmd):
        with self.lock:
            if self.proc is None or self.proc.poll() is not None:
                self.proc = subprocess.Popen(["adb", "shell"], stdin=subprocess.PIPE,
                                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, text=True)
            try:
                self.proc.stdin.write(cmd + "\n")
                self.proc.stdin.flush()
            except OSError:
                self.proc = None


shell = Shell()


def to_px(value, size):
    try:
        return max(0, min(size - 1, round(float(value) * size)))
    except (TypeError, ValueError):
        return 0


def handle_input(data):
    w, h = state["w"], state["h"]
    kind = data.get("type")
    if kind == "tap":
        shell.run(f"input tap {to_px(data.get('x'), w)} {to_px(data.get('y'), h)}")
    elif kind == "swipe":
        ms = int(max(50, min(2000, float(data.get("ms", 300)))))
        shell.run("input swipe {} {} {} {} {}".format(
            to_px(data.get("x1"), w), to_px(data.get("y1"), h),
            to_px(data.get("x2"), w), to_px(data.get("y2"), h), ms))
    elif kind == "key" and data.get("key") in KEYS:
        shell.run(f"input keyevent {KEYS[data['key']]}")
    elif kind == "text":
        text = str(data.get("text", ""))[:500]
        if text:
            # Any language: the app's debug receiver puts the text on the clipboard, then it is pasted.
            encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
            shell.run(f"am broadcast -f 0x20 -n {RECEIVER} --es text64 {encoded} > /dev/null; input keyevent 279")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, code, body=b"", ctype="text/plain; charset=utf-8", headers=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def authorized(self, query):
        return query.get("k", [""])[0] == TOKEN

    def do_GET(self):
        url = urlparse(self.path)
        query = parse_qs(url.query)
        if not self.authorized(query):
            return self.send(403, "Ссылка устарела или неверна.".encode())
        if url.path == "/":
            return self.send(200, PAGE.encode(), "text/html; charset=utf-8")
        if url.path == "/frame":
            state["last_seen"] = time.time()
            try:
                after = int(query.get("after", ["0"])[0])
            except ValueError:
                after = 0
            with cond:
                cond.wait_for(lambda: state["id"] > after and state["jpeg"], timeout=8)
                frame_id, jpeg = state["id"], state["jpeg"]
            if frame_id <= after or not jpeg:
                return self.send(204, headers={"X-Frame": str(frame_id)})
            return self.send(200, jpeg, "image/jpeg", {"X-Frame": str(frame_id)})
        return self.send(404)

    def do_POST(self):
        url = urlparse(self.path)
        if url.path != "/input" or not self.authorized(parse_qs(url.query)):
            return self.send(403)
        length = min(int(self.headers.get("Content-Length") or 0), 10000)
        try:
            data = json.loads(self.rfile.read(length) or b"{}")
        except ValueError:
            return self.send(400)
        state["last_seen"] = time.time()
        handle_input(data if isinstance(data, dict) else {})
        return self.send(204)


PAGE = """<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="theme-color" content="#0e0e14">
<title>Trackly — тест</title>
<style>
html,body{margin:0;height:100%;background:#0e0e14;color:#fff;font:15px -apple-system,system-ui,sans-serif;
  overflow:hidden;overscroll-behavior:none;-webkit-user-select:none;user-select:none}
#wrap{position:fixed;inset:0;display:flex;flex-direction:column;
  padding:env(safe-area-inset-top) 0 env(safe-area-inset-bottom)}
#stage{flex:1;min-height:0;display:flex;align-items:center;justify-content:center;position:relative;padding:6px}
#screen{max-width:100%;max-height:100%;display:block;border-radius:14px;touch-action:none;
  -webkit-touch-callout:none;-webkit-user-drag:none}
#bar{display:flex;gap:8px;padding:8px 10px 10px;justify-content:center}
#bar button{flex:1;max-width:120px;height:46px;border:0;border-radius:14px;background:#262635;color:#fff;
  font:600 15px -apple-system,system-ui,sans-serif;touch-action:manipulation}
#bar button:active{background:#3d3d58}
#status{position:absolute;top:14px;left:50%;transform:translateX(-50%);background:rgba(20,20,30,.85);
  padding:8px 14px;border-radius:20px;font-size:14px;white-space:nowrap}
.dot{position:absolute;width:40px;height:40px;margin:-20px 0 0 -20px;border-radius:50%;
  background:rgba(124,124,248,.5);pointer-events:none;animation:fade .45s ease-out forwards}
@keyframes fade{to{opacity:0;transform:scale(1.7)}}
</style>
</head>
<body>
<div id="wrap">
  <div id="stage"><img id="screen" alt=""><div id="status">Подключение…</div></div>
  <div id="bar">
    <button data-key="back">◀ Назад</button>
    <button data-key="home">● Домой</button>
    <button id="type">⌨ Текст</button>
  </div>
</div>
<script>
const k = new URLSearchParams(location.search).get('k');
const img = document.getElementById('screen');
const stage = document.getElementById('stage');
const statusEl = document.getElementById('status');
let frame = 0, objectUrl = null, start = null;

const sleep = ms => new Promise(r => setTimeout(r, ms));
function show(msg) { statusEl.textContent = msg; statusEl.style.display = msg ? 'block' : 'none'; }

async function stream() {
  for (;;) {
    try {
      const r = await fetch('frame?k=' + encodeURIComponent(k) + '&after=' + frame, {cache: 'no-store'});
      if (r.status === 200) {
        const blob = await r.blob();
        frame = +r.headers.get('X-Frame') || frame + 1;
        const u = URL.createObjectURL(blob);
        img.src = u;
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        objectUrl = u;
        show('');
      } else if (r.status === 204) {
        const id = +r.headers.get('X-Frame');
        if (!isNaN(id) && id < frame) frame = id;
      } else {
        show('Сессия закончилась'); await sleep(4000);
      }
    } catch (e) {
      show('Нет связи, переподключаюсь…'); await sleep(1500);
    }
  }
}

function send(o) {
  fetch('input?k=' + encodeURIComponent(k), {method: 'POST', body: JSON.stringify(o)}).catch(() => {});
}

function ripple(x, y) {
  const s = stage.getBoundingClientRect(), d = document.createElement('div');
  d.className = 'dot'; d.style.left = (x - s.left) + 'px'; d.style.top = (y - s.top) + 'px';
  stage.appendChild(d); setTimeout(() => d.remove(), 500);
}

img.addEventListener('pointerdown', e => {
  e.preventDefault();
  try { img.setPointerCapture(e.pointerId); } catch (_) {}
  start = {x: e.clientX, y: e.clientY, t: performance.now()};
  ripple(e.clientX, e.clientY);
});
img.addEventListener('pointerup', e => {
  if (!start) return;
  const r = img.getBoundingClientRect();
  const nx = v => (v - r.left) / r.width, ny = v => (v - r.top) / r.height;
  const moved = Math.hypot(e.clientX - start.x, e.clientY - start.y), ms = performance.now() - start.t;
  if (moved < 12) {
    if (ms < 450) send({type: 'tap', x: nx(start.x), y: ny(start.y)});
    else send({type: 'swipe', x1: nx(start.x), y1: ny(start.y), x2: nx(start.x), y2: ny(start.y), ms: Math.min(ms, 1500)});
  } else {
    send({type: 'swipe', x1: nx(start.x), y1: ny(start.y), x2: nx(e.clientX), y2: ny(e.clientY),
          ms: Math.max(120, Math.min(ms, 1200))});
  }
  start = null;
});
img.addEventListener('pointercancel', () => { start = null; });
img.addEventListener('contextmenu', e => e.preventDefault());
document.addEventListener('touchmove', e => e.preventDefault(), {passive: false});

document.querySelectorAll('[data-key]').forEach(b => b.addEventListener('click', () => send({type: 'key', key: b.dataset.key})));
document.getElementById('type').addEventListener('click', () => {
  const t = prompt('Сначала нажмите на поле ввода в приложении. Что вписать?');
  if (t) send({type: 'text', text: t});
});

stream();
</script>
</body>
</html>
"""


if __name__ == "__main__":
    threading.Thread(target=capture_loop, daemon=True).start()
    ThreadingHTTPServer.daemon_threads = True
    print(f"live server on :{PORT}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
