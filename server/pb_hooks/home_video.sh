#!/bin/sh
# Converts one uploaded home video for the TVs, started (in the background) by
# home_video_lib.js:  sh home_video.sh <folder> <ffmpeg> <ffprobe>
#
# The folder holds the upload's pieces, 0.part, 1.part, … in order. This joins them, makes a
# 720p H.264 MP4 with AAC sound (what a Fire Stick plays best, and small: about 15 MB a
# minute), at most 30 frames a second, with phone HDR toned down to normal colours and the
# location and other metadata taken out. Then a still for the TV (poster.jpg), a thumbnail
# (thumb.jpg) and meta.json (duration, width, height). It ends by writing "done", or "failed"
# with the reason; the server's cron job picks either up. Runs niced, on two threads, so the
# web server stays quick while it works.
dir=$1
ffmpeg=${2:-ffmpeg}
ffprobe=${3:-ffprobe}
cd "$dir" || exit 1
echo $$ > pid
rm -f done failed

fail() {
  echo "$1" > failed.tmp
  mv failed.tmp failed
  rm -f source out.tmp.mp4
  exit 1
}

# The pieces, in number order, become one file.
n=0
: > source
while [ -f "$n.part" ]; do
  cat "$n.part" >> source || fail "The server ran out of space."
  n=$((n + 1))
done
[ "$n" -gt 0 ] || fail "Nothing was uploaded."
rm -f ./*.part

"$ffprobe" -v error -select_streams v:0 -show_entries stream=codec_type -of csv=p=0 source 2>/dev/null | grep -q video \
  || fail "That isn't a video the server can read."

# Shorter side at most 720 pixels, never made bigger, even numbers for H.264.
scale="scale='if(gt(iw,ih),-2,trunc(min(720,iw)/2)*2)':'if(gt(iw,ih),trunc(min(720,ih)/2)*2,-2)'"
vf="$scale,format=yuv420p"
# iPhones and newer Androids film in HDR (HLG or PQ), which looks washed out on an ordinary
# picture unless it's tone mapped. Needs ffmpeg's zscale (the static builds have it).
transfer=$("$ffprobe" -v error -select_streams v:0 -show_entries stream=color_transfer -of csv=p=0 source 2>/dev/null)
case "$transfer" in
  arib-std-b67|smpte2084)
    if "$ffmpeg" -hide_banner -filters 2>/dev/null | grep -q ' zscale '; then
      vf="zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,$scale,format=yuv420p"
    fi
    ;;
esac

fps="-fpsmax 30"
"$ffmpeg" -hide_banner -h full 2>/dev/null | grep -q -- '-fpsmax' || fps="-r 30"

# shellcheck disable=SC2086
nice -n 10 "$ffmpeg" -hide_banner -nostdin -y -i source \
  -map 0:v:0 -map '0:a:0?' -map_metadata -1 -map_chapters -1 \
  -vf "$vf" $fps \
  -c:v libx264 -preset veryfast -crf 23 -profile:v high -level:v 4.0 \
  -c:a aac -b:a 128k -ac 2 -ar 48000 \
  -movflags +faststart -threads 2 -f mp4 out.tmp.mp4 > convert.log 2>&1 \
  || fail "The server couldn't convert that video: $(tail -n 3 convert.log | tr '\n' ' ' | cut -c1-300)"
rm -f source
mv out.tmp.mp4 video.mp4

duration=$("$ffprobe" -v error -show_entries format=duration -of csv=p=0 video.mp4 2>/dev/null | awk '{ printf "%.2f", $1 + 0 }')
size=$("$ffprobe" -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0:s=x video.mp4 2>/dev/null)
width=${size%x*}
height=${size#*x}
# The still comes from a second in (or the middle of a very short clip), to skip a dark first frame.
at=$(awk -v d="${duration:-0}" 'BEGIN { t = d / 2; if (t > 1) t = 1; printf "%.2f", t }')
"$ffmpeg" -hide_banner -nostdin -y -ss "$at" -i video.mp4 -frames:v 1 -q:v 3 poster.jpg >> convert.log 2>&1 \
  || "$ffmpeg" -hide_banner -nostdin -y -i video.mp4 -frames:v 1 -q:v 3 poster.jpg >> convert.log 2>&1 \
  || fail "The server couldn't make a picture for that video."
"$ffmpeg" -hide_banner -nostdin -y -i poster.jpg -vf "scale='if(gt(iw,ih),400,-2)':'if(gt(iw,ih),-2,400)'" -q:v 4 thumb.jpg >> convert.log 2>&1 || cp poster.jpg thumb.jpg

printf '{"duration": %s, "width": %s, "height": %s}\n' "${duration:-0}" "${width:-0}" "${height:-0}" > meta.json
touch done
