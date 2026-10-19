package app.teleprompter

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class EditorActivity : Activity() {

    private lateinit var store: Store
    private lateinit var script: Script
    private lateinit var titleEt: EditText
    private lateinit var bodyEt: EditText
    private lateinit var info: TextView
    private lateinit var delBtn: Button
    private var deleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val id = intent.getStringExtra("id")
        val found = if (id != null) store.get(id) else null
        if (found == null) {
            finish()
            return
        }
        script = found

        val p = Ui.dp(this, 16)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.setPadding(p, p, p, p)

        titleEt = styledEdit("Título do roteiro")
        titleEt.setText(script.title)
        titleEt.textSize = 20f
        titleEt.setSingleLine(true)
        root.addView(titleEt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        bodyEt = styledEdit("Cole ou escreva o seu roteiro aqui…")
        bodyEt.setText(script.text)
        bodyEt.textSize = 17f
        bodyEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        bodyEt.gravity = Gravity.TOP or Gravity.START
        val bp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        bp.topMargin = Ui.dp(this, 10)
        root.addView(bodyEt, bp)

        info = TextView(this)
        info.setTextColor(Ui.MUTED)
        info.textSize = 13f
        val ip = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ip.topMargin = Ui.dp(this, 8)
        ip.bottomMargin = Ui.dp(this, 10)
        root.addView(info, ip)

        val play = Ui.button(this, "Abrir teleprompter flutuante", true)
        root.addView(play, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        delBtn = Ui.button(this, "Excluir roteiro", false)
        delBtn.setTextColor(Ui.DANGER)
        val dp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dp2.topMargin = Ui.dp(this, 8)
        root.addView(delBtn, dp2)

        setContentView(root)
        updateInfo()

        bodyEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updateInfo() }
        })

        play.setOnClickListener { startOverlay() }

        delBtn.setOnClickListener {
            if (delBtn.text.toString() == "Excluir roteiro") {
                delBtn.text = "Toque de novo para confirmar"
                delBtn.postDelayed({ if (!deleted) delBtn.text = "Excluir roteiro" }, 3000)
            } else {
                deleted = true
                store.delete(script.id)
                finish()
            }
        }
    }

    private fun styledEdit(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(Ui.MUTED)
        e.setTextColor(Ui.FG)
        val bg = GradientDrawable()
        bg.cornerRadius = Ui.dp(this, 12).toFloat()
        bg.setColor(Ui.SURFACE)
        e.background = bg
        val pad = Ui.dp(this, 12)
        e.setPadding(pad, pad, pad, pad)
        return e
    }

    private fun updateInfo() {
        val n = Regex("\\S+").findAll(bodyEt.text).count()
        val secs = (n / 150f * 60f).toInt()
        val t = if (n == 0) "" else if (secs < 60) " · ≈ $secs s" else " · ≈ ${"%.1f".format(secs / 60f)} min"
        info.text = "$n palavras$t (a 150 palavras/min)"
    }

    private fun save() {
        if (deleted) return
        script.title = titleEt.text.toString()
        script.text = bodyEt.text.toString()
        script.updated = System.currentTimeMillis()
        store.upsert(script)
    }

    private fun startOverlay() {
        save()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Ative “Exibir sobre outros apps” para o Teleprompter e volte para tentar de novo.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java).putExtra("id", script.id))
        Toast.makeText(this, "Teleprompter aberto. Abra a câmera e toque no texto para iniciar.", Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
    }

    override fun onPause() {
        super.onPause()
        if (::titleEt.isInitialized) save()
    }
}
