package chat.stoat.voice

/**
 * Screen-share profiles for the Homelab client.
 * 60 FPS values are custom profiles; 30 FPS values preserve upstream behavior.
 */
enum class ScreenShareQuality(
    val width: Int,
    val height: Int,
    val fps: Int,
    val maxBitrate: Int,
    val label: String,
) {
    H720_30(1280, 720, 30, 2_000_000, "720p 30 FPS"),
    H720_60(1280, 720, 60, 4_000_000, "720p 60 FPS"),
    H1080_30(1920, 1080, 30, 5_000_000, "1080p 30 FPS"),
    H1080_60(1920, 1080, 60, 8_000_000, "1080p 60 FPS"),
}
