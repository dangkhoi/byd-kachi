#!/usr/bin/env bash
# Núm vặn: N sự kiện VOLUME UP liên tiếp (một lệnh shell, sendevent thật) — đếm lệnh HAL (ctllog) + dòng KeyCtl.
source <scratchpad>/lane2-286/kc/lib.sh
N=${1:-20}; LK=${2:-115}; TAG=${3:-knob}
a shell media volume --stream 3 --set 8 >/dev/null 2>&1
V0="$(a shell media volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -o 'volume is [0-9]*')"
d=/dev/input/event11; cmd=""
for i in $(seq 1 $N); do cmd="$cmd sendevent $d 1 $LK 1; sendevent $d 0 0 0; sendevent $d 1 $LK 0; sendevent $d 0 0 0;"; done
lc_clear
T0=$(a shell date +%H:%M:%S.%3N | tr -d '\r')
a shell "$cmd"
T1=$(a shell date +%H:%M:%S.%3N | tr -d '\r')
sleep 3
lc_dump $K/$TAG.log
echo "gửi $N sự kiện từ $T0 tới $T1"
echo "onKeyEvent DOWN: $(grep -c 'onKeyEvent DOWN' $K/$TAG.log) · voice-key fire: $(grep -c 'voice-key fire' $K/$TAG.log)"
grep -E "KeyCtl" $K/$TAG.log
br "--es cmd ctllog --ei n 60" > $K/$TAG-ctllog.json
python3 - $K/$TAG-ctllog.json "$T0" <<'PY'
import json,sys
a=json.load(open(sys.argv[1])); t0=sys.argv[2]
ls=[l for l in a.get('lines',[]) if l.split('T',1)[1][:12] >= t0 and ' id=fan ' in l]
print('lệnh HAL fan sau T0 =',len(ls))
for l in ls: print('  ',l)
PY
V1="$(a shell media volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -o 'volume is [0-9]*')"
echo "âm lượng nhạc trước: $V0 | sau: $V1 (phím đã gán bị NUỐT ⇒ không đổi)"
