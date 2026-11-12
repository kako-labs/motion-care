#!/usr/bin/env bash
# Builds the launcher icon layers and the guidance card icon from the generated symbols in
# docs/design-archive/images/app-icon. Needs ImageMagick 7 (`magick`). Run from anywhere:
#
#     bash docs/design-archive/tools/app_icon.sh
#
# Sizes follow the adaptive icon layout: a 108dp layer, of which the launcher mask shows the middle 72dp and
# always keeps the 66dp circle. Each symbol stays inside that circle. Why and how it was checked: docs/design.md.
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
src="$root/docs/design-archive/images/app-icon"
res="$root/app/src/main/res"
tmp="$(mktemp -d)"
trap '/bin/rm -rf "$tmp"' EXIT
png=(-strip -define png:exclude-chunks=date,time -depth 8)

# Background layer at 432 px (xxxhdpi): app navy (MotionStyle Base #030E1F) with a soft glow above the middle.
magick -size 432x432 -define gradient:center=200,150 -define gradient:radii=330,330 \
    radial-gradient:'#0E4C8C'-'#030E1F' "$tmp/background.png"

# Symbol drawn on pure black -> 432 px layer with transparency. Alpha is the brightest channel, so the black goes
# away and the glow fades out; colors are divided by it again so the symbol keeps its brightness.
symbol_layer() { # <source> <scale %> <out>
    magick "$1" -filter Lanczos -resize "$2%" -gravity center -background black -extent 1024x1024 \
        -resize 432x432 -depth 16 "$tmp/raw.png"
    magick "$tmp/raw.png" -fx 'max(max(r,g),b)' -colorspace gray -level 3%,100% "$tmp/alpha.png"
    magick "$tmp/raw.png" -fx 'mx=max(max(r,g),b); mx>0.01 ? min(1,u/mx) : 0' "$tmp/color.png"
    magick "$tmp/color.png" "$tmp/alpha.png" -alpha off -compose CopyOpacity -composite -depth 8 "$3"
}

# Launcher icon: the logo_tight symbol as it was generated.
symbol_layer "$src/logo_tight_symbol.png" 100 "$tmp/foreground.png"
for density in mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432; do
    dir="$res/mipmap-${density%%:*}"
    px="${density##*:}"
    mkdir -p "$dir"
    magick "$tmp/foreground.png" -filter Lanczos -resize "${px}x${px}" "${png[@]}" "$dir/ic_launcher_foreground.png"
    magick "$tmp/background.png" -filter Lanczos -resize "${px}x${px}" "${png[@]}" "$dir/ic_launcher_background.png"
done

# Card icon: the flow symbol, enlarged 25% to fill the 66dp circle (the generated ribbon reached only 77% of it),
# on the same background. The card draws the icon without a mask, so the visible 72dp is cut here with the dock's
# corner radius (about 20%) and kept transparent outside it.
symbol_layer "$src/flow_symbol.png" 125 "$tmp/flow.png"
mkdir -p "$res/drawable-nodpi"
magick "$tmp/background.png" "$tmp/flow.png" -compose over -composite \
    -gravity center -crop 288x288+0+0 +repage -alpha set \
    \( -size 288x288 xc:none -fill white -draw 'roundrectangle 0,0 287,287 58,58' \) -compose DstIn -composite \
    -filter Lanczos -resize 192x192 "${png[@]}" "$res/drawable-nodpi/ic_notification.png"

echo "Wrote mipmap-*/ic_launcher_{foreground,background}.png and drawable-nodpi/ic_notification.png"
