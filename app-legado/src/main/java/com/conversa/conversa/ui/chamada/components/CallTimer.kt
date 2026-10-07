package com.conversa.conversa.ui.chamada.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun CallTimer(
    timerText: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF94A3B8)
) {
    Text(
        text = timerText,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = color,
        modifier = modifier
    )
}
