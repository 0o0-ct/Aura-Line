#!/system/bin/sh

BACKUP_DIR="$MODPATH/backup"
mkdir -p "$BACKUP_DIR"

if [ -f "/vendor/etc/audio_effects.xml" ]; then
    cp /vendor/etc/audio_effects.xml "$BACKUP_DIR/audio_effects.xml.bak"
elif [ -f "/system/etc/audio_effects.xml" ]; then
    cp /system/etc/audio_effects.xml "$BACKUP_DIR/audio_effects.xml.bak"
fi
