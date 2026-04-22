package com.mydev.photoslide

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray

class MainActivity : FlutterActivity() {
    private val CH = "com.mydev.photoslide/ch"
    private val PERM = 4001
    private val PICK = 4002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission()
    }

    private fun requestPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(perm), PERM)
    }

    override fun configureFlutterEngine(fe: FlutterEngine) {
        super.configureFlutterEngine(fe)
        val prefs = getSharedPreferences(PhotoWidget.PREFS, Context.MODE_PRIVATE)

        MethodChannel(fe.dartExecutor.binaryMessenger, CH).setMethodCallHandler { call, result ->
            when (call.method) {
                "getAlbums" -> {
                    val albums = JSONArray()
                    try {
                        val cursor = contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media._ID),
                            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
                        )
                        val seen = mutableSetOf<String>()
                        cursor?.use {
                            val bidCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                            val bnCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            while (it.moveToNext()) {
                                val bid = it.getString(bidCol) ?: continue
                                if (!seen.contains(bid)) {
                                    seen.add(bid)
                                    val name = it.getString(bnCol) ?: "Unknown"
                                    val imgId = it.getLong(idCol)
                                    val obj = org.json.JSONObject()
                                    obj.put("id", bid)
                                    obj.put("name", name)
                                    obj.put("thumb", Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgId.toString()).toString())
                                    albums.put(obj)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                    result.success(albums.toString())
                }
                "selectAlbum" -> {
                    val bucketId = call.argument<String>("bucketId") ?: ""
                    val uris = JSONArray()
                    try {
                        val cursor = contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Images.Media._ID),
                            "${MediaStore.Images.Media.BUCKET_ID} = ?",
                            arrayOf(bucketId),
                            "${MediaStore.Images.Media.DATE_ADDED} DESC"
                        )
                        cursor?.use {
                            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            while (it.moveToNext()) {
                                val imgId = it.getLong(idCol)
                                uris.put(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgId.toString()).toString())
                            }
                        }
                    } catch (e: Exception) {}
                    prefs.edit().putString("photos", uris.toString()).putInt("idx", 0).apply()
                    val mgr = AppWidgetManager.getInstance(this)
                    mgr.getAppWidgetIds(ComponentName(this, PhotoWidget::class.java))
                        .forEach { PhotoWidget.update(this, mgr, it) }
                    PhotoWidget.schedule(this)
                    result.success(uris.length())
                }
                "pickPhotos" -> {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(i, PICK)
                    result.success(true)
                }
                "getCount" -> {
                    val photos = try { JSONArray(prefs.getString("photos", "[]")) } catch (e: Exception) { JSONArray() }
                    result.success(photos.length())
                }
                "pinWidget" -> {
                    val mgr = AppWidgetManager.getInstance(this)
                    if (mgr.isRequestPinAppWidgetSupported)
                        mgr.requestPinAppWidget(ComponentName(this, PhotoWidget::class.java), null, null)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == PICK && res == RESULT_OK && data != null) {
            val prefs = getSharedPreferences(PhotoWidget.PREFS, Context.MODE_PRIVATE)
            val uris = JSONArray()
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    val u = clip.getItemAt(i).uri
                    try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                    uris.put(u.toString())
                }
            } else data.data?.let { u ->
                try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                uris.put(u.toString())
            }
            val existing = try { JSONArray(prefs.getString("photos", "[]")) } catch (e: Exception) { JSONArray() }
            for (i in 0 until existing.length()) uris.put(existing.getString(i))
            prefs.edit().putString("photos", uris.toString()).putInt("idx", 0).apply()
            val mgr = AppWidgetManager.getInstance(this)
            mgr.getAppWidgetIds(ComponentName(this, PhotoWidget::class.java))
                .forEach { PhotoWidget.update(this, mgr, it) }
            PhotoWidget.schedule(this)
        }
    }
}
