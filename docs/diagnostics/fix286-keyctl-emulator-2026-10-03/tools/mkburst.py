# Sinh tệp nhị phân struct input_event (arm64: timeval 16 B + type u16 + code u16 + value s32 = 24 B).
import struct,sys
out, code, n = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
ev = lambda t,c,v: struct.pack('<qqHHi', 0, 0, t, c, v)
with open(out,'wb') as f:
    for _ in range(n):
        f.write(ev(1,code,1)+ev(0,0,0)+ev(1,code,0)+ev(0,0,0))
