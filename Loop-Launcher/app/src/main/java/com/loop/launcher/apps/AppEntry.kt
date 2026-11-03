package com.loop.launcher.apps

import com.loop.launcher.trays.AppCategory

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val hasBanner: Boolean,
    val declaredCategory: AppCategory?,
)
