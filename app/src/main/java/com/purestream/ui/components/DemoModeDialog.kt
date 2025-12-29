package com.purestream.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DemoModePlaybackBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Preview,
                contentDescription = null,
                tint = Color(0xFFE5A00D)
            )
        },
        title = {
            Text(
                text = "Demo Mode Active",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Playback is not available in Demo Mode.",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "Connect to a real Plex server to watch content.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE5A00D),
                    contentColor = Color.Black
                )
            ) {
                Text("OK", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color(0xFF2A2A2A),
        tonalElevation = 8.dp
    )
}
