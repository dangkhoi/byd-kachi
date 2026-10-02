#!/bin/bash
# Probe helper (scratchpad của phiên đo 01–02/10). Đường adb lấy từ biến môi trường, không ghi đường máy cá nhân.
exec "${ADB:-adb}" -s emulator-5554 "$@"
