package app.mindblow.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.IOException
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * "Compartilhar" de outros apps. Texto curto vira sticky; com assunto (o título da
 * página, vindo do navegador), mais de 500 caracteres ou várias imagens, vira texto.
 * Salva no servidor daqui, então funciona sem rede, e abre a nota no app.
 */
class Share : Activity() {
    companion object {
        private const val STICKY = 500
        private const val IMAGES = 50
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val pics = pics()
        if (text.isEmpty() && pics.isEmpty()) {
            toast(getString(R.string.empty))
            return finish()
        }
        toast(getString(R.string.saving))
        thread {
            val r = runCatching { save(subject, text, pics) }
            runOnUiThread {
                r.onSuccess { id ->
                    startActivity(Intent(this, Main::class.java).putExtra(Main.NOTE, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { e ->
                    toast(getString(R.string.failed, e.message))
                }
                finish()
            }
        }
    }

    /** O sistema costuma copiar as imagens para o clipData, já com a permissão de leitura; se não, vale o EXTRA_STREAM. */
    private fun pics(): List<Uri> {
        val clip = intent.clipData
        val all = clip?.let { c -> (0 until c.itemCount).mapNotNull { c.getItemAt(it).uri } }.orEmpty().ifEmpty { streams() }
        return all.filter { contentResolver.getType(it)?.startsWith("image/") == true }.take(IMAGES)
    }

    @Suppress("DEPRECATION")
    private fun streams(): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }

    private fun save(subject: String, text: String, pics: List<Uri>): String {
        val sticky = subject.isEmpty() && pics.size <= 1 && text.codePointCount(0, text.length) <= STICKY
        val draft = JSONObject().put("kind", if (sticky) "sticky" else "text").put("body", text)
        if (!sticky) draft.put("title", subject)
        val id = Server.add(this, draft).getString("id")
        for (u in pics) {
            val p = Pic.read(this, u) ?: throw IOException("imagem ilegível")
            Server.attach(this, id, p)
        }
        return id
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
}
