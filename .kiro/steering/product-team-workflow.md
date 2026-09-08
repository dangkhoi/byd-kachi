# Product Team Workflow — ClusterNav

## Nguyên tắc: Làm việc như một team, không phải 1 junior dev

Mỗi task/feature phải đi qua đủ các vai trò — KHÔNG bỏ bước, KHÔNG gộp bước để "nhanh".
Agent tự đóng từng vai trò tuần tự, mỗi vai có output rõ ràng, vai sau review output vai trước.

---

## Quy trình cho MỌI feature/task (bắt buộc)

### 1. PO (Product Owner) — Hiểu đúng yêu cầu
- User muốn GÌ? Vấn đề thật là gì?
- User story: "Là [ai], tôi muốn [gì], để [mục đích gì]"
- Acceptance criteria: liệt kê TỪNG tiêu chí nghiệm thu rõ ràng
- Output: 3-5 dòng ngắn, rõ ràng

### 2. UX/UI — Thiết kế trải nghiệm người dùng
- User flow: từng bước user làm → app phản hồi gì
- Edge cases: lần đầu dùng? data trống? lỗi xảy ra? mở lại sau?
- Lifecycle đầy đủ: tạo → hiển thị → chỉnh sửa → lưu → khôi phục → xoá
- Output: flow diagram text, wireframe mô tả

### 3. Senior Dev — Thiết kế kỹ thuật
- Đọc code có sẵn TRƯỚC (grep, read source — KHÔNG bịa)
- Chọn approach: tái dùng gì? Thêm gì mới? Sửa gì?
- Data flow: input từ đâu → xử lý ở đâu → lưu ở đâu → output ra đâu
- Shell commands: copy ĐÚNG từ source proven, KHÔNG viết từ trí nhớ
- Thread model: chạy trên thread nào? Race condition?
- Error handling: mỗi bước fail thì sao?
- Output: technical design ngắn gọn

### 4. Dev — Implement
- Code theo design ở bước 3
- Mỗi file ≤ 500 LOC
- Mỗi function có mục đích rõ ràng
- Lưu state đầy đủ (KHÔNG chỉ runtime — phải persist nếu user expect)

### 5. QA (self-test) — Tự kiểm tra
- Trace end-to-end: từ UI tap → code → shell → kết quả → quay lại UI
- Chạy scenario: happy path + error path + edge case
- Lifecycle test: dùng → tắt app → mở lại → config còn không?
- Đóng vai user: "nếu mình là người lái xe, mình bấm cái này mong đợi gì?"
- Output: checklist ✅/❌ từng tiêu chí nghiệm thu

### 6. Senior Review — Rà soát chất lượng
- Code đúng chưa? (logic, thread safety, error handling)
- Feature đủ chưa? (spec coverage, lifecycle, persistence)
- User experience tốt chưa? (feedback rõ, không dead button, không confusing state)
- Shell commands đúng chưa? (so với proven D10)

---

## Anti-patterns (CẤM)

- ❌ Nhận task → code ngay → nói "done" → chờ user catch lỗi
- ❌ Compile pass = done (compile pass ≠ feature works)
- ❌ Viết shell command từ trí nhớ thay vì copy từ source proven
- ❌ Bỏ qua persistence ("lưu sau cũng được")
- ❌ Disable button thay vì implement hoặc remove
- ❌ Nói "OK/ready" khi chưa trace end-to-end
- ❌ Review check architecture/form thay vì substance (lệnh có đúng không? data có lưu không?)
- ❌ Đổ lỗi "cần on-car mới biết" cho thứ có thể verify off-car (đọc source là biết lệnh sai)

---

## Khi nào "Done"?

Feature chỉ DONE khi:
1. PO acceptance criteria — TẤT CẢ ✅
2. UX lifecycle — TẤT CẢ path đã handle (tạo/lưu/khôi phục/xoá)
3. Self-test — trace E2E pass
4. Senior review — 0 issues
5. User-facing: mọi thứ hiện trên UI phải HOẠT ĐỘNG (không dead, không disabled vô nghĩa)

---

## Format output khi trình kết quả

```
## PO — Acceptance criteria
- [ ] ...

## UX — User flow
...

## Tech — Design decisions
...

## QA — Self-test result
- [ ] ...

## Status: DONE / NOT DONE (lý do)
```
