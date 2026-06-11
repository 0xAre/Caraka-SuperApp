package com.example.caraka.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.caraka.ui.theme.LocalCarakaDimens
import com.example.caraka.ui.components.CarakaGlassSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.graphics.Color
import com.example.caraka.ui.theme.DotGothicFamily

@Composable
fun CarakaStatTile(
    modifier: Modifier = Modifier,
    nodeCount: Int,
    coverageKm: Float,
    alarmCount: Int,
    forwardedCount: Int
) {
    CarakaGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(value = "$nodeCount", label = "Node")
            VerticalDivider()
            StatItem(value = "${coverageKm}km", label = "Jangkauan")
            VerticalDivider()
            StatItem(
                value = "$alarmCount", 
                label = "Alarm", 
                color = if (alarmCount > 0) com.example.caraka.ui.theme.IosSystemRed else Color.White
            )
            VerticalDivider()
            StatItem(value = "$forwardedCount", label = "Diteruskan")
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(Color(0x15FFFFFF))
    )
}

@Composable
private fun StatItem(value: String, label: String, color: Color = Color.White) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Text(
            text = value,
            fontFamily = DotGothicFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8899AA)
        )
    }
}
