# DeJaBu 遊戲機制完整文件

> 本文件涵蓋所有已實作的遊戲系統，包括數值公式、系統流程與玩家互動細節。  
> **個人主線時間線**（玩家進度不同步、組隊跟隊長）的完整規範見 [§7.5](#75-主線章節與個人時間線) — 後續擴充章節請優先遵循該節。

---

## 目錄

1. [玩家屬性與升等系統](#1-玩家屬性與升等系統)
2. [戰鬥系統](#2-戰鬥系統)
3. [技能系統](#3-技能系統)
4. [組隊與多人系統](#4-組隊與多人系統)
5. [夥伴（Companion）系統](#5-夥伴companion系統)
6. [裝備與物品系統](#6-裝備與物品系統)
7. [地圖與世界系統](#7-地圖與世界系統)
7.5. [主線章節與個人時間線](#75-主線章節與個人時間線)
8. [怪物系統](#8-怪物系統)
9. [元素系統](#9-元素系統)
10. [任務系統](#10-任務系統)
11. [商店系統](#11-商店系統)
12. [網路通訊協定](#12-網路通訊協定)
13. [遊戲流程總覽](#13-遊戲流程總覽)

---

## 1. 玩家屬性與升等系統

### 七項核心屬性

戰鬥中使用的屬性為 **基礎值 + 裝備加成**（`CharacterStats.withBonus()`）。

| 屬性 | 代號 | 作用 |
|------|------|------|
| 武力 | Might | 普攻 `max(1, 武力)`；技能 `武力係數 × 武力 × 等級倍率` |
| 智力 | Intelligence | 技能 `智力係數 × 智力 × 等級倍率`；治療 `智力係數 × 智力 × 0.9 × 等級倍率` |
| 體力 | Vitality | 最大 HP = 體力 × 20 |
| 防禦 | Defense | 百分比減傷（每 1 點 −0.1%）；防禦姿態有效防禦 ×2；減傷上限 75% |
| 精神 | Spirit | 最大 MP = 精神 × 5 |
| 幸運 | Luck | 暴擊率（攻−守）；逃跑 +1%/點差；捕捉 +0.8%/點；連擊配對機率 |
| 敏捷 | Agility | 出手順序（高者先）；連擊配對需敏捷差 ≤ 15；逃跑 +1%/點差 |

**屬性下限：** 0（無上限）

### 各屬性公式

#### 體力（Vitality）

```
最大 HP = 體力 × 20
```

#### 精神（Spirit）

```
最大 MP = 精神 × 5
```

#### 武力（Might）

**普通攻擊原始傷害（暴擊／元素／連擊／防禦前）：**

```
原始傷害 = max(1, 武力)
```

**技能傷害 — 武力項：**

```
等級倍率 = 1.0 + 0.15 × (技能等級 − 1)
武力項   = 武力係數 × 武力 × 等級倍率
```

#### 智力（Intelligence）

**技能傷害 — 智力項：**

```
智力項 = 智力係數 × 智力 × 等級倍率
```

**治療技能（`effectType = HEAL`）：**

```
回復量 = max(5, round(智力係數 × 智力 × 等級倍率 × 0.9) + 隨機(0～3))
```

#### 防禦（Defense）

**百分比減傷（套用於普攻與技能傷害的最後一步）：**

```
有效防禦 = 防禦
若本回合為「防禦」姿態：有效防禦 = 防禦 × 2

減傷率 = min(75%, 有效防禦 × 0.1%)
最終傷害 = max(1, round(傷害 × (1 − 減傷率)))
```

- 750 防禦（或防禦姿態下 375 防禦）即達減傷上限 75%。

#### 幸運（Luck）

**暴擊率（普攻與技能共用）：**

```
暴擊率(%) = max(0, 攻方幸運 − 守方幸運)
判定：random(0～99) < 暴擊率 → 暴擊
暴擊時：當次原始傷害 × 2（在元素倍率之前套用）
```

**逃跑成功率：**

```
逃跑成功率 = Clamp(
  0.35
  + (行動人敏捷 − 敵方存活單位最高敏捷) × 0.01
  + (行動人幸運 − 敵方存活單位最高幸運) × 0.01
  + (行動人等級 − 敵方存活單位最高等級) × 0.10,
  0.10, 0.95
)
```

**捕捉成功率（僅玩家；等級差 ≤ 10 才可嘗試）：**

```
等級差 = |玩家等級 − 怪物等級|
hpFactor = 1 − (怪物當前 HP / 怪物最大 HP)

捕捉率 = Clamp(
  0.18 + hpFactor × 0.52 + 玩家幸運 × 0.008 − 等級差 × 0.015,
  0.05, 0.92
)
```

**連擊配對機率（每次獨立判定；幸運取被配對加入的單位）：**

```
連擊機率 = Clamp(
  0.50
  + (我方平均等級 − 敵方平均等級) × 0.05
  + max(0.10, 配對單位幸運 × 0.001),
  0.00, 0.95
)
```

（敵方連擊時，平均等級的角色對調。）

#### 敏捷（Agility）

**回合出手順序：**

```
所有存活單位依「敏捷由高到低」排序
同敏捷時：unitId 較小者先行動
```

**連擊配對門檻（硬性條件，非機率）：**

```
| 連擊領隊敏捷 − 候選單位敏捷 | ≤ 15
```

### 基礎數值公式

```
最大 HP = 體力 × 20
最大 MP = 精神 × 5
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
| 每次升等獲得屬性點 | 5 點（可自由分配至七項屬性） |
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
| 防禦 | 本回合有效防禦 ×2（等同雙倍防禦減傷率） |
| 逃跑 | 計算逃跑成功率後嘗試脫離戰鬥 |
| 技能 | 使用已學技能（消耗 MP，有冷卻） |
| 道具 | 使用背包中的消耗品 |
| 捕捉 | 對標記為可捕捉的野外怪物嘗試捕獲 |

### 傷害計算

普攻與技能共用同一套後續修飾，差別只在「原始傷害」的計算方式。

**1. 原始傷害**

普通攻擊：
```
原始傷害 = max(1, 武力)
```

技能：
```
原始傷害 = round(武力項 + 智力項)
原始傷害 = max(1, 原始傷害 + 隨機(−1～3))
```

**2. 暴擊（幸運）**
```
暴擊率(%) = max(0, 攻方幸運 − 守方幸運)
若暴擊：原始傷害 = 原始傷害 × 2
```

**3. 元素克制**
```
克制（火>風>土>雷>水>火）：× 1.1
被克：× 0.9
同屬性／任一方為「無」：× 1.0

元素後傷害 = max(1, round(原始傷害 × 元素倍率))
```

**4. 連擊倍率（若為連擊）**
```
傷害 = round(元素後傷害 × 1.1)
```

**5. 防禦減傷**
```
有效防禦 = 防禦（防禦姿態下 × 2）
減傷率   = min(75%, 有效防禦 × 0.1%)
最終傷害 = max(1, round(傷害 × (1 − 減傷率)))
```

**6. 元素特性（各 5% 機率觸發）**

| 元素 | 效果 |
|------|------|
| 火 | 最終傷害 × 1.05 |
| 土 | 受到的最終傷害 × 0.95 |
| 雷 | 暴擊率 +5%（加在幸運差計算後） |
| 風 | 若與前一個出手的對手敏捷差 < 50，本回合出手順序優先於該對手 |
| 水 | 行動結束後回復 5% 最大 HP 與 5% 最大 MP |

- 火／雷：攻擊方造成傷害時判定（含普攻與傷害技能）
- 土：守方受到傷害時判定
- 風：每回合建立出手順序時判定
- 水：該單位完成本回合行動後判定

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

**觸發條件（除機率外）：**
- 同目標、連續行動窗口、行動可連擊（普攻或 `combo_eligible` 技能）
- 相鄰候選單位的**敏捷差距 ≤ 15**（門檻值，非「敏捷 ≥ 15 才有加成」）

**配對／延伸機率（每次獨立判定）：**
```
連擊機率 = Clamp(
  0.5
  + (發動方平均等級 − 對方平均等級) × 0.05
  + max(0.1, 配對候選單位幸運值 × 0.001),
  0, 0.95
)
```
- 我方連擊：以我方平均等級對敵方平均等級，幸運取**被配對加入的候選單位**
- 敵方連擊：以敵方平均等級對我方平均等級，幸運取**被配對加入的候選怪物**

- 連擊傷害倍率：1.1×
- **連擊擊殺 EXP 加成**：參與連擊並擊殺怪物的單位，在共享 + 擊殺獎勵之外，額外獲得該怪物 EXP 的 **10%**（非連擊擊殺者無此加成）

### 敵方 AI

- 55% 機率使用技能（前提：有可用技能、MP 足夠，且存在有效目標）
- 其餘進行普通攻擊
- 攻擊技能：隨機選取存活的我方單位
- 治療技能：僅對範圍內**已損血**的存活隊友施放
- 復活技能：僅對範圍內**已倒下**的隊友施放
- 輔助技能：僅對範圍內**尚未持有任何 buff** 的存活隊友施放（避免覆蓋既有 buff 或重複施加）
- 若某技能沒有有效目標，改嘗試其他可用技能；皆無有效目標時改為普攻

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

**醫館位置（13 座，節錄）：**
| 地圖 | 醫館 | NPC |
|------|------|-----|
| 許縣 | 許縣醫廬 | `hospital_xuchang` |
| 洛陽 | 太醫署 | `hospital_luoyang` |
| 長安 | 長安醫館 | `hospital_changan` |
| 成都 | 成都醫館 | `hospital_chengdu` |
| 建業 | 建業醫館 | `hospital_jiankang` |

完整列表見 [WORLD_MAP.md](WORLD_MAP.md) §9。

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

### 組隊與主線時間線（Story Era）

組隊時，**隊長的個人主線章節**（`story_era`）與任務進度決定全隊的「有效時間線」。詳見 [§7.5 主線章節與個人時間線](#75-主線章節與個人時間線)。

| 行為 | 組隊規則 |
|------|----------|
| NPC 對話起始節點 | 依**隊長**的任務狀態與 `story_era` 解析 |
| 誰能按 F 對話 | **僅隊長**；隊員嘗試會收到錯誤提示 |
| 對話內容同步 | 隊長收到 `NPC_INTERACT_OK`；隊員收到 `PARTY_DIALOGUE_SYNC`（旁聽模式，無選項） |
| 接受任務（`questAccept`） | **僅隊長**接受；隊員不會自動接取 |
| 領取報酬（`questComplete`） | 隊長選擇領取時，**所有已接且達標**的隊員一併結算，各自獲得 EXP／技能點 |
| 戰鬥擊殺進度 | 全隊每位**已接該任務**的成員各自 +1；未接者可以參戰但**不累積進度** |
| 狀態列時代顯示 | 隊員顯示隊長的有效時代，並標註「隊長時間線」 |

---

## 5. 夥伴（Companion）系統

### 夥伴基本屬性

夥伴擁有與玩家相同的七項屬性，另有：
- 獨立等級與 EXP（同玩家公式）
- 獨立 HP / MP
- 可被暱稱
- 獨立裝備欄位

### 夥伴升等成長（每升一級）

升級時分配 **5 點**屬性成長，流程如下：

1. 以夥伴**扣除裝備加成後**的七項屬性由高到低排序
2. 依排序賦予機率：32%、24%、16%、12%、6%、5%、5%
3. 依機率抽選一項屬性，隨機增加 **1～2** 點
4. 若仍有剩餘點數，重新排序後重複步驟 2～3，直到 5 點分配完畢
5. 升級時同步更新最大 HP / MP（體力 × 20、精神 × 5）

**機率對照（由高到低）：**

| 排序 | 機率 |
|------|------|
| 第 1 高 | 32% |
| 第 2 高 | 24% |
| 第 3 高 | 16% |
| 第 4 高 | 12% |
| 第 5 高 | 6% |
| 第 6 高 | 5% |
| 第 7 高 | 5% |

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

- 夥伴 HP / MP **跨戰鬥持久化**：戰鬥結束後將剩餘值同步至資料庫，**不會自動恢復滿血**
- HP ≤ 0 的夥伴無法出戰；**勝利時**若夥伴曾在戰鬥中歸零，戰後 HP 恢復為 **1**（不觸發死亡機制，見[死亡與醫館機制](#死亡與醫館機制)）

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

- 每個商店 NPC 的販售清單預先配置於 `shop_items` 表（`npc_id` + `item_id` + `price`）
- 商店**列出該 NPC 配置的所有販售物品**，不依玩家等級篩選顯示
- 購買時僅檢查金幣是否足夠；裝備的**需求等級**在裝備時才檢查
- 賣出價格為裝備的 `sell_price`（0 = 不可出售）

---

## 7. 地圖與世界系統

> 完整設計文件：**[WORLD_MAP.md](../WORLD_MAP.md)**（184–280 AD、216 張地圖、496 傳點）。  
> 地圖生成：`python3 tools/generate_world_maps.py`；名地手工佈局：`tools/map_handcraft.py`。

### 座標系統

- 基於格子（Grid）的等距（Isometric）座標系
- 地圖有可行走性格子（walkability grid）
  - `.`、`P`、`=`、`@` = 可行走
  - `#`、`T`、`W`、`M` 等 = 障礙物

### 地圖資料（`maps.json`）

| 項目 | 說明 |
|------|------|
| 預設地圖 | `xuchang`（許縣） |
| 地圖數量 | 216 張（城、關、渡、官道、野原、戰場、營寨） |
| 地圖格式 | 檔案路徑 + 顯示名稱 |
| NPC 位置 | 指定格子座標 + sprite 鍵 |
| 傳送點 | 座標 → 目標地圖 + 目標座標（496 條） |
| 明雷（`visibleEnemies`） | 出生點 id、templateId、座標、chaseRange、loseRange；間距 ≥ 4 格 |
| 明雷最大敵人數（`maxVisibleEnemies`） | 明雷戰鬥最多敵人數，預設 1，範圍 1～5 |
| 暗雷最大敵人數（`maxDarkEnemies`） | 危險區域戰鬥最多敵人數，預設 3，範圍 1～5 |
| 危險區域（`dangerZones`） | id + `rect` 矩形或 `cells` 格子列表 |

**玩家預設出生點：** 許縣 `xuchang` (8, 8)

**新手流程地圖：**

| 地圖 | 明雷 | 危險區 | 備註 |
|------|------|--------|------|
| `xuchang` | 無 | 無 | 鄉老、許縣醫廬 |
| `yingchuan_suburb` | 2 野狼 | 全圖暗雷 | 斥候、初試身手目標 |
| `rebel_camp_yingchuan` | 1 幽影 | 有 | 潁川賊營（E1 主線第二環） |

**名地手工佈局**（`map_handcraft.py`）：洛陽、長安、成都、建業、赤壁、官渡戰場、赤壁戰場、函谷關、隆中、許縣。

其餘地圖為程序生成模板，詳見 [WORLD_MAP.md](../WORLD_MAP.md) §4–§5。

> **個人時間線 vs 共享地圖**：216 張地圖對所有玩家共用（地理永恆）；每位玩家的 `story_era` 與任務進度決定 NPC 對話、可接任務與主線體驗。組隊時以隊長時間線為準。完整規範見 [§7.5](#75-主線章節與個人時間線)。

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

## 7.5 主線章節與個人時間線

> 世界地圖涵蓋 184–280 全時間線（見 [WORLD_MAP.md](../WORLD_MAP.md) §7），但**不採用全服統一推進的日曆**。  
> 每位玩家有自己的 `story_era`（個人時間）；組隊時暫時跟隨**隊長**的有效時間線。

### 設計原則（三層模型）

```
┌─────────────────────────────────────────────────────────┐
│  共用層：216 張地圖、傳點、醫館、自由探索、組隊戰鬥       │
│          → 地理永恆，所有玩家走同一套 map_id             │
├─────────────────────────────────────────────────────────┤
│  個人層：users.story_era (E1–E8) + player_quests        │
│          → NPC 對話、可接任務、章節推進存於玩家身上       │
├─────────────────────────────────────────────────────────┤
│  組隊層：有效時間線 = 隊長的 story_era + 隊長任務狀態     │
│          → 對話／主線 NPC 以隊長為準；隊員可旁聽、可參戰   │
└─────────────────────────────────────────────────────────┘
```

| 原則 | 說明 |
|------|------|
| **地理永恆** | 洛陽、赤壁、黃巾營地等地圖永遠存在；不因「全服已進入三國時代」而關閉 |
| **故事個人化** | 晚入坑玩家仍從 E1（中平／黃巾）完整體驗；快通關玩家可推進至 E2、E3… |
| **組隊跟隊長** | 同一隊伍內，NPC 對話與主線節點解析以**隊長**為準，避免同圖不同戲 |
| **不強制全服時鐘** | 不做「伺服器 3 個月後自動 E5、新手追不上」；後續 Live Ops 活動應為加成型，不阻塞個人主線 |

### 時代代號（Story Era）

對照 [WORLD_MAP.md](../WORLD_MAP.md) §7.1：

| 代號 | 年號 | 年 | 世界狀態（摘要） |
|------|------|-----|------------------|
| E1 | 中平 | 184–189 | 黃巾；董卓入洛前 |
| E2 | 初平–興平 | 190–195 | 董卓、群雄割據 |
| E3 | 建安初 | 196–200 | 迎獻帝；官渡 |
| E4 | 建安中 | 201–219 | 赤壁、漢中、襄樊 |
| E5 | 建安末–黄初 | 220–229 | 魏蜀吳建國 |
| E6 | 太和–青龙 | 226–237 | 諸葛北伐 |
| E7 | 正始–咸熙 | 240–265 | 司馬專權；滅蜀 |
| E8 | 太康 | 280 | 晉滅吳 |

- **資料庫**：`users.story_era VARCHAR(4) NOT NULL DEFAULT 'E1'`
- **程式**：`StoryEra.java` 列舉；`StoryContextService` 解析有效時代
- **創角**：新角色固定 `E1`；創角成功訊息為中平元年／鄉勇入伍語境

### 資料模型

**玩家（`users`）**

| 欄位 | 說明 |
|------|------|
| `story_era` | 個人主線章節，預設 `E1` |

**任務（`quests`）— 章節相關欄位（`V32__story_era_and_e1_opening.sql`）**

| 欄位 | 說明 |
|------|------|
| `required_era` | 可接取此任務的**最低**個人時代（玩家 `story_era` ordinal ≥ 此值） |
| `prerequisite_quest_id` | 前置任務 ID；須 `COMPLETED` 才可接取 |
| `unlocks_era` | （可選）領取報酬後若高於目前 `story_era` 則推進章節 |

**玩家任務進度（`player_quests`）** — 不變；仍為每人獨立的 `IN_PROGRESS` / `COMPLETED` + `progress`。

### 有效時間線解析（StoryContextService）

```java
// 組隊中 → 隊長 userId；單人 → 自己
Long storyUserId = storyContextService.resolveStoryUserId(actorUserId);
String era = storyContextService.resolveStoryEra(actorUserId);
```

| 方法 | 用途 |
|------|------|
| `resolveStoryUserId(userId)` | NPC 起始對話節點、任務可用性判斷用誰的進度 |
| `resolveStoryEra(userId)` | 讀取有效 `story_era`（組隊 = 隊長） |
| `isStoryActor(userId)` | 是否為當前時間線的「主角」（隊長或單人） |

### NPC 與對話

**起始節點**（`NpcService.resolveStartingNode`）：

1. 取 **storyUserId**（組隊 = 隊長）的任務狀態
2. 僅考慮 `required_era` ≤ 該玩家時代的任務
3. 有進行中 → `quest_already`；可領取 → `quest_complete`；否則 → `root`

**選項過濾**（`buildDialogueResponse`）：

- 含 `questAccept` 的選項：僅在**操作者**（組隊 = 隊長）`canAcceptQuest` 時顯示
- 含 `questComplete` 的選項：僅在操作者 `canClaimQuest` 時顯示

**組隊對話同步**：

| 步驟 | 行為 |
|------|------|
| 1 | 僅隊長可送 `NPC_INTERACT` / `DIALOGUE_CHOICE` |
| 2 | 隊長收到 `NPC_INTERACT_OK` |
| 3 | 其他隊員收到 `PARTY_DIALOGUE_SYNC`（相同 `text` / `nodeKey`，`observer: true`） |
| 4 | 隊員客戶端：對話框唯讀，顯示「跟隨隊長對話中」；不可選選項 |
| 5 | 隊長觸發 `openShop` / `hospitalRevive` 時，隊員同步封包**不含**這些動作欄位 |

### 任務接受、進度、領獎

**接受（`questAccept`）**

- 僅寫入**操作者**（隊長）的 `player_quests`
- 隊員若要解同一任務，須在**脫隊後**自行找 NPC 接取，或組隊前已接好

**戰鬥擊殺（`QuestService.recordKills`）**

- 組隊勝利時，對**每位隊員**各自呼叫
- 條件：該員有 `IN_PROGRESS` 且 `target_id` 匹配的任務
- **未接任務的隊員**：可參戰、可獲 EXP／掉落，但 `progress` 不增加
- `BATTLE_RESULT` 內 `questProgress` 依各員個人化下發

**領獎（`questComplete`）**

- 隊長選擇領取時，`claimRewardForReadyPartyMembers` 對全隊檢查
- **已接且達標**的隊員一併 `COMPLETED` 並發獎
- 各員 `PARTY_DIALOGUE_SYNC` / 結束封包中的 `questRewards` 只含**自己的**獎勵

**章節推進（`unlocks_era`）**

- 在 `claimReward` 時若任務設有 `unlocks_era`，且其 ordinal 高於玩家目前 era，則更新 `users.story_era`
- 建議僅用於**章節結算任務**（例如 E1 尾聲 → `E2`），避免小任務頻繁跳時代

### E1 開局：鄉勇入伍（已實作）

**出生**：許縣 `xuchang` (8, 8) — 184 年潁川屬縣，距黃巾主戰場近。

**主線任務鏈**（`V32` 更新文案）：

| ID | 名稱 | 前置 | required_era | 目標 | 給予 NPC |
|----|------|------|--------------|------|----------|
| 1 | 初試身手 | — | E1 | 擊殺 `wild_wolf` ×3（潁川郊野） | `xuchang_elder` 鄉老 |
| 2 | 潁川賊營 | 任務 1 | E1 | 擊殺 `shadow_wisp` ×2（`rebel_camp_yingchuan`） | `yingchuan_scout` 斥候 |

**流程**：許縣鄉老 → 潁川郊野 → 潁川黃巾營 → （後續章節待擴充）

### 組隊情境對照表

以下說明「隊長 vs 隊員個人進度不一致」時的預期行為——**後續功能應維持同一套規則**：

| 情境 | 預期行為 |
|------|----------|
| 隊長 E1、隊員 E3 | 隊員跟隨時看到 **E1 隊長對話**；隊員個人 `story_era` 不變 |
| 隊長已接任務 1、隊員未接 | 隊員跟隊打狼：**無任務進度**；隊長有進度 |
| 兩人都已接任務 1 | 同場戰鬥擊殺後**兩人 progress 各自 +1** |
| 兩人都達標、隊長領獎 | 隊長選「領取報酬」→ **兩人皆 COMPLETED** 並各得獎勵 |
| 隊員達標、隊長未達標 | 對話節點依隊長狀態為 `quest_already`；隊員須脫隊自行領獎，或等隊長達標後由隊長觸發領取 |
| 隊員想接同一任務 | 組隊前自行接取，或暫時離隊找 NPC |
| 隊長換人 | 有效時間線改為**新隊長**的 era／任務狀態 |

### 客戶端狀態

`GameState` 欄位：

| 欄位 | 說明 |
|------|------|
| `player_story_era` | 自己的章節代號 |
| `effective_story_era` | 目前生效的時代（組隊 = 隊長） |
| `effective_story_era_name` | 顯示用（如「中平」） |
| `party_dialogue_observer` | 是否為隊長對話旁聽模式 |

`LOGIN_OK` / 組隊 `party` 物件含：`playerStoryEra`、`effectiveStoryEra`、`effectiveStoryEraName`。

### 後續擴充指引

新增主線章節時，建議依序：

1. **Migration**：插入 `quests`（設 `required_era`、`prerequisite_quest_id`、可選 `unlocks_era`）與 `dialogue_nodes`
2. **NPC**：任務給予者 `giver_npc_id`；對話文案符合該 `required_era` 語境
3. **驗證**：`QuestService.canAcceptQuest` 單元測試覆蓋 era 與前置
4. **地圖**（可選）：在 `maps.json` 加 `eraTags` 標記史實活躍期，供日後遭遇／副本過濾
5. **組隊**：確認新任務的擊殺目標在組隊戰中仍走 `recordKills` 全員迴圈
6. **不做的**：勿用全服單一 era 關閉舊任務；勿對整張地圖做 phasing 分線（除非特定 `battlefield_*` 副本）

**關鍵原始碼**

| 元件 | 路徑 |
|------|------|
| 時代列舉 | `server/.../game/StoryEra.java` |
| 有效時間線 | `server/.../service/StoryContextService.java` |
| 任務 era／前置 | `server/.../service/QuestService.java` |
| 對話 + 組隊同步 | `server/.../service/NpcService.java`、`GameWebSocketHandler.java` |
| DB 遷移 | `V32__story_era_and_e1_opening.sql` |
| 客戶端旁聽 | `client/scripts/ui/dialogue_panel.gd`、`main.gd` |

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
| 剋制 | ×1.1 |
| 被剋 | ×0.9 |
| 中立 | ×1.0 |
| 無元素（NONE） | 永遠 ×1.0，無屬性加成或懲罰 |

### 元素特性

各元素持有者在戰鬥中有 **5% 機率** 觸發對應特性（「無」無特性）：

| 元素 | 特性 |
|------|------|
| 火 | 造成傷害時，最終傷害 × 1.05 |
| 風 | 若與前一個出手的對手敏捷差距 < 50，本回合出手順序優先於該對手 |
| 土 | 受到傷害時，最終傷害 × 0.95 |
| 雷 | 造成傷害時，暴擊率 +5%（加在幸運差計算後） |
| 水 | 回合行動結束後，回復 5% 最大 HP 與 5% 最大 MP |

### UNIVERSAL 技能

元素設為 UNIVERSAL 的技能，使用者自身元素決定屬性加成計算。

---

## 10. 任務系統

> 任務與個人主線章節（`story_era`）的關係見 [§7.5](#75-主線章節與個人時間線)。

### 任務類型

目前實作：**KILL 任務**（擊殺指定怪物 X 隻）

### 任務定義欄位（`quests` 表）

| 欄位 | 說明 |
|------|------|
| `quest_type` | `KILL`（`TALK` 預留） |
| `target_id` | 怪物模板 ID 或 NPC ID |
| `required_count` | 需求數量 |
| `reward_exp` / `reward_skill_points` | 報酬 |
| `giver_npc_id` | 任務給予 NPC |
| `required_era` | 最低個人時代（預設 `E1`） |
| `prerequisite_quest_id` | 前置任務（可 NULL） |
| `unlocks_era` | 領獎後推進章節（可 NULL） |

### 任務狀態

| 狀態 | 說明 |
|------|------|
| IN_PROGRESS | 進行中，持續追蹤擊殺數 |
| COMPLETED | 已完成並領取獎勵 |

### 任務流程

1. 玩家（或組隊時的**隊長**）接受任務 → `player_quests` 新增列，`IN_PROGRESS`
2. 每次擊殺符合 `target_id` 的怪物 → 該玩家的 `progress` +1（組隊時全員各自判定是否已接）
3. `progress` ≥ `required_count` → 可領取
4. 與給予 NPC 對話 → 自動進入 `quest_complete` 節點 → 領取後 `COMPLETED`
5. 若任務設 `unlocks_era` → 更新 `users.story_era`

### E1 現有主線任務

| ID | 名稱 | 前置 | 目標 | 數量 | 給予者 |
|----|------|------|------|------|--------|
| 1 | 初試身手 | — | `wild_wolf` | 3 | 許縣鄉老 |
| 2 | 潁川賊營 | 任務 1 | `shadow_wisp` | 2 | 潁川斥候 |

### 任務獎勵

- 經驗值（EXP）
- 技能點
- 獎勵僅能領取一次

---

## 11. 商店系統

### 商店 NPC

- 特定 NPC 透過對話選項「我想買東西」（`action: open_shop`）開啟商店
- 各商店的販售品項預先配置於 **`shop_items` 表**，欄位：`npc_id`、`item_id`、`price`
- 同一 NPC 可販售多種物品；同一物品可出現在不同商店（各自定價）
- 商店**列出該 NPC 在表中配置的所有物品**，不依玩家等級篩選

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
| PARTY_DIALOGUE_SYNC | Server → Client | 組隊時隊員同步隊長 NPC 對話（`observer: true`） |

**`LOGIN_OK` 額外欄位**：`playerStoryEra`、`effectiveStoryEra`、`effectiveStoryEraName`（組隊時有效時代為隊長）。

**`party` 物件額外欄位**：同上，供組隊面板與狀態列顯示。

**`NPC_INTERACT_OK` / `PARTY_DIALOGUE_SYNC`**：進行中帶 `observer`（隊員為 `true`）；結束時帶 `questRewards`（各員僅含自己的獎勵）。

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
[登入] → [角色創建：分配 10 點屬性] → story_era = E1（中平／鄉勇入伍）
           ↓
       [世界探索：許縣出生，與鄉老對話接 E1 主線]
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
  [獲得 EXP + 掉落物；已接任務者更新 questProgress]
      ↓
  [戰鬥後冷卻：明雷屏蔽 / 追擊 / 遇敵]
      ↓
  [升等？→ 獲得技能點 + 屬性點]
      ↓
  [分配屬性點 / 學習升級技能]
      ↓
  [購買裝備 / 出售物品]
      ↓
  [捕捉夥伴 → 管理夥伴]
      ↓
  [接受任務 → 推進進度 → 領取獎勵 → 可解鎖下一 story_era]
      ↓
  [邀請玩家組隊 → 隊長主導移動／對話／時間線；隊員旁聽對話、並行參戰]
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
| 主線時間線 | [StoryContextService.java](server/src/main/java/com/dejebu/service/StoryContextService.java)、[StoryEra.java](server/src/main/java/com/dejebu/game/StoryEra.java) |
| 商店系統 | [ShopService.java](server/src/main/java/com/dejebu/service/ShopService.java) |
| 地圖服務 | [MapService.java](server/src/main/java/com/dejebu/service/MapService.java) |
| 明雷追擊 | [VisibleEnemyService.java](server/src/main/java/com/dejebu/service/VisibleEnemyService.java) |
| 危險區域 | [DangerZoneService.java](server/src/main/java/com/dejebu/service/DangerZoneService.java) |
| 遇敵冷卻 | [EncounterCooldownService.java](server/src/main/java/com/dejebu/service/EncounterCooldownService.java) |
| 野外遭遇 | [EncounterService.java](server/src/main/java/com/dejebu/service/EncounterService.java) |
| 明雷渲染 | [visible_enemy_manager.gd](client/scripts/world/visible_enemy_manager.gd) |
| 戰鬥 UI | [battle_scene.gd](client/scripts/battle/battle_scene.gd) |
| 客戶端狀態 | [game_state.gd](client/scripts/game/game_state.gd) |
