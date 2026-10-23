#!/usr/bin/env bash
# Joins the scenes recorded by record-demo.mjs into one 1280x720 MP4.
# Phone scenes are centred on the brand-green background.
#   scripts/make-demo-video.sh <folder from record-demo.mjs> <output.mp4>
set -euo pipefail
IN=${1:?folder with raw-* scenes}
OUT=${2:?output mp4}
TMP=$(mktemp -d)
i=0
for dir in "$IN"/raw-*; do
  src=$(ls "$dir"/*.webm | head -1)
  i=$((i + 1))
  ffmpeg -loglevel error -y -i "$src" \
    -vf "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=#14231B,fps=30,format=yuv420p" \
    -c:v libx264 -preset medium -crf 23 -an "$TMP/$(printf '%02d' $i).mp4"
  echo "file '$TMP/$(printf '%02d' $i).mp4'" >> "$TMP/list.txt"
done
ffmpeg -loglevel error -y -f concat -safe 0 -i "$TMP/list.txt" -c copy -movflags +faststart "$OUT"
rm -rf "$TMP"
echo "Wrote $OUT"
