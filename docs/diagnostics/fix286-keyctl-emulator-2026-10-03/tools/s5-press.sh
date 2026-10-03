#!/usr/bin/env bash
# Một lần bấm VOLUME UP (gán ctl:fan:+1) ở màn chính: log KeyCtl + ctllog + âm lượng không đổi.
source <scratchpad>/lane2-286/kc/lib.sh
a shell input keyevent 4; sleep 1; home; sleep 2
br "--es cmd ctllog --ei n 5" > $K/ctllog-before.json
python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print('ctllog total trước =',d.get('total'))" $K/ctllog-before.json
V0="$(a shell media volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -i volume)"
lc_clear
key 115; sleep 0.25; shot press1-toast; sleep 2
lc_dump $K/press1.log
grep -E "KeyCtl|onKeyEvent|voice-key" $K/press1.log
br "--es cmd ctllog --ei n 5" > $K/ctllog-after.json
python3 - $K/ctllog-before.json $K/ctllog-after.json <<'PY'
import json,sys
b=json.load(open(sys.argv[1])); a=json.load(open(sys.argv[2]))
print('ctllog total sau =',a.get('total'),' (+%d)'%(a.get('total',0)-b.get('total',0)))
for l in a.get('lines',[])[-3:]: print('  ',l)
PY
V1="$(a shell media volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -i volume)"
echo "âm lượng nhạc trước: $V0 | sau: $V1"
