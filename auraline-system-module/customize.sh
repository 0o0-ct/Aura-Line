#!/system/bin/sh

ui_print "--------------------------------------------------"
ui_print "       AuraLine DSP System Module Installer       "
ui_print "--------------------------------------------------"

# Detect ABI
ARCH=$(getprop ro.product.cpu.abi)
ui_print "- Dispositivo ABI: $ARCH"

if [ "$ARCH" != "arm64-v8a" ] && [ "$ARCH" != "armeabi-v7a" ]; then
    ui_print "! Arquitectura no soportada: $ARCH"
    abort "! Instalacion cancelada para prevenir bootloop."
fi

# Run HAL detection
ui_print "- Detectando arquitectura Audio HAL..."
sh $MODPATH/common/detect_audio_hal.sh

# Run backup
ui_print "- Creando copia de seguridad de la configuracion de audio..."
sh $MODPATH/common/backup_config.sh

# Install audio_effects patch
ui_print "- Inyectando descriptor de efecto AuraLine DSP..."
sh $MODPATH/common/install_effect.sh

# Verify
ui_print "- Verificando instalacion..."
sh $MODPATH/common/verify_installation.sh

ui_print "--------------------------------------------------"
ui_print "Instalacion de AuraLine DSP System Module exitosa."
ui_print "Reinicie el dispositivo para aplicar los cambios."
ui_print "--------------------------------------------------"
