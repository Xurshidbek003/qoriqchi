"""Yuzni topish va ko'zlar orasidagi masofani o'lchash (OpenCV YuNet)."""

import os
import sys
import math

import cv2

MODEL_NOMI = "face_detection_yunet_2023mar.onnx"
ANIQLASH_KENGLIGI = 320  # tezlik uchun kadr shu kenglikka kichraytiriladi


def resurs_yoli(nom):
    """PyInstaller .exe ichida ham, oddiy ishga tushirishda ham fayl yo'li."""
    asos = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(asos, "models", nom)


class YuzAniqlovchi:
    def __init__(self, ishonch=0.7):
        self._detektor = cv2.FaceDetectorYN.create(
            resurs_yoli(MODEL_NOMI), "", (ANIQLASH_KENGLIGI, 240),
            ishonch, 0.3, 5)
        self._olcham = None

    def aniqla(self, kadr):
        """
        Eng katta (eng yaqin) yuzni topadi.
        Qaytaradi: (nisbat, quti) yoki (None, None).
          nisbat — ko'zlar orasidagi masofa / kadr kengligi
          quti   — asl kadrdagi (x, y, w, h, o'ng_ko'z, chap_ko'z)
        """
        h, w = kadr.shape[:2]
        miqyos = ANIQLASH_KENGLIGI / float(w)
        kichik = cv2.resize(kadr, (ANIQLASH_KENGLIGI, int(round(h * miqyos))))
        olcham = (kichik.shape[1], kichik.shape[0])
        if olcham != self._olcham:
            self._detektor.setInputSize(olcham)
            self._olcham = olcham

        _, yuzlar = self._detektor.detect(kichik)
        if yuzlar is None or len(yuzlar) == 0:
            return None, None

        yuz = max(yuzlar, key=lambda f: f[2] * f[3])
        x, y, fw, fh = yuz[0:4]
        rx, ry, lx, ly = yuz[4:8]
        koz = math.hypot(lx - rx, ly - ry)
        # Bosh yonga burilganda ko'zlar orasi qisqaradi — yuz kengligi bilan
        # pastki chegara qo'yamiz (frontal yuzda ko'z masofasi ≈ 0.47 × kenglik)
        olchov = max(koz, 0.40 * fw)
        nisbat = olchov / ANIQLASH_KENGLIGI
        k = 1.0 / miqyos
        quti = (int(x * k), int(y * k), int(fw * k), int(fh * k),
                (int(rx * k), int(ry * k)), (int(lx * k), int(ly * k)))
        return nisbat, quti
