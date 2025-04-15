package com.parwar.german_learning.media

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

private const val TAG = "TTSMediaSession"

class TTSMediaSession(
    private val service: MediaPlaybackService
) {
    private val mediaSession: MediaSessionCompat = MediaSessionCompat(service, "TTSMediaSession").apply {
        Log.d(TAG, "Initializing MediaSession")
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d(TAG, "onPlay called")
                service.mediaControlCallback?.onPlayPause()
                val isPlaying = service.mediaControlCallback?.isPlaying() == true
                Log.d(TAG, "Current playing state: $isPlaying")
                updatePlaybackState(if (isPlaying) 
                    PlaybackStateCompat.STATE_PLAYING 
                else 
                    PlaybackStateCompat.STATE_PAUSED
                )
            }

            override fun onPause() {
                Log.d(TAG, "onPause called")
                service.mediaControlCallback?.onPlayPause()
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }

            override fun onStop() {
                Log.d(TAG, "onStop called")
                service.mediaControlCallback?.onStop()
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            }

            override fun onSkipToNext() {
                Log.d(TAG, "onSkipToNext called")
                updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
                service.mediaControlCallback?.onNext()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }

            override fun onSkipToPrevious() {
                Log.d(TAG, "onSkipToPrevious called")
                updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS)
                service.mediaControlCallback?.onPrevious()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                Log.d(TAG, "onPlayFromMediaId called with id: $mediaId")
                onPlay()
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                Log.d(TAG, "onPlayFromSearch called with query: $query")
                onPlay()
            }
        })

        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or 
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
            MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
        )
        Log.d(TAG, "MediaSession flags set")
        setActive(true)
        Log.d(TAG, "MediaSession activated")
    }

    fun getSessionToken(): MediaSessionCompat.Token {
        return mediaSession.sessionToken
    }

    fun updatePlaybackState(state: Int) {
        Log.d(TAG, "Updating playback state to: $state")
        val stateStr = when(state) {
            PlaybackStateCompat.STATE_PLAYING -> "PLAYING"
            PlaybackStateCompat.STATE_PAUSED -> "PAUSED"
            PlaybackStateCompat.STATE_STOPPED -> "STOPPED"
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
            else -> "UNKNOWN"
        }
        Log.d(TAG, "State in human readable form: $stateStr")
        
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
        Log.d(TAG, "PlaybackState updated with actions: ${playbackState.actions}")
    }

    fun updateMetadata(text: String) {
        Log.d(TAG, "Updating metadata with text: $text")
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, text)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, text)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "tts_${System.currentTimeMillis()}")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "Text-to-Speech Playback")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "German Learning")
            .build()
        mediaSession.setMetadata(metadata)
        Log.d(TAG, "Metadata updated successfully")
    }

    fun release() {
        Log.d(TAG, "Releasing MediaSession")
        mediaSession.release()
    }

    fun setActive(active: Boolean) {
        Log.d(TAG, "Setting MediaSession active: $active")
        mediaSession.isActive = active
    }
}
