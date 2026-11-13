package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ProvisioningUiState
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun ProvisioningDialog(
    state: ProvisioningUiState,
    onOpenClone: (String) -> Unit,
    onDone: () -> Unit
) {
    if (state is ProvisioningUiState.Idle) return

    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current

    Dialog(
        onDismissRequest = {
            if (state is ProvisioningUiState.Success || state is ProvisioningUiState.Error) {
                onDone()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is ProvisioningUiState.Running,
            dismissOnClickOutside = state !is ProvisioningUiState.Running
        )
    ) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(28.dp),
            elevated = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surface.copy(alpha = 0.94f), RoundedCornerShape(28.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                when (state) {
                    is ProvisioningUiState.Running -> {
                        val animatedProgress by animateFloatAsState(
                            targetValue = state.progress,
                            animationSpec = tween(durationMillis = 360),
                            label = "clone_progress"
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.8.dp,
                                color = scheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mengonfigurasi ${state.appName}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tahap ${state.currentStepIndex}/${state.totalSteps} • ${state.platform.badge} • ${state.region.code}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.accentTeal
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = glass.accentTeal,
                            trackColor = scheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = state.currentTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.currentDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = scheme.background.copy(alpha = 0.72f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "PROSES PEMASANGAN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.accentTeal
                                )
                                state.logs.takeLast(4).forEach { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    is ProvisioningUiState.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(glass.verifiedEmerald.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Kloning selesai",
                                    tint = glass.verifiedEmerald,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aplikasi Siap Dibuka",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = state.cloneLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPillBadge(
                                text = "${state.platform.badge} • ${state.regionCode}",
                                color = glass.accentTeal
                            )
                            StatusPillBadge(
                                text = "Terenkripsi",
                                color = glass.verifiedEmerald,
                                icon = Icons.Filled.Security
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = scheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Perangkat Virtual: ${state.virtualModel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "ID Perangkat: ${state.virtualAndroidId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDone) {
                                Text("Nanti Saja")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onOpenClone(state.cloneId) },
                                shape = CircleShape,
                                modifier = Modifier.testTag("provisioning_open_button")
                            ) {
                                Text("Buka Sekarang")
                            }
                        }
                    }

                    is ProvisioningUiState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Gagal kloning",
                                tint = scheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Konfigurasi Gagal",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = onDone, shape = CircleShape) {
                                Text("Tutup")
                            }
                        }
                    }

                    ProvisioningUiState.Idle -> Unit
                }
            }
        }
    }
}
