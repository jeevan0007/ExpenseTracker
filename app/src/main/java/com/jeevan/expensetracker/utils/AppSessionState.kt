package com.jeevan.expensetracker.utils

/**
 * Holds process-lifetime session state that needs to survive
 * Activity recreation but not app restarts.
 *
 * Previously declared as mutable companion object vars in MainActivity,
 * which is effectively a global variable — accessible from anywhere,
 * with no encapsulation. Moved here so the state is named and scoped.
 *
 * All three vars are only read/written by MainActivity.
 */
object AppSessionState {

    /** True once the user has authenticated via biometric/PIN this session. */
    var isSessionUnlocked: Boolean = false

    /**
     * Timestamp (ms) when MainActivity moved to the background.
     * Used to expire the unlock session after 3 seconds in the background.
     * Reset to 0L when MainActivity returns to the foreground.
     */
    var backgroundedTime: Long = 0L

    /**
     * True when MainActivity starts another internal Activity (Charts, Trips etc.).
     * Prevents the 3-second background timer from firing during internal navigation,
     * which would lock the app every time the user opens a sub-screen.
     */
    var isNavigatingInternally: Boolean = false
}