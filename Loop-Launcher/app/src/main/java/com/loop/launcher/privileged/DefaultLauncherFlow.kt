package com.loop.launcher.privileged

interface HomeSystem {
    fun isDefaultHome(): Boolean
    fun roleAvailable(): Boolean
    fun requestHomeRole()
    fun openHomeSettings(): Boolean
    fun openDefaultAppsSettings(): Boolean
    fun openHomeChooser(): Boolean
    fun hasStockGoogleTvLauncher(): Boolean
}

interface HomeFlowPrefs {
    var autoHomeAttemptDone: Boolean
}

enum class AutoResult { ALREADY_DEFAULT, APPLIED_WITH_SHIZUKU, ROLE_REQUESTED, NOTHING_TO_TRY, ALREADY_TRIED }
enum class ButtonResult { ALREADY_DEFAULT, SYSTEM_SCREEN_OPENED, SHOW_ALTERNATIVES }
enum class ReturnResult { NOW_DEFAULT, SHOW_ALTERNATIVES }

/** What the help screen needs from any permission it helps with. */
interface AccessFlow {
    fun onButtonPressed(): ButtonResult
    fun onNothingHappened(): ButtonResult
    fun onReturnedFromSystem(): ReturnResult
    suspend fun applyWithShizuku(): Boolean
}

/** Order set by the project owner: try alone, then the system screen, then Shizuku or ADB. */
class DefaultLauncherFlow(
    private val system: HomeSystem,
    private val gateway: PrivilegedGateway,
    private val prefs: HomeFlowPrefs,
) : AccessFlow {
    private val screens: List<() -> Boolean> = listOf(
        ::openRoleDialog,
        system::openHomeSettings,
        system::openDefaultAppsSettings,
        system::openHomeChooser,
    )
    private var nextScreen = 0

    /** Runs once per install. Android does not let a normal app do this without a confirmation. */
    suspend fun onAppOpened(): AutoResult {
        if (system.isDefaultHome()) return AutoResult.ALREADY_DEFAULT
        if (prefs.autoHomeAttemptDone) return AutoResult.ALREADY_TRIED
        prefs.autoHomeAttemptDone = true
        if (applyWithShizuku()) return AutoResult.APPLIED_WITH_SHIZUKU
        if (!system.roleAvailable()) return AutoResult.NOTHING_TO_TRY
        system.requestHomeRole()
        return AutoResult.ROLE_REQUESTED
    }

    override fun onButtonPressed(): ButtonResult {
        if (system.isDefaultHome()) return ButtonResult.ALREADY_DEFAULT
        nextScreen = 0
        return openNextScreen()
    }

    /** Some devices report that a screen opened and show nothing, so the caller checks and tells us. */
    override fun onNothingHappened(): ButtonResult = openNextScreen()

    private fun openRoleDialog(): Boolean {
        if (!system.roleAvailable()) return false
        system.requestHomeRole()
        return true
    }

    private fun openNextScreen(): ButtonResult {
        while (nextScreen < screens.size) {
            if (screens[nextScreen++]()) return ButtonResult.SYSTEM_SCREEN_OPENED
        }
        return ButtonResult.SHOW_ALTERNATIVES
    }

    override fun onReturnedFromSystem(): ReturnResult =
        if (system.isDefaultHome()) ReturnResult.NOW_DEFAULT else ReturnResult.SHOW_ALTERNATIVES

    /** Success means the launcher really became the default, not just that the command ran. */
    override suspend fun applyWithShizuku(): Boolean {
        if (gateway.state() != ShizukuState.READY) return false
        gateway.run(AdbCommands.setHome.argv)
        return system.isDefaultHome()
    }

    /** Only ever called after the user confirms the warning on the help screen. */
    suspend fun disableGoogleTvLauncher(): Boolean {
        if (gateway.state() != ShizukuState.READY) return false
        gateway.run(AdbCommands.disableGoogleTvLauncher.argv)
        return system.isDefaultHome()
    }
}
