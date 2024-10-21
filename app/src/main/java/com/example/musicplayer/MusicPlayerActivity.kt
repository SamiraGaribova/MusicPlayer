package com.example.musicplayer

import FileDownloadWorker
import android.content.ServiceConnection
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.musicplayer.databinding.ActivityMusicPlayerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MusicPlayerActivity : AppCompatActivity() {
    private val CHANNEL_ID = "music_player_channel"
    private val POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE = 1
    private lateinit var binding: ActivityMusicPlayerBinding
    private val viewModel by viewModels<MusicViewModel>()
    private var musicService: MusicService? = null
    private var isMusicServiceConnected = false
    private var currentTrack: MusicTrackModel? = null

    private var notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){isGranted ->

        if (isGranted){
            startDownload()
        }
    }

    private val musicServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicService.MusicBinder
            musicService = binder?.getMusicService()
            isMusicServiceConnected = true
            onServiceConnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isMusicServiceConnected = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE
                )
            }
        }

        bindService(
            Intent(this, MusicService::class.java),
            musicServiceConnection,
            Context.BIND_AUTO_CREATE
        )

        // Set click listeners for music control buttons
        binding.playPauseButton.setOnClickListener {
            musicService?.playPauseTrack()
        }

        binding.prevButton.setOnClickListener {
            musicService?.prevTrack()
        }

        binding.nextButton.setOnClickListener {
            musicService?.nextTrack()
        }

        // Download button click listener
        binding.downloadButton.setOnClickListener {
            currentTrack = musicService?.getCurrentTrack()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }else{
                startDownload()
            }
        }

        // SeekBar listener
        binding.trackProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.onUserProgressChange(progress)
                    binding.currentTime.text = getMinutesFromSeconds(progress)
                }
                seekBar?.setProgress(progress, true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startDownload() {
        currentTrack?.let {
            downloadFileUsingWorkManager(it.trackUrl, "${it.name}.mp3")
        }
    }

    private fun onServiceConnected() {
        musicService?.setOnTrackChanged { track ->
            binding.trackName.text = track.name
            binding.prevButton.isEnabled = track.hasPrevTrack
            binding.nextButton.isEnabled = track.hasNextTrack
            binding.trackProgress.max = track.duration
            binding.currentTime.text = getMinutesFromSeconds(0)
            binding.totalTime.text = getMinutesFromSeconds(track.duration)
        }

        musicService?.setOnPlayingStateChanged { isPlaying ->
            val iconResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            binding.playPauseButton.setImageDrawable(
                ContextCompat.getDrawable(this, iconResId)
            )
        }

        musicService?.setOnTrackPositionChanged { position ->
            binding.trackProgress.setProgress(position, true)
            binding.currentTime.text = getMinutesFromSeconds(position)
        }
        musicService?.trackList = viewModel.createMusic()
    }

    private fun getMinutesFromSeconds(seconds: Int): String {
        val minutesText = seconds / 60
        val secondsText = seconds % 60
        return String.format("%02d:%02d", minutesText, secondsText)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(musicServiceConnection)
        isMusicServiceConnected = false
    }

    // Helper method to start file download using WorkManager
    private fun downloadFileUsingWorkManager(url: String, fileName: String) {
        Log.d("MusicPlayerActivity", "Starting download for: $fileName from URL: $url")

        val data = Data.Builder()
            .putString("file_url", url)
            .putString("file_name", fileName)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueue(downloadRequest)
    }



    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MusicPlayerActivity", "Notification permission granted")
            } else {
                Log.d("MusicPlayerActivity", "Notification permission denied")
            }
        }
    }
}