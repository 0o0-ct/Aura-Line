#!/system/bin/sh
# Wait for boot completion
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# Verify audioserver status
log -t AuraLineDSP "Servicio AuraLine DSP inicializado tras el arranque del sistema."
