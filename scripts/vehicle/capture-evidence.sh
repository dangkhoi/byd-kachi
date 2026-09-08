#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_candidate
select_device
new_evidence_dir

{
  echo "timestamp=$(date -u +%FT%TZ)"
  echo "apk=$APK"
  echo "sha256=$EXPECTED_SHA256"
  echo "serial=$DEVICE_SERIAL"
  "${ADB[@]}" shell getprop ro.product.model
  "${ADB[@]}" shell getprop ro.build.fingerprint
  "${ADB[@]}" shell dumpsys package com.byd.clusternav | grep -E 'versionCode|versionName|signatures|lastUpdateTime' || true
} > "$EVIDENCE_DIR/device-and-build.txt"
"${ADB[@]}" exec-out screencap -p > "$EVIDENCE_DIR/screenshot.png"
"${ADB[@]}" logcat -d -v threadtime > "$EVIDENCE_DIR/logcat.txt"
"${ADB[@]}" shell am stack list > "$EVIDENCE_DIR/am-stack-list.txt" || true
"${ADB[@]}" shell dumpsys window displays > "$EVIDENCE_DIR/window-displays.txt" || true
"${ADB[@]}" shell dumpsys display > "$EVIDENCE_DIR/display.txt" || true
shasum -a 256 "$EVIDENCE_DIR"/* > "$EVIDENCE_DIR/SHA256SUMS.txt"
chmod 600 "$EVIDENCE_DIR"/*
echo "Sensitive evidence captured locally at $EVIDENCE_DIR (gitignored; review/redact before sharing)."
