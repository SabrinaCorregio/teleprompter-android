package app.teleprompter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Script(val id: String, var title: String, var text: String, var updated: Long)

class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("tp", Context.MODE_PRIVATE)

    fun all(): MutableList<Script> {
        val out = mutableListOf<Script>()
        val raw = sp.getString("scripts", null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(Script(o.getString("id"), o.optString("title"), o.optString("text"), o.optLong("updated")))
                }
            } catch (e: Exception) {
                // dados corrompidos: começa vazio
            }
        }
        return out
    }

    fun saveAll(list: List<Script>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("text", s.text)
                    .put("updated", s.updated)
            )
        }
        sp.edit().putString("scripts", arr.toString()).apply()
    }

    fun get(id: String): Script? = all().firstOrNull { it.id == id }

    fun upsert(s: Script) {
        val l = all()
        val i = l.indexOfFirst { it.id == s.id }
        if (i >= 0) l[i] = s else l.add(s)
        saveAll(l)
    }

    fun delete(id: String) {
        saveAll(all().filter { it.id != id })
    }

    var speed: Int
        get() = sp.getInt("speed", 30)
        set(v) { sp.edit().putInt("speed", v).apply() }

    var size: Int
        get() = sp.getInt("size", 28)
        set(v) { sp.edit().putInt("size", v).apply() }

    var mirror: Boolean
        get() = sp.getBoolean("mirror", false)
        set(v) { sp.edit().putBoolean("mirror", v).apply() }

    var bgLevel: Int
        get() = sp.getInt("bgLevel", 0)
        set(v) { sp.edit().putInt("bgLevel", v).apply() }

    var winX: Int
        get() = sp.getInt("wx", Int.MIN_VALUE)
        set(v) { sp.edit().putInt("wx", v).apply() }

    var winY: Int
        get() = sp.getInt("wy", Int.MIN_VALUE)
        set(v) { sp.edit().putInt("wy", v).apply() }

    var winW: Int
        get() = sp.getInt("ww", 0)
        set(v) { sp.edit().putInt("ww", v).apply() }

    var winH: Int
        get() = sp.getInt("wh", 0)
        set(v) { sp.edit().putInt("wh", v).apply() }
}
