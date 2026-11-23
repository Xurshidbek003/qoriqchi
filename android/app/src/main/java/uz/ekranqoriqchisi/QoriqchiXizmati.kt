package uz.ekranqoriqchisi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max

/**
 * Fonda ishlaydigan xizmat: old kamera orqali yuzni kuzatadi va kerak bo'lsa
 * ekranni qoraytiradi.
 */
class QoriqchiXizmati : LifecycleService() {

    companion object {
        const val KANAL = "qoriqchi"
        const val BILDIRISHNOMA_ID = 1
        private const val KADR_ORALIGI_MS = 330L   // ~3 kadr/soniya — batareyani tejash

        fun ishgaTushir(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, QoriqchiXizmati::class.java))
        }

        fun toxtat(ctx: Context) {
            ctx.stopService(Intent(ctx, QoriqchiXizmati::class.java))
        }
    }

    private val asosiy = Handler(Looper.getMainLooper())
    private lateinit var sozlamalar: Sozlamalar
    private lateinit var mantiq: QoriqchiMantiq
    private lateinit var qora: QoraEkran
    private lateinit var detektor: FaceDetector
    private lateinit var tahlilchi: ExecutorService
    private var kameraProvider: ProcessCameraProvider? = null
    private var oxirgiKadrMs = 0L
    private var oxirgiNatijaMs = 0L
    private var ekranYoniq = true

    private val ekranQabulchisi = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { ekranYoniq = false; kamerani_ochir() }
                Intent.ACTION_SCREEN_ON -> { ekranYoniq = true; kamerani_yoq() }
            }
        }
    }

    private val tikchi = object : Runnable {
        override fun run() {
            // Kadr kelmayotgan bo'lsa ham (ekran o'chgan, kamera band) vaqt hisoblanaversin
            if (SystemClock.elapsedRealtime() - oxirgiNatijaMs > 1500) qadam(null)
            asosiy.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sozlamalar = Sozlamalar(this)
        mantiq = QoriqchiMantiq(sozlamalar.mantiqSozlamalari())
        qora = QoraEkran(this, { sozlamalar.pinTogri(it) }) {
            Umumiy.pauzaGacha = SystemClock.elapsedRealtime() + PAUZA_DAQIQA * 60_000L
            mantiq.tanaffusniTugatish()
            mantiq.ochish()
            qora.yashir()
        }
        detektor = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
        tahlilchi = Executors.newSingleThreadExecutor()

        kanalYarat()
        val turi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        try {
            ServiceCompat.startForeground(this, BILDIRISHNOMA_ID, bildirishnoma(), turi)
        } catch (e: Exception) {
            // Masalan, tizim xizmatni fonda qayta ishga tushirganda Android 14+ kameraga ruxsat bermaydi
            Umumiy.kameraXatosi = "Himoyani ilovani ochib, qaytadan yoqing."
            stopSelf()
            return
        }

        ContextCompat.registerReceiver(
            this, ekranQabulchisi,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        Umumiy.ishlayapti = true
        kamerani_yoq()
        asosiy.post(tikchi)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        Umumiy.ishlayapti = false
        Umumiy.holat = Holat.NORMAL
        asosiy.removeCallbacksAndMessages(null)
        try { unregisterReceiver(ekranQabulchisi) } catch (_: Exception) {}
        kamerani_ochir()
        qora.yashir()
        detektor.close()
        tahlilchi.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------ kamera
    private fun kamerani_yoq() {
        val kelajak = ProcessCameraProvider.getInstance(this)
        kelajak.addListener({
            try {
                val provider = kelajak.get()
                kameraProvider = provider
                val tahlil = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            ).build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                tahlil.setAnalyzer(tahlilchi) { kadrniTahlil(it) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, tahlil)
                Umumiy.kameraXatosi = null
            } catch (e: Exception) {
                Umumiy.kameraXatosi = "Old kamerani ochib bo'lmadi: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun kamerani_ochir() {
        try { kameraProvider?.unbindAll() } catch (_: Exception) {}
    }

    @OptIn(ExperimentalGetImage::class)
    private fun kadrniTahlil(proxy: ImageProxy) {
        val hozir = SystemClock.elapsedRealtime()
        val media = proxy.image
        if (media == null || hozir - oxirgiKadrMs < KADR_ORALIGI_MS) {
            proxy.close()
            return
        }
        oxirgiKadrMs = hozir
        val burilish = proxy.imageInfo.rotationDegrees
        // Burilgandan keyingi kenglik
        val kenglik = if (burilish == 90 || burilish == 270) proxy.height else proxy.width
        val rasm = InputImage.fromMediaImage(media, burilish)

        detektor.process(rasm)
            .addOnSuccessListener { yuzlar ->
                val yuz = yuzlar.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val nisbat = yuz?.let {
                    val o = it.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                    val c = it.getLandmark(FaceLandmark.LEFT_EYE)?.position
                    val koz = if (o != null && c != null) hypot(c.x - o.x, c.y - o.y).toDouble() else 0.0
                    // Bosh burilganda ham ishlashi uchun yuz kengligi bilan pastki chegara
                    max(koz, 0.40 * it.boundingBox.width()) / kenglik
                }
                asosiy.post { qadam(nisbat) }
            }
            .addOnFailureListener { asosiy.post { qadam(null) } }
            .addOnCompleteListener { proxy.close() }
    }

    // ------------------------------------------------------------ mantiq
    private fun sekund() = SystemClock.elapsedRealtime() / 1000.0

    private fun qadam(nisbat: Double?) {
        oxirgiNatijaMs = SystemClock.elapsedRealtime()
        mantiq.s = sozlamalar.mantiqSozlamalari()
        val hozir = sekund()
        if (nisbat != null) {
            Umumiy.nisbatQosh(nisbat)
            Umumiy.oxirgiYuzVaqti = SystemClock.elapsedRealtime()
        }

        val pauzada = SystemClock.elapsedRealtime() < Umumiy.pauzaGacha
        if (pauzada) {
            mantiq.yangilash(hozir, null)
            mantiq.ochish()
            mantiq.tomoshaniTashlash()
        } else {
            mantiq.yangilash(hozir, if (ekranYoniq) nisbat else null)
        }
        // Kalibrlash paytida ekran qoraymasin
        if (Umumiy.kalibrlashOchiq && mantiq.holat == Holat.YAQIN) mantiq.ochish()

        Umumiy.holat = mantiq.holat
        Umumiy.masofaSm = mantiq.masofaSm
        Umumiy.tomoshaSoniya = mantiq.tomoshaSoniya(hozir)
        Umumiy.yaqinSoni = mantiq.yaqinlashishlarSoni
        Umumiy.tanaffusSoni = mantiq.tanaffuslarSoni

        when (mantiq.holat) {
            Holat.YAQIN -> qora.korsat(
                "👀", "Ekranga juda yaqinsan!",
                "Ko'zlaringni asra — telefonni uzoqroq tut.\n" +
                    "Kamida ${sozlamalar.chegaraSm.toInt()} sm uzoqlikda tutsang, ekran ochiladi.",
                mantiq.masofaSm?.let { "${it.toInt()} sm" } ?: ""
            )
            Holat.TANAFFUS -> {
                val q = (mantiq.tanaffusQoldi(hozir) + 0.999).toInt()
                qora.korsat(
                    "😌", "Ko'zlaring dam olsin!",
                    "Sen ${daqiqaMatn(sozlamalar.tomoshaDaqiqa)} daqiqa uzluksiz ekranga qarading.\n" +
                        "Uzoqqa — derazadan tashqariga qara, ko'zlaringni yumib-och.",
                    "%02d:%02d".format(q / 60, q % 60)
                )
            }
            Holat.NORMAL -> qora.yashir()
        }
    }

    private fun daqiqaMatn(d: Float): String =
        if (d % 1f == 0f) d.toInt().toString() else d.toString()

    // ------------------------------------------------------------ bildirishnoma
    private fun kanalYarat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(KANAL, "Ekran qo'riqchisi", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Himoya yoqilganligini ko'rsatadi"
                setShowBadge(false)
            }
        )
    }

    private fun bildirishnoma(): Notification {
        val ochish = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_bildirishnoma)
            .setContentTitle("Ekran qo'riqchisi yoqilgan")
            .setContentText("Bolaning ko'zlari himoyada 👁")
            .setContentIntent(ochish)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
