"""
Ekran qo'riqchisi — kompyuter versiyasi (Windows / Linux).

Bola ekranga juda yaqin kelsa yoki uzluksiz uzoq vaqt (standart: 20 daqiqa)
qarab tursa, ekran qorayadi.

Ishga tushirish:   python ekran_qoriqchisi.py
Fonda boshlash:    python ekran_qoriqchisi.py --fonda
"""

import hashlib
import json
import os
import sys
import threading
import time
import tkinter as tk
from tkinter import messagebox, ttk

import cv2
from PIL import Image, ImageDraw, ImageTk

from qoriqchi_mantiq import NORMAL, YAQIN, TANAFFUS, STANDART_K, Qoriqchi, Sozlamalar
from yuz_aniqlash import YuzAniqlovchi, resurs_yoli

try:
    import pystray
except Exception:  # tray bo'lmasa ham dastur ishlayveradi
    pystray = None

DASTUR_NOMI = "Ekran qo'riqchisi"
WINDOWS = sys.platform.startswith("win")
SHRIFT = "Segoe UI" if WINDOWS else "DejaVu Sans"
EMOJI_SHRIFT = "Segoe UI Emoji" if WINDOWS else "Noto Color Emoji"
PAUZA_DAQIQA = 15

RANG_FON = "#F4F7FB"
RANG_ASOSIY = "#2563EB"
RANG_YASHIL = "#16A34A"
RANG_QIZIL = "#DC2626"
RANG_SARIQ = "#D97706"
RANG_MATN = "#1F2937"
RANG_KULRANG = "#6B7280"


# ---------------------------------------------------------------- sozlamalar
def sozlamalar_papkasi():
    if WINDOWS:
        asos = os.environ.get("APPDATA", os.path.expanduser("~"))
    else:
        asos = os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config"))
    papka = os.path.join(asos, "EkranQoriqchisi")
    os.makedirs(papka, exist_ok=True)
    return papka


SOZLAMALAR_FAYLI = os.path.join(sozlamalar_papkasi(), "sozlamalar.json")

STANDART = {
    "chegara_sm": 35,
    "yaqin_kutish_s": 2,
    "tomosha_daqiqa": 20,
    "tanaffus_daqiqa": 1,
    "k": STANDART_K,
    "kalibrlangan": False,
    "kamera": 0,
    "pin_hash": "",
    "avto_ishga_tushish": False,
}


def yukla():
    d = dict(STANDART)
    try:
        with open(SOZLAMALAR_FAYLI, "r", encoding="utf-8") as f:
            d.update(json.load(f))
    except Exception:
        pass
    return d


def saqla(d):
    with open(SOZLAMALAR_FAYLI, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)


def pin_hash(pin):
    return hashlib.sha256(("eq-tuz:" + pin).encode("utf-8")).hexdigest()


def avto_ishga_tushishni_sozla(yoqilgan):
    """Windows bilan birga ishga tushish (HKCU\\...\\Run)."""
    if not WINDOWS:
        return
    import winreg
    kalit_yoli = r"Software\Microsoft\Windows\CurrentVersion\Run"
    if getattr(sys, "frozen", False):
        buyruq = f'"{sys.executable}" --fonda'
    else:
        pyw = sys.executable.replace("python.exe", "pythonw.exe")
        buyruq = f'"{pyw}" "{os.path.abspath(__file__)}" --fonda'
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, kalit_yoli, 0, winreg.KEY_SET_VALUE) as k:
        if yoqilgan:
            winreg.SetValueEx(k, "EkranQoriqchisi", 0, winreg.REG_SZ, buyruq)
        else:
            try:
                winreg.DeleteValue(k, "EkranQoriqchisi")
            except FileNotFoundError:
                pass


# ---------------------------------------------------------------- kamera
class KameraIshchi(threading.Thread):
    """Kameradan kadr olib, yuzni topadi. Natija `natija`da saqlanadi."""

    def __init__(self, kamera_raqami=0):
        super().__init__(daemon=True)
        self.kamera_raqami = kamera_raqami
        self._toxta = threading.Event()
        self._qulf = threading.Lock()
        self.natija = (0.0, None, None)  # (vaqt, nisbat, quti)
        self.kadr = None
        self.xato = None
        self.qayta_ochish = False

    def _ochish(self):
        if WINDOWS:
            kam = cv2.VideoCapture(self.kamera_raqami, cv2.CAP_DSHOW)
        else:
            kam = cv2.VideoCapture(self.kamera_raqami)
        kam.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        kam.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
        return kam

    def run(self):
        try:
            aniqlovchi = YuzAniqlovchi()
        except Exception as e:
            self.xato = f"Model yuklanmadi: {e}"
            return
        kam = None
        while not self._toxta.is_set():
            if kam is None or not kam.isOpened() or self.qayta_ochish:
                if kam is not None:
                    kam.release()
                self.qayta_ochish = False
                kam = self._ochish()
                if not kam.isOpened():
                    self.xato = "Kamera topilmadi. Kamerani ulang yoki sozlamalarda boshqa raqamni tanlang."
                    kam = None
                    self._toxta.wait(3)
                    continue
            ok, kadr = kam.read()
            if not ok or kadr is None:
                self.xato = "Kameradan tasvir kelmayapti."
                kam.release()
                kam = None
                self._toxta.wait(2)
                continue
            self.xato = None
            kadr = cv2.flip(kadr, 1)  # ko'zgu ko'rinishi
            try:
                nisbat, quti = aniqlovchi.aniqla(kadr)
            except Exception:
                nisbat, quti = None, None
            with self._qulf:
                self.natija = (time.monotonic(), nisbat, quti)
                self.kadr = kadr
            self._toxta.wait(0.15)  # ~6 kadr/soniya — protsessorni tejash
        if kam is not None:
            kam.release()

    def olish(self):
        with self._qulf:
            return self.natija, self.kadr

    def toxtat(self):
        self._toxta.set()


# ---------------------------------------------------------------- oynalar
def markazga(oyna, ota=None):
    oyna.update_idletasks()
    w, h = oyna.winfo_reqwidth(), oyna.winfo_reqheight()
    if ota is not None and ota.winfo_viewable():
        x = ota.winfo_rootx() + (ota.winfo_width() - w) // 2
        y = ota.winfo_rooty() + (ota.winfo_height() - h) // 2
    else:
        x = (oyna.winfo_screenwidth() - w) // 2
        y = (oyna.winfo_screenheight() - h) // 2
    oyna.geometry(f"+{max(0, x)}+{max(0, y)}")


def pin_sora(ota, sarlavha="Ota-ona PIN kodi", izoh="PIN kodni kiriting:"):
    """PIN so'raydi. To'g'ri bo'lsa True."""
    s = yukla()
    natija = {"ok": False}
    d = tk.Toplevel(ota)
    d.title(sarlavha)
    d.configure(bg="white", padx=24, pady=20)
    d.resizable(False, False)
    d.attributes("-topmost", True)
    tk.Label(d, text="🔒", font=(EMOJI_SHRIFT, 28), bg="white").pack()
    tk.Label(d, text=izoh, font=(SHRIFT, 11), bg="white", fg=RANG_MATN).pack(pady=(4, 8))
    e = tk.Entry(d, show="•", font=(SHRIFT, 16), justify="center", width=10)
    e.pack()
    xato = tk.Label(d, text="", font=(SHRIFT, 9), bg="white", fg=RANG_QIZIL)
    xato.pack(pady=(4, 0))

    def tekshir(_=None):
        if pin_hash(e.get()) == s.get("pin_hash"):
            natija["ok"] = True
            d.destroy()
        else:
            xato.config(text="PIN noto'g'ri")
            e.delete(0, "end")

    qator = tk.Frame(d, bg="white")
    qator.pack(pady=(10, 0))
    ttk.Button(qator, text="Bekor qilish", command=d.destroy).pack(side="left", padx=4)
    ttk.Button(qator, text="Tasdiqlash", command=tekshir).pack(side="left", padx=4)
    e.bind("<Return>", tekshir)
    markazga(d, ota)
    d.lift()
    e.focus_force()
    d.grab_set()
    ota.wait_window(d)
    return natija["ok"]


def yangi_pin_oynasi(ota, birinchi=False):
    """Yangi PIN o'rnatish. O'rnatilsa True."""
    natija = {"ok": False}
    d = tk.Toplevel(ota)
    d.title("PIN kod o'rnatish")
    d.configure(bg="white", padx=24, pady=20)
    d.resizable(False, False)
    d.attributes("-topmost", True)
    if birinchi:
        matn = ("Xush kelibsiz! 👋\n\nAvval ota-ona uchun PIN kod o'rnating.\n"
                "Sozlamalarni o'zgartirish va dasturdan chiqish\nfaqat shu PIN bilan bo'ladi.")
    else:
        matn = "Yangi PIN kodni kiriting (kamida 4 ta raqam):"
    tk.Label(d, text=matn, font=(SHRIFT, 11), bg="white", fg=RANG_MATN,
             justify="center").pack(pady=(0, 10))
    tk.Label(d, text="Yangi PIN:", font=(SHRIFT, 10), bg="white").pack()
    e1 = tk.Entry(d, show="•", font=(SHRIFT, 14), justify="center", width=10)
    e1.pack(pady=(0, 6))
    tk.Label(d, text="PIN ni takrorlang:", font=(SHRIFT, 10), bg="white").pack()
    e2 = tk.Entry(d, show="•", font=(SHRIFT, 14), justify="center", width=10)
    e2.pack()
    xato = tk.Label(d, text="", font=(SHRIFT, 9), bg="white", fg=RANG_QIZIL)
    xato.pack(pady=(4, 0))

    def saqlash(_=None):
        p1, p2 = e1.get().strip(), e2.get().strip()
        if len(p1) < 4 or not p1.isdigit():
            xato.config(text="PIN kamida 4 ta raqamdan iborat bo'lsin")
            return
        if p1 != p2:
            xato.config(text="PIN kodlar bir xil emas")
            return
        s = yukla()
        s["pin_hash"] = pin_hash(p1)
        saqla(s)
        natija["ok"] = True
        d.destroy()

    ttk.Button(d, text="Saqlash", command=saqlash).pack(pady=(10, 0))
    e2.bind("<Return>", saqlash)
    if birinchi:
        d.protocol("WM_DELETE_WINDOW", lambda: None)
    markazga(d, ota)
    e1.focus_force()
    d.grab_set()
    ota.wait_window(d)
    return natija["ok"]


class QoraEkran(tk.Toplevel):
    """Butun ekran(lar)ni qoplaydigan qora oyna."""

    def __init__(self, ilova):
        super().__init__(ilova)
        self.ilova = ilova
        self.withdraw()
        self.configure(bg="black", cursor="arrow")
        self.overrideredirect(True)
        self.attributes("-topmost", True)
        self.protocol("WM_DELETE_WINDOW", lambda: None)
        self.bind("<Alt-F4>", lambda e: "break")

        markaz = tk.Frame(self, bg="black")
        markaz.place(relx=0.5, rely=0.45, anchor="center")
        self.emoji = tk.Label(markaz, text="", font=(EMOJI_SHRIFT, 72), bg="black", fg="white")
        self.emoji.pack()
        self.sarlavha = tk.Label(markaz, text="", font=(SHRIFT, 40, "bold"), bg="black", fg="white")
        self.sarlavha.pack(pady=(10, 6))
        self.matn = tk.Label(markaz, text="", font=(SHRIFT, 22), bg="black", fg="#D1D5DB",
                             justify="center")
        self.matn.pack()
        self.qoshimcha = tk.Label(markaz, text="", font=(SHRIFT, 54, "bold"), bg="black",
                                  fg="#60A5FA")
        self.qoshimcha.pack(pady=(20, 0))

        tugma = tk.Label(self, text="🔒 Ota-ona", font=(SHRIFT, 10), bg="black", fg="#4B5563",
                         cursor="hand2")
        tugma.place(relx=0.99, rely=0.98, anchor="se")
        tugma.bind("<Button-1>", lambda e: self.ilova.ota_ona_ochishi())
        self.korinadi = False

    def _geometriya(self):
        if WINDOWS:
            import ctypes
            u = ctypes.windll.user32
            x, y = u.GetSystemMetrics(76), u.GetSystemMetrics(77)
            w, h = u.GetSystemMetrics(78), u.GetSystemMetrics(79)
            if w and h:
                return f"{w}x{h}+{x}+{y}"
        return f"{self.winfo_screenwidth()}x{self.winfo_screenheight()}+0+0"

    def korsat(self, emoji, sarlavha, matn, qoshimcha=""):
        self.emoji.config(text=emoji)
        self.sarlavha.config(text=sarlavha)
        self.matn.config(text=matn)
        self.qoshimcha.config(text=qoshimcha)
        if not self.korinadi:
            self.geometry(self._geometriya())
            self.deiconify()
            self.korinadi = True
        # boshqa oynalar ustida turishini ta'minlash
        self.attributes("-topmost", True)
        self.lift()

    def yashir(self):
        if self.korinadi:
            self.withdraw()
            self.korinadi = False


# ---------------------------------------------------------------- asosiy ilova
class Ilova(tk.Tk):
    def __init__(self, fonda=False):
        super().__init__()
        self.title(DASTUR_NOMI)
        self.configure(bg=RANG_FON)
        self.resizable(False, False)
        try:
            self.iconbitmap(resurs_yoli("ikonka.ico"))
        except Exception:
            pass
        self.protocol("WM_DELETE_WINDOW", self.yashirish)

        self.s = yukla()
        self.qoriqchi = Qoriqchi(self._mantiq_sozlamalari())
        self.kamera = KameraIshchi(int(self.s.get("kamera", 0)))
        self.pauza_gacha = 0.0
        self.oxirgi_nisbatlar = []
        self.kalibr_oynasi = None
        self.tray = None

        self._stil()
        self._qurish()
        self.qora = QoraEkran(self)

        if not self.s.get("pin_hash"):
            self.after(200, self._birinchi_ishga_tushish)

        self.kamera.start()
        self._tray_boshlash()
        if fonda and self.s.get("pin_hash"):
            self.after(100, self.yashirish)
        self.after(300, self.tik)

    # ----- yordamchi -----
    def _mantiq_sozlamalari(self):
        s = self.s
        return Sozlamalar(
            chegara_sm=float(s["chegara_sm"]),
            yaqin_kutish_s=float(s["yaqin_kutish_s"]),
            tomosha_daqiqa=float(s["tomosha_daqiqa"]),
            tanaffus_daqiqa=float(s["tanaffus_daqiqa"]),
            k=float(s["k"]),
        )

    def _stil(self):
        st = ttk.Style(self)
        try:
            st.theme_use("vista" if WINDOWS else "clam")
        except tk.TclError:
            pass
        st.configure("TButton", font=(SHRIFT, 10), padding=6)

    def _qurish(self):
        tk.Label(self, text="👁  " + DASTUR_NOMI, font=(SHRIFT, 18, "bold"), bg=RANG_FON,
                 fg=RANG_MATN).pack(padx=20, pady=(16, 2), anchor="w")
        tk.Label(self, text="Bolalar ko'zini ekrandan himoya qiladi", font=(SHRIFT, 10),
                 bg=RANG_FON, fg=RANG_KULRANG).pack(padx=20, anchor="w")

        asosiy = tk.Frame(self, bg=RANG_FON)
        asosiy.pack(padx=20, pady=12)

        self.korinish = tk.Label(asosiy, bg="#111827", width=320, height=240)
        self.korinish.grid(row=0, column=0, rowspan=2, sticky="n")

        karta = tk.Frame(asosiy, bg="white", padx=16, pady=14, highlightthickness=1,
                         highlightbackground="#E5E7EB")
        karta.grid(row=0, column=1, sticky="nsew", padx=(14, 0))
        tk.Label(karta, text="Holat", font=(SHRIFT, 9), bg="white", fg=RANG_KULRANG).pack(anchor="w")
        self.holat_l = tk.Label(karta, text="Ishga tushmoqda…", font=(SHRIFT, 14, "bold"),
                                bg="white", fg=RANG_ASOSIY, width=22, anchor="w")
        self.holat_l.pack(anchor="w")
        tk.Label(karta, text="Ekrangacha masofa", font=(SHRIFT, 9), bg="white",
                 fg=RANG_KULRANG).pack(anchor="w", pady=(10, 0))
        self.masofa_l = tk.Label(karta, text="—", font=(SHRIFT, 22, "bold"), bg="white",
                                 fg=RANG_MATN)
        self.masofa_l.pack(anchor="w")
        tk.Label(karta, text="Uzluksiz qarash", font=(SHRIFT, 9), bg="white",
                 fg=RANG_KULRANG).pack(anchor="w", pady=(10, 0))
        self.tomosha_l = tk.Label(karta, text="00:00", font=(SHRIFT, 14), bg="white",
                                  fg=RANG_MATN)
        self.tomosha_l.pack(anchor="w")
        self.tomosha_bar = ttk.Progressbar(karta, length=200, maximum=100)
        self.tomosha_bar.pack(anchor="w", pady=(4, 0))
        self.stat_l = tk.Label(karta, text="", font=(SHRIFT, 9), bg="white", fg=RANG_KULRANG,
                               justify="left")
        self.stat_l.pack(anchor="w", pady=(10, 0))

        self.izoh_l = tk.Label(self, text="", font=(SHRIFT, 9), bg=RANG_FON, fg=RANG_SARIQ,
                               wraplength=560, justify="left")
        self.izoh_l.pack(padx=20, anchor="w")

        tugmalar = tk.Frame(self, bg=RANG_FON)
        tugmalar.pack(padx=20, pady=(6, 16), fill="x")
        ttk.Button(tugmalar, text="📏 Kalibrlash", command=self.kalibrlash).pack(side="left")
        ttk.Button(tugmalar, text="⚙ Sozlamalar", command=self.sozlamalar).pack(side="left", padx=6)
        self.pauza_t = ttk.Button(tugmalar, text=f"⏸ {PAUZA_DAQIQA} daq. pauza",
                                  command=self.pauza_almashtir)
        self.pauza_t.pack(side="left")
        ttk.Button(tugmalar, text="Chiqish", command=self.chiqish).pack(side="right")
        ttk.Button(tugmalar, text="Fonga yashirish", command=self.yashirish).pack(side="right", padx=6)

    def _birinchi_ishga_tushish(self):
        self.deiconify()
        yangi_pin_oynasi(self, birinchi=True)
        self.s = yukla()
        messagebox.showinfo(
            DASTUR_NOMI,
            "Aniq ishlashi uchun bir marta «📏 Kalibrlash» tugmasini bosib,\n"
            "masofani sozlab qo'ying (1 daqiqa vaqt oladi).",
            parent=self)

    # ----- tray -----
    def _tray_rasmi(self):
        r = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        d = ImageDraw.Draw(r)
        d.ellipse((2, 2, 62, 62), fill=(37, 99, 235, 255))
        d.ellipse((12, 20, 52, 44), fill="white")
        d.ellipse((24, 22, 40, 42), fill=(17, 24, 39, 255))
        return r

    def _tray_boshlash(self):
        if pystray is None:
            return
        try:
            menyu = pystray.Menu(
                pystray.MenuItem("Ko'rsatish", lambda: self.after(0, self.korsatish), default=True),
                pystray.MenuItem("Chiqish", lambda: self.after(0, self.chiqish)),
            )
            self.tray = pystray.Icon("EkranQoriqchisi", self._tray_rasmi(), DASTUR_NOMI, menyu)
            threading.Thread(target=self.tray.run, daemon=True).start()
        except Exception:
            self.tray = None

    def yashirish(self):
        if self.tray is not None:
            self.withdraw()
        else:
            self.iconify()

    def korsatish(self):
        self.deiconify()
        self.lift()
        self.focus_force()

    # ----- asosiy sikl -----
    def tik(self):
        hozir = time.monotonic()
        (vaqt, nisbat, quti), kadr = self.kamera.olish()
        if hozir - vaqt > 2.0:  # natija eskirgan — yuz yo'q deb hisoblaymiz
            nisbat, quti = None, None
        if nisbat:
            self.oxirgi_nisbatlar = (self.oxirgi_nisbatlar + [nisbat])[-15:]

        q = self.qoriqchi
        pauzada = hozir < self.pauza_gacha
        if pauzada:
            if q.holat != NORMAL:
                q.ochish()
            q.yangilash(hozir, None)
            q.holat = NORMAL
            q.tomosha_boshlandi = None
        else:
            q.yangilash(hozir, nisbat)

        # kalibrlash paytida ekran qoraymasin
        if self.kalibr_oynasi is not None and q.holat == YAQIN:
            q.ochish()

        self._qora_ekranni_yangila(hozir)
        self._oynani_yangila(hozir, kadr, quti, pauzada)
        self.after(200, self.tik)

    def _qora_ekranni_yangila(self, hozir):
        q = self.qoriqchi
        if q.holat == YAQIN:
            m = q.masofa_sm
            qosh = f"{m:.0f} sm" if m else ""
            self.qora.korsat(
                "👀", "Ekranga juda yaqinsan!",
                f"Ko'zlaringni asra — orqaroqqa surilib o'tir.\n"
                f"Kamida {self.s['chegara_sm']:.0f} sm uzoqlikda o'tirsang, ekran ochiladi.",
                qosh)
        elif q.holat == TANAFFUS:
            qoldi = int(q.tanaffus_qoldi(hozir) + 0.999)
            self.qora.korsat(
                "😌", "Ko'zlaring dam olsin!",
                f"Sen {self.s['tomosha_daqiqa']:g} daqiqa uzluksiz ekranga qarading.\n"
                "Uzoqqa — derazadan tashqariga qara, ko'zlaringni yumib-och.",
                f"{qoldi // 60:02d}:{qoldi % 60:02d}")
        else:
            self.qora.yashir()

    def _oynani_yangila(self, hozir, kadr, quti, pauzada):
        q = self.qoriqchi
        # holat
        if self.kamera.xato:
            self.holat_l.config(text="Kamera xatosi", fg=RANG_QIZIL)
            self.izoh_l.config(text=self.kamera.xato)
        else:
            self.izoh_l.config(text="" if self.s.get("kalibrlangan") else
                               "Maslahat: aniqroq ishlashi uchun «Kalibrlash»ni bajaring.")
            if pauzada:
                qoldi = int(self.pauza_gacha - hozir)
                self.holat_l.config(text=f"Pauza ({qoldi // 60 + 1} daq.)", fg=RANG_SARIQ)
            elif q.holat == YAQIN:
                self.holat_l.config(text="Juda yaqin!", fg=RANG_QIZIL)
            elif q.holat == TANAFFUS:
                self.holat_l.config(text="Tanaffus", fg=RANG_SARIQ)
            elif q.masofa_sm is None:
                self.holat_l.config(text="Yuz ko'rinmayapti", fg=RANG_KULRANG)
            else:
                self.holat_l.config(text="Hammasi yaxshi ✓", fg=RANG_YASHIL)

        if q.masofa_sm:
            rang = RANG_QIZIL if q.masofa_sm < self.s["chegara_sm"] else RANG_MATN
            self.masofa_l.config(text=f"{q.masofa_sm:.0f} sm", fg=rang)
        else:
            self.masofa_l.config(text="—", fg=RANG_MATN)

        t = int(q.tomosha_soniya(hozir))
        jami = self.s["tomosha_daqiqa"] * 60
        j = int(jami)
        self.tomosha_l.config(text=f"{t // 60:02d}:{t % 60:02d}  /  {j // 60:02d}:{j % 60:02d}")
        self.tomosha_bar["value"] = min(100, 100 * t / jami) if jami else 0
        self.stat_l.config(text=f"Shu seansda:\n• yaqinlashish: {q.yaqinlashishlar_soni} marta\n"
                                f"• tanaffus: {q.tanaffuslar_soni} marta")
        self.pauza_t.config(text="▶ Davom ettirish" if pauzada else f"⏸ {PAUZA_DAQIQA} daq. pauza")

        # kamera ko'rinishi (faqat oyna ochiq bo'lsa)
        if kadr is not None and self.state() == "normal":
            self._korinish_chiz(self.korinish, kadr, quti)

    def _korinish_chiz(self, label, kadr, quti, olcham=(320, 240)):
        k = kadr.copy()
        if quti:
            x, y, w, h, o, c = quti
            rang = (38, 38, 220) if self.qoriqchi.holat == YAQIN else (74, 222, 128)
            cv2.rectangle(k, (x, y), (x + w, y + h), rang, 2)
            cv2.circle(k, o, 4, rang, -1)
            cv2.circle(k, c, 4, rang, -1)
        k = cv2.resize(k, olcham)
        rasm = ImageTk.PhotoImage(Image.fromarray(cv2.cvtColor(k, cv2.COLOR_BGR2RGB)))
        label.configure(image=rasm, width=olcham[0], height=olcham[1])
        label.image = rasm

    # ----- tugmalar -----
    def ota_ona_ochishi(self):
        """Qora ekrandagi «Ota-ona» tugmasi: PIN bilan pauza."""
        if pin_sora(self.qora, izoh=f"PIN kiritilsa, himoya {PAUZA_DAQIQA} daqiqaga to'xtaydi:"):
            self.pauza_gacha = time.monotonic() + PAUZA_DAQIQA * 60
            self.qoriqchi.tanaffusni_tugatish(time.monotonic())
            self.qoriqchi.ochish()
            self.qora.yashir()

    def pauza_almashtir(self):
        if time.monotonic() < self.pauza_gacha:
            self.pauza_gacha = 0.0
        elif pin_sora(self, izoh=f"Himoyani {PAUZA_DAQIQA} daqiqaga to'xtatish:"):
            self.pauza_gacha = time.monotonic() + PAUZA_DAQIQA * 60

    def chiqish(self):
        self.korsatish()
        if not pin_sora(self, izoh="Dasturdan chiqish uchun PIN kiriting:"):
            return
        self.kamera.toxtat()
        if self.tray is not None:
            try:
                self.tray.stop()
            except Exception:
                pass
        self.destroy()

    def sozlamalar(self):
        if not pin_sora(self, izoh="Sozlamalarni ochish uchun PIN kiriting:"):
            return
        SozlamalarOynasi(self)

    def sozlamalarni_qollash(self):
        self.s = yukla()
        yangi = self._mantiq_sozlamalari()
        self.qoriqchi.s = yangi
        if int(self.s.get("kamera", 0)) != self.kamera.kamera_raqami:
            self.kamera.kamera_raqami = int(self.s.get("kamera", 0))
            self.kamera.qayta_ochish = True

    def kalibrlash(self):
        if self.kalibr_oynasi is None:
            self.kalibr_oynasi = KalibrlashOynasi(self)
        else:
            self.kalibr_oynasi.lift()


class SozlamalarOynasi(tk.Toplevel):
    def __init__(self, ilova):
        super().__init__(ilova)
        self.ilova = ilova
        self.title("Sozlamalar")
        self.configure(bg="white", padx=22, pady=18)
        self.resizable(False, False)
        s = yukla()

        self.qiymatlar = {}

        def qator(n, matn, kalit, dan, gacha, qadam=1):
            tk.Label(self, text=matn, font=(SHRIFT, 10), bg="white", fg=RANG_MATN,
                     anchor="w").grid(row=n, column=0, sticky="w", pady=5)
            v = tk.StringVar(value=str(s[kalit]).rstrip("0").rstrip(".") if isinstance(s[kalit], float) else str(s[kalit]))
            sb = ttk.Spinbox(self, from_=dan, to=gacha, increment=qadam, textvariable=v, width=8,
                             font=(SHRIFT, 10))
            sb.grid(row=n, column=1, sticky="e", padx=(16, 0))
            self.qiymatlar[kalit] = (v, dan, gacha)

        tk.Label(self, text="⚙ Sozlamalar", font=(SHRIFT, 14, "bold"), bg="white",
                 fg=RANG_MATN).grid(row=0, column=0, columnspan=2, sticky="w", pady=(0, 8))
        qator(1, "Ruxsat etilgan eng yaqin masofa (sm)", "chegara_sm", 20, 80)
        qator(2, "Yaqin tursa, necha soniyadan keyin qorayadi", "yaqin_kutish_s", 1, 10)
        qator(3, "Uzluksiz qarash chegarasi (daqiqa)", "tomosha_daqiqa", 5, 120)
        qator(4, "Tanaffus davomiyligi (daqiqa)", "tanaffus_daqiqa", 0.5, 15, 0.5)
        qator(5, "Kamera raqami (odatda 0)", "kamera", 0, 5)

        self.avto = tk.BooleanVar(value=bool(s.get("avto_ishga_tushish")))
        cb = ttk.Checkbutton(self, text="Kompyuter yoqilganda avtomatik ishga tushsin",
                             variable=self.avto)
        cb.grid(row=6, column=0, columnspan=2, sticky="w", pady=(10, 0))
        if not WINDOWS:
            cb.state(["disabled"])

        tk.Label(self, text=f"Kalibrlash: {'bajarilgan ✓' if s.get('kalibrlangan') else 'bajarilmagan'}",
                 font=(SHRIFT, 9), bg="white", fg=RANG_KULRANG).grid(row=7, column=0, sticky="w",
                                                                       pady=(10, 0))

        tugmalar = tk.Frame(self, bg="white")
        tugmalar.grid(row=8, column=0, columnspan=2, sticky="ew", pady=(16, 0))
        ttk.Button(tugmalar, text="PIN ni o'zgartirish",
                   command=lambda: yangi_pin_oynasi(self)).pack(side="left")
        ttk.Button(tugmalar, text="Saqlash", command=self.saqlash).pack(side="right")
        ttk.Button(tugmalar, text="Bekor", command=self.destroy).pack(side="right", padx=6)
        markazga(self, ilova)
        self.grab_set()

    def saqlash(self):
        s = yukla()
        for kalit, (v, dan, gacha) in self.qiymatlar.items():
            try:
                son = float(v.get().replace(",", "."))
            except ValueError:
                messagebox.showerror("Xato", "Faqat son kiriting.", parent=self)
                return
            son = min(max(son, dan), gacha)
            s[kalit] = int(son) if kalit == "kamera" else son
        s["avto_ishga_tushish"] = bool(self.avto.get())
        saqla(s)
        try:
            avto_ishga_tushishni_sozla(s["avto_ishga_tushish"])
        except Exception as e:
            messagebox.showwarning("Diqqat", f"Avto ishga tushirishni sozlab bo'lmadi:\n{e}",
                                   parent=self)
        self.ilova.sozlamalarni_qollash()
        self.destroy()


class KalibrlashOynasi(tk.Toplevel):
    def __init__(self, ilova):
        super().__init__(ilova)
        self.ilova = ilova
        self.title("Kalibrlash")
        self.configure(bg="white", padx=22, pady=18)
        self.resizable(False, False)
        self.protocol("WM_DELETE_WINDOW", self.yopish)

        tk.Label(self, text="📏 Masofani kalibrlash", font=(SHRIFT, 14, "bold"), bg="white",
                 fg=RANG_MATN).pack(anchor="w")
        tk.Label(self, text=(
            "1. Bolani odatdagidek ekran qarshisiga o'tqazing.\n"
            "2. Ekrandan yuzigacha bo'lgan masofani o'lchang (chizg'ich yoki santimetr lenta).\n"
            "3. Masofani pastga yozing, bola kameraga to'g'ri qarab tursin.\n"
            "4. Yuzida yashil ramka ko'ringanda «Saqlash» tugmasini bosing."),
            font=(SHRIFT, 10), bg="white", fg=RANG_MATN, justify="left").pack(anchor="w", pady=8)
        self.korinish = tk.Label(self, bg="#111827", width=320, height=240)
        self.korinish.pack(pady=4)
        q = tk.Frame(self, bg="white")
        q.pack(pady=6)
        tk.Label(q, text="O'lchangan masofa (sm):", font=(SHRIFT, 10), bg="white").pack(side="left")
        self.masofa = tk.StringVar(value="40")
        ttk.Spinbox(q, from_=20, to=100, textvariable=self.masofa, width=6).pack(side="left", padx=6)
        self.holat = tk.Label(self, text="", font=(SHRIFT, 9), bg="white", fg=RANG_KULRANG)
        self.holat.pack()
        t = tk.Frame(self, bg="white")
        t.pack(fill="x", pady=(8, 0))
        ttk.Button(t, text="Standartga qaytarish", command=self.standart).pack(side="left")
        ttk.Button(t, text="Saqlash", command=self.saqlash).pack(side="right")
        ttk.Button(t, text="Yopish", command=self.yopish).pack(side="right", padx=6)
        markazga(self, ilova)
        self.yangila()

    def yangila(self):
        if not self.winfo_exists():
            return
        (vaqt, nisbat, quti), kadr = self.ilova.kamera.olish()
        if kadr is not None:
            self.ilova._korinish_chiz(self.korinish, kadr, quti)
        yangi = time.monotonic() - vaqt < 1.0 and nisbat
        self.holat.config(text="Yuz topildi ✓" if yangi else "Yuz topilmadi — kameraga qarang",
                          fg=RANG_YASHIL if yangi else RANG_QIZIL)
        self.after(150, self.yangila)

    def saqlash(self):
        n = self.ilova.oxirgi_nisbatlar
        (vaqt, nisbat, _), _ = self.ilova.kamera.olish()
        if not nisbat or time.monotonic() - vaqt > 1.0 or len(n) < 5:
            messagebox.showwarning("Kalibrlash", "Yuz topilmadi. Bola kameraga to'g'ri qarab tursin.",
                                   parent=self)
            return
        try:
            sm = float(self.masofa.get().replace(",", "."))
        except ValueError:
            messagebox.showerror("Xato", "Masofani son bilan kiriting.", parent=self)
            return
        median = sorted(n[-10:])[len(n[-10:]) // 2]
        s = yukla()
        s["k"] = sm * median
        s["kalibrlangan"] = True
        saqla(s)
        self.ilova.sozlamalarni_qollash()
        messagebox.showinfo("Kalibrlash", "Saqlandi! Endi masofa aniqroq o'lchanadi.", parent=self)
        self.yopish()

    def standart(self):
        s = yukla()
        s["k"] = STANDART_K
        s["kalibrlangan"] = False
        saqla(s)
        self.ilova.sozlamalarni_qollash()
        self.holat.config(text="Standart qiymat tiklandi")

    def yopish(self):
        self.ilova.kalibr_oynasi = None
        self.destroy()


# ---------------------------------------------------------------- ishga tushirish
def yagona_nusxa():
    """Dastur ikki marta ochilmasligi uchun (Windows)."""
    if not WINDOWS:
        return True
    import ctypes
    ctypes.windll.kernel32.CreateMutexW(None, False, "EkranQoriqchisi_Yagona")
    return ctypes.windll.kernel32.GetLastError() != 183  # ERROR_ALREADY_EXISTS


def main():
    if WINDOWS:
        try:
            import ctypes
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:
            pass
    if not yagona_nusxa():
        return
    ilova = Ilova(fonda="--fonda" in sys.argv)
    ilova.mainloop()


if __name__ == "__main__":
    main()
