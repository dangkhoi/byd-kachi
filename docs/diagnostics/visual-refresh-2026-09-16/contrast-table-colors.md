# Bảng đo tương phản — P1b · R8 chọn màu (AC8.5)

> SINH BẰNG MÁY từ `KachiPalette` + `KachiPaletteDerive` bởi `ColorChoiceContractTest`. **Không sửa tay.**
> Mỗi dòng: mực TỆ NHẤT trong mọi cặp chữ/nền của lựa chọn đó, SAU khi `ContrastGuard` đã tự chỉnh.

| Bảng · màu nhấn · tông | accent | gradFrom→To | ON_ACCENT | Cặp tệ nhất | Đo được | Kết |
|---|---|---|---|---|---|---|
| TỐI · KACHI_BLUE · NEUTRAL | `#4c7dff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · KACHI_BLUE · WARM | `#4c7dff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · KACHI_BLUE · COOL | `#4c7dff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · VIOLET · NEUTRAL | `#8160ff` | `#6148c8`→`#a353cb` | `#ffffff` | ON_ACCENT trên gradTo | **4.51** | ✅ |
| TỐI · VIOLET · WARM | `#8160ff` | `#6148c8`→`#a353cb` | `#ffffff` | ON_ACCENT trên gradTo | **4.51** | ✅ |
| TỐI · VIOLET · COOL | `#8160ff` | `#6148c8`→`#a353cb` | `#ffffff` | ON_ACCENT trên gradTo | **4.51** | ✅ |
| TỐI · TEAL · NEUTRAL | `#46d9c6` | `#3fb3a5`→`#4595c1` | `#141b30` | INK trên surfOnFrom+bg | **4.62** | ✅ |
| TỐI · TEAL · WARM | `#46d9c6` | `#3fb3a5`→`#4595c1` | `#141b30` | INK trên surfOnFrom+bg | **4.62** | ✅ |
| TỐI · TEAL · COOL | `#46d9c6` | `#3fb3a5`→`#4595c1` | `#141b30` | INK trên surfOnFrom+bg | **4.62** | ✅ |
| TỐI · AMBER · NEUTRAL | `#f5bb47` | `#d5a23b`→`#c7dc47` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.67** | ✅ |
| TỐI · AMBER · WARM | `#f5bb47` | `#d5a23b`→`#c7dc47` | `#141b30` | mut2 trên surfFrom | **4.96** | ✅ |
| TỐI · AMBER · COOL | `#f5bb47` | `#d5a23b`→`#c7dc47` | `#141b30` | mut2 trên surfFrom | **5.00** | ✅ |
| TỐI · CHERRY · NEUTRAL | `#f16f9b` | `#d65e86`→`#dc7a6b` | `#141b30` | ON_ACCENT trên gradFrom | **4.72** | ✅ |
| TỐI · CHERRY · WARM | `#f16f9b` | `#d65e86`→`#dc7a6b` | `#141b30` | ON_ACCENT trên gradFrom | **4.72** | ✅ |
| TỐI · CHERRY · COOL | `#f16f9b` | `#d65e86`→`#dc7a6b` | `#141b30` | ON_ACCENT trên gradFrom | **4.72** | ✅ |
| TỐI · CORAL · NEUTRAL | `#ff5f5f` | `#913b43`→`#956a4b` | `#ffffff` | ON_ACCENT trên gradTo | **4.73** | ✅ |
| TỐI · CORAL · WARM | `#ff5f5f` | `#913b43`→`#956a4b` | `#ffffff` | ON_ACCENT trên gradTo | **4.73** | ✅ |
| TỐI · CORAL · COOL | `#ff5f5f` | `#913b43`→`#956a4b` | `#ffffff` | INK_ON_ACCENT trên tileOnTo+tile | **4.61** | ✅ |
| TỐI · SILVER · NEUTRAL | `#a9b5c6` | `#949faf`→`#a09eb8` | `#141b30` | INK trên surfOnFrom+bg | **4.76** | ✅ |
| TỐI · SILVER · WARM | `#a9b5c6` | `#949faf`→`#a09eb8` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.63** | ✅ |
| TỐI · SILVER · COOL | `#a9b5c6` | `#949faf`→`#a09eb8` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.65** | ✅ |
| TỐI · WARM_WHITE · NEUTRAL | `#e6d0b5` | `#d1bb9e`→`#d6d8aa` | `#141b30` | INK_ON_ACCENT trên tileOnTo+tile | **4.88** | ✅ |
| TỐI · WARM_WHITE · WARM | `#e6d0b5` | `#d1bb9e`→`#d6d8aa` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.59** | ✅ |
| TỐI · WARM_WHITE · COOL | `#e6d0b5` | `#d1bb9e`→`#d6d8aa` | `#141b30` | INK_ON_ACCENT trên tileOnFrom+tile | **4.65** | ✅ |
| TỐI · FROM_ART(trắng) · NEUTRAL | `#b1b1b1` | `#9b9b9b`→`#a4a4a4` | `#141b30` | INK trên surfOnFrom+bg | **4.91** | ✅ |
| TỐI · FROM_ART(đen) · NEUTRAL | `#969696` | `#686a6f`→`#707176` | `#ffffff` | ON_ACCENT trên gradTo | **4.87** | ✅ |
| TỐI · FROM_ART(đỏ rực) · NEUTRAL | `#f53c3c` | `#9d2b31`→`#a26939` | `#ffffff` | ON_ACCENT trên gradTo | **4.55** | ✅ |
| TỐI · FROM_ART(xám) · NEUTRAL | `#969696` | `#686a6f`→`#707176` | `#ffffff` | ON_ACCENT trên gradTo | **4.87** | ✅ |
| TỐI · FROM_ART(trắng) · WARM | `#b1b1b1` | `#9b9b9b`→`#a4a4a4` | `#141b30` | INK_ON_ACCENT trên tileOnTo+tile | **4.67** | ✅ |
| TỐI · FROM_ART(đen) · WARM | `#969696` | `#686a6f`→`#707176` | `#ffffff` | ON_ACCENT trên gradTo | **4.87** | ✅ |
| TỐI · FROM_ART(đỏ rực) · WARM | `#f53c3c` | `#9d2b31`→`#a26939` | `#ffffff` | ON_ACCENT trên gradTo | **4.55** | ✅ |
| TỐI · FROM_ART(xám) · WARM | `#969696` | `#686a6f`→`#707176` | `#ffffff` | ON_ACCENT trên gradTo | **4.87** | ✅ |
| TỐI · FROM_ART(trắng) · COOL | `#b1b1b1` | `#9b9b9b`→`#a4a4a4` | `#141b30` | INK_ON_ACCENT trên tileOnTo+tile | **4.75** | ✅ |
| TỐI · FROM_ART(đen) · COOL | `#969696` | `#686a6f`→`#707176` | `#ffffff` | ON_ACCENT trên gradTo | **4.87** | ✅ |
| TỐI · FROM_ART(đỏ rực) · COOL | `#f53c3c` | `#9d2b31`→`#a26939` | `#ffffff` | ON_ACCENT trên gradTo | **4.55** | ✅ |
| TỐI · FROM_ART(xám) · COOL | `#969696` | `#686a6f`→`#707176` | `#ffffff` | ON_ACCENT trên gradTo | **4.87** | ✅ |
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
