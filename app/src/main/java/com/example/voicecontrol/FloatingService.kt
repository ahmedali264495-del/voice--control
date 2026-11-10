package com.example.voicecontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service(), RecognitionListener {

    private lateinit var wm: WindowManager
    private var button: TextView? = null
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private val bg = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.RED)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        stopListening()
        button?.let { wm.removeView(it) }
        button = null
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channelId = "voice_ctl"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "التحكم الصوتي", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, channelId)
            .setContentTitle("التحكم الصوتي شغال")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    private fun addButton() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val tv = TextView(this).apply {
            text = "🎤"
            textSize = 24f
            gravity = Gravity.CENTER
            background = bg
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 300

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var downTime = 0L

        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    downTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 15 || abs(dy) > 15) moved = true
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        wm.updateViewLayout(tv, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (System.currentTimeMillis() - downTime > 1000) stopSelf() else toggle()
                    }
                }
            }
            true
        }
        wm.addView(tv, lp)
        button = tv
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun updateColor() {
        bg.setColor(if (listening) Color.rgb(0, 170, 60) else Color.RED)
    }

    private fun toggle() {
        if (listening) stopListening() else startListening()
    }

    private fun startListening() {
        if (ControlService.instance == null) {
            toast("فعّل خدمة إمكانية الوصول الأول")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("التعرف على الصوت مش متاح على الموبايل")
            return
        }
        listening = true
        updateColor()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        listen()
    }

    private fun stopListening() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        updateColor()
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(i)
    }

    private fun restart() {
        if (listening) handler.postDelayed({ if (listening) listen() }, 400)
    }

    // ---------- فهم الأوامر ----------

    private fun norm(s: String): String = s.lowercase()
        .replace(Regex("[\\u064B-\\u065F]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')

    private fun handle(raw: String) {
        val acc = ControlService.instance
        if (acc == null) {
            toast("خدمة إمكانية الوصول مش شغالة")
            return
        }
        val t = norm(raw)
        val flat = t.replace(" ", "")
        toast("سمعت: $raw")
        when {
            t.contains("تعليق") || t.contains("كومنت") || t.contains("comment") -> {
                if (t.contains("افتح") || t.contains("اعرض") || t.contains("open")) {
                    if (!acc.openComments()) toast("ما لقيتش زر التعليقات")
                } else {
                    acc.scrollComments()
                }
            }
            (t.contains("افتح") || t.contains("open")) &&
                (flat.contains("تيكتوك") || flat.contains("tiktok")) ->
                openApp(listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "تيك توك")
            (t.contains("افتح") || t.contains("open")) &&
                (flat.contains("فيسبوك") || flat.contains("facebook") || flat.contains("فيس")) ->
                openApp(listOf("com.facebook.katana"), "فيسبوك")
            t.contains("بعده") || t.contains("التالي") || t.contains("next") || t.contains("فيديو") ->
                acc.nextVideo()
            t.contains("سكرول") || t.contains("قلب") || t.contains("تصفح") ||
                t.contains("نزل") || t.contains("scroll") ->
                acc.slowScroll()
            else -> toast("ما فهمتش: $raw")
        }
    }

    private fun openApp(packages: List<String>, name: String) {
        for (p in packages) {
            val intent = packageManager.getLaunchIntentForPackage(p)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }
        }
        toast("$name مش متثبت")
    }

    // ---------- RecognitionListener ----------

    override fun onResults(results: Bundle?) {
        val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (said != null) handle(said)
        restart()
    }

    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            toast("مفيش سماح للمايك")
            stopListening()
            return
        }
        restart()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
