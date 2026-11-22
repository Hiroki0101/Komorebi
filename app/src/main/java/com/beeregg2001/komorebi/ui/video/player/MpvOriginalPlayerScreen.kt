package com.beeregg2001.komorebi.ui.video.player

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.beeregg2001.komorebi.common.UrlBuilder
import com.beeregg2001.komorebi.common.safeRequestFocus
import com.beeregg2001.komorebi.data.model.RecordedProgram
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.delay

private const val TAG = "MpvOriginalPlayer"

/**
 * ★ パッチ (Komorebi-fork): v10 実験プレイヤー。
 * 録画の original 画質 (生 MPEG-2 TS) を mpv コア (libmpv) の FFmpeg
 * ソフトウェアデコードで再生する。Channels アプリが Google TV Streamer で
 * 同症状を直したのと同じ再生スタック (mpv) を使うのが狙い。
 * v9 の VLC 実験はデコード自体は通ったがカクツキが残ったため、
 * フレーム提示パイプラインの違う mpv で再検証する。
 * 割り切りは v9 と同じ: 視聴位置の保存・チャプター・コメント等は未対応。
 * 操作: 中央=再生/一時停止、左右=∓10秒/+30秒、戻る=終了。
 */
@Composable
fun MpvOriginalPlayerScreen(
    program: RecordedProgram,
    serverIp: String,
    serverPort: String,
    initialPositionMs: Long = 0,
    onBackPressed: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current

    var isBuffering by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var timeSec by remember { mutableLongStateOf(initialPositionMs / 1000) }
    var durationSec by remember { mutableLongStateOf(0L) }
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
        val mpv = remember(streamUrl) {
            MPVLib.create(context)?.also { lib ->
                // ★ 実験の核心: ハードウェアデコードを使わず、mpv 内蔵 FFmpeg の
                // ソフトウェアデコード (mpeg2video) で再生させる
                lib.setOptionString("hwdec", "no")
                lib.setOptionString("vo", "gpu")
                lib.setOptionString("gpu-context", "android")
                // インターレース放送を mpv 側でデインターレースして出力する
                lib.setOptionString("deinterlace", "yes")
                lib.setOptionString("cache", "yes")
                if (initialPositionMs > 0) {
                    lib.setOptionString("start", (initialPositionMs / 1000.0).toString())
                }
                lib.init()
            }
        }

        if (mpv != null) {
            DisposableEffect(mpv) {
                var disposed = false
                val observer = object : MPVLib.EventObserver {
                    override fun eventProperty(property: String) {}

                    override fun eventProperty(property: String, value: Long) {
                        when (property) {
                            "time-pos" -> {
                                timeSec = value
                                isBuffering = false
                            }

                            "duration" -> durationSec = value
                        }
                    }

                    override fun eventProperty(property: String, value: Double) {}

                    override fun eventProperty(property: String, value: Boolean) {
                        when (property) {
                            "pause" -> isPaused = value
                            "paused-for-cache" -> isBuffering = value
                            "eof-reached" -> if (value && !disposed) onBackPressed()
                        }
                    }

                    override fun eventProperty(property: String, value: String) {}

                    override fun event(eventId: Int) {
                        if (eventId == MPVLib.MpvEvent.MPV_EVENT_END_FILE && !disposed) {
                            onBackPressed()
                        }
                    }
                }
                mpv.addObserver(observer)
                mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
                mpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
                mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                mpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)

                onDispose {
                    disposed = true
                    try {
                        mpv.removeObserver(observer)
                    } catch (e: Exception) {
                    }
                    try {
                        mpv.destroy()
                    } catch (e: Exception) {
                    }
                }
            }

            val togglePause: () -> Unit = {
                try {
                    mpv.setPropertyBoolean("pause", !isPaused)
                } catch (e: Exception) {
                }
                pokeOsd()
            }
            val seekRelative: (Long) -> Unit = { deltaSec ->
                try {
                    mpv.command(arrayOf("seek", deltaSec.toString(), "relative"))
                } catch (e: Exception) {
                }
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
                                togglePause(); true
                            }

                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                seekRelative(-10); true
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                seekRelative(30); true
                            }

                            else -> {
                                pokeOsd(); false
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    try {
                                        mpv.attachSurface(holder.surface)
                                        mpv.command(arrayOf("loadfile", streamUrl))
                                    } catch (e: Exception) {
                                        onShowToast("mpv実験プレイヤー: 開始に失敗しました")
                                    }
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    try {
                                        mpv.detachSurface()
                                    } catch (e: Exception) {
                                    }
                                }
                            })
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
                            text = "${formatMpvTime(timeSec * 1000)} / ${formatMpvTime(durationSec * 1000)}  (mpv実験: ソフトウェアデコード)",
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
                Text(text = "mpv の初期化に失敗しました", color = Color.White)
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

private fun formatMpvTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
