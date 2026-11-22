package com.beeregg2001.komorebi.data

import com.beeregg2001.komorebi.data.local.dao.RecordedProgramDao
import com.beeregg2001.komorebi.data.local.entity.RecordedProgramEntity
import kotlin.random.Random

// ★ パッチ (Komorebi-fork): おまかせ再生の選出ロジック。
// 端末内DBの録画から候補を段階的に絞ってランダムに1本選ぶ。
// 候補が枯れたら条件を緩める:
//   1段目: ジャンル一致 (+ 設定時は視聴済み除外)
//   2段目: ジャンル不問   (+ 設定時は視聴済み除外)
//   3段目: ジャンル不問 + 視聴済み含む
// 録画中とキーフレーム未解析 (録画一覧からも再生できない) は常時除外する。
// 前回のおまかせ選出は、候補が2件以上ある場合に限って候補から外す。
object OmakaseSelector {

    data class Pick(
        val entity: RecordedProgramEntity,
        // 続きから再生する位置 (秒)。見終わったものは 0 (最初から)
        val resumePositionSec: Double
    )

    // 「見終わった」判定: 再生位置が全体の9割以上
    fun isWatched(entity: RecordedProgramEntity): Boolean =
        entity.videoDuration > 0 && entity.playbackPosition >= entity.videoDuration * 0.9

    suspend fun pick(
        dao: RecordedProgramDao,
        genre: String,
        excludeWatched: Boolean,
        lastPickedId: Int?
    ): Pick? {
        val all = dao.getAllPrograms().filter { !it.isRecording && it.hasKeyFrames }
        if (all.isEmpty()) return null

        fun dropLastPicked(candidates: List<RecordedProgramEntity>): List<RecordedProgramEntity> =
            if (lastPickedId != null && candidates.size > 1) {
                candidates.filter { it.id != lastPickedId }.ifEmpty { candidates }
            } else {
                candidates
            }

        val stages: List<(RecordedProgramEntity) -> Boolean> = listOf(
            { e ->
                (genre == "すべて" || e.genres?.any { it.major.contains(genre) } == true) &&
                    (!excludeWatched || !isWatched(e))
            },
            { e -> !excludeWatched || !isWatched(e) },
            { true }
        )
        for (stage in stages) {
            val candidates = dropLastPicked(all.filter(stage))
            if (candidates.isNotEmpty()) {
                val picked = candidates[Random.nextInt(candidates.size)]
                val resume = if (isWatched(picked)) 0.0 else picked.playbackPosition
                return Pick(picked, resume)
            }
        }
        return null
    }
}
