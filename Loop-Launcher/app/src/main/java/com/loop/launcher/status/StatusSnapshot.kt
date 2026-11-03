package com.loop.launcher.status

/** A null value means the device does not allow that reading, so it is not shown. */
data class StatusSnapshot(
    val cpuPercent: Int?,
    val tempC: Int?,
    val ramUsed: Long,
    val ramTotal: Long,
    val storageUsed: Long,
    val storageTotal: Long,
    val uptimeMs: Long,
)
