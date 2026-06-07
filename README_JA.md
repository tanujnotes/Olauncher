<div align="center">
  <h1>QALAUNCHER</h1>
  <p><strong>ミニマルで美しいAndroidホームスクリーン</strong></p>
  <p>
    <img src="https://img.shields.io/badge/version-v1.0.3--beta-blue" alt="Version v1.0.3-beta" />
    <img src="https://img.shields.io/badge/minSdk-24-brightgreen" alt="minSdk 24" />
    <img src="https://img.shields.io/badge/Kotlin-Android-7F52FF" alt="Kotlin Android" />
  </p>
</div>

---

## 🪟 概要

QALAUNCHER は、ミニマルでクリーンなAndroidホームスクリーンランチャーです。縦一列のアプリリストとアルファベットインデックスで、アプリを素早く見つけて起動できます。余計なものを削ぎ落としたデザインで、スマートフォンとの向き合い方を変えます。

olauncher をベースに、独自のUI/UX改善とパフォーマンス最適化を施したフォークです。

## ✨ 特徴

### 🏠 ホーム画面
- **縦一列アプリリスト** — お気に入りのアプリをクリーンな縦リストで表示
- **アルファベットインデックス（A-Z, #）** — 画面右端（または左端）に常設。ドラッグで波アニメーション、タップでアプリ一覧へ
- **最大8個のアプリスロット** — 各スロットにアイコン・カスタムラベル・ショートカットを設定可能
- **フォント選択** — Noto Sans Thin / Noto Sans Regular / Noto Serif から選択可能

### 🎨 操作・ジェスチャー
| 操作 | 動作 |
|------|------|
| 上 / 左 / 右スワイプ | アプリ一覧を開く |
| 下スワイプ | 通知領域を開く（検索に変更可） |
| 時計タップ | アラームアプリを開く |
| 日付タップ | カレンダーを開く |
| 時計/日付ロングタップ | 任意のアプリに変更 |
| 空スロットロングタップ | アプリを割り当て |
| ホーム画面ロングタップ | 設定を開く |
| ダブルタップ | 端末をロック（ロックモードON時） |

### 🔍 アプリ一覧（ドロワー）
- **アルファベットインデックスナビゲーション** — ドラッグで文字を選択、該当位置へスクロール
- **カスケード表示アニメーション** — アプリ一覧がスムーズに出現
- **検索バー** — 必要な時だけ使う控えめな検索
- **アプリ情報 / アンインストール / 非表示** — ロングタップでオプション表示
- **アプリ名の変更** — 任意のラベルに変更可能

### 🖼️ カスタマイズ
- **ライト / ダーク / システムテーマ** — 自動切り替え対応
- **文字サイズ調整** — 0.5倍〜1.5倍（タブレットは2.0倍まで）
- **ステータスバー表示/非表示**
- **日時表示のON/OFF/日付のみ**
- **アプリ配置の左右中央揃え**
- **インデックス左右切り替え** — 左手操作対応
- **Hidden apps** — アプリを非表示に

### 🖼️ 壁紙
- **デイリー壁紙** — WorkManagerで自動更新（4時間ごと）
- **カスタム壁紙設定**

### 🔐 ロック機能
- **デバイス管理ロック** — ダブルタップロック（Android 8以下）
- **アクセシビリティロック** — アクセシビリティサービス経由（Android 9以降）
- **ホームボタンで最近のアプリ** — スマートな操作

## 📱 スクリーンショット

> _準備中_

```
┌──────────────────────┐
│          12:34       │  ← シンプルな時計
│       6月6日(木)     │  ← 日付
│                      │
│  📱 メッセージ    A  │  ← アプリリスト
│  📱 設定          B  │     アルファベット
│  📱 Chrome        C  │     インデックス
│  📱 +             D  │
│                      │
│ JL                   │  ← ブランド刻印
└──────────────────────┘
```

## 🛠️ ビルド方法

### 必要条件
- **Android Studio** Hedgehog (2023.1.1) 以降
- **JDK 17** 以降
- **Android SDK** 35

### 手順

```bash
# リポジトリをクローン
git clone https://github.com/kubobeem/jlancher.git
cd jlancher

# デバッグAPKをビルド
./gradlew assembleDebug

# APKの出力先:
# app/build/outputs/apk/debug/app-debug.apk
```

### インストール

```bash
# ADB経由（端末をUSB接続）
adb install -r app/build/outputs/apk/debug/app-debug.apk

# またはAPKを端末に転送してインストール
```

## 🚀 使い方

1. **APKをインストール**
2. **デフォルトランチャーに設定**（設定 → アプリ → デフォルトアプリ → ホームアプリ）
3. **空スロット（+）をロングタップ** してアプリを割り当て
4. **ホーム画面をスワイプ** してアプリ一覧を開く
5. **アルファベットインデックスをタップ/ドラッグ** してアプリ一覧を開く
6. **下スワイプ** で通知領域を開く（設定で検索に変更可）
7. **ホーム画面をロングタップ** で設定を開く

### ホーム画面スロット

| 操作 | 動作 |
|------|------|
| アプリアイコンをタップ | アプリを起動 |
| 空スロット（+）をタップ | 「ロングタップでアプリを選択」と表示 |
| スロットをロングタップ | アプリ選択画面を開く |
| 時計 / 日付をタップ | 時計 / カレンダーを開く |
| 時計をロングタップ | 時計アプリをリセット/変更 |
| 日付をロングタップ | カレンダーアプリをリセット/変更 |

## ⚙️ 設定

**ホーム画面をロングタップ** で設定を開きます。

| 設定項目 | 内容 |
|---------|------|
| ホームアプリ数 | 0〜8個のスロット |
| アプリ配置 | 左 / 中央 / 右 / 下寄せ |
| ステータスバー | 表示 / 非表示 |
| 日時表示 | 表示 / 非表示 / 日付のみ |
| テーマ | ライト / ダーク / システム |
| 文字サイズ | 0.5倍〜1.5倍（タブレット2.0倍） |
| キーボード自動表示 | On / Off |
| 下スワイプ動作 | 通知領域 / 検索 |
| デイリー壁紙 | On / Off（4時間ごと自動更新） |
| フォント | Noto Sans Thin / Noto Sans / Noto Serif |
| インデックス位置 | 右 / 左 |
| ロックモード | On / Off |
| 非表示アプリ | 管理 |

## 📁 プロジェクト構成

```
app/src/main/java/app/olauncher/
├── data/
│   ├── AppModel.kt              # アプリデータモデル
│   ├── Constants.kt             # 定数・フラグ
│   └── Prefs.kt                 # 設定保存
├── helper/
│   ├── Utils.kt                 # ユーティリティ関数
│   ├── WallpaperWorker.kt       # デイリー壁紙ワーカー
│   └── ...
├── listener/
│   ├── OnSwipeTouchListener.kt  # ジェスチャー検出
│   └── ...
├── ui/
│   ├── HomeFragment.kt          # ホーム画面
│   ├── AppDrawerFragment.kt     # アプリ一覧
│   ├── SettingsFragment.kt      # 設定画面
│   ├── NiagaraHomeAdapter.kt    # ホームアダプター
│   └── AppDrawerAdapter.kt      # ドロワーアダプター
├── MainActivity.kt
└── MainViewModel.kt

app/src/main/res/
├── drawable/
│   ├── badge_circle.xml         # 通知バッジ
│   ├── letter_popup_bg.xml      # レターポップアップ
│   └── haikei.jpg               # デフォルト壁紙
├── layout/
│   ├── fragment_home.xml        # ホーム画面レイアウト
│   ├── fragment_app_drawer.xml  # アプリ一覧レイアウト
│   ├── fragment_settings.xml    # 設定レイアウト
│   └── adapter_*.xml            # RecyclerViewアダプター
├── values/
│   ├── styles.xml               # ライトテーマ & スタイル
│   ├── colors.xml               # カラーパレット
│   └── strings.xml              # 文字列リソース
└── values-night/
    └── styles.xml               # ダークテーマ
```

## 🧩 依存関係

- **Kotlin** + AndroidX (AppCompat, RecyclerView, Lifecycle, Navigation)
- **WorkManager** — デイリー壁紙更新
- **Material Components**

## 📄 ライセンス

本プロジェクトは [olauncher](https://github.com/tanujnotes/Olauncher) をベースとしたフォークであり、オリジナルのライセンスに従います。

---

<div align="center">
  <p>美しいミニマリズムを求めるあなたへ</p>
  <p><sub>QALAUNCHER v1.0.3-beta</sub></p>
</div>
