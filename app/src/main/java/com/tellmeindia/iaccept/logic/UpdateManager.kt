package com.tellmeindia.iaccept.logic

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.tellmeindia.iaccept.data.AppUpdate
import java.io.File

object UpdateManager {
    private const val TAG = "UpdateManager"

    fun downloadAndInstallApk(context: Context, update: AppUpdate) {
        try {
            val destinationFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "IAccept_v${update.versionName}.apk"
            )

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(update.apkUrl)).apply {
                setTitle("IAccept Update v${update.versionName}")
                setDescription("Downloading new update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            val onCompleteReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            ctx?.unregisterReceiver(this)
                        } catch (e: Exception) { }
                        installApk(context, destinationFile)
                    }
                }
            }

            androidx.core.content.ContextCompat.registerReceiver(
                context,
                onCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val packageInstaller = context.packageManager.packageInstaller
                    val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(context.packageName)
                        setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }

                    val sessionId = packageInstaller.createSession(params)
                    val session = packageInstaller.openSession(sessionId)

                    apkFile.inputStream().use { input ->
                        session.openWrite("iaccept_update_session", 0, apkFile.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }

                    val intent = Intent(context, com.tellmeindia.iaccept.MainActivity::class.java).apply {
                        action = "com.tellmeindia.iaccept.ACTION_UPDATE_COMPLETE"
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        sessionId,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                    )

                    session.commit(pendingIntent.intentSender)
                    session.close()
                    Log.d(TAG, "SILENT UNATTENDED UPDATE COMMITTED SUCCESSFULLY")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Silent update fallback: ${e.message}")
                }
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                setDataAndType(apkUri, "application/vnd.android.package-archive")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}")
        }
    }
}
