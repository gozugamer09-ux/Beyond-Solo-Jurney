#!/bin/bash
# Stops the client, the server (it saves the world first) and the virtual screen.
source "$(dirname "$0")/common.sh"

stop_client
if [ -n "$(game_pids server)" ]; then
    "$UITEST/rcon.py" stop > /dev/null 2>&1
    for _ in $(seq 1 60); do [ -z "$(game_pids server)" ] && break; sleep 1; done
fi
for pid in $(gradle_pids runServer) $(game_pids server) $(display_pids); do kill "$pid" 2> /dev/null; done
echo "stopped"
