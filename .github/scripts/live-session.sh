#!/usr/bin/env bash
# Interactive test session: the emulator's screen in any browser.
#
# Runs inside android-emulator-runner (emulator already booted, its window on the Xvfb display $DISPLAY).
# x11vnc shares that display, noVNC turns it into a web page, and a Cloudflare quick tunnel gives the
# page a temporary https://….trycloudflare.com address. The address and the VNC password are
# encrypted with .github/live-session.pub.pem before they leave the job, because this repository's
# check runs and logs are public.
set -u
PKG=com.example.trackly
MINUTES="${LIVE_MINUTES:-60}"

adb shell settings put global hide_error_dialogs 1 || true
adb shell svc power stayon true || true
adb shell settings put system screen_off_timeout 2147483647 || true
# Back / Home / Recents buttons are much easier to use with a mouse than gesture navigation.
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
adb shell input keyevent 82 || true

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd locale set-app-locales "$PKG" --locales "${LIVE_LOCALE:-ru-RU}" || true
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null || true

PASS=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 8)
mkdir -p "$HOME/.vnc"
x11vnc -storepasswd "$PASS" "$HOME/.vnc/passwd" > /dev/null
x11vnc -display "$DISPLAY" -rfbauth "$HOME/.vnc/passwd" -forever -shared -noxdamage -rfbport 5900 \
  -bg -quiet -o /tmp/x11vnc.log
websockify --web /usr/share/novnc 6080 localhost:5900 > /tmp/websockify.log 2>&1 &

curl -fsSL -o /tmp/cloudflared \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x /tmp/cloudflared
/tmp/cloudflared tunnel --no-autoupdate --url http://localhost:6080 > /tmp/cloudflared.log 2>&1 &

URL=""
for _ in $(seq 1 90); do
  URL=$(grep -oE 'https://[-a-z0-9]+\.trycloudflare\.com' /tmp/cloudflared.log | head -n 1)
  [ -n "$URL" ] && break
  sleep 2
done
if [ -z "$URL" ]; then
  echo "The tunnel did not start:"; cat /tmp/cloudflared.log; exit 1
fi
# Wait until the page is reachable through the tunnel before handing the link out.
for _ in $(seq 1 30); do curl -fsS -o /dev/null "$URL/vnc.html" && break; sleep 2; done

LINK="$URL/vnc.html?autoconnect=1&resize=scale&reconnect=1&password=$PASS"
SECRET=$(printf '%s' "$LINK" \
  | openssl pkeyutl -encrypt -pubin -inkey .github/live-session.pub.pem -pkeyopt rsa_padding_mode:oaep \
  | base64 -w0)
gh api "repos/$GITHUB_REPOSITORY/check-runs" \
  -f name="Live session link" -f head_sha="$GITHUB_SHA" -f status=completed -f conclusion=neutral \
  -f "output[title]=Live session (encrypted link)" -f "output[summary]=$SECRET" > /dev/null
echo "Live session is up for $MINUTES minutes; the encrypted link is in the 'Live session link' check."

sleep $((MINUTES * 60))
