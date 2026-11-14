#!/bin/bash
# usage: record.sh <name> <slowdown> "ui command" ...
# Records the client while the commands run and writes build/uitest/rec-<name>.mp4 (no sound).
# Software rendering cannot keep up in real time, so the game runs <slowdown> times slower while recording
# and the video is sped back up afterwards. 5 works well; 20 is the most the game allows.
source "$(dirname "$0")/common.sh"
[ $# -ge 3 ] || { sed -n '2,5p' "$0"; exit 1; }
name=$1; slow=$2; shift 2
raw="$OUT/rec-$name-raw.mkv"
out="$OUT/rec-$name.mp4"

"$UITEST/rcon.py" "tick rate $(python3 -c "print(20.0 / $slow)")" > /dev/null || exit 1
"$UITEST/ui.sh" "timescale $(python3 -c "print(1.0 / $slow)")" "wait 4" || exit 1
ffmpeg -loglevel error -y -f x11grab -draw_mouse 0 -video_size 1280x720 -framerate 15 -i "$DISPLAY" \
    -c:v libx264 -preset ultrafast -crf 12 -pix_fmt yuv420p "$raw" &
recorder=$!
sleep 1
"$UITEST/ui.sh" "$@"
kill -INT $recorder; wait $recorder 2> /dev/null
"$UITEST/rcon.py" "tick rate 20" > /dev/null
"$UITEST/ui.sh" "timescale 1"

ffmpeg -loglevel error -y -i "$raw" -vf "setpts=PTS/$slow,fps=30" -an -c:v libx264 -preset slow -crf 21 \
    -pix_fmt yuv420p -movflags +faststart "$out" || exit 1
rm -f "$raw"
echo "$out ($(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out") s)"
