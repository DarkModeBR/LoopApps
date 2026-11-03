package com.ravager.launcher.status

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads the device once per second, and only between [start] and [stop]. */
class StatusSampler(private val context: Context, private val scope: CoroutineScope) {
    /** Supplies the text of /proc/stat when a normal app is not allowed to read it. */
    var cpuPercentSource: (suspend () -> String?)? = null

    private var job: Job? = null

    fun start(onSample: (StatusSnapshot) -> Unit) {
        stop()
        job = scope.launch {
            var previousCpu: CpuTimes? = null
            while (isActive) {
                val snapshot = withContext(Dispatchers.Default) {
                    val cpu = readProcStat()?.let(StatusParsers::procStat)
                    val percent = if (cpu != null && previousCpu != null) {
                        StatusParsers.cpuPercent(previousCpu!!, cpu)
                    } else {
                        null
                    }
                    previousCpu = cpu ?: previousCpu
                    build(percent)
                }
                onSample(snapshot)
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun readProcStat(): String? =
        readFile(File("/proc/stat"))?.takeIf { StatusParsers.procStat(it) != null } ?: cpuPercentSource?.invoke()

    private fun build(percent: Int?): StatusSnapshot {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val data = StatFs(Environment.getDataDirectory().path)
        return StatusSnapshot(
            cpuPercent = percent,
            tempC = StatusParsers.thermalCelsius(readZones()),
            ramUsed = memory.totalMem - memory.availMem,
            ramTotal = memory.totalMem,
            storageUsed = data.totalBytes - data.availableBytes,
            storageTotal = data.totalBytes,
            uptimeMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun readZones(): List<String> {
        val names = File(THERMAL_ROOT).list().orEmpty().filter {
            it.startsWith("thermal_zone") && it.removePrefix("thermal_zone").toIntOrNull() != null
        }
        return names.mapNotNull { readFile(File(THERMAL_ROOT, "$it/temp")) }
    }

    private fun readFile(file: File): String? = runCatching { file.readText() }.getOrNull()

    private companion object {
        const val INTERVAL_MS = 1000L
        const val THERMAL_ROOT = "/sys/class/thermal"
    }
}
