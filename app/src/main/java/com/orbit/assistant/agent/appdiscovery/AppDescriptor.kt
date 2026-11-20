package com.orbit.assistant.agent.appdiscovery

import android.content.Intent
import android.graphics.drawable.Drawable

data class AppDescriptor(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val launchIntent: Intent?,
    val isSupported: Boolean
)
