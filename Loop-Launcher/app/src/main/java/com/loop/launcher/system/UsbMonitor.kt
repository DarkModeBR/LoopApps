package com.loop.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.storage.StorageManager
import com.loop.launcher.R

class UsbMonitor(private val context: Context) {
    private var receiver: BroadcastReceiver? = null

    /** Names of what is plugged in; a pen drive appears once, by its volume name. */
    fun devices(): List<String> {
        val volumes = removableVolumes()
        val usb = context.getSystemService(UsbManager::class.java)
        val others = runCatching { usb.deviceList.values.toList() }.getOrDefault(emptyList())
            .filter { it.deviceClass != UsbConstants.USB_CLASS_HUB }
            .filterNot { volumes.isNotEmpty() && isStorage(it) }
            .map { it.productName?.takeIf(String::isNotBlank) ?: context.getString(R.string.usb_device) }
        return volumes + others
    }

    fun start(onChange: (List<String>) -> Unit) {
        stop()
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange(devices())
        }
        receiver = listener
        context.registerReceiver(
            listener,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
        )
        context.registerReceiver(
            listener,
            IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addDataScheme("file")
            },
        )
        onChange(devices())
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private fun removableVolumes(): List<String> {
        val storage = context.getSystemService(StorageManager::class.java)
        return runCatching {
            storage.storageVolumes
                .filter { it.isRemovable && it.state == android.os.Environment.MEDIA_MOUNTED }
                .map { it.getDescription(context) }
        }.getOrDefault(emptyList())
    }

    private fun isStorage(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE }
}
