import socket, threading, time, sys
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 5556)); s.listen(16)
conns = []
log = open(sys.argv[1], "a")
while True:
    c, a = s.accept(); conns.append(c)
    c.settimeout(0.5)
    try:
        d = c.recv(4096)
    except Exception:
        d = b""
    log.write("%s accept %s bytes=%d\n" % (time.strftime("%H:%M:%S"), a, len(d))); log.flush()
