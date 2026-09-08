# Diagnostic: Verification LUÔN fail trên xe — 2026-08-01 sáng

## Triệu chứng

- Cast THÀNH CÔNG (app lên cụm, `am stack list` xác nhận visible=true trên display 1)
- Verification LUÔN "diverged" → transaction RECOVERING
- Nút nổi LUÔN trắng (không nhận ra app đang trên cụm)
- Bấm nút → báo lỗi (Stop bị chặn hoặc "cần phục hồi")

## Đã thử và KHÔNG giúp

1. Tăng delay 0.5s → 3s: không giúp
2. Grace period (2 attempts): không giúp (vì observation Unknown ngay)
3. Skip 2-sample gate cho CAST: không giúp
4. Fix appops "No operations" format: không giúp (xe dùng `dumpsys appops` có format đúng)
5. Relaxed verification (accept target+display match): không giúp
6. Ngắt adb laptop: không giúp

## Root cause hypothesis (CHƯA CHỨNG MINH)

**Observation transport (`ObservedStateReader`) trả `Unknown` NGAY TẠI verify() — `completeVerificationLocked` KHÔNG ĐƯỢC GỌI.**

Flow:
```
verify() → awaitSettle(3s) → observation.read() → Unknown → return VerificationPending
```

`VerificationPending` rồi caller nào đó set `stopRequested` hoặc transaction expire → RECOVERING.

**Tại sao observation Unknown:**
- Transport dùng dadb connect localhost:5555
- `pm clear` XOÁ ADB key lưu trong app data (`AdbKeys.ensure(app)`)
- Lần connect tiếp → cần auth → hiện prompt "Allow USB debugging"
- Prompt chờ user bấm → timeout 4s → ShellResult.Timeout → Unknown

**Bằng chứng gián tiếp:** User báo "hỏi Allow ADB hoài" — đó chính là prompt từ app connect localhost.

## Verification cần sửa OFF-CAR

1. **ADB key persistence**: Không xoá key khi `pm clear`. Hoặc lưu key NGOÀI app data (shared prefs với backup, hoặc system property).
2. **Verification fallback**: Khi observation Unknown → KHÔNG fail ngay. Cho phép retry nhiều lần (hiện verify() chỉ retry 2 lần).
3. **Hoặc bypass verification cho CAST**: Cast đã gửi lệnh thành công (ledger OBSERVED) → journal ACTIVE_VERIFIED ngay, không cần observation verify. Verification chỉ cần cho BOOTSTRAP/STOP.

## Bubble 3 ô TRẮNG

Là HỆ QUẢ của verification fail:
- Cast → RECOVERING (verification Unknown)
- `stableSession` vẫn IDLE_VERIFIED (chưa chuyển sang ACTIVE)
- `activeTargetPackage = null` (vì chưa ghi active target)
- Observation refresh cũng Unknown → `occupancy = Unmeasured`
- → Cả 3 ô trắng (không biết có gì trên cụm)

## Cách tiến tiếp

1. Fix ADB key persistence (không mất khi clear data)
2. HOẶC: bypass observation-based verification cho CAST/SWITCH — dùng ledger "all steps OBSERVED" làm đủ điều kiện ghi ACTIVE_VERIFIED
3. Bubble: khi observation Unknown nhưng stableSession cho biết vừa cast xong → hiện ô xanh anyway (trust journal)
