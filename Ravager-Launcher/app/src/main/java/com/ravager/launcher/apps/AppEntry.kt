package com.ravager.launcher.apps

import com.ravager.launcher.trays.AppCategory

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val hasBanner: Boolean,
    val declaredCategory: AppCategory?,
)
