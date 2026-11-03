package com.loop.explorer.privileged

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.loop.explorer.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

class ShizukuGateway(private val context: Context) : PrivilegedGateway {
    private val lock = Mutex()
    private var service: IPrivilegedService? = null
    private var connection: ServiceConnection? = null

    private val args = Shizuku.UserServiceArgs(ComponentName(context, PrivilegedService::class.java))
        .daemon(false)
        .processNameSuffix("privileged")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    /** Shizuku connects shortly after the app starts, so readings wait for this signal. */
    fun onConnected(listener: Shizuku.OnBinderReceivedListener) = Shizuku.addBinderReceivedListenerSticky(listener)

    fun removeOnConnected(listener: Shizuku.OnBinderReceivedListener) = Shizuku.removeBinderReceivedListener(listener)

    override fun state(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> if (isInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
            Shizuku.isPreV11() -> ShizukuState.UNSUPPORTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.NEEDS_PERMISSION
        }
    } catch (_: RuntimeException) {
        ShizukuState.NOT_RUNNING
    }

    private fun isInstalled() = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
    }.isSuccess

    override suspend fun requestPermission(): Boolean = withTimeoutOrNull(PERMISSION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (continuation.isActive) continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            try {
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(REQUEST_CODE)
            } catch (_: RuntimeException) {
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resume(false)
            }
        }
    } ?: false

    override suspend fun run(argv: List<String>): CommandResult? {
        if (state() != ShizukuState.READY) return null
        return lock.withLock {
            val bound = service ?: bind() ?: return@withLock null
            withContext(Dispatchers.IO) {
                try {
                    parse(bound.exec(argv.toTypedArray()))
                } catch (_: Exception) {
                    service = null
                    null
                }
            }
        }
    }

    private fun parse(answer: String?): CommandResult? {
        val text = answer ?: return null
        val code = text.substringBefore('\n').toIntOrNull() ?: return null
        return CommandResult(code, text.substringAfter('\n', ""))
    }

    private suspend fun bind(): IPrivilegedService? = withTimeoutOrNull(BIND_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                    val bound = binder?.takeIf { it.pingBinder() }?.let(IPrivilegedService.Stub::asInterface)
                    service = bound
                    if (continuation.isActive) continuation.resume(bound)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    service = null
                }
            }
            connection = listener
            try {
                Shizuku.bindUserService(args, listener)
            } catch (_: RuntimeException) {
                continuation.resume(null)
            }
        }
    }

    override fun release() {
        if (lock.isLocked) return // Another command is using the helper; its caller releases it.
        val current = connection ?: return
        connection = null
        service = null
        runCatching { Shizuku.unbindUserService(args, current, true) }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4021
        const val BIND_TIMEOUT_MS = 15_000L
        const val PERMISSION_TIMEOUT_MS = 60_000L
    }
}
