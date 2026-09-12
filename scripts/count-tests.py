#!/usr/bin/env python3
"""Đếm kết quả test bằng cách ĐỌC XML của JUnit, không tin dòng `BUILD SUCCESSFUL`.

Lý do tồn tại: dự án đã [ĐO] được `BUILD SUCCESSFUL` + `UP-TO-DATE` trong khi bài canh
KHÔNG hề chạy (bộ niêm phong T11 — dấu xanh giả). Nguồn sự thật là tệp kết quả.
"""
import glob
import sys
import xml.etree.ElementTree as ET

tests = failures = errors = skipped = 0
files = 0
bad = []

for path in glob.glob("*/build/test-results/**/TEST-*.xml", recursive=True):
    files += 1
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:                      # tệp hỏng = KHÔNG được coi là xanh
        bad.append(f"{path}: parse error {exc}")
        continue
    tests += int(root.get("tests", 0))
    f = int(root.get("failures", 0))
    e = int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    failures += f
    errors += e
    if f or e:
        bad.append(f"{path}: failures={f} errors={e}")

print(f"XML files : {files}")
print(f"tests     : {tests}")
print(f"failures  : {failures}")
print(f"errors    : {errors}")
print(f"skipped   : {skipped}")
for b in bad:
    print("RED ->", b)
print("VERDICT:", "GREEN" if (failures == 0 and errors == 0 and not bad and tests > 0) else "RED")
sys.exit(0 if (failures == 0 and errors == 0 and not bad and tests > 0) else 1)
