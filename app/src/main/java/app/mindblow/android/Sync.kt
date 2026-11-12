package app.mindblow.android

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkRequest
import kotlin.concurrent.thread
import org.json.JSONArray

/**
 * O que foi escrito sem rede sobe sozinho quando ela volta, mesmo com o app fechado: o
 * sistema roda este job quando há rede, e ele acorda o sync e espera cada link ficar em dia.
 */
class Sync : JobService() {
    companion object {
        private const val ID = 1
        private const val WAIT = 90_000L
        /** Estados em que tentar de novo não adianta (ver `sync::State` no servidor). */
        private val STUCK = setOf("self", "locked", "old")
        /**
         * Rede de verdade embaixo. A VPN do Tailscale continua de pé e "validada" no modo avião,
         * só sem transporte; com sinal, ela carrega o do Wi-Fi ou do celular junto.
         */
        private val REAL = intArrayOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR, NetworkCapabilities.TRANSPORT_ETHERNET)

        /** Agenda o job para quando houver rede; o que já estava agendado é trocado. */
        fun plan(c: Context) {
            val net = NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .apply { REAL.forEach(::addTransportType) }
                .build()
            val job = JobInfo.Builder(ID, ComponentName(c, Sync::class.java))
                .setRequiredNetwork(net)
                .setPersisted(true)
                .build()
            c.getSystemService(JobScheduler::class.java).schedule(job)
        }

        /** Enquanto o processo vive, a rede voltando (ou trocando de Wi-Fi para celular) acorda o sync na hora. */
        fun watch(c: Context) {
            var last: Network? = null
            var real = false
            c.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, nc: NetworkCapabilities) {
                    val up = nc.hasCapability(NET_CAPABILITY_VALIDATED) && REAL.any(nc::hasTransport)
                    if (up && last != null && (!real || n != last)) thread { runCatching { Server.now(c) } }
                    last = n
                    real = up
                }

                override fun onLost(n: Network) {
                    real = false
                }
            })
        }
    }

    override fun onStartJob(p: JobParameters): Boolean {
        thread { jobFinished(p, !run()) }
        return true
    }

    override fun onStopJob(p: JobParameters) = true

    /** Diz se todos os links sincronizaram depois que o job começou; se não, o sistema tenta de novo mais tarde. */
    private fun run(): Boolean = runCatching {
        val start = System.currentTimeMillis()
        if (Server.links(this).length() == 0) return true
        Server.now(this)
        while (System.currentTimeMillis() - start < WAIT) {
            if (done(Server.links(this), start)) return true
            Thread.sleep(1_000)
        }
        false
    }.getOrDefault(false)

    private fun done(links: JSONArray, since: Long) = (0 until links.length()).map(links::getJSONObject).all {
        val s = it.optString("state")
        s in STUCK || (s == "ok" && it.optLong("at") >= since)
    }
}
