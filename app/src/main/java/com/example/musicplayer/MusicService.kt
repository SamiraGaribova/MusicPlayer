package com.example.musicplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class MusicService : Service(), CoroutineScope {

    var trackList = listOf<MusicTrackModel>()
        set(value) {
            field = value
            getNewMusicFromList()
        }

    private var currentTrackIndex = 0
    private var musicPlayer: MediaPlayer? = null

    private var onTrackChanged: ((PlayerTrackModel) -> Unit)? = null
    private var onTrackPositionChanged: ((Int) -> Unit)? = null
    private var onPlayingStateChanged: ((Boolean) -> Unit)? = null
    private var progressJob: Job? = null

    fun setOnTrackChanged(onTrackChanged: (PlayerTrackModel) -> Unit) {
        this.onTrackChanged = onTrackChanged
    }

    fun setOnTrackPositionChanged(onTrackPositionChanged: (Int) -> Unit) {
        this.onTrackPositionChanged = onTrackPositionChanged
    }

    fun setOnPlayingStateChanged(onPlayingStateChanged: (Boolean) -> Unit) {
        this.onPlayingStateChanged = onPlayingStateChanged
    }

    override fun onBind(intent: Intent): IBinder {
        return MusicBinder()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "music_channel", "Music Player", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MusicPlayerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, "music_channel")
            .setContentTitle("Music Player")
            .setContentText("Playing music")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    inner class MusicBinder : Binder() {
        fun getMusicService() = this@MusicService
    }

    private fun getNewMusicFromList() {
        if (trackList.size <= currentTrackIndex) return
        val track = trackList[currentTrackIndex]
        val uri = Uri.parse(track.trackUrl)

        CoroutineScope(Dispatchers.IO).launch {
            musicPlayer?.stop()
            musicPlayer?.reset()
            musicPlayer = MediaPlayer.create(this@MusicService, uri)
            musicPlayer?.isLooping = false
            val durationInMilliseconds = musicPlayer?.duration ?: 0
            val duration = if (durationInMilliseconds > 0) durationInMilliseconds / 1000 else 0

            withContext(Dispatchers.Main) {
                val playerTrack = PlayerTrackModel(
                    name = track.name,
                    duration = duration,
                    hasNextTrack = hasNext(currentTrackIndex),
                    hasPrevTrack = hasPrev(currentTrackIndex)
                )
                startProgressUpdater()
                trackChanged(playerTrack)
            }
        }
    }

    private fun hasNext(trackIndex: Int) = trackList.size - 1 > trackIndex
    private fun hasPrev(trackIndex: Int) = trackIndex - 1 >= 0

    private fun trackChanged(playerTrackModel: PlayerTrackModel) {
        onTrackChanged?.invoke(playerTrackModel)
    }

    fun playPauseTrack() {
        try {
            if (musicPlayer?.isPlaying == true) {
                musicPlayer?.pause()
                stopProgressUpdater()
                onPlayingStateChanged?.invoke(false)
            } else {
                musicPlayer?.start()
                startProgressUpdater()
                onPlayingStateChanged?.invoke(true)
                startForegroundService()
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error during play/pause: ${e.message}")
        }
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()  // Cancel any previous job
        progressJob = launch {
            while (musicPlayer?.isPlaying == true) {
                val currentPosition = musicPlayer?.currentPosition ?: 0
                val duration = musicPlayer?.duration ?: 0
                updateProgress(currentPosition, duration)
                delay(1000)  // Update every second
            }
        }
    }

    private fun stopProgressUpdater() {
        progressJob?.cancel()
    }

    private fun updateProgress(currentPosition: Int, duration: Int) {
        val position = if (currentPosition > 0) currentPosition / 1000 else currentPosition
        onTrackPositionChanged?.invoke(position)
    }

    fun nextTrack() {
        currentTrackIndex++
        getNewMusicFromList()
        playPauseTrack()
    }

    fun prevTrack() {
        currentTrackIndex--
        getNewMusicFromList()
        playPauseTrack()
    }

    fun onUserProgressChange(position: Int) {
        musicPlayer?.seekTo(position * 1000)
    }


    fun getCurrentTrack(): MusicTrackModel? {
        return if (trackList.isNotEmpty() && currentTrackIndex in trackList.indices) {
            trackList[currentTrackIndex]
        } else {
            null
        }
    }


    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    private fun releaseMediaPlayer() {
        musicPlayer?.release()
        musicPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
        stopProgressUpdater()
    }
}