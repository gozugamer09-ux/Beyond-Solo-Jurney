#!/bin/bash
# usage: ui.sh "command" "command" ...
# Runs UiTestDriver commands in the dev client, in order, and returns when they have all finished.
# The commands are listed in the class comment of client/UiTestDriver. "shot name" saves
# runs/client/screenshots/name.png.
source "$(dirname "$0")/common.sh"
[ $# -gt 0 ] || { sed -n '2,5p' "$0"; exit 1; }
[ -n "$(game_pids client)" ] || { echo "The dev client is not running. Use tools/uitest/start.sh."; exit 1; }

shots=()
for command in "$@"; do
    case "$command" in "shot "*) shots+=("$CLIENT_DIR/screenshots/${command#shot }.png") ;; esac
done
[ ${#shots[@]} -gt 0 ] && rm -f "${shots[@]}"

before=$(grep -ac "UITEST idle" "$CLIENT_LOG")
printf '%s\n' "$@" > "$CLIENT_DIR/uitest/cmd.tmp"
mv "$CLIENT_DIR/uitest/cmd.tmp" "$CLIENT_DIR/uitest/cmd.txt"
for ((waited = 0; waited < 1200; waited++)); do
    if [ "$(grep -ac "UITEST idle" "$CLIENT_LOG")" -gt "$before" ]; then
        # Screenshots are written in the background; give them a moment to land.
        for shot in "${shots[@]}"; do
            for _ in 1 2 3 4 5 6 7 8 9 10; do [ -s "$shot" ] && break; sleep 0.5; done
        done
        [ ${#shots[@]} -gt 0 ] && sleep 0.5
        exit 0
    fi
    sleep 0.5
done
echo "Timed out waiting for the client, see $CLIENT_LOG"
exit 1
