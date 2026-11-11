package app.mindblow.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * Uma WebView com a interface que o servidor daqui (o `Node`) entrega, como o app
 * desktop. As notas moram no aparelho; sincronizar com outro mindblow é opcional e se
 * liga pela própria interface (QR do canto de cima).
 */
class Main : Activity() {
    companion object {
        const val KIND = "kind"
        const val NOTE = "note"
        private const val PICK = 1
    }

    private lateinit var web: WebView
    private lateinit var down: View
    private var files: ValueCallback<Array<Uri>>? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.main)
        web = findViewById(R.id.web)
        down = findViewById(R.id.down)
        edge(findViewById(R.id.root))
        prepare()
        findViewById<View>(R.id.retry).setOnClickListener { open(null) }
        open(if (state == null) hash(intent) else null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hash(intent)?.let(::open)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!web.isShown) return super.onBackPressed()
        web.evaluateJavascript("window.mindblowBack?.() === true") {
            if (it != "true") super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        if (code != PICK) return super.onActivityResult(code, result, data)
        val clip = data?.clipData
        val uris = if (result == RESULT_OK && clip != null) {
            Array(clip.itemCount) { clip.getItemAt(it).uri }
        } else {
            WebChromeClient.FileChooserParams.parseResult(result, data)
        }
        files?.onReceiveValue(uris)
        files = null
    }

    /** O atalho, o widget e o compartilhar chegam como extra; a interface entende pelo hash. */
    private fun hash(i: Intent?): String? {
        if (i == null || i.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        val h = i.getStringExtra(NOTE)?.let { "open=${Uri.encode(it)}" }
            ?: i.getStringExtra(KIND)?.let { "new=${Uri.encode(it)}" }
        i.removeExtra(NOTE)
        i.removeExtra(KIND)
        return h
    }

    /** Sobe o servidor (fora da thread da tela: abre o banco) e carrega a interface com o token dele. */
    private fun open(hash: String?) {
        thread {
            val up = Node.up(this)
            if (up) Server.adopt(this)
            runOnUiThread {
                if (!up) return@runOnUiThread show(down)
                show(web)
                val t = Uri.encode(Node.token(this))
                web.loadUrl("${Node.BASE}/#token=$t" + hash?.let { "&$it" }.orEmpty())
            }
        }
    }

    private fun show(v: View) {
        for (x in listOf(web, down)) x.visibility = if (x == v) View.VISIBLE else View.GONE
    }

    private fun prepare() {
        web.setBackgroundColor(getColor(R.color.bg))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "$userAgentString mindblow-android/${BuildConfig.VERSION_NAME}"
        }
        web.addJavascriptInterface(Bridge(), "mindblowAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.host == "127.0.0.1" && r.url.port == Node.PORT) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                return true
            }

            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) show(down)
            }

            override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                if (r.isForMainFrame && e.statusCode >= 500) show(down)
            }

            // O sistema matou o renderer (falta de memória): refaz a tela em vez de derrubar o app.
            override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                recreate()
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                v: WebView,
                cb: ValueCallback<Array<Uri>>,
                p: FileChooserParams,
            ): Boolean {
                files?.onReceiveValue(null)
                files = cb
                val i = p.createIntent()
                if (p.mode == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                return runCatching {
                    @Suppress("DEPRECATION")
                    startActivityForResult(i, PICK)
                }.onFailure {
                    files = null
                    cb.onReceiveValue(null)
                }.isSuccess
            }
        }
    }

    /** O que a interface pede ao app (window.mindblowAndroid). */
    private inner class Bridge {
        /** Lê o QR de outro mindblow; o link volta para a interface, que liga o sync. */
        @JavascriptInterface
        fun scan() = runOnUiThread(::scan)
    }

    private fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts)
            .startScan()
            .addOnSuccessListener { b ->
                b.rawValue?.let { web.evaluateJavascript("window.mindblowLink?.(${JSONObject.quote(it)})", null) }
            }
            .addOnFailureListener { Toast.makeText(this, R.string.no_scanner, Toast.LENGTH_SHORT).show() }
    }

    /** A partir do Android 11 desenha por baixo das barras e põe o recuo à mão, teclado incluso. */
    private fun edge(root: View) {
        if (Build.VERSION.SDK_INT < 30) return
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsets.CONSUMED
        }
    }
}
