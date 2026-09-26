# Bảng đo tương phản — P1b · R8 chọn màu (AC8.5)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-21 · **Mục đích**: bảng đo tương phản P1b · R8 chọn màu, AC8.5 (sinh bằng máy — đừng sửa tay).

> SINH BẰNG MÁY từ `KachiPalette` + `KachiPaletteDerive` bởi `ColorChoiceContractTest`. **Không sửa tay.**
> Mỗi dòng: mực TỆ NHẤT trong mọi cặp chữ/nền của lựa chọn đó, SAU khi `ContrastGuard` đã tự chỉnh.

| Bảng · màu nhấn · tông | accent | gradFrom→To | ON_ACCENT | Cặp tệ nhất | Đo được | Kết |
|---|---|---|---|---|---|---|
| TỐI · KACHI_BLUE · NEUTRAL | `#4d86ff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · KACHI_BLUE · WARM | `#4d86ff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · KACHI_BLUE · COOL | `#4d86ff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · VIOLET · NEUTRAL | `#8160ff` | `#6044bc`→`#9b4fbf` | `#ffffff` | ON_ACCENT trên gradTo | **4.92** | ✅ |
| TỐI · VIOLET · WARM | `#8160ff` | `#6044bc`→`#9b4fbf` | `#ffffff` | ON_ACCENT trên gradTo | **4.92** | ✅ |
| TỐI · VIOLET · COOL | `#8160ff` | `#6044bc`→`#9b4fbf` | `#ffffff` | ON_ACCENT trên gradTo | **4.92** | ✅ |
| TỐI · TEAL · NEUTRAL | `#46d9c7` | `#3fb3aa`→`#448fc1` | `#141b30` | INK trên surfOnFrom+bg | **4.60** | ✅ |
| TỐI · TEAL · WARM | `#46d9c7` | `#3fb3aa`→`#448fc1` | `#141b30` | INK trên surfOnFrom+bg | **4.60** | ✅ |
| TỐI · TEAL · COOL | `#46d9c7` | `#3fb3aa`→`#448fc1` | `#141b30` | INK trên surfOnFrom+bg | **4.60** | ✅ |
| TỐI · AMBER · NEUTRAL | `#f6bb47` | `#d5a93a`→`#c0dc46` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.67** | ✅ |
| TỐI · AMBER · WARM | `#f6bb47` | `#d5a93a`→`#c0dc46` | `#141b30` | INK trên surfOnFrom+bg | **4.94** | ✅ |
| TỐI · AMBER · COOL | `#f6bb47` | `#d5a93a`→`#c0dc46` | `#141b30` | INK trên surfOnFrom+bg | **4.94** | ✅ |
| TỐI · CHERRY · NEUTRAL | `#f1709b` | `#d65e80`→`#dc7f6a` | `#141b30` | ON_ACCENT trên gradFrom | **4.70** | ✅ |
| TỐI · CHERRY · WARM | `#f1709b` | `#d65e80`→`#dc7f6a` | `#141b30` | ON_ACCENT trên gradFrom | **4.70** | ✅ |
| TỐI · CHERRY · COOL | `#f1709b` | `#d65e80`→`#dc7f6a` | `#141b30` | ON_ACCENT trên gradFrom | **4.70** | ✅ |
| TỐI · CORAL · NEUTRAL | `#ff5f5f` | `#e3564e`→`#e8a55c` | `#141b30` | INK_ON_ACCENT trên tileOnTo+tile | **4.58** | ✅ |
| TỐI · CORAL · WARM | `#ff5f5f` | `#e3564e`→`#e8a55c` | `#141b30` | ON_ACCENT trên gradFrom | **4.64** | ✅ |
| TỐI · CORAL · COOL | `#ff5f5f` | `#e3564e`→`#e8a55c` | `#141b30` | ON_ACCENT trên gradFrom | **4.64** | ✅ |
| TỐI · SILVER · NEUTRAL | `#aab6c6` | `#949eaf`→`#a19eb8` | `#141b30` | INK trên surfOnFrom+bg | **4.81** | ✅ |
| TỐI · SILVER · WARM | `#aab6c6` | `#949eaf`→`#a19eb8` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.63** | ✅ |
| TỐI · SILVER · COOL | `#aab6c6` | `#949eaf`→`#a19eb8` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.64** | ✅ |
| TỐI · WARM_WHITE · NEUTRAL | `#e6d0b5` | `#d0bd9d`→`#d3d7a9` | `#141b30` | INK_ON_ACCENT trên tileOnTo+tile | **4.87** | ✅ |
| TỐI · WARM_WHITE · WARM | `#e6d0b5` | `#d0bd9d`→`#d3d7a9` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.59** | ✅ |
| TỐI · WARM_WHITE · COOL | `#e6d0b5` | `#d0bd9d`→`#d3d7a9` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.65** | ✅ |
| TỐI · FROM_ART(trắng) · NEUTRAL | `#b1b1b1` | `#9a9a9a`→`#a4a4a4` | `#141b30` | INK trên surfOnFrom+bg | **4.94** | ✅ |
| TỐI · FROM_ART(đen) · NEUTRAL | `#969696` | `#686a6f`→`#6f7175` | `#ffffff` | ON_ACCENT trên gradTo | **4.89** | ✅ |
| TỐI · FROM_ART(đỏ rực) · NEUTRAL | `#f53c3c` | `#922f31`→`#976738` | `#ffffff` | ON_ACCENT trên gradTo | **4.87** | ✅ |
| TỐI · FROM_ART(xám) · NEUTRAL | `#969696` | `#686a6f`→`#6f7175` | `#ffffff` | ON_ACCENT trên gradTo | **4.89** | ✅ |
| TỐI · FROM_ART(trắng) · WARM | `#b1b1b1` | `#9a9a9a`→`#a4a4a4` | `#141b30` | INK_ON_ACCENT trên tileOnTo+tile | **4.60** | ✅ |
| TỐI · FROM_ART(đen) · WARM | `#969696` | `#686a6f`→`#6f7175` | `#ffffff` | ON_ACCENT trên gradTo | **4.89** | ✅ |
| TỐI · FROM_ART(đỏ rực) · WARM | `#f53c3c` | `#922f31`→`#976738` | `#ffffff` | INK_ON_ACCENT trên tileOnTo+tile | **4.63** | ✅ |
| TỐI · FROM_ART(xám) · WARM | `#969696` | `#686a6f`→`#6f7175` | `#ffffff` | ON_ACCENT trên gradTo | **4.89** | ✅ |
| TỐI · FROM_ART(trắng) · COOL | `#b1b1b1` | `#9a9a9a`→`#a4a4a4` | `#141b30` | INK_ON_ACCENT trên tileOnTo+tile | **4.68** | ✅ |
| TỐI · FROM_ART(đen) · COOL | `#969696` | `#686a6f`→`#6f7175` | `#ffffff` | ON_ACCENT trên gradTo | **4.89** | ✅ |
| TỐI · FROM_ART(đỏ rực) · COOL | `#f53c3c` | `#922f31`→`#976738` | `#ffffff` | ACCENT_INK trên tile | **4.71** | ✅ |
| TỐI · FROM_ART(xám) · COOL | `#969696` | `#686a6f`→`#6f7175` | `#ffffff` | ON_ACCENT trên gradTo | **4.89** | ✅ |
| SÁNG · KACHI_BLUE · NEUTRAL | `#2f5ae0` | `#2f5ae0`→`#5b3ee0` | `#ffffff` | mut2 trên slotTo | **4.78** | ✅ |
| SÁNG · KACHI_BLUE · WARM | `#2f5ae0` | `#2f5ae0`→`#5b3ee0` | `#ffffff` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · KACHI_BLUE · COOL | `#2f5ae0` | `#2f5ae0`→`#5b3ee0` | `#ffffff` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · VIOLET · NEUTRAL | `#633ce4` | `#8a6ee9`→`#c179e9` | `#0f1620` | ON_ACCENT trên gradFrom | **4.75** | ✅ |
| SÁNG · VIOLET · WARM | `#633ce4` | `#8a6ee9`→`#c179e9` | `#0f1620` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · VIOLET · COOL | `#633ce4` | `#8a6ee9`→`#c179e9` | `#0f1620` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · TEAL · NEUTRAL | `#13b8a7` | `#13b8a7`→`#178cc3` | `#0f1620` | mut2 trên slotTo | **4.78** | ✅ |
| SÁNG · TEAL · WARM | `#13b8a7` | `#13b8a7`→`#178cc3` | `#0f1620` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · TEAL · COOL | `#13b8a7` | `#13b8a7`→`#178cc3` | `#0f1620` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · AMBER · NEUTRAL | `#c98208` | `#c98208`→`#cbd50b` | `#0f1620` | mut2 trên slotTo | **4.78** | ✅ |
| SÁNG · AMBER · WARM | `#c98208` | `#c98208`→`#cbd50b` | `#0f1620` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · AMBER · COOL | `#c98208` | `#c98208`→`#cbd50b` | `#0f1620` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · CHERRY · NEUTRAL | `#c3487c` | `#c9628f`→`#cb6d6e` | `#0f1620` | accentInk trên slotTo | **4.66** | ✅ |
| SÁNG · CHERRY · WARM | `#c3487c` | `#c9628f`→`#cb6d6e` | `#0f1620` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · CHERRY · COOL | `#c3487c` | `#c9628f`→`#cb6d6e` | `#0f1620` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · CORAL · NEUTRAL | `#d43c3c` | `#d85759`→`#d99665` | `#0f1620` | accentInk trên slotTo | **4.63** | ✅ |
| SÁNG · CORAL · WARM | `#d43c3c` | `#d85759`→`#d99665` | `#0f1620` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · CORAL · COOL | `#d43c3c` | `#d85759`→`#d99665` | `#0f1620` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · SILVER · NEUTRAL | `#707d8d` | `#7a8696`→`#82839c` | `#0f1620` | mut2 trên slotTo | **4.78** | ✅ |
| SÁNG · SILVER · WARM | `#707d8d` | `#7a8696`→`#82839c` | `#0f1620` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · SILVER · COOL | `#707d8d` | `#7a8696`→`#82839c` | `#0f1620` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · WARM_WHITE · NEUTRAL | `#93816b` | `#93816b`→`#999874` | `#0f1620` | accentInk trên slotTo | **4.52** | ✅ |
| SÁNG · WARM_WHITE · WARM | `#93816b` | `#93816b`→`#999874` | `#0f1620` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · WARM_WHITE · COOL | `#93816b` | `#93816b`→`#999874` | `#0f1620` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · FROM_ART(trắng) · NEUTRAL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(đen) · NEUTRAL | `#676767` | `#676767`→`#6f6f6f` | `#ffffff` | mut2 trên slotTo | **4.78** | ✅ |
| SÁNG · FROM_ART(đỏ rực) · NEUTRAL | `#f30d0d` | `#f33031`→`#f18a3f` | `#0f1620` | ON_ACCENT trên gradFrom | **4.57** | ✅ |
| SÁNG · FROM_ART(xám) · NEUTRAL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(trắng) · WARM | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(đen) · WARM | `#676767` | `#676767`→`#6f6f6f` | `#ffffff` | mut2 trên slotTo | **4.57** | ✅ |
| SÁNG · FROM_ART(đỏ rực) · WARM | `#f30d0d` | `#f33031`→`#f18a3f` | `#0f1620` | accentInk trên slotTo | **4.52** | ✅ |
| SÁNG · FROM_ART(xám) · WARM | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(trắng) · COOL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(đen) · COOL | `#676767` | `#676767`→`#6f6f6f` | `#ffffff` | mut2 trên slotTo | **4.56** | ✅ |
| SÁNG · FROM_ART(đỏ rực) · COOL | `#f30d0d` | `#f33031`→`#f18a3f` | `#0f1620` | accentInk trên slotTo | **4.51** | ✅ |
| SÁNG · FROM_ART(xám) · COOL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
