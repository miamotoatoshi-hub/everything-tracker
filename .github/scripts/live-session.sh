#!/usr/bin/env bash
# Interactive test session: the emulator's screen on a phone-friendly web page.
#
# Runs inside android-emulator-runner (emulator booted, no window). live-server.py streams the screen
# and turns taps, swipes and text into Android input; a Cloudflare quick tunnel gives it a temporary
# https://….trycloudflare.com address. The link (with its access token) is encrypted with
# .github/live-session.pub.pem before it leaves the job, because this repository's checks are public.
set -u
PKG=$(sed -n 's/^TRACKLY_APPLICATION_ID=//p' gradle.properties)
export LIVE_PACKAGE="$PKG"
RECEIVER="$PKG/com.example.trackly.debug.LiveSessionReceiver"
MINUTES="${LIVE_MINUTES:-60}"

adb shell settings put global hide_error_dialogs 1 || true
adb shell svc power stayon true || true
adb shell settings put system screen_off_timeout 2147483647 || true
# Back / Home / Recents buttons instead of gestures.
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
# Android's own keyboard stays available for tapping its keys.
adb shell settings put secure show_ime_with_hard_keyboard 1 || true
adb shell input keyevent 82 || true

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 || true
sleep 5
# The same switch as Settings > Language, through the debug build's receiver.
adb shell am broadcast -f 0x20 -n "$RECEIVER" --es locale "${LIVE_LOCALE:-ru}" || true

TOKEN=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
python3 .github/scripts/live-server.py 8080 "$TOKEN" > /tmp/live-server.log 2>&1 &
sleep 2

curl -fsSL -o /tmp/cloudflared \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x /tmp/cloudflared
/tmp/cloudflared tunnel --no-autoupdate --url http://127.0.0.1:8080 > /tmp/cloudflared.log 2>&1 &

URL=""
for _ in $(seq 1 90); do
  URL=$(grep -oE 'https://[-a-z0-9]+\.trycloudflare\.com' /tmp/cloudflared.log | head -n 1)
  [ -n "$URL" ] && break
  sleep 2
done
if [ -z "$URL" ]; then
  echo "The tunnel did not start:"; cat /tmp/cloudflared.log; exit 1
fi
LINK="$URL/?k=$TOKEN"
for _ in $(seq 1 45); do curl -fsS -o /dev/null "$LINK" && { echo "Page reachable through the tunnel."; break; }; sleep 2; done

SECRET=$(printf '%s' "$LINK" \
  | openssl pkeyutl -encrypt -pubin -inkey .github/live-session.pub.pem -pkeyopt rsa_padding_mode:oaep \
  | base64 -w0)
gh api "repos/$GITHUB_REPOSITORY/check-runs" \
  -f name="Live session link" -f head_sha="$GITHUB_SHA" -f status=completed -f conclusion=neutral \
  -f "output[title]=Live session (encrypted link)" -f "output[summary]=$SECRET" > /dev/null
echo "Live session is up for $MINUTES minutes; the encrypted link is in the 'Live session link' check."

# A small picture of what the page shows (fetched through the tunnel, like a visitor would), so whoever
# started the session can check it without the link.
sleep 5
if curl -fsS -o /tmp/frame.jpg "$URL/frame?k=$TOKEN&after=0"; then
  python3 - <<'EOF'
from PIL import Image
Image.open('/tmp/frame.jpg').resize((300, 667)).save('/tmp/small.jpg', quality=55)
EOF
  gh api "repos/$GITHUB_REPOSITORY/check-runs" \
    -f name="Live session screen" -f head_sha="$GITHUB_SHA" -f status=completed -f conclusion=neutral \
    -f "output[title]=Screen (base64 JPEG)" -f "output[summary]=What the page showed after start" \
    -f "output[text]=$(base64 -w0 /tmp/small.jpg)" > /dev/null || true
else
  echo "Could not fetch a frame through the tunnel"; tail -n 20 /tmp/live-server.log
fi

sleep $((MINUTES * 60))
echo "--- server log"; tail -n 50 /tmp/live-server.log
