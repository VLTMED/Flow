package io.github.aedev.flow.ui.components.videoplayer.gesture

import kotlin.math.ceil

/**
 * The speed a press-and-hold on the video applies and the horizontal steps available while it is held.
 */
internal object PlayerSpeedBoost {
    const val MIN_BOOST_SPEED = 0.1f
    const val MAX_BOOST_SPEED = 4.0f
    const val HORIZONTAL_DRAG_STEP = 0.5f
    const val MAX_HORIZONTAL_DRAG_STEPS = 4

    fun longPressSpeed(configuredSpeed: Float): Float = configuredSpeed.coerceIn(MIN_BOOST_SPEED, MAX_BOOST_SPEED)

    fun speedAtDragStep(
        baseSpeed: Float,
        stepOffset: Int,
    ): Float {
        val base = longPressSpeed(baseSpeed)
        val steps = stepOffset.coerceIn(-MAX_HORIZONTAL_DRAG_STEPS, MAX_HORIZONTAL_DRAG_STEPS)
        return (base + steps * HORIZONTAL_DRAG_STEP).coerceIn(MIN_BOOST_SPEED, MAX_BOOST_SPEED)
    }
}

/** Accumulates horizontal travel into deliberate, thresholded speed steps while a long press is held. */
internal class PlayerSpeedBoostDragController(
    baseSpeed: Float,
    private val stepDistancePx: Float,
    private val onSpeedChanged: (Float) -> Unit,
) {
    private val baseSpeed = PlayerSpeedBoost.longPressSpeed(baseSpeed)
    private val maxStepsUp =
        minOf(
            PlayerSpeedBoost.MAX_HORIZONTAL_DRAG_STEPS,
            ceil((PlayerSpeedBoost.MAX_BOOST_SPEED - this.baseSpeed) / PlayerSpeedBoost.HORIZONTAL_DRAG_STEP).toInt(),
        )
    private val maxStepsDown =
        minOf(
            PlayerSpeedBoost.MAX_HORIZONTAL_DRAG_STEPS,
            ceil((this.baseSpeed - PlayerSpeedBoost.MIN_BOOST_SPEED) / PlayerSpeedBoost.HORIZONTAL_DRAG_STEP).toInt(),
        )

    private var accumulatedHorizontalPx = 0f
    private var stepOffset = 0

    var speed: Float = this.baseSpeed
        private set

    init {
        require(stepDistancePx > 0f && stepDistancePx.isFinite())
        require(baseSpeed.isFinite())
    }

    fun dragBy(deltaX: Float) {
        if (!deltaX.isFinite() || deltaX == 0f) return
        accumulatedHorizontalPx += deltaX

        while (accumulatedHorizontalPx >= stepDistancePx) {
            if (stepOffset >= maxStepsUp) {
                accumulatedHorizontalPx = 0f
                return
            }
            accumulatedHorizontalPx -= stepDistancePx
            stepOffset++
            publishSpeed()
        }

        while (accumulatedHorizontalPx <= -stepDistancePx) {
            if (stepOffset <= -maxStepsDown) {
                accumulatedHorizontalPx = 0f
                return
            }
            accumulatedHorizontalPx += stepDistancePx
            stepOffset--
            publishSpeed()
        }
    }

    private fun publishSpeed() {
        val nextSpeed = PlayerSpeedBoost.speedAtDragStep(baseSpeed, stepOffset)
        if (nextSpeed != speed) {
            speed = nextSpeed
            onSpeedChanged(nextSpeed)
        }
    }
}

/** A held boost owns horizontal movement; ordinary drags retain the existing seek behavior. */
internal fun shouldAcceptHorizontalSeekDrag(
    isSpeedBoostActive: Boolean,
    seekSwipeGesturesEnabled: Boolean,
    durationMs: Long,
): Boolean = !isSpeedBoostActive && seekSwipeGesturesEnabled && durationMs > 0L
