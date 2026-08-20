package chat.stoat.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class VideoStreamingInstrumentedTest {
    private lateinit var server: MockWebServer
    private lateinit var player: ExoPlayer

    private val ranges = CopyOnWriteArrayList<String>()

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        server = MockWebServer()

        val testContext =
            InstrumentationRegistry
                .getInstrumentation()
                .context

        val media = testContext.assets
            .open("seekable.webm")
            .readBytes()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(
                request: RecordedRequest
            ): MockResponse {
                if (request.path != "/media.webm") {
                    return MockResponse()
                        .setResponseCode(404)
                }

                val rawRange =
                    request.getHeader("Range")

                if (rawRange == null) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "video/webm")
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", media.size)
                        .setBody(Buffer().write(media))
                        .throttleBody(
                            64 * 1024L,
                            100,
                            TimeUnit.MILLISECONDS
                        )
                }

                ranges += rawRange

                val match = Regex(
                    """bytes=(\d*)-(\d*)"""
                ).matchEntire(rawRange)
                    ?: return MockResponse()
                        .setResponseCode(416)
                        .setHeader(
                            "Content-Range",
                            "bytes */${media.size}"
                        )

                val start =
                    match.groupValues[1]
                        .toLongOrNull()
                        ?: 0L

                val requestedEnd =
                    match.groupValues[2]
                        .toLongOrNull()
                        ?: (media.size - 1).toLong()

                if (start >= media.size) {
                    return MockResponse()
                        .setResponseCode(416)
                        .setHeader(
                            "Content-Range",
                            "bytes */${media.size}"
                        )
                }

                val end =
                    minOf(
                        requestedEnd,
                        (media.size - 1).toLong()
                    )

                val body =
                    media.copyOfRange(
                        start.toInt(),
                        end.toInt() + 1
                    )

                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/webm")
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader(
                        "Content-Range",
                        "bytes $start-$end/${media.size}"
                    )
                    .setHeader(
                        "Content-Length",
                        body.size
                    )
                    .setBody(Buffer().write(body))
                    .throttleBody(
                        64 * 1024L,
                        100,
                        TimeUnit.MILLISECONDS
                    )
            }
        }

        server.start()

        InstrumentationRegistry
            .getInstrumentation()
            .runOnMainSync {
                player = ExoPlayer.Builder(context).build()
            }
    }

    @After
    fun teardown() {
        if (::player.isInitialized) {
            InstrumentationRegistry
                .getInstrumentation()
                .runOnMainSync {
                    player.release()
                }
        }

        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    @Test
    fun playbackAndSeekUseStreamingRanges() {
        val readyLatch = CountDownLatch(1)
        val seekLatch = CountDownLatch(1)
        val playerError = AtomicReference<PlaybackException?>()

        InstrumentationRegistry
            .getInstrumentation()
            .runOnMainSync {
                player.addListener(
                    object : Player.Listener {
                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    if (
                        playbackState ==
                        Player.STATE_READY
                    ) {
                        readyLatch.countDown()
                    }
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    playerError.set(error)
                    readyLatch.countDown()
                }

                override fun onPositionDiscontinuity(
                    oldPosition:
                        Player.PositionInfo,
                    newPosition:
                        Player.PositionInfo,
                    reason: Int
                ) {
                    if (
                        reason ==
                        Player.DISCONTINUITY_REASON_SEEK
                    ) {
                        seekLatch.countDown()
                    }
                }
                    }
                )

                player.setMediaItem(
                    MediaItem.fromUri(
                        server.url("/media.webm").toString()
                    )
                )
                player.prepare()
            }

        assertTrue(
            "Player did not become ready or fail",
            readyLatch.await(
                15,
                TimeUnit.SECONDS
            )
        )

        playerError.get()?.let {
            throw AssertionError(
                "ExoPlayer failed: ${it.errorCodeName}: ${it.message}",
                it
            )
        }

        InstrumentationRegistry
            .getInstrumentation()
            .runOnMainSync {
                player.play()
            }

        Thread.sleep(500)

        InstrumentationRegistry
            .getInstrumentation()
            .runOnMainSync {
                player.seekTo(6_000)
            }

        assertTrue(
            "Seek did not complete",
            seekLatch.await(
                10,
                TimeUnit.SECONDS
            )
        )

        Thread.sleep(500)

        assertTrue(
            "Expected at least one HTTP Range request",
            ranges.isNotEmpty()
        )

        assertTrue(
            "Expected byte range syntax",
            ranges.any {
                it.startsWith("bytes=")
            }
        )
    }
}
