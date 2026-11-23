package uz.ekranqoriqchisi

/**
 * Asosiy mantiq — kompyuter versiyasidagi qoriqchi_mantiq.py bilan bir xil.
 *
 * Har bir kadr uchun [yangilash] chaqiriladi:
 *  hozir  — soniyalarda vaqt
 *  nisbat — ko'zlar orasidagi masofa / kadr kengligi (yuz yo'q bo'lsa null)
 */
enum class Holat { NORMAL, YAQIN, TANAFFUS }

data class MantiqSozlamalari(
    val chegaraSm: Double = 30.0,
    val yaqinKutishS: Double = 2.0,
    val qaytishZahiraSm: Double = 5.0,
    val ochilishKutishS: Double = 1.0,
    val tomoshaDaqiqa: Double = 20.0,
    val tanaffusDaqiqa: Double = 1.0,
    val k: Double = STANDART_K,
) {
    companion object {
        /** Telefon old kamerasi (~75°) va ~5.6 sm ko'z oralig'i uchun taxminiy qiymat. */
        const val STANDART_K = 3.6
    }
}

class QoriqchiMantiq(var s: MantiqSozlamalari) {
    var holat = Holat.NORMAL
        private set
    var masofaSm: Double? = null
        private set
    var tomoshaBoshlandi: Double? = null
        private set
    private var tanaffusTugashi: Double? = null
    private val nisbatlar = ArrayDeque<Double>()
    private var oxirgiKorildi: Double? = null
    private var yaqinBoshlandi: Double? = null
    private var uzoqBoshlandi: Double? = null

    var yaqinlashishlarSoni = 0
        private set
    var tanaffuslarSoni = 0
        private set

    fun tomoshaSoniya(hozir: Double): Double =
        tomoshaBoshlandi?.let { maxOf(0.0, hozir - it) } ?: 0.0

    fun tanaffusQoldi(hozir: Double): Double =
        if (holat == Holat.TANAFFUS) tanaffusTugashi?.let { maxOf(0.0, it - hozir) } ?: 0.0 else 0.0

    private fun tanaffusniBoshlash(hozir: Double) {
        holat = Holat.TANAFFUS
        tanaffusTugashi = hozir + s.tanaffusDaqiqa * 60
        tanaffuslarSoni++
        yaqinBoshlandi = null
        uzoqBoshlandi = null
    }

    /** Tanaffus tugadi (yoki ota-ona to'xtatdi) — hisob qaytadan boshlanadi. */
    fun tanaffusniTugatish() {
        holat = Holat.NORMAL
        tanaffusTugashi = null
        tomoshaBoshlandi = null
        yaqinBoshlandi = null
    }

    /** Ota-ona qora ekranni ochganda yoki pauza paytida. */
    fun ochish() {
        holat = Holat.NORMAL
        yaqinBoshlandi = null
        uzoqBoshlandi = null
        tanaffusTugashi = null
    }

    fun tomoshaniTashlash() {
        tomoshaBoshlandi = null
    }

    fun yangilash(hozir: Double, nisbat: Double?): Holat {
        val yuzBor = nisbat != null && nisbat > 0

        if (yuzBor) {
            nisbatlar.addLast(nisbat!!)
            while (nisbatlar.size > 5) nisbatlar.removeFirst()
            val tartib = nisbatlar.sorted()
            masofaSm = s.k / tartib[tartib.size / 2]
            oxirgiKorildi = hozir
        } else {
            val ok = oxirgiKorildi
            if (ok != null && hozir - ok > 1.5) {
                nisbatlar.clear()
                masofaSm = null
            }
        }

        // --- uzluksiz qarash hisoblagichi ---
        if (yuzBor) {
            if (tomoshaBoshlandi == null) tomoshaBoshlandi = hozir
        } else {
            val ok = oxirgiKorildi
            if (tomoshaBoshlandi != null && ok != null && hozir - ok >= s.tanaffusDaqiqa * 60) {
                tomoshaBoshlandi = null   // yetarlicha dam oldi
            }
        }

        // --- TANAFFUS ---
        if (holat == Holat.TANAFFUS) {
            if (hozir >= (tanaffusTugashi ?: 0.0)) tanaffusniTugatish()
            return holat
        }
        val tb = tomoshaBoshlandi
        if (tb != null && hozir - tb >= s.tomoshaDaqiqa * 60) {
            tanaffusniBoshlash(hozir)
            return holat
        }

        val masofa = masofaSm

        // --- NORMAL ---
        if (holat == Holat.NORMAL) {
            if (masofa != null && masofa < s.chegaraSm) {
                val yb = yaqinBoshlandi ?: hozir.also { yaqinBoshlandi = it }
                if (hozir - yb >= s.yaqinKutishS) {
                    holat = Holat.YAQIN
                    yaqinlashishlarSoni++
                    uzoqBoshlandi = null
                }
            } else if (masofa != null) {
                yaqinBoshlandi = null
            }
            return holat
        }

        // --- YAQIN: faqat yuz aniq uzoqda ko'rinsa ochiladi ---
        if (masofa != null && masofa >= s.chegaraSm + s.qaytishZahiraSm) {
            val ub = uzoqBoshlandi ?: hozir.also { uzoqBoshlandi = it }
            if (hozir - ub >= s.ochilishKutishS) {
                holat = Holat.NORMAL
                yaqinBoshlandi = null
            }
        } else {
            uzoqBoshlandi = null
        }
        return holat
    }
}
