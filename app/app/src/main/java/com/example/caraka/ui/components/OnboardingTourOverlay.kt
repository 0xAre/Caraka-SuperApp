package com.example.caraka.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.NavyBackground
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

private data class TourStep(val titleRes: Int, val descRes: Int)

private val tourSteps = listOf(
    TourStep(R.string.tour_step1_title, R.string.tour_step1_desc),
    TourStep(R.string.tour_step2_title, R.string.tour_step2_desc),
    TourStep(R.string.tour_step3_title, R.string.tour_step3_desc),
    TourStep(R.string.tour_step4_title, R.string.tour_step4_desc),
    TourStep(R.string.tour_step5_title, R.string.tour_step5_desc)
)

/**
 * First-run coach-mark overlay that walks the user through 5 key UI concepts.
 * Displayed once after profile setup and revisitable from the Help screen.
 * Implements Nielsen heuristic #6 (Recognition rather than recall) and #10
 * (Help & Documentation).
 */
@Composable
fun OnboardingTourOverlay(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    var stepIndex by remember { mutableStateOf(0) }
    val current = tourSteps.getOrNull(stepIndex)

    AnimatedVisibility(
        visible = visible && current != null,
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        exit = fadeOut() + scaleOut(targetScale = 0.95f)
    ) {
        if (current == null) return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyBackground.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = AmberAccent, spotColor = SurfaceDark)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassSurface)
                    .border(1.dp, AmberAccent.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(AmberAccent.copy(alpha = 0.15f))
                        .border(2.dp, AmberAccent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))

                // Step counter dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tourSteps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == stepIndex) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == stepIndex) AmberAccent else TextSecondary.copy(alpha = 0.4f))
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(current.titleRes),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(current.descRes),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onDismiss() }) {
                        Text(
                            stringResource(R.string.tour_skip),
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = {
                            if (stepIndex < tourSteps.lastIndex) stepIndex++
                            else onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            if (stepIndex < tourSteps.lastIndex) stringResource(R.string.tour_next)
                            else stringResource(R.string.tour_done),
                            color = NavyBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = NavyBackground,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
