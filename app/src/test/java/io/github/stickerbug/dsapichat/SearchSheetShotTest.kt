package io.github.stickerbug.dsapichat

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stickerbug.dsapichat.data.SearchSource
import io.github.stickerbug.dsapichat.ui.chat.SearchSheetContent
import io.github.stickerbug.dsapichat.ui.theme.DsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchSheetShotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sources = listOf(
        SearchSource(
            title = "2026 年国产大模型全景盘点：从开源到落地",
            url = "https://www.jiqizhixin.com/articles/2026-01-08-3",
            siteName = "机器之心",
            snippet = "过去一年国产大模型在多模态与长上下文上进展明显，推理成本继续下探。",
            pageAge = "2026/01/08"
        ),
        SearchSource(
            title = "国产大模型 2026 开年观察",
            url = "https://36kr.com/p/3000000000000",
            siteName = "36氪",
            snippet = "推理成本继续下降，端侧部署成为新战场。",
            pageAge = "2026/01/05"
        ),
        SearchSource(
            title = "如何评价 2026 年的国产大模型",
            url = "https://www.zhihu.com/question/000000000",
            siteName = "知乎",
            snippet = "讨论集中在代码能力与工具调用稳定性。"
        )
    )

    @Test
    fun shot() {
        compose.setContent {
            DsTheme(dark = false) {
                SearchSheetContent(sources = sources, onDismiss = {}, onOpen = {})
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val width = if (view.width > 0) view.width else 1233
        val height = if (view.height > 0) view.height else 2823
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
        val out = File("build/sheet.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
