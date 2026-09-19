#!/usr/bin/env python3
# =============================================================================
# adb_raw.py — Client ADB THÔ pure-python (không cần adb binary / cryptography).
# =============================================================================
# VÌ SAO TỒN TẠI: máy điều khiển (macOS) đôi khi KHÔNG chạy được adb-server tới
# LAN của xe (<car-ip-range>:5555) dù raw TCP tới cổng đó SỐNG. Client này nói
# thẳng giao thức ADB qua TCP nên KHÔNG phụ thuộc adb-server — [ĐO 2026-09-18]
# là đường DUY NHẤT vào được xe hôm đó.
#
# ⚠ THỨ TỰ ƯU TIÊN cho buổi test (RUNBOOK §0.3):
#   1) adb THẬT: `$SDK/platform-tools/adb connect <ip>:5555` — nếu nối được thì
#      DÙNG NÓ (Google test kỹ, push/pull/screencap/install đều chuẩn).
#   2) adb_raw.py — fallback khi (1) bị chặn. `shell` đã PROVEN trên xe;
#      push/pull/screencap/install viết theo giao thức chuẩn nhưng CHƯA đo off-car
#      ⇒ dùng khi (1) hỏng, và verify sha256 sau push (xem `push`).
#
# Ký AUTH bằng ~/.android/adbkey (PEM PKCS#8) — RSA PKCS#1 v1.5 over token (đúng
# adb RSA_sign). Xe phải đã "Luôn cho phép" khoá này (F4).
#
# DÙNG:
#   adb_raw.py <host> <port> shell '<cmd>'          # (mặc định nếu bỏ 'shell')
#   adb_raw.py <host> <port> push <local> <remote>
#   adb_raw.py <host> <port> pull <remote> <local>
#   adb_raw.py <host> <port> screencap <local.png>
#   adb_raw.py <host> <port> install <local.apk>    # push /data/local/tmp + pm install -r
# =============================================================================
import socket, struct, sys, base64, os, time, hashlib

CNXN=0x4e584e43; AUTH=0x48545541; OPEN=0x4e45504f; OKAY=0x59414b4f; CLSE=0x45534c43; WRTE=0x45545257
SHA1_PREFIX=bytes.fromhex("3021300906052b0e03021a05000414")
MAXDATA=256*1024
SYNC_CHUNK=64*1024

# ── RSA (pure-python, không dùng thư viện) ───────────────────────────────────
def der_len(b,i):
    n=b[i]; i+=1
    if n<0x80: return n,i
    k=n&0x7f; return int.from_bytes(b[i:i+k],'big'), i+k

def der_children(b):
    i=0; out=[]
    while i<len(b):
        tag=b[i]; i+=1; ln,i=der_len(b,i); out.append((tag,b[i:i+ln])); i+=ln
    return out

def load_rsa(pem_path):
    data=open(pem_path,'rb').read().decode()
    der=base64.b64decode("".join(l for l in data.splitlines() if "-----" not in l))
    assert der[0]==0x30; _,i=der_len(der,1)
    octet=[v for (t,v) in der_children(der[i:]) if t==0x04][0]
    assert octet[0]==0x30; _,j=der_len(octet,1)
    ints=[int.from_bytes(v,'big') for (t,v) in der_children(octet[j:]) if t==0x02]
    return ints[1], ints[3]   # n, d

def sign(token,n,d):
    k=(n.bit_length()+7)//8
    digest=SHA1_PREFIX+token
    em=b"\x00\x01"+b"\xff"*(k-3-len(digest))+b"\x00"+digest
    return pow(int.from_bytes(em,'big'),d,n).to_bytes(k,'big')

# ── khung gói ADB ────────────────────────────────────────────────────────────
def msg(cmd,a0,a1,data=b""):
    return struct.pack("<6I",cmd,a0,a1,len(data),sum(data)&0xffffffff,cmd^0xffffffff)+data

def recv_exact(s,n):
    buf=b""
    while len(buf)<n:
        c=s.recv(n-len(buf))
        if not c: raise IOError("closed")
        buf+=c
    return buf

def recv_msg(s):
    cmd,a0,a1,dl,_,_=struct.unpack("<6I",recv_exact(s,24))
    return cmd,a0,a1,(recv_exact(s,dl) if dl else b"")

def connect(host,port,n,d,pub):
    s=socket.create_connection((host,port),timeout=8); s.settimeout(20)
    s.sendall(msg(CNXN,0x01000001,MAXDATA,b"host::features=shell_v2,cmd\0"))
    for _ in range(6):
        cmd,a0,a1,data=recv_msg(s)
        if cmd==AUTH and a0==1: s.sendall(msg(AUTH,2,0,sign(data,n,d)))
        elif cmd==AUTH:         s.sendall(msg(AUTH,3,0,pub+b"\0"))
        elif cmd==CNXN:         return s
    raise IOError("no CNXN (auth failed? xe chưa 'Luôn cho phép' khoá này?)")

# ── một luồng (stream) trên kết nối ──────────────────────────────────────────
LID=7
def open_stream(s,service):
    s.sendall(msg(OPEN,LID,0,(service+"\0").encode()))
    while True:
        c,a0,a1,data=recv_msg(s)
        if c==OKAY: return a0            # remote id
        if c==CLSE: raise IOError("stream bị từ chối: "+service)

class Stream:
    """Đọc-ghi có đệm trên một luồng đã OPEN (tự ack OKAY hai chiều)."""
    def __init__(self,s,rid): self.s=s; self.rid=rid; self.buf=b""; self.closed=False
    def _pump(self):
        c,a0,a1,data=recv_msg(self.s)
        if c==WRTE: self.buf+=data; self.s.sendall(msg(OKAY,LID,a0))
        elif c==CLSE: self.closed=True; self.s.sendall(msg(CLSE,LID,a0))
        elif c==OKAY: pass
    def write(self,data):
        # gửi từng khối ≤ MAXDATA, chờ OKAY cho mỗi khối
        for i in range(0,len(data),MAXDATA) or [0]:
            chunk=data[i:i+MAXDATA]
            self.s.sendall(msg(WRTE,LID,self.rid,chunk))
            while True:
                c,a0,a1,d=recv_msg(self.s)
                if c==OKAY: break
                if c==WRTE: self.buf+=d; self.s.sendall(msg(OKAY,LID,a0))
                elif c==CLSE: self.closed=True; return
    def read(self,n):
        while len(self.buf)<n and not self.closed: self._pump()
        out=self.buf[:n]; self.buf=self.buf[n:]; return out
    def readall(self):
        while not self.closed: self._pump()
        out=self.buf; self.buf=b""; return out
    def close(self):
        try: self.s.sendall(msg(CLSE,LID,self.rid))
        except OSError: pass

# ── services ─────────────────────────────────────────────────────────────────
def shell(s,cmd):
    st=Stream(s,open_stream(s,"shell:"+cmd)); return st.readall().decode(errors="replace")

def exec_bin(s,cmd):
    """exec: — trả BINARY thô (không mangle LF), dùng cho screencap -p."""
    st=Stream(s,open_stream(s,"exec:"+cmd)); return st.readall()

def _sync_open(s):
    return Stream(s,open_stream(s,"sync:"))

def push(s,local,remote):
    data=open(local,'rb').read()
    mode=0o100644
    st=_sync_open(s)
    pm=f"{remote},{mode}".encode()
    st.write(b"SEND"+struct.pack("<I",len(pm))+pm)
    for i in range(0,len(data),SYNC_CHUNK):
        ch=data[i:i+SYNC_CHUNK]
        st.write(b"DATA"+struct.pack("<I",len(ch))+ch)
    st.write(b"DONE"+struct.pack("<I",int(time.time())))
    rid4=st.read(4); rlen=struct.unpack("<I",st.read(4))[0]
    err=st.read(rlen).decode(errors="replace") if rlen else ""
    st.close()
    ok = rid4==b"OKAY"
    print(f"push {os.path.basename(local)} → {remote}: {'OK' if ok else 'FAIL '+err} "
          f"({len(data)} B, sha256={hashlib.sha256(data).hexdigest()[:16]}…)")
    return ok

def pull(s,remote,local):
    st=_sync_open(s)
    rp=remote.encode()
    st.write(b"RECV"+struct.pack("<I",len(rp))+rp)
    out=b""
    while True:
        idb=st.read(4)
        if idb==b"DATA":
            ln=struct.unpack("<I",st.read(4))[0]; out+=st.read(ln)
        elif idb==b"DONE":
            st.read(4); break
        elif idb==b"FAIL":
            ln=struct.unpack("<I",st.read(4))[0]
            raise IOError("pull FAIL: "+st.read(ln).decode(errors="replace"))
        else:
            raise IOError("pull: id lạ "+repr(idb))
    open(local,'wb').write(out); st.close()
    print(f"pull {remote} → {local}: OK ({len(out)} B)")

def screencap(s,local):
    png=exec_bin(s,"screencap -p")
    open(local,'wb').write(png)
    print(f"screencap → {local}: {len(png)} B {'(PNG OK)' if png[:8]==bytes.fromhex('89504e470d0a1a0a') else '⚠ không phải PNG'}")

def install(s,apk):
    tmp="/data/local/tmp/_kachi_ota.apk"
    if not push(s,apk,tmp): print("install: push thất bại"); return
    print(shell(s,f"pm install -r {tmp}").strip())
    shell(s,f"rm -f {tmp}")

# ── CLI ──────────────────────────────────────────────────────────────────────
if __name__=="__main__":
    if len(sys.argv)<4:
        print(__doc__ or "adb_raw.py <host> <port> <shell|push|pull|screencap|install> ..."); sys.exit(2)
    host=sys.argv[1]; port=int(sys.argv[2]); op=sys.argv[3]
    n,d=load_rsa(os.path.expanduser("~/.android/adbkey"))
    pub=open(os.path.expanduser("~/.android/adbkey.pub"),'rb').read().split()[0]
    s=connect(host,port,n,d,pub)
    try:
        if op=="push":        push(s,sys.argv[4],sys.argv[5])
        elif op=="pull":      pull(s,sys.argv[4],sys.argv[5])
        elif op=="screencap": screencap(s,sys.argv[4])
        elif op=="install":   install(s,sys.argv[4])
        elif op=="shell":     sys.stdout.write(shell(s," ".join(sys.argv[4:])))
        else:                 sys.stdout.write(shell(s," ".join(sys.argv[3:])))  # tương thích: cmd ở vị trí 3
    finally:
        s.close()
