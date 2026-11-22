package com.beeregg2001.komorebi.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ★ パッチ (Komorebi-fork): v8.2
 *
 * MPEG-2 デコードの切り分け情報を設定画面に表示するために保持する。
 * 有線接続だとワイヤレスデバッグが維持できず logcat を見られない環境
 * でも、テレビ画面だけで状態を確認できるようにするための仕掛け。
 * プロセス内メモリのみ (永続化しない) なので、再生した同じセッションの
 * 中で設定画面を開いたときに値が見える。
 */
object Mpeg2DecodeDiagnostics {
    data class State(
        val allCandidates: String = "",
        val softwareCandidates: String = "",
        val usedDecoder: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun recordCandidates(all: List<String>, software: List<String>) {
        _state.update {
            it.copy(
                allCandidates = all.joinToString(", ").ifEmpty { "なし" },
                softwareCandidates = software.joinToString(", ").ifEmpty { "なし" }
            )
        }
    }

    fun recordUsedDecoder(where: String, decoderName: String) {
        _state.update { it.copy(usedDecoder = "$where: $decoderName") }
    }
}
