# Phá thử R-KC: mỗi đột biến một lượt gradle hẹp; khôi phục tệp trong finally. Chạy trong cây làm việc.
import subprocess, sys, os, re, glob
R = '<home>/Documents/workspaces/experiments/byd/byd-launcher'
CORE = 'core/src/main/kotlin/com/byd/clusternav/launcher/'
APPM = 'app/src/main/java/com/byd/clusternav/modules/voicekey/'
M = [
 ('M6b runner ghi thẳng cổng xe (bỏ VoiceControlDispatch)', APPM+'KeyCtlDispatch.kt',
  'is KeyCtlPlan.Outcome.Run -> controls.run(o.intent) {}',
  'is KeyCtlPlan.Outcome.Run -> { port().toggle(def.id, (o.intent.value ?: 1) > 0); Unit }'),
 ('M9b solo không ghim (đọc hết khi màn khuất)', CORE+'CarDataDemand.kt',
  '            value = emptySet()\n            return try', '            return try'),
 ('M10 solo không trả nhu cầu về null', CORE+'CarDataDemand.kt',
  'finally { if (value?.isEmpty() == true) value = null }', 'finally { }'),
 ('M11 fresh() bỏ nhánh màn khuất', APPM+'KeyCtlDispatch.kt',
  'c.refreshForRead(id) ?: c.carDemand.withSoloIfIdle(setOf(id)) { c.carStatusRepository.refreshNow() }', 'c.refreshForRead(id)'),
 ('M12 solo ghi đè nhu cầu thật của màn', CORE+'CarDataDemand.kt',
  'finally { if (value?.isEmpty() == true) value = null }', 'finally { value = null }'),
]
TESTS = ['--tests','*KeyCtl*','--tests','*CarDataDemand*']
env = dict(os.environ, JAVA_HOME='/opt/homebrew/opt/openjdk@17')
out = open(sys.argv[1], 'w')
def run():
    p = subprocess.run(['./gradlew', ':core:test', *TESTS, ':app:testDebugUnitTest', *TESTS, '--continue', '-q'],
                       cwd=R, env=env, capture_output=True, text=True)
    fails = []
    for f in glob.glob(R+'/core/build/test-results/test/TEST-*KeyCtl*.xml') + glob.glob(R+'/core/build/test-results/test/TEST-*CarDataDemand*.xml') + glob.glob(R+'/app/build/test-results/testDebugUnitTest/TEST-*KeyCtl*.xml'):
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
