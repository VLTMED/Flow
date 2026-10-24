package io.github.aedev.flow.ui.components.videoplayer.gesture

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.github.aedev.flow.player.EnhancedPlayerManager

private val LongPressBottomExclusion = 40.dp
private val LongPressBottomExclusionFullscreen = 28.dp

internal fun Modifier.playerTapGestures(
    isSpeedBoostActive: State<Boolean>,
    onSpeedBoostChange: State<(Boolean) -> Unit>,
    onSpeedBoostSpeedChange: State<(Float) -> Unit>,
    showControls: State<Boolean>,
    onShowControlsChange: State<(Boolean) -> Unit>,
    onShowSeekBackChange: State<(Boolean) -> Unit>,
    onShowSeekForwardChange: State<(Boolean) -> Unit>,
    onSeekAccumulate: State<(Int) -> Unit>,
    currentPosition: State<() -> Long>,
    duration: State<Long>,
    onNormalSpeedChange: State<(Float) -> Unit>,
    isFullscreen: State<Boolean>,
    doubleTapSeekMs: State<Long>,
    seekZoneFraction: State<Float>,
    seekZoneReachesEdges: State<Boolean>,
    longPressPlaybackSpeed: State<Float>,
    isSeekForwardActive: State<Boolean>,
    isSeekBackActive: State<Boolean>,
    hideCenterTransportControls: State<Boolean>,
    sideEdgeIgnorePx: State<Float>,
    onTapWindowChange: State<(Boolean) -> Unit>,
    haptics: HapticFeedback,
): Modifier {
    val currentIsSpeedBoostActive by isSpeedBoostActive
    val currentOnSpeedBoostChange by onSpeedBoostChange
    val currentOnSpeedBoostSpeedChange by onSpeedBoostSpeedChange
    val currentShowControls by showControls
    val currentOnShowControlsChange by onShowControlsChange
    val currentOnShowSeekBackChange by onShowSeekBackChange
    val currentOnShowSeekForwardChange by onShowSeekForwardChange
    val currentOnSeekAccumulate by onSeekAccumulate
    val currentPositionProvider by currentPosition
    val currentDuration by duration
    val currentOnNormalSpeedChange by onNormalSpeedChange
    val currentIsFullscreen by isFullscreen
    val currentDoubleTapSeekMs by doubleTapSeekMs
    val currentSeekZoneFraction by seekZoneFraction
    val currentSeekZoneReachesEdges by seekZoneReachesEdges
    val currentLongPressPlaybackSpeed by longPressPlaybackSpeed
    val currentIsSeekForwardActive by isSeekForwardActive
    val currentIsSeekBackActive by isSeekBackActive
    val currentHideCenterTransportControls by hideCenterTransportControls
    val currentSideEdgeIgnorePx by sideEdgeIgnorePx
    val currentOnTapWindowChange by onTapWindowChange

    return this.pointerInput(Unit) {
        var accumulatedForwardMs = 0L
        var accumulatedBackMs = 0L
        var lastForwardTapTime = 0L
        var lastBackTapTime = 0L
        var pendingForwardTargetMs: Long? = null
        var pendingBackTargetMs: Long? = null
        var speedBeforeLongPress: Float? = null
        var speedBoostDragController: PlayerSpeedBoostDragController? = null

        var revealedOnTap = false
        var hidePending = false
        var centerTapPending = false

        fun zoneOf(x: Float): TapZone =
            tapZoneOf(
                x = x,
                width = size.width.toFloat(),
                sideFraction = if (currentDoubleTapSeekMs > 0L) currentSeekZoneFraction else 0f,
                edgePx = tapEdgePx(currentSeekZoneReachesEdges, currentSideEdgeIgnorePx),
            )

        fun applyZoneSeek(forward: Boolean) {
            val manager = EnhancedPlayerManager.getInstance()
            val player = manager.getPlayer()
            val isLive = manager.playerState.value.isLive || player?.isCurrentMediaItemLive == true
            val playerPosition = player?.currentPosition ?: currentPositionProvider()
            val step = currentDoubleTapSeekMs
            val now = SystemClock.uptimeMillis()

            val target =
                if (forward) {
                    currentOnShowSeekBackChange(false)
                    accumulatedBackMs = 0L
                    lastBackTapTime = 0L
                    pendingBackTargetMs = null

                    val continuing = now - lastForwardTapTime < DOUBLE_TAP_SEEK_WINDOW_MS
                    accumulatedForwardMs = if (continuing) accumulatedForwardMs + step else step
                    lastForwardTapTime = now
                    val base = pendingForwardTargetMs?.takeIf { continuing } ?: playerPosition
                    (base + step).coerceAtMost(currentDuration).also {
                        pendingForwardTargetMs = it
                        currentOnSeekAccumulate((accumulatedForwardMs / 1000L).toInt())
                        currentOnShowSeekForwardChange(true)
                    }
                } else {
                    currentOnShowSeekForwardChange(false)
                    accumulatedForwardMs = 0L
                    lastForwardTapTime = 0L
                    pendingForwardTargetMs = null

                    val continuing = now - lastBackTapTime < DOUBLE_TAP_SEEK_WINDOW_MS
                    accumulatedBackMs = if (continuing) accumulatedBackMs + step else step
                    lastBackTapTime = now
                    val base = pendingBackTargetMs?.takeIf { continuing } ?: playerPosition
                    (base - step).coerceAtLeast(0L).also {
                        pendingBackTargetMs = it
                        currentOnSeekAccumulate(-(accumulatedBackMs / 1000L).toInt())
                        currentOnShowSeekBackChange(true)
                    }
                }

            if (isLive) manager.seekToLiveTimeline(target) else manager.seekTo(target)
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }

        fun togglePlayPause() {
            val manager = EnhancedPlayerManager.getInstance()
            val player = manager.getPlayer() ?: return
            when {
                player.playbackState == Player.STATE_ENDED -> manager.replay()
                player.isPlaying -> manager.pause()
                else -> manager.play()
            }
        }

        detectPlayerTaps(
            isEdge = { offset -> zoneOf(offset.x) == TapZone.EDGE },
            isNearEdge = { offset -> offset.x < currentSideEdgeIgnorePx || offset.x > size.width - currentSideEdgeIgnorePx },
            onTapWindowChange = { open -> currentOnTapWindowChange(open) },
            onTapUp = { offset ->
                if (!currentIsSpeedBoostActive) {
                    val zone = zoneOf(offset.x)
                    centerTapPending = false
                    val continuesActiveSeek =
                        (zone == TapZone.BACK && currentIsSeekBackActive) ||
                            (zone == TapZone.FORWARD && currentIsSeekForwardActive)
                    when {
                        continuesActiveSeek -> {
                            applyZoneSeek(forward = zone == TapZone.FORWARD)
                        }

                        zone == TapZone.CENTER && currentHideCenterTransportControls -> {
                            centerTapPending = true
                            hidePending = currentShowControls
                            revealedOnTap = false
                        }

                        !currentShowControls -> {
                            currentOnShowControlsChange(true)
                            revealedOnTap = true
                            hidePending = false
                        }

                        else -> {
                            hidePending = true
                            revealedOnTap = false
                        }
                    }
                }
            },
            onSingleTapConfirmed = {
                if (centerTapPending) {
                    togglePlayPause()
                    if (hidePending) currentOnShowControlsChange(false)
                } else if (hidePending) {
                    currentOnShowControlsChange(false)
                }
                centerTapPending = false
                hidePending = false
                revealedOnTap = false
            },
            onDoubleTap = { offset ->
                centerTapPending = false
                hidePending = false
                val zone = zoneOf(offset.x)
                if (zone != TapZone.CENTER && revealedOnTap) {
                    currentOnShowControlsChange(false)
                }
                revealedOnTap = false

                when (zone) {
                    TapZone.BACK -> applyZoneSeek(forward = false)
                    TapZone.FORWARD -> applyZoneSeek(forward = true)
                    TapZone.CENTER -> togglePlayPause()
                    TapZone.EDGE -> Unit
                }
            },
            onLongPress = { offset ->
                val bottomExclusion = if (currentIsFullscreen) LongPressBottomExclusionFullscreen else LongPressBottomExclusion
                if (currentLongPressPlaybackSpeed <= 0f || offset.y > size.height - bottomExclusion.toPx()) {
                    false
                } else {
                    val manager = EnhancedPlayerManager.getInstance()
                    val player = manager.getPlayer()
                    if (player != null && !currentIsSpeedBoostActive) {
                        val restoreSpeed =
                            manager.playerState.value.playbackSpeed
                                .takeIf { it > 0f }
                                ?: player.playbackParameters.speed
                        val boostSpeed = PlayerSpeedBoost.longPressSpeed(currentLongPressPlaybackSpeed)
                        speedBeforeLongPress = restoreSpeed
                        speedBoostDragController =
                            PlayerSpeedBoostDragController(
                                baseSpeed = boostSpeed,
                                stepDistancePx = with(this) { 48.dp.toPx() },
                            ) { speed ->
                                manager.setPlaybackSpeed(speed)
                                currentOnSpeedBoostSpeedChange(speed)
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        currentOnNormalSpeedChange(restoreSpeed)
                        currentOnSpeedBoostSpeedChange(boostSpeed)
                        currentOnSpeedBoostChange(true)
                        manager.setPlaybackSpeed(boostSpeed)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        true
                    } else {
                        false
                    }
                }
            },
            onLongPressDrag = { deltaX -> speedBoostDragController?.dragBy(deltaX) },
            onLongPressReleased = {
                val restoreSpeed = speedBeforeLongPress
                if (restoreSpeed != null) {
                    EnhancedPlayerManager.getInstance().setPlaybackSpeed(restoreSpeed)
                    currentOnNormalSpeedChange(restoreSpeed)
                    currentOnSpeedBoostSpeedChange(restoreSpeed)
                    speedBeforeLongPress = null
                    speedBoostDragController = null
                    currentOnSpeedBoostChange(false)
                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
            },
        )
    }
}

/**
 * Tap detection that reports the up event before the double-tap window has resolved.
 *
 * `detectTapGestures` withholds its tap callback until the double-tap timeout expires whenever an
 * `onDoubleTap` is supplied. The player needs the opposite ordering: [onTapUp] runs the instant the
 * finger lifts, so the controls reveal and a running seek accumulation continues without the wait,
 * while [onSingleTapConfirmed] still fires only once the window closes with no second touch.
 *
 * A touch that starts on [isEdge] may belong to the system back gesture, whose down reaches the app
 * before the system cancels it: such a touch never opens the double-tap window and never counts as a
 * second tap. The double tap fires on the second up, which a cancel never is. [isNearEdge] is the
 * same strip whatever the zone setting: a slow back swipe can outlast the long-press timeout before
 * the system takes it, so a touch starting there never long-presses even where taps reach the edge.
 *
 * [onTapWindowChange] reports the double-tap window, so the controls the first tap revealed can
 * leave the second tap to this detector (#1308). It is closed in a finally: the modifier is removed
 * mid-gesture on minimise, PiP and touch lock, and a window left open would lock the controls.
 */
private suspend fun PointerInputScope.detectPlayerTaps(
    isEdge: (Offset) -> Boolean,
    isNearEdge: (Offset) -> Boolean,
    onTapWindowChange: (Boolean) -> Unit,
    onTapUp: (Offset) -> Unit,
    onSingleTapConfirmed: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Boolean,
    onLongPressDrag: (Float) -> Unit,
    onLongPressReleased: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startsAtEdge = isEdge(down.position)

        val firstUp =
            if (startsAtEdge || isNearEdge(down.position)) {
                awaitStillUp(down)
            } else {
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) { awaitStillUp(down) }
                } catch (_: PointerEventTimeoutCancellationException) {
                    val speedBoostStarted = onLongPress(down.position)
                    if (speedBoostStarted) {
                        awaitLongPressSpeedDrag(down.id, onLongPressDrag)
                    } else {
                        waitForUpOrCancellation()
                    }
                    onLongPressReleased()
                    return@awaitEachGesture
                }
            } ?: return@awaitEachGesture

        onTapUp(firstUp.position)
        if (startsAtEdge) {
            onSingleTapConfirmed()
            return@awaitEachGesture
        }

        onTapWindowChange(true)
        val secondUp =
            try {
                val secondDown =
                    withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                secondDown?.takeUnless { isEdge(it.position) }?.let { awaitStillUp(it) }
            } finally {
                onTapWindowChange(false)
            }
        if (secondUp == null) {
            onSingleTapConfirmed()
            return@awaitEachGesture
        }

        onDoubleTap(secondUp.position)
    }
}

/** While the held boost is active, horizontal movement is consumed here rather than becoming a seek. */
private suspend fun AwaitPointerEventScope.awaitLongPressSpeedDrag(
    pointerId: PointerId,
    onHorizontalDrag: (Float) -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        if (change.changedToUp() || !change.pressed) return

        val deltaX = change.positionChange().x
        if (deltaX != 0f) onHorizontalDrag(deltaX)
        change.consume()
    }
}

/** The up of [down], or null once it moves past touch slop, leaves the bounds or is consumed. */
private suspend fun AwaitPointerEventScope.awaitStillUp(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
        if (change.changedToUp()) return change
        if (change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding)) return null
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return null
    }
}
