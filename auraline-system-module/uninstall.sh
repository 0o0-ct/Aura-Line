#!/system/bin/sh
# Restore original config
if [ -f "/data/adb/modules/auraline-dsp-system/common/restore_config.sh" ]; then
    sh /data/adb/modules/auraline-dsp-system/common/restore_config.sh
fi
log -t AuraLineDSP "Modulo desinstalado limpiamente y configuracion restaurada."
