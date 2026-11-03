package com.loop.explorer.files

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.loop.explorer.R
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What is running now, for the bar at the bottom of the screen. */
data class JobState(
    val title: String,
    val done: Long = 0,
    val total: Long = 0,
    val current: String = "",
    val finished: Boolean = false,
    /** Null on success or after a cancel; the message to show otherwise. */
    val error: String? = null,
    val cancelled: Boolean = false,
    /** Folders whose contents changed, to be shown again. */
    val touched: Set<File> = emptySet(),
)

/** One file operation at a time, kept outside the screens so it survives the menus closing. */
object Jobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(JobState?) -> Unit>()
    private var job: Job? = null
    private var lastReport = 0L

    @Volatile
    var state: JobState? = null
        private set

    val running: Boolean get() = job?.isActive == true

    fun addListener(listener: (JobState?) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (JobState?) -> Unit) {
        listeners -= listener
    }

    private fun publish(next: JobState?) {
        state = next
        main.post { listeners.forEach { it(next) } }
    }

    /** False when another operation is still running. */
    fun start(context: Context, title: String, touched: Set<File>, work: suspend (Operations) -> Unit): Boolean {
        if (running) return false
        val app = context.applicationContext
        publish(JobState(title, touched = touched))
        job = scope.launch {
            val operations = Operations(app) { done, total, current ->
                val now = System.currentTimeMillis()
                // The screen does not need more than a few updates per second.
                if (now - lastReport >= REPORT_MS || done == total) {
                    lastReport = now
                    publish(JobState(title, done, total, current, touched = touched))
                }
            }
            val end = try {
                work(operations)
                JobState(title, finished = true, touched = touched)
            } catch (_: CancellationException) {
                JobState(title, finished = true, cancelled = true, touched = touched)
            } catch (error: OperationError) {
                JobState(title, finished = true, error = message(app, error.reason), touched = touched)
            } catch (_: Throwable) {
                JobState(title, finished = true, error = message(app, OperationError.Reason.FAILED), touched = touched)
            }
            publish(end)
        }
        return true
    }

    fun cancel() {
        job?.cancel()
    }

    /** Clears the finished state once the screen has shown it. */
    fun acknowledge() {
        if (state?.finished == true) state = null
    }

    fun message(context: Context, reason: OperationError.Reason): String = context.getString(
        when (reason) {
            OperationError.Reason.NO_ACCESS -> R.string.error_no_access
            OperationError.Reason.NO_SPACE -> R.string.error_no_space
            OperationError.Reason.INTO_ITSELF -> R.string.error_into_itself
            OperationError.Reason.NOT_ZIP -> R.string.error_not_zip
            OperationError.Reason.FAILED -> R.string.error_failed
        },
    )

    private const val REPORT_MS = 200L
}

/** Files waiting to be pasted. [cut] moves them instead of copying. */
object Clipboard {
    var files: List<File> = emptyList()
        private set
    var cut = false
        private set

    fun set(newFiles: List<File>, isCut: Boolean) {
        files = newFiles
        cut = isCut
    }

    fun clear() {
        files = emptyList()
        cut = false
    }

    val isEmpty: Boolean get() = files.none { it.exists() }
}
