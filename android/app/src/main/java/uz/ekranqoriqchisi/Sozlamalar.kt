package uz.ekranqoriqchisi

import android.content.Context
import java.security.MessageDigest

/** Ilova sozlamalari (SharedPreferences). */
class Sozlamalar(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("sozlamalar", Context.MODE_PRIVATE)

    var chegaraSm: Float
        get() = p.getFloat("chegara_sm", 30f)
        set(v) = p.edit().putFloat("chegara_sm", v).apply()

    var yaqinKutishS: Float
        get() = p.getFloat("yaqin_kutish_s", 2f)
        set(v) = p.edit().putFloat("yaqin_kutish_s", v).apply()

    var tomoshaDaqiqa: Float
        get() = p.getFloat("tomosha_daqiqa", 20f)
        set(v) = p.edit().putFloat("tomosha_daqiqa", v).apply()

    var tanaffusDaqiqa: Float
        get() = p.getFloat("tanaffus_daqiqa", 1f)
        set(v) = p.edit().putFloat("tanaffus_daqiqa", v).apply()

    var k: Float
        get() = p.getFloat("k", MantiqSozlamalari.STANDART_K.toFloat())
        set(v) = p.edit().putFloat("k", v).apply()

    var kalibrlangan: Boolean
        get() = p.getBoolean("kalibrlangan", false)
        set(v) = p.edit().putBoolean("kalibrlangan", v).apply()

    val pinOrnatilgan: Boolean get() = p.getString("pin_hash", "").orEmpty().isNotEmpty()

    fun pinOrnat(pin: String) = p.edit().putString("pin_hash", hash(pin)).apply()

    fun pinTogri(pin: String): Boolean = hash(pin) == p.getString("pin_hash", "")

    fun mantiqSozlamalari() = MantiqSozlamalari(
        chegaraSm = chegaraSm.toDouble(),
        yaqinKutishS = yaqinKutishS.toDouble(),
        tomoshaDaqiqa = tomoshaDaqiqa.toDouble(),
        tanaffusDaqiqa = tanaffusDaqiqa.toDouble(),
        k = k.toDouble(),
    )

    private fun hash(pin: String): String {
        val b = MessageDigest.getInstance("SHA-256").digest(("eq-tuz:$pin").toByteArray())
        return b.joinToString("") { "%02x".format(it) }
    }
}

/** Xizmat va oyna o'rtasidagi umumiy holat. */
object Umumiy {
    @Volatile var ishlayapti = false
    @Volatile var holat = Holat.NORMAL
    @Volatile var masofaSm: Double? = null
    @Volatile var tomoshaSoniya = 0.0
    @Volatile var yaqinSoni = 0
    @Volatile var tanaffusSoni = 0
    @Volatile var kameraXatosi: String? = null
    /** SystemClock.elapsedRealtime() bo'yicha pauza tugash vaqti (ms). */
    @Volatile var pauzaGacha = 0L
    @Volatile var kalibrlashOchiq = false
    @Volatile var oxirgiYuzVaqti = 0L

    private val nisbatlar = ArrayDeque<Double>()

    @Synchronized fun nisbatQosh(n: Double) {
        nisbatlar.addLast(n)
        while (nisbatlar.size > 15) nisbatlar.removeFirst()
    }

    /** Oxirgi 10 ta o'lchovning medianasi (kalibrlash uchun). */
    @Synchronized fun nisbatMedianasi(): Double? {
        if (nisbatlar.size < 5) return null
        val oxirgi = nisbatlar.toList().takeLast(10).sorted()
        return oxirgi[oxirgi.size / 2]
    }
}

const val PAUZA_DAQIQA = 15
