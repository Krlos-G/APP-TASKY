#!/bin/sh
# Gera os icones do PWA a partir dos SVGs desta pasta.
# Precisa do ImageMagick com librsvg (o instalador de Windows ja traz).
#
#   sh frontend/icones/gerar.sh
set -e
cd "$(dirname "$0")"

# 8 bits e sem metadados: em 16 bits o de 512 passava de 450 KB.
png() {
  magick "$1" -resize "$2x$2" -alpha off -depth 8 -strip "../public/icons/$3"
}

png icone.svg 180 apple-touch-icon.png
png icone.svg 192 icone-192.png
png icone.svg 512 icone-512.png
png icone-maskable.svg 512 icone-512-maskable.png

# O favicon tem desenho proprio: em 16 px o T dentro do cartao sumia.
magick icone-favicon.svg -define icon:auto-resize=48,32,16 ../public/favicon.ico
