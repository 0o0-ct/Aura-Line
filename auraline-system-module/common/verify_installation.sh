#!/system/bin/sh

if [ -f "$MODPATH/system/vendor/etc/audio_effects.xml" ]; then
    echo "VERIFICATION_STATUS=OK"
else
    echo "VERIFICATION_STATUS=FAILED"
fi
