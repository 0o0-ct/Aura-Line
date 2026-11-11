#!/system/bin/sh

BACKUP_FILE="/data/adb/modules/auraline-dsp-system/backup/audio_effects.xml.bak"

if [ -f "$BACKUP_FILE" ]; then
    log -t AuraLineDSP "Configuracion restaurada desde $BACKUP_FILE"
fi
