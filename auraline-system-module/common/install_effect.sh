#!/system/bin/sh

TARGET_DIR="$MODPATH/system/vendor/etc"
mkdir -p "$TARGET_DIR"

if [ -f "$MODPATH/config/audio_effects.xml" ]; then
    cp "$MODPATH/config/audio_effects.xml" "$TARGET_DIR/audio_effects.xml"
fi
