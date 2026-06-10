class_name BattleSlot
extends Button

signal slot_pressed(slot_index: int, unit_id: int)

const EMPTY_TEXT := "—"

var slot_index: int = -1
var unit_id: int = -1
var unit_data: Dictionary = {}
var _occupied := false
var _actor_highlight := false
var _home_position := Vector2.ZERO
var _home_modulate := Color.WHITE
var _active_tween: Tween

func _ready() -> void:
	custom_minimum_size = Vector2(96, 76)
	flat = false
	pressed.connect(_on_pressed)
	clear_unit()

func setup(index: int) -> void:
	slot_index = index
	clear_unit()

func set_unit(data: Dictionary) -> void:
	unit_id = int(data.get("id", 0))
	unit_data = data
	if unit_id <= 0:
		clear_unit()
		return

	var alive := bool(data.get("alive", true))
	var element_name := str(data.get("elementName", ""))
	var level := int(data.get("level", 1))
	var hp_line := "%d / %d" % [int(data.get("hp", 0)), int(data.get("maxHp", 0))]
	if alive:
		_occupied = true
		text = "%s Lv.%d（%s）\n%s" % [data.get("name", ""), level, element_name, hp_line]
		disabled = true
		_apply_actor_visual()
	else:
		_occupied = false
		text = "%s Lv.%d（%s）\n%s\n[倒下]" % [data.get("name", ""), level, element_name, hp_line]
		disabled = true
		modulate = Color(0.45, 0.45, 0.45, 0.55)

func clear_unit() -> void:
	unit_id = -1
	unit_data = {}
	_occupied = false
	_actor_highlight = false
	text = EMPTY_TEXT
	disabled = true
	modulate = Color(0.55, 0.55, 0.55, 0.65)

func set_actor_highlight(highlight: bool) -> void:
	_actor_highlight = highlight
	_apply_actor_visual()

func set_actor_choosable(choosable: bool) -> void:
	if not _occupied or not bool(unit_data.get("alive", true)):
		disabled = true
		return
	disabled = not choosable
	_apply_actor_visual()

func _apply_actor_visual() -> void:
	if not _occupied:
		return
	if not bool(unit_data.get("alive", true)):
		modulate = Color(0.45, 0.45, 0.45, 0.55)
	elif _actor_highlight:
		modulate = Color(0.75, 1.15, 1.2)
	elif not disabled:
		modulate = Color(0.9, 1.05, 0.9)
	else:
		modulate = Color.WHITE

func set_selectable(selectable: bool) -> void:
	if not _occupied:
		return
	disabled = not selectable
	if selectable:
		modulate = Color(1.15, 1.1, 0.75)
	else:
		modulate = Color.WHITE

func _on_pressed() -> void:
	if _occupied:
		slot_pressed.emit(slot_index, unit_id)

func capture_home_transform() -> void:
	_home_position = position
	_home_modulate = modulate

func play_attack_animation(target_center: Vector2, critical := false) -> void:
	_stop_animation()
	capture_home_transform()
	pivot_offset = size * 0.5

	var my_center := global_position + size * 0.5
	var direction := (target_center - my_center).normalized()
	var lunge_distance := 34.0 if critical else 26.0
	var lunge := direction * lunge_distance
	var peak_scale := Vector2(1.14, 1.14) if critical else Vector2(1.08, 1.08)

	_active_tween = create_tween()
	_active_tween.set_trans(Tween.TRANS_QUAD)
	_active_tween.set_ease(Tween.EASE_OUT)
	_active_tween.tween_property(self, "position", _home_position + lunge, 0.11)
	_active_tween.parallel().tween_property(self, "scale", peak_scale, 0.11)
	_active_tween.tween_property(self, "position", _home_position, 0.13)
	_active_tween.parallel().tween_property(self, "scale", Vector2.ONE, 0.13)
	await _active_tween.finished
	_reset_animation_state()

func play_hit_animation(critical := false) -> void:
	_stop_animation()
	capture_home_transform()

	var flash_color := Color(1.0, 0.25, 0.25) if critical else Color(1.0, 0.5, 0.45)
	var shake := 12.0 if critical else 7.0

	_active_tween = create_tween()
	_active_tween.set_trans(Tween.TRANS_SINE)
	_active_tween.tween_property(self, "modulate", flash_color, 0.05)
	_active_tween.tween_property(self, "position", _home_position + Vector2(shake, 0), 0.04)
	_active_tween.tween_property(self, "position", _home_position + Vector2(-shake * 0.7, 0), 0.04)
	_active_tween.tween_property(self, "position", _home_position, 0.05)
	_active_tween.parallel().tween_property(self, "modulate", _home_modulate, 0.12)
	await _active_tween.finished
	_reset_animation_state()

func _stop_animation() -> void:
	if _active_tween != null and _active_tween.is_valid():
		_active_tween.kill()
	_active_tween = null

func _reset_animation_state() -> void:
	position = _home_position
	scale = Vector2.ONE
	modulate = _home_modulate

func play_capture_animation() -> void:
	_stop_animation()
	capture_home_transform()
	pivot_offset = size * 0.5

	_active_tween = create_tween()
	_active_tween.set_trans(Tween.TRANS_SINE)
	_active_tween.tween_property(self, "modulate", Color(0.55, 1.0, 0.65), 0.12)
	_active_tween.parallel().tween_property(self, "scale", Vector2(1.12, 1.12), 0.12)
	_active_tween.tween_property(self, "modulate", _home_modulate, 0.18)
	_active_tween.parallel().tween_property(self, "scale", Vector2.ONE, 0.18)
	await _active_tween.finished
	_reset_animation_state()
