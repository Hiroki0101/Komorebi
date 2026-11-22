package com.beeregg2001.komorebi.ui.video.player

import android.net.Uri
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.beeregg2001.komorebi.common.UrlBuilder
import com.beeregg2001.komorebi.common.safeRequestFocus
import com.beeregg2001.komorebi.data.model.RecordedProgram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private const val TAG = "VlcOriginalPlayer"

/**
 * ★ パッチ (Komorebi-fork): v9 実験プレイヤー。
 * 録画の original 画質 (生 MPEG-2 TS) を、端末の MediaCodec を使わず
 * libVLC 内蔵の FFmpeg ソフトウェアデコードで再生する。
 * Google TV Streamer の c2.mtk.mpeg2.decoder が激しい動きで乱れる問題への
 * 対症実験 (Channels アプリで同症状がソフトデコード切替で直った前例あり)。
 * 割り切り: 視聴位置の保存・チャプター・コメント・画質切替・字幕/音声切替は未対応。
 * 操作: 中央=再生/一時停止、左右=∓10秒/+30秒、戻る=終了。
 */
@Composable
fun VlcOriginalPlayerScreen(
    program: RecordedProgram,
    serverIp: String,
    serverPort: String,
    initialPositionMs: Long = 0,
    onBackPressed: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var timeMs by remember { mutableLongStateOf(initialPositionMs) }
    var lengthMs by remember { mutableLongStateOf(0L) }
    var showOsd by remember { mutableStateOf(true) }
    var osdDeadline by remember { mutableLongStateOf(System.currentTimeMillis() + 4000) }

    val streamUrl = remember(serverIp, serverPort, program.id) {
        if (serverIp.isBlank()) ""
        else UrlBuilder.getKonomiTvVideoDownloadUrl(serverIp, serverPort, program.id)
    }

    val mainFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { mainFocusRequester.safeRequestFocus(TAG) }
    LaunchedEffect(osdDeadline) {
        val remain = osdDeadline - System.currentTimeMillis()
        if (remain > 0) delay(remain)
        if (System.currentTimeMillis() >= osdDeadline) showOsd = false
    }
    val pokeOsd: () -> Unit = {
        showOsd = true
        osdDeadline = System.currentTimeMillis() + 4000
    }

    if (streamUrl.isNotEmpty()) {
        val vlcComponents = remember(streamUrl) {
            val options = arrayListOf(
                "--drop-late-frames",
                "--skip-frames",
                "--network-caching=3000",
                "--clock-jitter=0",
                "--clock-synchro=0",
                "--avcodec-skiploopfilter=4",
                "--avcodec-threads=0",
                "--avcodec-hurry-up"
            )
            val libVLC = LibVLC(context, options)
            val mediaPlayer = MediaPlayer(libVLC)
            val media = Media(libVLC, Uri.parse(streamUrl)).apply {
                // ★ 実験の核心: ハードウェアデコードを無効化し、FFmpeg の
                // ソフトウェアデコード (mpeg2video) で再生させる
                setHWDecoderEnabled(false, false)
                if (initialPositionMs > 0) {
                    addOption(":start-time=${initialPositionMs / 1000f}")
                }
            }
            mediaPlayer.media = media
            media.release()
            Pair(libVLC, mediaPlayer)
        }
        val mediaPlayer = vlcComponents.second

        DisposableEffect(lifecycleOwner, mediaPlayer) {
            val listener = MediaPlayer.EventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        isPlaying = true; isBuffering = false
                    }

                    MediaPlayer.Event.Paused -> {
                        isPlaying = false
                    }

                    MediaPlayer.Event.TimeChanged -> {
                        if (event.timeChanged > 0L) {
                            timeMs = event.timeChanged
                            isBuffering = false
                        }
                    }

                    MediaPlayer.Event.LengthChanged -> {
                        lengthMs = event.lengthChanged
                    }

                    MediaPlayer.Event.Buffering -> {
                        isBuffering = event.buffering < 100f
                    }

                    MediaPlayer.Event.EndReached -> {
                        onBackPressed()
                    }

                    MediaPlayer.Event.EncounteredError -> {
                        onShowToast("VLC実験プレイヤー: 再生エラーが発生しました")
                    }
                }
            }
            mediaPlayer.setEventListener(listener)
            mediaPlayer.play()

            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) mediaPlayer.pause()
                else if (event == Lifecycle.Event.ON_START && isPlaying) mediaPlayer.play()
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mediaPlayer.stop()
                mediaPlayer.detachViews()
                mediaPlayer.release()
                vlcComponents.first.release()
            }
        }

        val performSeek: (Long) -> Unit = { targetMs ->
            val safeTarget =
                if (lengthMs > 0L) targetMs.coerceIn(0L, lengthMs)
                else targetMs.coerceAtLeast(0L)
            timeMs = safeTarget
            scope.launch { mediaPlayer.time = safeTarget }
            pokeOsd()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(mainFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val e = keyEvent.nativeKeyEvent
                    if (e.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (e.keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            onBackPressed(); true
                        }

                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                            pokeOsd(); true
                        }

                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            performSeek(timeMs - 10_000); true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            performSeek(timeMs + 30_000); true
                        }

                        else -> {
                            pokeOsd(); false
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).apply {
                        keepScreenOn = true
                        mediaPlayer.attachViews(this, null, false, false)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            if (showOsd) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(32.dp)
                ) {
                    Text(
                        text = program.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${formatVlcTime(timeMs)} / ${formatVlcTime(lengthMs)}  (VLC実験: ソフトウェアデコード)",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

private fun formatVlcTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
