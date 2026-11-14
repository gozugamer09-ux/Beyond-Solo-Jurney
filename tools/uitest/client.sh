#!/bin/bash
# (Re)starts only the dev client and returns when it has joined the running dev server.
# Enough after a change to client code. After a change to server code restart both: stop.sh, then start.sh.
source "$(dirname "$0")/common.sh"
cd "$REPO"
[ -n "$(game_pids server)" ] || { echo "The dev server is not running. Use tools/uitest/start.sh."; exit 1; }

ensure_display
stop_client
mkdir -p "$CLIENT_DIR/uitest"
rm -f "$CLIENT_DIR/uitest/cmd.txt"
joins() { grep -ac "$PLAYER joined the game" "$SERVER_LOG"; }
before=$(joins)

# A library mod (Prickle) sometimes loses a start-up race when the game is run from Gradle. The log then says
# "broken mod state" and the game sits on an error screen. Starting it again fixes it.
for attempt in 1 2 3 4; do
    echo "starting the client (attempt $attempt)..."
    rm -f "$CLIENT_LOG"
    (nohup timeout "$MAX_SECONDS" ./gradlew runClient -PuiTest --console=plain < /dev/null > "$CLIENT_LOG" 2>&1 &)
    for ((waited = 0; waited < 600; waited += 2)); do
        if [ "$(joins)" -gt "$before" ]; then
            # The client's "cmd" runs commands as the player, so the player has to be an operator. This is done
            # after joining and by selector: looking the name up earlier can find a stranger's real account.
            "$UITEST/rcon.py" "op @a[name=$PLAYER]" > /dev/null
            sleep 8    # let the first chunks draw
            echo "the client has joined"
            exit 0
        fi
        grep -aq "broken mod state" "$CLIENT_LOG" && break
        if grep -aq "BUILD FAILED" "$CLIENT_LOG"; then
            echo "The client did not start. Last lines of $CLIENT_LOG:"; tail -n 20 "$CLIENT_LOG"; exit 1
        fi
        sleep 2
    done
    stop_client
    sleep 3
done
echo "The client did not join after 4 attempts, see $CLIENT_LOG"
exit 1
