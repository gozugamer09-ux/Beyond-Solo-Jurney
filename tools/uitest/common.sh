#!/bin/bash
# Shared settings and helpers for the uitest scripts. The other scripts source this file; it does nothing on its own.

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
UITEST="$REPO/tools/uitest"
OUT="${UITEST_OUT:-$REPO/build/uitest}"            # logs and recordings (build/ is git-ignored)
export DISPLAY="${UITEST_DISPLAY:-:99}"             # the virtual screen the client draws on
MAX_SECONDS="${UITEST_MAX_SECONDS:-20000}"          # a forgotten server or client stops by itself after this long
PLAYER=Dev                                          # the name the uiTest client joins with (see build.gradle)
SERVER_DIR="$REPO/runs/server"
CLIENT_DIR="$REPO/runs/client"
SERVER_LOG="$OUT/server.log"
CLIENT_LOG="$OUT/client.log"
mkdir -p "$OUT"

# Prints the command line of process $1, or nothing if it has gone.
cmdline() { { tr '\0' ' ' < "/proc/$1/cmdline"; } 2> /dev/null; }

has() { case "$1" in *"$2"*) return 0 ;; esac; return 1; }

# The game itself, as Gradle started it from this checkout. $1 is "client" or "server".
# This and gradle_pids only ever match Java processes, so a script can never stop the shell that called it.
game_pids() {
    local pid cmd
    for pid in $(pgrep -x java); do
        cmd=$(cmdline "$pid")
        has "$cmd" "$REPO/" && has "$cmd" "${1}RunVmArgs.txt" && echo "$pid"
    done
}

# The Gradle launcher that is running task $1 (runClient or runServer) for this checkout.
gradle_pids() {
    local pid cmd
    for pid in $(pgrep -x java); do
        cmd=$(cmdline "$pid")
        has "$cmd" "$REPO/gradle/wrapper/" && has "$cmd" " $1 " && echo "$pid"
    done
}

display_pids() {
    local pid
    for pid in $(pgrep -x Xvfb); do
        has "$(cmdline "$pid")" "Xvfb $DISPLAY " && echo "$pid"
    done
}

# Starts the virtual screen unless it is already there. The size matches the window size in build.gradle.
ensure_display() {
    [ -n "$(display_pids)" ] && return
    (Xvfb "$DISPLAY" -screen 0 1280x720x24 -nolisten tcp > "$OUT/xvfb.log" 2>&1 &)
    sleep 1
}

stop_client() {
    local pid
    for pid in $(gradle_pids runClient) $(game_pids client); do kill "$pid" 2> /dev/null; done
    for _ in $(seq 1 20); do [ -z "$(gradle_pids runClient)$(game_pids client)" ] && return; sleep 0.5; done
    for pid in $(gradle_pids runClient) $(game_pids client); do kill -9 "$pid" 2> /dev/null; done
}

# wait_for <file> <text> <seconds> [text that means it failed]
wait_for() {
    local waited
    for ((waited = 0; waited < $3; waited += 2)); do
        grep -aq "$2" "$1" 2> /dev/null && return 0
        [ -n "$4" ] && grep -aq "$4" "$1" 2> /dev/null && return 1
        sleep 2
    done
    return 1
}
