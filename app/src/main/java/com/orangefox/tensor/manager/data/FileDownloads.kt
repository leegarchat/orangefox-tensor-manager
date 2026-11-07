package com.orangefox.tensor.manager.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal fun copyFileToPublicDownloads(context: Context, sourceFile: File, displayFileName: String): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayFileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("MediaStore URI null")
        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(sourceFile).use { ins ->
                ins.copyTo(out, bufferSize = 256 * 1024)
            }
        } ?: error("Cannot open output stream")
        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        return "Downloads/$displayFileName"
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .apply { mkdirs() }
        val target = File(downloadsDir, displayFileName)
        FileInputStream(sourceFile).use { ins ->
            FileOutputStream(target).use { out ->
                ins.copyTo(out, bufferSize = 256 * 1024)
            }
        }
        return target.absolutePath
    }
}
