package io.github.stickerbug.dsapichat

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.noties.jlatexmath.JLatexMathDrawable

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class LatexProbeTest {
    @Test
    fun probe() {
        val out = StringBuilder()
        out.append("class=").append(JLatexMathDrawable::class.java.name).append('\n')
        try {
            ru.noties.jlatexmath.JLatexMathAndroid.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            out.append("init ok\n")
        } catch (e: Throwable) {
            out.append("init fail ").append(e.javaClass.name).append(": ").append(e.message).append('\n')
        }
        try {
            val d = JLatexMathDrawable.builder("\\eta x^2").textSize(48f).color(0xFF000000.toInt()).background(0).build()
            out.append("ok w=").append(d.intrinsicWidth).append(" h=").append(d.intrinsicHeight).append('\n')
        } catch (e: Throwable) {
            out.append("fail ").append(e.javaClass.name).append(": ").append(e.message).append('\n')
        }
        File("build/latex_probe.txt").writeText(out.toString())
    }
}
