class_name MapRegistry
extends RefCounted

const CONFIG_PATH := "res://data/maps/maps.json"
const MAPS_DIR := "res://data/maps/"

static var _maps: Dictionary = {}
static var _teleports: Dictionary = {}
static var _default_map_id := "village"
static var _loaded := false

static func ensure_loaded() -> void:
	if _loaded:
		return

	var file := FileAccess.open(CONFIG_PATH, FileAccess.READ)
	if file == null:
		push_error("無法讀取地圖設定: %s" % CONFIG_PATH)
		_loaded = true
		return

	var json := JSON.new()
	if json.parse(file.get_as_text()) != OK:
		push_error("地圖設定 JSON 格式錯誤")
		file.close()
		_loaded = true
		return
	file.close()

	var data: Dictionary = json.data
	_default_map_id = str(data.get("defaultMap", "village"))
	_maps = data.get("maps", {})
	_teleports = data.get("teleports", {})
	_loaded = true

static func get_default_map_id() -> String:
	ensure_loaded()
	return _default_map_id

static func get_map_path(map_id: String) -> String:
	ensure_loaded()
	var map_info: Variant = _maps.get(map_id)
	if typeof(map_info) != TYPE_DICTIONARY:
		return MAPS_DIR + "village.txt"
	return MAPS_DIR + str(map_info.get("file", "village.txt"))

static func get_map_name(map_id: String) -> String:
	ensure_loaded()
	var map_info: Variant = _maps.get(map_id)
	if typeof(map_info) != TYPE_DICTIONARY:
		return map_id
	return str(map_info.get("name", map_id))

static func get_teleport_target(map_id: String, grid_x: int, grid_y: int) -> Dictionary:
	ensure_loaded()
	var key := "%s:%d,%d" % [map_id, grid_x, grid_y]
	var target: Variant = _teleports.get(key)
	if typeof(target) != TYPE_DICTIONARY:
		return {}
	return {
		"map": str(target.get("map", "")),
		"x": int(target.get("x", 0)),
		"y": int(target.get("y", 0)),
	}

static func is_teleport_tile(ch: String) -> bool:
	return ch == "@"
