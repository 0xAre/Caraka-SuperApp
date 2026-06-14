package com.example.caraka.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.LocalCarakaDimens
import com.example.caraka.ui.theme.TextSecondary

@Composable
fun CarakaScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    Text(text = text, modifier = modifier, style = CarakaTextStyles.screenTitle, color = color)
}

@Composable
fun CarakaScreenSubtitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(text = text, modifier = modifier, style = CarakaTextStyles.screenSubtitle, color = color)
}

@Composable
fun CarakaSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary
) {
    val dimens = LocalCarakaDimens.current
    Text(
        text = text,
        modifier = modifier,
        style = CarakaTextStyles.sectionHeader,
        color = color
    )
}

@Composable
fun CarakaListTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        style = CarakaTextStyles.listTitle,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun CarakaBody(
    text: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        style = if (muted) CarakaTextStyles.listSubtitle else CarakaTextStyles.bodyDefault,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun CarakaTopBarTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        CarakaScreenTitle(title)
        if (!subtitle.isNullOrBlank()) {
            CarakaScreenSubtitle(subtitle)
        }
    }
}
