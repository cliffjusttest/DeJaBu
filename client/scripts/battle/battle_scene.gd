class_name BattleScene
extends CanvasLayer

signal battle_action_requested(action: String, target_id: int, actor_id: int, skill_id: int, item_id: int)

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
@onready var skills_button: Button = $BattleUI/Margin/VBox/ActionsRow/SkillsButton
@onready var items_button: Button = $BattleUI/Margin/VBox/ActionsRow/ItemsButton
@onready var hint_label: Label = $BattleUI/Margin/VBox/HintLabel
@onready var skill_hotkey_bar: HBoxContainer = $BattleUI/Margin/VBox/SkillHotkeyBar
@onready var item_hotkey_bar: HBoxContainer = $BattleUI/Margin/VBox/ItemHotkeyBar
@onready var skill_sub_scroll: ScrollContainer = $BattleUI/Margin/VBox/SkillSubScroll
@onready var skill_sub_vbox: HBoxContainer = $BattleUI/Margin/VBox/SkillSubScroll/SkillSubVBox
@onready var item_sub_scroll: ScrollContainer = $BattleUI/Margin/VBox/ItemSubScroll
@onready var item_sub_vbox: HBoxContainer = $BattleUI/Margin/VBox/ItemSubScroll/ItemSubVBox

var _enemy_slots: Array[BattleSlot] = []
var _ally_slots: Array[BattleSlot] = []
var _target_mode := TargetMode.NONE
var _actions_enabled := true
var _selected_actor_id := -1
var _planned_actor_ids: Array = []
var _pending_skill_id := -1
var _pending_skill_targets_allies := false
var _battle_consumables: Array = []

func _ready() -> void:
	_build_formation_grids()
	hide()

func show_battle(battle_data: Dictionary) -> void:
	_cancel_target_selection()
	_cancel_sub_panels()
	_actions_enabled = true
	_sync_actor_state(battle_data)
	_update_consumables(battle_data)
	_update_action_buttons()
	update_battle_data(battle_data)
	log_label.clear()
	show()

func hide_battle() -> void:
	_cancel_target_selection()
	_cancel_sub_panels()
	hide()

func update_battle_data(battle_data: Dictionary) -> void:
	_render_formation(enemy_grid, _enemy_slots, battle_data.get("enemies", []))
	_render_formation(ally_grid, _ally_slots, battle_data.get("allies", []))
	_sync_actor_state(battle_data)
	_update_consumables(battle_data)

	var player_element := str(
		battle_data.get("playerElementName", ElementData.display_name(str(battle_data.get("playerElement", ""))))
	)
	var player_level := int(battle_data.get("playerLevel", GameState.player_level))
	player_hp_label.text = "%s Lv.%d（%s） HP: %d / %d  MP: %d / %d" % [
		battle_data.get("playerName", GameState.player_name),
		player_level,
		player_element,
		int(battle_data.get("playerHp", 0)),
		int(battle_data.get("playerMaxHp", 0)),
		int(battle_data.get("playerMp", 0)),
		int(battle_data.get("playerMaxMp", 0))
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
	_render_hotkey_bars()

func _update_consumables(battle_data: Dictionary) -> void:
	var consumables: Variant = battle_data.get("consumables", [])
	if typeof(consumables) == TYPE_ARRAY:
		_battle_consumables = consumables

func append_log(text: String) -> void:
	log_label.append_text(text + "\n")

func set_actions_enabled(enabled: bool) -> void:
	_actions_enabled = enabled
	if not enabled:
		_cancel_sub_panels()
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

# ── Sub-panels ────────────────────────────────────────────────────────────────

func _cancel_sub_panels() -> void:
	skill_sub_scroll.hide()
	item_sub_scroll.hide()

func _on_skills_button_pressed() -> void:
	if not _actions_enabled:
		return
	if skill_sub_scroll.visible:
		skill_sub_scroll.hide()
	else:
		item_sub_scroll.hide()
		_render_skill_sub_panel()
		skill_sub_scroll.show()

func _on_items_button_pressed() -> void:
	if not _actions_enabled:
		return
	if item_sub_scroll.visible:
		item_sub_scroll.hide()
	else:
		skill_sub_scroll.hide()
		_render_item_sub_panel()
		item_sub_scroll.show()

func _render_skill_sub_panel() -> void:
	for child in skill_sub_vbox.get_children():
		child.queue_free()

	var skills := _active_actor_skills()
	if skills.is_empty():
		var label := Label.new()
		label.text = "無可用技能"
		skill_sub_vbox.add_child(label)
		return

	for entry in skills:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var skill: Dictionary = entry
		var skill_id := int(skill.get("skillId", 0))
		var skill_level := int(skill.get("skillLevel", 1))
		var cd := int(skill.get("cooldownRemaining", 0))
		var mp_cost := int(skill.get("mpCost", 0))
		var can_use := bool(skill.get("canUse", true))
		var btn := Button.new()
		btn.text = "%s Lv.%d" % [skill.get("name", "技能"), skill_level]
		if mp_cost > 0:
			btn.text += "\nMP %d" % mp_cost
		if cd > 0:
			btn.text += "\n(CD %d)" % cd
		btn.custom_minimum_size = Vector2(90, 0)
		btn.disabled = not _actions_enabled or not can_use
		var targets_allies := str(skill.get("targetSide", "ENEMY")) == "ALLY"
		btn.pressed.connect(_on_skill_sub_pressed.bind(skill_id, targets_allies))
		skill_sub_vbox.add_child(btn)

func _render_item_sub_panel() -> void:
	for child in item_sub_vbox.get_children():
		child.queue_free()

	if _battle_consumables.is_empty():
		var label := Label.new()
		label.text = "無可用道具"
		item_sub_vbox.add_child(label)
		return

	for item_data in _battle_consumables:
		if typeof(item_data) != TYPE_DICTIONARY:
			continue
		var item_id := int(item_data.get("id", 0))
		var quantity := int(item_data.get("quantity", 0))
		var btn := Button.new()
		btn.text = "%s\nx%d" % [item_data.get("name", "道具"), quantity]
		btn.custom_minimum_size = Vector2(90, 0)
		btn.disabled = not _actions_enabled
		btn.pressed.connect(_on_item_sub_pressed.bind(item_id))
		item_sub_vbox.add_child(btn)

func _on_skill_sub_pressed(skill_id: int, targets_allies: bool) -> void:
	skill_sub_scroll.hide()
	_on_skill_pressed(skill_id, targets_allies)

func _on_item_sub_pressed(item_id: int) -> void:
	item_sub_scroll.hide()
	battle_action_requested.emit("item", -1, _selected_actor_id, -1, item_id)

# ── Hotkey bars ───────────────────────────────────────────────────────────────

func _get_actor_slot() -> int:
	for slot in _ally_slots:
		if slot.unit_id == _selected_actor_id:
			return slot.slot_index
	return -1

func _render_hotkey_bars() -> void:
	_render_skill_hotkey_bar()
	_render_item_hotkey_bar()

func _render_skill_hotkey_bar() -> void:
	for child in skill_hotkey_bar.get_children():
		child.queue_free()

	var actor_slot := _get_actor_slot()
	var skills_by_id: Dictionary = {}
	for s in _active_actor_skills():
		if typeof(s) == TYPE_DICTIONARY:
			skills_by_id[int(s.get("skillId", -1))] = s

	for i in range(12):
		var btn := Button.new()
		var key_name := "F%d" % (i + 1)
		if actor_slot >= 0:
			var skill_id := BattleHotkeys.get_skill_hotkey(actor_slot, i)
			if skill_id > 0 and skills_by_id.has(skill_id):
				var s: Dictionary = skills_by_id[skill_id]
				var cd := int(s.get("cooldownRemaining", 0))
				var mp_cost := int(s.get("mpCost", 0))
				var can_use := bool(s.get("canUse", true))
				btn.text = "%s\n%s" % [key_name, s.get("name", "?")]
				if mp_cost > 0:
					btn.text += "\nMP%d" % mp_cost
				if cd > 0:
					btn.text += "\nCD%d" % cd
				btn.disabled = not _actions_enabled or not can_use
			elif skill_id > 0:
				btn.text = "%s\n(?)" % key_name
				btn.disabled = true
			else:
				btn.text = "%s\n(空)" % key_name
				btn.disabled = true
		else:
			btn.text = "%s\n(空)" % key_name
			btn.disabled = true
		btn.custom_minimum_size = Vector2(54, 50)
		skill_hotkey_bar.add_child(btn)

func _render_item_hotkey_bar() -> void:
	for child in item_hotkey_bar.get_children():
		child.queue_free()

	var items_by_id: Dictionary = {}
	for item_data in _battle_consumables:
		if typeof(item_data) == TYPE_DICTIONARY:
			items_by_id[int(item_data.get("id", -1))] = item_data

	for i in range(12):
		var btn := Button.new()
		var key_name := "SF%d" % (i + 1)
		var item_id := BattleHotkeys.get_item_hotkey(i)
		if item_id > 0:
			var quantity := 0
			var item_name := "?"
			if items_by_id.has(item_id):
				var d: Dictionary = items_by_id[item_id]
				quantity = int(d.get("quantity", 0))
				item_name = str(d.get("name", "?"))
			btn.text = "%s\n%s\nx%d" % [key_name, item_name, quantity]
			btn.disabled = not _actions_enabled or quantity <= 0
		else:
			btn.text = "%s\n(空)" % key_name
			btn.disabled = true
		btn.custom_minimum_size = Vector2(54, 50)
		item_hotkey_bar.add_child(btn)

# ── Hotkey triggers ───────────────────────────────────────────────────────────

func _trigger_skill_hotkey(index: int) -> void:
	if not _actions_enabled:
		return
	var actor_slot := _get_actor_slot()
	if actor_slot < 0:
		return
	var skill_id := BattleHotkeys.get_skill_hotkey(actor_slot, index)
	if skill_id <= 0:
		return
	for skill_data in _active_actor_skills():
		if typeof(skill_data) != TYPE_DICTIONARY:
			continue
		if int(skill_data.get("skillId", 0)) == skill_id:
			if not bool(skill_data.get("canUse", true)):
				return
			var targets_allies := str(skill_data.get("targetSide", "ENEMY")) == "ALLY"
			_cancel_sub_panels()
			_on_skill_pressed(skill_id, targets_allies)
			return

func _trigger_item_hotkey(index: int) -> void:
	if not _actions_enabled:
		return
	var item_id := BattleHotkeys.get_item_hotkey(index)
	if item_id <= 0:
		return
	for item_data in _battle_consumables:
		if typeof(item_data) != TYPE_DICTIONARY:
			continue
		if int(item_data.get("id", 0)) == item_id:
			var quantity := int(item_data.get("quantity", 0))
			if quantity <= 0:
				return
			_cancel_sub_panels()
			battle_action_requested.emit("item", -1, _selected_actor_id, -1, item_id)
			return

# ── Target selection ──────────────────────────────────────────────────────────

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
	var waiting := _waiting_for_other_players_text()
	if waiting.is_empty():
		if _is_companion_actor():
			return "為 %s 指定行動 | 1 攻擊 / 2 防禦 / 3 逃跑 / 5 技能 / 6 道具" % actor_name
		return "為 %s 指定行動 | 1 攻擊 / 4 捕捉 / 2 防禦 / 3 逃跑 / 5 技能 / 6 道具" % actor_name
	return "%s | %s" % [waiting, "為 %s 指定行動" % actor_name]

func _waiting_for_other_players_text() -> String:
	if not bool(GameState.battle_data.get("multiplayer", false)):
		return ""
	var waiting_names: Array = []
	for unit_data in GameState.battle_data.get("allies", []):
		if typeof(unit_data) != TYPE_DICTIONARY:
			continue
		var unit: Dictionary = unit_data
		if int(unit.get("ownerUserId", GameState.player_id)) == GameState.player_id:
			continue
		var unit_id := int(unit.get("id", 0))
		if unit_id <= 0 or not bool(unit.get("alive", true)):
			continue
		if unit_id in _planned_actor_ids:
			continue
		var owner_name := str(unit.get("name", ""))
		if owner_name not in waiting_names:
			waiting_names.append(owner_name)
	if waiting_names.is_empty():
		return ""
	return "等待其他玩家指定行動"

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

func _is_my_unit(unit_data: Dictionary) -> bool:
	if not unit_data.has("ownerUserId"):
		return true
	return int(unit_data.get("ownerUserId", 0)) == GameState.player_id

func _refresh_actor_highlights() -> void:
	var selecting_target := _target_mode != TargetMode.NONE
	for slot in _ally_slots:
		var alive := slot.unit_id > 0 and bool(slot.unit_data.get("alive", true))
		if _target_mode == TargetMode.SKILL and _pending_skill_targets_allies:
			slot.set_actor_highlight(false)
			continue
		var is_mine := slot.unit_data.is_empty() or _is_my_unit(slot.unit_data)
		var can_choose := (
			_actions_enabled
			and not selecting_target
			and alive
			and is_mine
			and not _has_planned(slot.unit_id)
		)
		slot.set_actor_choosable(can_choose)
		slot.set_actor_highlight(slot.unit_id == _selected_actor_id and alive and is_mine)

func _on_enemy_slot_pressed(_slot_index: int, unit_id: int) -> void:
	if _target_mode == TargetMode.NONE or not _actions_enabled:
		return
	if _target_mode == TargetMode.SKILL:
		var skill_id := _pending_skill_id
		_cancel_target_selection()
		battle_action_requested.emit("skill", unit_id, _selected_actor_id, skill_id, -1)
		return
	var action := "capture" if _target_mode == TargetMode.CAPTURE else "attack"
	_cancel_target_selection()
	battle_action_requested.emit(action, unit_id, _selected_actor_id, -1, -1)

func _on_ally_slot_pressed(_slot_index: int, unit_id: int) -> void:
	if not _actions_enabled:
		return
	if _target_mode == TargetMode.SKILL and _pending_skill_targets_allies:
		var skill_id := _pending_skill_id
		_cancel_target_selection()
		battle_action_requested.emit("skill", unit_id, _selected_actor_id, skill_id, -1)
		return
	if _target_mode != TargetMode.NONE:
		return
	var battle_slot := _find_slot_by_unit_id(unit_id)
	if battle_slot == null or not _is_my_unit(battle_slot.unit_data):
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
		if event.keycode >= KEY_F1 and event.keycode <= KEY_F12:
			var hotkey_index: int = event.keycode - KEY_F1
			if event.shift_pressed:
				_trigger_item_hotkey(hotkey_index)
			else:
				_trigger_skill_hotkey(hotkey_index)
			get_viewport().set_input_as_handled()
			return
		match event.keycode:
			KEY_1:
				_on_attack_pressed()
				get_viewport().set_input_as_handled()
			KEY_2:
				_cancel_target_selection()
				battle_action_requested.emit("defend", -1, _selected_actor_id, -1, -1)
				get_viewport().set_input_as_handled()
			KEY_3:
				_cancel_target_selection()
				battle_action_requested.emit("flee", -1, _selected_actor_id, -1, -1)
				get_viewport().set_input_as_handled()
			KEY_4:
				if not _is_companion_actor():
					_on_capture_pressed()
				get_viewport().set_input_as_handled()
			KEY_5:
				_on_skills_button_pressed()
				get_viewport().set_input_as_handled()
			KEY_6:
				_on_items_button_pressed()
				get_viewport().set_input_as_handled()
			KEY_ESCAPE:
				if skill_sub_scroll.visible or item_sub_scroll.visible:
					_cancel_sub_panels()
					get_viewport().set_input_as_handled()
				elif _target_mode != TargetMode.NONE:
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
	battle_action_requested.emit("defend", -1, _selected_actor_id, -1, -1)

func _on_flee_pressed() -> void:
	if not _actions_enabled:
		return
	_cancel_target_selection()
	battle_action_requested.emit("flee", -1, _selected_actor_id, -1, -1)

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

func _update_action_buttons() -> void:
	attack_button.disabled = not _actions_enabled
	capture_button.disabled = not _actions_enabled or _is_companion_actor()
	capture_button.visible = not _is_companion_actor()
	$BattleUI/Margin/VBox/ActionsRow/DefendButton.disabled = not _actions_enabled
	$BattleUI/Margin/VBox/ActionsRow/FleeButton.disabled = not _actions_enabled
	skills_button.disabled = not _actions_enabled
	items_button.disabled = not _actions_enabled
	_render_hotkey_bars()
	if not _actions_enabled:
		_cancel_target_selection()
		_cancel_sub_panels()
	elif _target_mode == TargetMode.NONE:
		hint_label.text = _default_hint_text()
