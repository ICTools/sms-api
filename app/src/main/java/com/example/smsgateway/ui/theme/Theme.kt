package com.example.smsgateway.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColors = darkColorScheme()

@Composable
fun SmsGatewayTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content,
    )
}

