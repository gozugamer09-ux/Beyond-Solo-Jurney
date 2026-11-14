#!/bin/bash
# One-time setup of the dev server and the dev client for uitest.
# Safe to run again: it keeps an existing world, seed and RCON password and only fills in what is missing.
set -e
source "$(dirname "$0")/common.sh"

mkdir -p "$SERVER_DIR" "$CLIENT_DIR/uitest"

# The dedicated server does not start without this. By running this script you accept https://aka.ms/MinecraftEULA
if ! grep -qs "^eula=true" "$SERVER_DIR/eula.txt"; then
    echo "eula=true" > "$SERVER_DIR/eula.txt"
    echo "Accepted the Minecraft EULA for the dev server (runs/server/eula.txt)."
fi

props="$SERVER_DIR/server.properties"
touch "$props"
set_prop() {
    if grep -q "^$1=" "$props"; then sed -i "s|^$1=.*|$1=$2|" "$props"; else echo "$1=$2" >> "$props"; fi
}
grep -q "^level-seed=." "$props" || set_prop level-seed 12345
grep -q "^rcon.password=." "$props" || set_prop rcon.password "$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')"
set_prop online-mode false          # the dev client has no account
set_prop server-ip 127.0.0.1        # the game and RCON can only be reached from this machine
set_prop enable-rcon true
set_prop rcon.port 25575
set_prop spawn-protection 0

# Without these the client stops at a first-start screen and waits for a click that never comes.
# The rest keeps the software renderer fast enough: short view distance, GUI scale 2 (640x360), no sound.
if [ ! -f "$CLIENT_DIR/options.txt" ]; then
    cat > "$CLIENT_DIR/options.txt" <<'OPTIONS'
onboardAccessibility:false
narrator:0
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
fov:0.0
renderDistance:6
guiScale:2
soundCategory_master:0.0
OPTIONS
fi

echo "uitest is set up. Next: tools/uitest/start.sh"
