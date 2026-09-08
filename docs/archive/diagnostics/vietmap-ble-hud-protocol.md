# VietMap BLE HUD Protocol — Reverse Engineering Findings

## Source
- APK: `VIETMAP_LIVE_3.0.0_fixGPS.apk`
- Decompiled class: `p229w0/a.java` (BLE manager + packet parser)
- Protocol encoder: `p251y0/e.java` (sends nav data to HUD)
- Product: VietMap H50 (BLE, LCD TFT 4.58")

## BLE Connection

### Service & Characteristics
| Role | UUID |
|------|------|
| **Service** | `0000fff0-0000-1000-8000-00805f9b34fb` |
| **Notify (HUD → App)** | `0000fff1-0000-1000-8000-00805f9b34fb` |
| **Write (App → HUD)** | `0000fff2-0000-1000-8000-00805f9b34fb` |
| **CCCD descriptor** | `00002902-0000-1000-8000-00805f9b34fb` |

### Connection flow
1. Scan BLE devices
2. `connectGatt(context, false, callback)`
3. `onConnectionStateChange(state=2)` → `discoverServices()`
4. `onServicesDiscovered` → find service `0000fff0`, get chars `fff1` (notify) + `fff2` (write)
5. Enable notifications on `fff1`: write `ENABLE_NOTIFICATION_VALUE` to CCCD `00002902`
6. `onDescriptorWrite` success → send version request (`cmd 14`) 3 times with 100ms delay
7. HUD responds with device info (MODEL, HW, FW, PROTOCOL version)
8. Based on PROTOCOL version, select encoder:
   - `2.2.0` / `2.2.1` → encoder C
   - `2.2.2` → encoder D  
   - `2.2.2S` → encoder E (latest)
   - else → encoder B (base)

### Also supports Classic Bluetooth (RFCOMM)
- UUID: `00001101-0000-1000-8000-00805F9B34FB` (standard SPP)
- Same packet format over RFCOMM socket
- Selection: `f27379o` flag — `true` = BLE, `false` = Classic

## Packet Format (Binary)

### Frame structure
```
Header (4 bytes): 0xA5 0x5A 0x37 0xC3
Length (4 bytes): big-endian int32 (payload length, 13-1024)
Payload: [length] bytes
```

### Payload parsing (method `h()`)
```
byte[12] = command ID
byte[13..end-8] = data (variable)
```

### Known command IDs (HUD → App responses)

| CMD | Meaning | Data format |
|-----|---------|-------------|
| `0x0E` (14) | Device info | ASCII: `MODEL:<m>,HW:<v>,FW:<v>,PROTOCOL:<v>,OBDV:<v>` |
| `0x0D` (13) | Camera/alert points list | Array of 9-byte records: [type(1), lat(4), lng(2), speed(1), dir(1)] |
| `0x0B` (11) | Navigation data | Hex-encoded string, prefixed `01`/`02`/`03` |
| `0x40` (64) | Display mode | 1 byte |
| `0x50` (80) | ??? | |
| `0x51` (81) | UI page change | 1 byte (page index) |
| `0x32` (50) | OBD data | [type(1), value(2 bytes BE)] — type 0=voltage, 1-3=temps, 16-18=pressures |
| `0x53` (83) | Settings/config response | [param_id(1), ??(1), value(1)] |
| `0x13` (19) | Settings/config response (alt) | same format |
| `0x5A` (90) | Alert list (if len>58) | Parsed by `p240x0.a.a()` |
| `0x5B` (91) | ??? | |
| `0x56` (86) | Tire pressure swap | hex string, check positions [1:5] and [6:10] > 0 |
| `0xF0` (240) | ??? | |
| `0xF2` (242) | ??? | 1 byte value |
| `0xEE` (238) | Custom data ack | byte[0]: 0=success, else=fail |

### Navigation data (cmd `0x0B`) — THE KEY DATA

Format: hex-encoded string, type prefix determines structure:

#### Type "01" and "02" — Nav state (speed limit, direction, distance...)
```
Offset  Length  Field
[2:6]   4 hex  Speed (×0.001) → float km/h? 
[6:10]  4 hex  Speed limit? (int)
[10:12] 2 hex  Turn type?
[12:14] 2 hex  ???
[14:16] 2 hex  ???
[16:20] 4 hex  Distance to next turn? (int)
[20:22] 2 hex  ???
[22:26] 4 hex  Bearing/heading (parsed as signed float)
[26:28] 2 hex  ???
[28:32] 4 hex  Something ×0.1
[32:40] 8 hex  Latitude? (long → int)
[40:44] 4 hex  Something ×0.0001
[44:52] 8 hex  Longitude? (long → int)
[52:56] 4 hex  ???
[56:58] 2 hex  ???
[58:60] 2 hex  ??? (parsed as binary string)
[60:62] 2 hex  ???
[62:64] 2 hex  ???
```

#### Type "03" — Extended nav info
```
[2:6]   4 hex  field a (int)
[6:10]  4 hex  field b (int)
[10:12] 2 hex  field c
[12:14] 2 hex  field d
[14:16] 2 hex  field e
[16:20] 4 hex  field f
[20:24] 4 hex  field g
[24:32] 8 hex  field h (long → int)
[32:40] 8 hex  field i (long → int)
[40:44] 4 hex  field j
[44:46] 2 hex  field k
[46:48] 2 hex  field l
[48:50] 2 hex  field m
```

## App → HUD Commands (Write to `fff2`)

### Packet encoding (method `c.f()`)
Wraps payload with same header `0xA5 0x5A 0x37 0xC3` + length + checksum.

### Known commands sent TO HUD

| Purpose | CMD byte | Notes |
|---------|----------|-------|
| Get version | `0x0E` (14) | Sent 3× after connect, payload `[14, 0]` |
| Navigation update | `0x4C` (76) | Turn direction + road names (UTF-8) |
| Speed limit | via encoder | Part of periodic nav update |
| Alert/camera | via encoder | Camera points list |

### Write chunking (BLE MTU)
Data > 20 bytes is split into 20-byte chunks with 100ms delay between writes.

## Implementation Strategy for ClusterNav

### Phase 1: Passive listener (giả lập HUD)
1. Advertise BLE service `0000fff0` with characteristics `fff1` + `fff2`
2. Wait for VietMap to connect (it scans for HUD devices)
3. On `fff2` write from VietMap: parse packet → extract nav data
4. Respond on `fff1` with device info when asked (cmd 14): `MODEL:H50,HW:1.6.4,FW:1.2.0,PROTOCOL:2.2.2S`

### Phase 2: Extract useful data
- Speed limit: from nav data type "01"/"02" field [6:10]
- Turn direction: from nav update cmd 0x4C
- Distance to turn: from nav data field [16:20]
- Current speed: from nav data field [2:6]

### Challenges
1. **VietMap scans for known device names?** — need to sniff what name HUD advertises
2. **Pairing/bonding** — may need specific PIN or just-works
3. **VietMap can only connect ONE HUD** — if real HUD connected, ClusterNav won't get data
4. **Protocol version negotiation** — must respond correctly to version query

### Next steps
1. Sniff real HUD advertisement (device name, flags, manufacturer data)
2. Implement BLE peripheral on Android (requires BLUETOOTH_ADVERTISE permission)
3. Test: can VietMap connect to our fake HUD?
4. Parse nav packets and extract speed limit
