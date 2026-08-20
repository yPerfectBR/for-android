package chat.stoat.voice

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import chat.stoat.StoatApplication
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track

object ScreenShareWatchController {
    private val watching = mutableStateMapOf<String, Boolean>()
    private val volumes = mutableStateMapOf<String, Float>()
    private val muted = mutableStateMapOf<String, Boolean>()

    private val voiceVolumePreferences by lazy {
        StoatApplication.instance.getSharedPreferences(
            "voice_user_volumes",
            Context.MODE_PRIVATE
        )
    }

    fun reset() {
        watching.clear()
        volumes.clear()
        muted.clear()
    }

    fun isWatching(userId: String): Boolean =
        watching[userId] ?: false

    fun getVolume(userId: String): Float =
        volumes[userId] ?: 1f

    fun isMuted(userId: String): Boolean =
        muted[userId] ?: false

    fun getVoiceVolume(userId: String): Float =
        voiceVolumePreferences
            .getFloat(userId, 1f)
            .coerceIn(0f, 2f)

    fun setVoiceVolume(
        room: Room,
        userId: String,
        value: Float
    ) {
        val clamped = value.coerceIn(0f, 2f)

        voiceVolumePreferences
            .edit()
            .putFloat(userId, clamped)
            .apply()

        applyAudioState(
            room,
            VoiceCallManager.isDeafened
        )
    }

    fun setWatching(
        room: Room,
        userId: String,
        value: Boolean
    ) {
        watching[userId] = value
        applySubscription(room, userId)
        applyAudioState(room, VoiceCallManager.isDeafened)
    }

    fun clearWatching(userId: String) {
        watching.remove(userId)
    }

    fun setVolume(
        room: Room,
        userId: String,
        value: Float
    ) {
        volumes[userId] = value.coerceIn(0f, 3f)
        applyAudioState(room, VoiceCallManager.isDeafened)
    }

    fun setMuted(
        room: Room,
        userId: String,
        value: Boolean
    ) {
        muted[userId] = value
        applyAudioState(room, VoiceCallManager.isDeafened)
    }

    private fun applySubscription(
        room: Room,
        userId: String
    ) {
        val participant = room.remoteParticipants.values
            .firstOrNull { it.identity?.value == userId }
            ?: return

        val desired = isWatching(userId)

        for (
            source in listOf(
                Track.Source.SCREEN_SHARE,
                Track.Source.SCREEN_SHARE_AUDIO
            )
        ) {
            val publication =
                participant.getTrackPublication(source)
                    as? RemoteTrackPublication
                    ?: continue

            if (publication.isDesired != desired) {
                publication.setSubscribed(desired)
            }
        }
    }

    fun enforceSubscriptions(room: Room) {
        for (participant in room.remoteParticipants.values) {
            val userId = participant.identity?.value ?: continue
            applySubscription(room, userId)
        }

        applyAudioState(room, VoiceCallManager.isDeafened)
    }

    fun applyAudioState(
        room: Room,
        deafened: Boolean
    ) {
        for (participant in room.remoteParticipants.values) {
            val userId = participant.identity?.value ?: continue

            for ((publication, track) in participant.audioTrackPublications) {
                val audioTrack = track as? RemoteAudioTrack ?: continue

                val volume = when {
                    deafened -> 0.0

                    publication.source == Track.Source.SCREEN_SHARE_AUDIO &&
                        !isWatching(userId) -> 0.0

                    publication.source == Track.Source.SCREEN_SHARE_AUDIO &&
                        isMuted(userId) -> 0.0

                    publication.source == Track.Source.SCREEN_SHARE_AUDIO ->
                        getVolume(userId).toDouble()

                    publication.source == Track.Source.MICROPHONE ->
                        getVoiceVolume(userId).toDouble()

                    else -> 1.0
                }

                audioTrack.setVolume(volume)
            }
        }
    }
}
