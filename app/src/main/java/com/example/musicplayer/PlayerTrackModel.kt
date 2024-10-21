package com.example.musicplayer

data class PlayerTrackModel(
    var name: String,
    var duration: Int,
    var hasPrevTrack: Boolean,
    var hasNextTrack: Boolean
)