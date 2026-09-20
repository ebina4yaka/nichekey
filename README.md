# NicheKey (Android 日本語ローマ字キーボード)

nix flakes + Gradle + Kotlin で作る Android IME。ローマ字入力前提の日本語キーボード。

## 開発環境

```sh
nix develop          # JDK17 / Gradle / Android SDK(35) が入る
# direnv なら: echo "use flake" > .envrc && direnv allow

gradle :app:testDebugUnitTest   # 変換エンジンのテスト
gradle :app:assembleDebug       # app/build/outputs/apk/debug/
gradle :app:installDebug        # 接続中の端末へインストール
```

## エミュレータでデバッグ

```sh
nix develop
nohup emulator -avd jp -gpu swiftshader_indirect -no-boot-anim -no-snapshot > /tmp/emulator.log 2>&1 &
adb wait-for-device && adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable dev.example.nichekey/.NicheImeService
adb shell ime set   dev.example.nichekey/.NicheImeService
adb logcat -s NicheKey          # IMEのログ（入力バッファ・変換候補）
adb exec-out screencap -p > /tmp/s.png   # スクリーンショット
```

AVD 「jp」が無い場合は作成:

```sh
echo no | avdmanager create avd --force --name jp --package "system-images;android-35;google_apis;x86_64" --device pixel_6
```

再インストールで IME の選択が外れたら、`ime set` をやり直す。

## インストール

配布用の APK は [GitHub Releases](https://github.com/ebina4yaka/nichekey/releases) にある。ダウンロードして端末で開く。
リリースに添付しているのは debug ビルドなので、自分で使う分にはそのままインストールできる。

## 使い方

1. 端末の「設定 → システム → 言語と入力 → 画面キーボード」で NicheKey を有効化する
2. 入力ボックスでキーボードを切り替え、NicheKey を選ぶ
3. ランチャーアイコンを開き、配列とテーマを変更する

キーの動き:

- 文字キー: ローマ字がバッファに溜まる。確定できる部分から順にかなで表示される
- 空白/変換: 読みを漢字かな交じり文の候補に変換し、押すたびに候補を巡回する
- 確定: 変換中の文字列をコミットする
- 英字 / かな: 英字入力モードとローマ字入力モードを切り替える

## 対応配列（一次情報から採取）

| 配列 | 出典 |
| --- | --- |
| Eucalyn（決定版） | [eucalyn.hatenadiary.jp](https://eucalyn.hatenadiary.jp/entry/about-eucalyn-layout) |
| Eucalyn改（biacco42） | [scrapbox.io/self-made-kbds-ja](https://scrapbox.io/self-made-kbds-ja/Eucalyn改配列) |
| 大西 | [o24.works/layout](https://o24.works/layout/) 公式 karabiner.json |
| Tomisuke | [tomisuke.com](https://tomisuke.com/tomisuke-keyboard-layout/) 公式 JIS 配列 |
| Dvorak(JP) | 標準 Dvorak |

## 機能

- 数字キー: 全モードの最上段に数字行。ローマ字変換せず直接コミットする
- 英字入力: 英字キーで英字入力モードに切替（選択中の配列のまま）。かなキーでローマ字入力に戻る
- シフト: ⇧ キーで次の1キーが大文字英字として直接コミットされる（英語入力用）
- 記号ページ: ?123 キーで数字・記号ページに切替（直接コミット）。あA で戻る
- 長音「ー」: ローマ字入力中の `-` キー、または記号ページ最終段の「ー」で入力する
- カタカナ: 変換候補の巡回にカタカナとひらがなを含む
- キーリピート: ⌫ 長押しで連続削除（400ms 後 50ms 間隔）
- 配列選択: 設定画面（ランチャーアイコン）からのみ変更できる。キーボード上からは変更できない
- カラーテーマ: 設定画面でカラーのスウォッチをタップして変更（ダーク/ライト/ブルー/ピンク）。次にキーボードを表示したときから反映される

## 変換エンジン（Mozc辞書）

かな→漢字変換には [Mozc](https://github.com/google/mozc) の OSS 辞書（IPAdic ベース、BSD-3-Clause / IPAdic ライセンス）を使う。
事前に変換した約 63MB・108万語のバイナリをアプリに同梱し、Kotlin 実装の Viterbi + A* で N-best を生成する。
スコアには単語コストと品詞接続コスト（bigram）の両方を使う。接続コストは connection_single_column.txt 由来の
2672×2672 行列で、助詞や活用のつながりの自然さを反映する。

再生成手順:

```sh
mkdir -p third_party/mozc
for i in 00 01 02 03 04 05 06 07 08 09; do
  curl -sfL -o third_party/mozc/dictionary$i.txt \
    https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/dictionary$i.txt
done
curl -sfL -o third_party/mozc/connection_single_column.txt \
  https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/connection_single_column.txt
python3 tools/build_mozc_dict.py   # → app/src/main/assets/mozc_dict.bin
```

## 構成

- `.../Composition.kt` — ローマ字→かなの構成状態（確定かな + 未確定ローマ字）。変換は WanaKana に委譲する
- `.../Layouts.kt` — 配列データ + カラーテーマ定義 (SharedPreferences)
- `.../MozcDict.kt` — Mozc辞書バイナリの読込・検索（二分探索）
- `.../Converter.kt` — Viterbi + A* による N-best 変換・ひらがな→カタカナ
- `.../Candidates.kt` — 候補生成（辞書N-best + カタカナ + ひらがな）
- `.../KeyboardView.kt` — 自前描画のキーボード View（シフト、記号ページ、キーリピート、システムジェスチャー領域の回避）
- `.../NicheImeService.kt` — InputMethodService 本体
- `.../SettingsActivity.kt` — 配列選択・テーマ選択
- `tools/build_mozc_dict.py` — Mozc TSV → バイナリ辞書変換スクリプト
