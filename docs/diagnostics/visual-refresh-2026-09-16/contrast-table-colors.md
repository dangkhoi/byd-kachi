# Bảng đo tương phản — P1b · R8 chọn màu (AC8.5)

> SINH BẰNG MÁY từ `KachiPalette` + `KachiPaletteDerive` bởi `ColorChoiceContractTest`. **Không sửa tay.**
> Mỗi dòng: mực TỆ NHẤT trong mọi cặp chữ/nền của lựa chọn đó, SAU khi `ContrastGuard` đã tự chỉnh.

| Bảng · màu nhấn · tông | accent | gradFrom→To | ON_ACCENT | Cặp tệ nhất | Đo được | Kết |
|---|---|---|---|---|---|---|
| TỐI · KACHI_BLUE · NEUTRAL | `#4c7dff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · KACHI_BLUE · WARM | `#4c7dff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · KACHI_BLUE · COOL | `#4c7dff` | `#3f6ae0`→`#6b4ce6` | `#ffffff` | ON_ACCENT trên gradFrom | **4.83** | ✅ |
| TỐI · VIOLET · NEUTRAL | `#8160ff` | `#6046c3`→`#a151c7` | `#ffffff` | ON_ACCENT trên gradTo | **4.65** | ✅ |
| TỐI · VIOLET · WARM | `#8160ff` | `#6046c3`→`#a151c7` | `#ffffff` | ON_ACCENT trên gradTo | **4.65** | ✅ |
| TỐI · VIOLET · COOL | `#8160ff` | `#6046c3`→`#a151c7` | `#ffffff` | ON_ACCENT trên gradTo | **4.65** | ✅ |
| TỐI · TEAL · NEUTRAL | `#46d9c6` | `#3fb3a5`→`#4595c1` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.61** | ✅ |
| TỐI · TEAL · WARM | `#46d9c6` | `#3fb3a5`→`#4595c1` | `#0a0d13` | mut2 trên surfFrom | **4.95** | ✅ |
| TỐI · TEAL · COOL | `#46d9c6` | `#3fb3a5`→`#4595c1` | `#0a0d13` | mut2 trên surfFrom | **5.01** | ✅ |
| TỐI · AMBER · NEUTRAL | `#f5bb47` | `#d5a23b`→`#c7dc47` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.62** | ✅ |
| TỐI · AMBER · WARM | `#f5bb47` | `#d5a23b`→`#c7dc47` | `#0a0d13` | INK trên surfOnFrom+bg | **4.70** | ✅ |
| TỐI · AMBER · COOL | `#f5bb47` | `#d5a23b`→`#c7dc47` | `#0a0d13` | INK trên surfOnFrom+bg | **4.70** | ✅ |
| TỐI · CHERRY · NEUTRAL | `#f16f9b` | `#d65e86`→`#dc7a6b` | `#0a0d13` | ON_ACCENT trên gradFrom | **5.38** | ✅ |
| TỐI · CHERRY · WARM | `#f16f9b` | `#d65e86`→`#dc7a6b` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.93** | ✅ |
| TỐI · CHERRY · COOL | `#f16f9b` | `#d65e86`→`#dc7a6b` | `#0a0d13` | mut2 trên surfFrom | **5.01** | ✅ |
| TỐI · CORAL · NEUTRAL | `#ff5f5f` | `#e3504f`→`#e89f5c` | `#0a0d13` | INK_ON_ACCENT trên tileOnTo+tile | **4.93** | ✅ |
| TỐI · CORAL · WARM | `#ff5f5f` | `#e3504f`→`#e89f5c` | `#0a0d13` | mut2 trên surfFrom | **4.95** | ✅ |
| TỐI · CORAL · COOL | `#ff5f5f` | `#e3504f`→`#e89f5c` | `#0a0d13` | INK_ON_ACCENT trên tileOnTo+tile | **4.57** | ✅ |
| TỐI · SILVER · NEUTRAL | `#a9b5c6` | `#949faf`→`#a09eb8` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **5.07** | ✅ |
| TỐI · SILVER · WARM | `#a9b5c6` | `#949faf`→`#a09eb8` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.61** | ✅ |
| TỐI · SILVER · COOL | `#a9b5c6` | `#949faf`→`#a09eb8` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.67** | ✅ |
| TỐI · WARM_WHITE · NEUTRAL | `#e6d0b5` | `#d1bb9e`→`#d6d8aa` | `#0a0d13` | INK trên surfOnFrom+bg | **4.83** | ✅ |
| TỐI · WARM_WHITE · WARM | `#e6d0b5` | `#d1bb9e`→`#d6d8aa` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.56** | ✅ |
| TỐI · WARM_WHITE · COOL | `#e6d0b5` | `#d1bb9e`→`#d6d8aa` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.67** | ✅ |
| TỐI · FROM_ART(trắng) · NEUTRAL | `#b1b1b1` | `#9b9b9b`→`#a4a4a4` | `#0a0d13` | INK_ON_ACCENT trên tileOnTo+tile | **5.13** | ✅ |
| TỐI · FROM_ART(đen) · NEUTRAL | `#969696` | `#808080`→`#8a8a8a` | `#0a0d13` | ON_ACCENT trên gradFrom | **4.92** | ✅ |
| TỐI · FROM_ART(đỏ rực) · NEUTRAL | `#f53c3c` | `#9a2829`→`#9f6531` | `#ffffff` | ON_ACCENT trên gradTo | **4.79** | ✅ |
| TỐI · FROM_ART(xám) · NEUTRAL | `#969696` | `#808080`→`#8a8a8a` | `#0a0d13` | ON_ACCENT trên gradFrom | **4.92** | ✅ |
| TỐI · FROM_ART(trắng) · WARM | `#b1b1b1` | `#9b9b9b`→`#a4a4a4` | `#0a0d13` | INK_ON_ACCENT trên tileOnFrom+tile | **4.69** | ✅ |
| TỐI · FROM_ART(đen) · WARM | `#969696` | `#808080`→`#8a8a8a` | `#0a0d13` | ON_ACCENT trên gradFrom | **4.92** | ✅ |
| TỐI · FROM_ART(đỏ rực) · WARM | `#f53c3c` | `#9a2829`→`#9f6531` | `#ffffff` | ACCENT_INK trên surfFrom | **4.66** | ✅ |
| TỐI · FROM_ART(xám) · WARM | `#969696` | `#808080`→`#8a8a8a` | `#0a0d13` | ON_ACCENT trên gradFrom | **4.92** | ✅ |
| TỐI · FROM_ART(trắng) · COOL | `#b1b1b1` | `#9b9b9b`→`#a4a4a4` | `#0a0d13` | INK_ON_ACCENT trên tileOnTo+tile | **4.73** | ✅ |
| TỐI · FROM_ART(đen) · COOL | `#969696` | `#808080`→`#8a8a8a` | `#0a0d13` | ON_ACCENT trên gradFrom | **4.92** | ✅ |
| TỐI · FROM_ART(đỏ rực) · COOL | `#f53c3c` | `#9a2829`→`#9f6531` | `#ffffff` | ACCENT_INK trên surfFrom | **4.72** | ✅ |
| TỐI · FROM_ART(xám) · COOL | `#969696` | `#808080`→`#8a8a8a` | `#0a0d13` | ON_ACCENT trên gradFrom | **4.92** | ✅ |
| SÁNG · KACHI_BLUE · NEUTRAL | `#2f5ae0` | `#2f5ae0`→`#5b3ee0` | `#ffffff` | mut2 trên fieldSunken | **5.06** | ✅ |
| SÁNG · KACHI_BLUE · WARM | `#2f5ae0` | `#2f5ae0`→`#5b3ee0` | `#ffffff` | mut2 trên fieldSunken | **4.84** | ✅ |
| SÁNG · KACHI_BLUE · COOL | `#2f5ae0` | `#2f5ae0`→`#5b3ee0` | `#ffffff` | mut2 trên fieldSunken | **4.78** | ✅ |
| SÁNG · VIOLET · NEUTRAL | `#633ce4` | `#8a6ee8`→`#c179e8` | `#0f1620` | ON_ACCENT trên gradFrom | **4.74** | ✅ |
| SÁNG · VIOLET · WARM | `#633ce4` | `#8a6ee8`→`#c179e8` | `#0f1620` | ON_ACCENT trên gradFrom | **4.74** | ✅ |
| SÁNG · VIOLET · COOL | `#633ce4` | `#8a6ee8`→`#c179e8` | `#0f1620` | ON_ACCENT trên gradFrom | **4.74** | ✅ |
| SÁNG · TEAL · NEUTRAL | `#13b8a7` | `#13b8a7`→`#178cc3` | `#0f1620` | ON_ACCENT trên gradTo | **4.82** | ✅ |
| SÁNG · TEAL · WARM | `#13b8a7` | `#13b8a7`→`#178cc3` | `#0f1620` | ON_ACCENT trên gradTo | **4.82** | ✅ |
| SÁNG · TEAL · COOL | `#13b8a7` | `#13b8a7`→`#178cc3` | `#0f1620` | mut2 trên fieldSunken | **4.78** | ✅ |
| SÁNG · AMBER · NEUTRAL | `#c98208` | `#c98208`→`#cbd50b` | `#0f1620` | mut2 trên fieldSunken | **5.06** | ✅ |
| SÁNG · AMBER · WARM | `#c98208` | `#c98208`→`#cbd50b` | `#0f1620` | mut2 trên fieldSunken | **4.84** | ✅ |
| SÁNG · AMBER · COOL | `#c98208` | `#c98208`→`#cbd50b` | `#0f1620` | mut2 trên fieldSunken | **4.78** | ✅ |
| SÁNG · CHERRY · NEUTRAL | `#c3487c` | `#c9628f`→`#cb6d6e` | `#0f1620` | ON_ACCENT trên gradFrom | **4.87** | ✅ |
| SÁNG · CHERRY · WARM | `#c3487c` | `#c9628f`→`#cb6d6e` | `#0f1620` | accentInk trên fieldSunken | **4.72** | ✅ |
| SÁNG · CHERRY · COOL | `#c3487c` | `#c9628f`→`#cb6d6e` | `#0f1620` | accentInk trên fieldSunken | **4.66** | ✅ |
| SÁNG · CORAL · NEUTRAL | `#d43c3c` | `#d85759`→`#d99665` | `#0f1620` | ON_ACCENT trên gradFrom | **4.70** | ✅ |
| SÁNG · CORAL · WARM | `#d43c3c` | `#d85759`→`#d99665` | `#0f1620` | accentInk trên fieldSunken | **4.69** | ✅ |
| SÁNG · CORAL · COOL | `#d43c3c` | `#d85759`→`#d99665` | `#0f1620` | accentInk trên fieldSunken | **4.63** | ✅ |
| SÁNG · SILVER · NEUTRAL | `#707d8d` | `#7a8695`→`#82839c` | `#0f1620` | ON_ACCENT trên gradFrom | **4.91** | ✅ |
| SÁNG · SILVER · WARM | `#707d8d` | `#7a8695`→`#82839c` | `#0f1620` | mut2 trên fieldSunken | **4.84** | ✅ |
| SÁNG · SILVER · COOL | `#707d8d` | `#7a8695`→`#82839c` | `#0f1620` | mut2 trên fieldSunken | **4.78** | ✅ |
| SÁNG · WARM_WHITE · NEUTRAL | `#93816b` | `#93816b`→`#999874` | `#0f1620` | accentInk trên fieldSunken | **4.78** | ✅ |
| SÁNG · WARM_WHITE · WARM | `#93816b` | `#93816b`→`#999874` | `#0f1620` | accentInk trên fieldSunken | **4.58** | ✅ |
| SÁNG · WARM_WHITE · COOL | `#93816b` | `#93816b`→`#999874` | `#0f1620` | accentInk trên fieldSunken | **4.52** | ✅ |
| SÁNG · FROM_ART(trắng) · NEUTRAL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(đen) · NEUTRAL | `#676767` | `#676767`→`#6f6f6f` | `#ffffff` | ON_ACCENT trên gradTo | **5.02** | ✅ |
| SÁNG · FROM_ART(đỏ rực) · NEUTRAL | `#f30d0d` | `#f33031`→`#f18a3f` | `#0f1620` | ON_ACCENT trên gradFrom | **4.57** | ✅ |
| SÁNG · FROM_ART(xám) · NEUTRAL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(trắng) · WARM | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(đen) · WARM | `#676767` | `#676767`→`#6f6f6f` | `#ffffff` | mut2 trên fieldSunken | **4.84** | ✅ |
| SÁNG · FROM_ART(đỏ rực) · WARM | `#f30d0d` | `#f33031`→`#f18a3f` | `#0f1620` | ON_ACCENT trên gradFrom | **4.57** | ✅ |
| SÁNG · FROM_ART(xám) · WARM | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(trắng) · COOL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
| SÁNG · FROM_ART(đen) · COOL | `#676767` | `#676767`→`#6f6f6f` | `#ffffff` | mut2 trên fieldSunken | **4.78** | ✅ |
| SÁNG · FROM_ART(đỏ rực) · COOL | `#f30d0d` | `#f33031`→`#f18a3f` | `#0f1620` | ON_ACCENT trên gradFrom | **4.57** | ✅ |
| SÁNG · FROM_ART(xám) · COOL | `#7f7f7f` | `#7f7f7f`→`#878787` | `#0f1620` | ON_ACCENT trên gradFrom | **4.54** | ✅ |
