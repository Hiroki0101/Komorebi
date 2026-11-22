package com.beeregg2001.komorebi.common

import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * ★ パッチ (Komorebi-fork): v8
 *
 * MPEG-2 映像について、ハードウェアデコーダを候補から外し、ソフトウェア
 * デコーダだけを返す MediaCodecSelector。
 *
 * 背景: Google TV Streamer で BS の無変換 (MPEG-2) 視聴中、動きの激しい
 * 場面で映像が乱れてそのまま固まる (音声は正常、チャンネル切替で復旧、
 * 地デジでは発生しない、録画再生でも再現) ため、端末の MPEG-2 ハード
 * ウェアデコーダが BS の高ビットレートのピークで破綻していると切り分けた。
 *
 * ソフトウェアデコーダが 1 つも無い端末では空の候補を返し、拡張
 * レンダラ (同梱の FFmpeg デコーダ) へ再生を落とす。それも対応して
 * いない場合は MPEG-2 の再生自体がエラーになるため、その場合は
 * 設定で OFF に戻すこと (v8.1 で安全策のフォールバックを外したのは、
 * 「変わらない」のか「ソフトでも乱れる」のかを切り分けるため)。
 * MPEG-2 以外の形式には一切干渉しない。
 *
 * 候補の一覧は切り分け用にログ (タグ: Mpeg2SwSelector) へ出す。
 */
object Mpeg2SoftwareCodecSelector : MediaCodecSelector {
    private const val TAG = "Mpeg2SwSelector"

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )
        if (mimeType != MimeTypes.VIDEO_MPEG2) return infos
        val software = infos.filter { !it.hardwareAccelerated }
        Log.i(
            TAG,
            "MPEG-2 decoders: all=${infos.map { it.name }} software=${software.map { it.name }}"
        )
        // ★ パッチ (Komorebi-fork): v8.2 設定画面でも見られるよう診断ホルダへも記録
        Mpeg2DecodeDiagnostics.recordCandidates(
            infos.map { it.name },
            software.map { it.name }
        )
        return software
    }
}
