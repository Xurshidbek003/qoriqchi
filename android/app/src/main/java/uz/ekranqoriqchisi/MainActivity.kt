package uz.ekranqoriqchisi

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        const val AVTO_YOQISH = "avto_yoqish"
    }

    private lateinit var s: Sozlamalar
    private lateinit var holatMatn: TextView
    private lateinit var masofaMatn: TextView
    private lateinit var tomoshaMatn: TextView
    private lateinit var tomoshaBar: LinearProgressIndicator
    private lateinit var statMatn: TextView
    private lateinit var izohMatn: TextView
    private lateinit var yoqishTugma: MaterialButton
    private lateinit var pauzaTugma: MaterialButton
    private lateinit var ruxsatKarta: MaterialCardView
    private lateinit var ruxsatMatn: TextView
    private lateinit var ruxsatTugma: MaterialButton

    private val ui = Handler(Looper.getMainLooper())
    private var yoqishKutilmoqda = false

    private val ruxsatSorovi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        ruxsatlarniTekshir()
        if (yoqishKutilmoqda) {
            yoqishKutilmoqda = false
            himoyaniYoq()
        }
    }

    private val yangilovchi = object : Runnable {
        override fun run() {
            ekraniYangila()
            ui.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        s = Sozlamalar(this)

        holatMatn = findViewById(R.id.holatMatn)
        masofaMatn = findViewById(R.id.masofaMatn)
        tomoshaMatn = findViewById(R.id.tomoshaMatn)
        tomoshaBar = findViewById(R.id.tomoshaBar)
        statMatn = findViewById(R.id.statMatn)
        izohMatn = findViewById(R.id.izohMatn)
        yoqishTugma = findViewById(R.id.yoqishTugma)
        pauzaTugma = findViewById(R.id.pauzaTugma)
        ruxsatKarta = findViewById(R.id.ruxsatKarta)
        ruxsatMatn = findViewById(R.id.ruxsatMatn)
        ruxsatTugma = findViewById(R.id.ruxsatTugma)

        yoqishTugma.setOnClickListener {
            if (Umumiy.ishlayapti) {
                pinSora("Himoyani o'chirish uchun PIN kiriting") {
                    QoriqchiXizmati.toxtat(this)
                }
            } else {
                himoyaniYoq()
            }
        }
        pauzaTugma.setOnClickListener {
            if (pauzada()) {
                Umumiy.pauzaGacha = 0L
            } else {
                pinSora("Himoyani $PAUZA_DAQIQA daqiqaga to'xtatish") {
                    Umumiy.pauzaGacha = SystemClock.elapsedRealtime() + PAUZA_DAQIQA * 60_000L
                }
            }
        }
        findViewById<MaterialButton>(R.id.sozlamaTugma).setOnClickListener {
            pinSora("Sozlamalarni ochish uchun PIN kiriting") { sozlamalarOynasi() }
        }
        findViewById<MaterialButton>(R.id.kalibrTugma).setOnClickListener { kalibrlashOynasi() }

        if (!s.pinOrnatilgan) pinOrnatOynasi(birinchi = true)
        else if (intent?.getBooleanExtra(AVTO_YOQISH, false) == true) himoyaniYoq()
    }

    override fun onResume() {
        super.onResume()
        ruxsatlarniTekshir()
        ui.post(yangilovchi)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(yangilovchi)
    }

    // ------------------------------------------------------------ ruxsatlar
    private fun ruxsatBor(r: String) =
        ContextCompat.checkSelfPermission(this, r) == PackageManager.PERMISSION_GRANTED

    private fun kerakliRuxsatlar(): Array<String> {
        val r = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) r += Manifest.permission.POST_NOTIFICATIONS
        return r.filter { !ruxsatBor(it) }.toTypedArray()
    }

    private fun batareyaCheklovsiz(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    /** Birinchi yetishmayotgan ruxsatni kartada ko'rsatadi. */
    @SuppressLint("BatteryLife")
    private fun ruxsatlarniTekshir(): Boolean {
        val oddiy = kerakliRuxsatlar()
        when {
            oddiy.isNotEmpty() -> {
                ruxsatKarta.visibility = View.VISIBLE
                ruxsatMatn.text = "📷 Kamera va bildirishnomalarga ruxsat kerak. Kamera faqat yuzgacha " +
                    "masofani o'lchash uchun ishlatiladi — rasm yoki video saqlanmaydi."
                ruxsatTugma.setOnClickListener { ruxsatSorovi.launch(oddiy) }
            }
            !Settings.canDrawOverlays(this) -> {
                ruxsatKarta.visibility = View.VISIBLE
                ruxsatMatn.text = "⬛ Ekranni qoraytirish uchun «Boshqa ilovalar ustidan ko'rsatish» " +
                    "ruxsati kerak. Ochilgan oynada «Ekran qo'riqchisi»ni yoqing."
                ruxsatTugma.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
            !batareyaCheklovsiz() -> {
                ruxsatKarta.visibility = View.VISIBLE
                ruxsatMatn.text = "🔋 Tavsiya: tizim himoyani o'chirib qo'ymasligi uchun batareya " +
                    "cheklovini olib tashlang."
                ruxsatTugma.setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")))
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
                return true  // bu majburiy emas
            }
            else -> {
                ruxsatKarta.visibility = View.GONE
                return true
            }
        }
        return false
    }

    private fun himoyaniYoq() {
        val oddiy = kerakliRuxsatlar()
        if (oddiy.isNotEmpty()) {
            yoqishKutilmoqda = true
            ruxsatSorovi.launch(oddiy)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Avval «Boshqa ilovalar ustidan ko'rsatish» ruxsatini bering",
                Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        QoriqchiXizmati.ishgaTushir(this)
    }

    // ------------------------------------------------------------ ekran
    private fun pauzada() = SystemClock.elapsedRealtime() < Umumiy.pauzaGacha

    private fun ekraniYangila() {
        val ishlayapti = Umumiy.ishlayapti
        yoqishTugma.text = if (ishlayapti) "Himoyani o'chirish 🔒" else "Himoyani yoqish"
        yoqishTugma.setBackgroundColor(
            ContextCompat.getColor(this, if (ishlayapti) R.color.kulrang else R.color.asosiy))

        val (matn, rang) = when {
            !ishlayapti -> "Himoya o'chiq" to R.color.kulrang
            Umumiy.kameraXatosi != null -> "Kamera xatosi" to R.color.qizil
            pauzada() -> {
                val q = ((Umumiy.pauzaGacha - SystemClock.elapsedRealtime()) / 60_000L) + 1
                "Pauza ($q daq.)" to R.color.sariq
            }
            Umumiy.holat == Holat.YAQIN -> "Juda yaqin!" to R.color.qizil
            Umumiy.holat == Holat.TANAFFUS -> "Tanaffus" to R.color.sariq
            Umumiy.masofaSm == null -> "Yuz ko'rinmayapti" to R.color.kulrang
            else -> "Hammasi yaxshi ✓" to R.color.yashil
        }
        holatMatn.text = matn
        holatMatn.setTextColor(ContextCompat.getColor(this, rang))

        val m = if (ishlayapti) Umumiy.masofaSm else null
        masofaMatn.text = m?.let { "${it.roundToInt()} sm" } ?: "—"
        masofaMatn.setTextColor(ContextCompat.getColor(this,
            if (m != null && m < s.chegaraSm) R.color.qizil else R.color.matn))

        val t = if (ishlayapti) Umumiy.tomoshaSoniya.toInt() else 0
        val jami = (s.tomoshaDaqiqa * 60).toInt()
        tomoshaMatn.text = "%02d:%02d / %02d:%02d".format(t / 60, t % 60, jami / 60, jami % 60)
        tomoshaBar.progress = if (jami > 0) minOf(100, t * 100 / jami) else 0
        statMatn.text = "Shu seansda: yaqinlashish — ${Umumiy.yaqinSoni} marta, " +
            "tanaffus — ${Umumiy.tanaffusSoni} marta"

        izohMatn.text = Umumiy.kameraXatosi
            ?: if (!s.kalibrlangan) "Maslahat: aniqroq ishlashi uchun «Kalibrlash»ni bajaring." else ""
        pauzaTugma.text = if (pauzada()) "▶ Davom ettirish" else "⏸ $PAUZA_DAQIQA daqiqa pauza"
        pauzaTugma.isEnabled = ishlayapti
    }

    // ------------------------------------------------------------ oynalar
    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun pinMaydoni(hint: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        this.hint = hint
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
    }

    private fun ustun(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), 0)
        v.forEach { addView(it) }
    }

    private fun pinSora(sarlavha: String, togri: () -> Unit) {
        val maydon = pinMaydoni("PIN")
        val d = MaterialAlertDialogBuilder(this)
            .setTitle("🔒 $sarlavha")
            .setView(ustun(maydon))
            .setNegativeButton("Bekor", null)
            .setPositiveButton("Tasdiqlash", null)
            .create()
        d.setOnShowListener {
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (s.pinTogri(maydon.text.toString())) {
                    d.dismiss(); togri()
                } else {
                    maydon.error = "PIN noto'g'ri"
                    maydon.setText("")
                }
            }
        }
        d.show()
    }

    private fun pinOrnatOynasi(birinchi: Boolean) {
        val m1 = pinMaydoni("Yangi PIN (kamida 4 raqam)")
        val m2 = pinMaydoni("PIN ni takrorlang")
        val b = MaterialAlertDialogBuilder(this)
            .setTitle(if (birinchi) "Xush kelibsiz! 👋" else "PIN ni o'zgartirish")
            .setView(ustun(
                TextView(this).apply {
                    text = if (birinchi) "Avval ota-ona uchun PIN kod o'rnating. Himoyani o'chirish, " +
                        "pauza va sozlamalar faqat shu PIN bilan ochiladi." else ""
                    visibility = if (birinchi) View.VISIBLE else View.GONE
                },
                m1, m2))
            .setPositiveButton("Saqlash", null)
            .setCancelable(!birinchi)
        if (!birinchi) b.setNegativeButton("Bekor", null)
        val d = b.create()
        d.setOnShowListener {
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val p1 = m1.text.toString().trim()
                val p2 = m2.text.toString().trim()
                when {
                    p1.length < 4 || !p1.all { it.isDigit() } -> m1.error = "Kamida 4 ta raqam"
                    p1 != p2 -> m2.error = "PIN kodlar bir xil emas"
                    else -> {
                        s.pinOrnat(p1)
                        d.dismiss()
                        Toast.makeText(this, "PIN saqlandi", Toast.LENGTH_SHORT).show()
                        if (birinchi) kalibrlashMaslahati()
                    }
                }
            }
        }
        d.show()
    }

    private fun kalibrlashMaslahati() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Deyarli tayyor!")
            .setMessage("1. «Himoyani yoqish» tugmasini bosing va ruxsatlarni bering.\n" +
                "2. Aniq ishlashi uchun bir marta «📏 Kalibrlash»ni bajaring.")
            .setPositiveButton("Tushunarli", null)
            .show()
    }

    private fun sonMaydoni(qiymat: Float, kasr: Boolean) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or
            (if (kasr) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        setText(if (qiymat % 1f == 0f) qiymat.toInt().toString() else qiymat.toString())
        minEms = 4
    }

    private fun qator(yorliq: String, maydon: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
        addView(TextView(this@MainActivity).apply {
            text = yorliq
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(maydon)
    }

    private fun sozlamalarOynasi() {
        val chegara = sonMaydoni(s.chegaraSm, false)
        val kutish = sonMaydoni(s.yaqinKutishS, false)
        val tomosha = sonMaydoni(s.tomoshaDaqiqa, false)
        val tanaffus = sonMaydoni(s.tanaffusDaqiqa, true)
        val pinTugma = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "PIN ni o'zgartirish"
        }
        val d = MaterialAlertDialogBuilder(this)
            .setTitle("⚙ Sozlamalar")
            .setView(ustun(
                qator("Eng yaqin masofa (sm)", chegara),
                qator("Yaqin tursa, necha soniyada qorayadi", kutish),
                qator("Uzluksiz qarash chegarasi (daqiqa)", tomosha),
                qator("Tanaffus davomiyligi (daqiqa)", tanaffus),
                TextView(this).apply {
                    text = "Kalibrlash: " + if (s.kalibrlangan) "bajarilgan ✓" else "bajarilmagan"
                    setPadding(0, dp(8), 0, dp(4))
                },
                pinTugma,
            ))
            .setNegativeButton("Bekor", null)
            .setPositiveButton("Saqlash") { _, _ ->
                fun o(e: EditText, dan: Float, gacha: Float, eski: Float) =
                    (e.text.toString().replace(",", ".").toFloatOrNull() ?: eski).coerceIn(dan, gacha)
                s.chegaraSm = o(chegara, 15f, 80f, s.chegaraSm)
                s.yaqinKutishS = o(kutish, 1f, 10f, s.yaqinKutishS)
                s.tomoshaDaqiqa = o(tomosha, 5f, 120f, s.tomoshaDaqiqa)
                s.tanaffusDaqiqa = o(tanaffus, 0.5f, 15f, s.tanaffusDaqiqa)
                Toast.makeText(this, "Saqlandi", Toast.LENGTH_SHORT).show()
            }
            .create()
        pinTugma.setOnClickListener { d.dismiss(); pinOrnatOynasi(birinchi = false) }
        d.show()
    }

    private fun kalibrlashOynasi() {
        if (!Umumiy.ishlayapti) {
            Toast.makeText(this, "Avval «Himoyani yoqish» tugmasini bosing", Toast.LENGTH_LONG).show()
            return
        }
        val masofa = sonMaydoni(30f, false)
        val holat = TextView(this).apply {
            setPadding(0, dp(8), 0, 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        Umumiy.kalibrlashOchiq = true
        val d = MaterialAlertDialogBuilder(this)
            .setTitle("📏 Masofani kalibrlash")
            .setView(ustun(
                TextView(this).apply {
                    text = "1. Bola telefonni odatdagidek ushlasin.\n" +
                        "2. Ekrandan ko'zigacha masofani chizg'ich bilan o'lchang.\n" +
                        "3. Masofani pastga yozing, bola ekranga to'g'ri qarab tursin.\n" +
                        "4. «Yuz topildi ✓» yozuvi chiqqanda «Saqlash»ni bosing."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                },
                qator("O'lchangan masofa (sm)", masofa),
                holat,
            ))
            .setNeutralButton("Standart", null)
            .setNegativeButton("Yopish", null)
            .setPositiveButton("Saqlash", null)
            .create()

        val tik = object : Runnable {
            override fun run() {
                val yangi = SystemClock.elapsedRealtime() - Umumiy.oxirgiYuzVaqti < 1200
                holat.text = if (yangi) "Yuz topildi ✓" else "Yuz topilmadi — ekranga qarang"
                holat.setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (yangi) R.color.yashil else R.color.qizil))
                ui.postDelayed(this, 300)
            }
        }
        d.setOnShowListener {
            ui.post(tik)
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val sm = masofa.text.toString().toFloatOrNull()
                val median = Umumiy.nisbatMedianasi()
                val yangi = SystemClock.elapsedRealtime() - Umumiy.oxirgiYuzVaqti < 1200
                when {
                    sm == null || sm < 10 || sm > 100 -> masofa.error = "10–100 sm oralig'ida kiriting"
                    median == null || !yangi ->
                        Toast.makeText(this, "Yuz topilmadi. Bola ekranga qarab tursin.",
                            Toast.LENGTH_SHORT).show()
                    else -> {
                        s.k = (sm * median).toFloat()
                        s.kalibrlangan = true
                        Toast.makeText(this, "Saqlandi! Endi masofa aniqroq o'lchanadi.",
                            Toast.LENGTH_LONG).show()
                        d.dismiss()
                    }
                }
            }
            d.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                s.k = MantiqSozlamalari.STANDART_K.toFloat()
                s.kalibrlangan = false
                Toast.makeText(this, "Standart qiymat tiklandi", Toast.LENGTH_SHORT).show()
            }
        }
        d.setOnDismissListener {
            ui.removeCallbacks(tik)
            Umumiy.kalibrlashOchiq = false
        }
        d.show()
    }
}
