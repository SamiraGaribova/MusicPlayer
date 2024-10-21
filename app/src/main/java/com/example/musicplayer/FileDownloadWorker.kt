import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.musicplayer.R
import kotlinx.coroutines.delay
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class FileDownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val notificationId = 1
    private val channelId = "download_channel"
    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun doWork(): Result {
        val fileUrl = inputData.getString("file_url") ?: return Result.failure()
        val fileName = inputData.getString("file_name") ?: return Result.failure()

        Log.d("FileDownloadWorker", "Downloading file: $fileName from $fileUrl")

        createNotificationChannel()

        // Show initial notification
        showProgressNotification("Downloading $fileName", 0)

        return try {
            downloadFile(fileUrl, fileName)
            Log.d("FileDownloadWorker", "Download completed: $fileName")

            // Update notification to "Download completed"
            delay(300)
            showProgressNotification("Download completed", 101)
            Result.success()
        } catch (e: Exception) {
            Log.e("FileDownloadWorker", "Error downloading file", e)

            // Update notification to "Download failed"

            showProgressNotification("Download failed",101)
            Result.failure()
        }
    }

    private fun downloadFile(fileUrl: String, fileName: String) {
        var input: InputStream? = null
        var output: OutputStream? = null
        var connection: HttpURLConnection? = null

        try {
            val url = URL(fileUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            // Check if the response is successful
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            // File length for progress calculation
            val fileLength = connection.contentLength

            // Download the file
            input = connection.inputStream
            val storagePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(storagePath, fileName)
            output = FileOutputStream(file)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count

                // Write data to file
                output.write(data, 0, count)

                // Update the notification progress
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    Log.d("ManagerTAG", "progress: $progress")
                    if (progress <= 100){
                        showProgressNotification("Downloading $fileName", progress)
                    }

                }
            }
            output.flush()

            Log.d("FileDownloadWorker", "File downloaded successfully: ${file.absolutePath}")
        } finally {
            // Close all resources
            try {
                input?.close()
                output?.close()
                connection?.disconnect()
            } catch (e: IOException) {
                Log.e("FileDownloadWorker", "Error closing streams", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showProgressNotification(content: String, progress: Int) {
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("File Download")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_download)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            if (progress in 0 .. 100) {
                builder.setProgress(100, progress, false)
            }

        builder.setOngoing(true)


        // Show the notification
        notificationManager.notify(notificationId, builder.build())
    }


    private fun createNotificationChannel() {
        val name = "Download Channel"
        val descriptionText = "Shows the progress of file downloads"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }

        // Register the channel with the system
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}