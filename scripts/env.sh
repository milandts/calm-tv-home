#!/usr/bin/env bash
# Gemeinsame Umgebung der Bauskripte, wird mit "source" geladen (nicht direkt ausführen).
#
# Sucht das Android SDK in dieser Reihenfolge: ANDROID_SDK, ANDROID_HOME, ANDROID_SDK_ROOT, dann die üblichen Orte unter macOS und Linux.
# Setzt ANDROID_SDK, BUILD_TOOLS_DIR (Build-Tools 33.0.0, überschreibbar mit BUILD_TOOLS) und ANDROID_JAR (Plattform android-33, PLATFORM).
# Windows: die Skripte laufen unter WSL (dort gilt Linux); in Git Bash fehlen die Endungen der SDK-Werkzeuge, das ist nicht getestet.

ANDROID_SDK=${ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}
if [[ -z $ANDROID_SDK ]]; then
  for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "/usr/lib/android-sdk"; do
    [[ -d $candidate ]] && { ANDROID_SDK=$candidate; break; }
  done
fi
if [[ -z $ANDROID_SDK || ! -d $ANDROID_SDK ]]; then
  echo "Android SDK nicht gefunden. ANDROID_SDK auf den SDK-Ordner setzen (mit build-tools/33.0.0 und platforms/android-33)." >&2
  exit 1
fi
BUILD_TOOLS_DIR="$ANDROID_SDK/build-tools/${BUILD_TOOLS:-33.0.0}"
ANDROID_JAR="$ANDROID_SDK/platforms/${PLATFORM:-android-33}/android.jar"
