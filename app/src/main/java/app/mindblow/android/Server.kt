package app.mindblow.android

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class Locked : IOException("token inválido")

/** O servidor do mindblow: o link guardado no aparelho e as rotas que o app chama direto. */
object Server {
    private const val LINK = "link"
    private const val TOKEN = "token"

    private fun prefs(c: Context) = c.getSharedPreferences("mindblow", Context.MODE_PRIVATE)

    fun link(c: Context): String? = prefs(c).getString(LINK, null)

    fun token(c: Context): String = prefs(c).getString(TOKEN, null).orEmpty()

    fun keep(c: Context, token: String) = prefs(c).edit().putString(TOKEN, token).apply()

    /** Troca de servidor esquece o token do anterior, a não ser que venha um novo junto. */
    fun save(c: Context, link: String, token: String?) {
        val same = link == link(c)
        prefs(c).edit()
            .putString(LINK, link)
            .putString(TOKEN, token ?: if (same) token(c) else "")
            .apply()
    }

    /** Responde se o link é um mindblow. 401 conta: a interface pede o token. */
    fun reach(link: String, token: String): Boolean = runCatching {
        val r = open("$link/api/health", "GET", token)
        when (r.responseCode) {
            HttpURLConnection.HTTP_OK -> JSONObject(text(r)).optBoolean("ok")
            HttpURLConnection.HTTP_UNAUTHORIZED -> true
            else -> false
        }
    }.getOrDefault(false)

    fun add(c: Context, draft: JSONObject): JSONObject =
        call(c, "POST", "/api/notes", draft.toString().toByteArray(), "application/json")

    fun attach(c: Context, id: String, p: Pic): JSONObject =
        call(c, "POST", "/api/notes/$id/images?w=${p.w}&h=${p.h}", p.bytes, p.mime)

    private fun call(c: Context, method: String, path: String, body: ByteArray, type: String): JSONObject {
        val link = link(c) ?: throw IOException("sem servidor")
        val r = open(link + path, method, token(c))
        r.doOutput = true
        r.setRequestProperty("Content-Type", type)
        r.setFixedLengthStreamingMode(body.size)
        r.outputStream.use { it.write(body) }
        val code = r.responseCode
        val out = text(r)
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw Locked()
        if (code >= 400) throw IOException(runCatching { JSONObject(out).getString("error") }.getOrDefault("HTTP $code"))
        return JSONObject(out)
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 60_000
            if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
        }

    private fun text(r: HttpURLConnection): String =
        (if (r.responseCode < 400) r.inputStream else r.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
}
