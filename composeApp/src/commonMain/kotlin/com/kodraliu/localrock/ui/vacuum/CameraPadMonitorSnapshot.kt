package com.kodraliu.localrock.ui.vacuum

internal enum class CameraPadButton {
    A, B, X, Y, LB, RB, LT, RT, LEFT_STICK, RIGHT_STICK, BACK, START, UP, DOWN, LEFT, RIGHT
}

/** Display-only values. No reference to a motion controller or robot transport. */
internal data class CameraPadMonitorSnapshot(
    val available: Boolean = false,
    val deviceName: String? = null,
    val leftX: Double = 0.0, val leftY: Double = 0.0,
    val rightX: Double = 0.0, val rightY: Double = 0.0,
    val leftTrigger: Double = 0.0, val rightTrigger: Double = 0.0,
    val buttons: Set<CameraPadButton> = emptySet(),
    val hat: Set<CameraPadButton> = emptySet(),
) {
    val pressed: Set<CameraPadButton> get() = buttons + hat
    fun effective(layout: CameraPadLayout): CameraPadInput? =
        cameraPadInput(layout, leftX, leftY, leftTrigger, rightTrigger)

    fun withButton(button: CameraPadButton, down: Boolean) =
        copy(buttons = if (down) buttons + button else buttons - button)

    fun cleared() = CameraPadMonitorSnapshot(available = available)
}
