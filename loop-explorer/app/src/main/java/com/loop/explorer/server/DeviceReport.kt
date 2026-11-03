package com.loop.explorer.server

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import com.loop.explorer.files.Places
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the device says about itself, read from several places at once. Cheap boxes often change
 * what the settings show (Android version, memory, storage, brand), but the kernel and the hardware
 * still tell the truth; the checks compare the two and say when something does not match.
 *
 * Texts are keys; the web page shows them in Portuguese or English.
 */
class DeviceReport(private val context: Context) {
    private val props: Map<String, String> by lazy { readProps() }

    fun collect(): JSONObject {
        val cpuUsage = cpuUsage()
        val sections = JSONArray()
            .put(section("device", device()))
            .put(section("cpu", cpu(cpuUsage)))
            .put(section("memory", memory()))
            .put(section("storage", storage()))
            .put(section("display", display()))
        return JSONObject()
            .put("sections", sections)
            .put("checks", checks())
            .put("live", JSONObject().put("cpu", cpuUsage ?: JSONObject.NULL).put("ramFree", memoryInfo().availMem).put("ramTotal", memoryInfo().totalMem).put("uptime", SystemClock.elapsedRealtime()))
    }

    // ---------- Sections

    private fun section(key: String, rows: List<Pair<String, Any?>>) = JSONObject()
        .put("key", key)
        .put("rows", JSONArray(rows.filter { it.second != null && it.second.toString().isNotBlank() }.map { JSONObject().put("key", it.first).put("value", it.second) }))

    private fun device(): List<Pair<String, Any?>> = listOf(
        "brand" to Build.BRAND,
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "device" to Build.DEVICE,
        "board" to Build.BOARD,
        "platform" to props["ro.board.platform"],
        "android_claimed" to Build.VERSION.RELEASE,
        "android_real" to releaseFor(Build.VERSION.SDK_INT)?.let { "$it (API ${Build.VERSION.SDK_INT})" },
        "security_patch" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else null),
        "build" to Build.DISPLAY,
        "kernel" to readFile("/proc/version")?.substringBefore(" (")?.take(80),
        "uptime" to SystemClock.elapsedRealtime(),
    )

    private fun cpu(usage: Int?): List<Pair<String, Any?>> {
        val info = readFile("/proc/cpuinfo").orEmpty()
        val hardware = cpuHardware()
        val model = Regex("""(?m)^(model name|Processor)\s*:\s*(.+)$""").find(info)?.groupValues?.get(2)?.trim()
        val maxFreq = (0 until (possibleCores() ?: Runtime.getRuntime().availableProcessors()))
            .mapNotNull { readFile("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")?.trim()?.toLongOrNull() }
            .maxOrNull()
        return listOf(
            "cpu_hardware" to (hardware ?: props["ro.hardware"]),
            "cpu_model" to model,
            "cpu_cores" to Runtime.getRuntime().availableProcessors(),
            "cpu_max" to maxFreq?.let { "%.2f GHz".format(it / 1_000_000.0) },
            "cpu_abi" to Build.SUPPORTED_ABIS.joinToString(", "),
            "cpu_kernel_arch" to System.getProperty("os.arch"),
            "cpu_usage" to usage?.let { "$it%" },
            "cpu_temp" to temperature()?.let { "$it °C" },
        )
    }

    private fun memoryInfo() = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }

    private fun memory(): List<Pair<String, Any?>> {
        val info = memoryInfo()
        return listOf(
            "ram_total" to info.totalMem,
            "ram_free" to info.availMem,
            "ram_kernel" to kernelMemory(),
            "ram_low" to context.getSystemService(ActivityManager::class.java).isLowRamDevice,
            "swap" to meminfo("SwapTotal")?.takeIf { it > 0 },
        )
    }

    private fun storage(): List<Pair<String, Any?>> {
        val rows = mutableListOf<Pair<String, Any?>>()
        statFs(Environment.getDataDirectory())?.let { (total, free) ->
            rows += "storage_data" to total
            rows += "storage_data_free" to free
        }
        physicalStorage()?.let { rows += "storage_physical" to it }
        Places.storages(context).drop(1).forEach { rows += "storage_usb" to "${it.label}: ${it.free}/${it.total}" }
        return rows
    }

    private fun display(): List<Pair<String, Any?>> {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
        val metrics = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION")
            display.getRealMetrics(it)
        }
        val modes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) display.supportedModes.toList() else emptyList()
        val best = modes.maxByOrNull { it.physicalWidth * it.physicalHeight }
        return listOf(
            "display_ui" to "${metrics.widthPixels} x ${metrics.heightPixels}",
            "display_max" to best?.let { "${it.physicalWidth} x ${it.physicalHeight}" },
            "display_refresh" to "%.0f Hz".format(display.refreshRate),
            "display_density" to metrics.densityDpi,
        )
    }

    // ---------- Checks

    /** Each check says ok, suspect or fake, or nothing when this device does not let it be checked. */
    private fun checks(): JSONArray {
        val list = JSONArray()
        fun add(key: String, status: String, vararg args: Pair<String, Any?>) {
            val values = JSONObject()
            args.forEach { (name, value) -> values.put(name, value ?: JSONObject.NULL) }
            list.put(JSONObject().put("key", key).put("status", status).put("args", values))
        }

        // Android version: the API level cannot be changed without rebuilding the system.
        val real = releaseFor(Build.VERSION.SDK_INT)
        val claimed = Build.VERSION.RELEASE.orEmpty()
        if (real != null) {
            val same = claimed.substringBefore('.') == real.substringBefore('.').removeSuffix("L")
            add("check_android", if (same) "ok" else "fake", "claimed" to claimed, "real" to real, "api" to Build.VERSION.SDK_INT)
        }

        // Memory: the kernel counts the chips that are really there.
        val kernel = kernelMemory()
        val system = memoryInfo().totalMem
        if (kernel != null && kernel > 0) {
            val inflated = system > kernel * 1.15
            // The kernel always keeps part of the memory for itself, so a perfectly round total was typed in.
            val tooRound = kernel % HALF_GB == 0L
            val status = when {
                inflated -> "fake"
                tooRound -> "suspect"
                else -> "ok"
            }
            add("check_ram", status, "claimed" to system, "real" to kernel, "rounded" to roundedRam(kernel))
        }

        // Storage: a data partition bigger than the memory chip itself is impossible.
        val physical = physicalStorage()
        val data = statFs(Environment.getDataDirectory())?.first
        if (physical != null && data != null) {
            add("check_storage", if (data > physical * 1.02) "fake" else "ok", "claimed" to data, "real" to physical)
        }

        // Processor cores: what apps are told against what the kernel lists.
        val possible = possibleCores()
        val reported = Runtime.getRuntime().availableProcessors()
        if (possible != null) {
            add("check_cores", if (reported > possible) "fake" else "ok", "claimed" to reported, "real" to possible)
        }

        // Brand: the system and the vendor partition should agree; edited boxes change only the first.
        val systemBrand = Build.BRAND.orEmpty()
        val vendorBrand = props["ro.product.vendor.brand"] ?: props["ro.product.vendor.manufacturer"]
        val fingerprintBrand = Build.FINGERPRINT.orEmpty().substringBefore('/')
        if (vendorBrand != null || fingerprintBrand.isNotEmpty()) {
            val mismatch = listOfNotNull(vendorBrand, fingerprintBrand.ifEmpty { null }).any { !it.equals(systemBrand, ignoreCase = true) }
            add("check_brand", if (mismatch) "suspect" else "ok", "claimed" to "${Build.MANUFACTURER} ${Build.MODEL}", "vendor" to vendorBrand, "fingerprint" to fingerprintBrand)
        }

        // Chip: a famous chip name in the model, while the platform is a different family.
        val platform = listOf(props["ro.board.platform"], cpuHardware(), props["ro.hardware"])
            .map { it.orEmpty().lowercase() }
            .firstOrNull { name -> CHIP_FAMILIES.keys.any { name.startsWith(it) } }
            ?: props["ro.board.platform"].orEmpty().lowercase()
        val family = CHIP_FAMILIES.entries.firstOrNull { (prefix, _) -> platform.startsWith(prefix) }?.value
        val text = listOf(Build.MODEL, Build.HARDWARE, Build.BOARD, Build.PRODUCT).joinToString(" ").lowercase()
        val mentioned = CHIP_FAMILIES.values.distinct().firstOrNull { name -> name.split('/').any { token -> text.contains(token.lowercase()) } }
        if (family != null) {
            add("check_chip", if (mentioned != null && mentioned != family) "suspect" else "ok", "platform" to platform, "family" to family, "mentioned" to mentioned)
            // Big brands only use some chip makers; a "Google" box with an Allwinner chip was renamed.
            val brand = Build.MANUFACTURER.orEmpty().lowercase()
            val makers = BRAND_CHIPS.entries.firstOrNull { brand.contains(it.key) }?.value
            if (makers != null) {
                val fits = makers.any { family.startsWith(it) }
                add("check_brand_chip", if (fits) "ok" else "suspect", "brand" to Build.MANUFACTURER, "family" to family)
            }
        }

        // Screen: a box sold as 4K or 8K must be able to send that picture.
        val maxHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.supportedModes?.maxOfOrNull { it.physicalHeight }
        } else {
            null
        }
        val claimsHeight = when {
            Regex("""\b8k\b""").containsMatchIn(text) -> 4320
            Regex("""\b4k\b""").containsMatchIn(text) -> 2160
            else -> null
        }
        if (maxHeight != null && claimsHeight != null) {
            add("check_display", if (maxHeight < claimsHeight) "fake" else "ok", "claimed" to claimsHeight, "real" to maxHeight)
        }
        return list
    }

    // ---------- Readings

    private fun cpuHardware(): String? =
        Regex("""(?m)^Hardware\s*:\s*(.+)$""").find(readFile("/proc/cpuinfo").orEmpty())?.groupValues?.get(1)?.trim()

    private fun readFile(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    private fun meminfo(field: String): Long? = readFile("/proc/meminfo")?.lineSequence()
        ?.firstOrNull { it.startsWith("$field:") }
        ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()?.times(1024)

    private fun kernelMemory(): Long? = meminfo("MemTotal")

    /** Memory is sold in round sizes; the kernel keeps a part for itself, so the real size is the next one up. */
    private fun roundedRam(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024 * 1024)
        val size = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0).firstOrNull { gb <= it } ?: gb
        return if (size % 1.0 == 0.0) "${size.toInt()} GB" else "$size GB"
    }

    private fun statFs(dir: File): Pair<Long, Long>? = runCatching { StatFs(dir.path).let { it.totalBytes to it.availableBytes } }.getOrNull()

    /** The size of the biggest memory chip, from the kernel's list of disks. */
    private fun physicalStorage(): Long? = File("/sys/block").listFiles().orEmpty()
        .filter { it.name.startsWith("mmcblk") && !it.name.contains("boot") && !it.name.contains("rpmb") || it.name.startsWith("sd") || it.name.startsWith("nvme") }
        .mapNotNull { readFile("${it.path}/size")?.trim()?.toLongOrNull()?.times(SECTOR) }
        .filter { it > 0 }
        .maxOrNull()

    private fun possibleCores(): Int? {
        val text = readFile("/sys/devices/system/cpu/possible")?.trim() ?: return null
        return text.split(',').sumOf { part ->
            val bounds = part.split('-').mapNotNull { it.toIntOrNull() }
            if (bounds.size == 2) bounds[1] - bounds[0] + 1 else bounds.size
        }.takeIf { it > 0 }
    }

    private fun temperature(): Int? = File("/sys/class/thermal").listFiles().orEmpty()
        .filter { it.name.startsWith("thermal_zone") }
        .mapNotNull { readFile("${it.path}/temp")?.trim()?.toIntOrNull() }
        .map { if (it >= 1000) it / 1000 else it }
        .filter { it in 1..120 }
        .maxOrNull()

    private fun cpuSample(): Pair<Long, Long>? {
        val line = readFile("/proc/stat")?.lineSequence()?.firstOrNull { it.startsWith("cpu ") } ?: return null
        val values = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        return values[3] to values.sum()
    }

    private fun cpuUsage(): Int? {
        val first = cpuSample() ?: return null
        Thread.sleep(CPU_SAMPLE_MS)
        val second = cpuSample() ?: return null
        val total = second.second - first.second
        if (total <= 0) return null
        return (100 * (total - (second.first - first.first)) / total).toInt().coerceIn(0, 100)
    }

    /** All system properties, read with getprop, which any app may run. */
    private fun readProps(): Map<String, String> = runCatching {
        val process = ProcessBuilder("getprop").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        Regex("""^\[(.+?)]: \[(.*)]$""", RegexOption.MULTILINE).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }.getOrDefault(emptyMap())

    companion object {
        private const val SECTOR = 512L
        private const val HALF_GB = 512L * 1024 * 1024

        /** Chip makers that each big brand really uses in its TV devices. */
        private val BRAND_CHIPS = mapOf(
            "google" to listOf("Amlogic", "MediaTek"),
            "xiaomi" to listOf("Amlogic", "MediaTek"),
            "nvidia" to listOf("Tegra"),
            "amazon" to listOf("MediaTek", "Amlogic"),
            "sony" to listOf("MediaTek"),
            "philips" to listOf("MediaTek"),
            "tcl" to listOf("MediaTek", "Amlogic"),
            "samsung" to listOf("Samsung"),
        )
        private const val CPU_SAMPLE_MS = 400L

        /** The Android version that each API level really is. */
        fun releaseFor(api: Int): String? = mapOf(
            21 to "5.0", 22 to "5.1", 23 to "6.0", 24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1", 28 to "9",
            29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16", 37 to "17",
        )[api]

        /** Platform names in the system properties and the chip family they belong to. */
        private val CHIP_FAMILIES = linkedMapOf(
            "sun50iw9" to "Allwinner H313/H616/H618",
            "cupid" to "Allwinner H313/H616/H618",
            "sun50iw10" to "Allwinner A133",
            "sun50iw6" to "Allwinner H6",
            "sun8iw7" to "Allwinner H3",
            "rk3588" to "RK3588",
            "rk3566" to "RK3566",
            "rk3568" to "RK3568",
            "rk3528" to "RK3528",
            "rk3328" to "RK3328",
            "rk3318" to "RK3318",
            "rk3229" to "RK3229",
            "rk30" to "RK3229",
            "gxl" to "Amlogic S905W/S905X",
            "g12a" to "Amlogic S905X2",
            "sm1" to "Amlogic S905X3",
            "sc2" to "Amlogic S905X4",
            "s4" to "Amlogic S905Y4",
            "t7" to "Amlogic A311D2",
            "mt" to "MediaTek",
            "tegra" to "Tegra",
        )
    }
}
