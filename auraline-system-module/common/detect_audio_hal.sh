#!/system/bin/sh

API=$(getprop ro.build.version.sdk)

if [ "$API" -ge 33 ]; then
    echo "HAL_TYPE=AIDL"
elif [ "$API" -ge 29 ]; then
    echo "HAL_TYPE=HIDL"
else
    echo "HAL_TYPE=LEGACY"
fi
