class_name BattleScene
extends CanvasLayer

signal battle_action_requested(action: String, target_id: int, actor_id: int, skill_id: int)

const SLOTS_PER_ROW := 5
const ROW_COUNT := 2
const MAX_SLOTS := SLOTS_PER_ROW * ROW_COUNT

enum TargetMode { NONE, ATTACK, CAPTURE, SKILL }

@onready var dim_overlay: ColorRect = $DimOverlay
@onready var enemy_grid: GridContainer = $FormationRoot/CenterColumn/EnemySection/EnemyGrid
@onready var ally_grid: GridContainer = $FormationRoot/CenterColumn/AllySection/AllyGrid
@onready var player_hp_label: Label = $BattleUI/Margin/VBox/StatsRow/PlayerHpLabel
@onready var player_stats_label: Label = $BattleUI/Margin/VBox/PlayerStatsLabel
@onready var enemy_hp_label: Label = $BattleUI/Margin/VBox/StatsRow/EnemyHpLabel
@onready var log_label: RichTextLabel = $BattleUI/Margin/VBox/LogLabel
@onready var attack_button: Button = $BattleUI/Margin/VBox/ActionsRow/AttackButton
@onready var capture_button: Button = $BattleUI/Margin/VBox/ActionsRow/CaptureButton
@onready var hint_label: Label = $BattleUI/Margin/VBox/HintLabel
@onready var skills_row: HBoxContainer = $BattleUI/Margin/VBox/SkillsRow

var _enemy_slots: Array[BattleSlot] = []
var _ally_slots: Array[BattleSlot] = []
var _target_mode := TargetMode.NONE
var _actions_enabled := true
var _selected_actor_id := -1
var _planned_actor_ids: Array = []
var _pending_skill_id := -1
var _pending_skill_targets_allies := false

func _ready() -> void:
	_build_formation_grids()
	hide()

func show_battle(battle_data: Dictionary) -> void:
	_cancel_target_selection()
	_actions_enabled = true
	_sync_actor_state(battle_data)
	_update_action_buttons()
	update_battle_data(battle_data)
	log_label.clear()
	show()

func hide_battle() -> void:
	_cancel_target_selection()
	hide()

func update_battle_data(battle_data: Dictionary) -> void:
	_render_formation(enemy_grid, _enemy_slots, battle_data.get("enemies", []))
	_render_formation(ally_grid, _ally_slots, battle_data.get("allies", []))
	_sync_actor_state(battle_data)

	var player_element := str(
		battle_data.get("playerElementName", ElementData.display_name(str(battle_data.get("playerElement", ""))))
	)
	var player_level := int(battle_data.get("playerLevel", GameState.player_level))
	player_hp_label.text = "%s Lv.%d（%s） HP: %d / %d" % [
		battle_data.get("playerName", GameState.player_name),
		player_level,
		player_element,
		int(battle_data.get("playerHp", 0)),
		int(battle_data.get("playerMaxHp", 0))
	]
	var stats := CharacterStatsData.from_payload(battle_data.get("playerStats", GameState.player_stats))
	player_stats_label.text = CharacterStatsData.summary_text(stats)
	enemy_hp_label.text = "%s 總 HP: %d / %d" % [
		battle_data.get("enemyName", "敵方"),
		int(battle_data.get("enemyHp", 0)),
		int(battle_data.get("enemyMaxHp", 0))
	]

	_refresh_target_highlights()
	_refresh_actor_highlights()
	_render_skill_buttons()

func append_log(text: String) -> void:
	log_label.append_text(text + "\n")

func set_actions_enabled(enabled: bool) -> void:
	_actions_enabled = enabled
	_update_action_buttons()

func play_attack_animations(events: Array) -> void:
	await get_tree().process_frame
	for event in events:
		if typeof(event) != TYPE_DICTIONARY:
			continue
		await _play_attack_event(event)
		await get_tree().create_timer(0.1).timeout

func play_capture_animation(target_id: int) -> void:
	var target := _find_slot_by_unit_id(target_id)
	if target == null:
		return
	await target.play_capture_animation()

func _play_attack_event(event: Dictionary) -> void:
	var attacker_id := int(event.get("attackerId", 0))
	var defender_id := int(event.get("defenderId", 0))
	var critical := bool(event.get("critical", false))
	if attacker_id <= 0 or defender_id <= 0:
		return

	var attacker := _find_slot_by_unit_id(attacker_id)
	var defender := _find_slot_by_unit_id(defender_id)
	if attacker == null or defender == null:
		return

	attacker.capture_home_transform()
	defender.capture_home_transform()
	var defender_center := defender.global_position + defender.size * 0.5
	await attacker.play_attack_animation(defender_center, critical)
	await defender.play_hit_animation(critical)

func _find_slot_by_unit_id(unit_id: int) -> BattleSlot:
	for slot in _ally_slots:
		if slot.unit_id == unit_id:
			return slot
	for slot in _enemy_slots:
		if slot.unit_id == unit_id:
			return slot
	return null

func _build_formation_grids() -> void:
	_enemy_slots = _create_slots(enemy_grid, true)
	_ally_slots = _create_slots(ally_grid, false)

func _create_slots(grid: GridContainer, is_enemy: bool) -> Array[BattleSlot]:
	var slots: Array[BattleSlot] = []
	grid.columns = SLOTS_PER_ROW
	for index in range(MAX_SLOTS):
		var slot := BattleSlot.new()
		slot.setup(index)
		if is_enemy:
			slot.slot_pressed.connect(_on_enemy_slot_pressed)
		else:
			slot.slot_pressed.connect(_on_ally_slot_pressed)
		grid.add_child(slot)
		slots.append(slot)
	return slots

func _render_formation(grid: GridContainer, slots: Array[BattleSlot], units: Variant) -> void:
	for slot in slots:
		slot.clear_unit()

	if typeof(units) != TYPE_ARRAY:
		return

	var unit_list: Array = units
	for unit_data in unit_list:
		if typeof(unit_data) != TYPE_DICTIONARY:
			continue
		var data: Dictionary = unit_data
		var slot_index := int(data.get("slot", -1))
		if slot_index < 0 or slot_index >= slots.size():
			continue
		slots[slot_index].set_unit(data)

func _sync_actor_state(battle_data: Dictionary) -> void:
	_selected_actor_id = int(battle_data.get("activeActorId", _selected_actor_id))
	_planned_actor_ids = []
	var planned: Variant = battle_data.get("plannedActorIds", [])
	if typeof(planned) == TYPE_ARRAY:
		for actor_id in planned:
			_planned_actor_ids.append(int(actor_id))

func _enter_target_selection(mode: TargetMode) -> void:
	if not _actions_enabled:
		return
	_target_mode = mode
	if mode == TargetMode.CAPTURE:
		hint_label.text = "本回合改為捕捉：請點選單一敵方目標（失敗浪費回合，Esc 取消）"
		capture_button.text = "取消捕捉"
	elif mode == TargetMode.SKILL:
		if _pending_skill_targets_allies:
			hint_label.text = "請點選我方單位作為技能目標（Esc 取消）"
		else:
			hint_label.text = "請點選敵方單位作為技能目標（Esc 取消）"
	else:
		hint_label.text = "請點選敵方單位作為攻擊目標（Esc 取消）"
		attack_button.text = "取消攻擊"
	_refresh_target_highlights()
	_refresh_actor_highlights()

func _cancel_target_selection() -> void:
	_target_mode = TargetMode.NONE
	_pending_skill_id = -1
	_pending_skill_targets_allies = false
	hint_label.text = _default_hint_text()
	attack_button.text = "1 - 攻擊"
	capture_button.text = "4 - 捕捉"
	_refresh_target_highlights()
	_refresh_actor_highlights()

func _default_hint_text() -> String:
	var actor_name := _selected_actor_name()
	if _is_companion_actor():
		return "為 %s 指定行動 | 點選未指定的單位切換 | 1 攻擊 / 2 防禦 / 3 逃跑" % actor_name
	return "為 %s 指定行動 | 點選未指定的單位切換 | 1 攻擊 / 4 捕捉 / 2 防禦 / 3 逃跑" % actor_name

func _selected_actor_name() -> String:
	for slot in _ally_slots:
		if slot.unit_id == _selected_actor_id:
			return str(slot.unit_data.get("name", "單位"))
	return "單位"

func _is_companion_actor() -> bool:
	for slot in _ally_slots:
		if slot.unit_id == _selected_actor_id:
			return bool(slot.unit_data.get("companion", false))
	return false

func _has_planned(actor_id: int) -> bool:
	return actor_id in _planned_actor_ids

func _refresh_target_highlights() -> void:
	var selecting := _target_mode != TargetMode.NONE
	for slot in _enemy_slots:
		slot.set_selectable(false)
	for slot in _ally_slots:
		if slot.unit_id > 0:
			slot.set_selectable(false)

	if _target_mode == TargetMode.SKILL and _pending_skill_targets_allies:
		for slot in _ally_slots:
			var can_select := selecting and slot.unit_id > 0 and bool(slot.unit_data.get("alive", true))
			slot.set_selectable(can_select)
		return

	for slot in _enemy_slots:
		var capturable := bool(slot.unit_data.get("capturable", true)) if slot.unit_data else true
		var can_select := selecting and slot.unit_id > 0
		if _target_mode == TargetMode.CAPTURE:
			can_select = can_select and capturable
		elif _target_mode == TargetMode.SKILL:
			can_select = can_select and bool(slot.unit_data.get("alive", true))
		slot.set_selectable(can_select)

func _refresh_actor_highlights() -> void:
	var selecting_target := _target_mode != TargetMode.NONE
	for slot in _ally_slots:
		var alive := slot.unit_id > 0 and bool(slot.unit_data.get("alive", true))
		if _target_mode == TargetMode.SKILL and _pending_skill_targets_allies:
			slot.set_actor_highlight(false)
			continue
		var can_choose := (
			_actions_enabled
			and not selecting_target
			and alive
			and not _has_planned(slot.unit_id)
		)
		slot.set_actor_choosable(can_choose)
		slot.set_actor_highlight(slot.unit_id == _selected_actor_id and alive)

func _on_enemy_slot_pressed(_slot_index: int, unit_id: int) -> void:
	if _target_mode == TargetMode.NONE or not _actions_enabled:
		return
	if _target_mode == TargetMode.SKILL:
		var skill_id := _pending_skill_id
		_cancel_target_selection()
		battle_action_requested.emit("skill", unit_id, _selected_actor_id, skill_id)
		return
	var action := "capture" if _target_mode == TargetMode.CAPTURE else "attack"
	_cancel_target_selection()
	battle_action_requested.emit(action, unit_id, _selected_actor_id, -1)

func _on_ally_slot_pressed(_slot_index: int, unit_id: int) -> void:
	if not _actions_enabled:
		return
	if _target_mode == TargetMode.SKILL and _pending_skill_targets_allies:
		var skill_id := _pending_skill_id
		_cancel_target_selection()
		battle_action_requested.emit("skill", unit_id, _selected_actor_id, skill_id)
		return
	if _target_mode != TargetMode.NONE:
		return
	if _has_planned(unit_id):
		return
	_selected_actor_id = unit_id
	_refresh_actor_highlights()
	_update_action_buttons()
	hint_label.text = _default_hint_text()

func _unhandled_input(event: InputEvent) -> void:
	if not visible or not _actions_enabled:
		return
	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_1:
				_on_attack_pressed()
				get_viewport().set_input_as_handled()
			KEY_2:
				_cancel_target_selection()
				battle_action_requested.emit("defend", -1, _selected_actor_id, -1)
				get_viewport().set_input_as_handled()
			KEY_3:
				_cancel_target_selection()
				battle_action_requested.emit("flee", -1, _selected_actor_id, -1)
				get_viewport().set_input_as_handled()
			KEY_4:
				if not _is_companion_actor():
					_on_capture_pressed()
				get_viewport().set_input_as_handled()
			KEY_ESCAPE:
				if _target_mode != TargetMode.NONE:
					_cancel_target_selection()
					get_viewport().set_input_as_handled()

func _on_attack_pressed() -> void:
	if not _actions_enabled:
		return
	if _target_mode == TargetMode.ATTACK:
		_cancel_target_selection()
		return
	if _target_mode == TargetMode.CAPTURE:
		_cancel_target_selection()
	_enter_target_selection(TargetMode.ATTACK)

func _on_capture_pressed() -> void:
	if not _actions_enabled or _is_companion_actor():
		return
	if _target_mode == TargetMode.CAPTURE:
		_cancel_target_selection()
		return
	if _target_mode == TargetMode.ATTACK:
		_cancel_target_selection()
	_enter_target_selection(TargetMode.CAPTURE)

func _on_defend_pressed() -> void:
	if not _actions_enabled:
		return
	_cancel_target_selection()
	battle_action_requested.emit("defend", -1, _selected_actor_id, -1)

func _on_flee_pressed() -> void:
	if not _actions_enabled:
		return
	_cancel_target_selection()
	battle_action_requested.emit("flee", -1, _selected_actor_id, -1)

func _on_skill_pressed(skill_id: int, targets_allies: bool) -> void:
	if not _actions_enabled:
		return
	_cancel_target_selection()
	_pending_skill_id = skill_id
	_pending_skill_targets_allies = targets_allies
	_enter_target_selection(TargetMode.SKILL)

func _active_actor_skills() -> Array:
	for slot in _ally_slots:
		if slot.unit_id == _selected_actor_id:
			var skills: Variant = slot.unit_data.get("skills", [])
			if typeof(skills) == TYPE_ARRAY:
				return skills
	return []

func _render_skill_buttons() -> void:
	for child in skills_row.get_children():
		child.queue_free()

	var skills := _active_actor_skills()
	if skills.is_empty():
		var empty := Label.new()
		empty.text = "無可用技能"
		skills_row.add_child(empty)
		return

	for entry in skills:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var skill: Dictionary = entry
		var button := Button.new()
		var skill_id := int(skill.get("skillId", 0))
		var skill_level := int(skill.get("skillLevel", 1))
		var cd := int(skill.get("cooldownRemaining", 0))
		var can_use := bool(skill.get("canUse", true))
		button.text = "%s Lv.%d" % [skill.get("name", "技能"), skill_level]
		if cd > 0:
			button.text += " (CD %d)" % cd
		button.disabled = not _actions_enabled or not can_use
		var targets_allies := str(skill.get("targetSide", "ENEMY")) == "ALLY"
		button.pressed.connect(_on_skill_pressed.bind(skill_id, targets_allies))
		skills_row.add_child(button)

func _update_action_buttons() -> void:
	attack_button.disabled = not _actions_enabled
	capture_button.disabled = not _actions_enabled or _is_companion_actor()
	capture_button.visible = not _is_companion_actor()
	$BattleUI/Margin/VBox/ActionsRow/DefendButton.disabled = not _actions_enabled
	$BattleUI/Margin/VBox/ActionsRow/FleeButton.disabled = not _actions_enabled
	_render_skill_buttons()
	if not _actions_enabled:
		_cancel_target_selection()
	elif _target_mode == TargetMode.NONE:
		hint_label.text = _default_hint_text()
