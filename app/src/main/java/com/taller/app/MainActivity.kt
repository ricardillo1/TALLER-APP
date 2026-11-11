package com.taller.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    companion object {
        // Cambia esta dirección si tu sistema se mueve a otro hosting o dominio.
        private const val START_URL = "https://herramientas.unaux.com/taller/"
        private const val ALLOWED_HOST_SUFFIX = "unaux.com"
    }

    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorView: View

    private var hadError = false
    private var lastUrl: String = START_URL
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: (() -> Unit)? = null

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingDownload?.invoke()
            else Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
            pendingDownload = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        swipe = findViewById(R.id.swipe)
        progress = findViewById(R.id.progress)
        errorView = findViewById(R.id.error_view)

        findViewById<Button>(R.id.retry).setOnClickListener {
            hadError = false
            errorView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(lastUrl)
        }

        swipe.setOnRefreshListener { webView.reload() }
        // Solo permite "jalar para recargar" cuando la página está arriba del todo.
        swipe.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: ""
                val host = uri.host ?: ""
                val internal = (scheme == "http" || scheme == "https") &&
                    host.endsWith(ALLOWED_HOST_SUFFIX)
                if (internal) return false
                // Enlaces externos, tel:, mailto:, WhatsApp, etc. se abren fuera de la app.
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "No se puede abrir este enlace", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                lastUrl = url
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                swipe.isRefreshing = false
                if (!hadError) {
                    errorView.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    hadError = true
                    lastUrl = request.url.toString()
                    webView.visibility = View.INVISIBLE
                    errorView.visibility = View.VISIBLE
                    swipe.isRefreshing = false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                if (newProgress == 100) progress.visibility = View.GONE
            }

            // Para subir fotos o archivos desde formularios (por ejemplo, fotos del vehículo).
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    fileLauncher.launch(params.createIntent())
                    true
                } catch (_: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        // Descargas (reportes PDF, Excel, etc.)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val start = {
                try {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimeType)
                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                        addRequestHeader("User-Agent", userAgent)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimeType)
                        )
                    }
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this, "Descargando…", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "No se pudo descargar el archivo", Toast.LENGTH_SHORT).show()
                }
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pendingDownload = start
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                start()
            }
        }

        // Botón "atrás" navega dentro de la web; si no hay historial, cierra la app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    hadError = false
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
