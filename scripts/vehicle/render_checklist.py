#!/usr/bin/env python3
"""render_checklist.py — dựng lại docs/diagnostics/artifacts/carexec-checklist.html.

Trạng thái "đã thử / OK / FAIL" đọc THẲNG từ docs/refactor-car-execution/verdicts.tsv (ledger
thật, ghi bằng `scripts/vehicle/carexec.sh verdict <candidate> ok|fail --note "..."`) — KHÔNG dùng
localStorage của trình duyệt. Muốn cập nhật trạng thái trên trang: ghi verdict bằng carexec.sh rồi
chạy lại script này, file HTML tự đổi theo, không cần mở trình duyệt để tick gì cả.

Nguồn catalog: `scripts/vehicle/carexec.sh steps` (đọc thẳng từ CarExecCatalog.kt qua runner
:car-integration:run — không gõ tay danh sách function/candidate, không thể lệch khỏi source).
"""
import csv
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "docs/refactor-car-execution/verdicts.tsv"
OUT = ROOT / "docs/diagnostics/artifacts/carexec-checklist.html"

STEP_RE = re.compile(r"^(\S+)\s+\[(\w+)\]\s+(.*)$")
CAND_RE = re.compile(r"^    - (\S+)\s+\(verdict: (\w+), rủi ro: (\w+)\)$")

FEATURES = [
    ("NAVIGATION", "Navigation"),
    ("HUD_SWITCH", "HUD Switch"),
    ("SPEED_SIGN", "Speed Sign"),
    ("CLUSTER_CAST", "Cluster Cast"),
]
NEW_STEPS = {"nav-render-gate", "hud-probe", "cluster-overlay-toggles"}


def run_carexec_steps() -> list[str]:
    result = subprocess.run(
        ["scripts/vehicle/carexec.sh", "steps"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    )
    return result.stdout.splitlines()


def parse_steps(lines: list[str]) -> list[dict]:
    steps: list[dict] = []
    cur_step = None
    cur_cand = None
    i = 0
    n = len(lines)
    while i < n and (lines[i].startswith("→") or lines[i].startswith("   lưu") or lines[i].startswith("#")):
        i += 1
    while i < n:
        line = lines[i]
        m = STEP_RE.match(line)
        if m and not line.startswith(" "):
            cur_step = {"id": m.group(1), "feature": m.group(2), "purpose": m.group(3), "precondition": "", "candidates": []}
            steps.append(cur_step)
            cur_cand = None
            i += 1
            continue
        if line.startswith("    tiền đề: "):
            cur_step["precondition"] = line[len("    tiền đề: "):]
            i += 1
            continue
        cm = CAND_RE.match(line)
        if cm:
            cur_cand = {"id": cm.group(1), "verdict": cm.group(2), "risk": cm.group(3), "purpose": "", "commands": [], "evidence": "", "fieldNote": ""}
            cur_step["candidates"].append(cur_cand)
            i += 1
            continue
        if cur_cand is not None:
            if line.startswith("        $ "):
                cur_cand["commands"].append(line[len("        $ "):])
            elif line.startswith("        đạt khi: "):
                cur_cand["evidence"] = line[len("        đạt khi: "):]
            elif line.startswith("        field: "):
                cur_cand["fieldNote"] = line[len("        field: "):]
            elif line.strip():
                cur_cand["purpose"] = (cur_cand["purpose"] + " " + line.strip()).strip()
        i += 1
    return steps


def latest_ledger_status() -> dict[str, dict]:
    """key = 'step/candidate' -> {verdict, recordedAt, note}, giữ bản ghi MỚI NHẤT."""
    status: dict[str, dict] = {}
    if not LEDGER.exists():
        return status
    with LEDGER.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            key = f"{row['step']}/{row['candidate']}"
            prev = status.get(key)
            if prev is None or row["recordedAt"] > prev["recordedAt"]:
                status[key] = {
                    "verdict": row["verdict"],
                    "recordedAt": row["recordedAt"],
                    "note": row.get("note", ""),
                    "source": row.get("source", ""),
                }
    return status


TEMPLATE = r"""<title>carexec — checklist đo trên xe</title>
<style>
:root {
  --bg: #f3f5f7; --surface: #ffffff; --surface-2: #eef1f4; --border: #dde3ea;
  --ink: #12181f; --sub: #5c6b7d; --sub-2: #8493a3;
  --accent: #0e7490; --accent-ink: #ffffff;
  --risk-read: #2563eb; --risk-rev: #0f8b5f; --risk-disrupt: #b4740e; --risk-hang: #c22b4d;
  --risk-read-bg: #e6eefc; --risk-rev-bg: #e2f5ec; --risk-disrupt-bg: #fbeed9; --risk-hang-bg: #fbe3e9;
  --ok: #0f8b5f; --ok-bg: #e2f5ec; --fail: #c22b4d; --fail-bg: #fbe3e9; --skip: #8493a3; --skip-bg: #eef1f4;
  --shadow: 0 1px 2px rgba(20,30,40,.06), 0 6px 20px rgba(20,30,40,.05);
  --mono: ui-monospace, "SF Mono", "SFMono-Regular", Menlo, Consolas, "Liberation Mono", monospace;
  --sans: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, sans-serif;
  --display: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI Semibold", Roboto, sans-serif;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #0a0d12; --surface: #131a24; --surface-2: #171f2b; --border: #232c3a;
    --ink: #eef3f8; --sub: #93a3b8; --sub-2: #6b7a8f;
    --accent: #22d3ee; --accent-ink: #06222a;
    --risk-read: #60a5fa; --risk-rev: #34d399; --risk-disrupt: #f5a623; --risk-hang: #fb5279;
    --risk-read-bg: #142238; --risk-rev-bg: #10241e; --risk-disrupt-bg: #2a1f0e; --risk-hang-bg: #2c1420;
    --ok: #34d399; --ok-bg: #10241e; --fail: #fb5279; --fail-bg: #2c1420; --skip: #6b7a8f; --skip-bg: #171f2b;
    --shadow: 0 1px 2px rgba(0,0,0,.4), 0 10px 30px rgba(0,0,0,.35);
  }
}
:root[data-theme="dark"] {
  --bg: #0a0d12; --surface: #131a24; --surface-2: #171f2b; --border: #232c3a;
  --ink: #eef3f8; --sub: #93a3b8; --sub-2: #6b7a8f;
  --accent: #22d3ee; --accent-ink: #06222a;
  --risk-read: #60a5fa; --risk-rev: #34d399; --risk-disrupt: #f5a623; --risk-hang: #fb5279;
  --risk-read-bg: #142238; --risk-rev-bg: #10241e; --risk-disrupt-bg: #2a1f0e; --risk-hang-bg: #2c1420;
  --ok: #34d399; --ok-bg: #10241e; --fail: #fb5279; --fail-bg: #2c1420; --skip: #6b7a8f; --skip-bg: #171f2b;
  --shadow: 0 1px 2px rgba(0,0,0,.4), 0 10px 30px rgba(0,0,0,.35);
}
:root[data-theme="light"] {
  --bg: #f3f5f7; --surface: #ffffff; --surface-2: #eef1f4; --border: #dde3ea;
  --ink: #12181f; --sub: #5c6b7d; --sub-2: #8493a3;
  --accent: #0e7490; --accent-ink: #ffffff;
  --risk-read: #2563eb; --risk-rev: #0f8b5f; --risk-disrupt: #b4740e; --risk-hang: #c22b4d;
  --risk-read-bg: #e6eefc; --risk-rev-bg: #e2f5ec; --risk-disrupt-bg: #fbeed9; --risk-hang-bg: #fbe3e9;
  --ok: #0f8b5f; --ok-bg: #e2f5ec; --fail: #c22b4d; --fail-bg: #fbe3e9; --skip: #8493a3; --skip-bg: #eef1f4;
  --shadow: 0 1px 2px rgba(20,30,40,.06), 0 6px 20px rgba(20,30,40,.05);
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body { background: var(--bg); color: var(--ink); font-family: var(--sans); font-size: 15px; line-height: 1.5; -webkit-font-smoothing: antialiased; }
::selection { background: var(--accent); color: var(--accent-ink); }
a { color: var(--accent); }
.wrap { max-width: 980px; margin: 0 auto; padding: 0 20px 120px; }
.topbar { position: sticky; top: 0; z-index: 20; background: color-mix(in srgb, var(--bg) 88%, transparent); backdrop-filter: blur(10px); border-bottom: 1px solid var(--border); }
.topbar-inner { max-width: 980px; margin: 0 auto; padding: 14px 20px; display: flex; flex-direction: column; gap: 12px; }
.topbar-row1 { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.brand { display: flex; align-items: baseline; gap: 10px; }
.brand-mark { font-family: var(--mono); font-size: 12px; letter-spacing: .04em; color: var(--accent-ink); background: var(--accent); padding: 3px 7px; border-radius: 4px; font-weight: 700; }
.brand h1 { font-family: var(--display); font-size: 19px; font-weight: 650; margin: 0; letter-spacing: -.01em; }
.counts { font-family: var(--mono); font-size: 12.5px; color: var(--sub); font-variant-numeric: tabular-nums; white-space: nowrap; }
.counts strong { color: var(--ink); }
.controls { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.search { flex: 1 1 220px; min-width: 160px; font-family: var(--sans); font-size: 13.5px; background: var(--surface); color: var(--ink); border: 1px solid var(--border); border-radius: 8px; padding: 7px 10px; }
.search:focus { outline: 2px solid var(--accent); outline-offset: -1px; }
.search::placeholder { color: var(--sub-2); }
.tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.tab { font-family: var(--sans); font-size: 12.5px; font-weight: 600; padding: 6px 11px; border-radius: 999px; cursor: pointer; border: 1px solid var(--border); background: var(--surface); color: var(--sub); transition: background .12s, color .12s, border-color .12s; }
.tab:hover { color: var(--ink); }
.tab.active { background: var(--accent); border-color: var(--accent); color: var(--accent-ink); }
.tab .n { opacity: .7; font-variant-numeric: tabular-nums; margin-left: 2px; }
.legend { display: flex; gap: 14px; flex-wrap: wrap; align-items: center; font-size: 12px; color: var(--sub); padding: 10px 0 2px; }
.legend-group { display: flex; gap: 7px; align-items: center; flex-wrap: wrap; }
.legend-label { text-transform: uppercase; font-size: 10.5px; letter-spacing: .06em; color: var(--sub-2); font-weight: 700; }
.pill { display: inline-flex; align-items: center; gap: 4px; font-family: var(--mono); font-size: 10.5px; font-weight: 700; letter-spacing: .02em; text-transform: uppercase; padding: 2.5px 7px; border-radius: 5px; line-height: 1.5; white-space: nowrap; }
.pill-READ_ONLY { color: var(--risk-read); background: var(--risk-read-bg); }
.pill-REVERSIBLE { color: var(--risk-rev); background: var(--risk-rev-bg); }
.pill-MAY_DISRUPT_DRIVER { color: var(--risk-disrupt); background: var(--risk-disrupt-bg); }
.pill-MAY_HANG_SYSTEM { color: var(--risk-hang); background: var(--risk-hang-bg); }
.status { display: inline-flex; align-items: center; gap: 4px; font-family: var(--mono); font-size: 10.5px; font-weight: 700; letter-spacing: .02em; text-transform: uppercase; padding: 2.5px 7px; border-radius: 5px; }
.status-OK { color: var(--ok); background: var(--ok-bg); }
.status-FAIL { color: var(--fail); background: var(--fail-bg); }
.status-SKIPPED { color: var(--skip); background: var(--skip-bg); }
.status-untried { color: var(--sub-2); background: transparent; border: 1px dashed var(--border); }
.vtag { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; color: var(--sub-2); font-weight: 600; }
.vtag svg { width: 12px; height: 12px; flex: none; }
.priority { margin: 22px 0; padding: 16px 18px; border-radius: 14px; background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 14%, var(--surface)), var(--surface)); border: 1px solid color-mix(in srgb, var(--accent) 40%, var(--border)); box-shadow: var(--shadow); }
.priority-eyebrow { font-family: var(--mono); font-size: 11px; font-weight: 700; letter-spacing: .07em; color: var(--accent); text-transform: uppercase; margin: 0 0 6px; }
.priority h2 { margin: 0 0 4px; font-family: var(--display); font-size: 17px; }
.priority p { margin: 0; color: var(--sub); font-size: 13.5px; max-width: 62ch; }
.ledger-note { margin: 8px 0 0; font-size: 12px; color: var(--sub-2); }
.ledger-note code { font-family: var(--mono); background: var(--surface-2); padding: 1px 5px; border-radius: 4px; }
.feature-section { margin-top: 40px; scroll-margin-top: 128px; }
.feature-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 14px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.feature-head h2 { font-family: var(--display); font-size: 13px; font-weight: 750; letter-spacing: .08em; text-transform: uppercase; margin: 0; color: var(--ink); }
.feature-head .sub { font-size: 12.5px; color: var(--sub-2); }
.step { background: var(--surface); border: 1px solid var(--border); border-radius: 14px; padding: 16px 18px; margin-bottom: 14px; box-shadow: var(--shadow); scroll-margin-top: 128px; }
.step-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.step-id { font-family: var(--mono); font-size: 13.5px; font-weight: 700; color: var(--ink); }
.step-new { font-size: 10px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; color: var(--accent); border: 1px solid color-mix(in srgb, var(--accent) 55%, transparent); border-radius: 999px; padding: 1.5px 7px; margin-left: 8px; }
.step-purpose { font-size: 14px; margin: 4px 0 2px; color: var(--ink); }
.step-pre { font-size: 12px; color: var(--sub-2); }
.step-pre b { color: var(--sub); font-weight: 600; }
.step-count { font-family: var(--mono); font-size: 11.5px; color: var(--sub-2); white-space: nowrap; }
.candidates { margin-top: 12px; display: flex; flex-direction: column; gap: 10px; }
.cand { border-top: 1px solid var(--border); padding-top: 10px; }
.cand-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cand-id { font-family: var(--mono); font-size: 12.5px; font-weight: 700; color: var(--ink); }
.cand-purpose { font-size: 13px; color: var(--ink); margin: 5px 0 7px; }
.cand-commands { display: flex; flex-direction: column; gap: 4px; margin-bottom: 7px; }
.cmd-row { display: flex; align-items: stretch; gap: 6px; background: var(--surface-2); border: 1px solid var(--border); border-radius: 7px; overflow: hidden; }
.cmd-text { font-family: var(--mono); font-size: 12px; padding: 6px 9px; overflow-x: auto; white-space: pre; flex: 1; color: var(--ink); }
.cmd-copy { flex: none; border: none; border-left: 1px solid var(--border); background: transparent; color: var(--sub); cursor: pointer; padding: 0 10px; font-size: 11px; font-family: var(--sans); font-weight: 600; }
.cmd-copy:hover { color: var(--accent); }
.cand-evidence { font-size: 12px; color: var(--sub); margin-bottom: 4px; }
.cand-evidence b { color: var(--sub-2); font-weight: 700; text-transform: uppercase; font-size: 10px; letter-spacing: .04em; margin-right: 5px; }
.cand-ledger { font-size: 11.5px; color: var(--sub-2); margin-bottom: 4px; }
.cand-field summary { cursor: pointer; color: var(--sub-2); font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: .03em; list-style: none; }
.cand-field summary::-webkit-details-marker { display: none; }
.cand-field summary::before { content: "field note ›"; }
.cand-field[open] summary::before { content: "field note ⌄"; }
.cand-field p { margin: 5px 0 0; color: var(--sub); font-size: 12px; line-height: 1.5; }
.empty { text-align: center; padding: 60px 20px; color: var(--sub-2); font-size: 14px; }
footer { text-align: center; color: var(--sub-2); font-size: 11.5px; margin-top: 60px; font-family: var(--mono); }
@media (max-width: 640px) { .topbar-row1 { flex-direction: column; align-items: flex-start; gap: 4px; } }
</style>

<div class="topbar">
  <div class="topbar-inner">
    <div class="topbar-row1">
      <div class="brand"><span class="brand-mark">carexec</span><h1>Checklist đo trên xe — ClusterNav</h1></div>
      <div class="counts"><strong id="count-visible">__TOTAL__</strong>/__TOTAL__ candidate · <strong>__STEPCOUNT__</strong> function · <strong id="count-ok">__OKCOUNT__</strong> OK</div>
    </div>
    <div class="controls">
      <input class="search" id="search" type="text" placeholder="Lọc theo id, mô tả, lệnh… (vd: hud, adas, setprop)" autocomplete="off" />
      <div class="tabs" id="tabs"></div>
    </div>
    <div class="legend">
      <div class="legend-group"><span class="legend-label">Rủi ro</span>
        <span class="pill pill-READ_ONLY">read only</span><span class="pill pill-REVERSIBLE">reversible</span>
        <span class="pill pill-MAY_DISRUPT_DRIVER">disrupt driver</span><span class="pill pill-MAY_HANG_SYSTEM">may hang</span>
      </div>
      <div class="legend-group"><span class="legend-label">Trạng thái</span>
        <span class="status status-OK">ok</span><span class="status status-FAIL">fail</span>
        <span class="status status-SKIPPED">skipped</span><span class="status status-untried">chưa thử</span>
      </div>
    </div>
  </div>
</div>

<div class="wrap">
  <div class="priority">
    <p class="priority-eyebrow">Ưu tiên #1 — làm trước tất cả</p>
    <h2>nav-render-gate</h2>
    <p>Mở cổng render nav zin (semon/navi_protect) — đã live-confirm dữ liệu vào cụm thật từ 2026-06-22, chỉ chưa xác nhận cổng render mở được chưa. Rẻ, không cần code mới, có thể giải quyết luôn câu hỏi VietMap + HUD.</p>
    <p class="ledger-note">Trạng thái dưới đây đọc từ <code>docs/refactor-car-execution/verdicts.tsv</code>. Ghi verdict bằng
      <code>scripts/vehicle/carexec.sh verdict &lt;candidate&gt; ok|fail --note "..."</code> rồi chạy lại
      <code>scripts/vehicle/render-checklist.sh</code> để trang này tự cập nhật — không tick trên trình duyệt.</p>
  </div>

  <div id="sections"></div>
  <div class="empty" id="empty-state" style="display:none">Không khớp candidate nào.</div>
  <footer>ClusterNav · CarExecCatalog + verdicts.tsv · dựng lại lúc __GENERATED_AT__</footer>
</div>

<script id="catalog-data" type="application/json">__CATALOG_JSON__</script>
<script>
(function () {
  var CATALOG = JSON.parse(document.getElementById('catalog-data').textContent);
  var STEPS = CATALOG.steps;
  var FEATURES = [
    { key: 'NAVIGATION', label: 'Navigation' }, { key: 'HUD_SWITCH', label: 'HUD Switch' },
    { key: 'SPEED_SIGN', label: 'Speed Sign' }, { key: 'CLUSTER_CAST', label: 'Cluster Cast' },
  ];
  var NEW_STEPS = { 'nav-render-gate': 1, 'hud-probe': 1, 'cluster-overlay-toggles': 1 };
  var eyeSvg = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/><circle cx="12" cy="12" r="3"/></svg>';
  var chipSvg = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 9h6v6H9z"/></svg>';

  var tabsEl = document.getElementById('tabs');
  var counts = {};
  FEATURES.forEach(function (f) { counts[f.key] = STEPS.filter(function (s) { return s.feature === f.key; }).length; });
  var activeFeature = 'ALL';
  function renderTabs() {
    var html = '<span class="tab' + (activeFeature === 'ALL' ? ' active' : '') + '" data-f="ALL">Tất cả<span class="n">' + STEPS.length + '</span></span>';
    FEATURES.forEach(function (f) {
      html += '<span class="tab' + (activeFeature === f.key ? ' active' : '') + '" data-f="' + f.key + '">' + f.label + '<span class="n">' + counts[f.key] + '</span></span>';
    });
    tabsEl.innerHTML = html;
    Array.prototype.forEach.call(tabsEl.querySelectorAll('.tab'), function (el) {
      el.addEventListener('click', function () { activeFeature = el.getAttribute('data-f'); renderTabs(); render(); });
    });
  }
  function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; });
  }
  function matchesQuery(step, cand, q) {
    if (!q) return true;
    var hay = [step.id, step.purpose, cand.id, cand.purpose, cand.evidence, cand.fieldNote].concat(cand.commands).join(' \n ').toLowerCase();
    return hay.indexOf(q) !== -1;
  }
  function statusNode(cand) {
    var st = cand.status;
    if (!st) return '<span class="status status-untried">chưa thử</span>';
    return '<span class="status status-' + st.verdict + '">' + st.verdict.toLowerCase() + '</span>';
  }
  function ledgerLine(cand) {
    var st = cand.status;
    if (!st) return '';
    var when = st.recordedAt ? st.recordedAt.replace('T', ' ').replace(/\.\d+Z$/, 'Z') : '';
    var note = st.note ? ' — ' + escapeHtml(st.note) : '';
    return '<div class="cand-ledger">' + when + note + '</div>';
  }
  function candidateNode(step, cand) {
    var cmdsHtml = cand.commands.map(function (c, i) {
      var cmdId = 'cmd-' + step.id + '-' + cand.id + '-' + i;
      return '<div class="cmd-row"><code class="cmd-text" id="' + cmdId + '">' + escapeHtml(c) + '</code><button class="cmd-copy" data-copy="' + cmdId + '" type="button">copy</button></div>';
    }).join('');
    var fieldHtml = cand.fieldNote ? '<details class="cand-field"><summary></summary><p>' + escapeHtml(cand.fieldNote) + '</p></details>' : '';
    return (
      '<div class="cand">' +
        '<div class="cand-head"><span class="cand-id">' + escapeHtml(cand.id) + '</span>' +
          '<span class="pill pill-' + cand.risk + '">' + cand.risk.replace(/_/g, ' ') + '</span>' +
          '<span class="vtag">' + (cand.verdict === 'HUMAN' ? eyeSvg : chipSvg) + '</span>' +
          statusNode(cand) +
        '</div>' +
        '<p class="cand-purpose">' + escapeHtml(cand.purpose) + '</p>' +
        '<div class="cand-commands">' + cmdsHtml + '</div>' +
        '<div class="cand-evidence"><b>đạt khi</b>' + escapeHtml(cand.evidence) + '</div>' +
        ledgerLine(cand) + fieldHtml +
      '</div>'
    );
  }
  function stepNode(step, visibleCandidates) {
    var newBadge = NEW_STEPS[step.id] ? '<span class="step-new">mới · 29/07</span>' : '';
    return (
      '<div class="step" id="step-' + step.id + '">' +
        '<div class="step-head"><div><span class="step-id">' + escapeHtml(step.id) + '</span>' + newBadge +
          '<div class="step-purpose">' + escapeHtml(step.purpose) + '</div>' +
          '<div class="step-pre"><b>tiền đề</b> ' + escapeHtml(step.precondition) + '</div></div>' +
          '<div class="step-count">' + visibleCandidates.length + '/' + step.candidates.length + '</div></div>' +
        '<div class="candidates">' + visibleCandidates.map(function (c) { return candidateNode(step, c); }).join('') + '</div>' +
      '</div>'
    );
  }
  function render() {
    var q = document.getElementById('search').value.trim().toLowerCase();
    var sectionsEl = document.getElementById('sections');
    var visibleTotal = 0;
    var html = '';
    var feats = activeFeature === 'ALL' ? FEATURES : FEATURES.filter(function (f) { return f.key === activeFeature; });
    feats.forEach(function (f) {
      var stepsInFeature = STEPS.filter(function (s) { return s.feature === f.key; });
      var sectionHtml = '';
      stepsInFeature.forEach(function (step) {
        var visible = step.candidates.filter(function (c) { return matchesQuery(step, c, q); });
        if (visible.length === 0) return;
        visibleTotal += visible.length;
        sectionHtml += stepNode(step, visible);
      });
      if (sectionHtml) html += '<div class="feature-section"><div class="feature-head"><h2>' + f.label + '</h2><span class="sub">' + stepsInFeature.length + ' function</span></div>' + sectionHtml + '</div>';
    });
    sectionsEl.innerHTML = html;
    document.getElementById('empty-state').style.display = visibleTotal === 0 ? 'block' : 'none';
    document.getElementById('count-visible').textContent = visibleTotal;
    Array.prototype.forEach.call(sectionsEl.querySelectorAll('.cmd-copy'), function (btn) {
      btn.addEventListener('click', function () {
        var text = document.getElementById(btn.getAttribute('data-copy')).textContent;
        var reset = function () { btn.textContent = 'copied'; setTimeout(function () { btn.textContent = 'copy'; }, 1100); };
        if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(reset, reset); else reset();
      });
    });
  }
  document.getElementById('search').addEventListener('input', render);
  renderTabs();
  render();
})();
</script>
"""


def main() -> int:
    lines = run_carexec_steps()
    steps = parse_steps(lines)
    ledger = latest_ledger_status()

    ok_count = 0
    for step in steps:
        for cand in step["candidates"]:
            key = f"{step['id']}/{cand['id']}"
            st = ledger.get(key)
            cand["status"] = st
            if st and st["verdict"] == "OK":
                ok_count += 1

    total = sum(len(s["candidates"]) for s in steps)
    data_json = json.dumps({"steps": steps}, ensure_ascii=False).replace("</script>", "<\\/script>")

    from datetime import datetime, timezone
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    html = (
        TEMPLATE
        .replace("__CATALOG_JSON__", data_json)
        .replace("__TOTAL__", str(total))
        .replace("__STEPCOUNT__", str(len(steps)))
        .replace("__OKCOUNT__", str(ok_count))
        .replace("__GENERATED_AT__", generated_at)
    )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(html, encoding="utf-8")
    print(f"đã ghi {OUT} — {len(steps)} step, {total} candidate, {ok_count} OK trong ledger")
    return 0


if __name__ == "__main__":
    sys.exit(main())
