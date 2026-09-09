# JpKeyboard (Android 日本語ローマ字キーボード)

nix flakes + Gradle + Kotlin で作る Android IME。ローマ字入力前提の日本語キーボード。

## 開発環境

```sh
nix develop          # JDK17 / Gradle / Android SDK(35) が入る
# direnv なら: echo "use flake" > .envrc && direnv allow

gradle :app:testDebugUnitTest   # ローマ字変換のテスト
gradle assembleDebug            # app/build/outputs/apk/debug/
gradle installDebug             # 接続中の端末へインストール
```

## エミュレータでデバッグ

```sh
nix develop
nohup emulator -avd jp -gpu swiftshader_indirect -no-boot-anim -no-snapshot > /tmp/emulator.log 2>&1 &
adb wait-for-device && adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable dev.example.jpkeyboard/.JpImeService
adb shell ime set   dev.example.jpkeyboard/.JpImeService
adb logcat -s JpIme             # IMEのログ（入力バッファ・変換候補）
adb exec-out screencap -p > /tmp/s.png   # スクリーンショット
```

AVD 「jp」が無い場合は作成:

```sh
echo no | avdmanager create avd --force --name jp --package "system-images;android-35;google_apis;x86_64" --device pixel_6
```

再インストール後は IME 選択が外れることがあるので `ime set` を再実行する。

## 使い方

1. 端末の「設定 → システム → 言語と入力 → 画面キーボード」で JpKeyboard を有効化
2. 入力ボックスでキーボード切替 → JpKeyboard を選択
3. ランチャーアイコンで配列の選択・カスタム配列(JSON)の編集

操作: キー入力→ローマ字バッファへ（自動でかな表示）/ **空白/変換** → かな→漢字候補を巡回 / **確定** → コミット / **英字** ⇄ **かな** で英字入力モード切替。配列は設定画面から選択。

## 対応配列（一次情報から採取）

| 配列 | 出典 |
| --- | --- |
| QWERTY | 標準 |
| Eucalyn（決定版） | eucalyn.hatenadiary.jp/entry/about-eucalyn-layout |
| Eucalyn改（biacco42） | scrapbox.io/self-made-kbds-ja/Eucalyn改配列 |
| 大西 | o24.works/layout/ 公式 karabiner.json |
| Tomisuke | tomisuke.com/tomisuke-keyboard-layout/ 公式 JIS 配列 |
| Dvorak(JP) | 標準 Dvorak |

## 機能

- **英字入力**: **英字** キーで英字パレットに切替。最上段が数字キー、英字・数字は直接コミット（⇧ で大文字）。**かな** でローマ字入力に戻る
- **シフト**: ⇧ キーで次の1キーが大文字英字として直接コミット（英語入力用）
- **記号ページ**: ?123 キーで数字・記号ページに切替（直接コミット）。あA で戻る
- **カタカナ**: 変換候補の巡回にカタカナ・ひらがなを含む
- **キーリピート**: ⌫ 長押しで連続削除（400ms 後 50ms 間隔）
- **配列選択**: 設定画面（ランチャーアイコン）からのみ変更できる（キーボード上からは不可）

## 変換エンジン（Mozc辞書）

かな→漢字変換は [Mozc](https://github.com/google/mozc) の OSS 辞書（IPAdic ベース、BSD-3-Clause / IPAdic ライセンス）を
コンパクトなバイナリ（約 45MB・108万語）に変換し、Kotlin 実装の Viterbi + A* で N-best を生成する。

再生成手順:

```sh
mkdir -p third_party/mozc
for i in 00 01 02 03 04 05 06 07 08 09; do
  curl -sfL -o third_party/mozc/dictionary$i.txt \
    https://raw.githubusercontent.com/google/mozc/master/src/data/dictionary_oss/dictionary$i.txt
done
python3 tools/build_mozc_dict.py   # → app/src/main/assets/mozc_dict.bin
```

## カスタム配列

設定画面で JSON を編集・保存（保存時に検証）:

```json
{"name":"独自","rows":[["q","w","e","r","t","y","u","i","o","p"],["a","s","d","f","g","h","j","k","l"],["z","x","c","v","b","n","m",",","."]]}
```

`rows` は上段→下段の各行のキー出力文字。

## 構成

- `app/src/main/java/.../Layouts.kt` — 配列データ + カスタム配列の保存/読込(SharedPreferences+JSON)
- `.../Romaji.kt` — ローマ字→かな（貪欲最長一致、促音・拗音・n処理）
- `.../MozcDict.kt` — Mozc辞書バイナリの読込・検索（二分探索）
- `.../Converter.kt` — Viterbi + A* による N-best 変換・ひらがな→カタカナ
- `.../Candidates.kt` — 候補生成（辞書N-best + カタカナ + ひらがな）
- `tools/build_mozc_dict.py` — Mozc TSV → バイナリ辞書変換スクリプト
- `.../KeyboardView.kt` — 自前描画のキーボード View（シフト/記号ページ/リピート/ジェスチャー領域回避）（システムジェスチャー領域を避ける）
- `.../JpImeService.kt` — InputMethodService 本体
- `.../SettingsActivity.kt` — 配列選択・カスタム配列エディタ

# nichekey
