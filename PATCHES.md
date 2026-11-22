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
- 状態： ビルド・実機確認済み（2026-11-22）

## v4： お気に入り局の「次の番組」をヒーローに表示

- ホームの「お気に入り・最近のチャンネル」行で、お気に入りカードにフォーカスしたとき、
  ヒーローの説明文末尾に次の番組を1行追加する（例：`次: 19:00〜 『番組名』`）
- お気に入り以外のカードは従来どおり。次の番組データが無い局では何も追加しない
- データは既存の `Channel.programFollowing`（全バックエンドで取得済み）を使う。
  時刻は `HH:mm` 固定表記
- v4.1 修正： 当初は説明文の末尾に足す方式だったが、ヒーローの説明文は最大3行で
  切り捨てられるため実機で見えなかった（2026-11-22 にユーザー報告）。
  `HomeHeroInfo.nextProgramText` という独立フィールドに変え、ヒーロー内に
  専用の1行（アクセント色・太字）で必ず表示する方式に変更
- v4.2 修正： この環境では KonomiTV のチャンネルAPIが `program_following` を
  返していないらしく（EPG には全チャンネルの番組が揃っていることをユーザーが確認）、
  データ自体が届いていなかった。ホームが保持している共有EPGから「次の番組」の
  索引（`HomeViewModel.nextProgramByChannelKey`、キー＝networkId_serviceId）を
  作り、API の following が無い場合の補完に使うよう変更（API 優先は維持）
- 変更ファイル（3件）： `ui/home/components/HomeSections.kt`、
  `ui/home/components/HomeHeroDashboard.kt`、`viewmodel/HomeViewModel.kt`
- 状態： 実機確認待ち（v4.2）

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

## v6： おまかせ再生（ランダム再生）

- 仕様（2026-11-22 にユーザーと設計確定）：
  - 入口は2か所。ホーム先頭の横長カードと、録画一覧トップバーの「おまかせ」ボタン。
    どちらも `RecordViewModel.requestOmakase()` を呼ぶだけで、選出結果は
    MainRootScreen が拾って再生を開始する（開始時にトーストで番組名を表示）
  - 選出は端末内DBから（`data/OmakaseSelector.kt`）。録画中とキーフレーム未解析
    （一覧からも再生不可）は常時除外。条件は段階的に緩める：
    ジャンル一致＋視聴済み除外 → ジャンル不問＋視聴済み除外 → 全部
  - ジャンルは `EpgGenre.major` への部分一致（ピックアップと同じ方式）。
    既定は「バラエティ」。「見終わった」は再生位置が全体の9割以上
  - 途中まで見た番組は続きから再生。見終わったものが選ばれた場合は最初から
  - 前回のおまかせ選出は、候補が2件以上ある場合に限って次回の候補から外す
  - 連続再生はなし。おまかせ経由の再生（`MainRootState.omakaseSession`）だけ、
    再生が最後まで終わったら自動でプレイヤーを閉じてホームタブへ戻る
    （プレイヤーに終了通知コールバックを追加。途中で戻ればフラグは解除される）
  - ホームでは、おまかせカードが常設の最上段になる（初期フォーカスと
    上キー→タブ移動もカードが引き受ける）
  - 設定は 設定→一般→「おまかせ再生」セクション：対象ジャンル（選択ダイアログ、
    既定バラエティ）と視聴済み除外 ON/OFF（既定 ON）。
    保存キー： `omakase_genre` / `omakase_exclude_watched` / `omakase_last_program_id`
- 変更ファイル（16件）： `data/OmakaseSelector.kt`（新規）、
  `data/repository/SettingsRepository.kt`、`viewmodel/RecordViewModel.kt`、
  `viewmodel/HomeViewModel.kt`、`ui/main/MainRootState.kt`、`ui/main/MainRootScreen.kt`、
  `ui/video/player/VideoPlayerManager.kt`、`ui/video/player/VideoPlayerScreen.kt`、
  `ui/home/HomeContents.kt`、`ui/home/components/HomeSections.kt`、
  `ui/video/RecordListState.kt`、`ui/video/components/RecordScreenTopBar.kt`、
  `ui/video/RecordListScreen.kt`、`ui/setting/SettingState.kt`、
  `ui/setting/SettingContents.kt`、`ui/setting/SettingScreen.kt`
- 状態： ビルド・実機確認済み（2026-11-22、ランダム再生の成功をユーザーが確認）

## v8: MPEG-2 ソフトウェアデコード（BS の映像の乱れ対策）

- 背景： Google TV Streamer で BS の無変換（MPEG-2）視聴中、動きの激しい
  場面で映像が乱れてそのまま固まる（音声は正常、チャンネル切替で復旧、
  地デジでは発生しない）。切り分けの結果、録画再生（ファイル経路）でも
  再現し、TVTest（PC のソフトデコード）では無事なことから、端末の
  MPEG-2 ハードウェアデコーダが BS の高ビットレートのピークで破綻
  していると判断した（release ビルドでも発生し、debug 負荷説は消えた）。
  さらに公式 APK を Streamer に入れた実機テストでも再現したため、
  フォーク／パッチ／ビルド起因ではないことが確定した（2026-11-22）。
  Fire TV Stick（公式 APK）では再現しなかった
- 内容： `common/Mpeg2SoftwareCodecSelector`（新規）。MPEG-2 映像のとき
  だけ、デコーダ候補からハードウェアデコーダを外してソフトウェア
  デコーダのみを返す。ソフトウェアデコーダが無い端末では元の候補を
  そのまま返し、再生不能にはしない。MPEG-2 以外には干渉しない
- 適用先： ライブ（`LivePlayerFactory`、メイン／デュアル両方）と録画再生
  （`VideoPlayerManager`）の両プレイヤー。設定が ON のときだけ
  `setMediaCodecSelector` で差し込む
- 設定： 設定→一般に「映像デコード」セクションを追加し、
  「MPEG-2をソフトウェアデコード」ON/OFF（既定 ON）。保存キーは
  DataStore の `mpeg2_software_decode`
- 注意： ソフトウェアデコードは CPU 負荷と発熱が上がる。コマ落ちする
  ようなら設定で OFF に戻す（従来動作）。録画再生側はプレイヤー生成時に
  設定値を読むため、変更は次の再生から反映される
- 変更ファイル： `common/Mpeg2SoftwareCodecSelector.kt`（新規）、
  `data/repository/SettingsRepository.kt`、`viewmodel/SettingsViewModel.kt`、
  `viewmodel/LivePlayerViewModel.kt`、`ui/live/LivePlayerFactory.kt`、
  `ui/video/player/VideoPlayerManager.kt`、`ui/setting/SettingState.kt`、
  `ui/setting/SettingContents.kt`、`ui/setting/SettingScreen.kt`
- 状態： v8 初版は実機で「変わらず乱れる」（2026-11-22）。端末にソフト
  MPEG-2 デコーダが無く、安全策フォールバックで元の HW デコーダに戻って
  いた可能性が高い。v8.1 でフォールバックを外し（ソフト候補が無ければ
  空を返して同梱 FFmpeg 拡張へ落とす）、デコーダ候補と実際に使われた
  デコーダ名をログ（タグ `Mpeg2SwSelector` / `KomorebiV8`）へ出す診断を
  入れた。FFmpeg 側のライブラリに mpeg2video デコーダが含まれることは
  AAR の実物で確認済み。再確認待ち
- v8.1 実機結果： BS の局を選ぶと画面が真っ暗になった（HW 経路は外れた
  が、その先で映像が出ない）。有線接続ではワイヤレスデバッグが維持
  できず logcat を見られないため、v8.2 で診断を設定画面内表示に変更：
  `common/Mpeg2DecodeDiagnostics`（プロセス内メモリ）に候補と使用
  デコーダを記録し、設定→一般「映像デコード」セクションに
  「MPEG-2デコーダ候補」「うちソフトウェア」「使用中のデコーダ」の
  表示行（フォーカス不可）を追加した。再確認待ち
- v8.2 実機結果（確定）： Streamer の MPEG-2 デコーダは
  `c2.mtk.mpeg2.decoder` の1個のみで、ソフトウェアデコーダは存在
  しない。受け皿のはずの FFmpeg 拡張は、ネイティブライブラリには
  mpeg2video が含まれるものの、Java 側（FfmpegLibrary）の対応表が
  H.264／HEVC のみで MPEG-2 を受け付けない（依存 AAR の中身を直接
  確認）。よってこの端末ではアプリ側から使えるソフトデコード経路が
  存在せず、v8 の方針は Streamer では成立しないと結論。v8.3 で
  セレクタのフォールバック（ソフト候補が無ければ元の候補を返す）を
  復活させ、従来動作に戻した（トグルと診断表示は温存。ソフト
  デコーダを持つ端末では引き続き有効）。BS の無変換視聴は Fire TV
  Stick／BenQ 側で、Streamer では変換画質で見るのが現実解
- 補足（FFmpeg 拡張を掘った最終結論）： Jellyfin 製 FFmpeg 拡張は
  映像側が実質スタブだった。ExperimentalFfmpegVideoRenderer は
  supportsFormat が常時非対応・createDecoder が null を返し、
  FfmpegVideoDecoder クラス自体が AAR に存在しない。ネイティブ
  ライブラリの JNI も音声専用で、映像デコード用関数は無い
  （mpeg2video の文字列は FFmpeg 本体側のもの）。「対応表に MPEG-2 を
  足せば動く」類の話ではなく、この部品で MPEG-2 映像を回す道はない

## ビルド・更新手順

1. Android Studio でこのフォークを開き、Update Project（Ctrl+T、Merge を選択）で
   最新を取り込む
2. **release ビルドで作る**（Build → Generate Signed App Bundle or APK、
   署名は debug.keystore のまま）。debug ビルドは UI が全体的にもっさりする
   （2026-11-22 に実機で確認： release 化で改善）。署名が同じなので上書き可
3. 視聴端末に合う APK を上書きインストールする
   （Google TV Streamer は armeabi-v7a 版）
- 前提： `app/src/main/res/font/` の notosansjp_* フォント6ファイル
  （Type.kt が `R.font.*` で参照するため実ファイルが必須）
- 署名の注意： 自前ビルドは debug 署名。公式 APK とは署名が違うため初回のみ
  アンインストールが必要だった（実施済み）。以後の自前ビルド同士は上書き可だが、
  署名の元になる `C:\Users\hkoik\.android\debug.keystore` を失うと署名が変わり、
  再インストール＋接続設定の再入力になる。**このファイルはバックアップしておくこと**

## v9: original 画質を VLC で再生（実験）【撤去済み】

- 状態: 2026-11-23 にユーザーの判断で v10 とともに実験系を全撤去。
  後継は v11（MPEG-2 は変換画質で再生する Streamer 用トグル）。
  以下は記録として残す。
- 実機確認済み（2026-11-22）。前に乱れた BS 録画が乱れ・固まりなく
  再生できた。仮説（FFmpeg ソフトデコードなら MT8696 で MPEG-2 1080i が
  通る）は実証。残課題はカクツキ（v9.1 で対策中）。発熱は長時間再生で
  なければ許容範囲との実機感想
- v9.1: カクツキ対策として、SMB 画面由来のフレーム破棄系オプション
  （--drop-late-frames / --skip-frames / --avcodec-hurry-up）を除去。
  実機結果: カクツキは残った（フレーム破棄は主因ではなかった）
- v9.2: 残る容疑2つをまとめて対策。(1) クロック平滑化を殺していた
  --clock-jitter=0 / --clock-synchro=0 を除去（VLC 既定に戻す）。
  (2) デインターレースを有効化（:deinterlace=1、
  :deinterlace-mode=yadif2x で 59.94p 出力）。実機確認待ち
- 背景: v8 の結論（Streamer にはアプリから使える MPEG-2 ソフトデコーダが
  MediaCodec にも FFmpeg 拡張にも無い）を受け、Channels アプリで同症状
  （Google TV Streamer + MPEG-2 で映像だけ固まる）がプレイヤー側の
  ソフトデコード切替で直った前例に基づく実験。Komorebi 同梱の libVLC は
  FFmpeg 系デコーダを内蔵し MediaCodec を経由しない
- 仕様: 設定→一般→映像デコード「original画質をVLCで再生 (実験)」
  （キー `vlc_original_experiment`、既定 OFF）。ON の間は録画再生が常に
  original（`/api/videos/{id}/download` の生 TS）を新規の最小プレイヤー
  `VlcOriginalPlayerScreen` で再生する。核心は Media の
  `setHWDecoderEnabled(false, false)`（ハードウェアデコード無効化）
- 操作: 中央=再生/一時停止、左右=-10秒/+30秒、戻る=終了、再生終了で自動で閉じる
- 割り切り（実験のため未対応）: 視聴位置の保存、チャプター、コメント、
  画質切替、字幕/音声切替、ミニプレイヤー、おまかせ再生の終了後ホーム復帰
- 変更ファイル: data/repository/SettingsRepository.kt（キー/Flow 追加）、
  viewmodel/SettingsViewModel.kt（StateFlow 追加）、
  ui/setting/SettingState.kt（prefs 項目追加）、
  ui/setting/SettingScreen.kt（配線+フォーカスリクエスタ[0][11]追加）、
  ui/setting/SettingContents.kt（トグル行追加、MPEG-2 トグルの down を付け替え）、
  ui/video/player/VlcOriginalPlayerScreen.kt（新規）、
  ui/main/MainRootScreen.kt（selectedProgram 分岐の振り分け）

## v10: 実験プレイヤーのエンジンを mpv に差し替え【撤去済み】

- 状態: 2026-11-23 にユーザーの判断で実験系を全撤去（プレイヤー画面・
  実験トグル・libmpv 依存・packaging の pickFirsts・マニフェストの
  overrideLibrary・実験ログ表示をすべて戻した。jniLibs に置いた
  libc++_shared.so 2ファイルはローカル配置のため手元で削除する）。
  v10.1 の統計では displayFps が取れず display-resample が効いて
  いない疑いが残ったが、追及は打ち切り。以下は記録として残す。
- 実機確認済み（2026-11-22、BS 録画が乱れ・固まりなく再生できた。
  カクツキは VLC より改善したが完全な滑らかさではない）
- v10.1: 残るカクツキ対策として video-sync=display-resample を追加し、
  15秒間隔で再生統計（frame-drop / mistimed / vo-delayed / avsync /
  display・container fps）を実験ログへ記録するようにした
- v10.2: v10.1 の実機統計で estimated-display-fps が null（libmpv が画面
  レートを自動検出できず display-resample が効いていない疑い）と判明した
  ため、Android 側で検出した実リフレッシュレートを display-fps-override
  で明示的に渡すようにした。あわせて統計の drop に15秒あたりの増加分を
  併記（起動直後の一括ドロップと継続ドロップを1行で見分けるため）
- 経緯の訂正: 当初「上流 Media3 の decoder_ffmpeg を自前ビルドすれば
  本物の FFmpeg 映像デコーダが手に入る」と見込んだが、Media3 1.7.1 の
  実ソースを確認したところ、上流の ExperimentalFfmpegVideoRenderer 自体が
  未実装のスタブだった（javadoc に "not yet functional"、
  supportsFormat は常時非対応、createDecoder は null を返し、
  ffmpeg_jni.cc に映像用 JNI は存在しない）。Google は Media3 で FFmpeg
  映像デコードを実装したことがなく、この路線は存在しないと確定
- 代替として、Channels が同症状を直したのと同じ mpv コアを採用。
  当初 Maven Central の `io.github.midasvo:libmpv:1.3.0` を使ったが、
  この系統（midasvo 1.1.0〜1.3.0、jdtech 1.1.0）はすべて
  minCompileSdk=37 で、compileSdk 35 の本プロジェクトではビルド不可と判明
  （2026-11-22 実機ビルドで checkReleaseAarMetadata エラー）。
  そのため要件の緩い `dev.jdtech.mpv:libmpv:0.5.1`（libmpv-android 系の
  旧版、静的 API 版）に変更。実物で mpeg2video の含有を確認済み。
  あわせて、libmpv と libVLC が両方同梱する libc++_shared.so の重複を
  packaging の pickFirsts で解決（mergeReleaseNativeLibs 対策）。
  ただし pickFirsts では libVLC 側の古い libc++ が勝ってしまい、
  libmpv.so が要求する std::to_chars(float) シンボルが無く
  UnsatisfiedLinkError になることが実機で判明（2026-11-22）。
  対策として libmpv 0.5.1 同梱の libc++_shared.so を
  app/src/main/jniLibs/<abi>/ に配置して最優先で読ませる
  （この2ファイルはバイナリのため Git 管理外、zip で別途配置する）。
  実効ライセンスは GPL 系（個人フォークのため実害なし）
- 仕様: v9 の実験トグルの行き先を VLC 画面から新規
  `MpvOriginalPlayerScreen` に差し替え（VLC 画面のコードは温存）。
  mpv のオプションは hwdec=no（ソフトウェアデコード固定）、vo=gpu、
  gpu-context=android、deinterlace=yes、cache=yes。
  操作・割り切りは v9 と同一
- 変更ファイル: app/build.gradle.kts（libmpv 依存追加）、
  ui/video/player/MpvOriginalPlayerScreen.kt（新規）、
  ui/main/MainRootScreen.kt（振り分け先の差し替え）、
  ui/setting/SettingContents.kt（トグル表記を mpv に変更）

## v11: MPEG-2 は変換画質で再生する（Streamer 用トグル）

- 状態: 実装済み・実機未確認（2026-11-23）
- 背景: Google TV Streamer の MPEG-2 ハードウェアデコードが壊れている
  問題への恒常対策。端末側でどうにかする実験（v8〜v10）を打ち切り、
  MPEG-2 録画だけサーバー（KonomiTV）側の変換配信に逃がす方針にした
- 仕様: 設定→一般→映像デコードにトグル「MPEG-2は変換画質で再生
  (Streamer用)」（キー `mpeg2_transcoded_playback`、既定 OFF）。
  ON のとき、VideoPlayerViewModel.fetchAvailableQualities の KONOMITV
  判定で、録画の videoCodec が MPEG-2 なら original を「番組固有の
  除外」（perProgramExcludedQualities）に倒す。既存の仕組みにより、
  既定画質が original のままでも再生時は 1080p-60fps（変換）へ
  フォールバックし、設定値そのものは書き換わらない。録画中・非 TS
  コンテナの除外判定、他バックエンド、H.264/H.265 録画には影響しない。
  設定は端末ごとなので Streamer でだけ ON にする想定
- ライブ対応（2026-11-23 追加 → 同日 v11.1 でチャンネル単位化）:
  当初は録画のみで、ユーザーの指摘でライブにも広げた。当初版はライブ
  全チャンネル一律だったが、ユーザーの案でチャンネル単位の選択式に変更。
  設定→一般→映像デコードに「変換対象チャンネル」行を追加し、
  お気に入り登録と同じ多選ダイアログで選ぶ。保存は uniqueId の JSON
  配列（キー `mpeg2_transcode_channels`）。未保存（キー自体が無い）の
  ときは BS 全チャンネルを既定の対象とし、保存済みの空配列は「対象なし」
  として区別する。LivePlayerScreen 側で、トグル ON かつ表示中チャンネル
  が対象のときだけ VM の画質一覧から original を差し引いて使う
  （ザッピングにも追随）。このとき保存済みライブ画質が original でも
  設定値の書き換えはしない（トグルを切れば元に戻る）。二画面表示は
  元々 original が強制ダウングレードされるため対象判定はメイン画面の
  チャンネル基準で割り切る。録画側は v11 のままコーデック判定
- 変更ファイル: data/repository/SettingsRepository.kt、
  viewmodel/SettingsViewModel.kt、viewmodel/VideoPlayerViewModel.kt、
  ui/live/LivePlayerScreen.kt、
  ui/setting/SettingState.kt、ui/setting/SettingScreen.kt、
  ui/setting/SettingContents.kt

## 本家の新版が出たら

パッチは自動では新版に乗らない。このファイルのパッチ一覧を地図にして、
新版のコードへ同じ変更を入れ直す必要がある。
