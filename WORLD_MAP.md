# DeJaBu 世界地圖設計書

> **時代範圍**：中平元年（184）黃巾起事 → 太康元年（280）晉滅吳統一  
> **設計狀態**：完整節點與傳點規劃（**已實作** ASCII 地圖與 `maps.json`）  
> **生成**：`python3 tools/generate_world_maps.py` · **名地手工佈局**：`tools/map_handcraft.py`（洛陽、赤壁等 10 張）
> **對照系統**：[GAME_MECHANICS.md](GAME_MECHANICS.md) §7、[README.md](README.md) 地圖與探索

---

## 目錄

1. [設計原則](#1-設計原則)
2. [地圖類型與字元](#2-地圖類型與字元)
3. [十三州總覽](#3-十三州總覽)
4. [完整地圖清單](#4-完整地圖清單)
5. [完整傳點連接表](#5-完整傳點連接表)
6. [天然屏障與不可通行邊界](#6-天然屏障與不可通行邊界)
7. [歷史節點對照（184–280）](#7-歷史節點對照184280)
8. [醫館、出生點、首都標記](#8-醫館出生點首都標記)
9. [實作規範](#9-實作規範)
10. [附錄 A：傳點 JSON 範本](#附錄-a傳點-json-範本)
11. [附錄 B：地圖 ID 索引](#附錄-b地圖-id-索引)

---

## 1. 設計原則

| 原則 | 說明 |
|------|------|
| **節點式世界** | 每個地點為一張 ASCII 格子圖；長途必經「官道／郊野／關隘／渡口」圖，城與城不直連 |
| **雙向傳點** | 每條路徑在 `maps.json` 的 `teleports` 中成對設定（A→B 與 B→A） |
| **方向一致** | 每張圖 `@` 出口：北=地理北、東=東、南=南、西=西（官道圖長軸對應主要走向） |
| **史實優先** | 州郡治所、關隘、渡口、戰場位置依《後漢書·郡國志》《三國志》《水經注》《讀史方輿紀要》 |
| **全時間線** | 同一套地圖覆蓋 184–280；特殊地圖以 `eraTags` 標記主要活躍時期（實作時可選用） |
| **同步要求** | 客戶端 `client/data/maps/` 與伺服器 `server/src/main/resources/maps/` 完全一致 |

**地圖總數**：**216 張**（城 71 · 郊 11 · 官道 95 · 關 6 · 渡 6 · 野 14 · 戰 10 · 營 3）

---

## 2. 地圖類型與字元

### 2.1 地圖類型

| 代碼 | 類型 | 用途 | 預設危險 |
|------|------|------|----------|
| `city` | 城 | 郡國治所、安全區、商店、醫館、主線 NPC | 0 |
| `suburb` | 郊 | 城郭外、傳點樞紐、低遭遇 | 1 |
| `road` | 官道 | 長途移動、暗雷、流寇 | 2 |
| `pass` | 關隘 |  choke point，高遭遇 | 3 |
| `ferry` | 渡口 | 跨黃河、長江、漢水 | 2 |
| `wild` | 野原 | 州境過渡、中遭遇 | 2 |
| `battle` | 戰場 | 劇情／高難副本 | 4 |
| `camp` | 營寨 | 黃巾／賊寇營地 | 4 |

### 2.2 格子字元（現有 + 建議擴充）

| 字元 | 地形 | 可走 | 備註 |
|------|------|------|------|
| `.` | 野地 | ✅ | 現有 |
| `P` | 土路 | ✅ | 現有 |
| `=` | 石道／橋面 | ✅ | 現有 |
| `@` | 傳送點 | ✅ | 須在 `maps.json` 註冊座標 |
| `#` | 城牆／山崖 | ❌ | 現有 |
| `T` | 樹木 | ❌ | 現有 |
| `W` | 河流 | ❌ | 現有 |
| `M` | 山脈 | ❌ | **建議新增** |
| `B` | 碼頭 | ✅ | **建議新增**，渡口中心 |
| `C` | 城門 | ✅ | **建議新增** |

### 2.3 建議尺寸（寬×高，格）

| 類型 | 小型 | 中型 | 大型 |
|------|------|------|------|
| 城 | 24×18 | 32×24 | 48×36（洛陽、長安、成都、建業） |
| 郊 | 28×20 | 32×24 | — |
| 官道 | 長軸 20–48，短軸 10–14 | 依史實里程 | — |
| 關隘 | 24×14 | — | — |
| 渡口 | 20×14 | — | — |

---

## 3. 十三州總覽

```
                         [幽州] 薊─涿─遼東
                            |  \
                     [并州] 晉陽   代
                       |  \      |
              雁門─井陉   上黨    |
                       |         |
    [涼州] 敦煌─…─天水─長安←函谷─弘農─洛陽─河內─[冀州] 鄴─鉅鹿─廣宗
                              |    |     \         |
                           武關  許昌   孟津渡    常山─真定─薊
                              |    |       |         |
                         [荊州] 宛─新野─襄陽─江陵─夷陵─巴東
                              \   |      |    \      |
                          [豫州] 汝南  江夏  長沙─桂陽─[交州] 番禺
                              |         \    |              |
                          [兗州] 陳留─東郡─白馬    赤壁      交趾
                              |                      |
                          [徐州] 彭城─下邳─廣陵────建業─[揚州] 柴桑─濡須─合肥─壽春
                              |              /           |
                          [青州] 濟南─北海      會稽─吳郡
                                                 |
                                            [益州] 漢中─成都─劍閣─建寧
```

**州境地圖 ID 前綴慣例**

| 州 | 前綴 | 地圖數 |
|----|------|--------|
| 司隸 | 核心城無前綴 | 19 |
| 豫州 | `yu_` 野原 | 17 |
| 兗州 | 城名 | 20 |
| 冀州 | 城名 | 23 |
| 并州 | `bing_` | 10 |
| 幽州 | `you_` | 12 |
| 青州 | `qing_` | 7 |
| 徐州 | `xu_` | 13 |
| 荊州 | 城名 | 35 |
| 揚州 | 城名 | 23 |
| 益州 | 城名 | 19 |
| 交州 | `jiao_` | 7 |
| 涼州 | `liang_` | 11 |

---

## 4. 完整地圖清單

> 欄位：**map_id** · 顯示名 · 類型 · 危險 · 尺寸 · 主要史實 · 連接（見 §5 詳細座標）

### 4.1 司隸校尉部（18）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `luoyang` | 洛陽 | city | 0 | 48×36 | 184 帝都；189 董卓入洛；220 禪讓 | `luoyang_suburb` |
| `luoyang_suburb` | 洛陽外郭 | suburb | 1 | 32×24 | 市集、難民 | N→`road_luoyang_henei` E→`road_luoyang_yingchuan` W→`road_luoyang_hongnong` S→`road_luoyang_xuchang` N→`mengjin_ferry` |
| `changan` | 長安 | city | 0 | 48×36 | 西都；192 董卓遷都 | `changan_suburb` |
| `changan_suburb` | 長安外郭 | suburb | 1 | 32×24 |  | E→`road_hangu_changan` S→`road_changan_wu` W→`road_changan_tianshui` SW→`road_changan_hanzhong` |
| `hongnong` | 弘農 | city | 1 | 28×20 | 崤函東側要衝 | `road_luoyang_hongnong` ↔ `road_hongnong_hangu` |
| `henei` | 河內溫縣 | city | 1 | 28×20 | 司隸北部 | `road_luoyang_henei` ↔ `road_henei_ye` |
| `xuchang` | 許縣 | city | 0 | 32×24 | 196 曹操迎獻帝；220 魏都 | `xuchang_suburb` |
| `xuchang_suburb` | 許昌外郭 | suburb | 1 | 28×20 |  | N→`road_luoyang_xuchang` E→`road_xuchang_yingchuan` S→`battlefield_guandu` |
| `hangu_pass` | 函谷關 | pass | 3 | 24×14 | 關中入口 | `road_hongnong_hangu` ↔ `road_hangu_changan` |
| `wu_pass` | 武關 | pass | 3 | 24×14 | 南陽北門 | `road_changan_wu` ↔ `road_wu_wancheng` |
| `mengjin_ferry` | 孟津渡 | ferry | 2 | 20×14 | 洛陽北渡黃河 | `luoyang_suburb` ↔ `road_mengjin_baima` |
| `road_luoyang_henei` | 洛河官道 | road | 2 | 40×12 | 洛陽→河內 ~120里 | 見 §5.1 |
| `road_luoyang_hongnong` | 崤函道（洛→弘） | road | 2 | 44×12 | ~150里 | 見 §5.1 |
| `road_luoyang_xuchang` | 洛許官道 | road | 2 | 32×12 | ~100里 | 見 §5.1 |
| `road_luoyang_yingchuan` | 嵩洛道 | road | 2 | 36×12 | 洛陽→潁川 ~200里 | 見 §5.1 |
| `road_hongnong_hangu` | 弘農→函谷段 | road | 2 | 28×12 |  | 見 §5.1 |
| `road_hangu_changan` | 函谷→長安段 | road | 2 | 36×12 | ~200里 | 見 §5.1 |
| `road_changan_wu` | 長安→武關段 | road | 3 | 40×12 | 秦嶺道 | 見 §5.1 |
| `road_changan_tianshui` | 隴右道（長→天水） | road | 3 | 48×12 | 涼州入口 | 見 §5.13 |

### 4.2 豫州（15）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `yingchuan` | 潁川 | city | 1 | 32×24 | 184 波才；官渡後方 | `yingchuan_suburb` ↔ `rebel_camp_yingchuan` |
| `yingchuan_suburb` | 潁川郊野 | suburb | 2 | 28×20 |  | W→`road_luoyang_yingchuan` E→`road_chenliu_yingchuan` |
| `runan` | 汝南 | city | 1 | 28×20 | 袁術據地；朱儁討賊 | `runan_suburb` |
| `runan_suburb` | 汝南郊野 | suburb | 2 | 28×20 |  | N→`road_yingchuan_runan` S→`road_runan_wancheng` E→`road_shouchun_runan` |
| `chen` | 陳國平輿 | city | 1 | 24×18 |  | `road_yingchuan_chen` |
| `pei` | 沛國 | city | 1 | 24×18 | 劉備早期 | `road_runan_pei` ↔ `road_pei_pengcheng` |
| `liang` | 梁國睢陽 | city | 1 | 24×18 |  | `road_yingchuan_liang` |
| `lu` | 魯國 | city | 1 | 24×18 | 孔廟所在 | `road_chen_lu` ↔ `road_lu_yanzhou_wild` |
| `road_chen_lu` | 陳魯道 | road | 1 | 20×10 | 陳國→魯國 | 見 §5.2 |
| `road_lu_yanzhou_wild` | 魯國→兗州野 | road | 2 | 24×10 |  | 見 §5.3 |
| `road_yingchuan_runan` | 潁汝道 | road | 2 | 32×12 | ~150里 | 見 §5.2 |
| `road_yingchuan_chen` | 潁陳道 | road | 1 | 24×10 |  | 見 §5.2 |
| `road_yingchuan_liang` | 潁梁道 | road | 1 | 24×10 |  | 見 §5.2 |
| `road_runan_pei` | 汝沛道 | road | 2 | 28×12 |  | 見 §5.2 |
| `road_xuchang_yingchuan` | 許潁道 | road | 1 | 24×10 |  | 見 §5.2 |
| `rebel_camp_yingchuan` | 潁川黃巾營 | camp | 4 | 24×18 | 184 波才 | `yingchuan` |
| `yu_wild` | 豫州野原 | wild | 2 | 32×20 |  | `liang` ↔ `road_yingchuan_liang` |

### 4.3 兗州（17）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `chenliu` | 陳留 | city | 1 | 32×24 | 兗州治；官渡後勤 | `chenliu_suburb` |
| `chenliu_suburb` | 陳留郊野 | suburb | 2 | 28×20 |  | 四向官道樞紐 |
| `dongjun` | 東郡白馬 | city | 2 | 28×20 | 白馬津 | `road_chenliu_dongjun` ↔ `baima_ferry` |
| `jibei` | 濟北 | city | 2 | 24×18 | 184 黃巾 | `road_dongjun_jibei` |
| `dongping` | 東平 | city | 2 | 24×18 |  | `road_dongjun_dongping` ↔ `road_dongping_xu_wild` |
| `taishan` | 泰山 | city | 1 | 24×18 |  | `road_chenliu_taishan` |
| `ren` | 任城 | wild | 3 | 24×18 | 兗州討伐 | `road_dongping_ren` |
| `baima_ferry` | 白馬津 | ferry | 2 | 20×14 | 官渡渡河 | `dongjun` ↔ `road_mengjin_baima` |
| `road_chenliu_yingchuan` | 陳潁道 | road | 2 | 32×12 |  | 見 §5.3 |
| `road_chenliu_dongjun` | 陳東道 | road | 2 | 28×12 |  | 見 §5.3 |
| `road_chenliu_pengcheng` | 陳彭道 | road | 2 | 40×12 | 入徐州 | 見 §5.3 |
| `road_chenliu_taishan` | 陳泰道 | road | 1 | 28×10 |  | 見 §5.3 |
| `road_dongjun_jibei` | 東濟北道 | road | 2 | 24×10 |  | 見 §5.3 |
| `road_dongjun_dongping` | 東平道 | road | 2 | 28×12 |  | 見 §5.3 |
| `road_dongping_ren` | 東平→任城 | road | 3 | 20×10 |  | 見 §5.3 |
| `road_dongjun_jinan` | 東濟南道 | road | 2 | 32×12 | 入青州 | 見 §5.3 |
| `road_mengjin_baima` | 孟津→白馬渡 | ferry | 2 | 24×12 | 黃河 | 見 §5.3 |
| `battlefield_guandu` | 官渡 | battle | 4 | 32×24 | 200 曹操 vs 袁紹 | `xuchang_suburb` ↔ `road_guandu_ye` |
| `road_guandu_ye` | 官渡→鄴道 | road | 3 | 36×12 |  | 見 §5.3 |
| `yanzhou_wild` | 兗州野原 | wild | 2 | 32×20 |  | `lu` ↔ `road_lu_yanzhou_wild` ↔ `chenliu_suburb` |

### 4.4 冀州（19）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `ye` | 鄴 | city | 1 | 32×24 | 204 曹操基業；220 魏陪都 | `ye_suburb` |
| `ye_suburb` | 鄴郊 | suburb | 2 | 28×20 |  | 多向官道 |
| `julu` | 鉅鹿 | city | 2 | 28×20 | 184 張角 | `road_ye_julu` ↔ `rebel_camp_julu` |
| `guangzong` | 廣宗 | battle | 4 | 24×18 | 184 皇甫嵩火攻張寶 | `road_julu_guangzong` |
| `xiayang` | 下曲陽 | battle | 4 | 24×18 | 184 張梁戰死 | `road_julu_xiayang` |
| `changshan` | 常山真定 | city | 2 | 24×18 |  | `road_ye_changshan` ↔ `road_changshan_zhending` |
| `zhending` | 真定 | city | 2 | 24×18 |  | `road_changshan_zhending` ↔ `road_zhending_ji` |
| `hejian` | 河間 | city | 1 | 24×18 |  | `road_ye_hejian` |
| `anping` | 安平 | city | 1 | 24×18 |  | `road_ye_anping` |
| `changli` | 昌黎 | city | 2 | 24×18 | 遼西入口 | `road_anping_changli` ↔ `road_changli_liaodong` |
| `road_changshan_zhending` | 常山→真定道 | road | 2 | 24×10 |  | 見 §5.4 |
| `road_anping_changli` | 安平→昌黎道 | road | 2 | 32×12 |  | 見 §5.4 |
| `jingxing_pass` | 井陉關 | pass | 3 | 24×14 | 太行險道 | `road_ye_jingxing` ↔ `road_jingxing_jinyang` |
| `road_henei_ye` | 河內→鄴道 | road | 2 | 36×12 | ~150里 | 見 §5.4 |
| `road_ye_julu` | 鄴→鉅鹿道 | road | 3 | 32×12 | 張角線 | 見 §5.4 |
| `road_julu_guangzong` | 鉅鹿→廣宗 | road | 4 | 24×10 |  | 見 §5.4 |
| `road_julu_xiayang` | 鉅鹿→下曲陽 | road | 4 | 28×10 |  | 見 §5.4 |
| `road_ye_changshan` | 鄴→常山 | road | 2 | 32×12 |  | 見 §5.4 |
| `road_ye_hejian` | 鄴→河間 | road | 2 | 28×10 |  | 見 §5.4 |
| `road_ye_anping` | 鄴→安平 | road | 2 | 28×10 |  | 見 §5.4 |
| `road_ye_jingxing` | 鄴→井陉 | road | 3 | 32×12 | 入并州 | 見 §5.4 |
| `road_guandu_ye` | 官渡→鄴 | road | 3 | 36×12 | 200 官渡 | 見 §5.4 |
| `rebel_camp_julu` | 鉅鹿太平道壇 | camp | 4 | 28×20 | 184 張角 | `julu` |
| `ji_wild` | 冀州野原 | wild | 2 | 32×20 |  | `hejian` ↔ `road_ye_hejian` |

### 4.5 并州（10）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `jinyang` | 晉陽 | city | 2 | 28×20 | 并州治 | 多向 |
| `taiyuan` | 太原 | city | 2 | 24×18 |  | `road_jinyang_taiyuan` |
| `shangdang` | 上黨 | city | 2 | 24×18 |  | `road_shangdang_jinyang` |
| `yanmen_pass` | 雁門關 | pass | 3 | 24×14 | 北疆 | `road_taiyuan_yanmen` |
| `road_jinyang_taiyuan` | 晉太道 | road | 2 | 28×12 |  | 見 §5.5 |
| `road_taiyuan_yanmen` | 太雁道 | road | 3 | 32×12 |  | 見 §5.5 |
| `road_jingxing_jinyang` | 井陉→晉陽 | road | 3 | 36×12 |  | 見 §5.5 |
| `road_shangdang_jinyang` | 上黨→晉陽 | road | 2 | 32×12 |  | 見 §5.5 |
| `road_yanmen_you_wild` | 雁門→幽州 | road | 3 | 40×12 |  | 見 §5.5 |
| `bing_wild` | 并州野原 | wild | 3 | 32×20 |  | `shangdang` ↔ `road_shangdang_jinyang` |

### 4.6 幽州（11）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `ji` | 薊 | city | 2 | 28×20 | 幽州治 | 多向 |
| `zhuo` | 涿 | city | 1 | 24×18 | 劉備故里 | `road_ji_zhuo` |
| `yuyang` | 漁陽 | city | 2 | 24×18 |  | `road_ji_yuyang` |
| `dai` | 代 | city | 2 | 24×18 |  | `road_ji_dai` ↔ `road_yanmen_you_wild` |
| `liaodong` | 遼東 | city | 3 | 28×20 | 公孫度據地 | `road_ji_liaodong` ↔ `road_changli_liaodong` |
| `road_zhending_ji` | 真定→薊 | road | 2 | 40×12 |  | 見 §5.6 |
| `road_ji_zhuo` | 薊→涿 | road | 1 | 24×10 |  | 見 §5.6 |
| `road_ji_yuyang` | 薊→漁陽 | road | 2 | 28×12 |  | 見 §5.6 |
| `road_ji_dai` | 薊→代 | road | 2 | 32×12 |  | 見 §5.6 |
| `road_ji_liaodong` | 薊→遼東 | road | 3 | 48×12 |  | 見 §5.6 |
| `road_changli_liaodong` | 昌黎→遼東 | road | 3 | 36×12 |  | 見 §5.6 |
| `you_wild` | 幽州野原 | wild | 3 | 32×20 |  | `yuyang` ↔ `road_ji_yuyang` |

### 4.7 青州（8）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `jinan` | 濟南 | city | 2 | 28×20 | 青州治 | 多向 |
| `beihai` | 北海 | city | 2 | 24×18 | 孔融 | `road_jinan_beihai` |
| `road_dongjun_jinan` | 東→濟南 | road | 2 | 32×12 |  | 見 §5.7 |
| `road_jinan_beihai` | 濟北海口 | road | 2 | 28×12 |  | 見 §5.7 |
| `road_beihai_donghai` | 北海→東海 | road | 2 | 32×12 | 入徐州 | 見 §5.7 |
| `road_jibei_jinan` | 濟北→濟南 | road | 2 | 24×10 |  | 見 §5.7 |
| `road_jinan_qing_wild` | 濟南野道 | road | 2 | 28×12 | 入青州野 | 見 §5.7 |
| `qing_wild` | 青州野原 | wild | 2 | 32×20 |  | `jinan` ↔ `road_jinan_qing_wild` |

### 4.8 徐州（12）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `pengcheng` | 彭城 | city | 2 | 28×20 | 徐州治 | 多向 |
| `xiapi` | 下邳 | city | 2 | 28×20 | 198 呂布殞命 | `road_pengcheng_xiapi` |
| `donghai` | 東海 | city | 2 | 24×18 |  | `road_xiapi_donghai` ↔ `road_beihai_donghai` |
| `guangling` | 廣陵 | city | 2 | 28×20 |  | `road_guangling_donghai` ↔ `road_guangling_jiankang` |
| `langye` | 琅邪 | city | 2 | 24×18 |  | `road_pengcheng_langye` |
| `road_chenliu_pengcheng` | 陳彭道 | road | 2 | 40×12 |  | 見 §5.8 |
| `road_pei_pengcheng` | 沛彭道 | road | 2 | 32×12 |  | 見 §5.8 |
| `road_pengcheng_xiapi` | 彭下道 | road | 2 | 28×12 |  | 見 §5.8 |
| `road_pengcheng_langye` | 彭琅道 | road | 2 | 32×12 |  | 見 §5.8 |
| `road_xiapi_donghai` | 下東海道 | road | 2 | 28×12 |  | 見 §5.8 |
| `road_guangling_donghai` | 廣東海道 | road | 2 | 32×12 |  | 見 §5.8 |
| `road_guangling_jiankang` | 廣→建業 | road | 2 | 36×12 | 跨江前段 | 見 §5.8 |
| `road_dongping_xu_wild` | 東平→徐州 | road | 2 | 28×12 |  | 見 §5.8 |
| `xu_wild` | 徐州野原 | wild | 2 | 32×20 |  | `langye` ↔ `road_pengcheng_langye` |

### 4.9 荊州（28）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `wancheng` | 宛城 | city | 1 | 32×24 | 184 張曼成；197 曹操失夏侯 | `nanyang_suburb` ↔ `rebel_camp_nanyang` |
| `nanyang_suburb` | 南陽郊野 | suburb | 2 | 28×20 |  | 多向 |
| `xinye` | 新野 | city | 1 | 24×18 | 208 劉備屯新野 | `road_wancheng_xinye` ↔ `longzhong` |
| `longzhong` | 隆中 | wild | 1 | 20×16 | 207 三顧茅廬 | `xinye` |
| `xiangyang` | 襄陽 | city | 2 | 32×24 | 219 關羽水淹七軍前線 | 多向 |
| `jiangling` | 江陵 | city | 2 | 32×24 | 208 赤壁後據點 | 多向 |
| `jiangxia` | 江夏 | city | 2 | 28×20 |  | `road_jiangxia_chibi` ↔ `road_chaisang_jiangxia` |
| `changsha` | 長沙 | city | 2 | 28×20 |  | `road_jiangling_changsha` |
| `gui` | 桂陽 | city | 2 | 24×18 |  | `road_changsha_gui` |
| `ling` | 零陵 | city | 2 | 24×18 |  | `road_changsha_ling` |
| `wuling` | 武陵 | city | 2 | 24×18 |  | `road_jiangling_wuling` |
| `yiling` | 夷陵 | city | 2 | 28×20 | 222 猇亭之戰 | `battlefield_yiling` |
| `chibi` | 赤壁 | ferry | 3 | 24×16 | 208 火燒赤壁 | 長江渡 |
| `nanjun` | 南郡 | suburb | 2 | 24×18 | 江陵近郊 | `road_jiangling_nanjun` |
| `road_jiangling_nanjun` | 江陵→南郡道 | road | 1 | 16×8 |  | 見 §5.9 |
| `badong` | 巴東 | pass | 3 | 28×20 | 三峽口 | `road_yiling_badong` ↔ `road_badong_yongan` |
| `road_runan_wancheng` | 汝宛道 | road | 2 | 36×12 |  | 見 §5.9 |
| `road_wu_wancheng` | 武關→宛城 | road | 3 | 40×12 |  | 見 §5.9 |
| `road_wancheng_xinye` | 宛新道 | road | 2 | 24×10 |  | 見 §5.9 |
| `road_xinye_xiangyang` | 新襄道 | road | 2 | 32×12 |  | 見 §5.9 |
| `road_xiangyang_jiangling` | 襄江道 | road | 2 | 36×12 |  | 見 §5.9 |
| `road_xiangyang_jiangxia` | 襄→江夏 | road | 2 | 32×12 |  | 見 §5.9 |
| `road_jiangling_yiling` | 江陵→夷陵 | road | 2 | 32×12 |  | 見 §5.9 |
| `road_yiling_badong` | 夷陵→巴東 | road | 3 | 28×12 | 三峽 | 見 §5.9 |
| `road_jiangling_changsha` | 江陵→長沙 | road | 2 | 40×12 |  | 見 §5.9 |
| `road_jiangling_wuling` | 江陵→武陵 | road | 2 | 36×12 |  | 見 §5.9 |
| `road_changsha_gui` | 長→桂陽 | road | 2 | 36×12 |  | 見 §5.9 |
| `road_changsha_ling` | 長→零陵 | road | 2 | 32×12 |  | 見 §5.9 |
| `road_jiangxia_chibi` | 江夏→赤壁 | road | 2 | 28×12 |  | 見 §5.9 |
| `road_chibi_jiangling` | 赤壁→江陵 | ferry | 3 | 24×12 | 渡長江 | 見 §5.9 |
| `road_chibi_chaisang` | 赤壁→柴桑 | ferry | 3 | 28×12 |  | 見 §5.9 |
| `battlefield_xiangfan` | 襄樊 | battle | 4 | 32×24 | 219 水淹七軍 | `xiangyang` |
| `battlefield_yiling` | 夷陵戰場 | battle | 4 | 32×24 | 222 猇亭 | `yiling` |
| `rebel_camp_nanyang` | 南陽黃巾營 | camp | 4 | 24×18 | 184 | `wancheng` |
| `jing_wild` | 荊州野原 | wild | 2 | 32×20 |  | `wuling` ↔ `road_jiangling_wuling` |

### 4.10 揚州（22）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `jiankang` | 建業 | city | 0 | 48×36 | 229 吳都；280 晉滅吳 | `jiankang_suburb` |
| `jiankang_suburb` | 建業外郭 | suburb | 1 | 32×24 |  | 多向 |
| `wu` | 吳郡 | city | 1 | 28×20 |  | `road_jiankang_wu` |
| `kuaiji` | 會稽 | city | 1 | 28×20 |  | `road_wu_kuaiji` |
| `danyang` | 丹陽 | city | 1 | 24×18 |  | `road_jiankang_danyang` |
| `lujiang` | 廬江 | city | 2 | 24×18 |  | `road_danyang_lujiang` |
| `hefei` | 合肥 | city | 2 | 28×20 | 215 逍遙津；234 新城 | `battlefield_hefei` |
| `shouchun` | 壽春 | city | 2 | 28×20 | 258 諸葛誕之亂 | `road_hefei_shouchun` |
| `ruxu` | 濡須口 | battle | 3 | 28×20 | 213–217 魏吳对峙 | `road_lujiang_ruxu` |
| `chaisang` | 柴桑 | city | 2 | 28×20 | 208 周瑜前線 | 多向 |
| `road_jiankang_wu` | 建→吳 | road | 1 | 24×10 |  | 見 §5.10 |
| `road_wu_kuaiji` | 吳→會稽 | road | 1 | 32×12 |  | 見 §5.10 |
| `road_jiankang_danyang` | 建→丹陽 | road | 1 | 24×10 |  | 見 §5.10 |
| `road_danyang_lujiang` | 丹→廬江 | road | 2 | 28×12 |  | 見 §5.10 |
| `road_lujiang_ruxu` | 廬→濡須 | road | 2 | 28×12 |  | 見 §5.10 |
| `road_ruxu_hefei` | 濡→合肥 | road | 2 | 32×12 |  | 見 §5.10 |
| `road_hefei_shouchun` | 合→壽春 | road | 2 | 32×12 |  | 見 §5.10 |
| `road_shouchun_runan` | 壽→汝南 | road | 2 | 36×12 | 入豫州 | 見 §5.10 |
| `road_chaisang_jiankang` | 柴→建業 | road | 2 | 32×12 |  | 見 §5.10 |
| `road_chaisang_jiangxia` | 柴→江夏 | road | 2 | 36×12 |  | 見 §5.10 |
| `road_guangling_jiankang` | 廣陵→建業渡 | ferry | 2 | 28×12 | 長江 | 見 §5.10 |
| `battlefield_chibi` | 赤壁（江東岸） | battle | 4 | 32×24 | 208 | `chibi` ↔ `chaisang` |
| `battlefield_hefei` | 合肥新城 | battle | 4 | 28×24 | 234 | `hefei` |
| `yang_wild` | 揚州野原 | wild | 2 | 32×20 |  | `kuaiji` ↔ `road_wu_kuaiji` |

### 4.11 益州（18）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `chengdu` | 成都 | city | 0 | 48×36 | 214 劉備入蜀；263 降魏 | `chengdu_suburb` |
| `chengdu_suburb` | 成都外郭 | suburb | 1 | 32×24 |  | 多向 |
| `hanzhong` | 漢中 | city | 2 | 32×24 | 219 漢中之戰 | `battlefield_hanzhong` |
| `jianmen_pass` | 劍閣關 | pass | 4 | 24×14 | 263 鄧艾破蜀 | 多向 |
| `mianzhu` | 綿竹 | city | 2 | 24×18 | 263 諸葛瞻殞 | `road_chengdu_mianzhu` |
| `zitong` | 梓潼 | city | 2 | 24×18 |  | `road_jianmen_zitong` |
| `yongan` | 永安 | city | 2 | 28×20 | 222 夷陵後防 | `road_badong_yongan` |
| `jianning` | 建寧 | city | 3 | 24×18 | 南中 | `road_chengdu_jianning` |
| `yunnan` | 雲南 | city | 3 | 24×18 | 225 諸葛南征 | `road_jianning_yunnan` |
| `road_changan_hanzhong` | 秦嶺道（長→漢中） | road | 4 | 48×12 | 219 漢中 | 見 §5.11 |
| `road_hanzhong_chengdu` | 漢中→成都 | road | 3 | 44×12 | 金牛道 | 見 §5.11 |
| `road_chengdu_jianmen` | 成都→劍閣 | road | 3 | 36×12 |  | 見 §5.11 |
| `road_jianmen_zitong` | 劍閣→梓潼 | road | 3 | 28×12 |  | 見 §5.11 |
| `road_chengdu_mianzhu` | 成都→綿竹 | road | 2 | 24×10 |  | 見 §5.11 |
| `road_chengdu_jianning` | 成都→建寧 | road | 3 | 48×12 |  | 見 §5.11 |
| `road_jianning_yunnan` | 建寧→雲南 | road | 3 | 36×12 |  | 見 §5.11 |
| `road_badong_yongan` | 巴東→永安 | road | 3 | 32×12 | 三峽 | 見 §5.11 |
| `battlefield_hanzhong` | 漢中戰場 | battle | 4 | 32×24 | 219 | `hanzhong` |
| `yi_wild` | 益州野原 | wild | 2 | 32×20 |  | `zitong` ↔ `road_jianmen_zitong` |

### 4.12 交州（7）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `panyu` | 番禺 | city | 2 | 28×20 | 交州治 | 多向 |
| `jiaozhi` | 交趾 | city | 3 | 24×18 | 士燮據地 | `road_panyu_jiaozhi` |
| `hepu` | 合浦 | city | 2 | 24×18 |  | `road_panyu_hepu` |
| `road_changsha_panyu` | 長→番禺 | road | 3 | 56×12 | 嶺南道 | 見 §5.12 |
| `road_panyu_jiaozhi` | 番→交趾 | road | 3 | 48×12 |  | 見 §5.12 |
| `road_panyu_hepu` | 番→合浦 | road | 2 | 32×12 |  | 見 §5.12 |
| `jiao_wild` | 交州野原 | wild | 3 | 32×20 |  | `hepu` ↔ `road_panyu_hepu` |

### 4.13 涼州（11）

| map_id | 名稱 | 類型 | 危 | 尺寸 | 主要史實 | 連接節點 |
|--------|------|------|----|------|----------|----------|
| `dunhuang` | 敦煌 | city | 3 | 28×20 | 絲綢之路西端 | `road_jiuquan_dunhuang` |
| `wuwei` | 武威 | city | 3 | 28×20 |  | `road_tianshui_wuwei` |
| `zhangye` | 張掖 | city | 3 | 24×18 |  | `road_wuwei_zhangye` |
| `jiuquan` | 酒泉 | city | 3 | 24×18 |  | `road_zhangye_jiuquan` |
| `tianshui` | 天水 | city | 2 | 28×20 | 228 街亭 | `road_changan_tianshui` |
| `road_tianshui_wuwei` | 天水→武威 | road | 3 | 40×12 |  | 見 §5.13 |
| `road_wuwei_zhangye` | 武→張掖 | road | 3 | 44×12 |  | 見 §5.13 |
| `road_zhangye_jiuquan` | 張→酒泉 | road | 3 | 40×12 |  | 見 §5.13 |
| `road_jiuquan_dunhuang` | 酒→敦煌 | road | 3 | 48×12 |  | 見 §5.13 |
| `battlefield_jieting` | 街亭 | battle | 4 | 24×20 | 228 馬謖失街 | `tianshui` |
| `liang_wild` | 涼州野原 | wild | 3 | 32×20 |  | `wuwei` ↔ `road_tianshui_wuwei` |

---

## 5. 完整傳點連接表

> **格式**：`來源地圖:gridX,gridY` → `{ "map": "目標", "x": 入口X, "y": 入口Y }`  
> **座標**：實作 ASCII 地圖時依實際 `@` 位置填入；下表 `TBD` 表示待地圖繪製後定稿。  
> **方向**：出口在來源圖的方位 → 入口在目標圖的對側（北↔南、東↔西）。

### 5.1 司隸

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `luoyang` | 南 | `luoyang_suburb` | 城門 C |
| `luoyang_suburb` | 北 | `road_luoyang_henei` | 北門 |
| `luoyang_suburb` | 東 | `road_luoyang_yingchuan` | 東門 |
| `luoyang_suburb` | 西 | `road_luoyang_hongnong` | 西門 |
| `luoyang_suburb` | 南 | `road_luoyang_xuchang` | 南門 |
| `luoyang_suburb` | 北 | `mengjin_ferry` | 渡黃河 |
| `road_luoyang_henei` | 南 | `luoyang_suburb` | |
| `road_luoyang_henei` | 北 | `henei` | |
| `henei` | 南 | `road_luoyang_henei` | |
| `henei` | 北 | `road_henei_ye` | 入冀州 |
| `road_luoyang_hongnong` | 東 | `luoyang_suburb` | |
| `road_luoyang_hongnong` | 西 | `hongnong` | |
| `hongnong` | 東 | `road_luoyang_hongnong` | |
| `hongnong` | 西 | `road_hongnong_hangu` | |
| `road_hongnong_hangu` | 東 | `hongnong` | |
| `road_hongnong_hangu` | 西 | `hangu_pass` | |
| `hangu_pass` | 東 | `road_hongnong_hangu` | |
| `hangu_pass` | 西 | `road_hangu_changan` | |
| `road_hangu_changan` | 東 | `hangu_pass` | |
| `road_hangu_changan` | 西 | `changan_suburb` | |
| `changan` | 東 | `changan_suburb` | |
| `changan_suburb` | 東 | `road_hangu_changan` | |
| `changan_suburb` | 南 | `road_changan_wu` | 入荊州 |
| `changan_suburb` | 西 | `road_changan_tianshui` | 入涼州 |
| `changan_suburb` | 西南 | `road_changan_hanzhong` | 入益州（褒斜道） |
| `road_changan_wu` | 北 | `changan_suburb` | |
| `road_changan_wu` | 南 | `wu_pass` | |
| `wu_pass` | 北 | `road_changan_wu` | |
| `wu_pass` | 南 | `road_wu_wancheng` | |
| `road_luoyang_xuchang` | 北 | `luoyang_suburb` | |
| `road_luoyang_xuchang` | 南 | `xuchang_suburb` | |
| `xuchang` | 北 | `xuchang_suburb` | |
| `xuchang_suburb` | 北 | `road_luoyang_xuchang` | |
| `xuchang_suburb` | 東 | `road_xuchang_yingchuan` | |
| `xuchang_suburb` | 南 | `battlefield_guandu` | |
| `road_luoyang_yingchuan` | 西 | `luoyang_suburb` | |
| `road_luoyang_yingchuan` | 東 | `yingchuan_suburb` | |
| `mengjin_ferry` | 南 | `luoyang_suburb` | |
| `mengjin_ferry` | 北 | `road_mengjin_baima` | |
| `road_changan_tianshui` | 東 | `changan_suburb` | |
| `road_changan_tianshui` | 西 | `tianshui` | |

### 5.2 豫州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `yingchuan` | 西 | `yingchuan_suburb` | |
| `yingchuan_suburb` | 西 | `road_luoyang_yingchuan` | |
| `yingchuan_suburb` | 東 | `road_chenliu_yingchuan` | 入兗州 |
| `yingchuan_suburb` | 南 | `yingchuan` | |
| `yingchuan` | 北 | `rebel_camp_yingchuan` | 支線 |
| `rebel_camp_yingchuan` | 北 | `yingchuan` | |
| `road_yingchuan_runan` | 北 | `yingchuan_suburb` | |
| `road_yingchuan_runan` | 南 | `runan_suburb` | |
| `runan` | 北 | `runan_suburb` | |
| `runan_suburb` | 北 | `road_yingchuan_runan` | |
| `runan_suburb` | 南 | `road_runan_wancheng` | 入荊州 |
| `runan_suburb` | 東 | `road_shouchun_runan` | 入揚州 |
| `road_xuchang_yingchuan` | 西 | `xuchang_suburb` | |
| `road_xuchang_yingchuan` | 東 | `yingchuan_suburb` | |
| `road_yingchuan_chen` | 西 | `yingchuan` | |
| `road_yingchuan_chen` | 東 | `chen` | |
| `chen` | 西 | `road_yingchuan_chen` | |
| `chen` | 東 | `road_chen_lu` | |
| `road_chen_lu` | 西 | `chen` | |
| `road_chen_lu` | 東 | `lu` | |
| `lu` | 西 | `road_chen_lu` | |
| `lu` | 北 | `road_lu_yanzhou_wild` | |
| `road_runan_pei` | 北 | `runan` | |
| `road_runan_pei` | 南 | `pei` | |
| `pei` | 北 | `road_runan_pei` | |
| `pei` | 東 | `road_pei_pengcheng` | 入徐州 |
| `road_yingchuan_liang` | 西 | `yingchuan` | |
| `road_yingchuan_liang` | 東 | `liang` | |
| `liang` | 西 | `road_yingchuan_liang` | |
| `liang` | 南 | `yu_wild` | |
| `road_lu_yanzhou_wild` | 南 | `lu` | |
| `road_lu_yanzhou_wild` | 北 | `yanzhou_wild` | |

### 5.3 兗州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `chenliu` | 南 | `chenliu_suburb` | |
| `chenliu_suburb` | 北 | `chenliu` | |
| `chenliu_suburb` | 西 | `road_chenliu_yingchuan` | |
| `chenliu_suburb` | 東 | `road_chenliu_dongjun` | |
| `chenliu_suburb` | 南 | `road_chenliu_pengcheng` | 入徐州 |
| `chenliu_suburb` | 北 | `road_chenliu_taishan` | |
| `road_chenliu_yingchuan` | 東 | `chenliu_suburb` | |
| `road_chenliu_yingchuan` | 西 | `yingchuan_suburb` | |
| `road_chenliu_dongjun` | 西 | `chenliu_suburb` | |
| `road_chenliu_dongjun` | 東 | `dongjun` | |
| `dongjun` | 西 | `road_chenliu_dongjun` | |
| `dongjun` | 北 | `baima_ferry` | |
| `dongjun` | 東 | `road_dongjun_jinan` | 入青州 |
| `dongjun` | 南 | `road_dongjun_dongping` | |
| `baima_ferry` | 南 | `dongjun` | |
| `baima_ferry` | 北 | `road_mengjin_baima` | |
| `road_mengjin_baima` | 南 | `mengjin_ferry` | |
| `road_mengjin_baima` | 北 | `baima_ferry` | |
| `road_dongjun_jibei` | 西 | `dongjun` | |
| `road_dongjun_jibei` | 東 | `jibei` | |
| `jibei` | 西 | `road_dongjun_jibei` | |
| `jibei` | 東 | `road_jibei_jinan` | |
| `road_dongjun_dongping` | 北 | `dongjun` | |
| `road_dongjun_dongping` | 南 | `dongping` | |
| `dongping` | 北 | `road_dongjun_dongping` | |
| `dongping` | 東 | `road_dongping_xu_wild` | |
| `dongping` | 西 | `road_dongping_ren` | |
| `road_dongping_ren` | 東 | `dongping` | |
| `road_dongping_ren` | 西 | `ren` | |
| `road_dongjun_jinan` | 西 | `dongjun` | |
| `road_dongjun_jinan` | 東 | `jinan` | |
| `road_chenliu_taishan` | 南 | `chenliu_suburb` | |
| `road_chenliu_taishan` | 北 | `taishan` | |
| `taishan` | 南 | `road_chenliu_taishan` | |
| `road_chenliu_pengcheng` | 北 | `chenliu_suburb` | |
| `road_chenliu_pengcheng` | 南 | `pengcheng` | |
| `battlefield_guandu` | 北 | `xuchang_suburb` | |
| `battlefield_guandu` | 東 | `road_guandu_ye` | |
| `road_guandu_ye` | 西 | `battlefield_guandu` | |
| `road_guandu_ye` | 東 | `ye_suburb` | |
| `yanzhou_wild` | 南 | `road_lu_yanzhou_wild` | |
| `yanzhou_wild` | 北 | `chenliu_suburb` | |

### 5.4 冀州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `ye` | 南 | `ye_suburb` | |
| `ye_suburb` | 北 | `ye` | |
| `ye_suburb` | 南 | `road_henei_ye` | 入司隸 |
| `ye_suburb` | 西 | `road_guandu_ye` | |
| `ye_suburb` | 北 | `road_ye_julu` | |
| `ye_suburb` | 東 | `road_ye_changshan` | |
| `ye_suburb` | 西北 | `road_ye_jingxing` | 入并州 |
| `road_henei_ye` | 南 | `henei` | |
| `road_henei_ye` | 北 | `ye_suburb` | |
| `road_ye_julu` | 南 | `ye_suburb` | |
| `road_ye_julu` | 北 | `julu` | |
| `julu` | 南 | `road_ye_julu` | |
| `julu` | 北 | `rebel_camp_julu` | |
| `julu` | 東 | `road_julu_guangzong` | |
| `julu` | 西 | `road_julu_xiayang` | |
| `rebel_camp_julu` | 南 | `julu` | |
| `road_julu_guangzong` | 西 | `julu` | |
| `road_julu_guangzong` | 東 | `guangzong` | |
| `guangzong` | 西 | `road_julu_guangzong` | |
| `road_julu_xiayang` | 東 | `julu` | |
| `road_julu_xiayang` | 西 | `xiayang` | |
| `xiayang` | 東 | `road_julu_xiayang` | |
| `road_ye_changshan` | 西 | `ye_suburb` | |
| `road_ye_changshan` | 東 | `changshan` | |
| `changshan` | 西 | `road_ye_changshan` | |
| `changshan` | 北 | `road_changshan_zhending` | |
| `road_changshan_zhending` | 南 | `changshan` | |
| `road_changshan_zhending` | 北 | `zhending` | |
| `zhending` | 南 | `road_changshan_zhending` | |
| `zhending` | 北 | `road_zhending_ji` | 入幽州 |
| `road_ye_hejian` | 西 | `ye_suburb` | |
| `road_ye_hejian` | 東 | `hejian` | |
| `hejian` | 西 | `road_ye_hejian` | |
| `hejian` | 北 | `ji_wild` | |
| `road_ye_anping` | 西 | `ye_suburb` | |
| `road_ye_anping` | 東 | `anping` | |
| `anping` | 西 | `road_ye_anping` | |
| `anping` | 北 | `road_anping_changli` | |
| `road_anping_changli` | 南 | `anping` | |
| `road_anping_changli` | 北 | `changli` | |
| `changli` | 南 | `road_anping_changli` | |
| `changli` | 東 | `road_changli_liaodong` | |
| `road_ye_jingxing` | 東 | `ye_suburb` | |
| `road_ye_jingxing` | 西 | `jingxing_pass` | |
| `jingxing_pass` | 東 | `road_ye_jingxing` | |
| `jingxing_pass` | 西 | `road_jingxing_jinyang` | |
| `road_guandu_ye` | 西 | `battlefield_guandu` | |
| `road_guandu_ye` | 東 | `ye_suburb` | |

### 5.5 并州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `road_jingxing_jinyang` | 東 | `jingxing_pass` | |
| `road_jingxing_jinyang` | 西 | `jinyang` | |
| `jinyang` | 東 | `road_jingxing_jinyang` | |
| `jinyang` | 西 | `road_jinyang_taiyuan` | |
| `jinyang` | 南 | `road_shangdang_jinyang` | |
| `road_jinyang_taiyuan` | 東 | `jinyang` | |
| `road_jinyang_taiyuan` | 西 | `taiyuan` | |
| `taiyuan` | 東 | `road_jinyang_taiyuan` | |
| `taiyuan` | 北 | `road_taiyuan_yanmen` | |
| `road_taiyuan_yanmen` | 南 | `taiyuan` | |
| `road_taiyuan_yanmen` | 北 | `yanmen_pass` | |
| `yanmen_pass` | 南 | `road_taiyuan_yanmen` | |
| `yanmen_pass` | 北 | `road_yanmen_you_wild` | 入幽州 |
| `road_shangdang_jinyang` | 北 | `jinyang` | |
| `road_shangdang_jinyang` | 南 | `shangdang` | |
| `shangdang` | 北 | `road_shangdang_jinyang` | |
| `shangdang` | 南 | `bing_wild` | |
| `road_yanmen_you_wild` | 南 | `yanmen_pass` | |
| `road_yanmen_you_wild` | 北 | `dai` | |

### 5.6 幽州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `road_zhending_ji` | 南 | `zhending` | |
| `road_zhending_ji` | 北 | `ji` | |
| `ji` | 南 | `road_zhending_ji` | |
| `ji` | 西 | `road_ji_zhuo` | |
| `ji` | 東 | `road_ji_yuyang` | |
| `ji` | 北 | `road_ji_liaodong` | |
| `ji` | 西南 | `road_ji_dai` | |
| `road_ji_zhuo` | 東 | `ji` | |
| `road_ji_zhuo` | 西 | `zhuo` | |
| `zhuo` | 東 | `road_ji_zhuo` | |
| `road_ji_yuyang` | 西 | `ji` | |
| `road_ji_yuyang` | 東 | `yuyang` | |
| `yuyang` | 西 | `road_ji_yuyang` | |
| `yuyang` | 北 | `you_wild` | |
| `road_ji_dai` | 東 | `ji` | |
| `road_ji_dai` | 西 | `dai` | |
| `dai` | 東 | `road_ji_dai` | |
| `dai` | 北 | `road_yanmen_you_wild` | |
| `road_ji_liaodong` | 南 | `ji` | |
| `road_ji_liaodong` | 北 | `liaodong` | |
| `liaodong` | 南 | `road_ji_liaodong` | |
| `liaodong` | 西 | `road_changli_liaodong` | |
| `road_changli_liaodong` | 西 | `changli` | |
| `road_changli_liaodong` | 東 | `liaodong` | |

### 5.7 青州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `road_dongjun_jinan` | 西 | `dongjun` | |
| `road_dongjun_jinan` | 東 | `jinan` | |
| `jinan` | 西 | `road_dongjun_jinan` | |
| `jinan` | 北 | `road_jibei_jinan` | |
| `jinan` | 東 | `road_jinan_beihai` | |
| `road_jibei_jinan` | 南 | `jibei` | |
| `road_jibei_jinan` | 北 | `jinan` | |
| `road_jinan_beihai` | 西 | `jinan` | |
| `road_jinan_beihai` | 東 | `beihai` | |
| `beihai` | 西 | `road_jinan_beihai` | |
| `beihai` | 南 | `road_beihai_donghai` | 入徐州 |
| `jinan` | 南 | `road_jinan_qing_wild` | |
| `road_jinan_qing_wild` | 北 | `jinan` | |
| `road_jinan_qing_wild` | 南 | `qing_wild` | |
| `qing_wild` | 北 | `road_jinan_qing_wild` | |
| `qing_wild` | 西 | `road_jinan_beihai` | 經北海 |
| `road_beihai_donghai` | 北 | `beihai` | |
| `road_beihai_donghai` | 南 | `donghai` | |

### 5.8 徐州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `road_chenliu_pengcheng` | 北 | `chenliu_suburb` | |
| `road_chenliu_pengcheng` | 南 | `pengcheng` | |
| `road_pei_pengcheng` | 西 | `pei` | |
| `road_pei_pengcheng` | 東 | `pengcheng` | |
| `pengcheng` | 北 | `road_chenliu_pengcheng` | |
| `pengcheng` | 西 | `road_pei_pengcheng` | |
| `pengcheng` | 南 | `road_pengcheng_xiapi` | |
| `pengcheng` | 東 | `road_pengcheng_langye` | |
| `road_pengcheng_xiapi` | 北 | `pengcheng` | |
| `road_pengcheng_xiapi` | 南 | `xiapi` | |
| `xiapi` | 北 | `road_pengcheng_xiapi` | |
| `xiapi` | 東 | `road_xiapi_donghai` | |
| `road_xiapi_donghai` | 西 | `xiapi` | |
| `road_xiapi_donghai` | 東 | `donghai` | |
| `donghai` | 西 | `road_xiapi_donghai` | |
| `donghai` | 北 | `road_beihai_donghai` | |
| `donghai` | 南 | `road_guangling_donghai` | |
| `road_guangling_donghai` | 北 | `donghai` | |
| `road_guangling_donghai` | 南 | `guangling` | |
| `guangling` | 北 | `road_guangling_donghai` | |
| `guangling` | 南 | `road_guangling_jiankang` | 入揚州 |
| `road_pengcheng_langye` | 西 | `pengcheng` | |
| `road_pengcheng_langye` | 東 | `langye` | |
| `langye` | 西 | `road_pengcheng_langye` | |
| `langye` | 南 | `xu_wild` | |
| `road_dongping_xu_wild` | 北 | `dongping` | |
| `road_dongping_xu_wild` | 南 | `pengcheng` | |
| `road_guangling_jiankang` | 北 | `guangling` | |
| `road_guangling_jiankang` | 南 | `jiankang_suburb` | 長江渡 |

### 5.9 荊州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `road_runan_wancheng` | 北 | `runan_suburb` | |
| `road_runan_wancheng` | 南 | `nanyang_suburb` | |
| `road_wu_wancheng` | 北 | `wu_pass` | |
| `road_wu_wancheng` | 南 | `nanyang_suburb` | |
| `nanyang_suburb` | 北 | `road_runan_wancheng` | |
| `nanyang_suburb` | 西北 | `road_wu_wancheng` | |
| `nanyang_suburb` | 南 | `wancheng` | |
| `nanyang_suburb` | 東 | `road_wancheng_xinye` | |
| `wancheng` | 北 | `nanyang_suburb` | |
| `wancheng` | 南 | `rebel_camp_nanyang` | |
| `rebel_camp_nanyang` | 北 | `wancheng` | |
| `road_wancheng_xinye` | 西 | `nanyang_suburb` | |
| `road_wancheng_xinye` | 東 | `xinye` | |
| `xinye` | 西 | `road_wancheng_xinye` | |
| `xinye` | 北 | `longzhong` | |
| `longzhong` | 南 | `xinye` | |
| `xinye` | 東 | `road_xinye_xiangyang` | |
| `road_xinye_xiangyang` | 西 | `xinye` | |
| `road_xinye_xiangyang` | 東 | `xiangyang` | |
| `xiangyang` | 西 | `road_xinye_xiangyang` | |
| `xiangyang` | 南 | `battlefield_xiangfan` | |
| `xiangyang` | 東 | `road_xiangyang_jiangling` | |
| `xiangyang` | 北 | `road_xiangyang_jiangxia` | |
| `battlefield_xiangfan` | 北 | `xiangyang` | |
| `road_xiangyang_jiangling` | 西 | `xiangyang` | |
| `road_xiangyang_jiangling` | 東 | `jiangling` | |
| `jiangling` | 西 | `road_xiangyang_jiangling` | |
| `jiangling` | 北 | `road_chibi_jiangling` | 渡江 |
| `jiangling` | 南 | `road_jiangling_yiling` | |
| `jiangling` | 東 | `road_jiangling_changsha` | |
| `jiangling` | 西北 | `road_jiangling_wuling` | |
| `jiangling` | 東南 | `road_jiangling_nanjun` | |
| `road_jiangling_nanjun` | 西北 | `jiangling` | |
| `road_jiangling_nanjun` | 東南 | `nanjun` | |
| `nanjun` | 西北 | `road_jiangling_nanjun` | |
| `road_jiangling_yiling` | 北 | `jiangling` | |
| `road_jiangling_yiling` | 南 | `yiling` | |
| `yiling` | 北 | `road_jiangling_yiling` | |
| `yiling` | 東 | `battlefield_yiling` | |
| `yiling` | 西 | `road_yiling_badong` | |
| `battlefield_yiling` | 西 | `yiling` | |
| `road_yiling_badong` | 東 | `yiling` | |
| `road_yiling_badong` | 西 | `badong` | |
| `badong` | 東 | `road_yiling_badong` | |
| `badong` | 西 | `road_badong_yongan` | 入益州 |
| `road_xiangyang_jiangxia` | 南 | `xiangyang` | |
| `road_xiangyang_jiangxia` | 北 | `jiangxia` | |
| `jiangxia` | 南 | `road_xiangyang_jiangxia` | |
| `jiangxia` | 東 | `road_jiangxia_chibi` | |
| `jiangxia` | 南 | `road_chaisang_jiangxia` | |
| `road_jiangxia_chibi` | 西 | `jiangxia` | |
| `road_jiangxia_chibi` | 東 | `chibi` | |
| `chibi` | 西 | `road_jiangxia_chibi` | |
| `chibi` | 北 | `road_chibi_jiangling` | |
| `chibi` | 南 | `road_chibi_chaisang` | |
| `chibi` | 東 | `battlefield_chibi` | |
| `road_chibi_jiangling` | 南 | `chibi` | |
| `road_chibi_jiangling` | 北 | `jiangling` | |
| `road_chibi_chaisang` | 北 | `chibi` | |
| `road_chibi_chaisang` | 南 | `chaisang` | |
| `road_jiangling_changsha` | 西 | `jiangling` | |
| `road_jiangling_changsha` | 東 | `changsha` | |
| `changsha` | 西 | `road_jiangling_changsha` | |
| `changsha` | 南 | `road_changsha_gui` | |
| `changsha` | 東 | `road_changsha_ling` | |
| `changsha` | 北 | `road_changsha_panyu` | 入交州 |
| `road_changsha_gui` | 北 | `changsha` | |
| `road_changsha_gui` | 南 | `gui` | |
| `gui` | 北 | `road_changsha_gui` | |
| `road_changsha_ling` | 西 | `changsha` | |
| `road_changsha_ling` | 東 | `ling` | |
| `ling` | 西 | `road_changsha_ling` | |
| `road_jiangling_wuling` | 東 | `jiangling` | |
| `road_jiangling_wuling` | 西 | `wuling` | |
| `wuling` | 東 | `road_jiangling_wuling` | |
| `wuling` | 南 | `jing_wild` | |

### 5.10 揚州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `jiankang` | 北 | `jiankang_suburb` | |
| `jiankang_suburb` | 南 | `jiankang` | |
| `jiankang_suburb` | 西 | `road_jiankang_wu` | |
| `jiankang_suburb` | 北 | `road_guangling_jiankang` | |
| `jiankang_suburb` | 東 | `road_chaisang_jiankang` | |
| `jiankang_suburb` | 南 | `road_jiankang_danyang` | |
| `road_jiankang_wu` | 東 | `jiankang_suburb` | |
| `road_jiankang_wu` | 西 | `wu` | |
| `wu` | 東 | `road_jiankang_wu` | |
| `wu` | 南 | `road_wu_kuaiji` | |
| `road_wu_kuaiji` | 北 | `wu` | |
| `road_wu_kuaiji` | 南 | `kuaiji` | |
| `kuaiji` | 北 | `road_wu_kuaiji` | |
| `kuaiji` | 西 | `yang_wild` | |
| `road_jiankang_danyang` | 北 | `jiankang_suburb` | |
| `road_jiankang_danyang` | 南 | `danyang` | |
| `danyang` | 北 | `road_jiankang_danyang` | |
| `danyang` | 西 | `road_danyang_lujiang` | |
| `road_danyang_lujiang` | 東 | `danyang` | |
| `road_danyang_lujiang` | 西 | `lujiang` | |
| `lujiang` | 東 | `road_danyang_lujiang` | |
| `lujiang` | 北 | `road_lujiang_ruxu` | |
| `road_lujiang_ruxu` | 南 | `lujiang` | |
| `road_lujiang_ruxu` | 北 | `ruxu` | |
| `ruxu` | 南 | `road_lujiang_ruxu` | |
| `ruxu` | 西 | `road_ruxu_hefei` | |
| `road_ruxu_hefei` | 東 | `ruxu` | |
| `road_ruxu_hefei` | 西 | `hefei` | |
| `hefei` | 東 | `road_ruxu_hefei` | |
| `hefei` | 北 | `battlefield_hefei` | |
| `hefei` | 西 | `road_hefei_shouchun` | |
| `battlefield_hefei` | 南 | `hefei` | |
| `road_hefei_shouchun` | 東 | `hefei` | |
| `road_hefei_shouchun` | 西 | `shouchun` | |
| `shouchun` | 東 | `road_hefei_shouchun` | |
| `shouchun` | 北 | `road_shouchun_runan` | 入豫州 |
| `road_shouchun_runan` | 南 | `shouchun` | |
| `road_shouchun_runan` | 北 | `runan_suburb` | |
| `chaisang` | 北 | `road_chibi_chaisang` | |
| `chaisang` | 西 | `road_chaisang_jiangxia` | |
| `chaisang` | 東 | `road_chaisang_jiankang` | |
| `road_chaisang_jiangxia` | 東 | `chaisang` | |
| `road_chaisang_jiangxia` | 西 | `jiangxia` | |
| `road_chaisang_jiankang` | 西 | `chaisang` | |
| `road_chaisang_jiankang` | 東 | `jiankang_suburb` | |
| `road_guangling_jiankang` | 南 | `jiankang_suburb` | |
| `road_guangling_jiankang` | 北 | `guangling` | |
| `battlefield_chibi` | 西 | `chibi` | |

### 5.11 益州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `chengdu` | 北 | `chengdu_suburb` | |
| `chengdu_suburb` | 南 | `chengdu` | |
| `chengdu_suburb` | 北 | `road_hanzhong_chengdu` | |
| `chengdu_suburb` | 東 | `road_chengdu_jianmen` | |
| `chengdu_suburb` | 西 | `road_chengdu_mianzhu` | |
| `chengdu_suburb` | 南 | `road_chengdu_jianning` | |
| `road_changan_hanzhong` | 東北 | `changan_suburb` | 秦嶺 |
| `road_changan_hanzhong` | 西南 | `hanzhong` | |
| `hanzhong` | 東北 | `road_changan_hanzhong` | |
| `hanzhong` | 北 | `battlefield_hanzhong` | |
| `hanzhong` | 東 | `road_hanzhong_chengdu` | |
| `battlefield_hanzhong` | 南 | `hanzhong` | |
| `road_hanzhong_chengdu` | 西 | `hanzhong` | |
| `road_hanzhong_chengdu` | 東 | `chengdu_suburb` | |
| `road_chengdu_jianmen` | 西 | `chengdu_suburb` | |
| `road_chengdu_jianmen` | 東 | `jianmen_pass` | |
| `jianmen_pass` | 西 | `road_chengdu_jianmen` | |
| `jianmen_pass` | 東 | `road_jianmen_zitong` | |
| `road_jianmen_zitong` | 西 | `jianmen_pass` | |
| `road_jianmen_zitong` | 東 | `zitong` | |
| `zitong` | 西 | `road_jianmen_zitong` | |
| `zitong` | 南 | `yi_wild` | |
| `road_chengdu_mianzhu` | 東 | `chengdu_suburb` | |
| `road_chengdu_mianzhu` | 西 | `mianzhu` | |
| `mianzhu` | 東 | `road_chengdu_mianzhu` | |
| `road_chengdu_jianning` | 北 | `chengdu_suburb` | |
| `road_chengdu_jianning` | 南 | `jianning` | |
| `jianning` | 北 | `road_chengdu_jianning` | |
| `jianning` | 南 | `road_jianning_yunnan` | |
| `road_jianning_yunnan` | 北 | `jianning` | |
| `road_jianning_yunnan` | 南 | `yunnan` | |
| `yunnan` | 北 | `road_jianning_yunnan` | |
| `road_badong_yongan` | 東 | `badong` | |
| `road_badong_yongan` | 西 | `yongan` | |
| `yongan` | 東 | `road_badong_yongan` | |

### 5.12 交州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `road_changsha_panyu` | 北 | `changsha` | 嶺南 |
| `road_changsha_panyu` | 南 | `panyu` | |
| `panyu` | 北 | `road_changsha_panyu` | |
| `panyu` | 西 | `road_panyu_hepu` | |
| `panyu` | 南 | `road_panyu_jiaozhi` | |
| `road_panyu_jiaozhi` | 北 | `panyu` | |
| `road_panyu_jiaozhi` | 南 | `jiaozhi` | |
| `jiaozhi` | 北 | `road_panyu_jiaozhi` | |
| `road_panyu_hepu` | 東 | `panyu` | |
| `road_panyu_hepu` | 西 | `hepu` | |
| `hepu` | 東 | `road_panyu_hepu` | |
| `hepu` | 北 | `jiao_wild` | |

### 5.13 涼州

| 來源 | 方向 | 目標 | 備註 |
|------|------|------|------|
| `road_changan_tianshui` | 東 | `changan_suburb` | |
| `road_changan_tianshui` | 西 | `tianshui` | |
| `tianshui` | 東 | `road_changan_tianshui` | |
| `tianshui` | 西 | `road_tianshui_wuwei` | |
| `tianshui` | 南 | `battlefield_jieting` | |
| `battlefield_jieting` | 北 | `tianshui` | |
| `road_tianshui_wuwei` | 東 | `tianshui` | |
| `road_tianshui_wuwei` | 西 | `wuwei` | |
| `wuwei` | 東 | `road_tianshui_wuwei` | |
| `wuwei` | 西 | `road_wuwei_zhangye` | |
| `wuwei` | 南 | `liang_wild` | |
| `road_wuwei_zhangye` | 東 | `wuwei` | |
| `road_wuwei_zhangye` | 西 | `zhangye` | |
| `zhangye` | 東 | `road_wuwei_zhangye` | |
| `zhangye` | 西 | `road_zhangye_jiuquan` | |
| `road_zhangye_jiuquan` | 東 | `zhangye` | |
| `road_zhangye_jiuquan` | 西 | `jiuquan` | |
| `jiuquan` | 東 | `road_zhangye_jiuquan` | |
| `jiuquan` | 西 | `road_jiuquan_dunhuang` | |
| `road_jiuquan_dunhuang` | 東 | `jiuquan` | |
| `road_jiuquan_dunhuang` | 西 | `dunhuang` | |
| `dunhuang` | 東 | `road_jiuquan_dunhuang` | |

### 5.14 傳點統計

| 項目 | 數量 |
|------|------|
| 地圖節點 | 216 |
| 雙向傳送對 | **223 對**（446 條 `teleports` 記錄） |
| 黃河渡 | 孟津↔白馬（2 節點） |
| 長江渡 | 赤壁↔江陵、赤壁↔柴桑、廣陵↔建業（6 節點） |
| 關隘 | 函谷、武關、井陉、雁門、劍閣、巴東（6 節點） |

---

## 6. 天然屏障與不可通行邊界

地圖邊緣以 `#`（山崖）或 `W`/`M`（水／山）封死，**不設 `@` 傳點**。

| 屏障 | 分隔區域 | 唯一通道 map_id |
|------|----------|-----------------|
| **黃河** | 司隸 ↔ 兗州北 | `mengjin_ferry` ↔ `road_mengjin_baima` ↔ `baima_ferry` |
| **長江** | 荊州 ↔ 揚州 | `chibi`（三向：江陵、柴桑、戰場） |
| **長江** | 徐州 ↔ 揚州 | `road_guangling_jiankang` |
| **秦嶺** | 司隸 ↔ 益州 | `road_changan_wu` → `wu_pass` → 南陽；`road_changan_hanzhong` → 漢中 |
| **太行山** | 冀州 ↔ 并州 | `jingxing_pass` |
| **太行山** | 并州 ↔ 幽州 | `yanmen_pass` → `road_yanmen_you_wild` |
| **三峽** | 荊州 ↔ 益州 | `badong` ↔ `road_badong_yongan` ↔ `yongan` |
| **南嶺** | 荊州 ↔ 交州 | `road_changsha_panyu`（長途高遭遇） |
| **隴山走廊** | 司隸 ↔ 涼州 | `road_changan_tianshui` |
| **渤海湾** | 青州 ↔ 遼東 | 不可步行；須走 `road_ji_liaodong` 陸路繞行 |
| **东海** | 徐州／青州东岸 | 不可通行 |

---

## 7. 歷史節點對照（184–280）

### 7.1 時代分期

| 代號 | 年號區間 | 年 | 世界狀態 |
|------|----------|-----|----------|
| E1 | 中平 | 184–189 | 黃巾；董卓入洛前 |
| E2 | 初平–興平 | 190–195 | 董卓、李傕郭汜；群雄割據 |
| E3 | 建安初 | 196–200 | 曹操迎獻帝；官渡之戰 |
| E4 | 建安中 | 201–219 | 赤壁、漢中、襄樊 |
| E5 | 建安末–黄初 | 220–229 | 魏蜀吳建國 |
| E6 | 太和–青龙 | 226–237 | 諸葛北伐；合肥新城 |
| E7 | 正始–咸熙 | 240–265 | 司馬專權；滅蜀 |
| E8 | 太康 | 280 | 晉滅吳，天下統一 |

### 7.2 重大事件 ↔ 地圖

| 年 | 事件 | 地圖 | eraTags |
|----|------|------|---------|
| 184 | 黃巾起事 | `julu`, `rebel_camp_*`, `yingchuan`, `wancheng` | E1 |
| 189 | 董卓入洛 | `luoyang` | E1–E2 |
| 190 | 關東討董 | `hangu_pass`, `road_hangu_changan` | E2 |
| 196 | 遷許 | `xuchang` | E3 |
| 200 | 官渡之戰 | `battlefield_guandu`, `ye` | E3 |
| 208 | 赤壁之戰 | `chibi`, `battlefield_chibi`, `jiangling`, `chaisang` | E4 |
| 219 | 漢中之戰 | `hanzhong`, `battlefield_hanzhong` | E4 |
| 219 | 襄樊之戰 | `battlefield_xiangfan`, `xiangyang` | E4 |
| 222 | 夷陵之戰 | `battlefield_yiling`, `yiling` | E5 |
| 229 | 建業為吳都 | `jiankang` | E5 |
| 228 | 街亭之戰 | `battlefield_jieting`, `tianshui` | E6 |
| 234 | 合肥新城 | `battlefield_hefei` | E6 |
| 263 | 魏滅蜀 | `chengdu`, `mianzhu`, `jianmen_pass` | E7 |
| 280 | 晉滅吳 | `jiankang` | E8 |

### 7.3 各國首都演變（地圖標記）

| 勢力 | 時期 | 首都 map_id | 備註 |
|------|------|-------------|------|
| 東漢 | 184–190 | `luoyang` | 正式帝都 |
| 東漢／傀儡 | 196–220 | `xuchang` | 獻帝駐蹕 |
| 魏 | 220–266 | `luoyang` | 220 遷洛 |
| 蜀 | 221–263 | `chengdu` | |
| 吳 | 229–280 | `jiankang` | 229 遷都 |
| 晉 | 265– | `luoyang` | 265 代魏 |

---

## 8. 醫館、出生點、首都標記

### 8.1 建議預設出生

| 項目 | 值 | 理由 |
|------|-----|------|
| `defaultMap` | `xuchang` | 184 年許縣為潁川屬縣，距黃巾主戰場近，非帝都 |
| 出生座標 | `(8, 8)` TBD | 城內安全區 |

**替代出生點（創角可選）**

| map_id | 名稱 | 背景 |
|--------|------|------|
| `chenliu_suburb` | 陳留難民營 | 兗州線 |
| `runan_suburb` | 汝南鄉勇 | 豫州南 |
| `zhuo` | 涿縣 | 幽州線（劉備故里） |
| `panyu` | 番禺 | 交州邊陲 |

### 8.2 醫館配置

陣亡時依「同地圖優先 → 同州最近城」規則傳送（現有 `HospitalService` 邏輯擴充）。

| hospital_id | 名稱 | map_id | 建議座標 |
|-------------|------|--------|----------|
| `hospital_luoyang` | 太醫署 | `luoyang` | TBD |
| `hospital_xuchang` | 許縣醫廬 | `xuchang` | TBD |
| `hospital_changan` | 長安醫館 | `changan` | TBD |
| `hospital_yingchuan` | 潁川醫館 | `yingchuan` | TBD |
| `hospital_chenliu` | 陳留醫館 | `chenliu` | TBD |
| `hospital_ye` | 鄴城醫館 | `ye` | TBD |
| `hospital_wancheng` | 宛城醫館 | `wancheng` | TBD |
| `hospital_xiangyang` | 襄陽醫館 | `xiangyang` | TBD |
| `hospital_jiangling` | 江陵醫館 | `jiangling` | TBD |
| `hospital_jiankang` | 建業醫館 | `jiankang` | TBD |
| `hospital_chengdu` | 成都醫館 | `chengdu` | TBD |
| `hospital_jinyang` | 晉陽醫館 | `jinyang` | TBD |
| `hospital_panyu` | 番禺醫館 | `panyu` | TBD |

### 8.3 舊圖替換對照

| 舊 map_id | 新 map_id | 說明 |
|-----------|-----------|------|
| `village` | `xuchang` | 新手村 → 許縣 |
| `forest` | `yingchuan_suburb` | 幽暗森林 → 潁川郊野 |

---

## 9. 實作規範

### 9.1 檔案結構

```
client/data/maps/                    server/src/main/resources/maps/
├── maps.json                        ├── maps.json          ← 216 張地圖 + teleports + npcs
├── luoyang.txt                      ├── luoyang.txt
├── road_luoyang_henei.txt           ├── road_luoyang_henei.txt
└── …（每 map_id 一個 .txt）         └── …
```

### 9.2 maps.json 單張地圖範本

```json
"yingchuan": {
  "file": "yingchuan.txt",
  "name": "潁川",
  "eraTags": ["E1", "E3"],
  "maxVisibleEnemies": 2,
  "maxDarkEnemies": 3,
  "visibleEnemies": [],
  "dangerZones": []
}
```

### 9.3 地形字元

| 字元 | 含义 | 可走 | 客戶端渲染 |
|------|------|------|------------|
| `.` | 草地 | ✅ | 綠色地面 |
| `P` `=` | 官道／橋 | ✅ | 土色地面 |
| `@` | 傳送點 | ✅ | 傳送門圖示 |
| `#` | 牆／建築 | ❌ | 牆體立繪 |
| `T` | 樹木 | ❌ | 樹立繪 |
| `W` | 水 | ❌ | 藍色地面 + 水面圖示 |
| `M` | 山岩 | ❌ | 灰褐地面 + 山岩立繪 |

名地手工佈局見 `tools/map_handcraft.py`（洛陽、長安、成都、建業、許縣、赤壁、官渡／赤壁戰場、函谷關、隆中）。

### 9.4 官道圖設計要點

1. 長軸沿 `P`/`=` 鋪設，兩側 `T` 叢林作暗雷 `dangerZones`
2. 每 8～12 格設一休息區（`.` 空曠地，無遭遇）
3. 兩端 `@` 位於長軸端點，與相鄰圖對齊方向
4. 關隘圖中間窄道 `#` 夾擊，寬度 ≤ 3 格

### 9.5 驗證清單（實作後）

- [x] 223 組傳送雙向可达（496 teleports）
- [x] 每張 `battle`/`camp` 至少一條入路
- [x] 13 州每州至少 1 醫館
- [x] `MapServiceTest` + `EncounterServiceTest` 通過
- [x] 客戶端／伺服器 `maps.json` 完全一致
- [x] 明雷間距 ≥ 4 格；危險區地圖有暗雷模板

### 9.6 建議 MapService 擴充

```java
// WALKABLE_CHARS 新增
Set.of(".", "P", "=", "@", "B", "C")
```

---

## 附錄 A：傳點 JSON 範本

實作時將 `TBD` 替換為 ASCII 地圖上 `@` 的實際座標。

```json
{
  "defaultMap": "xuchang",
  "maps": { "...": "見 §4 與附錄 B，共 216 項" },
  "teleports": {
    "luoyang_suburb:24,2":  { "map": "road_luoyang_henei", "x": 20, "y": 10 },
    "road_luoyang_henei:20,10": { "map": "luoyang_suburb", "x": 24, "y": 2 },
    "luoyang_suburb:24,22": { "map": "road_luoyang_yingchuan", "x": 18, "y": 10 },
    "road_luoyang_yingchuan:18,10": { "map": "luoyang_suburb", "x": 24, "y": 22 },
    "luoyang_suburb:2,12":  { "map": "road_luoyang_hongnong", "x": 22, "y": 10 },
    "road_luoyang_hongnong:22,10": { "map": "luoyang_suburb", "x": 2, "y": 12 },
    "luoyang_suburb:24,12": { "map": "road_luoyang_xuchang", "x": 16, "y": 10 },
    "road_luoyang_xuchang:16,10": { "map": "luoyang_suburb", "x": 24, "y": 12 },
    "luoyang_suburb:24,1":  { "map": "mengjin_ferry", "x": 10, "y": 12 },
    "mengjin_ferry:10,12":  { "map": "luoyang_suburb", "x": 24, "y": 1 },
    "mengjin_ferry:10,1":   { "map": "road_mengjin_baima", "x": 12, "y": 10 },
    "road_mengjin_baima:12,10": { "map": "mengjin_ferry", "x": 10, "y": 1 },
    "xuchang:16,2":         { "map": "xuchang_suburb", "x": 14, "y": 18 },
    "xuchang_suburb:14,18": { "map": "xuchang", "x": 16, "y": 2 },
    "yingchuan_suburb:2,10": { "map": "road_luoyang_yingchuan", "x": 34, "y": 10 },
    "road_luoyang_yingchuan:34,10": { "map": "yingchuan_suburb", "x": 2, "y": 10 },
    "chibi:12,6":           { "map": "road_chibi_jiangling", "x": 12, "y": 10 },
    "road_chibi_jiangling:12,10": { "map": "chibi", "x": 12, "y": 6 },
    "chibi:12,8":           { "map": "road_chibi_chaisang", "x": 14, "y": 10 },
    "road_chibi_chaisang:14,10": { "map": "chibi", "x": 12, "y": 8 }
  },
  "npcs": {}
}
```

> 完整 446 條記錄於實作階段依 §5 各表批次生成；可使用腳本從連接表自動產生雙向 JSON。

---

## 附錄 B：地圖 ID 索引

按字母排序，共 **216** 個唯一 map_id。

| # | map_id | 名稱 | 州 |
|---|--------|------|-----|
| 1 | `anping` | 安平 | 冀 |
| 2 | `badong` | 巴東 | 荊 |
| 3 | `baima_ferry` | 白馬津 | 兗 |
| 4 | `battlefield_chibi` | 赤壁（江東岸） | 揚 |
| 5 | `battlefield_guandu` | 官渡 | 兗 |
| 6 | `battlefield_hanzhong` | 漢中戰場 | 益 |
| 7 | `battlefield_hefei` | 合肥新城 | 揚 |
| 8 | `battlefield_jieting` | 街亭 | 涼 |
| 9 | `battlefield_xiangfan` | 襄樊 | 荊 |
| 10 | `battlefield_yiling` | 夷陵戰場 | 荊 |
| 11 | `beihai` | 北海 | 青 |
| 12 | `bing_wild` | 并州野原 | 并 |
| 13 | `chaisang` | 柴桑 | 揚 |
| 14 | `changan` | 長安 | 司 |
| 15 | `changan_suburb` | 長安外郭 | 司 |
| 16 | `changli` | 昌黎 | 冀 |
| 17 | `changsha` | 長沙 | 荊 |
| 18 | `changshan` | 常山真定 | 冀 |
| 19 | `chen` | 陳國平輿 | 豫 |
| 20 | `chengdu` | 成都 | 益 |
| 21 | `chengdu_suburb` | 成都外郭 | 益 |
| 22 | `chenliu` | 陳留 | 兗 |
| 23 | `chenliu_suburb` | 陳留郊野 | 兗 |
| 24 | `chibi` | 赤壁 | 荊 |
| 25 | `dai` | 代 | 幽 |
| 26 | `danyang` | 丹陽 | 揚 |
| 27 | `donghai` | 東海 | 徐 |
| 28 | `dongjun` | 東郡白馬 | 兗 |
| 29 | `dongping` | 東平 | 兗 |
| 30 | `dunhuang` | 敦煌 | 涼 |
| 31 | `guangling` | 廣陵 | 徐 |
| 32 | `guangzong` | 廣宗 | 冀 |
| 33 | `gui` | 桂陽 | 荊 |
| 34 | `hangu_pass` | 函谷關 | 司 |
| 35 | `hanzhong` | 漢中 | 益 |
| 36 | `hefei` | 合肥 | 揚 |
| 37 | `hejian` | 河間 | 冀 |
| 38 | `henei` | 河內溫縣 | 司 |
| 39 | `hepu` | 合浦 | 交 |
| 40 | `hongnong` | 弘農 | 司 |
| 41 | `ji` | 薊 | 幽 |
| 42 | `ji_wild` | 冀州野原 | 冀 |
| 43 | `jiangling` | 江陵 | 荊 |
| 44 | `jiangxia` | 江夏 | 荊 |
| 45 | `jiankang` | 建業 | 揚 |
| 46 | `jiankang_suburb` | 建業外郭 | 揚 |
| 47 | `jianmen_pass` | 劍閣關 | 益 |
| 48 | `jianning` | 建寧 | 益 |
| 49 | `jiao_wild` | 交州野原 | 交 |
| 50 | `jiaozhi` | 交趾 | 交 |
| 51 | `jibei` | 濟北 | 兗 |
| 52 | `jinan` | 濟南 | 青 |
| 53 | `jing_wild` | 荊州野原 | 荊 |
| 54 | `jingxing_pass` | 井陉關 | 冀 |
| 55 | `jinyang` | 晉陽 | 并 |
| 56 | `jiuquan` | 酒泉 | 涼 |
| 57 | `julu` | 鉅鹿 | 冀 |
| 58 | `kuaiji` | 會稽 | 揚 |
| 59 | `langye` | 琅邪 | 徐 |
| 60 | `liang` | 梁國睢陽 | 豫 |
| 61 | `liang_wild` | 涼州野原 | 涼 |
| 62 | `liaodong` | 遼東 | 幽 |
| 63 | `ling` | 零陵 | 荊 |
| 64 | `longzhong` | 隆中 | 荊 |
| 65 | `lu` | 魯國 | 豫 |
| 66 | `lujiang` | 廬江 | 揚 |
| 67 | `luoyang` | 洛陽 | 司 |
| 68 | `luoyang_suburb` | 洛陽外郭 | 司 |
| 69 | `mengjin_ferry` | 孟津渡 | 司 |
| 70 | `mianzhu` | 綿竹 | 益 |
| 71 | `nanjun` | 南郡 | 荊 |
| 72 | `nanyang_suburb` | 南陽郊野 | 荊 |
| 73 | `panyu` | 番禺 | 交 |
| 74 | `pei` | 沛國 | 豫 |
| 75 | `pengcheng` | 彭城 | 徐 |
| 76 | `qing_wild` | 青州野原 | 青 |
| 77 | `rebel_camp_julu` | 鉅鹿太平道壇 | 冀 |
| 78 | `rebel_camp_nanyang` | 南陽黃巾營 | 荊 |
| 79 | `rebel_camp_yingchuan` | 潁川黃巾營 | 豫 |
| 80 | `ren` | 任城 | 兗 |
| 81 | `road_anping_changli` | 安平→昌黎道 | 冀 |
| 82 | `road_badong_yongan` | 巴東→永安 | 益 |
| 83 | `road_beihai_donghai` | 北海→東海 | 青 |
| 84 | `road_chaisang_jiangxia` | 柴→江夏 | 揚 |
| 85 | `road_chaisang_jiankang` | 柴→建業 | 揚 |
| 86 | `road_changan_hanzhong` | 秦嶺道（長→漢中） | 益 |
| 87 | `road_changan_tianshui` | 隴右道（長→天水） | 涼 |
| 88 | `road_changan_wu` | 長安→武關段 | 司 |
| 89 | `road_changli_liaodong` | 昌黎→遼東 | 幽 |
| 90 | `road_changsha_gui` | 長→桂陽 | 荊 |
| 91 | `road_changsha_ling` | 長→零陵 | 荊 |
| 92 | `road_changsha_panyu` | 長→番禺 | 交 |
| 93 | `road_changshan_zhending` | 常山→真定道 | 冀 |
| 94 | `road_chen_lu` | 陳魯道 | 豫 |
| 95 | `road_chengdu_jianmen` | 成都→劍閣 | 益 |
| 96 | `road_chengdu_jianning` | 成都→建寧 | 益 |
| 97 | `road_chengdu_mianzhu` | 成都→綿竹 | 益 |
| 98 | `road_chenliu_dongjun` | 陳東道 | 兗 |
| 99 | `road_chenliu_pengcheng` | 陳彭道 | 徐 |
| 100 | `road_chenliu_taishan` | 陳泰道 | 兗 |
| 101 | `road_chenliu_yingchuan` | 陳潁道 | 兗 |
| 102 | `road_chibi_chaisang` | 赤壁→柴桑 | 荊 |
| 103 | `road_chibi_jiangling` | 赤壁→江陵 | 荊 |
| 104 | `road_danyang_lujiang` | 丹→廬江 | 揚 |
| 105 | `road_dongjun_dongping` | 東平道 | 兗 |
| 106 | `road_dongjun_jibei` | 東濟北道 | 兗 |
| 107 | `road_dongjun_jinan` | 東濟南道 | 青 |
| 108 | `road_dongping_ren` | 東平→任城 | 兗 |
| 109 | `road_dongping_xu_wild` | 東平→徐州 | 徐 |
| 110 | `road_guandu_ye` | 官渡→鄴道 | 冀 |
| 111 | `road_guangling_donghai` | 廣東海道 | 徐 |
| 112 | `road_guangling_jiankang` | 廣→建業 | 揚 |
| 113 | `road_hangu_changan` | 函谷→長安段 | 司 |
| 114 | `road_hanzhong_chengdu` | 漢中→成都 | 益 |
| 115 | `road_hefei_shouchun` | 合→壽春 | 揚 |
| 116 | `road_henei_ye` | 河內→鄴道 | 冀 |
| 117 | `road_hongnong_hangu` | 弘農→函谷段 | 司 |
| 118 | `road_ji_dai` | 薊→代 | 幽 |
| 119 | `road_ji_liaodong` | 薊→遼東 | 幽 |
| 120 | `road_ji_yuyang` | 薊→漁陽 | 幽 |
| 121 | `road_ji_zhuo` | 薊→涿 | 幽 |
| 122 | `road_jiangling_changsha` | 江陵→長沙 | 荊 |
| 123 | `road_jiangling_nanjun` | 江陵→南郡道 | 荊 |
| 124 | `road_jiangling_wuling` | 江陵→武陵 | 荊 |
| 125 | `road_jiangling_yiling` | 江陵→夷陵 | 荊 |
| 126 | `road_jiangxia_chibi` | 江夏→赤壁 | 荊 |
| 127 | `road_jiankang_danyang` | 建→丹陽 | 揚 |
| 128 | `road_jiankang_wu` | 建→吳 | 揚 |
| 129 | `road_jianmen_zitong` | 劍閣→梓潼 | 益 |
| 130 | `road_jianning_yunnan` | 建寧→雲南 | 益 |
| 131 | `road_jibei_jinan` | 濟北→濟南 | 青 |
| 132 | `road_jinan_beihai` | 濟北海口 | 青 |
| 133 | `road_jinan_qing_wild` | 濟南野道 | 青 |
| 134 | `road_jingxing_jinyang` | 井陉→晉陽 | 并 |
| 135 | `road_jinyang_taiyuan` | 晉太道 | 并 |
| 136 | `road_jiuquan_dunhuang` | 酒→敦煌 | 涼 |
| 137 | `road_julu_guangzong` | 鉅鹿→廣宗 | 冀 |
| 138 | `road_julu_xiayang` | 鉅鹿→下曲陽 | 冀 |
| 139 | `road_lu_yanzhou_wild` | 魯國→兗州野 | 兗 |
| 140 | `road_lujiang_ruxu` | 廬→濡須 | 揚 |
| 141 | `road_luoyang_henei` | 洛河官道 | 司 |
| 142 | `road_luoyang_hongnong` | 崤函道（洛→弘） | 司 |
| 143 | `road_luoyang_xuchang` | 洛許官道 | 司 |
| 144 | `road_luoyang_yingchuan` | 嵩洛道 | 豫 |
| 145 | `road_mengjin_baima` | 孟津→白馬渡 | 司 |
| 146 | `road_panyu_hepu` | 番→合浦 | 交 |
| 147 | `road_panyu_jiaozhi` | 番→交趾 | 交 |
| 148 | `road_pei_pengcheng` | 沛彭道 | 徐 |
| 149 | `road_pengcheng_langye` | 彭琅道 | 徐 |
| 150 | `road_pengcheng_xiapi` | 彭下道 | 徐 |
| 151 | `road_runan_pei` | 汝沛道 | 豫 |
| 152 | `road_runan_wancheng` | 汝宛道 | 荊 |
| 153 | `road_ruxu_hefei` | 濡→合肥 | 揚 |
| 154 | `road_shangdang_jinyang` | 上黨→晉陽 | 并 |
| 155 | `road_shouchun_runan` | 壽→汝南 | 豫 |
| 156 | `road_taiyuan_yanmen` | 太雁道 | 并 |
| 157 | `road_tianshui_wuwei` | 天水→武威 | 涼 |
| 158 | `road_wancheng_xinye` | 宛新道 | 荊 |
| 159 | `road_wu_kuaiji` | 吳→會稽 | 揚 |
| 160 | `road_wu_wancheng` | 武關→宛城 | 荊 |
| 161 | `road_wuwei_zhangye` | 武→張掖 | 涼 |
| 162 | `road_xiangyang_jiangling` | 襄江道 | 荊 |
| 163 | `road_xiangyang_jiangxia` | 襄→江夏 | 荊 |
| 164 | `road_xiapi_donghai` | 下東海道 | 徐 |
| 165 | `road_xinye_xiangyang` | 新襄道 | 荊 |
| 166 | `road_xuchang_yingchuan` | 許潁道 | 豫 |
| 167 | `road_yanmen_you_wild` | 雁門→幽州 | 并 |
| 168 | `road_ye_anping` | 鄴→安平 | 冀 |
| 169 | `road_ye_changshan` | 鄴→常山 | 冀 |
| 170 | `road_ye_hejian` | 鄴→河間 | 冀 |
| 171 | `road_ye_jingxing` | 鄴→井陉 | 冀 |
| 172 | `road_ye_julu` | 鄴→鉅鹿道 | 冀 |
| 173 | `road_yiling_badong` | 夷陵→巴東 | 荊 |
| 174 | `road_yingchuan_chen` | 潁陳道 | 豫 |
| 175 | `road_yingchuan_liang` | 潁梁道 | 豫 |
| 176 | `road_yingchuan_runan` | 潁汝道 | 豫 |
| 177 | `road_zhangye_jiuquan` | 張→酒泉 | 涼 |
| 178 | `road_zhending_ji` | 真定→薊 | 幽 |
| 179 | `runan` | 汝南 | 豫 |
| 180 | `runan_suburb` | 汝南郊野 | 豫 |
| 181 | `ruxu` | 濡須口 | 揚 |
| 182 | `shangdang` | 上黨 | 并 |
| 183 | `shouchun` | 壽春 | 揚 |
| 184 | `taishan` | 泰山 | 兗 |
| 185 | `taiyuan` | 太原 | 并 |
| 186 | `tianshui` | 天水 | 涼 |
| 187 | `wancheng` | 宛城 | 荊 |
| 188 | `wu` | 吳郡 | 揚 |
| 189 | `wu_pass` | 武關 | 司 |
| 190 | `wuling` | 武陵 | 荊 |
| 191 | `wuwei` | 武威 | 涼 |
| 192 | `xiangyang` | 襄陽 | 荊 |
| 193 | `xiapi` | 下邳 | 徐 |
| 194 | `xiayang` | 下曲陽 | 冀 |
| 195 | `xinye` | 新野 | 荊 |
| 196 | `xu_wild` | 徐州野原 | 徐 |
| 197 | `xuchang` | 許縣 | 司 |
| 198 | `xuchang_suburb` | 許昌外郭 | 司 |
| 199 | `yang_wild` | 揚州野原 | 揚 |
| 200 | `yanmen_pass` | 雁門關 | 并 |
| 201 | `yanzhou_wild` | 兗州野原 | 兗 |
| 202 | `ye` | 鄴 | 冀 |
| 203 | `ye_suburb` | 鄴郊 | 冀 |
| 204 | `yi_wild` | 益州野原 | 益 |
| 205 | `yiling` | 夷陵 | 荊 |
| 206 | `yingchuan` | 潁川 | 豫 |
| 207 | `yingchuan_suburb` | 潁川郊野 | 豫 |
| 208 | `yongan` | 永安 | 益 |
| 209 | `you_wild` | 幽州野原 | 幽 |
| 210 | `yu_wild` | 豫州野原 | 豫 |
| 211 | `yunnan` | 雲南 | 益 |
| 212 | `yuyang` | 漁陽 | 幽 |
| 213 | `zhangye` | 張掖 | 涼 |
| 214 | `zhending` | 真定 | 冀 |
| 215 | `zhuo` | 涿 | 幽 |
| 216 | `zitong` | 梓潼 | 益 |

---

## 修訂紀錄

| 日期 | 版本 | 說明 |
|------|------|------|
| 2026-06-18 | 1.0 | 初版：216 張地圖、223 對傳點、184–280 全時間線 |

