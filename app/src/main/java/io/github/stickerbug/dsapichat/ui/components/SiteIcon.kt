package io.github.stickerbug.dsapichat.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stickerbug.dsapichat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 站点 favicon 的内存缓存（LRU，最多 32 个站点）。 */
private object SiteIconCache {
    private val cache = object : LruCache<String, Bitmap>(32) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, value: Bitmap) {
        cache.put(key, value)
    }
}

private fun downloadIcon(url: String): Bitmap? = runCatching {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 4000
        readTimeout = 4000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
    }
    val bitmap = conn.inputStream.use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 2 })
    }
    conn.disconnect()
    bitmap
}.getOrNull()

/** 圆形站点图标：有 site_icon 就加载，否则回退到链接图标。 */
@Composable
fun SiteIcon(
    siteIcon: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    placeholder: Int = R.drawable.ic_link_outline_20,
    background: Color = Color(0xFFF2F2F2),
    tint: Color = Color(0xFF9A9A9A)
) {
    val key = siteIcon?.trim()?.takeIf { it.startsWith("http") }
    var bitmap by remember(key) { mutableStateOf(key?.let { SiteIconCache.get(it) }) }

    LaunchedEffect(key) {
        val url = key ?: return@LaunchedEffect
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) { downloadIcon(url) }
            if (loaded != null) {
                SiteIconCache.put(url, loaded)
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(placeholder),
                contentDescription = null,
                modifier = Modifier.size(size * 0.61f),
                tint = tint
            )
        }
    }
}
