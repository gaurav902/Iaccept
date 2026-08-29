package com.tellmeindia.iaccept.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellmeindia.iaccept.ui.theme.*

@Composable
fun PremiumCard(title: String, containerColor: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, if (MaterialTheme.colorScheme.surface == NeonSurface) White10 else LiteBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = NeonBlue, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}
