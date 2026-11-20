package io.github.stickerbug.dsapichat

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stickerbug.dsapichat.data.Attachment
import io.github.stickerbug.dsapichat.data.Citation
import io.github.stickerbug.dsapichat.data.Msg
import io.github.stickerbug.dsapichat.data.Role
import io.github.stickerbug.dsapichat.data.SearchSource
import io.github.stickerbug.dsapichat.ui.chat.MessageItem
import io.github.stickerbug.dsapichat.ui.theme.DsTheme
import java.io.File
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class ChatGalleryShotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun initLatex() {
        runCatching { ru.noties.jlatexmath.JLatexMathAndroid.init(ApplicationProvider.getApplicationContext()) }
    }

    @Test
    fun shot() {
        val sources = listOf(
            SearchSource(
                title = "国产大模型 2026 开年观察",
                url = "https://36kr.com/p/1",
                siteName = "36氪",
                snippet = "推理成本继续下降，端侧部署成为新战场。",
                pageAge = "2026/01/05"
            ),
            SearchSource(
                title = "如何评价 2026 年的国产大模型",
                url = "https://zhihu.com/q/2",
                siteName = "知乎",
                siteIcon = "https://example.invalid/favicon.png",
                snippet = "讨论集中在代码能力与工具调用稳定性。",
                pageAge = "2026/01/03"
            )
        )
        val imgFile = File(compose.activity.cacheDir, "attach").apply { mkdirs() }.resolve("test_chart.png")
        runCatching {
            val bmp = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(android.graphics.Color.rgb(236, 240, 252))
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(77, 107, 254)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 9f
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val path = android.graphics.Path().apply {
                moveTo(24f, 205f)
                lineTo(115f, 130f)
                lineTo(205f, 162f)
                lineTo(336f, 42f)
            }
            canvas.drawPath(path, paint)
            imgFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val user = Msg(
            id = "u1",
            role = Role.USER,
            text = "梯度下降是什么？",
            attachments = listOf(
                Attachment(
                    name = "chart.png",
                    mime = "image/png",
                    fileId = "file_1",
                    image = true,
                    size = 120000,
                    localPath = imgFile.absolutePath
                ),
                Attachment(name = "notes.pdf", mime = "application/pdf", fileId = "file_2", image = false, size = 88000)
            )
        )
        val bot = Msg(
            id = "b1",
            role = Role.ASSISTANT,
            reasoning = "用户问梯度下降。先给定义，再给公式与代码。",
            thinkMs = 3200,
            text = "梯度下降是一种沿负梯度方向迭代逼近极小值的优化方法。\n\n$$\\theta_{t+1} = \\theta_t - \\eta \\nabla J(\\theta_t)$$\n\n学习率 $\\eta$ 决定步长，需要适中。\n\n1. 计算梯度\n2. 更新参数\n\n```python\ndef step(theta, grad, lr=0.1):\n    return theta - lr * grad\n```\n\n| 方法 | 收敛速度 | 数据量 |\n| --- | :---: | ---: |\n| 批量梯度下降 | 慢 | 小 |\n| 随机梯度下降 | 快 | 大 |",
            searchQuery = "梯度下降",
            sources = sources,
            citations = listOf(
                Citation(text = "需要适中", url = "https://36kr.com/p/1", index = 1),
                Citation(text = "更新参数", url = "https://zhihu.com/q/2", index = 2)
            )
        )
        compose.setContent {
            DsTheme(dark = false) {
                Column(Modifier.fillMaxSize().background(Color.White)) {
                    MessageItem(user, false, {}, {})
                    MessageItem(bot, false, {}, {})
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(600)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File("build/chat_gallery.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
