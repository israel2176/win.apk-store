package app.apkstore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** אייקון האפליקציה מקבצי האפליקציה עצמם: עגול, מרובע מעוגל, וחד-צבעי (אנדרואיד 13+) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h170dp-xxhdpi")
class IconTest {
    @get:Rule val rule = createComposeRule()

    /** כמו אנדרואיד: שכבות של 108dp, ורואים רק את 72dp שבמרכז */
    @Composable
    private fun Adaptive(shape: Shape, mono: Boolean = false) {
        // השכבה גדולה פי 1.5 מהאייקון שרואים (108 מול 72), וממורכזת
        val layer = Modifier.requiredSize(162.dp)
        Box(Modifier.size(108.dp).clip(shape), contentAlignment = Alignment.Center) {
            if (mono) {
                Box(layer.background(Color(0xFFDDE3F6)))
                Image(painterResource(R.drawable.ic_launcher_mono), null, layer, contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(Color(0xFF2E3A66)))
            } else {
                Image(painterResource(R.drawable.ic_launcher_bg), null, layer, contentScale = ContentScale.FillBounds)
                Image(painterResource(R.drawable.ic_launcher_fg), null, layer, contentScale = ContentScale.FillBounds)
            }
        }
    }

    @Test fun launcherIcon() {
        rule.setContent {
            Row(
                Modifier.background(Color(0xFFF4F2FB)).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.size(108.dp)) { Adaptive(CircleShape) }
                Box(Modifier.size(108.dp)) { Adaptive(RoundedCornerShape(26.dp)) }
                Box(Modifier.size(108.dp)) { Adaptive(CircleShape, mono = true) }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/launcher-icon.png")
    }
}
