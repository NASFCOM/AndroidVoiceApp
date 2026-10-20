package com.voiceapp.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. تهيئة محرك تحويل النص إلى كلام (TTS) الخاص بنظام أندرويد
        textToSpeech = TextToSpeech(this, this)

        // 2. إعداد الـ WebView
        setupWebView()

        // 3. التحقق من صلاحيات الميكروفون إذا تم استخدام الاستماع الصوتي
        // لم يتم تفعيل الاستماع بالميكروفون
    }

    private fun setupWebView() {
        webView = findViewById(R.id.myWebView)
        val settings = webView.settings

        // تفعيل جافاسكريبت (أساسي لتشغيل كود المساعد الخاص بك)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // السماح بالوصول لملفات التطبيق المحلية (assets)
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // إيقاف شرط تفاعل المستخدم لتشغيل الصوت (هام جداً لقراءة المحتوى تلقائياً)
        settings.mediaPlaybackRequiresUserGesture = false

        // 4. السحر هنا: ربط جسر أندرويد الأصلي بصفحة الويب باسم "AndroidTTS"
        // هذا يسمح لملف الـ HTML باستدعاء نطق أندرويد مباشرة!
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidTTS")

        // 5. ضبط WebChromeClient لمنح أذونات الميكروفون والكاميرا إن وجدت
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                // منح الإذن لصفحة الويب تلقائياً عند طلبها
                request?.let {
                    runOnUiThread {
                        it.grant(it.resources)
                    }
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()}")
                return true
            }
        }

        // إبقاء التنقل داخل الـ WebView بدلاً من فتح المتصفح الخارجي
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // إشعار صفحة HTML بأن جسر الأندرويد جاهز للاستخدام
                webView.evaluateJavascript("if(window.onAndroidTTSReady) { window.onAndroidTTSReady(); }", null)
            }
        }

        // تحميل ملف HTML الخاص بك
        webView.loadUrl("file:///android_asset/index.html")
    }

    // تهيئة محرك النطق عند جاهزيته
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "اللغة غير مدعومة في محرك TTS الحالي للجهاز")
            } else {
                isTtsReady = true
                Log.d("TTS", "محرك النطق في أندرويد جاهز للعمل بنجاح!")
            }
        } else {
            Log.e("TTS", "فشل تهيئة TextToSpeech")
        }
    }

    // فئة الجسر بين الجافاسكريبت ونظام أندرويد
    inner class WebAppInterface(private val context: Context) {

        /**
         * دالة نطق النص - تستدعيها صفحة الويب عبر: AndroidTTS.speak("النص المطلوب")
         */
        @JavascriptInterface
        fun speak(text: String?) {
            if (!text.isNullOrEmpty() && isTtsReady) {
                runOnUiThread {
                    // QUEUE_FLUSH يوقف أي كلام سابق ويبدأ قراءة النص الجديد فوراً
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_${System.currentTimeMillis()}")
                }
            }
        }

        /**
         * دالة إيقاف القراءة فوراً
         */
        @JavascriptInterface
        fun stop() {
            runOnUiThread {
                textToSpeech?.stop()
            }
        }

        /**
         * فحص هل المحرك جاهز أم لا
         */
        @JavascriptInterface
        fun isReady(): Boolean {
            return isTtsReady
        }

        /**
         * إظهار رسالة سريعة في أندرويد للتأكد من الاتصال
         */
        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    

    override fun onDestroy() {
        // تحرير الذاكرة وإيقاف النطق عند إغلاق التطبيق
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}