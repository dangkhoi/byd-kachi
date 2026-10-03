# Phá thử R-KC: mỗi đột biến một lượt gradle hẹp; khôi phục tệp trong finally. Chạy trong cây làm việc.
import subprocess, sys, os, re, glob
R = '<home>/Documents/workspaces/experiments/byd/byd-launcher'
CORE = 'core/src/main/kotlin/com/byd/clusternav/launcher/'
APPM = 'app/src/main/java/com/byd/clusternav/modules/voicekey/'
M = [
 ('M1 Đảo không cần đường đọc', CORE+'KeyCtlTarget.kt',
  't(KeyCtlAction.FLIP).takeIf { readable })', 't(KeyCtlAction.FLIP))'),
 ('M2 Đảo đoán khi đọc hỏng', CORE+'KeyCtlPlan.kt',
  'KeyCtlAction.FLIP -> readState()?.let { run(value = if (it > 0) 0 else 1) } ?: Outcome.Unreadable',
  'KeyCtlAction.FLIP -> readState()?.let { run(value = if (it > 0) 0 else 1) } ?: run(value = 1)'),
 ('M3 bỏ chống dồn (trong cửa sổ vẫn bắn ngay)', CORE+'KeyCtlPlan.kt',
  '        return Step.Hold(flushAt, first)', '        return Step.Fire(t, 1)'),
 ('M4 lối phát đích rẽ ctl SAU nhánh mở app', APPM+'AssistantLauncher.kt',
  '        if (KeyCtlTargets.isCtl(spec)) return KeyCtlDispatch.fire(ctx, spec)\n', ''),
 ('M5 thi hành ngay trên luồng onKeyEvent', APPM+'KeyCtlDispatch.kt',
  'MacroExec.submitSerial(ControlTileWrite.LANE) {', 'run {'),
 ('M6 runner ghi thẳng actByKind (bỏ VoiceControlDispatch)', APPM+'KeyCtlDispatch.kt',
  'is KeyCtlPlan.Outcome.Run -> controls.run(o.intent) {}',
  'is KeyCtlPlan.Outcome.Run -> { port().actByKind(def.id, o.intent.value ?: 1); Unit }'),
 ('M7 decode không hỏi registry', CORE+'KeyCtlTarget.kt',
  '        return t.takeIf { it in actionsFor(def) }', '        return t'),
 ('M8 gộp nấc mất (đếm lại từ 1 mỗi lần)', CORE+'KeyCtlPlan.kt',
  "s.count = (if (prevStep) s.count else 0) + (if (t.action == KeyCtlAction.UP) 1 else -1)",
  "s.count = (if (t.action == KeyCtlAction.UP) 1 else -1)"),
 ('M9 fresh() không đọc tươi khi màn khuất', APPM+'KeyCtlDispatch.kt',
  'c.refreshForRead(id) ?: if (c.carDemand.get() != null) null else {', 'c.refreshForRead(id) ?: if (true) null else {'),
]
TESTS = ['--tests','*KeyCtl*']
env = dict(os.environ, JAVA_HOME='/opt/homebrew/opt/openjdk@17')
out = open(sys.argv[1], 'w')
def run():
    p = subprocess.run(['./gradlew', ':core:test', *TESTS, ':app:testDebugUnitTest', *TESTS, '--continue', '-q'],
                       cwd=R, env=env, capture_output=True, text=True)
    fails = []
    for f in glob.glob(R+'/core/build/test-results/test/TEST-*KeyCtl*.xml') + glob.glob(R+'/app/build/test-results/testDebugUnitTest/TEST-*KeyCtl*.xml'):
        s = open(f).read()
        for m in re.finditer(r'<testcase name="([^"]*)" classname="([^"]*)"[^>]*>\s*<failure', s):
            fails.append(m.group(2).split('.')[-1] + ' › ' + m.group(1))
    comp = 'e: ' in p.stdout + p.stderr
    return p.returncode, fails, comp
for name, path, old, new in M:
    fp = os.path.join(R, path); src = open(fp, encoding='utf-8').read()
    if old not in src:
        out.write(f'{name}: KHÔNG ÁP ĐƯỢC (mốc không có)\n'); out.flush(); continue
    try:
        open(fp, 'w', encoding='utf-8').write(src.replace(old, new, 1))
        rc, fails, comp = run()
        verdict = 'ĐỎ' if (rc != 0 and (fails or comp)) else ('SỐNG' if rc == 0 else f'rc={rc}')
        out.write(f'{name}: {verdict} rc={rc} compileErr={comp} · {len(fails)} bài đỏ: {fails[:4]}\n'); out.flush()
    finally:
        open(fp, 'w', encoding='utf-8').write(src)
rc, fails, comp = run()
out.write(f'KHÔI PHỤC: rc={rc} · {len(fails)} đỏ\n'); out.close()
