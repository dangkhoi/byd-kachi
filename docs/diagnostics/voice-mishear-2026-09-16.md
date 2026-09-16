# [ĐO host 2026-09-16] Bảng NGHE NHẦM trên corpus tự dựng — voice 1.66

> Sinh bằng máy: `scripts/voice/mishear-table.py`. Không sửa tay — chạy lại là ra y hệt.
> Corpus: `scripts/voice/data/variants.tsv` (LLM soạn) → WAV bằng `scripts/voice/synth-corpus.py`.

## 0. Mức bằng chứng

| Nhánh | Mức | Vì sao |
|---|---|---|
| `linh` (macOS `say`) | [ĐO host] | giọng tổng hợp, đọc rõ, KHÔNG ồn đường — nói về *model + hotword*, không nói về xe |
| `piper` (vi_VN-vais1000-medium) | [ĐO host] | giọng thứ hai; câu sai ở CẢ HAI giọng ⇒ lỗi từ vựng, sai ở một giọng ⇒ lỗi âm học của giọng đó |
| `linh-ola` (nén thời gian 1.3×/1.6×) | [SUY] | âm tổng hợp từ âm tổng hợp; mô phỏng *nhịp* nói nhanh, KHÔNG mô phỏng nuốt phụ âm |
| cột `parse` | [SUY] | `approx_parse` dựng lại nguyên tắc của `VoiceIntentParser`, KHÔNG phải chính nó |

Quy mô: **1874 WAV corpus** + 25 WAV của ma trận cũ · 5 cấu hình hotword · score 3.0.

## 1. Tổng theo tệp hotword

| tệp hotword | WAV | đúng nguyên văn | 25 WAV cũ |
|---|---|---|---|
| none | 1899 | 729 (38.4%) | 18/25 |
| hotwords-phrases.txt | 1899 | 935 (49.2%) | 22/25 |
| hotwords-tonefix.txt | 1899 | 935 (49.2%) | 22/25 |
| hotwords-existing+proposed.txt | 1899 | 990 (52.1%) | 22/25 |
| hotwords-tonefix+proposed.txt | 1899 | 990 (52.1%) | 22/25 |

## 2. Nói NHANH có hỏng không — tỉ lệ đúng theo giọng × tốc độ

`say -r N` = N từ/phút (180 là mặc định macOS). `piper speed` > 1 là nhanh hơn.

| giọng · tốc độ | none | hotwords-phrases.txt | hotwords-tonefix.txt | hotwords-existing+proposed.txt | hotwords-tonefix+proposed.txt | số WAV |
|---|---|---|---|---|---|---|
| linh 140 | 38.6% | 43.4% | 43.4% | 49.4% | 49.4% | 83 |
| linh 180 | 50.3% | 62.9% | 62.9% | 65.2% | 65.2% | 431 |
| linh 220 | 39.8% | 44.6% | 44.6% | 49.4% | 49.4% | 83 |
| linh 260 | 49.9% | 63.8% | 63.6% | 65.9% | 65.7% | 431 |
| linh 300 | 38.6% | 42.2% | 42.2% | 44.6% | 44.6% | 83 |
| linh-ola 1.3 | 30.1% | 33.7% | 33.7% | 42.2% | 42.2% | 83 |
| linh-ola 1.6 | 32.5% | 34.9% | 34.9% | 41.0% | 41.0% | 83 |
| piper 0.9 | 18.1% | 22.9% | 24.1% | 26.5% | 27.7% | 83 |
| piper 1.0 | 23.2% | 38.5% | 38.5% | 40.4% | 40.4% | 431 |
| piper 1.3 | 18.1% | 20.5% | 20.5% | 22.9% | 22.9% | 83 |

## 3. Theo LOẠI ý định

| intent_kind | none | hotwords-phrases.txt | hotwords-tonefix.txt | hotwords-existing+proposed.txt | hotwords-tonefix+proposed.txt | số WAV |
|---|---|---|---|---|---|---|
| app | 12.8% | 12.6% | 12.6% | 20.6% | 20.6% | 549 |
| control_button | 56.8% | 65.9% | 65.9% | 72.7% | 72.7% | 44 |
| control_cover | 39.4% | 57.6% | 57.6% | 60.6% | 60.6% | 33 |
| control_select | 64.3% | 69.0% | 69.0% | 71.4% | 71.4% | 42 |
| control_step | 66.7% | 76.9% | 76.9% | 76.9% | 76.9% | 39 |
| control_toggle | 35.3% | 46.8% | 46.8% | 46.8% | 46.8% | 218 |
| launcher | 86.7% | 100.0% | 100.0% | 100.0% | 100.0% | 15 |
| layout | 33.3% | 50.0% | 50.0% | 50.0% | 50.0% | 6 |
| macro | 61.9% | 61.9% | 61.9% | 81.0% | 81.0% | 21 |
| media | 33.3% | 57.1% | 57.1% | 52.4% | 52.4% | 21 |
| nav | 55.6% | 55.6% | 55.6% | 55.6% | 55.6% | 9 |
| old_case | 61.4% | 73.6% | 74.1% | 73.6% | 74.1% | 220 |
| profile | 66.7% | 66.7% | 66.7% | 66.7% | 66.7% | 3 |
| telemetry_read | 44.9% | 64.6% | 64.5% | 64.9% | 64.8% | 633 |
| unknown | 57.1% | 66.7% | 66.7% | 71.4% | 71.4% | 21 |

## 4. Theo VÙNG MIỀN và KIỂU NÓI

| region | none | hotwords-phrases.txt | hotwords-tonefix.txt | hotwords-existing+proposed.txt | hotwords-tonefix+proposed.txt | số WAV |
|---|---|---|---|---|---|---|
| bac | 35.7% | 41.1% | 41.1% | 46.4% | 46.4% | 56 |
| chung | 40.8% | 53.1% | 53.2% | 55.9% | 56.0% | 1575 |
| nam | 20.2% | 21.8% | 21.4% | 25.1% | 24.7% | 243 |

| style | none | hotwords-phrases.txt | hotwords-tonefix.txt | hotwords-existing+proposed.txt | hotwords-tonefix+proposed.txt | số WAV |
|---|---|---|---|---|---|---|
| dai | 49.3% | 66.1% | 65.8% | 66.9% | 66.7% | 363 |
| lich_su | 40.0% | 46.7% | 46.7% | 51.1% | 51.1% | 45 |
| ngan | 40.0% | 51.5% | 51.5% | 54.0% | 54.1% | 1197 |
| than_mat | 14.7% | 15.4% | 15.4% | 19.9% | 19.9% | 136 |
| tieng_anh_viet | 11.3% | 11.3% | 11.3% | 21.8% | 21.8% | 133 |

## 5. 30 cặp NGHE NHẦM hay gặp nhất

Cấu hình `hotwords-phrases.txt`. `ref` là câu đưa cho TTS (đã đọc chữ số thành chữ), `hyp` là chuỗi model in ra.

| ref | hyp (model nghe ra) | lần |
|---|---|---|
| mở gu gồ máp | mở google map | 10 |
| mở gu gồ mép | mở google map | 9 |
| mở nhạc du túp | mở nhạc youtube | 8 |
| mở gu gồ map | mở google map | 7 |
| mở du túp | mở youtube | 7 |
| mở iu túp | mở youtube | 7 |
| mở gia lô | mở zalô | 7 |
| mở da lô | mở zalô | 7 |
| mở pô ti phai | mở potifi | 7 |
| mở diu túp | mở youtube | 6 |
| mở cạc plây | mở cài | 5 |
| dẫn đường đến bitexco | dẫn đường đến bi | 5 |
| mở google maps | mở google map | 4 |
| mở youtube music | mở youtubec | 4 |
| mở za lô | mở giữa a lô | 4 |
| mở spo ti phy | mở sy | 4 |
| mở waze | mở hoa | 4 |
| mở an đờ roi ao tô | mở anderi ao | 4 |
| mở zing | mở dựa | 4 |
| pin còn bao nhiêu | còn bao nhiêu | 4 |
| pin còn bao nhiêu | tin còn bao nhiêu | 4 |
| dẫn đường tới chợ bến thành bằng waze | dẫn đường tới chợ bến thành bằng loa e | 4 |
| lọc bụi | các bụi | 3 |
| google | goovel | 3 |
| mở bản đồ google | mở bản đồ vovle | 3 |
| mở dút túp | mở giúp top | 3 |
| mở du túp miu dích | mở youtube | 3 |
| mở da lô | mở ra lộ | 3 |
| mở spotify | mở seit | 3 |
| mở spô ti phai | mở spoty file | 3 |

## 6. Mã yếu — dưới 50 % ngay cả ở tốc độ CHẬM

`loại` = TỪ VỰNG khi chính câu gốc (chữ đúng 100 %) cũng không trỏ về đúng mã qua `approx_parse` ⇒ sửa bằng `VoiceSynonyms`, không cần đụng model. ÂM HỌC khi câu gốc trỏ đúng mà model nghe ra chữ khác.

| id | đúng/tổng (chậm) | % | loại | câu gốc parse đúng |
|---|---|---|---|---|
| old_w07 | 0/2 | 0.0% | TỪ VỰNG | 0/1 |
| old_w09 | 0/2 | 0.0% | TỪ VỰNG | 0/1 |
| old_w22 | 0/2 | 0.0% | TỪ VỰNG | 0/1 |
| old_w24 | 0/2 | 0.0% | TỪ VỰNG | 0/1 |
| open_app | 12/108 | 11.1% | TỪ VỰNG | 21/55 |

## 7. Đề xuất bí danh

`scripts/voice/data/aliases-proposed.tsv` — **542 dòng**, chỉ từ chuỗi model thật sự in ra (không bịa cách viết). `synonym` = thêm dạng bỏ dấu vào `VoiceSynonyms`; `hotword` = thêm cụm HOA có dấu vào tệp hotword; `clarify` = quá hiếm để mã hoá, để `VoiceClarify` hỏi lại.

| id | heard_text | lần | đề xuất |
|---|---|---|---|
| open_app | mở zalô | 14 | synonym |
| open_app | mở ca lây | 9 | synonym |
| open_app | mở potifi | 8 | synonym |
| open_app | mở viett map | 6 | synonym |
| open_app | mở youtubec | 6 | synonym |
| open_app | mở cài | 5 | synonym |
| open_app | mở du tu | 5 | synonym |
| open_app | mở ra lộ | 5 | synonym |
| open_app | mở sei | 5 | synonym |
| open_app | mở viett | 5 | synonym |
| open_app | mở anderi ao | 4 | synonym |
| open_app | mở ca py | 4 | synonym |
| open_app | mở dựa | 4 | synonym |
| open_app | mở giữa a lô | 4 | synonym |
| open_app | mở hoa | 4 | synonym |
| open_app | mở sy | 4 | synonym |
| ac_on | xem điều hòa | 3 | hotword |
| ambient_color | chuyển sang tím | 3 | synonym |
| cabin_temp | nhiệt trong xe sao rồi | 3 | hotword |
| camera_view | chuyển sang trước | 3 | synonym |
| charging_eta_hour | còn mấy giờ nữa đầy sao rồi | 3 | hotword |
| drive_mode | chuyển sang thể thao | 3 | synonym |
| energy_mode | chế độ năng lượng thế nào | 3 | hotword |
| headlight_feedback | chế độ đèn pha thế nào | 3 | hotword |
| headlight_mode | chuyển sang đỗ | 3 | synonym |
| launcher_settings | mở thiết lập giúp mình với | 3 | synonym |
| layout | bố cục hai cột | 3 | synonym |
| light_drl | đèn ban ngày thế nào | 3 | hotword |
| light_high_beam | xem đèn pha | 3 | hotword |
| mac_door_light | mở cửa bật đèn đọc | 3 | hotword |

## 8. Soát CHỖ ĐẶT DẤU của tệp hotword so với `tokens.txt`

*«hoà»* và *«hòa»* là cùng một chữ, khác chỗ đặt dấu thanh. Dự án viết kiểu CŨ; từ điển của mô hình chỉ có kiểu MỚI. Dòng hotword viết sai kiểu vẫn **mã hoá được** bằng mảnh BPE, nhưng nó bias một đường token khác đường mô hình thật sự đi — im lặng, không báo lỗi gì.

Tệp soát: `hotwords-phrases.txt` — **54/1757 dòng** viết kiểu cũ.

| viết trong tệp | kiểu mô hình dùng | số dòng | có trong tokens.txt? | dạng mới có? |
|---|---|---|---|---|
| KHOÁ | KHÓA | 24 | **không** | có |
| HOÀ | HÒA | 23 | **không** | có |
| KHOẺ | KHỎE | 7 | **không** | có |

⚠ [ĐO] Sửa chỗ đặt dấu **một mình** KHÔNG đổi tổng số (xem §1: `tonefix` = `phrases`); nó chỉ nhích ở nhánh `piper 0.9`. Tức giả thuyết *"sai chỗ đặt dấu ⇒ hotword vô hiệu"* **bị bác** — BPE vẫn kéo được. Vẫn nên sửa vì đó là chữ viết đúng chuẩn của từ điển mô hình, nhưng KHÔNG được bán nó như một bản vá cải thiện độ chính xác.

