package app.mindblow.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Uma barra na tela inicial: o logo abre o app e cada ícone cria uma nota daquele tipo. */
class Widget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val v = RemoteViews(c.packageName, R.layout.widget)
        v.setOnClickPendingIntent(R.id.home, go(c, 0, null))
        val kinds = listOf(R.id.sticky to "sticky", R.id.text to "text", R.id.todo to "todo", R.id.board to "board")
        kinds.forEachIndexed { n, (id, kind) -> v.setOnClickPendingIntent(id, go(c, n + 1, kind)) }
        m.updateAppWidget(ids, v)
    }

    private fun go(c: Context, code: Int, kind: String?): PendingIntent {
        val i = Intent(c, Main::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (kind != null) i.putExtra(Main.KIND, kind)
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
