#!/bin/bash
# Starts the virtual screen, the dev server and the dev client, and returns when the client has joined.
# Takes a minute or two. The first run in a new checkout takes much longer, while Gradle sets the game up.
source "$(dirname "$0")/common.sh"
cd "$REPO"
[ -f "$SERVER_DIR/eula.txt" ] || { echo "Run tools/uitest/setup.sh first."; exit 1; }

if [ -z "$(game_pids server)" ]; then
    echo "starting the server..."
    rm -f "$SERVER_LOG"
    (nohup timeout "$MAX_SECONDS" ./gradlew runServer --console=plain < /dev/null > "$SERVER_LOG" 2>&1 &)
    if ! wait_for "$SERVER_LOG" "Done (" 1200 "BUILD FAILED"; then
        echo "The server did not start. Last lines of $SERVER_LOG:"; tail -n 20 "$SERVER_LOG"; exit 1
    fi
fi
exec "$UITEST/client.sh"
