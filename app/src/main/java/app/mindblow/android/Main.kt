package app.mindblow.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.concurrent.thread
import org.json.JSONTokener

/**
 * Uma WebView com a interface que o próprio servidor entrega, como o app desktop.
 * Por fora dela, só a tela de conectar (QR ou link) e a de sem conexão.
 */
class Main : Activity() {
    companion object {
        const val KIND = "kind"
        const val NOTE = "note"
        private const val PICK = 1
        private const val TOKEN =
            "(() => { try { return JSON.parse(localStorage.getItem('mindblow.token')) || '' } catch (e) { return '' } })()"
    }

    private lateinit var web: WebView
    private lateinit var setup: View
    private lateinit var down: View
    private lateinit var field: EditText
    private lateinit var note: TextView
    private var files: ValueCallback<Array<Uri>>? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.main)
        web = findViewById(R.id.web)
        setup = findViewById(R.id.setup)
        down = findViewById(R.id.down)
        field = findViewById(R.id.field)
        note = findViewById(R.id.note)
        edge(findViewById(R.id.root))
        prepare()

        findViewById<View>(R.id.scan).setOnClickListener { scan() }
        findViewById<View>(R.id.go).setOnClickListener { connect(field.text.toString()) }
        field.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_GO).also { if (it) connect(field.text.toString()) }
        }
        findViewById<View>(R.id.retry).setOnClickListener { open(null) }
        findViewById<View>(R.id.change).setOnClickListener {
            field.setText(Server.link(this))
            show(setup)
        }

        if (Server.link(this) == null) show(setup) else open(if (state == null) hash(intent) else null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val h = hash(intent) ?: return
        if (Server.link(this) != null) open(h)
    }

    override fun onPause() {
        super.onPause()
        keep()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            setup.isShown && Server.link(this) != null -> open(null)
            !web.isShown -> super.onBackPressed()
            else -> web.evaluateJavascript("window.mindblowBack?.() === true") {
                if (it != "true") super.onBackPressed()
            }
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
        val h = i.getStringExtra(NOTE)?.let { "#open=${Uri.encode(it)}" }
            ?: i.getStringExtra(KIND)?.let { "#new=${Uri.encode(it)}" }
        i.removeExtra(NOTE)
        i.removeExtra(KIND)
        return h
    }

    private fun open(hash: String?) {
        val link = Server.link(this) ?: return show(setup)
        show(web)
        web.loadUrl("$link/${hash.orEmpty()}")
    }

    private fun show(v: View) {
        for (x in listOf(web, setup, down)) x.visibility = if (x == v) View.VISIBLE else View.GONE
    }

    private fun prepare() {
        web.setBackgroundColor(getColor(R.color.bg))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "$userAgentString mindblow-android/${BuildConfig.VERSION_NAME}"
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val home = Uri.parse(Server.link(this@Main) ?: return false)
                if (r.url.host == home.host && r.url.port == home.port) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                return true
            }

            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) show(down)
            }

            override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                if (r.isForMainFrame && e.statusCode >= 500) show(down)
            }

            override fun onPageFinished(v: WebView, url: String) = keep()

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

    /** Guarda o token que a interface usa, para o compartilhar falar com a API. */
    private fun keep() {
        if (!web.isShown) return
        web.evaluateJavascript(TOKEN) { v ->
            val t = runCatching { JSONTokener(v).nextValue() as? String }.getOrNull() ?: return@evaluateJavascript
            Server.keep(this, t)
        }
    }

    private fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts)
            .startScan()
            .addOnSuccessListener { b -> b.rawValue?.let(::connect) }
            .addOnFailureListener { say(R.string.no_scanner) }
    }

    /** Aceita o link do QR (com ou sem #token=) ou um endereço digitado; sem esquema, tenta https e depois http. */
    private fun connect(raw: String) {
        val s = raw.trim().ifEmpty { return }
        val tries = (if ("://" in s) listOf(s) else listOf("https://$s", "http://$s")).map(Uri::parse)
        val u = tries.first()
        if (u.scheme !in setOf("http", "https") || u.host.isNullOrEmpty()) return say(R.string.bad_link)
        val token = Regex("(?:^|&)token=([^&]*)").find(u.encodedFragment.orEmpty())?.groupValues?.get(1)?.let(Uri::decode)
        busy(true)
        thread {
            val hit = tries.map(::origin).firstOrNull { Server.reach(it, token ?: saved(it)) }
            runOnUiThread {
                busy(false)
                if (hit == null) return@runOnUiThread say(R.string.unreachable)
                Server.save(this, hit, token)
                show(web)
                web.loadUrl("$hit/" + (u.encodedFragment?.let { "#$it" } ?: ""))
            }
        }
    }

    private fun origin(u: Uri) = "${u.scheme}://${u.encodedAuthority}"

    private fun saved(link: String) = Server.token(this).takeIf { link == Server.link(this) }.orEmpty()

    private fun busy(on: Boolean) {
        for (id in listOf(R.id.scan, R.id.go)) findViewById<View>(id).isEnabled = !on
        field.isEnabled = !on
        note.setTextColor(getColor(R.color.muted))
        note.text = if (on) getString(R.string.looking) else ""
    }

    private fun say(msg: Int) {
        note.setTextColor(getColor(R.color.tone_1))
        note.setText(msg)
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
