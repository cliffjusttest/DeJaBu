extends Node

const CONFIG_PATH := "user://battle_hotkeys.cfg"
const HOTKEY_COUNT := 12

var _cfg := ConfigFile.new()

func _ready() -> void:
	_cfg.load(CONFIG_PATH)

func get_skill_hotkey(actor_slot: int, hotkey_index: int) -> int:
	return _cfg.get_value("skills_slot_%d" % actor_slot, "key_%d" % hotkey_index, -1)

func set_skill_hotkey(actor_slot: int, hotkey_index: int, skill_id: int) -> void:
	_cfg.set_value("skills_slot_%d" % actor_slot, "key_%d" % hotkey_index, skill_id)
	_cfg.save(CONFIG_PATH)

func get_item_hotkey(hotkey_index: int) -> int:
	return _cfg.get_value("items", "key_%d" % hotkey_index, -1)

func set_item_hotkey(hotkey_index: int, item_id: int) -> void:
	_cfg.set_value("items", "key_%d" % hotkey_index, item_id)
	_cfg.save(CONFIG_PATH)
