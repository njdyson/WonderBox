#!/usr/bin/env bash
# Installs or updates the Wonder Box server: PocketBase as a systemd service listening
# on 127.0.0.1 only. Plesk's nginx then serves it publicly over HTTPS (see server/README.md).
#
# With Plesk Git (recommended): point APP_DIR at the folder Plesk deploys the repo to.
# PocketBase then runs straight from that folder, so each Git deploy updates the app:
# hook changes restart it automatically, and the phone app files are served as they are.
#
#   sudo APP_DIR=/var/www/vhosts/psybob.uk/wonder.psybob.uk bash install.sh
#
# Without APP_DIR the script clones the repo itself into /opt/wonderbox (re-run to update).
#
# It can also run non-interactively, e.g. as a Plesk Scheduled Task (Run Now) as root:
# set ADMIN_EMAIL and ADMIN_PASSWORD to create the dashboard admin on first install.
# Optionally set YOUTUBE_API_KEY: the phone app then also refuses age-restricted and
# region-blocked videos when they're added, and PIXABAY_API_KEY for photo search in the quiz
# picture picker, and GEMINI_API_KEY for the TV's story maker (GEMINI_MODEL to pick another
# model than story_lib.js's default), all kept in <data>/wonderbox.env, not in Git.
#
# It also installs Piper and a British voice (about 90 MB) so the server can read questions
# aloud for TVs whose own text-to-speech is missing or poor. Set VOICE=0 to skip that.
#
# And ffmpeg, which makes the home videos parents send from the phone app playable on the TVs:
# the system's own if it has one, otherwise a static build (about 80 MB) in /opt/wonderbox/ffmpeg.
#
# Re-running is safe: the database is never touched except to apply new migrations.
set -euo pipefail
# Scheduled tasks start with a minimal PATH.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export DEBIAN_FRONTEND=noninteractive

PB_VERSION="${PB_VERSION:-0.40.6}"
PORT="${PORT:-8096}"
APP_DIR="${APP_DIR:-}"
REPO="${REPO:-https://github.com/njdyson/WonderBox.git}"
BRANCH="${BRANCH:-main}"
BIN_DIR=/opt/wonderbox
SERVICE=wonderbox

[ "$(id -u)" = 0 ] || { echo "Please run as root (sudo bash install.sh)"; exit 1; }
for tool in git unzip curl; do
  command -v "$tool" >/dev/null || apt-get install -y "$tool"
done
mkdir -p "$BIN_DIR"

if [ -n "$APP_DIR" ]; then
  # Plesk Git mode: run as the subscription's user, who owns the deployed files.
  [ -d "$APP_DIR/server/pb_hooks" ] || { echo "$APP_DIR doesn't look like the deployed repo (no server/pb_hooks). Check the Git deployment path in Plesk."; exit 1; }
  SRC="$APP_DIR/server"
  RUN_AS=$(stat -c %U "$APP_DIR")
  DATA="${DATA:-$(dirname "$APP_DIR")/wonderbox-data}"
else
  # Standalone mode: copy the app from GitHub into /opt/wonderbox.
  id "$SERVICE" >/dev/null 2>&1 || useradd --system --home "$BIN_DIR" --shell /usr/sbin/nologin "$SERVICE"
  echo "Fetching Wonder Box ($BRANCH)…"
  tmp=$(mktemp -d)
  git clone --quiet --depth 1 --branch "$BRANCH" "$REPO" "$tmp"
  rm -rf "$BIN_DIR/app"
  mkdir -p "$BIN_DIR/app"
  cp -r "$tmp/server/pb_hooks" "$tmp/server/pb_migrations" "$tmp/server/pb_public" "$BIN_DIR/app/"
  rm -rf "$tmp"
  SRC="$BIN_DIR/app"
  RUN_AS=$SERVICE
  DATA="${DATA:-$BIN_DIR/pb_data}"
  chown -R "$SERVICE:$SERVICE" "$BIN_DIR/app"
fi

# The database lives outside the deployed code, so deploys never touch it.
mkdir -p "$DATA"
chown "$RUN_AS" "$DATA"

# Server-only settings, read by the service. Each key given is saved; running again without
# one keeps the one already saved.
ENV_FILE="$DATA/wonderbox.env"
save_key() {
  local name="$1" value="$2"
  [ -n "$value" ] || return 0
  umask 077
  touch "$ENV_FILE"
  grep -v "^$name=" "$ENV_FILE" > "$ENV_FILE.new" || true
  echo "$name=$value" >> "$ENV_FILE.new"
  mv "$ENV_FILE.new" "$ENV_FILE"
  umask 022
  echo "Saved $name."
}
save_key YOUTUBE_API_KEY "${YOUTUBE_API_KEY:-}"
save_key PIXABAY_API_KEY "${PIXABAY_API_KEY:-}"
save_key GEMINI_API_KEY "${GEMINI_API_KEY:-}"
save_key GEMINI_MODEL "${GEMINI_MODEL:-}"

# PocketBase itself.
if ! "$BIN_DIR/pocketbase" --version 2>/dev/null | grep -q "$PB_VERSION"; then
  echo "Downloading PocketBase $PB_VERSION…"
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/pb.zip" "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_amd64.zip"
  unzip -o -q "$tmp/pb.zip" pocketbase -d "$BIN_DIR"
  chmod 755 "$BIN_DIR/pocketbase"
  rm -rf "$tmp"
fi

# The server's voice: Piper and one British English voice (Cori, southern English), read by speech.pb.js.
PIPER_VERSION="2023.11.14-2"
VOICE_NAME="en_GB-cori-medium"
VOICE_URL="https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_GB/cori/medium/$VOICE_NAME.onnx"
PIPER_ENV=""
if [ "${VOICE:-1}" != 0 ]; then
  case "$(uname -m)" in
    x86_64) PIPER_ARCH=x86_64 ;;
    aarch64|arm64) PIPER_ARCH=aarch64 ;;
    *) PIPER_ARCH="" ;;
  esac
  if [ -z "$PIPER_ARCH" ]; then
    echo "No Piper build for $(uname -m): the TVs will use their own voices."
  else
    if [ ! -x "$BIN_DIR/piper/piper" ] || [ "$(cat "$BIN_DIR/piper/VERSION" 2>/dev/null)" != "$PIPER_VERSION" ]; then
      echo "Downloading Piper $PIPER_VERSION…"
      tmp=$(mktemp -d)
      curl -fsSL -o "$tmp/piper.tgz" "https://github.com/rhasspy/piper/releases/download/$PIPER_VERSION/piper_linux_$PIPER_ARCH.tar.gz"
      rm -rf "$BIN_DIR/piper"
      tar -xzf "$tmp/piper.tgz" -C "$BIN_DIR"
      echo "$PIPER_VERSION" > "$BIN_DIR/piper/VERSION"
      rm -rf "$tmp"
    fi
    mkdir -p "$BIN_DIR/voices"
    if [ ! -s "$BIN_DIR/voices/$VOICE_NAME.onnx" ] || [ ! -s "$BIN_DIR/voices/$VOICE_NAME.onnx.json" ]; then
      echo "Downloading the voice ($VOICE_NAME)…"
      curl -fsSL -o "$BIN_DIR/voices/$VOICE_NAME.onnx.json" "$VOICE_URL.json"
      curl -fsSL -o "$BIN_DIR/voices/$VOICE_NAME.onnx" "$VOICE_URL"
    fi
    chmod -R a+rX "$BIN_DIR/piper" "$BIN_DIR/voices"
    # Check it really speaks before telling the server it can.
    if echo "Hello" | "$BIN_DIR/piper/piper" --model "$BIN_DIR/voices/$VOICE_NAME.onnx" --output_file /tmp/wonderbox-voice-test.wav >/tmp/wonderbox-voice-test.log 2>&1 \
      && [ -s /tmp/wonderbox-voice-test.wav ]; then
      PIPER_ENV="Environment=PIPER_BIN=$BIN_DIR/piper/piper
Environment=PIPER_VOICE=$BIN_DIR/voices/$VOICE_NAME.onnx"
      echo "The server's voice works."
    else
      echo "Piper didn't run here, so the TVs will use their own voices. What it said:"
      tail -n 15 /tmp/wonderbox-voice-test.log || true
    fi
    rm -f /tmp/wonderbox-voice-test.wav /tmp/wonderbox-voice-test.log
  fi
fi

# ffmpeg for home videos (home_video_lib.js): the system's own, or a static build.
FFMPEG_ENV=""
if command -v ffmpeg >/dev/null && command -v ffprobe >/dev/null; then
  FFMPEG_BIN=$(command -v ffmpeg)
  FFPROBE_BIN=$(command -v ffprobe)
else
  case "$(uname -m)" in
    x86_64) FFMPEG_ARCH=amd64 ;;
    aarch64|arm64) FFMPEG_ARCH=arm64 ;;
    *) FFMPEG_ARCH="" ;;
  esac
  FFMPEG_BIN="$BIN_DIR/ffmpeg/ffmpeg"
  FFPROBE_BIN="$BIN_DIR/ffmpeg/ffprobe"
  if [ -n "$FFMPEG_ARCH" ] && [ ! -x "$FFMPEG_BIN" ]; then
    echo "Downloading ffmpeg for home videos…"
    command -v xz >/dev/null || apt-get install -y xz-utils || yum install -y xz || true
    tmp=$(mktemp -d)
    if curl -fsSL -o "$tmp/ffmpeg.tar.xz" "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-$FFMPEG_ARCH-static.tar.xz" \
      && tar -xJf "$tmp/ffmpeg.tar.xz" -C "$tmp"; then
      rm -rf "$BIN_DIR/ffmpeg"
      mkdir -p "$BIN_DIR/ffmpeg"
      cp "$tmp"/ffmpeg-*-static/ffmpeg "$tmp"/ffmpeg-*-static/ffprobe "$BIN_DIR/ffmpeg/"
      chmod 755 "$BIN_DIR/ffmpeg/ffmpeg" "$BIN_DIR/ffmpeg/ffprobe"
    fi
    rm -rf "$tmp"
  fi
fi
if "$FFMPEG_BIN" -hide_banner -version >/dev/null 2>&1 && "$FFPROBE_BIN" -hide_banner -version >/dev/null 2>&1; then
  FFMPEG_ENV="Environment=FFMPEG_BIN=$FFMPEG_BIN
Environment=FFPROBE_BIN=$FFPROBE_BIN"
  echo "ffmpeg works, so home videos can be added: $("$FFMPEG_BIN" -hide_banner -version | head -n 1)"
else
  echo "ffmpeg isn't working, so home videos can't be added yet. Install it (apt-get install ffmpeg) and run this again."
fi
mkdir -p "$DATA/home-videos"
chown "$RUN_AS" "$DATA/home-videos"

PB_ARGS="--dir=$DATA --hooksDir=$SRC/pb_hooks --migrationsDir=$SRC/pb_migrations --publicDir=$SRC/pb_public"

cat > "/etc/systemd/system/$SERVICE.service" <<UNIT
[Unit]
Description=Wonder Box server (PocketBase)
After=network.target

[Service]
User=$RUN_AS
WorkingDirectory=$DATA
EnvironmentFile=-$ENV_FILE
$PIPER_ENV
$FFMPEG_ENV
ExecStart=$BIN_DIR/pocketbase serve --http=127.0.0.1:$PORT $PB_ARGS
Restart=always
RestartSec=5
LimitNOFILE=4096

[Install]
WantedBy=multi-user.target
UNIT

# Plesk Git mode: restart the server whenever a deploy changes its API code or migrations.
# PocketBase's own reload on hook changes can catch a deploy halfway and leave the
# /api/wonder routes missing (the phone app then looks empty), so a clean restart
# follows a few seconds after the files stop changing. Restarting also applies new migrations.
if [ -n "$APP_DIR" ]; then
  cat > "/etc/systemd/system/$SERVICE-redeploy.path" <<UNIT
[Unit]
Description=Restart Wonder Box when a deploy changes its code

[Path]
PathChanged=$SRC/pb_hooks
PathChanged=$SRC/pb_migrations
Unit=$SERVICE-redeploy.service

[Install]
WantedBy=multi-user.target
UNIT
  cat > "/etc/systemd/system/$SERVICE-redeploy.service" <<UNIT
[Unit]
Description=Restart Wonder Box after a deploy

[Service]
Type=oneshot
ExecStart=/bin/sh -c 'sleep 10; systemctl restart $SERVICE'
UNIT
else
  systemctl disable --now --quiet "$SERVICE-redeploy.path" 2>/dev/null || true
  rm -f "/etc/systemd/system/$SERVICE-redeploy.path" "/etc/systemd/system/$SERVICE-redeploy.service"
fi

# Apply any new database migrations, then (re)start.
sudo -u "$RUN_AS" "$BIN_DIR/pocketbase" migrate up $PB_ARGS
systemctl daemon-reload
if [ -n "$APP_DIR" ]; then
  systemctl enable --quiet "$SERVICE-redeploy.path"
  systemctl restart "$SERVICE-redeploy.path"
fi
systemctl enable --quiet "$SERVICE"
systemctl restart "$SERVICE"
sleep 2
if systemctl is-active --quiet "$SERVICE"; then
  echo "Wonder Box is running on 127.0.0.1:$PORT as $RUN_AS (data in $DATA)"
else
  echo "The service didn't start. See: journalctl -u $SERVICE -n 50"
  exit 1
fi

# First install: create the admin account for the dashboard (https://<your domain>/_/).
if [ ! -f "$DATA/.admin-created" ]; then
  email="${ADMIN_EMAIL:-}"
  password="${ADMIN_PASSWORD:-}"
  if [ -z "$email" ] && [ -t 0 ]; then
    read -rp "Admin email for the PocketBase dashboard: " email
    read -rsp "Admin password (10+ characters): " password; echo
  fi
  if [ -n "$email" ]; then
    sudo -u "$RUN_AS" "$BIN_DIR/pocketbase" superuser upsert "$email" "$password" --dir="$DATA"
    sudo -u "$RUN_AS" touch "$DATA/.admin-created"
    echo "Dashboard admin $email created."
  else
    echo "No dashboard admin yet: run again with ADMIN_EMAIL and ADMIN_PASSWORD set."
  fi
fi
echo "Done."
