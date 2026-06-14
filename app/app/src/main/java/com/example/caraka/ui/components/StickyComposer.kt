package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Perplexity-style sticky composer — keeps chat input anchored above system nav.
 */
@Composable
fun StickyComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(com.example.caraka.R.string.chat_input_placeholder),
    topContent: @Composable (() -> Unit)? = null
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = modifier
                .imePadding()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            topContent?.invoke()
            ChatInputBar(
                value = value,
                onValueChange = onValueChange,
                onSend = onSend,
                placeholder = placeholder
            )
        }
    }
}
