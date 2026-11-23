"""Mantiq testlari:  python -m pytest test_mantiq.py  (yoki python test_mantiq.py)"""

from qoriqchi_mantiq import NORMAL, YAQIN, TANAFFUS, Qoriqchi, Sozlamalar


def nisbat(sm, k=4.4):
    return k / sm


def yur(q, t0, t1, sm, qadam=0.2):
    """t0..t1 oralig'ida berilgan masofada (None — yuz yo'q) kadr beradi."""
    t = t0
    while t < t1:
        q.yangilash(t, None if sm is None else nisbat(sm))
        t += qadam
    return t


def test_uzoqda_normal():
    q = Qoriqchi(Sozlamalar())
    yur(q, 0, 10, 50)
    assert q.holat == NORMAL
    assert 49 < q.masofa_sm < 51


def test_yaqinda_qorayadi_va_ochiladi():
    q = Qoriqchi(Sozlamalar())
    yur(q, 0, 5, 50)
    yur(q, 5, 6.5, 25)            # 1.5 s — hali qoraymaydi
    assert q.holat == NORMAL
    yur(q, 6.5, 8.5, 25)
    assert q.holat == YAQIN
    assert q.yaqinlashishlar_soni == 1
    yur(q, 8.5, 12, 37)           # chegaradan o'tdi, lekin zahiradan emas
    assert q.holat == YAQIN
    yur(q, 12, 15, 45)
    assert q.holat == NORMAL


def test_yaqin_holatda_yuz_yoqolsa_qora_qoladi():
    q = Qoriqchi(Sozlamalar())
    yur(q, 0, 4, 20)
    assert q.holat == YAQIN
    yur(q, 4, 30, None)
    assert q.holat == YAQIN


def test_qisqa_yaqinlashish_hisobga_olinmaydi():
    q = Qoriqchi(Sozlamalar())
    for i in range(10):
        t = i * 3
        yur(q, t, t + 1, 25)
        yur(q, t + 1, t + 3, 50)
    assert q.holat == NORMAL


def test_20_daqiqadan_keyin_tanaffus():
    q = Qoriqchi(Sozlamalar(tomosha_daqiqa=20, tanaffus_daqiqa=1))
    t = yur(q, 0, 20 * 60 - 5, 50, qadam=1)
    assert q.holat == NORMAL
    t = yur(q, t, t + 10, 50, qadam=1)
    assert q.holat == TANAFFUS
    # tanaffus davomida yuz ko'rinsa ham ochilmaydi
    t = yur(q, t, t + 50, 50, qadam=1)
    assert q.holat == TANAFFUS
    t = yur(q, t, t + 15, 50, qadam=1)
    assert q.holat == NORMAL
    assert q.tomosha_soniya(t) < 20


def test_qisqa_chalgish_hisobni_tashlamaydi():
    q = Qoriqchi(Sozlamalar(tomosha_daqiqa=20, tanaffus_daqiqa=1))
    t = 0
    for _ in range(19):                 # har daqiqada 20 s boshqa tomonga qaraydi
        t = yur(q, t, t + 40, 50, qadam=1)
        t = yur(q, t, t + 20, None, qadam=1)
    t = yur(q, t, t + 120, 50, qadam=1)
    assert q.holat == TANAFFUS


def test_uzoq_dam_olsa_hisob_tashlanadi():
    q = Qoriqchi(Sozlamalar(tomosha_daqiqa=20, tanaffus_daqiqa=1))
    t = yur(q, 0, 15 * 60, 50, qadam=1)
    t = yur(q, t, t + 70, None, qadam=1)  # 70 s ekranga qaramadi
    t = yur(q, t, t + 10 * 60, 50, qadam=1)
    assert q.holat == NORMAL


if __name__ == "__main__":
    import sys
    xatolar = 0
    for nom, f in list(globals().items()):
        if nom.startswith("test_"):
            try:
                f()
                print("✓", nom)
            except AssertionError:
                xatolar += 1
                print("✗", nom)
    sys.exit(1 if xatolar else 0)
