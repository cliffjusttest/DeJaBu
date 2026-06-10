class_name ElementData
extends RefCounted

const FIRE := "FIRE"
const WIND := "WIND"
const EARTH := "EARTH"
const THUNDER := "THUNDER"
const WATER := "WATER"
const NONE := "NONE"

const SELECTABLE: Array[String] = [FIRE, WIND, EARTH, THUNDER, WATER]

const DISPLAY_NAMES := {
	FIRE: "火",
	WIND: "風",
	EARTH: "土",
	THUNDER: "雷",
	WATER: "水",
	NONE: "無",
}

static func display_name(code: String) -> String:
	return DISPLAY_NAMES.get(code, code)

static func selectable_options() -> Array[Dictionary]:
	var options: Array[Dictionary] = []
	for code in SELECTABLE:
		options.append({"code": code, "label": display_name(code)})
	return options
