package app.teleprompter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var listView: ListView
    private lateinit var empty: TextView
    private val reqImport = 1

    private val seed = "Bem-vindo ao seu teleprompter.\n\n" +
        "Toque em “Abrir teleprompter flutuante”, depois abra a câmera do celular e comece a gravar.\n\n" +
        "A janela do teleprompter fica por cima da câmera. Arraste pela barra de cima para mover, " +
        "use o canto de baixo para mudar o tamanho e toque no texto para iniciar ou pausar.\n\n" +
        "Posicione a janela perto da lente para manter o contato visual com quem vai assistir.\n\n" +
        "Apague este texto e cole o seu roteiro."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        if (store.all().isEmpty()) {
            store.upsert(Script(UUID.randomUUID().toString(), "Exemplo", seed, System.currentTimeMillis()))
        }

        val p = Ui.dp(this, 18)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.setPadding(p, p, p, p)

        val title = TextView(this)
        title.text = "Teleprompter"
        title.textSize = 28f
        title.setTextColor(Ui.FG)
        title.setTypeface(null, Typeface.BOLD)
        root.addView(title)

        val hint = TextView(this)
        hint.text = "Abra um roteiro e toque em “Abrir teleprompter flutuante” para usar sobre a câmera."
        hint.setTextColor(Ui.MUTED)
        hint.textSize = 14f
        val hp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        hp.topMargin = Ui.dp(this, 6)
        root.addView(hint, hp)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val bNew = Ui.button(this, "Novo roteiro", true)
        val bImp = Ui.button(this, "Importar .txt/.md", false)
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp1.rightMargin = Ui.dp(this, 8)
        row.addView(bNew, lp1)
        row.addView(bImp, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val rp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rp.topMargin = Ui.dp(this, 16)
        root.addView(row, rp)

        empty = TextView(this)
        empty.text = "Nenhum roteiro ainda."
        empty.setTextColor(Ui.MUTED)
        empty.visibility = View.GONE
        root.addView(empty)

        listView = ListView(this)
        listView.divider = null
        listView.setBackgroundColor(Ui.BG)
        val lvp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        lvp.topMargin = Ui.dp(this, 12)
        root.addView(listView, lvp)

        setContentView(root)

        bNew.setOnClickListener {
            val s = Script(UUID.randomUUID().toString(), "", "", System.currentTimeMillis())
            store.upsert(s)
            openEditor(s.id)
        }
        bImp.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/markdown", "text/*"))
            startActivityForResult(i, reqImport)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = store.all().sortedByDescending { it.updated }
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val adapter = object : ArrayAdapter<Script>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val s = getItem(position)!!
                val t1 = v.findViewById<TextView>(android.R.id.text1)
                val t2 = v.findViewById<TextView>(android.R.id.text2)
                t1.text = if (s.title.isBlank()) "Sem título" else s.title
                t1.setTextColor(Ui.FG)
                t1.textSize = 18f
                t2.text = s.text.replace(Regex("\\s+"), " ").trim().take(90)
                t2.setTextColor(Ui.MUTED)
                return v
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ -> openEditor(items[position].id) }
    }

    private fun openEditor(id: String) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("id", id))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqImport || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            var name = "Importado"
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx).substringBeforeLast('.')
            }
            val s = Script(UUID.randomUUID().toString(), name, text.replace("\r\n", "\n"), System.currentTimeMillis())
            store.upsert(s)
            openEditor(s.id)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível ler o arquivo. Use um .txt ou .md.", Toast.LENGTH_LONG).show()
        }
    }
}
