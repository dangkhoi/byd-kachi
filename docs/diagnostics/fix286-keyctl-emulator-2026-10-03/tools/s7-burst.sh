#!/usr/bin/env bash
# Núm vặn THẬT NHANH: $2 lần mỗi đợt × $3 đợt cách nhau $4 s, ghi thẳng struct input_event vào thiết bị (root).
source <scratchpad>/lane2-286/kc/lib.sh
LK=${1:-115}; PER=${2:-5}; ROUNDS=${3:-6}; GAP=${4:-0.1}; TAG=${5:-burst}
python3 $K/mkburst.py $K/burst.bin $LK $PER
a push $K/burst.bin /data/local/tmp/kc-burst.bin >/dev/null
a shell media volume --stream 3 --set 8 >/dev/null 2>&1
lc_clear
T0=$(a shell date +%H:%M:%S.%3N | tr -d '\r')
a shell "for i in \$(seq 1 $ROUNDS); do cat /data/local/tmp/kc-burst.bin > /dev/input/event11; sleep $GAP; done"
T1=$(a shell date +%H:%M:%S.%3N | tr -d '\r')
sleep 3
lc_dump $K/$TAG.log
echo "gửi $((PER*ROUNDS)) sự kiện ($ROUNDS đợt × $PER, cách $GAP s) từ $T0 tới $T1"
echo "onKeyEvent DOWN: $(grep -c 'onKeyEvent DOWN' $K/$TAG.log) · voice-key fire: $(grep -c 'voice-key fire' $K/$TAG.log) · KeyCtl fire: $(grep -c 'phím → ' $K/$TAG.log)"
grep -E "KeyCtl" $K/$TAG.log | sed 's/^.*KeyCtl  ( *[0-9]*): //' 
python3 - $K/$TAG.log <<'PY'
import re,sys
tot=sum(int(m.group(1)) for m in re.finditer(r'phím → \S+ ×(\d+)',open(sys.argv[1]).read()))
print('tổng nấc qua các lệnh =',tot)
PY
br "--es cmd ctllog --ei n 80" > $K/$TAG-ctllog.json
python3 - $K/$TAG-ctllog.json "$T0" <<'PY'
import json,sys
a=json.load(open(sys.argv[1])); t0=sys.argv[2]
ls=[l for l in a.get('lines',[]) if l.split('T',1)[1][:12] >= t0 and ' id=fan ' in l]
print('lệnh HAL fan sau T0 =',len(ls))
for l in ls: print('  ',l.split(' rc=')[0])
PY
V1="$(a shell media volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -o 'volume is [0-9]*')"
echo "âm lượng nhạc sau: $V1 (trước: 8)"
a shell rm -f /data/local/tmp/kc-burst.bin
