# 👁 Ekran qo'riqchisi

Bolalar ko'zini ekrandan himoya qiluvchi dastur. Kamera orqali bolaning yuzini kuzatadi va:

- **ekranga juda yaqin kelsa** (kompyuterda 35 sm, telefonda 30 sm dan yaqin, 2 soniyadan ortiq) — ekran qorayadi. Bola orqaga surilsa, ekran o'zi ochiladi;
- **uzluksiz 20 daqiqa qarab tursa** — ekran qorayadi va tanaffus boshlanadi (standart 1 daqiqa, teskari sanoq bilan).

Hamma narsa qurilmaning o'zida ishlaydi: rasm yoki video saqlanmaydi va internetga yuborilmaydi.

| Papka | Platforma | Texnologiya |
|---|---|---|
| `kompyuter/` | Windows (Linux'da ham ishlaydi) | Python, OpenCV (YuNet yuz aniqlash), Tkinter |
| `android/` | Android 8.0+ | Kotlin, CameraX, Google ML Kit |

---

## Qanday ishlaydi

Masofa ko'zlar orasidagi masofaga qarab hisoblanadi: `masofa = K / (ko'zlar_orasi_piksel / kadr_kengligi)`.
`K` dastlab taxminiy qiymatga teng. **Kalibrlash** paytida bola ma'lum masofada (masalan, 40 sm) o'tiradi va `K` shu bolaga va shu kameraga moslab aniqlanadi.

Qoidalar (ikkala versiyada bir xil):

- Bola qisqa vaqt (2 soniyadan kam) yaqinlashsa, ekran qoraymaydi.
- Ekran qoraygandan keyin bola **chegara + 5 sm** dan uzoqlashsagina ochiladi. Bu tez-tez qorayib-ochilishning oldini oladi.
- Bola kameraga shunchalik yaqin kelsaki, yuzi kadrga sig'masa, ekran qora bo'lib qolaveradi.
- Bola qisqa vaqtga boshqa tomonga qarasa, 20 daqiqalik hisob to'xtamaydi. Tanaffus davomiyligicha (1 daqiqa) ekranga qaramasa, hisob boshidan boshlanadi.
- Tanaffus paytida ekran ochilmaydi. Faqat ota-ona PIN bilan ochishi mumkin.
- **Ota-ona PIN kodi** quyidagilar uchun kerak: sozlamalar, dasturdan chiqish (yoki himoyani o'chirish) va 15 daqiqalik pauza. Qora ekranning pastki burchagidagi «🔒 Ota-ona» yozuvini bosib ham PIN kiritish mumkin.

---

## 💻 Kompyuter versiyasi

### Tayyor .exe ni yig'ish (Windows)
1. [python.org](https://www.python.org/downloads/) saytidan Python 3.11 yoki 3.12 ni o'rnating. O'rnatishda «Add Python to PATH» belgisini qo'ying.
2. `kompyuter` papkasida `build_exe.bat` ni ikki marta bosing.
3. Tayyor fayl `kompyuter\dist\EkranQoriqchisi.exe` papkasida paydo bo'ladi. Uni bolaning kompyuteriga ko'chiring.

### Yig'masdan ishga tushirish
`ishga_tushirish.bat` faylini bosing yoki terminalda quyidagilarni bajaring:
```
cd kompyuter
pip install -r requirements.txt
python ekran_qoriqchisi.py
```

### Birinchi ishga tushirish
1. Ota-ona PIN kodini o'rnating.
2. **📏 Kalibrlash** tugmasini bosing, ekrandan yuzgacha bo'lgan masofani o'lchab yozing va «Saqlash»ni bosing.
3. **⚙ Sozlamalar** bo'limida «Kompyuter yoqilganda avtomatik ishga tushsin» belgisini qo'ying.
4. «Fonga yashirish» tugmasini bosing. Dastur soat yonidagi trayda ishlashda davom etadi.

---

## 📱 Android versiyasi

### APK yig'ish — 1-usul: Android Studio
1. Android Studio'da `android` papkasini oching (*File → Open*).
2. Gradle sinxronizatsiyasi tugashini kuting.
3. *Build → Build App Bundle(s) / APK(s) → Build APK(s)* ni tanlang.
4. Tayyor fayl: `android/app/build/outputs/apk/debug/app-debug.apk`.

### APK yig'ish — 2-usul: GitHub (kompyuterga hech narsa o'rnatmasdan)
1. Butun loyihani GitHub'dagi yangi repozitoriyga yuklang.
2. **Actions** bo'limida «Dasturlarni yig'ish» jarayoni avtomatik boshlanadi (~5 daqiqa).
3. Jarayon tugagach, **Artifacts** bo'limidan `EkranQoriqchisi-android` (APK) va `EkranQoriqchisi-windows` (EXE) fayllarini yuklab oling.

### Telefonda sozlash
1. APK ni o'rnating. Telefon «noma'lum manbalar»dan o'rnatishga ruxsat so'rasa, ruxsat bering.
2. PIN kodni o'rnating va **Himoyani yoqish** tugmasini bosing.
3. So'ralgan ruxsatlarni bering:
   - Kamera va bildirishnomalar.
   - «Boshqa ilovalar ustidan ko'rsatish» — ekranni qoraytirish uchun.
   - Batareya cheklovini olib tashlash (tavsiya etiladi) — tizim himoyani o'chirib qo'ymasligi uchun.
4. **📏 Kalibrlash** ni bajaring: bola telefonni odatdagidek ushlaydi, siz masofani o'lchaysiz.

**Eslatmalar:**
- Himoya ishlayotganda tepada doim «kamera ishlatilmoqda» belgisi (yashil nuqta) ko'rinib turadi. Bu Android qoidasi, uni o'chirib bo'lmaydi.
- Ekran o'chganda kamera ham to'xtaydi (batareyani tejash uchun).
- Android 14 va undan yangi versiyalarda kamerali xizmatni telefon yoqilganda avtomatik ishga tushirish taqiqlangan. Shuning uchun telefon yoqilganda «Himoyani yoqish uchun bosing» degan eslatma chiqadi.
- iPhone uchun versiya yo'q: iOS ilovalarga fonda kamera ishlatishga ruxsat bermaydi.

---

## Sozlamalar (standart qiymatlar)

| Sozlama | Kompyuter | Telefon |
|---|---|---|
| Ruxsat etilgan eng yaqin masofa | 35 sm | 30 sm |
| Yaqin tursa, qorayishgacha vaqt | 2 soniya | 2 soniya |
| Uzluksiz qarash chegarasi | 20 daqiqa | 20 daqiqa |
| Tanaffus davomiyligi | 1 daqiqa | 1 daqiqa |

## Testlar
```
cd kompyuter
python test_mantiq.py
```
