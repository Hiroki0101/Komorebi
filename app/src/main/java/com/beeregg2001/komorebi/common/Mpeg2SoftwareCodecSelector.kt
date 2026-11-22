package com.beeregg2001.komorebi.common

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
 * ソフトウェアデコーダが 1 つも無い端末では元の候補をそのまま返し、
 * 再生不能にはしない (その場合は従来動作になる)。
 * MPEG-2 以外の形式には一切干渉しない。
 */
object Mpeg2SoftwareCodecSelector : MediaCodecSelector {
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
        return software.ifEmpty { infos }
    }
}
