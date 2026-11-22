package com.beeregg2001.komorebi.common

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ★ パッチ (Komorebi-fork): v10 mpv 実験プレイヤーの到達ステップ記録。
 * ネイティブ側で落ちるとプロセスごと消えるため、ステップを SharedPreferences に
 * 同期書き込み (commit) しておき、再起動後に設定画面で「どこまで進んだか」を読めるようにする。
 */
object MpvExperimentLog {
    private const val PREFS = "mpv_experiment"
    private const val KEY = "log"

    fun record(context: Context, step: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val time = SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date())
            val lines = (prefs.getString(KEY, "") ?: "").split("\n").filter { it.isNotBlank() }
            val next = (lines + "$time $step").takeLast(10).joinToString("\n")
            prefs.edit().putString(KEY, next).commit()
        } catch (e: Exception) {
            // 記録の失敗で本処理を止めない
        }
    }

    fun readAll(context: Context): String =
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        } catch (e: Exception) {
            ""
        }

    fun readLast(context: Context): String =
        readAll(context).lines().lastOrNull { it.isNotBlank() } ?: ""
}
