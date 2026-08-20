package chat.stoat.composables.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.composables.chat.displayNameInChannel
import chat.stoat.voice.ScreenShareWatchController
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room

@Composable
fun RemoteScreenShareCard(
    trackReference: TrackReference,
    room: Room,
    channelId: String,
    modifier: Modifier = Modifier
) {
    val userId = trackReference.participant.identity?.value ?: return

    val watching = ScreenShareWatchController.isWatching(userId)
    val volume = ScreenShareWatchController.getVolume(userId)
    val muted = ScreenShareWatchController.isMuted(userId)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            if (
                watching &&
                trackReference.publication?.track != null
            ) {
                VideoTrackView(
                    trackReference = trackReference,
                    room = room,
                    scaleType = ScaleType.FitInside,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (watching) {
                        Text("Connecting stream...")
                    } else {
                        Button(
                            onClick = {
                                ScreenShareWatchController.setWatching(
                                    room,
                                    userId,
                                    true
                                )
                            }
                        ) {
                            Text("Watch stream")
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(
                    alpha = 0.85f
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(
                            R.drawable.ic_screen_share_24dp
                        ),
                        contentDescription = stringResource(
                            R.string.voice_screen_sharing
                        ),
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = displayNameInChannel(
                            userId,
                            channelId
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (watching) {
                Button(
                    onClick = {
                        ScreenShareWatchController.setWatching(
                            room,
                            userId,
                            false
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text("Stop watching")
                }
            }
        }

        if (watching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        ScreenShareWatchController.setMuted(
                            room,
                            userId,
                            !muted
                        )
                    }
                ) {
                    Text(
                        if (muted) {
                            "Unmute stream"
                        } else {
                            "Mute stream"
                        }
                    )
                }

                Text(
                    text = "Volume ${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Slider(
                value = volume,
                onValueChange = {
                    ScreenShareWatchController.setVolume(
                        room,
                        userId,
                        it
                    )
                },
                valueRange = 0f..3f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
