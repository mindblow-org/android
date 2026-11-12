package app.mindblow.android

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
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

    /** A rede mudou: o sync tenta os links agora, sem esperar a próxima tentativa. */
    fun now(c: Context) {
        call(c, "POST", "/api/sync/now", ByteArray(0), "application/json")
    }

    /** Os links do sync, cada um com o estado e o último sync que deu certo (`at`). */
    fun links(c: Context): JSONArray = JSONArray(send(c, "GET", "/api/links"))

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

    private fun call(c: Context, method: String, path: String, body: ByteArray, type: String): JSONObject =
        send(c, method, path, body, type).let { out -> runCatching { JSONObject(out) }.getOrDefault(JSONObject()) }

    private fun send(c: Context, method: String, path: String, body: ByteArray? = null, type: String? = null): String {
        if (!Node.up(c)) throw IOException("o mindblow não abriu")
        val r = (URL(Node.BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer ${Node.token(c)}")
            // Com doOutput, o HttpURLConnection troca GET por POST.
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", type)
                setFixedLengthStreamingMode(body.size)
            }
        }
        body?.let { b -> r.outputStream.use { it.write(b) } }
        val code = r.responseCode
        val out = (if (code < 400) r.inputStream else r.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code >= 400) throw IOException(runCatching { JSONObject(out).getString("error") }.getOrDefault("HTTP $code"))
        return out
    }
}
