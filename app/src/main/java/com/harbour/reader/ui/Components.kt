package com.harbour.reader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.harbour.reader.Book
import com.harbour.reader.CoverCache
import com.harbour.reader.LibraryItem

val LocalCovers = staticCompositionLocalOf<CoverCache> { error("No cover cache provided") }

@Composable
fun CoverImage(comic: LibraryItem?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val covers = LocalCovers.current
    val bitmap by produceState<ImageBitmap?>(comic?.let { covers.peek(it) }, comic?.key) {
        if (comic != null && value == null) value = covers.load(comic)
    }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(280), label = "cover")

    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(HarbourColors.SurfaceHigh),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = comic?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha),
            )
        } else if (comic is Book) {
            // Books without a cover image get a simple title card.
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    comic.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = HarbourColors.Text.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comic.author.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        comic.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = HarbourColors.Muted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (comic != null) {
            Text(
                comic.title.take(1).uppercase(),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineMedium,
                color = HarbourColors.Muted.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun ProgressLine(
    fraction: Float,
    modifier: Modifier = Modifier,
    track: Color = Color.White.copy(alpha = 0.12f),
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(HarbourColors.Accent),
        )
    }
}
