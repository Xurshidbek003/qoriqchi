"""
Ekran qo'riqchisi — asosiy mantiq (kamera va oynalardan mustaqil).

Har bir kadr uchun `yangilash(hozir, nisbat)` chaqiriladi:
  * hozir  — soniyalarda vaqt (time.monotonic())
  * nisbat — ko'zlar orasidagi masofa (piksel) / kadr kengligi,
             yuz topilmasa None.

Natijada holat qaytadi: NORMAL, YAQIN yoki TANAFFUS.
YAQIN va TANAFFUS holatlarida ekran qoraytiriladi.
"""

from collections import deque
from dataclasses import dataclass

NORMAL = "normal"
YAQIN = "yaqin"          # ekranga juda yaqin
TANAFFUS = "tanaffus"    # uzluksiz uzoq qaradi — dam olish

# Kalibrlanmagan holat uchun taxminiy koeffitsient:
# masofa_sm = K / nisbat.  Oddiy veb-kamera (~65° ko'rish burchagi) va
# bolaning ko'zlari orasidagi ~5.6 sm masofa uchun K ≈ 4.4
STANDART_K = 4.4


@dataclass
class Sozlamalar:
    chegara_sm: float = 35.0        # shundan yaqin kelsa — ekran qorayadi
    yaqin_kutish_s: float = 2.0     # necha soniya yaqin tursa qorayadi
    qaytish_zahira_sm: float = 5.0  # ochilishi uchun chegara + zahira masofa
    ochilish_kutish_s: float = 1.0  # uzoqda necha soniya tursa ochiladi
    tomosha_daqiqa: float = 20.0    # uzluksiz qarash chegarasi
    tanaffus_daqiqa: float = 1.0    # dam olish davomiyligi
    k: float = STANDART_K           # kalibrlash koeffitsienti


class Qoriqchi:
    def __init__(self, sozlamalar: Sozlamalar):
        self.s = sozlamalar
        self.holat = NORMAL
        self.masofa_sm = None          # silliqlangan oxirgi masofa
        self._nisbatlar = deque(maxlen=5)
        self._oxirgi_korildi = None    # yuz oxirgi marta ko'rilgan vaqt
        self._yaqin_boshlandi = None
        self._uzoq_boshlandi = None
        self.tomosha_boshlandi = None  # uzluksiz qarash boshlangan vaqt
        self.tanaffus_tugashi = None
        # Statistika (dastur ishlagan vaqt davomida)
        self.yaqinlashishlar_soni = 0
        self.tanaffuslar_soni = 0

    # ---------- yordamchi ----------
    def masofa_hisobla(self, nisbat):
        if not nisbat or nisbat <= 0:
            return None
        return self.s.k / nisbat

    def tomosha_soniya(self, hozir):
        if self.tomosha_boshlandi is None:
            return 0.0
        return max(0.0, hozir - self.tomosha_boshlandi)

    def tanaffus_qoldi(self, hozir):
        if self.holat != TANAFFUS or self.tanaffus_tugashi is None:
            return 0.0
        return max(0.0, self.tanaffus_tugashi - hozir)

    def tanaffusni_boshlash(self, hozir):
        self.holat = TANAFFUS
        self.tanaffus_tugashi = hozir + self.s.tanaffus_daqiqa * 60
        self.tanaffuslar_soni += 1
        self._yaqin_boshlandi = None
        self._uzoq_boshlandi = None

    def tanaffusni_tugatish(self, hozir):
        """Tanaffus tugadi (yoki ota-ona PIN bilan tugatdi)."""
        self.holat = NORMAL
        self.tanaffus_tugashi = None
        self.tomosha_boshlandi = None   # hisob qaytadan boshlanadi
        self._yaqin_boshlandi = None

    def ochish(self):
        """Ota-ona qora ekranni vaqtincha ochishi uchun."""
        self.holat = NORMAL
        self._yaqin_boshlandi = None
        self._uzoq_boshlandi = None
        self.tanaffus_tugashi = None

    # ---------- asosiy qadam ----------
    def yangilash(self, hozir, nisbat):
        s = self.s
        yuz_bor = nisbat is not None and nisbat > 0

        if yuz_bor:
            self._nisbatlar.append(nisbat)
            silliq = sorted(self._nisbatlar)[len(self._nisbatlar) // 2]
            self.masofa_sm = self.masofa_hisobla(silliq)
            self._oxirgi_korildi = hozir
        elif self._oxirgi_korildi is not None and hozir - self._oxirgi_korildi > 1.5:
            self._nisbatlar.clear()
            self.masofa_sm = None

        # --- uzluksiz qarash hisoblagichi ---
        if yuz_bor:
            if self.tomosha_boshlandi is None:
                self.tomosha_boshlandi = hozir
        elif self.tomosha_boshlandi is not None and self._oxirgi_korildi is not None:
            # Tanaffus davomiyligicha ekranga qaramasa — dam olgan hisoblanadi
            if hozir - self._oxirgi_korildi >= s.tanaffus_daqiqa * 60:
                self.tomosha_boshlandi = None

        # --- TANAFFUS holati ---
        if self.holat == TANAFFUS:
            if hozir >= self.tanaffus_tugashi:
                self.tanaffusni_tugatish(hozir)
            return self.holat

        if (self.tomosha_boshlandi is not None
                and hozir - self.tomosha_boshlandi >= s.tomosha_daqiqa * 60):
            self.tanaffusni_boshlash(hozir)
            return self.holat

        masofa = self.masofa_sm

        # --- NORMAL holati ---
        if self.holat == NORMAL:
            if masofa is not None and masofa < s.chegara_sm:
                if self._yaqin_boshlandi is None:
                    self._yaqin_boshlandi = hozir
                if hozir - self._yaqin_boshlandi >= s.yaqin_kutish_s:
                    self.holat = YAQIN
                    self.yaqinlashishlar_soni += 1
                    self._uzoq_boshlandi = None
            elif masofa is not None:
                self._yaqin_boshlandi = None
            # yuz yo'qolsa (juda yaqin kelib kadrdan chiqqan bo'lishi mumkin)
            # hisoblagich tashlanmaydi
            return self.holat

        # --- YAQIN holati ---
        # Faqat yuz aniq uzoqda ko'rinsa ochiladi. Yuz ko'rinmasa — qora qoladi
        # (bola kameraga juda yaqin kelib, yuzi kadrga sig'magan bo'lishi mumkin).
        if masofa is not None and masofa >= s.chegara_sm + s.qaytish_zahira_sm:
            if self._uzoq_boshlandi is None:
                self._uzoq_boshlandi = hozir
            if hozir - self._uzoq_boshlandi >= s.ochilish_kutish_s:
                self.holat = NORMAL
                self._yaqin_boshlandi = None
        else:
            self._uzoq_boshlandi = None
        return self.holat
