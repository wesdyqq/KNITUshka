package com.qq.knitushka.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

// Native swiping keeps the pager's gesture physics. Deliberate button navigation uses
// a short, interruptible transition; Compose honours the system animation-duration scale.
internal val CalendarPageAnimation = tween<Float>(
    durationMillis = 250,
    easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f),
)
