class_name EncounterCooldown
extends RefCounted

const VISIBLE_MASK_MS := 20000
const GLOBAL_COOLDOWN_MS := 5000
const DARK_COOLDOWN_MS := 3000

var _global_until_ms := 0
var _chase_until_ms := 0
var _dark_until_ms := 0
var _masked_enemies: Dictionary = {}

func apply_server_snapshot(payload: Dictionary) -> void:
	var now := Time.get_ticks_msec()
	if payload.has("noVisibleEncounterMs"):
		_global_until_ms = maxi(_global_until_ms, now + int(payload.get("noVisibleEncounterMs", 0)))
	elif payload.has("encounterCooldownMs"):
		_global_until_ms = maxi(_global_until_ms, now + int(payload.get("encounterCooldownMs", 0)))
	if payload.has("chaseCooldownMs"):
		_chase_until_ms = maxi(_chase_until_ms, now + int(payload.get("chaseCooldownMs", 0)))
	if payload.has("darkEncounterCooldownMs"):
		_dark_until_ms = maxi(_dark_until_ms, now + int(payload.get("darkEncounterCooldownMs", 0)))
	var masked: Variant = payload.get("maskedVisibleEnemies", {})
	if typeof(masked) == TYPE_DICTIONARY:
		for enemy_id in masked.keys():
			_masked_enemies[str(enemy_id)] = now + int(masked[enemy_id])

func apply_battle_end(visible_enemy_id: String, from_danger_zone: bool) -> void:
	var now := Time.get_ticks_msec()
	_global_until_ms = now + GLOBAL_COOLDOWN_MS
	_chase_until_ms = now + GLOBAL_COOLDOWN_MS
	_dark_until_ms = now + (DARK_COOLDOWN_MS if from_danger_zone else GLOBAL_COOLDOWN_MS)
	if not visible_enemy_id.is_empty():
		_masked_enemies[visible_enemy_id] = now + VISIBLE_MASK_MS

func is_enemy_masked(enemy_id: String) -> bool:
	_cleanup_expired()
	return _masked_enemies.has(enemy_id)

func _cleanup_expired() -> void:
	var now := Time.get_ticks_msec()
	for enemy_id in _masked_enemies.keys():
		if int(_masked_enemies[enemy_id]) <= now:
			_masked_enemies.erase(enemy_id)

func clear() -> void:
	_global_until_ms = 0
	_chase_until_ms = 0
	_dark_until_ms = 0
	_masked_enemies.clear()
