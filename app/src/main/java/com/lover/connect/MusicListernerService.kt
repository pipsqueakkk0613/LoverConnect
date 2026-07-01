package com.lover.connect

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class MusicListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    companion object {
        fun getNowPlaying(context: Context): String {
            val json = getNowPlayingJson(context)
            return if (json.optBoolean("playing", false)) {
                val sb = StringBuilder()
                sb.append("${json.optString("title")} - ${json.optString("artist")}")
                val album = json.optString("album")
                if (album.isNotEmpty()) sb.append("（$album）")
                sb.append(" [${if (json.optBoolean("playing")) "播放中" else "已暂停"}]")

                val duration = json.optLong("duration", 0)
                val position = json.optLong("position", 0)
                if (duration > 0) {
                    val min = position / 60000
                    val sec = (position % 60000) / 1000
                    val dmin = duration / 60000
                    val dsec = (duration % 60000) / 1000
                    sb.append(" ${min}:${String.format("%02d", sec)}/${dmin}:${String.format("%02d", dsec)}")
                }

                val app = json.optString("app")
                if (app.isNotEmpty()) sb.append(" - $app")
                sb.toString()
            } else {
                json.optString("info", "未在播放音乐")
            }
        }

        fun getNowPlayingJson(context: Context): JSONObject {
            val result = JSONObject()
            try {
                val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val componentName = ComponentName(context, MusicListenerService::class.java)
                val controllers = msm.getActiveSessions(componentName)

                if (controllers.isEmpty()) {
                    result.put("playing", false)
                    result.put("info", "未在播放音乐")
                    return result
                }

                val controller = controllers[0]
                val metadata = controller.metadata
                val state = controller.playbackState

                if (metadata == null) {
                    result.put("playing", false)
                    result.put("info", "未在播放音乐")
                    return result
                }

                result.put("playing", state?.state == PlaybackState.STATE_PLAYING)
                result.put("title", metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "未知")
                result.put("artist", metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "未知")
                result.put("album", metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "")

                val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                val position = state?.position ?: 0
                result.put("duration", duration)
                result.put("position", position)

                if (duration > 0) {
                    val progress = (position.toFloat() / duration * 100).toInt()
                    result.put("progress", progress)
                }

                val pkg = controller.packageName
                val pm = context.packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) { pkg }
                result.put("app", appName)
                result.put("package", pkg)

            } catch (e: Exception) {
                result.put("playing", false)
                result.put("error", e.message ?: "未知错误")
            }
            return result
        }
    }
}
