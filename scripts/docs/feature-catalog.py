#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dựng `docs/kachi-feature-catalog.html` — BẢNG TOÀN BỘ chức năng launcher Kachi (diễn giải · voice command · phản hồi
· status thật). Owner 2026-09-16: *"không sót chức năng nào, và status thực tế của chức năng đó"*.

Ba nguồn, KHÔNG chép tay phần nào có máy sinh được:
  1. core/build/catalog/registry.json — dump từ chính 4 bộ đăng ký + VoiceReply/VoiceRiskTable
     (chạy `./gradlew :core:test --tests '*FeatureCatalogDumpTest*'`).
  2. docs/catalog/features.json — chức năng NGOÀI registry (ô, hồ sơ, cast, HOME, OTA, voice engine, cài đặt, chẩn
     đoán…), tay-khảo-sát từ docs/specs/backlog, mỗi dòng có evidence.
  3. docs/catalog/status-by-id.json — status on-car theo id nút/datum (captest 09-15, remediation, NEEDS-ONCAR…).

Dùng:  python3 scripts/docs/feature-catalog.py [--registry core/build/catalog/registry.json]
       [--features docs/catalog/features.json] [--status docs/catalog/status-by-id.json]
       [--out docs/kachi-feature-catalog.html]
"""
import argparse
import datetime as dt
import html
import json
import os
import sys

TIER_VI = {
    "PROVEN": "🟢 đã chứng minh (đọc/ghi thật)",
    "OVERDRIVE": "🚗 binding từ RE, chờ xe",
    "DASHCAST": "🟡 kế thừa DashCast (field-proven đời khác)",
}
DEFAULT_TIER_VI = "🚗 chờ xe"
KIND_VI = {"TOGGLE": "Bật/tắt", "STEP": "Tăng/giảm/đặt mức", "COVER": "Mở/đóng (có 50%)", "SELECT": "Chọn một trong N", "BUTTON": "Bấm một phát"}
DOMAIN_VI = {
    "ENERGY": "Năng lượng", "DRIVETRAIN": "Truyền động", "CLIMATE": "Điều hoà", "TYRES": "Lốp", "BODY": "Thân xe",
    # `SAFETY` đã gỡ 2026-09-16 cùng toàn bộ ADAS/an toàn (owner) — không còn domain nào mang tên đó.
    "LIGHTS": "Đèn", "IDENTITY": "Định danh", "INFOTAINMENT": "Giải trí/hiển thị",
}
RISK_VI = {"SAFE": "an toàn", "NORMAL": "chạy ngay", "CONFIRM": "⚠ hỏi xác nhận trước"}


def esc(s):
    return html.escape("" if s is None else str(s))


def li(items):
    items = [i for i in (items or []) if i]
    return "<ul class=\"v\">" + "".join(f"<li>{esc(i)}</li>" for i in items) + "</ul>" if items else "<span class=\"muted\">—</span>"


def status_of(row, by_id, kind):
    s = by_id.get(row["id"])
    tier = row.get("tier")
    base = TIER_VI.get(tier, DEFAULT_TIER_VI) if tier else DEFAULT_TIER_VI
    if s:
        return f"<b>{esc(s.get('status', ''))}</b> {esc(s.get('note', ''))}<div class=\"ev\">{esc(s.get('evidence', ''))}</div><div class=\"ev\">tier: {esc(base)}</div>"
    return f"{esc(base)}<div class=\"ev\">chưa có kết quả on-car riêng cho mục này (tier từ registry)</div>"


def control_desc(c):
    k = c["kind"]
    d = [f"Nút <b>{esc(c['label'])}</b>" + (f" (<i>{esc(c['labelEn'])}</i>)" if c.get("labelEn") else "") + f" — loại <b>{esc(KIND_VI.get(k, k))}</b>, nhóm {esc(DOMAIN_VI.get(c['domain'], c['domain']))}."]
    if k == "STEP":
        d.append(f"Dải {c['min']}–{c['max']}, bước {c['step']}, mặc định {c['value']}.")
    if c.get("args"):
        d.append("Lựa chọn: " + " · ".join(esc(a) for a in c["args"]) + ".")
    d.append(("Có sẵn trên thanh nút mặc định." if c.get("enabledByDefault") else "Mặc định tắt — bật qua Cài đặt › Tuỳ biến thanh nút.") )
    d.append(f"HAL: <code>{esc(c.get('bindingKey') or '—')}</code>" + (f" · <code>{esc(c['halDevice'])}</code>" if c.get("halDevice") else "") + ".")
    if c.get("synonyms"):
        d.append("Cách gọi khác: " + ", ".join(f"“{esc(s)}”" for s in c["synonyms"]) + ".")
    return " ".join(d)


def telemetry_desc(t):
    d = [f"Thông tin đọc <b>{esc(t['label'])}</b>" + (f" (<i>{esc(t['labelEn'])}</i>)" if t.get("labelEn") else "") + (f", đơn vị <code>{esc(t['unit'])}</code>" if t.get("unit") else "") + f", nhóm {esc(DOMAIN_VI.get(t['domain'], t['domain']))}."]
    d.append("Đặt được ở thanh trên (chip), ô giữa (widget) hoặc thanh nút; voice đọc ra giá trị hiện tại.")
    d.append(f"HAL: <code>{esc(t.get('bindingKey') or '—')}</code>" + (f" · <code>{esc(t['halDevice'])}</code>" if t.get("halDevice") else "") + ".")
    if t.get("synonyms"):
        d.append("Cách gọi khác: " + ", ".join(f"“{esc(s)}”" for s in t["synonyms"]) + ".")
    return " ".join(d)


def reply_cell(row):
    parts = []
    for key, lab in (("replyDone", "xong"), ("replyPreview", "đọc"), ("confirmQuestion", "hỏi"), ("replyFailed", "lỗi"), ("replyNoReading", "không có số")):
        if row.get(key):
            parts.append(f"<div><span class=\"tag\">{lab}</span> {esc(row[key])}</div>")
    if row.get("risk"):
        parts.append(f"<div class=\"ev\">rủi ro: {esc(RISK_VI.get(row['risk'], row['risk']))}</div>")
    return "".join(parts) or "<span class=\"muted\">—</span>"


def table(headers, rows):
    th = "".join(f"<th>{h}</th>" for h in headers)
    return f"<div class=\"tblwrap\"><table><thead><tr>{th}</tr></thead><tbody>" + "".join(rows) + "</tbody></table></div>"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--registry", default="core/build/catalog/registry.json")
    ap.add_argument("--features", default="docs/catalog/features.json")
    ap.add_argument("--status", default="docs/catalog/status-by-id.json")
    ap.add_argument("--out", default="docs/kachi-feature-catalog.html")
    a = ap.parse_args()
    reg = json.load(open(a.registry, encoding="utf-8"))
    feats = json.load(open(a.features, encoding="utf-8")) if os.path.isfile(a.features) else []
    by_id = json.load(open(a.status, encoding="utf-8")) if os.path.isfile(a.status) else {}

    n_ctl, n_tel, n_mac, n_la = len(reg["controls"]), len(reg["telemetry"]), len(reg["macros"]), len(reg["launcher"])
    groups = {}
    for f in feats:
        groups.setdefault(f["group"], []).append(f)

    parts = []
    parts.append(f"""<!DOCTYPE html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Kachi — Danh mục chức năng (voice · phản hồi · status thật)</title>
<style>
:root{{--bg:#fff;--fg:#1d1d1f;--muted:#6e6e73;--line:#e5e5ea;--card:#f5f5f7;--accent:#0071e3;--code:#f2f2f7}}
@media(prefers-color-scheme:dark){{:root{{--bg:#000;--fg:#f5f5f7;--muted:#98989d;--line:#2c2c2e;--card:#1c1c1e;--accent:#2997ff;--code:#1c1c1e}}}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--bg);color:var(--fg);font-family:-apple-system,BlinkMacSystemFont,"SF Pro Text","Segoe UI",Roboto,sans-serif;line-height:1.5}}
.wrap{{max-width:1400px;margin:0 auto;padding:40px 24px 120px}}
h1{{font-size:30px;font-weight:700;letter-spacing:-.02em;margin:0 0 6px}}h2{{font-size:22px;margin:44px 0 12px;padding-top:18px;border-top:1px solid var(--line)}}h3{{font-size:17px;margin:24px 0 8px}}
p,li,td,th{{font-size:14px}}.muted{{color:var(--muted)}}code{{background:var(--code);padding:1px 5px;border-radius:5px;font-family:"SF Mono",ui-monospace,monospace;font-size:12.5px}}
table{{border-collapse:collapse;width:100%;margin:12px 0}}th,td{{text-align:left;padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:top}}
th{{font-weight:600;color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.03em;position:sticky;top:0;background:var(--bg)}}
.tblwrap{{overflow-x:auto}}ul.v{{margin:0;padding-left:18px}}ul.v li{{margin:1px 0}}.ev{{color:var(--muted);font-size:12px;margin-top:3px}}
.tag{{font-size:10.5px;font-weight:700;padding:1px 6px;border-radius:5px;background:var(--accent);color:#fff;margin-right:4px}}
.card{{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px;margin:14px 0}}
.toc a{{margin-right:14px}}td:nth-child(2){{min-width:280px}}td:nth-child(3){{min-width:180px}}td:nth-child(4){{min-width:220px}}td:nth-child(5){{min-width:200px}}
</style></head><body><div class="wrap">
<h1>Kachi — Danh mục chức năng</h1>
<p class="muted">Diễn giải · voice command · phản hồi · status THẬT. Sinh ngày {dt.date.today().isoformat()} bằng <code>scripts/docs/feature-catalog.py</code>
từ <code>registry.json</code> (dump máy từ 4 bộ đăng ký, {n_ctl} nút · {n_tel} thông tin · {n_mac} gói lệnh · {n_la} hành động) + <code>docs/catalog/features.json</code>
({len(feats)} chức năng ngoài registry) + <code>docs/catalog/status-by-id.json</code> ({len(by_id)} mục có kết quả on-car). Chủ: dangkhoi.</p>
<div class="card"><b>Quy ước status</b>: 🟢 đã xác nhận trên xe · ⚠ chạy một phần trên xe · ❌ trên xe không tác dụng / bỏ theo thiết kế · 🚗 code xong, chờ xe · ✅ off-car (máy ảo/test) · 🔨 đang dở · 🔲 chưa làm.
Cột <i>Phản hồi</i> là <b>chuỗi thật</b> Kachi hiện trên tấm chữ và đọc bằng giọng (bỏ ký hiệu ✓/✗ khi đọc); “hỏi” = câu xác nhận trước khi bắn (rủi ro CONFIRM).
Không suy diễn: mục chưa có phép đo trên xe giữ tier của registry (🚗), không được ghi 🟢.</div>
<p class="toc"><b>Mục lục:</b> """ + " ".join(f"<a href=\"#g{i}\">{esc(g)}</a>" for i, g in enumerate(groups)) + f""" <a href="#controls">Nút điều khiển ({n_ctl})</a> <a href="#telemetry">Thông tin đọc ({n_tel})</a> <a href="#macros">Gói lệnh</a> <a href="#launcher">Hành động launcher</a> <a href="#generic">Voice ngoài registry</a></p>
""")

    for i, (g, rows) in enumerate(groups.items()):
        trs = []
        for f in rows:
            trs.append("<tr><td><b>%s</b>%s</td><td>%s</td><td>%s</td><td>%s</td><td><b>%s</b><div class=\"ev\">%s</div></td></tr>" % (
                esc(f["name"]), (f"<div class=\"ev\">{esc(f['spec'])}</div>" if f.get("spec") else ""), f["desc"] if f.get("desc_html") else esc(f.get("desc", "")),
                li(f.get("voice")), esc(f.get("voice_reply") or "—"), esc(f["status"]), esc(f.get("evidence", ""))))
        parts.append(f"<h2 id=\"g{i}\">{esc(g)} <span class=\"muted\">({len(rows)})</span></h2>" + table(["Chức năng", "Diễn giải", "Voice command", "Phản hồi", "Status thật"], trs))

    trs = []
    for c in reg["controls"]:
        trs.append(f"<tr><td><b>{esc(c['label'])}</b><div class=\"ev\"><code>{esc(c['id'])}</code></div></td><td>{control_desc(c)}</td><td>{li(c['voice'])}</td><td>{reply_cell(c)}</td><td>{status_of(c, by_id, 'control')}</td></tr>")
    parts.append(f"<h2 id=\"controls\">Nút điều khiển xe <span class=\"muted\">({n_ctl} — từ ControlRegistry)</span></h2>" + table(["Nút", "Diễn giải", "Voice command", "Phản hồi", "Status thật"], trs))

    trs = []
    for t in reg["telemetry"]:
        trs.append(f"<tr><td><b>{esc(t['label'])}</b><div class=\"ev\"><code>{esc(t['id'])}</code></div></td><td>{telemetry_desc(t)}</td><td>{li(t['voice'])}</td><td>{reply_cell(t)}</td><td>{status_of(t, by_id, 'telemetry')}</td></tr>")
    parts.append(f"<h2 id=\"telemetry\">Thông tin đọc từ xe <span class=\"muted\">({n_tel} — từ TelemetryRegistry)</span></h2>" + table(["Thông tin", "Diễn giải", "Voice command", "Phản hồi", "Status thật"], trs))

    trs = []
    for m in reg["macros"]:
        desc = f"Gói lệnh <b>{esc(m['label'])}</b> ({esc(DOMAIN_VI.get(m['domain'], m['domain']))}) chạy tuần tự các bước: " + ", ".join(f"<code>{esc(s)}</code>" for s in m["steps"]) + f". Tier yếu nhất trong các bước: {esc(m['tier'])}."
        trs.append(f"<tr><td><b>{esc(m['label'])}</b><div class=\"ev\"><code>{esc(m['id'])}</code></div></td><td>{desc}</td><td>{li(m['voice'])}</td><td>{reply_cell(m)}</td><td>{status_of(m, by_id, 'macro')}</td></tr>")
    parts.append(f"<h2 id=\"macros\">Gói lệnh <span class=\"muted\">({n_mac} — từ ActionMacros)</span></h2>" + table(["Gói", "Diễn giải", "Voice command", "Phản hồi", "Status thật"], trs))

    trs = []
    for l in reg["launcher"]:
        trs.append(f"<tr><td><b>{esc(l['label'])}</b><div class=\"ev\"><code>{esc(l['id'])}</code></div></td><td>Hành động của launcher (không chạm xe): mở {esc(l['label'].lower())}.</td><td>{li(l['voice'])}</td><td>{reply_cell(l)}</td><td>✅ off-car — E2E máy ảo <code>voice-e2e.sh</code> (T1)</td></tr>")
    parts.append(f"<h2 id=\"launcher\">Hành động launcher <span class=\"muted\">({n_la})</span></h2>" + table(["Hành động", "Diễn giải", "Voice command", "Phản hồi", "Status thật"], trs))

    trs = []
    for g in reg["generic"]:
        trs.append(f"<tr><td><code>{esc(g['id'])}</code></td><td>{li(g['voice'])}</td><td>{reply_cell(g)}</td></tr>")
    parts.append("<h2 id=\"generic\">Voice — ý định ngoài registry (nhạc · dẫn đường · sổ địa chỉ · app · hồ sơ · không hiểu)</h2><p class=\"muted\">Status của các ý định này nằm ở nhóm <i>Voice</i> phía trên; đây là bảng câu mẫu ↔ phản hồi thật.</p>" + table(["Ý định", "Voice command", "Phản hồi"], trs))

    parts.append("</div></body></html>\n")
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
    open(a.out, "w", encoding="utf-8").write("".join(parts))
    print(f"đã ghi {a.out}: {n_ctl} nút · {n_tel} thông tin · {n_mac} gói · {n_la} hành động · {len(feats)} chức năng ngoài registry · {len(by_id)} status on-car")
    return 0


if __name__ == "__main__":
    sys.exit(main())
