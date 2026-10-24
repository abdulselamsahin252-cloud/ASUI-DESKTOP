#!/bin/bash
# ASUI proje klasorunde calistir:  bash motor_kopyala.sh
# Winlator motor dosyalarini (assets + jniLibs) winlator/src/main altina koyar.
set -e
[ -d winlator/src/main ] || { echo "HATA: Bu betigi ASUI proje klasorunde calistir (winlator klasoru yok)."; exit 1; }

ZIP="$1"
for c in ../winlator-app-main.zip winlator-app-main.zip ../Winlator-app-main.zip; do
  if [ -z "$ZIP" ] && [ -f "$c" ]; then ZIP="$c"; fi
done
if [ -z "$ZIP" ]; then
  ZIP=$(find / -iname "winlator-app-main.zip" -size +50M -not -path "/proc/*" 2>/dev/null | head -1)
fi
if [ -z "$ZIP" ] || [ ! -f "$ZIP" ]; then
  echo "HATA: winlator-app-main.zip bulunamadi."
  echo "Zip'in yolunu bilirsen su sekilde yaz:  bash motor_kopyala.sh /zipin/yolu/winlator-app-main.zip"
  exit 1
fi
echo "Kullanilan zip: $ZIP"

TMP="$(mktemp -d ../wl_tmp.XXXXXX)"
unzip -q -o "$ZIP" "*/app/src/main/assets/*" "*/app/src/main/jniLibs/*" -d "$TMP"
SRC="$(ls -d "$TMP"/*/app/src/main 2>/dev/null | head -1)"
if [ -z "$SRC" ] || [ ! -d "$SRC/assets" ]; then
  echo "HATA: Zip'in icinde assets klasoru yok (bu zip eksik/yanlis olabilir)."
  rm -rf "$TMP"
  exit 1
fi

mkdir -p winlator/src/main/assets winlator/src/main/jniLibs
cp -r "$SRC/assets/." winlator/src/main/assets/
[ -d "$SRC/jniLibs" ] && cp -r "$SRC/jniLibs/." winlator/src/main/jniLibs/
rm -rf "$TMP"

if [ -f winlator/src/main/assets/rootfs.tzst ] && [ -f winlator/src/main/jniLibs/arm64-v8a/libFLAC.so ]; then
  echo "TAMAM: motor dosyalari yerine kondu."
else
  echo "HATA: rootfs.tzst veya jniLibs eksik. Cikti: ls winlator/src/main/assets | head  komutunun sonucunu bana yaz."
  exit 1
fi
