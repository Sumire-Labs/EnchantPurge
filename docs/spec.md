# Minecraft Paper プラグイン要件定義書
## エンチャント剥がしプラグイン「Disenchanter」

- **バージョン**: 1.0.0（初版）
- **対象プラットフォーム**: Paper 1.21.11
- **Java バージョン**: Java 21（Paper 1.21.x 要件）
- **作成日**: 2026-05-14
- **開発体制**:
  - オーケストレーター: ChatGPT-5.5（全体統括・タスク分解）
  - コーダー: DeepSeek V4 Pro（実装担当）
  - エクスプローラー: DeepSeek V4 Flash（調査・検証担当）

---

## 1. プロジェクト概要

ツール・防具・エンチャント本などからエンチャントを剥がす機能を提供する Paper プラグイン。コマンドから GUI を開いて操作する形式で、個別剥がし／一括剥がしの両方に対応する。剥がしたエンチャントの扱い、コスト、対象アイテム、呪い系の挙動などはサーバー管理者が `config.yml` で柔軟に設定可能。Vault および PlaceholderAPI と任意連携し、MiniMessage 形式の多言語メッセージをサポートする。

---

## 2. 機能要件

### 2.1 Must（必須）

- `/disenchant` コマンドで GUI を開き、現在手に持っているアイテムのエンチャント一覧を表示する
- GUI 上でエンチャントアイコンを個別クリックし、1つだけ剥がせる
- GUI 上の「一括剥がしボタン」で、対象アイテムから剥がせる全エンチャントを一括除去できる
- 剥がしたエンチャントの扱いを config で `vanish` / `book` / `exp` から選択可能（デフォルト: `book`）
- コスト（経験値レベル・Vault通貨・アイテム消費）を組み合わせて設定可能、個別／一括で個別設定可
- 対象アイテムをホワイトリスト/ブラックリスト方式で制限可能
- 呪い系エンチャントの剥がし可否・追加コスト倍率を config で設定可能
- 細分化された権限ノードによるアクセス制御
- `config.yml` + `lang/<locale>.yml`（日本語・英語）のファイル構成
- MiniMessage 形式でのメッセージカスタマイズ
- `/disenchant reload` で設定再読み込み（権限制限あり）
- 操作ログのコンソール出力（config で ON/OFF）

### 2.2 Should（推奨）

- Vault が導入されていれば経済プラグイン通貨でのコスト消費
- PlaceholderAPI が導入されていればプレースホルダ提供
- 専用ログファイルへの操作記録（日付別ローテート、config で ON/OFF）
- ログフォーマットの config カスタマイズ
- エンチャント一覧の起動時キャッシュによる性能最適化
- 非同期I/O（ログ書き込み）

### 2.3 Could（あれば望ましい）

- プレイヤー個別言語の自動切替（`Player#locale()` ベース）
- MockBukkit による統合テスト
- Discord Webhook 通知連携
- カスタムエンチャント系プラグイン（EcoEnchants 等）対応
- データ永続化による統計表示・クールダウン

---

## 3. 非機能要件

### 3.1 パフォーマンス
- 中規模サーバー（同時接続50〜100人）対応
- GUI 開閉・剥がし操作: 10ms 以内
- ログI/O は非同期実行（`runTaskAsynchronously`）
- エンチャント一覧は起動時に `Registry.ENCHANTMENT` から取得・キャッシュ
- ItemStack 操作はメインスレッドで実行（Paper API 要件）

### 3.2 信頼性
- ステートレス設計（プレイヤーデータの永続化なし）
- 設定ファイル破損時はデフォルト値で起動 + 警告ログ
- Vault / PlaceholderAPI 未導入時もコア機能は動作（softdepend）

### 3.3 拡張性
- 機能別クラス分割（`CostHandler`、`EnchantmentFilter`、`MessageFormatter` 等）
- 剥がしモード（vanish/book/exp）は enum + switch で容易に追加可能

### 3.4 国際化
- サーバー全体で1言語固定（`config.yml` の `language: ja_JP` で指定）
- 日本語・英語を同梱、追加言語は `lang/*.yml` を配置するだけで対応可能

---

## 4. コマンド一覧

| コマンド | 構文 | 権限 | 説明 |
|---------|------|------|------|
| disenchant | `/disenchant` | `disenchant.use` | 手持ちアイテムのエンチャント剥がしGUIを開く |
| disenchant reload | `/disenchant reload` | `disenchant.admin.reload` | 設定ファイルを再読み込みする |
| disenchant help | `/disenchant help` | `disenchant.use` | コマンドヘルプを表示する |

### 権限ノード一覧

| ノード | デフォルト | 説明 |
|--------|-----------|------|
| `disenchant.use` | `true` | `/disenchant` の使用許可 |
| `disenchant.use.individual` | `true` | 個別剥がし操作の許可 |
| `disenchant.use.bulk` | `true` | 一括剥がし操作の許可 |
| `disenchant.use.curse` | `op` | 呪い系エンチャントの剥がし許可 |
| `disenchant.bypass.cost` | `op` | コスト消費を免除 |
| `disenchant.admin.reload` | `op` | 設定再読み込みの実行許可 |
| `disenchant.*` | `op` | 全権限の付与 |

---

## 5. 設定ファイル構造例

### 5.1 ディレクトリ構成

```
plugins/Disenchanter/
├── config.yml
└── lang/
├── ja_JP.yml
└── en_US.yml
```

### 5.2 `config.yml`

```yaml
# Disenchanter 設定ファイル

# 使用する言語ファイル（lang/ ディレクトリ内のファイル名）
language: ja_JP

# 剥がしたエンチャントの扱い
# vanish: 消滅 / book: エンチャント本に変換 / exp: 経験値に変換
removal_mode: book

# 経験値変換時のレベル係数（mode: exp の場合のみ使用）
exp_conversion:
  # エンチャントレベル × 係数 = 返却経験値（ポイント）
  multiplier: 30

# コスト設定
cost:
  # 個別剥がし時のコスト
  individual:
    exp_level: 3
    money: 100.0       # Vault 必須。0 で無効
    items:
      # - material: LAPIS_LAZULI
      #   amount: 3
  # 一括剥がし時のコスト
  bulk:
    exp_level: 10
    money: 500.0
    items: []

# 対象アイテム設定
target:
  # mode: whitelist / blacklist
  mode: blacklist
  whitelist: []
  blacklist:
    - ELYTRA
  # エンチャント本を対象に含めるか
  include_enchanted_books: false

# 呪い系エンチャントの扱い
curses:
  # 剥がし可能かどうか
  removable: true
  # 通常コストに対する倍率
  extra_cost_multiplier: 2.0

# GUI 設定
gui:
  # GUI のタイトル（MiniMessage 形式可）
  title: "<gold>エンチャント剥がし</gold>"
  # 一括ボタンの表示位置（インベントリスロット番号）
  bulk_button_slot: 49

# ログ設定
logging:
  # コンソールへの出力
  console: true
  # 専用ファイル（plugins/Disenchanter/logs/disenchant-YYYY-MM-DD.log）への出力
  file: true
  # ログフォーマット
  format: "[{time}] {player} removed {enchant} Lv.{level} from {item} (cost: {cost})"
```

### 5.3 `lang/ja_JP.yml`

```yaml
prefix: "<gray>[<gold>Disenchanter</gold>]</gray> "
messages:
  no_item_in_hand: "<red>手にアイテムを持ってください。</red>"
  no_enchantments: "<red>このアイテムにはエンチャントが付与されていません。</red>"
  item_not_supported: "<red>このアイテムは対象外です。</red>"
  remove_success_individual: "<green><enchant> Lv.<level> を剥がしました！</green>"
  remove_success_bulk: "<green>全てのエンチャント（<count>個）を剥がしました！</green>"
  insufficient_exp: "<red>経験値レベルが不足しています（必要: <required>）。</red>"
  insufficient_money: "<red>所持金が不足しています（必要: <required>）。</red>"
  insufficient_items: "<red>必要なアイテムが不足しています: <items></red>"
  curse_not_removable: "<red>呪い系エンチャントは剥がせません。</red>"
  no_permission: "<red>このコマンドを実行する権限がありません。</red>"
  config_reloaded: "<green>設定を再読み込みしました。</green>"
gui:
  bulk_button_name: "<yellow>全てのエンチャントを剥がす</yellow>"
  bulk_button_lore:
    - "<gray>クリックで一括除去</gray>"
    - "<gray>コスト: <cost></gray>"
  enchant_lore:
    - "<gray>クリックでこのエンチャントを剥がす</gray>"
    - "<gray>コスト: <cost></gray>"
```

---

## 6. イベントフロー（テキスト図）

```
[プレイヤー] ─ /disenchant 実行
       │
       ▼
[CommandExecutor]
   │ 権限チェック (disenchant.use)
   │ 手持ちアイテム取得
   │ 対象アイテム判定 (EnchantmentFilter)
   │ エンチャント有無確認
       │
       ▼
[GUIBuilder] ──→ Inventory 構築（エンチャント一覧 + 一括ボタン）
       │
       ▼
[プレイヤー] ─ スロットをクリック
       │
       ▼
[InventoryClickEvent ハンドラ]
   │ クリックスロット判定
   │   ├─ エンチャントアイコン → 個別剥がし処理
   │   └─ 一括ボタン        → 一括剥がし処理
       │
       ▼
[権限チェック]
   │ disenchant.use.individual / disenchant.use.bulk
   │ disenchant.use.curse（呪いの場合）
       │
       ▼
[CostCalculator]
   │ 必要コスト算出（呪い倍率適用）
   │ disenchant.bypass.cost を持つならスキップ
       │
       ▼
[CostHandler]
   │ 経験値・通貨(Vault)・アイテムの所持確認
   │ 不足 → エラーメッセージ送信して終了
   │ 充足 → コスト消費
       │
       ▼
[EnchantmentRemover]
   │ ItemStack#removeEnchantment() 実行
   │ removal_mode に応じた処理:
   │   ├─ vanish → 何もしない
   │   ├─ book   → EnchantedBook 生成 → プレイヤーに渡す
   │   └─ exp    → 経験値ポイント付与
       │
       ▼
[Logger（非同期）]
   │ コンソール出力 / ファイル出力
       │
       ▼
[プレイヤーへ成功メッセージ送信]
   └─ GUI を再構築または閉じる
```

---

## 7. 想定エッジケース

| # | ケース | 期待動作 |
|---|--------|---------|
| 1 | 手にアイテムを持っていない | `no_item_in_hand` メッセージを送信 |
| 2 | エンチャント無しアイテムを持っている | `no_enchantments` メッセージを送信 |
| 3 | ブラックリストに登録されたアイテム | `item_not_supported` メッセージを送信 |
| 4 | 呪いエンチャントのみ付与 + `curses.removable: false` | GUI に表示するがクリックしても剥がせない（メッセージ表示） |
| 5 | コスト不足（経験値/通貨/アイテム） | 対応する `insufficient_*` メッセージを送信、消費せず終了 |
| 6 | Vault 未導入 + `money` コスト設定あり | プラグイン起動時に警告 + `money` コストを無効化 |
| 7 | GUI 操作中にアイテムをドロップ/移動された | 操作キャンセル、整合性チェック後にエラーメッセージ |
| 8 | プレイヤーが GUI を開いたまま死亡/退出 | サーバー側で GUI を自動クローズ、ステート破棄 |
| 9 | 同一プレイヤーが連続クリック（マクロ） | 各クリックで権限・コスト・所持を都度再チェック |
| 10 | エンチャント本が対象 + `include_enchanted_books: false` | `item_not_supported` メッセージ送信 |
| 11 | エンチャント本対象化 ON で本から全エンチャント剥がした結果、本が空になる | 通常の Book にダウングレード or 消費（実装で要明示） |
| 12 | 設定ファイルが破損している | デフォルト値で起動、コンソールに警告 |
| 13 | リロード中に剥がし操作が走った | 設定の不整合を避けるため、リロード時は新規操作を一時的に拒否 |
| 14 | 呪い + 通常エンチャント混在で「一括剥がし」、`disenchant.use.curse` 権限なし | 呪い以外のみ剥がす、結果メッセージで呪いはスキップした旨を通知 |
| 15 | アイテムの耐久値・カスタムデータ | 剥がし操作で耐久・NBT・カスタムモデルデータは保持する |

---

## 8. コーダー（DeepSeek V4 Pro）向け実装ヒント

### 8.1 推奨パッケージ構成

```
com.example.disenchanter/
├── DisenchanterPlugin.java           # JavaPlugin エントリ
├── command/
│   ├── DisenchantCommand.java        # /disenchant コマンド本体
│   └── DisenchantTabCompleter.java
├── gui/
│   ├── DisenchantGUI.java            # GUI 構築
│   └── DisenchantGUIListener.java    # InventoryClickEvent ハンドラ
├── core/
│   ├── EnchantmentRemover.java       # 剥がし処理本体
│   ├── EnchantmentFilter.java        # 対象アイテム判定
│   ├── CurseDetector.java            # 呪い判定
│   └── CostCalculator.java           # コスト計算
├── cost/
│   ├── CostHandler.java              # コスト消費の統合
│   ├── ExpCost.java
│   ├── MoneyCost.java                # Vault 連携
│   └── ItemCost.java
├── config/
│   ├── ConfigManager.java
│   └── LanguageManager.java
├── integration/
│   ├── VaultIntegration.java
│   └── PlaceholderAPIIntegration.java
├── logging/
│   └── DisenchantLogger.java         # 非同期ログ出力
└── util/
    └── MessageFormatter.java         # MiniMessage 整形
```

### 8.2 重要な実装ポイント

- **MiniMessage**: Paper 1.21.11 に同梱、`MiniMessage.miniMessage().deserialize(...)` で `Component` 化
- **エンチャント取得**: `ItemStack#getEnchantments()`、エンチャント本は `EnchantmentStorageMeta#getStoredEnchants()` を使用（要分岐）
- **エンチャント削除**: `ItemMeta#removeEnchant(Enchantment)` → `ItemStack#setItemMeta(...)`
- **エンチャント本生成**: `Material.ENCHANTED_BOOK` の ItemStack → `EnchantmentStorageMeta#addStoredEnchant(...)`
- **呪い判定**: `EnchantmentTags.CURSE` を使用（`Enchantment#isCursed()` は deprecated）。詳細はエクスプローラーに確認させる
- **Registry**: `Registry.ENCHANTMENT` から全エンチャント一覧取得（起動時にキャッシュ）
- **Vault**: `softdepend`。`Bukkit.getServicesManager().getRegistration(Economy.class)` で取得
- **PlaceholderAPI**: `PlaceholderExpansion` を継承して登録
- **非同期I/O**: `Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ...)` でログ書き込み
- **GUI ステート管理**: `Map<UUID, GUISession>` でプレイヤーごとに開いている GUI の対象 ItemStack を一時保持（プラグイン無効化時にクリア）
- **plugin.yml**:
    - `api-version: '1.21'`
    - `softdepend: [Vault, PlaceholderAPI]`
    - `commands` / `permissions` セクション記載

### 8.3 ビルド設定

- **ビルドツール**: Maven または Gradle（推奨: Gradle Kotlin DSL）
- **Paper 依存追加**: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`
- **JUnit 5** をテスト依存に追加
- **GitHub Actions ワークフロー**（`.github/workflows/build.yml`）:
    - `on: [push, pull_request]`
    - JDK 21 セットアップ
    - `mvn clean package` または `./gradlew build` 実行
    - JUnit テスト実行
    - 成果物 JAR を Actions Artifacts にアップロード

### 8.4 単体テスト対象（JUnit 5）

- `CostCalculator` — コスト計算 + 呪い倍率
- `EnchantmentFilter` — ホワイト/ブラックリスト判定
- `CurseDetector` — 呪い判定
- `MessageFormatter` — プレースホルダ置換

---

## 9. エクスプローラー（DeepSeek V4 Flash）向け調査推奨項目

Paper 1.21.11 API の以下のポイントを実装前に検証してください:

1. **`EnchantmentTags.CURSE` の使い方** — `Enchantment#isCursed()` が deprecated になっており、Paper 1.21 系での呪い判定のベストプラクティスを確認。Tag API のパスと `Registry.ENCHANTMENT.getTag(...)` の構文を調査
2. **`Registry.ENCHANTMENT` の安定性** — 1.21.x で `Enchantment` クラスが `keyed` 化されており、列挙方法が旧 API と異なる。`Registry.ENCHANTMENT.iterator()` の使用例を確認
3. **`EnchantmentStorageMeta` の挙動** — エンチャント本の `getStoredEnchants()` と通常アイテムの `getEnchants()` の違い、本から全エンチャントを除去した際の挙動（空の本になるか自動的に通常Bookになるか）を確認
4. **MiniMessage の Paper 同梱状況** — `net.kyori.adventure.text.minimessage.MiniMessage` がシェードなしで利用可能か、推奨される取得方法（`MiniMessage.miniMessage()`）を確認
5. **`Player#locale()` の戻り値型** — `Locale` か `String` か、Paper 1.21 系の現行仕様を確認（将来の C 拡張用）
6. **Vault API バージョン互換性** — Paper 1.21.11 環境で動作する Vault（または Vault unlocked）の最新バージョンを確認
7. **PlaceholderAPI** — Paper 1.21.11 対応の最新版バージョンと `PlaceholderExpansion` 登録方法を確認
8. **`InventoryClickEvent` のキャンセル挙動** — GUI 内でのドラッグ防止に必要なイベント（`InventoryDragEvent` も含む）と `setCancelled(true)` のタイミング
9. **`ItemStack#removeEnchantment` の戻り値とエラー処理** — エンチャントが存在しない場合の挙動
10. **MockBukkit の Paper 1.21 対応状況** — 将来的な統合テスト導入を見据えて、現在のサポートバージョンを確認
11. **plugin.yml vs paper-plugin.yml** — Paper 1.21 で推奨される `paper-plugin.yml` 形式を使うべきか、従来の `plugin.yml` で十分か確認

---

## 10. 動作確認チェックリスト（手動テスト用、20項目）

実装完了後、以下のチェックリストに沿ってサーバー上で動作確認を実施してください。

### 基本動作
- [ ] 1. `/disenchant` を実行すると GUI が開く
- [ ] 2. エンチャント付きツールを持って `/disenchant` を実行すると、エンチャント一覧が GUI に表示される
- [ ] 3. GUI 上のエンチャントアイコンをクリックすると、そのエンチャントが個別に剥がせる
- [ ] 4. GUI 上の「一括剥がしボタン」をクリックすると、全エンチャントが一括で剥がせる
- [ ] 5. `/disenchant reload` で設定が再読み込みされる

### 剥がし後の処理（removal_mode）
- [ ] 6. `removal_mode: book` の場合、剥がしたエンチャントの本がプレイヤーに渡される
- [ ] 7. `removal_mode: vanish` の場合、エンチャントが完全に消滅する
- [ ] 8. `removal_mode: exp` の場合、レベルに応じた経験値が付与される

### コスト
- [ ] 9. 経験値レベル不足時に剥がせず、エラーメッセージが表示される
- [ ] 10. Vault 通貨が不足している場合に剥がせず、エラーメッセージが表示される
- [ ] 11. 必要アイテムを持っていない場合に剥がせず、エラーメッセージが表示される
- [ ] 12. `disenchant.bypass.cost` 権限保持者はコストなしで剥がせる
- [ ] 13. 個別／一括でコストが個別に適用される（config の値どおり）

### 対象アイテム・呪い
- [ ] 14. ブラックリストに登録した Material は対象外メッセージが表示される
- [ ] 15. エンチャント本は `include_enchanted_books: true` の時のみ対象になる
- [ ] 16. `curses.removable: false` の場合、呪いはクリックしても剥がせない
- [ ] 17. `curses.removable: true` + `extra_cost_multiplier: 2.0` 時、呪い剥がしのコストが2倍になる

### 権限・連携
- [ ] 18. `disenchant.use` 権限を剥奪したプレイヤーは `/disenchant` を実行できない
- [ ] 19. Vault 未導入時もプラグインが起動し、`money` コストが無効化される旨の警告が出る
- [ ] 20. 言語切替（`language: en_US`）でメッセージが英語に変わる

---

## 11. 開発スケジュール提案（参考）

| フェーズ | 担当 | 内容 |
|---------|------|------|
| 1. 調査 | エクスプローラー | 第9章の調査項目を完了 |
| 2. スケルトン実装 | コーダー | パッケージ構成・plugin.yml・config.yml の雛形・GitHub Actions の設定 |
| 3. コア機能実装 | コーダー | コマンド・GUI・剥がし処理・コスト処理 |
| 4. 拡張機能実装 | コーダー | Vault/PlaceholderAPI 連携・ログ機能・MiniMessage 対応 |
| 5. 単体テスト | コーダー | JUnit テスト追加 |
| 6. 動作確認 | オーケストレーター | 第10章のチェックリストで検証 |
| 7. リリース | オーケストレーター | バージョンタグ付け・JAR 公開 |

---

**以上が本プラグインの要件定義書です。**

実装を開始する前に、エクスプローラーによる第9章の調査が完了していることを推奨します。
