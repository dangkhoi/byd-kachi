#!/usr/bin/env python3
"""
Qt RCC (Resource Collection) v3 extractor — used to answer Cluster/HUD open
question Q4 (does the cluster QML render a plugin-string / msg-45 as the
speed-limit sign?).

Context: docs/_handoff/hud-cluster-injection-findings-2026-08-10.md §25.
Prior sessions assumed cluster_theme*.rcc "needs an rcc decompressor". It does
NOT block QML inspection: the themes are Qt RCC v3 and the QML/JS blobs are
plain zlib (0 zstd). This parser walks the names + data sections, inflates
zlib entries (Qt qUncompress = 4-byte big-endian length prefix + zlib stream),
and dumps a text corpus you can grep for `trafficSignValue`, `onPluginMsgReceived`,
`SEND_MSG_ID_*`, etc.

Usage:
    python3 scripts/re/rcc_extract.py <theme.rcc> <out_dir>
Outputs:
    <out_dir>/_files.txt   — every resource path in the bundle
    <out_dir>/_corpus.txt  — concatenated text (QML/JS/QSS) blobs, each
                             prefixed with "===== FILE: <path> =====".

Source artifact (off-car): ~/Library/Caches/clusternav-re/sysimg/cluster_theme{1,2}.rcc
extracted from system.img (/system/lib64/). See findings §13 for extraction.
"""
import sys
import struct
import zlib
import os


def u16(b, o):
    return struct.unpack_from('>H', b, o)[0]


def u32(b, o):
    return struct.unpack_from('>I', b, o)[0]


def parse(path, outdir):
    data = open(path, 'rb').read()
    if data[:4] != b'qres':
        raise SystemExit('not a Qt RCC file (bad magic): ' + path)
    version = u32(data, 4)
    tree_off = u32(data, 8)
    data_off = u32(data, 12)
    name_off = u32(data, 16)
    print('[%s] version=%d tree=%#x data=%#x names=%#x size=%#x'
          % (os.path.basename(path), version, tree_off, data_off, name_off, len(data)))

    # node = name(4) + flags(2) + [dir:count(4)+child(4) | file:country(2)+lang(2)+dataoff(4)] + mtime(8 if v>=2)
    node_sz = 4 + 2 + 8 + (8 if version >= 2 else 0)

    # --- names section: u16 len, u32 hash, len*2 UTF-16BE ---
    names = {}
    p = name_off
    while p < tree_off:
        ln = u16(data, p)
        s = data[p + 6:p + 6 + ln * 2].decode('utf-16-be', 'replace')
        names[p - name_off] = s
        p += 6 + ln * 2

    def node(i):
        o = tree_off + i * node_sz
        nameoff = u32(data, o)
        flags = u16(data, o + 4)
        if flags & 0x02:  # directory
            return ('dir', names.get(nameoff, '?'), flags,
                    u32(data, o + 6), u32(data, o + 10))
        return ('file', names.get(nameoff, '?'), flags,
                u16(data, o + 6), u16(data, o + 8), u32(data, o + 10))

    os.makedirs(outdir, exist_ok=True)
    corpus, filelist = [], []
    stats = {'raw': 0, 'zlib': 0, 'zstd': 0}

    def read_blob(doff, flags):
        ln = u32(data, data_off + doff)
        payload = data[data_off + doff + 4: data_off + doff + 4 + ln]
        if flags & 0x04:  # zstd — not handled (none observed in cluster themes)
            stats['zstd'] += 1
            return None
        if flags & 0x01:  # zlib (Qt qCompress: 4-byte BE uncompressed size + stream)
            stats['zlib'] += 1
            try:
                return zlib.decompress(payload[4:])
            except Exception:
                try:
                    return zlib.decompress(payload)
                except Exception:
                    return None
        stats['raw'] += 1
        return payload

    def walk(i, prefix):
        typ = node(i)
        if typ[0] == 'dir':
            _, nm, _, cc, cf = typ
            base = prefix + ('/' + nm if nm and nm != '?' else '')
            for k in range(cc):
                walk(cf + k, base)
            return
        _, nm, flags, _, _, doff = typ
        full = prefix + '/' + nm
        filelist.append(full)
        blob = read_blob(doff, flags)
        if not blob:
            return
        head = blob[:64]
        printable = sum(1 for c in head if 9 <= c <= 13 or 32 <= c <= 126)
        if printable / max(1, len(head)) > 0.85:
            corpus.append((full, blob.decode('utf-8', 'replace')))

    walk(0, '')
    print('  files=%d textfiles=%d raw=%d zlib=%d zstd=%d'
          % (len(filelist), len(corpus), stats['raw'], stats['zlib'], stats['zstd']))

    with open(os.path.join(outdir, '_corpus.txt'), 'w') as fo:
        for full, txt in corpus:
            fo.write('\n\n===== FILE: %s =====\n%s' % (full, txt))
    with open(os.path.join(outdir, '_files.txt'), 'w') as fo:
        fo.write('\n'.join(sorted(filelist)))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: rcc_extract.py <theme.rcc> <out_dir>')
    parse(sys.argv[1], sys.argv[2])
