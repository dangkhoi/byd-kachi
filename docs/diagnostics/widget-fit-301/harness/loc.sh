#!/bin/bash
S=${WFIT_DIR:?set WFIT_DIR}; cd $S
NARROW="0,0,2,6;2,0,2,6;4,0,2,6;6,0,2,6;8,0,2,6;10,0,2,6"; SMALL="0,0,2,3;2,0,2,3;4,0,2,3;6,0,2,3;8,0,2,3;10,0,2,3"; WIDE="0,0,6,2;6,0,6,2;0,2,6,2;6,2,6,2;0,4,6,2;6,4,6,2"
PRE=${PRE:-loc}
for L in ${LANGS:-en zh th ms}; do for LY in NARROW SMALL WIDE; do OUTDIR=$S/${PRE}_$L ./shot.sh ${LY}_1 ${!LY} $L g_trip g_energy g_battery g_climate g_windows g_lights >/dev/null 2>&1; OUTDIR=$S/${PRE}_$L ./shot.sh ${LY}_3 ${!LY} $L w_media w_car w_speed w_board w_photos w_apps >/dev/null 2>&1; done; echo "== $L"; python3 analyze.py ${PRE}_$L | awk '$0 ~ /bad=[1-9]/'; done
