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

        textToSpeech = TextToSpeech(this, this)
        setupWebView()
    }

    private fun setupWebView() {
        webView = findViewById(R.id.myWebView)
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidTTS")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript("if(window.onAndroidTTSReady) { window.onAndroidTTSReady(); }", null)
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e("TTS", "فشل تهيئة TextToSpeech")
            isTtsReady = false
            return
        }

        val preferredLocales = listOf(
            Locale("ar", "SA"),
            Locale("ar", "EG"),
            Locale("ar"),
            Locale.US,
            Locale.ENGLISH
        )

        var selectedLocale: Locale? = null

        for (locale in preferredLocales) {
            val result = textToSpeech?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                selectedLocale = locale
                break
            }
        }

        if (selectedLocale == null) {
            Log.e("TTS", "لا توجد لغة مدعومة على هذا الجهاز")
            isTtsReady = false
            return
        }

        val languageResult = textToSpeech?.setLanguage(selectedLocale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TTS", "اللغة المختارة غير مدعومة على هذا الجهاز: $selectedLocale")
            isTtsReady = false
        } else {
            textToSpeech?.setSpeechRate(1.0f)
            textToSpeech?.setPitch(1.0f)
            isTtsReady = true
            Log.d("TTS", "تم تجهيز TextToSpeech بنجاح: $selectedLocale")
        }
    }

    inner class WebAppInterface(private val context: Context) {

        @JavascriptInterface
        fun speak(text: String?) {
            if (text.isNullOrBlank()) return

            if (!isTtsReady) {
                Log.e("TTS", "TextToSpeech غير جاهز. النص لم يقرأ: $text")
                return
            }

            runOnUiThread {
                val utteranceId = "utteranceId_${System.currentTimeMillis()}"
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
        }

        @JavascriptInterface
        fun stop() {
            runOnUiThread {
                textToSpeech?.stop()
            }
        }

        @JavascriptInterface
        fun isReady(): Boolean {
            return isTtsReady
        }

        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
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
