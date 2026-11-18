package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.components.library.LibraryAlbumCard
import io.github.aedev.flow.ui.components.library.LibraryEmptyShelf
import io.github.aedev.flow.ui.components.library.LibraryMediaItem
import io.github.aedev.flow.ui.components.library.LibraryMediaShelf
import io.github.aedev.flow.ui.components.library.LibrarySection
import io.github.aedev.flow.ui.components.library.LibraryShelf
import io.github.aedev.flow.ui.components.library.LibraryShelfPlaceholder
import io.github.aedev.flow.ui.components.library.LibraryShortsShelf
import io.github.aedev.flow.ui.components.library.LibraryVideoCard
import io.github.aedev.flow.ui.components.library.PlaylistActionsMenu
import io.github.aedev.flow.ui.components.music.sheet.LocalMusicMenus
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionActionItem
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun LibraryMediaShelfRoute(
    section: LibrarySection,
    itemsFlow: StateFlow<List<LibraryMediaItem>?>,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDownloadedMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    emptyMessage: String? = null,
    onRemoveVideo: ((Video) -> Unit)? = null,
) {
    val items by itemsFlow.collectAsStateWithLifecycle()
    val title = section.title
    when {
        items == null -> {
            LibraryShelfPlaceholder(title = title, icon = section.icon)
        }

        items.isNullOrEmpty() && emptyMessage != null -> {
            LibraryEmptyShelf(title = title, icon = section.icon, message = emptyMessage, onTitleClick = onTitleClick)
        }

        items.isNullOrEmpty() -> {
            Unit
        }

        else -> {
            val removeVideoLabel =
                if (section == LibrarySection.HISTORY) {
                    stringResource(R.string.remove_from_history)
                } else {
                    null
                }
            LibraryMediaShelf(
                title = title,
                icon = section.icon,
                items = items.orEmpty(),
                sourceName = title,
                onTitleClick = onTitleClick,
                onVideoClick = onVideoClick,
                onRemoveVideo = onRemoveVideo,
                removeVideoLabel = removeVideoLabel,
                onMusicClick = onMusicClick,
                onDownloadedVideoClick = onDownloadedVideoClick,
                onDownloadedMusicClick = onDownloadedMusicClick,
            )
        }
    }
}

@Composable
internal fun LibraryPlaylistsShelf(
    section: LibrarySection,
    videoPlaylistsFlow: StateFlow<List<PlaylistInfo>?>?,
    musicPlaylistsFlow: StateFlow<List<PlaylistInfo>?>?,
    ownedMusicPlaylistIdsFlow: StateFlow<Set<String>?>,
    onTitleClick: () -> Unit,
    onVideoPlaylistClick: (String) -> Unit,
    onMusicPlaylistClick: (String) -> Unit,
    onDeletePlaylist: ((PlaylistInfo) -> Unit)? = null,
    onRenameMusicPlaylist: ((PlaylistInfo) -> Unit)? = null,
) {
    val musicMenus = LocalMusicMenus.current
    val videoPlaylists = videoPlaylistsFlow?.collectAsStateWithLifecycle()?.value
    val musicPlaylists = musicPlaylistsFlow?.collectAsStateWithLifecycle()?.value
    val ownedMusicPlaylistIds by ownedMusicPlaylistIdsFlow.collectAsStateWithLifecycle()
    val title = section.title

    if (videoPlaylists == null && musicPlaylists == null) {
        LibraryShelfPlaceholder(title = title, icon = section.icon)
        return
    }
    if (videoPlaylists.isNullOrEmpty() && musicPlaylists.isNullOrEmpty()) return

    LibraryShelf(
        title = title,
        icon = section.icon,
        onTitleClick = onTitleClick,
    ) { cardWidth ->
        items(
            items = videoPlaylists.orEmpty(),
            key = { "video-${it.id}" },
            contentType = { "video-playlist" },
        ) { playlist ->
            var showActions by remember(playlist.id) { mutableStateOf(false) }
            Box(modifier = Modifier.width(cardWidth)) {
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onVideoPlaylistClick(playlist.id) },
                    onLongClick = { showActions = true },
                    layout = PlaylistCardLayout.SHELF,
                    modifier = Modifier.fillMaxWidth(),
                )
                PlaylistActionsMenu(
                    expanded = showActions,
                    onDismiss = { showActions = false },
                    onDownload = null,
                    onRename = null,
                    onDelete = onDeletePlaylist?.let { delete -> { delete(playlist) } },
                    onOpen = { onVideoPlaylistClick(playlist.id) },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        items(
            items = musicPlaylists.orEmpty(),
            key = { "music-${it.id}" },
            contentType = { "music-playlist" },
        ) { playlist ->
            val artworkSize = cardWidth * 9f / 16f
            val isOwnedPlaylist = playlist.id in ownedMusicPlaylistIds.orEmpty()
            var showActions by remember(playlist.id) { mutableStateOf(false) }
            Box(modifier = Modifier.width(artworkSize)) {
                LibraryAlbumCard(
                    title = playlist.name,
                    subtitle = stringResource(R.string.tracks_count_template, playlist.videoCount),
                    thumbnailUrl = playlist.thumbnailUrl,
                    onClick = { onMusicPlaylistClick(playlist.id) },
                    artworkSize = artworkSize,
                    onLongClick =
                        if (ownedMusicPlaylistIds == null) {
                            null
                        } else if (isOwnedPlaylist) {
                            { showActions = true }
                        } else {
                            {
                                musicMenus.openCollection(
                                    MusicCollectionActionItem(
                                        id = playlist.id,
                                        title = playlist.name,
                                        subtitle = playlist.description,
                                        thumbnailUrl = playlist.thumbnailUrl,
                                    ),
                                )
                            }
                        },
                )
                if (isOwnedPlaylist) {
                    PlaylistActionsMenu(
                        expanded = showActions,
                        onDismiss = { showActions = false },
                        onDownload = null,
                        onRename = onRenameMusicPlaylist?.let { rename -> { rename(playlist) } },
                        onDelete = onDeletePlaylist?.let { delete -> { delete(playlist) } },
                        onOpen = { onMusicPlaylistClick(playlist.id) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryVideoShelf(
    section: LibrarySection,
    videosFlow: StateFlow<List<Video>?>,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onRemoveVideo: (Video) -> Unit,
) {
    val videos by videosFlow.collectAsStateWithLifecycle()
    val title = section.title
    val removeVideoLabel =
        stringResource(
            when (section) {
                LibrarySection.WATCH_LATER -> R.string.remove_from_watch_later
                else -> R.string.unlike
            },
        )
    when {
        videos == null -> {
            LibraryShelfPlaceholder(title = title, icon = section.icon)
        }

        videos.isNullOrEmpty() -> {
            Unit
        }

        else -> {
            LibraryShelf(
                title = title,
                icon = section.icon,
                onTitleClick = onTitleClick,
            ) { cardWidth ->
                items(
                    items = videos.orEmpty(),
                    key = Video::id,
                    contentType = { "video" },
                ) { video ->
                    LibraryVideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        width = cardWidth,
                        onRemoveFromCollection = { onRemoveVideo(video) },
                        removeFromCollectionLabel = removeVideoLabel,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryShortsShelfRoute(
    section: LibrarySection,
    shortsFlow: StateFlow<List<Video>?>,
    onTitleClick: () -> Unit,
    onShortClick: (Video) -> Unit,
) {
    val shorts by shortsFlow.collectAsStateWithLifecycle()
    val title = section.title
    when {
        shorts == null -> {
            LibraryShelfPlaceholder(title = title, icon = section.icon, portrait = true)
        }

        shorts.isNullOrEmpty() -> {
            Unit
        }

        else -> {
            LibraryShortsShelf(
                title = title,
                icon = section.icon,
                shorts = shorts.orEmpty(),
                onTitleClick = onTitleClick,
                onShortClick = onShortClick,
            )
        }
    }
}
