#!/bin/bash
S=${WFIT_DIR:?set WFIT_DIR (thư mục làm việc: base/ prefs + mkprefs.py)}
NARROW="0,0,2,6;2,0,2,6;4,0,2,6;6,0,2,6;8,0,2,6;10,0,2,6"
SMALL="0,0,2,3;2,0,2,3;4,0,2,3;6,0,2,3;8,0,2,3;10,0,2,3"
WIDE="0,0,6,2;6,0,6,2;0,2,6,2;6,2,6,2;0,4,6,2;6,4,6,2"
LARGE="0,0,4,6;4,0,4,6;8,0,4,6"
SETS=(
"g_trip g_energy g_battery g_climate g_windows g_lights"
"g_tyres g_doors w_energy w_tire w_pm25 w_clock"
"w_media w_car w_speed w_board w_photos w_apps"
"soc speed motor_power ev_range_km gear window_lf"
"soh_oem trunk trip_hours,odometer,ev_range_km,soc g_trip g_climate g_energy"
)
LANG=${LANG_K:-vi}
for L in ${LAYOUTS:-NARROW SMALL WIDE LARGE}; do
  i=0
  for set in "${SETS[@]}"; do
    i=$((i+1))
    if [ $L = LARGE ]; then
      a=($set); $S/shot.sh ${L}_${i}a ${!L} $LANG ${a[0]} ${a[1]} ${a[2]}; $S/shot.sh ${L}_${i}b ${!L} $LANG ${a[3]} ${a[4]} ${a[5]}
    else
      $S/shot.sh ${L}_$i ${!L} $LANG $set
    fi
  done
done
