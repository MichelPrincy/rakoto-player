package com.rakotoplayer.android

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded, on-demand artwork. Private videos never enter the public image cache. */
internal class MediaArtLoader(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val worker =
        ThreadPoolExecutor(
            2,
            2,
            15,
            TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable>(24),
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )
    private val cache =
        object :
            LruCache<String, Bitmap>(
                if (
                    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                        .isLowRamDevice
                )
                    2 * 1024 * 1024
                else 8 * 1024 * 1024
            ) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
    @Volatile private var closed = false

    fun load(view: ImageView, entry: MediaEntry, size: Int = 160) {
        val pixels = size.coerceIn(48, 640)
        val key = "${entry.uri}|${entry.modifiedAt}|$pixels"
        view.tag = key
        view.setImageDrawable(CoverDrawable(entry.title, entry.video, entry.isPrivate))
        if (entry.isPrivate || closed) return
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        worker.execute {
            if (closed || view.tag != key) return@execute
            val image = decode(entry, pixels) ?: return@execute
            if (closed) {
                image.recycle()
                return@execute
            }
            cache.put(key, image)
            handler.post { if (!closed && view.tag == key) view.setImageBitmap(image) }
        }
    }

    private fun decode(entry: MediaEntry, size: Int): Bitmap? {
        val uri = Uri.parse(entry.uri)
        if (Build.VERSION.SDK_INT >= 29 && entry.video) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } catch (_: Exception) {}
        }
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                retriever.setDataSource(fd.fileDescriptor)
                if (entry.video) {
                    val frame =
                        if (Build.VERSION.SDK_INT >= 27)
                            retriever.getScaledFrameAtTime(
                                0,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                size,
                                size,
                            )
                        else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    frame ?: return null
                    val scale = size.toFloat() / maxOf(frame.width, frame.height)
                    if (scale >= 1f) return frame
                    val result =
                        Bitmap.createScaledBitmap(
                            frame,
                            maxOf(1, (frame.width * scale).toInt()),
                            maxOf(1, (frame.height * scale).toInt()),
                            true,
                        )
                    if (result !== frame) frame.recycle()
                    return result
                }
                val bytes = retriever.embeddedPicture ?: return null
                if (bytes.size > 16 * 1024 * 1024) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > size * 2) sample *= 2
                return BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    },
                )
            }
        } catch (_: Exception) {} finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        return null
    }

    fun close() {
        closed = true
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        cache.evictAll()
    }
}
