class_name CharacterStatsData
extends RefCounted

const MIGHT := "might"
const INTELLIGENCE := "intelligence"
const VITALITY := "vitality"
const DEFENSE := "defense"
const SPIRIT := "spirit"
const LUCK := "luck"
const AGILITY := "agility"

const ALL: Array[String] = [MIGHT, INTELLIGENCE, VITALITY, DEFENSE, SPIRIT, LUCK, AGILITY]

const DISPLAY_NAMES := {
	MIGHT: "武力",
	INTELLIGENCE: "智力",
	VITALITY: "體力",
	DEFENSE: "防禦",
	SPIRIT: "精神",
	LUCK: "幸運",
	AGILITY: "敏捷",
}

const BASE_VALUE := 0
const FREE_POINTS := 10

static func display_name(code: String) -> String:
	return DISPLAY_NAMES.get(code, code)

static func zero_base() -> Dictionary:
	return {
		MIGHT: BASE_VALUE,
		INTELLIGENCE: BASE_VALUE,
		VITALITY: BASE_VALUE,
		DEFENSE: BASE_VALUE,
		SPIRIT: BASE_VALUE,
		LUCK: BASE_VALUE,
		AGILITY: BASE_VALUE,
	}

static func from_payload(payload: Variant) -> Dictionary:
	if typeof(payload) != TYPE_DICTIONARY:
		return zero_base()

	var data: Dictionary = payload
	return {
		MIGHT: int(data.get(MIGHT, BASE_VALUE)),
		INTELLIGENCE: int(data.get(INTELLIGENCE, BASE_VALUE)),
		VITALITY: int(data.get(VITALITY, BASE_VALUE)),
		DEFENSE: int(data.get(DEFENSE, BASE_VALUE)),
		SPIRIT: int(data.get(SPIRIT, BASE_VALUE)),
		LUCK: int(data.get(LUCK, BASE_VALUE)),
		AGILITY: int(data.get(AGILITY, BASE_VALUE)),
	}

static func total_points(stats: Dictionary) -> int:
	var total := 0
	for code in ALL:
		total += int(stats.get(code, BASE_VALUE))
	return total

static func remaining_points(stats: Dictionary) -> int:
	return FREE_POINTS - total_points(stats)

static func max_hp(stats: Dictionary) -> int:
	return 50 + int(stats.get(VITALITY, BASE_VALUE)) * 5

static func summary_text(stats: Dictionary) -> String:
	var parts: PackedStringArray = []
	for code in ALL:
		parts.append("%s %d" % [display_name(code), int(stats.get(code, BASE_VALUE))])
	return "  ".join(parts)

static func compact_text(stats: Dictionary) -> String:
	return "武%d 智%d 體%d 防%d 精%d 幸%d 敏%d" % [
		int(stats.get(MIGHT, BASE_VALUE)),
		int(stats.get(INTELLIGENCE, BASE_VALUE)),
		int(stats.get(VITALITY, BASE_VALUE)),
		int(stats.get(DEFENSE, BASE_VALUE)),
		int(stats.get(SPIRIT, BASE_VALUE)),
		int(stats.get(LUCK, BASE_VALUE)),
		int(stats.get(AGILITY, BASE_VALUE)),
	]
