package com.example.caraka.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.caraka.ui.components.CarakaBody
import com.example.caraka.ui.components.CarakaCard
import com.example.caraka.ui.components.CarakaGlassSurface
import com.example.caraka.ui.components.CarakaListTitle
import com.example.caraka.ui.theme.CarakaTextStyles
import com.example.caraka.ui.theme.CoralError
import com.example.caraka.ui.theme.TealPrimary
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextMuted

@Composable
fun ConnectionRequestDialog(
    peerId: String,
    peerName: String,
    peerRole: String,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        CarakaCard(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            hasSubtleBorder = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Permintaan Koneksi Masuk",
                    style = CarakaTextStyles.dialogTitle,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(24.dp))

                CarakaGlassSurface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        tint = TealPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                CarakaListTitle(peerName)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkWifi,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = peerRole,
                        style = CarakaTextStyles.listSubtitle,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                CarakaBody(
                    text = "Terima permintaan koneksi dari perangkat ini?",
                    muted = true,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = {
                            onReject(peerId)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CoralError
                        )
                    ) {
                        Text("TOLAK", style = CarakaTextStyles.buttonLabel)
                    }

                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = {
                            onAccept(peerId)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("TERIMA", style = CarakaTextStyles.buttonLabel)
                    }
                }
            }
        }
    }
}
