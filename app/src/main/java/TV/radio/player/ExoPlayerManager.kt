package tv.radio.player

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import tv.radio.data.Station
import tv.radio.ui.PlaybackState
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.icy.IcyInfo
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.charset.Charset

/**
 * ExoPlayer 播放器管理器单例类
 * 负责管理 ExoPlayer 的生命周期和播放控制
 * 同时处理 ICY 元数据乱码修复（自动检测 UTF-8/GBK/GB18030）
 */
class ExoPlayerManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ExoPlayerManager"

        @Volatile
        private var instance: ExoPlayerManager? = null

        fun getInstance(context: Context): ExoPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: ExoPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // 播放器实例
    private var exoPlayer: ExoPlayer? = null

    // 当前播放的电台
    private var currentStation: Station? = null

    // 播放状态
    private val _playbackState = MutableLiveData<PlaybackState>(PlaybackState.Stopped)
    val playbackState: Flow<PlaybackState> = _playbackState.asFlow()

    // 元数据流（歌曲信息）
    private val _metadataFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val metadataFlow: SharedFlow<String> = _metadataFlow.asSharedFlow()

    // 音量 (0.0 - 1.0)
    private var currentVolume = 1.0f

    // 硬解码开关
    private var hardwareDecodeEnabled = true

    /**
     * 初始化播放器
     */
    fun initialize() {
        try {
            exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build()

            exoPlayer?.addListener(ExoPlayerListener())
            Log.d(TAG, "ExoPlayer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ExoPlayer", e)
            _playbackState.postValue(PlaybackState.Error("播放器初始化失败: ${e.message}"))
        }
    }

    /**
     * 播放电台
     */
    fun playStation(station: Station) {
        Log.d(TAG, "Playing station: ${station.name}, URL: ${station.url}")
        currentStation = station
        _playbackState.postValue(PlaybackState.Buffering)

        try {
            exoPlayer?.let { player ->
                val dataSourceFactory = DefaultDataSourceFactory(
                    context,
                    Util.getUserAgent(context, "IjkRadioPlayer")
                )

                val uri = Uri.parse(station.url)
                val mediaItemBuilder = MediaItem.Builder().setUri(uri)

                // 仅在 API 21+ 设置追帧速度（SDK 19 跳过）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaItemBuilder.setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f)
                            .build()
                    )
                }
                val mediaItem = mediaItemBuilder.build()

                val mediaSource = when {
                    uri.lastPathSegment?.endsWith(".m3u8", ignoreCase = true) == true ||
                    Util.inferContentType(uri) == C.CONTENT_TYPE_HLS -> {
                        HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                    }
                    else -> {
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                    }
                }

                player.setMediaSource(mediaSource)
                player.prepare()
                player.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing station", e)
            _playbackState.postValue(PlaybackState.Error("播放失败: ${e.message}"))
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        try {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _playbackState.postValue(PlaybackState.Paused)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    /**
     * 恢复播放
     */
    fun resume() {
        try {
            exoPlayer?.let { player ->
                player.play()
                currentStation?.let { station ->
                    _playbackState.postValue(PlaybackState.Playing(station.name))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        try {
            exoPlayer?.stop()
            _playbackState.postValue(PlaybackState.Stopped)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * 设置音量
     */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            exoPlayer?.volume = currentVolume
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    /**
     * 设置硬解码
     */
    fun setHardwareDecode(useHardware: Boolean) {
        hardwareDecodeEnabled = useHardware
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    /**
     * 获取当前播放的电台
     */
    fun getCurrentStation(): Station? = currentStation

    /**
     * 释放播放器资源
     */
    fun release() {
        try {
            exoPlayer?.release()
            exoPlayer = null
            currentStation = null
            _playbackState.postValue(PlaybackState.Stopped)
            Log.d(TAG, "ExoPlayer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ExoPlayer", e)
        }
    }

    // ==================== 智能编码检测与修复 ====================

    /**
     * 智能修复元数据编码
     * 优先尝试 UTF-8，仅在 UTF-8 明显失败时才回退到 GBK/GB18030
     */
    private fun smartFixMetadata(badString: String): String {
        if (badString.isBlank()) return badString

        // 还原为原始字节（ExoPlayer 错误地按 ISO-8859-1 读取）
        val bytes = badString.toByteArray(Charsets.ISO_8859_1)

        // 1. 优先尝试 UTF-8，并检查解码质量
        try {
            val utf8Decoded = String(bytes, Charsets.UTF_8)
            // 如果解码结果中不包含替换字符 '�'，并且至少有一个中文字符 → 认为是有效的 UTF-8
            if (!utf8Decoded.contains('�') && utf8Decoded.any { it in '\u4e00'..'\u9fff' }) {
                Log.d(TAG, "UTF-8 decoding successful: $utf8Decoded")
                return utf8Decoded
            }
            Log.d(TAG, "UTF-8 decoding contains replacement char or no Chinese, fallback")
        } catch (e: Exception) {
            Log.w(TAG, "UTF-8 decode failed", e)
        }

        // 2. 回退到多编码比较（UTF-8、GBK、GB18030）
        val encodings = listOf(
            Charsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB18030")
        )

        var bestResult = badString
        var bestChineseCount = 0

        for (charset in encodings) {
            try {
                val decoded = String(bytes, charset)
                val chineseCount = decoded.count { it in '\u4e00'..'\u9fff' }
                if (chineseCount > bestChineseCount) {
                    bestChineseCount = chineseCount
                    bestResult = decoded
                }
                Log.d(TAG, "Encoding ${charset.name()}: '$decoded' (Chinese count: $chineseCount)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode with ${charset.name()}", e)
            }
        }

        return bestResult
    }

    // ==================== 内部监听器实现 ====================

    private inner class ExoPlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Buffering")
                    _playbackState.postValue(PlaybackState.Buffering)
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "Ready")
                    currentStation?.let {
                        _playbackState.postValue(PlaybackState.Playing(it.name))
                    }
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Ended")
                    _playbackState.postValue(PlaybackState.Stopped)
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Idle")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}")
            _playbackState.postValue(PlaybackState.Error(error.message ?: "未知错误"))
        }

        override fun onMetadata(metadata: Metadata) {
            Log.d(TAG, "onMetadata called, entry count: ${metadata.length()}")
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is IcyInfo) {
                    val rawTitle = entry.title ?: ""
                    Log.d(TAG, "IcyInfo raw title: $rawTitle")
                    val fixed = smartFixMetadata(rawTitle)
                    if (fixed.isNotBlank()) {
                        _metadataFlow.tryEmit(fixed)
                    }
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            Log.d(TAG, "onMediaMetadataChanged called")
            val title = mediaMetadata.title?.toString() ?: ""
            val artist = mediaMetadata.artist?.toString() ?: ""
            Log.d(TAG, "MediaMetadata - title: $title, artist: $artist")
            val raw = if (artist.isNotEmpty()) "$artist - $title" else title
            if (raw.isNotBlank()) {
                val fixed = smartFixMetadata(raw)
                if (fixed.isNotBlank()) {
                    _metadataFlow.tryEmit(fixed)
                }
            }
        }
    }
}