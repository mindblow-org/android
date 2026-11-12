package app.mindblow.android

import android.annotation.SuppressLint
import android.content.Context
import android.system.Os
import android.util.Base64
import java.security.SecureRandom

/**
 * O mindblow dentro do app: o servidor do desktop compilado para Android
 * (libmindblow.so, do crate `mobile` do mindblow), com o banco no armazenamento do app.
 * Funciona sem rede; o sync com outros aparelhos é ele que faz.
 */
object Node {
    const val PORT = 7341
    const val BASE = "http://127.0.0.1:$PORT"
    private const val TOKEN = "local"

    private external fun start(): Int

    @Volatile
    private var up = false

    /** Sobe o servidor, uma vez por processo. Diz se ele está no ar; se não subiu, tenta de novo na próxima. */
    @Synchronized
    fun up(c: Context): Boolean {
        if (up) return true
        // Porta e banco vão por variável de ambiente, como no serviço do PC. O token
        // fecha a API para os outros apps: 127.0.0.1 é o mesmo para todos no aparelho.
        Os.setenv("MINDBLOW_DB", c.filesDir.resolve("mindblow.db").path, true)
        Os.setenv("MINDBLOW_HOST", "127.0.0.1", true)
        Os.setenv("MINDBLOW_PORT", PORT.toString(), true)
        Os.setenv("MINDBLOW_TOKEN", token(c), true)
        up = runCatching {
            System.loadLibrary("mindblow")
            start() == PORT
        }.getOrDefault(false)
        if (up) Sync.watch(c.applicationContext)
        return up
    }

    /** O token do servidor daqui, criado na primeira vez (gravado na hora, antes de o servidor subir com ele). */
    @SuppressLint("ApplySharedPref")
    fun token(c: Context): String {
        val p = c.getSharedPreferences("mindblow", Context.MODE_PRIVATE)
        p.getString(TOKEN, null)?.let { return it }
        val b = ByteArray(24).also(SecureRandom()::nextBytes)
        val t = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        p.edit().putString(TOKEN, t).commit()
        return t
    }
}
