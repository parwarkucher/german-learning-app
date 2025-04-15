package com.parwar.german_learning.media

interface MediaControlCallback {
    fun onPlayPause()
    fun onNext()
    fun onPrevious()
    fun onStop()
    fun isPlaying(): Boolean
}
