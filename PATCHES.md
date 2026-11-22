# PATCHES.md — このフォークのパッチ一覧

本家 [BeerEgg2001/Komorebi](https://github.com/BeerEgg2001/Komorebi) に対する個人用フォーク
（Hiroki0101/Komorebi）の差分を記録するファイル。本体 README は本家のまま無改造。

- ベース： 本家 v1.1.1-Stable（versionCode 19）時点の main（6f20e8b）
- 方針： パッチは機能単位でコミットを分け、問題があればその機能だけ戻せるようにする
- 検証環境： Google TV Streamer（32bit 実行環境のため armeabi-v7a 版 APK を使用）、
  バックエンドは KonomiTV

## v1： 孤児レコードの自動スイープ（コミット 26e8c24）

サーバー側で削除された録画が、アプリ内の録画一覧に「ゴースト」として残る問題への対策。

- 15分間隔の定期同期（smartSync）の末尾で、サーバー側の全録画 ID とローカル DB を
  照合し、サーバーに無いものをローカル DB からも削除する
- 変更ファイル： `data/sync/RecordSyncEngine.kt` のみ
- 安全策（いずれかに該当したら何も消さない）：
  1. 全ページ取得が完了したときだけ削除する（通信エラー時は中断）
  2. サーバー申告の総件数と取得件数が一致しない場合は削除しない
  3. 削除対象がローカル件数の半分を超える場合は異常とみなし削除しない
     （大掃除は従来どおり手動の完全同期に委ねる）
- 状態： ビルド・実機動作とも確認済み（2026-11-22）

## v2.1： お気に入りチャンネルのホーム常駐（コミット 38c70e8 〜 93f97e3）

- ホーム先頭の「前回視聴したチャンネル」行の左端に、お気に入りチャンネルを常駐表示する
  - 行全体は「お気に入り＋視聴履歴（重複除去）」で最大15件
  - お気に入りカードには星バッジ。見出しは、お気に入りがあるとき
    「お気に入り・最近のチャンネル」に変わる
  - ライブタブのチャンネルカードにも星バッジ
- 登録は設定 → ホーム表示設定 →「お気に入りチャンネル」の多選リストのみ（上限8件、
  登録順＝表示順）。局が一覧から消えたら表示からも自動で消える（登録データ自体は残り、
  復活すれば戻る）
- 保存先： DataStore キー `favorite_channels` に `Channel.uniqueId`
  （`"networkId_serviceId"`）の登録順 JSON 配列
- v2.1 の経緯： 当初はカードの長押しでも登録できるようにしたが、中央キーの長押しは
  本体のグローバル処理（MainRoot の onPreviewKeyEvent）が AI コンシェルジュ起動に
  使っており競合するため、長押しは撤去して設定登録のみにした
- 変更ファイル（11件）： `data/repository/SettingsRepository.kt`、
  `viewmodel/HomeViewModel.kt`、`viewmodel/SettingsViewModel.kt`、
  `ui/setting/SettingState.kt`、`ui/setting/SettingScreen.kt`、
  `ui/setting/SettingContents.kt`、`ui/home/HomeLauncherScreen.kt`、
  `ui/home/HomeContents.kt`、`ui/home/LiveContents.kt`、
  `ui/home/components/HomeSections.kt`、`ui/home/components/HomeCards.kt`
- 状態： ビルド・実機動作とも確認済み（2026-11-22）

## v3： 孤児スイープのオンオフ＋見える化（コミット 6b58232 〜 22dfad8）

- 設定 → 一般 → データベース情報に2行を追加
  - 「消えた録画を自動で一覧から削除」ON/OFF（既定 ON ＝ v1 従来の挙動）。
    OFF の間は全ページ走査自体をスキップする
  - 「最終クリーンアップ」： 最後にスイープが走った日時と結果
    （「削除 ◯件」／安全策が働いた回は「安全策で見送り」／未実行なら「未実行」）
- 保存先： DataStore キー `orphan_sweep_enabled`（真偽）、
  `orphan_sweep_last_run_at` / `orphan_sweep_last_deleted`（文字列）、
  `orphan_sweep_last_aborted`（真偽）
- 変更ファイル（5件）： `data/sync/RecordSyncEngine.kt`、
  `data/repository/SettingsRepository.kt`、`ui/setting/SettingState.kt`、
  `ui/setting/SettingContents.kt`、`ui/setting/SettingScreen.kt`
- 状態： 実機確認待ち

## v4： お気に入り局の「次の番組」をヒーローに表示

- ホームの「お気に入り・最近のチャンネル」行で、お気に入りカードにフォーカスしたとき、
  ヒーローの説明文末尾に次の番組を1行追加する（例：`次: 19:00〜 『番組名』`）
- お気に入り以外のカードは従来どおり。次の番組データが無い局では何も追加しない
- データは既存の `Channel.programFollowing`（全バックエンドで取得済み）を使う。
  時刻は `HH:mm` 固定表記
- 変更ファイル（1件）： `ui/home/components/HomeSections.kt`
- 状態： 実機確認待ち

## v5： 録画再生の先読みバッファ制限（150MB → 48MB）

- 背景： Komorebi の録画再生は先読み上限が 150MB あり、再生開始やシークのたびに
  最大 150MB をディスク上限速度（実測約 110MB/s）で一気に読む。このバーストが
  録画用 HDD（SMR）の書き込みと衝突すると読みが数秒待たされ、再生の停止や
  シーク後の待ちとして見えることがある（KonomiTV の Web 再生やライブでは発生しない）
- 変更： `VideoPlayerManager` の `setTargetBufferBytes` を 48MB に、
  `setBufferDurationsMs` の最大を 120 秒 → 60 秒に。48MB でも地デジ約23秒／
  BS約16秒分のクッションがあり、数秒級の渋滞吸収には十分という判断
- 影響範囲は録画再生のみ（ライブと SMB 再生は別プレイヤーで無関係）
- 変更ファイル（1件）： `ui/video/player/VideoPlayerManager.kt`
- 状態： 実機確認待ち

## ビルド・更新手順

1. Android Studio でこのフォークを開き、Update Project（Ctrl+T、Merge を選択）で
   最新を取り込む
2. Build → Generate APKs でビルド
3. 視聴端末に合う APK を上書きインストールする
   （Google TV Streamer は armeabi-v7a 版）
- 前提： `app/src/main/res/font/` の notosansjp_* フォント6ファイル
  （Type.kt が `R.font.*` で参照するため実ファイルが必須）
- 署名の注意： 自前ビルドは debug 署名。公式 APK とは署名が違うため初回のみ
  アンインストールが必要だった（実施済み）。以後の自前ビルド同士は上書き可だが、
  署名の元になる `C:\Users\hkoik\.android\debug.keystore` を失うと署名が変わり、
  再インストール＋接続設定の再入力になる。**このファイルはバックアップしておくこと**

## 本家の新版が出たら

パッチは自動では新版に乗らない。このファイルのパッチ一覧を地図にして、
新版のコードへ同じ変更を入れ直す必要がある。
