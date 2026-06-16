package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Perplexity-style sticky composer. Owns NO window insets — the parent Column in
 * ChatScreen is the single inset authority (IME + navigation bars). Do not add
 * imePadding()/navigationBarsPadding() here or the keyboard gap regression returns.
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
        Column(modifier = modifier) {
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
