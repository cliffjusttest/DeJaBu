# DeJaBu 遊戲機制完整文件

> 本文件涵蓋所有已實作的遊戲系統，包括數值公式、系統流程與玩家互動細節。

---

## 目錄

1. [玩家屬性與升等系統](#1-玩家屬性與升等系統)
2. [戰鬥系統](#2-戰鬥系統)
3. [技能系統](#3-技能系統)
4. [組隊與多人系統](#4-組隊與多人系統)
5. [夥伴（Companion）系統](#5-夥伴companion系統)
6. [裝備與物品系統](#6-裝備與物品系統)
7. [地圖與世界系統](#7-地圖與世界系統)
8. [怪物系統](#8-怪物系統)
9. [元素系統](#9-元素系統)
10. [任務系統](#10-任務系統)
11. [商店系統](#11-商店系統)
12. [網路通訊協定](#12-網路通訊協定)
13. [遊戲流程總覽](#13-遊戲流程總覽)

---

## 1. 玩家屬性與升等系統

### 七項核心屬性

| 屬性 | 代號 | 作用 |
|------|------|------|
| 武力 | Might | 普通攻擊傷害；技能武力係數 |
| 智力 | Intelligence | 技能智力係數與治療量 |
| 體力 | Vitality | 最大 HP（每 1 點 +20） |
| 防禦 | Defense | 減少受到傷害（每 1 點 -1） |
| 精神 | Spirit | 最大 MP（每 1 點 +5） |
| 幸運 | Luck | 暴擊率（依雙方幸運差）、逃跑成功率（依雙方幸運差）、捕捉成功率 |
| 敏捷 | Agility | 出手順序、連擊判斷、逃跑成功率（依雙方敏捷差） |

**屬性下限：** 0（無上限）

### 數值公式

```
最大 HP  = 體力 × 20
最大 MP  = 精神 × 5
```

### 角色創建

- 玩家在創建時分配 **10 點自由點數** 至七項屬性
- 各屬性初始值為 0，必須恰好用完 10 點

### 升等系統

| 項目 | 數值 |
|------|------|
| 最高等級 | 99 |
| 升等所需 EXP | 10 × 等級^2.22 |
| 每次升等獲得技能點 | 1 點 |
| 角色創建初始技能點 | 1 點 |

**戰鬥 EXP 計算：**
```
單位 EXP = 5 × 怪物等級 × (1 / (1 + e^((單位等級 - 怪物等級) / 10)))
獲得 EXP = Σ 各隻已擊倒怪物的 EXP（玩家以玩家等級、夥伴以夥伴等級代入）
```
- 單位等級與怪物等級差距 ≥ 20 時，該單位不獲得 EXP
- 夥伴也獨立獲得完整 EXP

---

## 2. 戰鬥系統

### 戰鬥觸發

#### 明雷（可見敵人）

- 每張地圖在 `maps.json` 設定特定的明雷出生點（彼此間距 ≥ 4 格）
- 明雷會主動追擊追擊範圍內最近的玩家（預設追擊 5 格、脫離 8 格）
- 同一時間只追擊一名玩家；若該玩家進入戰鬥，改追其他可追擊範圍內的玩家
- 接觸玩家（相鄰或同格）觸發戰鬥，怪物模板依明雷設定

#### 暗雷（危險區域）

- 特定危險區域（地圖 `dangerZones` 設定）內累計危險值
- 進入危險區域提示：「這裡充滿危險氣息...」
- 每走 1 步 +1 危險值，每場戰鬥 +3；離開危險區域重置
- 危險值越高越容易遇敵（每點 +3% 機率，上限 80%）

#### 戰鬥後冷卻（以隊長為準，組隊時全隊共用）

| 冷卻類型 | 一般戰鬥 | 危險區域戰鬥 |
|----------|----------|--------------|
| 對應明雷屏蔽 / 不可觸發 | 20 秒 | 20 秒 |
| 明雷追擊 | 5 秒 | 5 秒 |
| 明雷戰鬥 | 5 秒 | 5 秒 |
| 暗雷戰鬥 | 5 秒 | 3 秒 |

- 客戶端同步屏蔽對應明雷顯示，伺服器同步阻擋觸發

#### 戰鬥內容

- 明雷：依該明雷的 `templateId` 生成怪物，數量 1～該地圖 `maxVisibleEnemies`（預設 1）
- 暗雷：從該地圖可生成的暗雷模板中隨機選取，數量 1～該地圖 `maxDarkEnemies`（預設 3）
- 怪物等級：依怪物模板 `min_level`～`max_level` 隨機決定（不再依玩家等級偏移）
- 模板生成設定（`monster_templates` + `monster_template_spawn_maps`）：
  - `min_level` / `max_level`：等級範圍
  - `visible_spawn` / `dark_spawn`：是否可作為明雷／暗雷生成
  - `monster_template_spawn_maps`：允許生成的地圖 ID 列表

### 戰場配置（5×2 網格，共 10 格）

```
[ 0 ][ 1 ][ 2 ][ 3 ][ 4 ]    ← 上排
[ 5 ][ 6 ][ 7 ][ 8 ][ 9 ]    ← 下排
```

**玩家固定在格子 7（中心）**

**單人**

| 單位 | 格位 |
|------|------|
| 角色 | 7 |
| 夥伴（索引 0） | 2 |
| 夥伴（索引 1） | 1 |
| 夥伴（索引 2） | 3 |
| 夥伴（索引 3） | 0 |
| 夥伴（索引 4） | 4 |

**組隊**

| 隊伍索引 | 角色格 | 夥伴格 |
|----------|--------|--------|
| 隊長（0） | 7 | 2 |
| 成員 1 | 6 | 1 |
| 成員 2 | 8 | 3 |
| 成員 3 | 5 | 0 |
| 成員 4 | 9 | 4 |

組隊時每位玩家的夥伴固定在其角色正前方（角色格 - 5）。

### 回合順序

1. 所有存活我方單位**同時規劃行動**（多人模式下各自操控自己的單位）
2. 等待所有存活我方單位完成規劃後，**同步執行本回合**
3. 敵方 AI 同步決策

### 行動種類

| 行動 | 說明 |
|------|------|
| 攻擊 | 普通物理攻擊 |
| 防禦 | 本回合減傷值額外 +防禦（等同雙倍防禦減傷） |
| 逃跑 | 計算逃跑成功率後嘗試脫離戰鬥 |
| 技能 | 使用已學技能（消耗 MP，有冷卻） |
| 道具 | 使用背包中的消耗品 |
| 捕捉 | 對標記為可捕捉的野外怪物嘗試捕獲 |

### 傷害計算

**普通攻擊：**
```
原始傷害 = max(1, 武力)

減傷值   = 防禦
  （防禦狀態下：減傷值 += 防禦）
最終傷害 = max(1, 原始傷害 - 減傷值)
```
（再套用元素克制與連擊倍率等修飾）

**暴擊率：**
```
暴擊率（%） = max(0, 攻方幸運 - 守方幸運)
暴擊傷害    = 原始傷害 × 2
```

### 逃跑公式

```
逃跑成功率 = Clamp(
  0.35
  + (逃跑行動人敏捷 − 敵方最高敏捷) × 0.01
  + (逃跑行動人幸運 − 敵方最高幸運) × 0.01
  + (逃跑行動人等級 − 敵方最高等級) × 0.1,
  0.1, 0.95
)
```

### 連擊系統

- 觸發機率：50%
- 敏捷 ≥ 15 可額外獲得連擊加成
- 連擊傷害倍率：1.1×

### 敵方 AI

- 55% 機率使用技能（前提：有可用技能且 MP 足夠）
- 其餘進行普通攻擊
- 目標隨機選取存活的敵方單位
- 不使用治療技能

### 戰鬥結束條件

| 條件 | 結果 |
|------|------|
| 全部敵人被擊敗 | 勝利（獲得 EXP、掉落物品） |
| 逃跑成功 | 脫離戰鬥 |
| 玩家被擊敗 | 戰鬥結束，觸發死亡機制（見下方） |
| 全體我方陣亡 | 戰鬥失敗 |

### 死亡與醫館機制

**角色陣亡（戰鬥失敗或逃跑時 HP = 0）：**
- 損失當前等級 EXP 條的 **10%**（不會降級）
- 傳送至**最近醫館**並恢復滿 HP / MP

**夥伴戰力耗盡（戰鬥失敗或逃跑時夥伴 HP = 0，玩家 HP > 0 或同時陣亡）：**
- 夥伴損失 **10%** EXP（不會降級；HP 不為 0 的夥伴不受影響）
- **10 分鐘內**無法出戰
- 10 分鐘後須至**醫館 NPC 對話 → 治療夥伴**，才能再次設為出戰

**勝利特例：**
- 無論角色或夥伴在戰鬥中 HP 歸零，只要**最終勝利**，戰鬥結束時 HP 會恢復為 **1**，不觸發死亡機制

**醫館位置：**
| 地圖 | 醫館 | NPC |
|------|------|-----|
| 新手村 | 新手村醫館 | 村醫 |
| 幽暗森林 | 森林醫館 | 森林醫師 |

---

## 3. 技能系統

### 技能屬性

| 屬性 | 說明 |
|------|------|
| 元素 | FIRE / WIND / EARTH / THUNDER / WATER / UNIVERSAL |
| 武力係數 | 物理傷害倍率 |
| 智力係數 | 魔法傷害倍率 |
| 需求等級 | 玩家等級需達到才可學習 |
| 最高等級 | 1～5 級 |
| 冷卻回合 | 使用後需等待的回合數 |
| MP 消耗 | 每次使用消耗的 MP |
| 目標陣營 | ALLY（我方）/ ENEMY（敵方）/ ANY |
| 目標範圍 | 見下表 |
| 連擊適用 | 是否可觸發連擊 |
| 前置技能 | 需先學習的技能 |

### 目標範圍類型

| 範圍 | 說明 |
|------|------|
| SINGLE | 單體，僅鎖定目標格 |
| ROW_ADJACENT_THREE | 橫向 3 格（目標 ± 1） |
| CROSS | 橫向 3 格 + 正上/下方 1 格 |
| ROW | 整排（5 格） |
| ALL | 全場（10 格） |

### 技能傷害公式

```
等級倍率 = 1.0 + 0.15 × (技能等級 - 1)

傷害 = (武力係數 × 武力 × 等級倍率)
      + (智力係數 × 智力 × 等級倍率)
      + 隨機變動值

治療（智力係數 > 0 且無武力係數，目標我方）：
     = 智力係數 × 智力 × 0.9 × 等級倍率
```

### 學習與升級規則

- 每次學習/升級消耗 **1 技能點**
- 玩家等級需達到技能需求等級
- 所有前置技能皆需先學習
- 不可超過技能最高等級上限

### 冷卻機制

- 使用後標記冷卻，**使用當回合不計入冷卻倒數**
- 每回合結束後倒數一次
- 倒數至 0 即可再次使用

---

## 4. 組隊與多人系統

### 基本規格

| 項目 | 數值 |
|------|------|
| 最大隊伍人數 | 5 名玩家 |
| 地圖移動控制 | 僅隊長可控制整隊移動 |
| 戰鬥控制 | 各玩家只能操控自己的單位 |

### 組隊流程

1. 隊長邀請玩家 → 發送 pending invite
2. 被邀請者接受 → 加入隊伍，記錄 leaderId
3. 成員自動加入隊長的 party list

### 組隊戰鬥規則

- 所有隊員的玩家角色 + 夥伴形成統一我方陣線
- 組隊模式下每名玩家**最多帶 1 隻夥伴**參戰（單人模式最多 5 隻）
- 夥伴格位分配依照隊伍索引（見[戰場配置](#戰場配置5×2-網格共-10-格)）

### 行動規劃同步

- 每名玩家獨立規劃自己單位的行動
- UI 顯示各成員是否已完成規劃
- 等待所有**存活**我方單位都規劃完畢後統一執行

### 組隊探索與遇敵

- 僅隊長可移動；隊員透過 `PARTY_SYNC` 跟隨
- 明雷追擊、暗雷遇敵、戰鬥後冷卻均以**隊長**為準，全隊共用同一場戰鬥
- 隊長遭遇時，全隊一起進入戰鬥（僅隊長發起 `BATTLE_START`）

---

## 5. 夥伴（Companion）系統

### 夥伴基本屬性

夥伴擁有與玩家相同的七項屬性，另有：
- 獨立等級與 EXP（同玩家公式）
- 獨立 HP / MP
- 可被暱稱
- 獨立裝備欄位

### 夥伴升等成長（每升一級）

| 屬性 | 成長值 |
|------|--------|
| 武力 | +2 |
| 智力 | +1 |
| 體力 | +2（最大 HP = 體力 × 20，升級時同步） |
| 防禦 | +1 |
| 精神 | +1（最大 MP = 精神 × 5，升級時同步） |
| 幸運 | +1 |
| 敏捷 | +1 |

### 夥伴技能

- 從怪物模板繼承初始技能
- 可用技能點升級技能等級
- 各夥伴技能獨立追蹤

### 捕獲系統

- 只有標記為 `capturable` 的野外怪物可被捕捉
- 戰鬥中使用「捕捉」行動
- 成功率由伺服器計算
- 成功後在資料庫建立 `UserCompanion` 記錄

### 夥伴上陣限制

| 模式 | 最大上陣數 |
|------|------------|
| 單人 | 5 隻 |
| 組隊 | 每名玩家 1 隻 |

**夥伴在戰鬥結束後自動恢復滿血**

---

## 6. 裝備與物品系統

### 物品類型

| 類型 | 說明 |
|------|------|
| EQUIPMENT | 提升屬性的裝備 |
| CONSUMABLE | 一次性消耗品（戰鬥中或背包使用） |

### 裝備欄位（共 9 格）

`HEAD`、`FACE`、`SHOULDER`、`HAND`、`BODY`、`LEG`、`FOOT`、`BACK`、`ACCESSORY`

### 裝備屬性

每件裝備可提供以下加成（可疊加）：
- 武力 / 智力 / 體力 / 防禦 / 精神 / 幸運 / 敏捷 的加值
- 需求等級（玩家等級需達到才可裝備）
- 販售價格

**屬性無上限；裝備加成直接疊加至基礎能力值（`CharacterStats.withBonus()`）**

### 夥伴裝備

- 夥伴有獨立的裝備欄位
- 裝備加成在戰鬥計算前套用（`CharacterStats.withBonus()`）

### 消耗品

- 固定治療 HP 量
- 戰鬥中或道具欄可使用
- 背包以數量追蹤

### 商業系統

- 商店只顯示**玩家等級達到需求等級**的物品
- 賣出價格為裝備的 sell_price（0 = 不可出售）

---

## 7. 地圖與世界系統

### 座標系統

- 基於格子（Grid）的等距（Isometric）座標系
- 地圖有可行走性格子（walkability grid）
  - `.`、`P`、`=`、`@` = 可行走
  - `#`、`T` 等 = 障礙物

### 地圖資料（`maps.json`）

| 項目 | 說明 |
|------|------|
| 預設地圖 | village |
| 地圖格式 | 檔案路徑 + 顯示名稱 |
| NPC 位置 | 指定格子座標 + sprite 鍵 |
| 傳送點 | 座標 → 目標地圖 + 目標座標 |
| 明雷（`visibleEnemies`） | 出生點 id、templateId、座標、chaseRange、loseRange；間距 ≥ 4 格 |
| 明雷最大敵人數（`maxVisibleEnemies`） | 明雷戰鬥最多敵人數，預設 1，範圍 1～5 |
| 暗雷最大敵人數（`maxDarkEnemies`） | 危險區域戰鬥最多敵人數，預設 3，範圍 1～5 |
| 危險區域（`dangerZones`） | id + `rect` 矩形或 `cells` 格子列表 |

**玩家預設出生點：** village 地圖 (5, 5)

**現有地圖配置：**

| 地圖 | 明雷 | 危險區 | 明雷上限 | 暗雷上限 |
|------|------|--------|----------|----------|
| village | 無 | 無 | 1（預設） | 3（預設） |
| forest | 3 隻（2 野狼 + 1 幽影） | 3 處灌木叢 | 1 | 3 |

### 移動系統

- 8 方向輸入
- 支援點擊移動（含尋路）
- 動畫依移動方向切換（上 / 下 / 左 / 右）
- 移動前檢查目標格可行走性
- 每次換格向伺服器送 `MOVE`，收到 `MOVE_OK` 後才允許下一步

### 明雷追擊（伺服器權威）

- 玩家移動後，伺服器更新該地圖所有明雷的追擊狀態
- 選取追擊範圍內最近且可追擊的玩家（不在冷卻、未在戰鬥中）
- 每步向目標靠近一格；脫離 loseRange 或目標進入戰鬥時回到出生點
- 位置透過 `MOVE_OK` 與 `VISIBLE_ENEMY_UPDATE` 同步至所有同地圖客戶端

### 危險區域（暗雷）

- 進入危險區域：`dangerZoneEntered: true`，提示「這裡充滿危險氣息...」
- 離開危險區域：危險值重置為 0
- 區域內每步累計危險值，暗雷遇敵機率 = `min(80%, 危險值 × 3%)`

### 多人可見性

- 同地圖的其他玩家在世界上可見
- 明雷位置對所有同地圖玩家同步

---

## 8. 怪物系統

### 怪物模板

| 模板 | HP | 元素 | 可捕捉 |
|------|-----|------|--------|
| wild_wolf | 60 | WIND | 是 |
| shadow_wisp | 80 | NONE | 是 |

### 怪物屬性縮放（依等級）

```
武力 / 體力 = 基礎值 + (等級 - 1) × 2
其他屬性   = 基礎值 + (等級 - 1) × 1
```

### 掉落系統

- 金幣：`random(min, max)` 固定掉落
- 物品：每件物品有獨立掉落機率
- 依擊殺每隻怪物各自判定掉落

---

## 9. 元素系統

### 六種元素

`火（FIRE）`、`風（WIND）`、`土（EARTH）`、`雷（THUNDER）`、`水（WATER）`、`無（NONE）`

### 元素剋制關係

```
火 → 風 → 土 → 雷 → 水 → 火
（箭頭方向為剋制方）
```

### 傷害倍率

| 關係 | 倍率 |
|------|------|
| 剋制 | ×1.5 |
| 被剋 | ×0.75 |
| 中立 | ×1.0 |
| 無元素（NONE） | 永遠 ×1.0，無屬性加成或懲罰 |

### UNIVERSAL 技能

元素設為 UNIVERSAL 的技能，使用者自身元素決定屬性加成計算。

---

## 10. 任務系統

### 任務類型

目前實作：**KILL 任務**（擊殺指定怪物 X 隻）

### 任務狀態

| 狀態 | 說明 |
|------|------|
| IN_PROGRESS | 進行中，持續追蹤擊殺數 |
| COMPLETED | 已完成並領取獎勵 |

### 任務流程

1. 玩家接受任務 → 狀態設為 IN_PROGRESS
2. 每次擊殺符合條件的怪物 → 進度 +1
3. 進度 ≥ 需求數量 → 可領取獎勵
4. 領取後標記為 COMPLETED

### 任務獎勵

- 經驗值（EXP）
- 技能點
- 獎勵僅能領取一次

---

## 11. 商店系統

### 商店 NPC

- 特定 NPC 擁有商店物品清單
- 僅顯示玩家等級達到需求等級的物品

### 購買流程

1. 確認玩家金幣 ≥ 物品售價
2. 扣除金幣
3. 物品加入背包

### 出售流程

1. 篩選背包中可出售物品（sell_price > 0）
2. 扣除玩家物品數量
3. 增加對應金幣

---

## 12. 網路通訊協定

### WebSocket

- 連線端點：`ws://localhost:8080/ws/game`
- 訊息格式：`{"type": "MESSAGE_TYPE", "payload": {...}}`

### 主要 WebSocket 訊息

| 類型 | 方向 | 說明 |
|------|------|------|
| PING | 雙向 | 保持連線 |
| LOGIN | Client → Server | 驗證 token |
| MOVE | Client → Server | 玩家移動（x, y, direction, mapId） |
| BATTLE_START | Client → Server | 發起戰鬥 |
| BATTLE_ACTION | Client → Server | 規劃戰鬥行動（行動類型、目標、技能、道具） |
| NPC_INTERACT | Client → Server | 與 NPC 互動 |
| DIALOGUE_CHOICE | Client → Server | 選擇對話選項 |
| QUEST_LIST | Client → Server | 請求任務列表 |
| PARTY_INVITE | Client → Server | 邀請玩家入隊 |
| LOGIN_OK | Server → Client | 登入成功；含 `visibleEnemies`、冷卻 snapshot |
| MOVE_OK | Server → Client | 移動確認；可能含 `encounter`、`visibleEnemyId`、`fromDangerZone`、`wildMonsters`、`visibleEnemies`、`inDangerZone`、`dangerValue`、`dangerZoneEntered`、冷卻欄位 |
| VISIBLE_ENEMY_UPDATE | Server → Client | 同地圖明雷位置廣播 |
| BATTLE_RESULT | Server → Client | 回合結算；戰鬥結束時含 `visibleEnemyId`、`fromDangerZone` |
| PARTY_SYNC | Server → Client | 隊員跟隨隊長；含 `encounter`、冷卻欄位 |

**冷卻欄位**（`MOVE_OK`／`LOGIN_OK`／`PARTY_SYNC`）：`noVisibleEncounterMs`、`chaseCooldownMs`、`darkEncounterCooldownMs`、`maskedVisibleEnemies`。

### REST HTTP 端點

| 端點 | 說明 |
|------|------|
| `/api/companions/list` | 取得夥伴列表 |
| `/api/backpack/status` | 取得裝備與背包狀態 |
| `/api/backpack/equip` | 裝備物品 |
| `/api/backpack/unequip` | 卸除裝備 |
| `/api/backpack/use` | 使用消耗品 |

### 身分驗證

- 所有請求附帶 Auth Token
- 伺服器驗證 Token 並取出 User ID

---

## 13. 遊戲流程總覽

```
[登入] → [角色創建：分配 10 點屬性]
           ↓
       [世界探索：移動、與 NPC 對話]
           ↓
      ┌────┴────┐
 [明雷追擊]  [危險區暗雷]
  接觸觸發    累計危險值
      └────┬────┘
           ↓
       [戰鬥回合：規劃 → 執行 → 重複]
           ↓
      ┌────┴────┐
  [勝利]     [失敗 / 逃跑]
      ↓
  [獲得 EXP + 掉落物]
      ↓
  [戰鬥後冷卻：明雷屏蔽 / 追擊 / 遇敵]
      ↓
  [升等？→ 獲得技能點]
      ↓
  [學習/升級技能]
      ↓
  [購買裝備 / 出售物品]
      ↓
  [捕捉夥伴 → 管理夥伴]
      ↓
  [接受任務 → 推進進度 → 領取獎勵]
      ↓
  [邀請玩家組隊 → 多人探索與戰鬥]
```

---

## 附錄：關鍵程式碼位置

| 系統 | 檔案路徑 |
|------|----------|
| 屬性計算 | [CharacterStats.java](server/src/main/java/com/dejebu/game/CharacterStats.java) |
| 技能傷害 | [SkillCombatCalculator.java](server/src/main/java/com/dejebu/game/SkillCombatCalculator.java) |
| 戰鬥流程 | [BattleService.java](server/src/main/java/com/dejebu/service/BattleService.java) |
| 升等/EXP | [ProgressionService.java](server/src/main/java/com/dejebu/service/ProgressionService.java) |
| 夥伴管理 | [CompanionService.java](server/src/main/java/com/dejebu/service/CompanionService.java) |
| 裝備系統 | [EquipmentService.java](server/src/main/java/com/dejebu/service/EquipmentService.java) |
| 技能系統 | [SkillService.java](server/src/main/java/com/dejebu/service/SkillService.java) |
| 掉落系統 | [LootService.java](server/src/main/java/com/dejebu/service/LootService.java) |
| 任務系統 | [QuestService.java](server/src/main/java/com/dejebu/service/QuestService.java) |
| 商店系統 | [ShopService.java](server/src/main/java/com/dejebu/service/ShopService.java) |
| 地圖服務 | [MapService.java](server/src/main/java/com/dejebu/service/MapService.java) |
| 明雷追擊 | [VisibleEnemyService.java](server/src/main/java/com/dejebu/service/VisibleEnemyService.java) |
| 危險區域 | [DangerZoneService.java](server/src/main/java/com/dejebu/service/DangerZoneService.java) |
| 遇敵冷卻 | [EncounterCooldownService.java](server/src/main/java/com/dejebu/service/EncounterCooldownService.java) |
| 野外遭遇 | [EncounterService.java](server/src/main/java/com/dejebu/service/EncounterService.java) |
| 明雷渲染 | [visible_enemy_manager.gd](client/scripts/world/visible_enemy_manager.gd) |
| 戰鬥 UI | [battle_scene.gd](client/scripts/battle/battle_scene.gd) |
| 客戶端狀態 | [game_state.gd](client/scripts/game/game_state.gd) |
