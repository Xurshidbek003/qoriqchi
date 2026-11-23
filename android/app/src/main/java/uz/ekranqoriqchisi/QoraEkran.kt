package uz.ekranqoriqchisi

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Boshqa barcha ilovalar ustida turadigan qora ekran.
 * «Ota-ona» tugmasi orqali PIN kiritib, himoyani vaqtincha to'xtatish mumkin.
 */
class QoraEkran(
    private val ctx: Context,
    private val pinTekshir: (String) -> Boolean,
    private val pinTogriBolsa: () -> Unit,
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var ildiz: FrameLayout? = null
    private lateinit var emoji: TextView
    private lateinit var sarlavha: TextView
    private lateinit var matn: TextView
    private lateinit var qoshimcha: TextView
    private lateinit var pinPanel: LinearLayout
    private lateinit var pinMaydon: EditText
    private lateinit var pinXato: TextView
    private lateinit var params: WindowManager.LayoutParams

    val korinadi: Boolean get() = ildiz != null

    private fun sp(v: Float) = v
    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private fun matnView(olcham: Float, rang: Int, qalin: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(olcham))
        setTextColor(rang)
        gravity = Gravity.CENTER
        if (qalin) setTypeface(typeface, Typeface.BOLD)
    }

    private fun qurish(): FrameLayout {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true      // pastdagi ilovaga bosish o'tmasin
            isFocusable = true
        }
        val markaz = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        emoji = matnView(64f, Color.WHITE)
        sarlavha = matnView(28f, Color.WHITE, true).apply { setPadding(0, dp(12), 0, dp(8)) }
        matn = matnView(17f, Color.parseColor("#D1D5DB"))
        qoshimcha = matnView(44f, Color.parseColor("#60A5FA"), true).apply { setPadding(0, dp(20), 0, 0) }
        markaz.addView(emoji); markaz.addView(sarlavha); markaz.addView(matn); markaz.addView(qoshimcha)

        // PIN paneli (yashirin)
        pinPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(28), 0, 0)
        }
        val pinIzoh = matnView(14f, Color.WHITE).apply {
            text = "Ota-ona PIN kodi — himoya $PAUZA_DAQIQA daqiqaga to'xtaydi"
        }
        pinMaydon = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "PIN"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            minEms = 6
        }
        pinXato = matnView(13f, Color.parseColor("#F87171"))
        val tugmalar = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val bekor = Button(ctx).apply { text = "Bekor"; setOnClickListener { pinniYashir() } }
        val ok = Button(ctx).apply { text = "Tasdiqlash"; setOnClickListener { pinniTekshir() } }
        tugmalar.addView(bekor); tugmalar.addView(ok)
        pinPanel.addView(pinIzoh); pinPanel.addView(pinMaydon); pinPanel.addView(pinXato); pinPanel.addView(tugmalar)
        markaz.addView(pinPanel)

        root.addView(markaz, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val otaOna = TextView(ctx).apply {
            text = "🔒 Ota-ona"
            setTextColor(Color.parseColor("#6B7280"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { pinniKorsat() }
        }
        root.addView(otaOna, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dp(32) })
        return root
    }

    fun korsat(emojiMatn: String, sarlavhaMatn: String, asosiy: String, qosh: String) {
        if (!Settings.canDrawOverlays(ctx)) return
        if (ildiz == null) {
            val v = qurish()
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE,
            )
            try {
                wm.addView(v, params)
                ildiz = v
            } catch (e: Exception) {
                return
            }
        }
        emoji.text = emojiMatn
        sarlavha.text = sarlavhaMatn
        matn.text = asosiy
        qoshimcha.text = qosh
    }

    fun yashir() {
        ildiz?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        ildiz = null
    }

    private fun pinniKorsat() {
        val v = ildiz ?: return
        pinPanel.visibility = View.VISIBLE
        pinXato.text = ""
        pinMaydon.setText("")
        // Klaviatura chiqishi uchun oyna fokuslanadigan bo'lishi kerak
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        wm.updateViewLayout(v, params)
        pinMaydon.requestFocus()
        pinMaydon.postDelayed({
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(pinMaydon, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun pinniYashir() {
        val v = ildiz ?: return
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(pinMaydon.windowToken, 0)
        pinPanel.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(v, params)
    }

    private fun pinniTekshir() {
        if (pinTekshir(pinMaydon.text.toString())) {
            pinniYashir()
            pinTogriBolsa()
        } else {
            pinXato.text = "PIN noto'g'ri"
            pinMaydon.setText("")
        }
    }
}
