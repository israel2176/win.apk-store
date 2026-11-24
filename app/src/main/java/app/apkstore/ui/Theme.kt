package app.apkstore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * שפה עיצובית: מבנה מוכר כמו בחנות של גוגל (שורות אופקיות, דף אפליקציה, תוצאה ראשונה מודגשת),
 * אבל צבע אינדיגו במקום ירוק, אזורים בכרטיסים צבעוניים במקום רקע לבן שטוח,
 * ואייקונים מעוגלים יותר - כדי שיהיה ברור שזו לא החנות של גוגל.
 */

private val Light = lightColorScheme(
    primary = Color(0xFF3B4FD8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFFB45309),
    tertiaryContainer = Color(0xFFFDF1E3),
    onTertiaryContainer = Color(0xFF7A3A05),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF8C1D18),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F2FB),
    surfaceContainer = Color(0xFFEEECF6),
    surfaceContainerHigh = Color(0xFFE8E6F1),
    surfaceContainerHighest = Color(0xFFE2E0EB),
    outlineVariant = Color(0xFFC7C5D0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF0B1F8F),
    primaryContainer = Color(0xFF2A3AB7),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFFFB870),
    tertiaryContainer = Color(0xFF5A2D05),
    onTertiaryContainer = Color(0xFFFFDCC0),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34343B),
    outlineVariant = Color(0xFF46464F),
)

@Composable
fun StoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

// ---- גודל חלון: קובע את הפריסה ----

enum class WidthClass { Compact, Medium, Expanded }

fun widthClassOf(width: Dp) = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    else -> WidthClass.Expanded
}

data class WindowInfo(val width: WidthClass, val widthDp: Dp, val heightDp: Dp) {
    val compact get() = width == WidthClass.Compact
    val landscape get() = widthDp > heightDp
    /** דף אפליקציה בשני טורים - בטאבלט, ובטלפון לרוחב */
    val twoPane get() = width == WidthClass.Expanded || (width == WidthClass.Medium && landscape)
    /** גובה צילומי מסך לפי גובה החלון */
    val shotHeight get() = (heightDp * 0.42f).coerceIn(150.dp, 320.dp)
    val tileSize get() = if (compact) 76.dp else 92.dp
}

val LocalWindow = compositionLocalOf { WindowInfo(WidthClass.Compact, 400.dp, 800.dp) }
