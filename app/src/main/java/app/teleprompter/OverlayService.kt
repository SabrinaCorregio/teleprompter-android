package app.teleprompter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "app.teleprompter.STOP"
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var sv: ScrollView
    private lateinit var tv: TextView
    private lateinit var countdown: TextView
    private lateinit var btnPlay: TextView
    private lateinit var handle: TextView
    private lateinit var bg: GradientDrawable

    private var y = 0f
    private var playing = false
    private var dragging = false
    private var lastNs = 0L
    private var counting: Runnable? = null
    private val bgLevels = intArrayOf(0xDD, 0x99, 0x44)

    private val syncRunnable = Runnable {
        dragging = false
        y = sv.scrollY.toFloat()
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!playing || root == null) return
            val dt = ((frameTimeNanos - lastNs) / 1_000_000_000f).coerceIn(0f, 0.1f)
            lastNs = frameTimeNanos
            if (!dragging) {
                val max = (tv.height - sv.height).coerceAtLeast(0)
                y += pps() * dt
                if (y >= max) {
                    y = max.toFloat()
                    sv.scrollTo(0, max)
                    setPlaying(false)
                    return
                }
                sv.scrollTo(0, y.toInt())
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = Store(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra("id")
        val script = if (id != null) store.get(id) else null
        if (script == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        removeOverlay()
        buildOverlay(script)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pps(): Float =
        store.speed * 1.9f * (store.size / 48f) * resources.displayMetrics.density

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Teleprompter flutuante", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat), "Fechar", stopPi
        ).build()
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Teleprompter ativo")
            .setContentText("Toque em Fechar para encerrar a janela flutuante.")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(openPi)
            .addAction(action)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun tb(label: String, onClick: (() -> Unit)?): TextView {
        val t = TextView(this)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.textSize = 16f
        t.gravity = Gravity.CENTER
        t.setTypeface(null, Typeface.BOLD)
        if (onClick != null) t.setOnClickListener { onClick() }
        return t
    }

    private fun buildOverlay(script: Script) {
        val dm = resources.displayMetrics
        var w = if (store.winW > 0) store.winW else (dm.widthPixels * 0.92f).toInt()
        var h = if (store.winH > 0) store.winH else (dm.heightPixels * 0.38f).toInt()
        w = w.coerceIn(dp(200), dm.widthPixels)
        h = h.coerceIn(dp(140), dm.heightPixels)

        params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val rawX = if (store.winX != Int.MIN_VALUE) store.winX else (dm.widthPixels - w) / 2
        val rawY = if (store.winY != Int.MIN_VALUE) store.winY else dp(70)
        params.x = rawX.coerceIn(0, (dm.widthPixels - w).coerceAtLeast(0))
        params.y = rawY.coerceIn(0, (dm.heightPixels - h).coerceAtLeast(0))

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.argb(bgLevels[store.bgLevel.coerceIn(0, 2)], 0, 0, 0))
        container.background = bg

        // Barra de cima
        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.argb(50, 255, 255, 255))

        handle = tb("≡ ${store.speed}", null)
        btnPlay = tb("▶") { togglePlay() }
        val slower = tb("−") {
            store.speed = (store.speed - 3).coerceIn(1, 100)
            handle.text = "≡ ${store.speed}"
        }
        val faster = tb("+") {
            store.speed = (store.speed + 3).coerceIn(1, 100)
            handle.text = "≡ ${store.speed}"
        }
        val smaller = tb("A−") {
            store.size = (store.size - 2).coerceIn(14, 70)
            tv.textSize = store.size.toFloat()
        }
        val bigger = tb("A+") {
            store.size = (store.size + 2).coerceIn(14, 70)
            tv.textSize = store.size.toFloat()
        }
        val mirror = tb("⇋") {
            store.mirror = !store.mirror
            tv.scaleX = if (store.mirror) -1f else 1f
        }
        val shade = tb("◐") {
            store.bgLevel = (store.bgLevel + 1) % 3
            bg.setColor(Color.argb(bgLevels[store.bgLevel], 0, 0, 0))
        }
        val close = tb("✕") { stopSelf() }
        close.setTextColor(Color.parseColor("#FF8080"))

        val views = listOf(handle, btnPlay, slower, faster, smaller, bigger, mirror, shade, close)
        for (v in views) {
            toolbar.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        container.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        // Texto
        val frameLayout = FrameLayout(this)
        sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        tv = TextView(this)
        tv.text = if (script.text.isBlank()) "(roteiro vazio)" else script.text
        tv.setTextColor(Color.WHITE)
        tv.setTypeface(null, Typeface.BOLD)
        tv.setShadowLayer(6f, 0f, 2f, Color.BLACK)
        tv.textSize = store.size.toFloat()
        tv.setLineSpacing(0f, 1.2f)
        tv.setPadding(dp(14), dp(22), dp(14), dp(320))
        tv.scaleX = if (store.mirror) -1f else 1f
        sv.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frameLayout.addView(sv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        countdown = TextView(this)
        countdown.setTextColor(Color.WHITE)
        countdown.textSize = 72f
        countdown.setTypeface(null, Typeface.BOLD)
        countdown.gravity = Gravity.CENTER
        countdown.visibility = View.GONE
        frameLayout.addView(countdown, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val resize = TextView(this)
        resize.text = "◢"
        resize.setTextColor(Color.argb(200, 255, 255, 255))
        resize.textSize = 22f
        resize.gravity = Gravity.CENTER
        frameLayout.addView(resize, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM or Gravity.END))

        container.addView(frameLayout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root = container

        // Toque no texto = iniciar/pausar
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                togglePlay()
                return true
            }
        })
        sv.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(syncRunnable)
                    dragging = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(syncRunnable)
                    handler.postDelayed(syncRunnable, 350)
                }
            }
            false
        }

        // Arrastar a janela pela barra de cima
        handle.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY; ox = params.x; oy = params.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = ox + (e.rawX - sx).toInt()
                        params.y = oy + (e.rawY - sy).toInt()
                        updateLayout()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        store.winX = params.x
                        store.winY = params.y
                    }
                }
                return true
            }
        })

        // Redimensionar pelo canto
        resize.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f
            var sy = 0f
            var ow = 0
            var oh = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY; ow = params.width; oh = params.height
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val d = resources.displayMetrics
                        params.width = (ow + (e.rawX - sx)).toInt().coerceIn(dp(200), d.widthPixels)
                        params.height = (oh + (e.rawY - sy)).toInt().coerceIn(dp(140), d.heightPixels)
                        updateLayout()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        store.winW = params.width
                        store.winH = params.height
                    }
                }
                return true
            }
        })

        try {
            wm.addView(container, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir a janela flutuante. Verifique a permissão.", Toast.LENGTH_LONG).show()
            root = null
            stopSelf()
        }
    }

    private fun updateLayout() {
        val r = root ?: return
        try {
            wm.updateViewLayout(r, params)
        } catch (e: Exception) {
            // janela já removida
        }
    }

    private fun removeOverlay() {
        playing = false
        Choreographer.getInstance().removeFrameCallback(frame)
        handler.removeCallbacksAndMessages(null)
        counting = null
        val r = root
        if (r != null) {
            try {
                wm.removeView(r)
            } catch (e: Exception) {
                // já removida
            }
        }
        root = null
    }

    private fun togglePlay() {
        if (counting != null) {
            cancelCountdown()
        } else if (playing) {
            setPlaying(false)
        } else {
            startWithCountdown()
        }
    }

    private fun startWithCountdown() {
        if (y > 2f) {
            setPlaying(true)
            return
        }
        var n = 3
        countdown.text = n.toString()
        countdown.visibility = View.VISIBLE
        btnPlay.text = "❚❚"
        val r = object : Runnable {
            override fun run() {
                n--
                if (n <= 0) {
                    countdown.visibility = View.GONE
                    counting = null
                    setPlaying(true)
                } else {
                    countdown.text = n.toString()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        counting = r
        handler.postDelayed(r, 1000)
    }

    private fun cancelCountdown() {
        counting?.let { handler.removeCallbacks(it) }
        counting = null
        countdown.visibility = View.GONE
        btnPlay.text = "▶"
    }

    private fun setPlaying(on: Boolean) {
        if (on) {
            val max = (tv.height - sv.height).coerceAtLeast(0)
            if (max > 0 && sv.scrollY >= max - 2) {
                y = 0f
                sv.scrollTo(0, 0)
            } else {
                y = sv.scrollY.toFloat()
            }
            playing = true
            lastNs = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(frame)
            btnPlay.text = "❚❚"
        } else {
            playing = false
            Choreographer.getInstance().removeFrameCallback(frame)
            btnPlay.text = "▶"
        }
    }
}
