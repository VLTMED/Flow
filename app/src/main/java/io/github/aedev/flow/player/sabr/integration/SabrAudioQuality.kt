package io.github.aedev.flow.player.sabr.integration

import io.github.aedev.flow.innertube.models.response.PlayerResponse

internal fun SabrStreamInfo.withAudioQuality(format: PlayerResponse.StreamingData.Format?): SabrStreamInfo {
    if (format == null || !format.isAudio) return this
    return copy(
        audioItag = format.itag,
        audioLmt = format.lastModified ?: 0L,
        audioTrackId = format.audioTrack?.id.orEmpty(),
        audioXtags = format.xtags.orEmpty(),
        audioMimeType = format.mimeType,
    )
}
