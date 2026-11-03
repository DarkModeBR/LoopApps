package com.loop.launcher.notifications

import com.loop.launcher.privileged.PrivilegedGateway
import com.loop.launcher.privileged.ShizukuState

/** Reads notifications through Shizuku on devices where Android blocks the normal access. */
object DumpsysNotifications {
    /** Keeps only the useful lines, so the answer stays small. */
    val COMMAND = listOf(
        "sh",
        "-c",
        """dumpsys notification --noredact | grep -E 'NotificationRecord\(|^ +flags=|contentIntent=|android\.(title|text)='""",
    )

    private val RECORD = Regex("""NotificationRecord\(\S+ pkg=(\S+) .* key=(\S+?)(?:appImportanceLocked\S*)?: Notification""")
    private val FLAGS = Regex("""^\s*flags=0x([0-9a-fA-F]+)""")
    private val PRESS = Regex("""contentIntent=PendingIntent\{.*PendingIntentRecord\{([0-9a-fA-F]+) """)
    private val TITLE = Regex("""android\.title=\S+ \((.*)\)\s*$""")
    private val TEXT = Regex("""android\.text=\S+ \((.*)\)\s*$""")
    private const val GREP_FOUND_NOTHING = 1
    private const val FLAG_ONGOING = 0x2
    private const val FLAG_NO_CLEAR = 0x20

    private class Draft(val pkg: String, val key: String) {
        var flags = 0
        var press: String? = null
        var title: String? = null
        var text: String? = null
    }

    fun parse(dump: String): List<NotificationItem> {
        val drafts = LinkedHashMap<String, Draft>()
        var current: Draft? = null
        for (line in dump.lineSequence()) {
            val record = RECORD.find(line)
            if (record != null) {
                val key = record.groupValues[2]
                val draft = Draft(record.groupValues[1], key)
                current = if (key in drafts) null else draft.also { drafts[key] = it }
                continue
            }
            val draft = current ?: continue
            FLAGS.find(line)?.let { draft.flags = it.groupValues[1].toIntOrNull(16) ?: 0 }
            PRESS.find(line)?.let { draft.press = it.groupValues[1] }
            TITLE.find(line)?.let { draft.title = it.groupValues[1] }
            TEXT.find(line)?.let { draft.text = it.groupValues[1] }
        }
        val useful = drafts.values.filter { it.title != null || it.text != null }
        return useful.mapIndexed { index, draft ->
            NotificationItem(
                key = draft.key,
                app = draft.pkg,
                text = listOfNotNull(draft.title, draft.text).joinToString(" · "),
                postedAt = (useful.size - index).toLong(),
                ongoing = draft.flags and (FLAG_ONGOING or FLAG_NO_CLEAR) != 0,
                pkg = draft.pkg,
                pressRecord = draft.press,
            )
        }
    }

    /**
     * The current list, empty when Shizuku is stopped so nothing stale stays on screen.
     * Null when this one reading failed and what is on screen should stay.
     */
    suspend fun read(gateway: PrivilegedGateway): List<NotificationItem>? {
        if (gateway.state() != ShizukuState.READY) return emptyList()
        val result = gateway.run(COMMAND) ?: return null
        return when (result.exitCode) {
            0 -> parse(result.output)
            GREP_FOUND_NOTHING -> emptyList()
            else -> null
        }
    }
}
