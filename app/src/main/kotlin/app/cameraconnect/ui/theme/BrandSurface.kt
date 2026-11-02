package app.cameraconnect.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import app.cameraconnect.R
import kotlin.random.Random

// One deterministic, transparent tile for the process. No per-frame random allocation.
private val grainTile: Bitmap by lazy {
    val random = Random(73)
    val pixels = IntArray(128 * 128) {
        // Dark-only noise preserves the minimum contrast of light/orange text on graphite.
        random.nextInt(0, 13) shl 24
    }
    Bitmap.createBitmap(pixels, 128, 128, Bitmap.Config.ARGB_8888)
}

/** Paints material underneath content, never over photographs, glyphs or text. */
fun Modifier.precisionGrain(base: Color = Charcoal): Modifier = drawWithCache {
    val paint = Paint().apply {
        asFrameworkPaint().shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    onDrawBehind {
        drawRect(base)
        drawIntoCanvas { it.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = modifier)
}

@Composable
fun PrecisionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = Graphite) {
        Column(modifier = Modifier.precisionGrain(Graphite), content = content)
    }
}
