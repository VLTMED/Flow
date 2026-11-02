package io.github.aedev.flow.ui.components.library

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ThumbnailQuality
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.components.shared.LocalThumbnailQuality
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class LibraryShelfLongPressTest {
    @get:Rule
    val rule = createComposeRule()

    private fun video(
        id: String,
        title: String,
    ) = Video(
        id = id,
        title = title,
        channelName = "Channel",
        channelId = "channel-id",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
    )

    @Test
    fun historyWatchLaterLikedAndPlaylistCardsDispatchLongPressAndKeepNormalClicks() {
        val longPresses = mutableListOf<String>()
        var normalClicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalThumbnailQuality provides ThumbnailQuality.OFF) {
                MaterialTheme {
                    Column {
                        listOf("history", "watch-later", "liked").forEach { shelf ->
                            LibraryVideoCard(
                                video = video(shelf, shelf),
                                onClick = { normalClicks++ },
                                onLongClick = { longPresses += shelf },
                                modifier = Modifier.testTag(shelf),
                            )
                        }
                        PlaylistCard(
                            playlist =
                                PlaylistInfo(
                                    id = "playlist",
                                    name = "Video playlist",
                                    description = "",
                                    videoCount = 1,
                                    thumbnailUrl = "",
                                    isPrivate = true,
                                    createdAt = 0L,
                                ),
                            onClick = { normalClicks++ },
                            onLongClick = { longPresses += "video-playlist" },
                            layout = PlaylistCardLayout.SHELF,
                            modifier = Modifier.width(220.dp).testTag("video-playlist"),
                        )
                        LibraryAlbumCard(
                            title = "Music playlist",
                            subtitle = "1 track",
                            thumbnailUrl = null,
                            onClick = { normalClicks++ },
                            modifier = Modifier.testTag("music-playlist"),
                            artworkSize = 120.dp,
                            onLongClick = { longPresses += "music-playlist" },
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("history").performClick()
        rule.onNodeWithTag("history").performTouchInput { longClick() }
        rule.onNodeWithTag("watch-later").performTouchInput { longClick() }
        rule.onNodeWithTag("liked").performTouchInput { longClick() }
        rule.onNodeWithTag("video-playlist").performTouchInput { longClick() }
        rule.onNodeWithTag("music-playlist").performTouchInput { longClick() }

        assertThat(normalClicks).isEqualTo(1)
        assertThat(longPresses)
            .containsExactly(
                "history",
                "watch-later",
                "liked",
                "video-playlist",
                "music-playlist",
            ).inOrder()
    }

    @Test
    fun playlistActionsMenuOffersOpenAndDelete() {
        var openCount = 0
        var deleteCount = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent {
            MaterialTheme {
                Box {
                    Spacer(Modifier.size(width = 220.dp, height = 100.dp))
                    PlaylistActionsMenu(
                        expanded = true,
                        onDismiss = {},
                        onDownload = null,
                        onRename = null,
                        onDelete = { deleteCount++ },
                        onOpen = { openCount++ },
                    )
                }
            }
        }

        val openLabel = context.getString(R.string.open_playlist)
        val deleteLabel = context.getString(R.string.action_delete)
        rule.onNodeWithText(openLabel).assertIsDisplayed().performClick()
        rule.onNodeWithText(deleteLabel).assertIsDisplayed().performClick()

        assertThat(openCount).isEqualTo(1)
        assertThat(deleteCount).isEqualTo(1)
    }
}
