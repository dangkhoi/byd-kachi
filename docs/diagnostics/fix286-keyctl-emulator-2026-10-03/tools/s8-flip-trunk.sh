#!/usr/bin/env bash
# Ghi đè gán VOLUME DOWN: (a) "Bật/tắt Kính lái (đảo)" — máy ảo không đọc được ⇒ báo, 0 lệnh; (b) "Mở Cốp sau".
source <scratchpad>/lane2-286/kc/lib.sh
S=$K/s4-bind.sh
one() {  # $1 nhóm  $2 việc  $3 tag  $4 id kiểm ctllog
  a shell am start -n $HOME_ACT --es open_settings_group keys >/dev/null; sleep 3
  bash $S "VOLUME DOWN (mã 25)" "$1" "$2" $3 | grep -E "tap|voicekey"
  a shell input keyevent 4; sleep 1; home; sleep 2
  T0=$(a shell date +%H:%M:%S.%3N | tr -d '\r')
  lc_clear; key 114; sleep 0.3; shot $3-toast; sleep 2
  lc_dump $K/$3.log; grep -E "KeyCtl|voice-key" $K/$3.log
  br "--es cmd ctllog --ei n 20" > $K/$3-ctllog.json
  python3 - $K/$3-ctllog.json "$T0" "$4" <<'PY'
import json,sys
a=json.load(open(sys.argv[1])); t0=sys.argv[2]; cid=sys.argv[3]
ls=[l for l in a.get('lines',[]) if l.split('T',1)[1][:12] >= t0 and (' id=%s '%cid) in l]
print('lệnh HAL %s sau T0 = %d'%(cid,len(ls)))
for l in ls: print('  ',l.split(' rc=')[0])
PY
}
one "Kính" "Bật/tắt Kính lái (đảo)" flip win_lf
one "Cửa & khoang" "Mở Cốp sau" trunk trunk
