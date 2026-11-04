package org.koocheh.ui.disclosure

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.koocheh.security.PhotoSanitizer

/**
 * Blurred until Tier 3. At Tier 3 (both people confirmed) the clear photo is
 * available, and only if the viewer taps to show it. [sanitizedPhoto] may be null.
 */
@Composable
fun PhotoRevealView(
    sanitizedPhoto: ByteArray?,
    tier: Int,
    modifier: Modifier = Modifier,
) {
    if (sanitizedPhoto == null) return
    var showClear by remember(sanitizedPhoto) { mutableStateOf(false) }
    val bothConfirmed = tier >= 3
    val shown = if (bothConfirmed && showClear) sanitizedPhoto else PhotoSanitizer.blurred(sanitizedPhoto)
    val bitmap = remember(shown) { BitmapFactory.decodeByteArray(shown, 0, shown.size)?.asImageBitmap() }

    Column(modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
        }
        if (bothConfirmed) {
            TextButton(onClick = { showClear = !showClear }) {
                Text(if (showClear) "Blur photo again" else "Show clear photo")
            }
        } else {
            Text(
                "The photo stays blurred until you both confirm the last step.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
