# Phá thử FIX286 R-SC: mỗi đột biến → gradle hẹp → đọc XML → trả NGUYÊN tệp (finally). Chạy tuần tự.
import subprocess, glob, re, os, sys
R = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../..')) + '/'   # gốc repo
C = 'core/src/main/kotlin/com/byd/clusternav/launcher/'
L = 'app/src/main/java/com/byd/clusternav/launcher/'
LOG = os.path.dirname(os.path.abspath(__file__)) + '/mut/'
os.makedirs(LOG, exist_ok=True)
CORE_T = ['*ShortcutPlanTest', '*SlotPresenceTest', '*SlotOverlayTest']
APP_T = ['*ShortcutsWiringContractTest']
M = [
    ('M1 dong3-cu-o-widget-toan-man', C + 'ShortcutPlan.kt',
     "                    m == n -> when (i.presence) {",
     "                    i.slots.getOrNull(n).let { it is SlotContent.Widget || it is SlotContent.AppWidget } -> ShortcutAction.OpenFull(null)\n                    m == n -> when (i.presence) {"),
    ('M2 dong4-bo-phep-do', C + 'ShortcutPlan.kt',
     "SlotPresence.GONE -> ShortcutAction.Reopen(n)", "SlotPresence.GONE -> ShortcutAction.Noop(highlight = n)"),
    ('M3 dong12-bo-mo-lai', C + 'ShortcutPlan.kt',
     "m >= 0 && i.presence == SlotPresence.GONE -> ShortcutAction.Reopen(m)", "false && i.presence == SlotPresence.GONE -> ShortcutAction.Reopen(m)"),
    ('M4 khong-bao-gio-do', C + 'ShortcutPlan.kt',
     "is ShortcutMode.Slot -> if (mode.n <= i.slotCount && m == mode.n - 1) m else -1", "is ShortcutMode.Slot -> -1"),
    ('M5 cho-khac-thanh-da-dong', C + 'SlotPresence.kt', "                else -> ELSEWHERE", "                else -> GONE"),
    ('M6 doc-rong-thanh-da-dong', C + 'SlotPresence.kt', "            if (entries.isEmpty()) return UNKNOWN\n", ""),
    ('M7 bo-rao-dang-mo-do', L + 'VdAppHost.kt', "pkg != expect || busy) return false", "pkg != expect) return false"),
    ('M8 bo-thoi-do-truoc', L + 'VdAppHost.kt', "SlotLiveProbe.unwatch(probeKey); full.reset(); reopen(); return true",
     "full.reset(); reopen(); return true"),
    ('M9 quyet-truoc-khi-do', L + 'KachiHomeShortcuts.kt', "if (probe < 0) return decideAndAct(sc, input, count, stages)",
     "if (probe < 99) return decideAndAct(sc, input, count, stages)"),
    ('M10 do-gia', L + 'KachiHomeSlots.kt', "val p = SlotPresence.of(out, pkg, stage.vd)", "val p = SlotPresence.GONE"),
    ('M11 id-widget-theo-lop-tam', C + 'AppWidgetIds.kt', "        idsIn(state.workspace) + state.widgetIdsOtherProfiles",
     "        idsIn(state.effectiveWorkspace) + state.widgetIdsOtherProfiles"),
]


def run(name, mod):
    tag = name.split()[0]
    log = LOG + tag + '.log'
    tests = CORE_T if mod == 'core' else APP_T
    task = ':core:test' if mod == 'core' else ':app:testDebugUnitTest'
    args = ['./gradlew', task]
    for t in tests:
        args += ['--tests', t]
    args += ['--continue']
    env = dict(os.environ, JAVA_HOME='/opt/homebrew/opt/openjdk@17')
    with open(log, 'w') as f:
        rc = subprocess.call(args, cwd=R, stdout=f, stderr=subprocess.STDOUT, env=env)
    res = R + ('core/build/test-results/test/' if mod == 'core' else 'app/build/test-results/testDebugUnitTest/')
    fails = []
    for x in glob.glob(res + '*.xml'):
        if not any(t[1:] in x for t in tests):
            continue
        s = open(x, encoding='utf-8').read()
        for tc in re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure', s):
            fails.append(x.split('.')[-2] + '.' + tc)
    comp = 'e: ' in open(log).read()
    return rc, fails, comp


ONLY = sys.argv[1:]
for name, f, old, new in M:
    if ONLY and name.split()[0] not in ONLY:
        continue
    p = R + f
    orig = open(p, encoding='utf-8').read()
    assert orig.count(old) == 1, (name, orig.count(old))
    open(p, 'w', encoding='utf-8').write(orig.replace(old, new))
    try:
        mod = 'app' if f.startswith('app/') else 'core'
        rc, fails, comp = run(name, mod)
    finally:
        open(p, 'w', encoding='utf-8').write(orig)
    print(f'{name}: rc={rc} compile_err={comp} red={len(fails)} {fails}', flush=True)
