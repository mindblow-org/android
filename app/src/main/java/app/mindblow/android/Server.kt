package app.mindblow.android

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** As rotas do servidor daqui (o `Node`) que o app chama direto. */
object Server {
    private const val LINK = "link"
    private const val TOKEN = "token"

    fun add(c: Context, draft: JSONObject): JSONObject =
        call(c, "POST", "/api/notes", draft.toString().toByteArray(), "application/json")

    fun attach(c: Context, id: String, p: Pic): JSONObject =
        call(c, "POST", "/api/notes/$id/images?w=${p.w}&h=${p.h}", p.bytes, p.mime)

    /** Passa a sincronizar com outro mindblow (o link do QR dele, com ou sem `#token=`). */
    fun link(c: Context, url: String) {
        call(c, "POST", "/api/links", JSONObject().put("url", url).toString().toByteArray(), "application/json")
    }

    /**
     * Antes o app só abria o mindblow do PC, pelo link guardado aqui. Na primeira vez com o
     * servidor junto, esse link vira o sync com o PC, e as notas de lá descem para cá.
     */
    fun adopt(c: Context) {
        val p = c.getSharedPreferences("mindblow", Context.MODE_PRIVATE)
        val link = p.getString(LINK, null) ?: return
        val token = p.getString(TOKEN, null).orEmpty()
        val url = if (token.isEmpty()) link else "$link/#token=${Uri.encode(token)}"
        runCatching { link(c, url) }.onSuccess { p.edit().remove(LINK).remove(TOKEN).apply() }
    }

    private fun call(c: Context, method: String, path: String, body: ByteArray, type: String): JSONObject {
        if (!Node.up(c)) throw IOException("o mindblow não abriu")
        val r = (URL(Node.BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${Node.token(c)}")
            setRequestProperty("Content-Type", type)
            setFixedLengthStreamingMode(body.size)
        }
        r.outputStream.use { it.write(body) }
        val code = r.responseCode
        val out = (if (code < 400) r.inputStream else r.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code >= 400) throw IOException(runCatching { JSONObject(out).getString("error") }.getOrDefault("HTTP $code"))
        return runCatching { JSONObject(out) }.getOrDefault(JSONObject())
    }
}
