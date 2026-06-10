class_name SkillElementData
extends RefCounted

const FIRE := "FIRE"
const WIND := "WIND"
const EARTH := "EARTH"
const THUNDER := "THUNDER"
const WATER := "WATER"
const UNIVERSAL := "UNIVERSAL"

const DISPLAY_NAMES := {
	FIRE: "火",
	WIND: "風",
	EARTH: "土",
	THUNDER: "雷",
	WATER: "水",
	UNIVERSAL: "通用",
}

static func display_name(code: String) -> String:
	return DISPLAY_NAMES.get(code, code)
