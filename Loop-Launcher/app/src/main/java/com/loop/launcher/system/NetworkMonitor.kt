package com.loop.launcher.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

class NetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null

    val state: ConnectionState
        get() {
            val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
                ?: return ConnectionState(NetworkKind.NONE)
            return ConnectionState(
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
                    else -> NetworkKind.OTHER
                },
            )
        }

    fun start(onChange: (ConnectionState) -> Unit) {
        stop()
        val update = Runnable { onChange(state) }
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = schedule(update)
            override fun onLost(network: Network) = schedule(update)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = schedule(update)
        }
        callback = listener
        runCatching { manager.registerNetworkCallback(NetworkRequest.Builder().build(), listener) }
        onChange(state)
    }

    fun stop() {
        callback?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        callback = null
        main.removeCallbacksAndMessages(null)
    }

    /** Several callbacks arrive for one change, so they are merged into one update. */
    private fun schedule(update: Runnable) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed(update, 300)
    }
}
