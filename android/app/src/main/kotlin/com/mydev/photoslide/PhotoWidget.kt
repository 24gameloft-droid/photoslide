package com.mydev.photoslide

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray

class PhotoWidget : AppWidgetProvider() {
    companion object {
        const val ACTION = "com.mydev.photoslide.NEXT"
        const val PREFS = "photo_prefs"
        const val INTERVAL = 5 * 60 * 1000L

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val photosJson = prefs.getString("photos", "[]") ?: "[]"
            val idx = prefs.getInt("idx", 0)
            val v = RemoteViews(ctx.packageName, R.layout.widget_layout)

            try {
                val photos = JSONArray(photosJson)
                if (photos.length() > 0) {
                    v.setViewVisibility(R.id.widget_empty, View.GONE)
                    val uri = Uri.parse(photos.getString(idx % photos.length()))
                    val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    if (bmp != null) {
                        v.setImageViewBitmap(R.id.widget_img, bmp)
                    }
                } else {
                    v.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                }
            } catch (e: Exception) {
                v.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            }

            val ni = Intent(ctx, PhotoWidget::class.java).setAction(ACTION)
            val pi = PendingIntent.getBroadcast(ctx, id, ni,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_img, pi)
            v.setOnClickPendingIntent(R.id.widget_empty, pi)
            mgr.updateAppWidget(id, v)
        }

        fun schedule(ctx: Context) {
            val i = Intent(ctx, PhotoWidget::class.java).setAction(ACTION)
            val pi = PendingIntent.getBroadcast(ctx, 9999, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + INTERVAL, INTERVAL, pi)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
        schedule(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION) {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val photos = try { JSONArray(prefs.getString("photos", "[]")) } catch (e: Exception) { JSONArray() }
            if (photos.length() > 0) {
                val cur = prefs.getInt("idx", 0)
                prefs.edit().putInt("idx", (cur + 1) % photos.length()).apply()
            }
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, PhotoWidget::class.java))
                .forEach { update(ctx, mgr, it) }
        }
    }
}
