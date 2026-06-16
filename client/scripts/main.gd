extends Node2D

@onready var world_map: WorldMap = $World/WorldMap
@onready var player: PlayerController = $World/Player
@onready var other_players: OtherPlayersManager = $World/OtherPlayers
@onready var status_label: Label = $UI/StatusLabel
@onready var log_label: RichTextLabel = $UI/LogLabel
@onready var battle_scene: CanvasLayer = $BattleLayer/BattleScene
@onready var login_panel: Control = $LoginLayer/LoginPanel
@onready var login_layer: CanvasLayer = $LoginLayer
@onready var character_create_panel: Control = $CharacterCreateLayer/CharacterCreatePanel
@onready var character_create_layer: CanvasLayer = $CharacterCreateLayer
@onready var skill_tree_panel: Control = $SkillTreeLayer/SkillTreePanel
@onready var skill_tree_layer: CanvasLayer = $SkillTreeLayer
@onready var companion_panel: Control = $CompanionLayer/CompanionPanel
@onready var companion_layer: CanvasLayer = $CompanionLayer
@onready var dialogue_panel: Control = $DialogueLayer/DialoguePanel
@onready var dialogue_layer: CanvasLayer = $DialogueLayer
@onready var quest_log_panel: Control = $QuestLayer/QuestLogPanel
@onready var quest_layer: CanvasLayer = $QuestLayer
@onready var equipment_panel: Control = $EquipmentLayer/EquipmentPanel
@onready var equipment_layer: CanvasLayer = $EquipmentLayer
@onready var hotkey_panel: Control = $HotkeyLayer/HotkeyPanel
@onready var hotkey_layer: CanvasLayer = $HotkeyLayer
@onready var status_panel: Control = $StatusLayer/StatusPanel
@onready var status_layer: CanvasLayer = $StatusLayer
@onready var shop_panel: Control = $ShopLayer/ShopPanel
@onready var shop_layer: CanvasLayer = $ShopLayer
@onready var party_panel: Control = $PartyLayer/PartyPanel
@onready var party_layer: CanvasLayer = $PartyLayer
@onready var skills_button: Button = $UI/SkillsButton
@onready var companions_button: Button = $UI/CompanionsButton
@onready var quests_button: Button = $UI/QuestsButton
@onready var equipment_button: Button = $UI/EquipmentButton
@onready var hotkey_button: Button = $UI/HotkeyButton
@onready var status_button: Button = $UI/StatusButton
@onready var party_button: Button = $UI/PartyButton
@onready var world: Node2D = $World
@onready var ui: CanvasLayer = $UI

var _pending_other_players: Array = []
var _last_direction := "down"
var _pending_sync_grid := Vector2i(-9999, -9999)
var _awaiting_server := false
var _game_started := false
func _ready() -> void:
	world_map.map_loaded.connect(_on_map_loaded)
	other_players.setup(world_map)
	player.grid_cell_changed.connect(_on_grid_cell_changed)
	battle_scene.battle_action_requested.connect(_on_battle_action_requested)
	login_panel.authenticated.connect(_on_login_authenticated)
	login_panel.auth_failed.connect(_on_login_auth_failed)
	character_create_panel.character_created.connect(_on_character_created)
	character_create_panel.create_failed.connect(_on_character_create_failed)
	skill_tree_panel.closed.connect(_on_skill_tree_closed)
	skill_tree_panel.tree_updated.connect(_on_skill_tree_updated)
	companion_panel.closed.connect(_on_companion_panel_closed)
	dialogue_panel.choice_made.connect(_on_dialogue_choice_made)
	dialogue_panel.dialogue_closed.connect(_on_dialogue_closed)
	quest_log_panel.closed.connect(_on_quest_log_closed)
	equipment_panel.closed.connect(_on_equipment_panel_closed)
	hotkey_panel.closed.connect(_on_hotkey_panel_closed)
	status_panel.closed.connect(_on_status_panel_closed)
	shop_panel.closed.connect(_on_shop_panel_closed)
	party_panel.closed.connect(_on_party_panel_closed)
	skills_button.pressed.connect(_on_skills_button_pressed)
	companions_button.pressed.connect(_on_companions_button_pressed)
	quests_button.pressed.connect(_on_quests_button_pressed)
	equipment_button.pressed.connect(_on_equipment_button_pressed)
	hotkey_button.pressed.connect(_on_hotkey_button_pressed)
	status_button.pressed.connect(_on_status_button_pressed)
	party_button.pressed.connect(_on_party_button_pressed)
	NetworkClient.connected.connect(_on_connected)
	NetworkClient.disconnected.connect(_on_disconnected)
	NetworkClient.connection_failed.connect(_on_connection_failed)
	NetworkClient.message_received.connect(_on_message_received)
	_set_gameplay_visible(false)
	skills_button.hide()
	companions_button.hide()
	quests_button.hide()
	equipment_button.hide()
	hotkey_button.hide()
	status_button.hide()
	party_button.hide()
	status_label.text = "DeJaBu - 請登入"

func _on_login_authenticated(auth_data: Dictionary) -> void:
	GameState.apply_auth(auth_data)
	login_layer.hide()
	if GameState.has_character:
		_enter_game()
	else:
		_show_character_create()

func _show_character_create() -> void:
	character_create_layer.show()
	character_create_panel.begin(GameState.auth_token)

func _on_character_created(auth_data: Dictionary) -> void:
	GameState.apply_auth(auth_data)
	character_create_layer.hide()
	_enter_game()

func _on_character_create_failed(message: String) -> void:
	_log(message)

func _enter_game() -> void:
	_log("已驗證帳號，正在進入遊戲...")
	NetworkClient.connect_to_server()

func _on_login_auth_failed(message: String) -> void:
	status_label.text = "登入失敗"
	_log(message)

func _load_player_map() -> void:
	if world_map.get_current_map_id() != GameState.player_map_id:
		world_map.load_map_by_id(GameState.player_map_id)
	elif world_map.get_map_size() != Vector2i.ZERO:
		_on_map_loaded()
	else:
		_update_status()

func _on_map_loaded() -> void:
	if not _game_started:
		return

	var spawn: Vector2i = world_map.find_spawn_point(Vector2i(GameState.player_x, GameState.player_y))
	GameState.player_x = spawn.x
	GameState.player_y = spawn.y
	player.setup(world_map, spawn)
	GameState.player_world_position = player.global_position
	other_players.setup(world_map)
	if not _pending_other_players.is_empty():
		other_players.sync_players(_pending_other_players)
		_pending_other_players.clear()
	_update_status()

func _process(_delta: float) -> void:
	if not _game_started or not NetworkClient.is_server_connected() or GameState.mode != GameState.Mode.EXPLORE:
		player.set_input_direction(Vector2.ZERO)
		return
	_check_nearby_npc()
	_update_nearby_players_for_party()

	if not GameState.can_control_movement():
		player.set_input_direction(Vector2.ZERO)
		player.stop_movement()
		GameState.player_world_position = player.global_position
		return

	player.set_input_direction(_read_movement_input())
	GameState.player_world_position = player.global_position

func _unhandled_input(event: InputEvent) -> void:
	if not _game_started or not NetworkClient.is_server_connected():
		return

	if GameState.mode == GameState.Mode.DIALOGUE:
		return

	if GameState.mode != GameState.Mode.EXPLORE:
		return

	if skill_tree_layer.visible or companion_layer.visible or quest_layer.visible or equipment_layer.visible or hotkey_layer.visible or status_layer.visible or party_layer.visible:
		return

	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_G:
				_toggle_party_panel()
				get_viewport().set_input_as_handled()
				return
			KEY_C:
				_toggle_status_panel()
				get_viewport().set_input_as_handled()
				return
			KEY_K:
				_toggle_skill_tree()
				get_viewport().set_input_as_handled()
				return
			KEY_P:
				_toggle_companion_panel()
				get_viewport().set_input_as_handled()
				return
			KEY_J:
				_toggle_quest_log()
				get_viewport().set_input_as_handled()
				return
			KEY_E:
				_toggle_equipment_panel()
				get_viewport().set_input_as_handled()
				return
			KEY_H:
				_toggle_hotkey_panel()
				get_viewport().set_input_as_handled()
				return
			KEY_F:
				_try_npc_interact()
				get_viewport().set_input_as_handled()
				return

	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		_handle_click_move()

func _read_movement_input() -> Vector2:
	var direction := Vector2.ZERO
	if Input.is_key_pressed(KEY_W) or Input.is_key_pressed(KEY_UP):
		direction.y -= 1.0
	if Input.is_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN):
		direction.y += 1.0
	if Input.is_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT):
		direction.x -= 1.0
	if Input.is_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT):
		direction.x += 1.0

	if direction != Vector2.ZERO:
		return direction.normalized()
	return Vector2.ZERO

func _handle_click_move() -> void:
	if not GameState.can_control_movement():
		_log("組隊時只有隊長可以移動")
		return
	var world_pos := _get_mouse_world_position()
	if not world_map.is_world_walkable(world_pos):
		_log("無法移動至該位置")
		return

	player.set_click_target(world_pos)

func _get_mouse_world_position() -> Vector2:
	var camera := player.get_node("Camera2D") as Camera2D
	return camera.get_global_mouse_position()

func _on_grid_cell_changed(grid_x: int, grid_y: int, direction: String) -> void:
	_last_direction = direction
	if _awaiting_server:
		_pending_sync_grid = Vector2i(grid_x, grid_y)
		return
	_send_move(grid_x, grid_y, direction)

func _send_move(grid_x: int, grid_y: int, direction: String) -> void:
	_awaiting_server = true
	NetworkClient.move(grid_x, grid_y, direction, GameState.player_map_id)

func _on_connected() -> void:
	_log("已連線，正在進入遊戲...")
	NetworkClient.login_with_token(GameState.auth_token)

func _on_disconnected() -> void:
	_game_started = false
	other_players.clear_all()
	_pending_other_players.clear()
	GameState.clear_auth()
	_set_gameplay_visible(false)
	character_create_layer.hide()
	skill_tree_layer.hide()
	companion_layer.hide()
	dialogue_layer.hide()
	quest_layer.hide()
	status_layer.hide()
	_show_login_screen("與伺服器斷線，請重新登入")

func _on_connection_failed(reason: String) -> void:
	_show_login_screen(reason)

func _on_message_received(type: String, payload: Dictionary) -> void:
	match type:
		"PONG":
			_log(payload.get("message", "pong"))
		"LOGIN_OK":
			_handle_login_ok(payload)
		"MOVE_OK":
			_handle_move_ok(payload)
		"BATTLE_START":
			_handle_battle_start(payload)
		"BATTLE_RESULT":
			_handle_battle_result(payload)
		"NPC_INTERACT_OK":
			_handle_npc_interact_ok(payload)
		"QUEST_LIST_OK":
			_handle_quest_list_ok(payload)
		"PLAYER_JOIN":
			other_players.handle_join(payload)
		"PLAYER_LEAVE":
			other_players.handle_leave(payload)
		"PLAYER_MOVE":
			other_players.handle_move(payload)
		"PARTY_INVITE_OK":
			_handle_party_invite_ok(payload)
		"PARTY_ACCEPT_OK":
			_handle_party_state_payload(payload)
		"PARTY_UPDATE":
			_handle_party_update(payload)
		"PARTY_LEAVE_OK":
			_handle_party_state_payload(payload)
		"PARTY_SYNC":
			_handle_party_sync(payload)
		"ERROR":
			var err_msg := str(payload.get("message", "未知錯誤"))
			_log("錯誤: %s" % err_msg)
			if GameState.mode == GameState.Mode.BATTLE:
				battle_scene.set_actions_enabled(true)
				battle_scene.append_log(err_msg)
			elif not _game_started:
				NetworkClient.disconnect_from_server()
				_show_login_screen(err_msg)
			else:
				_awaiting_server = false

func _handle_login_ok(payload: Dictionary) -> void:
	GameState.player_name = payload.get("playerName", GameState.player_name)
	GameState.player_id = int(payload.get("playerId", GameState.player_id))
	GameState.player_x = int(payload.get("playerX", GameState.player_x))
	GameState.player_y = int(payload.get("playerY", GameState.player_y))
	GameState.player_map_id = str(payload.get("playerMapId", GameState.player_map_id))
	GameState.player_level = int(payload.get("playerLevel", GameState.player_level))
	GameState.player_exp = int(payload.get("playerExp", GameState.player_exp))
	GameState.exp_to_next_level = int(payload.get("expToNextLevel", GameState.exp_to_next_level))
	GameState.skill_points = int(payload.get("skillPoints", GameState.skill_points))
	GameState.player_current_hp = int(payload.get("playerCurrentHp", GameState.player_current_hp))
	if payload.has("playerMaxHp"):
		GameState.player_max_hp = int(payload.get("playerMaxHp"))
	if payload.has("playerCurrentMp"):
		GameState.player_current_mp = int(payload.get("playerCurrentMp"))
	if payload.has("playerMaxMp"):
		GameState.player_max_mp = int(payload.get("playerMaxMp"))
	if payload.has("playerGold"):
		GameState.player_gold = int(payload.get("playerGold"))
	elif not GameState.player_stats.is_empty():
		GameState.player_max_hp = CharacterStatsData.max_hp(GameState.player_stats)
		GameState.player_max_mp = CharacterStatsData.max_mp(GameState.player_stats)
	GameState.player_element = str(payload.get("playerElement", GameState.player_element))
	GameState.player_appearance = str(payload.get("playerAppearance", GameState.player_appearance))
	if payload.has("playerStats"):
		GameState.player_stats = CharacterStatsData.from_payload(payload.get("playerStats"))
	GameState.mark_session_authenticated()

	_game_started = true
	_set_gameplay_visible(true)
	login_layer.hide()
	character_create_layer.hide()
	skill_tree_layer.hide()
	companion_layer.hide()
	dialogue_layer.hide()
	quest_layer.hide()
	equipment_layer.hide()
	status_layer.hide()
	_log(payload.get("message", "登入成功"))

	if not GameState.player_appearance.is_empty():
		player.apply_appearance(GameState.player_appearance)

	_pending_other_players = payload.get("otherPlayers", [])
	if payload.has("party"):
		GameState.apply_party_state(payload.get("party", {}))
	_check_party_invite_prompt()
	_load_player_map()

func _handle_move_ok(payload: Dictionary) -> void:
	_awaiting_server = false
	var new_x := int(payload.get("x", GameState.player_x))
	var new_y := int(payload.get("y", GameState.player_y))
	var new_map_id := str(payload.get("mapId", GameState.player_map_id))
	GameState.player_x = new_x
	GameState.player_y = new_y
	GameState.player_map_id = new_map_id

	if payload.get("mapChanged", false):
		player.stop_movement()
		_pending_sync_grid = Vector2i(-9999, -9999)
		other_players.clear_all()
		_pending_other_players = payload.get("otherPlayers", [])
		_log(payload.get("message", "傳送至 %s" % MapRegistry.get_map_name(new_map_id)))
		if world_map.get_current_map_id() != new_map_id:
			world_map.load_map_by_id(new_map_id)
		else:
			_on_map_loaded()
		return

	if payload.get("encounter", false):
		player.stop_movement()
		_pending_sync_grid = Vector2i(-9999, -9999)
		_log(payload.get("message", "遭遇野生怪物！"))
		if GameState.is_party_leader or not GameState.in_player_party:
			NetworkClient.start_battle()
	elif _pending_sync_grid != Vector2i(-9999, -9999):
		var pending := _pending_sync_grid
		_pending_sync_grid = Vector2i(-9999, -9999)
		_send_move(pending.x, pending.y, _last_direction)

	_update_status()

func _handle_battle_start(payload: Dictionary) -> void:
	GameState.mode = GameState.Mode.BATTLE
	player.stop_movement()
	GameState.battle_data = payload.get("battle", {})
	battle_scene.show_battle(GameState.battle_data)
	battle_scene.append_log(payload.get("message", "進入戰鬥"))
	_update_status()

func _handle_battle_result(payload: Dictionary) -> void:
	if payload.has("message"):
		_log(payload.get("message"))
		battle_scene.append_log(str(payload.get("message")))

	# Planning acknowledgement: just update state and re-enable for next actor
	if not payload.get("roundExecuted", true):
		if payload.has("battle"):
			GameState.battle_data = payload["battle"]
			battle_scene.update_battle_data(GameState.battle_data)
		battle_scene.set_actions_enabled(true)
		return

	# Round executed: play animations then handle outcome
	var attack_events: Variant = payload.get("attackEvents", [])
	if typeof(attack_events) == TYPE_ARRAY and attack_events.size() > 0:
		await battle_scene.play_attack_animations(attack_events)

	if payload.get("captureSuccess", false):
		var companion_name := str(payload.get("companionName", "新夥伴"))
		var companion_level := int(payload.get("companionLevel", 1))
		GameState.companions.append({
			"nickname": companion_name,
			"level": companion_level
		})

	if payload.has("battle"):
		GameState.battle_data = payload["battle"]
		battle_scene.update_battle_data(GameState.battle_data)

	battle_scene.set_actions_enabled(true)

	if payload.get("battleOver", false):
		_sync_player_hp_from_battle(GameState.battle_data)
		if payload.get("victory", false):
			_apply_progression(payload)
			_apply_quest_progress(payload)
			_apply_battle_loot(payload)
		else:
			_log("戰鬥失敗...")
		_end_battle()
	elif payload.get("escaped", false):
		_sync_player_hp_from_battle(GameState.battle_data)
		_log("成功逃離戰鬥")
		_end_battle()

func _sync_player_hp_from_battle(battle_data: Dictionary) -> void:
	if battle_data.is_empty():
		return
	GameState.player_current_hp = int(battle_data.get("playerHp", GameState.player_current_hp))
	GameState.player_max_hp = int(battle_data.get("playerMaxHp", GameState.player_max_hp))
	GameState.player_current_mp = int(battle_data.get("playerMp", GameState.player_current_mp))
	GameState.player_max_mp = int(battle_data.get("playerMaxMp", GameState.player_max_mp))

func _apply_progression(payload: Dictionary) -> void:
	GameState.player_exp = int(payload.get("playerExp", GameState.player_exp))
	GameState.exp_to_next_level = int(payload.get("expToNextLevel", GameState.exp_to_next_level))
	GameState.player_level = int(payload.get("playerLevel", GameState.player_level))
	GameState.skill_points = int(payload.get("skillPoints", GameState.skill_points))

	var exp_gained := int(payload.get("expGained", 0))
	_log("戰鬥勝利！獲得 %d EXP" % exp_gained)

	if payload.get("leveledUp", false):
		var previous_level := int(payload.get("previousLevel", GameState.player_level))
		var levels_gained := int(payload.get("levelsGained", 1))
		var skill_points_gained := int(payload.get("skillPointsGained", 0))
		_log(
			"等級提升！Lv.%d → Lv.%d（+%d 技能點）" % [
				previous_level,
				previous_level + levels_gained,
				skill_points_gained
			]
		)

func _apply_quest_progress(payload: Dictionary) -> void:
	var progress_list: Variant = payload.get("questProgress", [])
	if typeof(progress_list) != TYPE_ARRAY:
		return
	for kp in progress_list:
		var q_name := str(kp.get("questName", ""))
		var prog := int(kp.get("progress", 0))
		var req := int(kp.get("requiredCount", 1))
		if bool(kp.get("readyToClaim", false)):
			_log("任務進度：%s（%d/%d）— 可回去領取報酬！" % [q_name, prog, req])
		else:
			_log("任務進度：%s（%d/%d）" % [q_name, prog, req])

func _apply_battle_loot(payload: Dictionary) -> void:
	if payload.has("playerGold"):
		GameState.player_gold = int(payload.get("playerGold"))
	var gold_gained := int(payload.get("goldGained", 0))
	if gold_gained > 0:
		_log("獲得 %d 金幣（持有 %d）" % [gold_gained, GameState.player_gold])

	var drops: Variant = payload.get("itemDrops", [])
	if typeof(drops) != TYPE_ARRAY or drops.is_empty():
		return
	for drop in drops:
		if typeof(drop) != TYPE_DICTIONARY:
			continue
		var name := str(drop.get("name", "道具"))
		var qty := int(drop.get("quantity", 1))
		if qty > 1:
			_log("掉落：%s x%d" % [name, qty])
		else:
			_log("掉落：%s" % name)

func _end_battle() -> void:
	GameState.reset_battle()
	battle_scene.hide_battle()
	_update_status()

func _on_battle_action_requested(
	action: String,
	target_id: int = -1,
	actor_id: int = -1,
	skill_id: int = -1,
	item_id: int = -1
) -> void:
	if GameState.mode != GameState.Mode.BATTLE:
		return
	if (action == "attack" or action == "capture" or action == "skill") and target_id <= 0:
		return
	if action == "skill" and skill_id <= 0:
		return
	if action == "item" and item_id <= 0:
		return
	if actor_id <= 0:
		return
	battle_scene.set_actions_enabled(false)
	NetworkClient.battle_action(action, target_id, actor_id, skill_id, item_id)

func _handle_npc_interact_ok(payload: Dictionary) -> void:
	if payload.get("finished", false):
		_on_dialogue_finished(payload)
		return

	GameState.mode = GameState.Mode.DIALOGUE
	GameState.dialogue_npc_id = str(payload.get("npcId", ""))
	GameState.dialogue_node_key = str(payload.get("nodeKey", ""))
	var npc_name := str(payload.get("npcName", "NPC"))
	var text := str(payload.get("text", ""))
	var choices: Array = payload.get("choices", [])
	dialogue_layer.show()
	dialogue_panel.show_dialogue(npc_name, text, choices)

func _handle_quest_list_ok(payload: Dictionary) -> void:
	var quests: Array = payload.get("quests", [])
	GameState.active_quests = quests
	quest_log_panel.populate(quests)

func _on_dialogue_choice_made(choice_index: int) -> void:
	if GameState.dialogue_npc_id.is_empty():
		return
	NetworkClient.dialogue_choice(GameState.dialogue_npc_id, GameState.dialogue_node_key, choice_index)

func _on_dialogue_closed() -> void:
	GameState.reset_dialogue()
	dialogue_layer.hide()
	_update_status()

func _on_dialogue_finished(payload: Dictionary) -> void:
	var rewards: Array = payload.get("questRewards", [])
	for reward in rewards:
		var quest_name := str(reward.get("questName", ""))
		var exp_gained := int(reward.get("expGained", 0))
		var sp_gained := int(reward.get("skillPointsGained", 0))
		_log("任務完成：%s！獲得 %d EXP 和 %d 技能點" % [quest_name, exp_gained, sp_gained])
		GameState.player_exp += exp_gained
		GameState.skill_points += sp_gained

	dialogue_panel.hide_dialogue()
	GameState.reset_dialogue()
	dialogue_layer.hide()

	if payload.get("openShop", false):
		var npc_id := str(payload.get("npcId", ""))
		var npc_name := str(payload.get("npcName", "商人"))
		shop_layer.show()
		shop_panel.open_for_npc(npc_id, npc_name)
	else:
		_update_status()

func _on_shop_panel_closed() -> void:
	shop_panel.hide()
	shop_layer.hide()
	_update_status()

func _try_npc_interact() -> void:
	var npc := MapRegistry.get_adjacent_npc(GameState.player_map_id, GameState.player_x, GameState.player_y)
	if npc.is_empty():
		return
	NetworkClient.npc_interact(str(npc.get("id", "")), GameState.player_map_id)

func _check_nearby_npc() -> void:
	var npc := MapRegistry.get_adjacent_npc(GameState.player_map_id, GameState.player_x, GameState.player_y)
	var hint_text := " | [F] 與 %s 對話" % str(npc.get("name", "")) if not npc.is_empty() else ""
	GameState.last_message = hint_text

func _toggle_quest_log() -> void:
	if quest_layer.visible:
		quest_log_panel.hide()
		quest_layer.hide()
	else:
		skill_tree_layer.hide()
		companion_layer.hide()
		quest_layer.show()
		quest_log_panel.open()

func _on_quests_button_pressed() -> void:
	_toggle_quest_log()

func _on_quest_log_closed() -> void:
	quest_log_panel.hide()
	quest_layer.hide()
	_update_status()

func _set_gameplay_visible(visible: bool) -> void:
	world.visible = visible
	ui.visible = visible
	skills_button.visible = visible
	companions_button.visible = visible
	quests_button.visible = visible
	equipment_button.visible = visible
	hotkey_button.visible = visible
	status_button.visible = visible
	party_button.visible = visible

func _update_nearby_players_for_party() -> void:
	var nearby: Array = []
	for child in other_players.get_children():
		if child.has_method("get_player_id"):
			var player_id := int(child.get_player_id())
			if player_id > 0:
				nearby.append({
					"playerId": player_id,
					"playerName": child.get_player_name(),
					"playerLevel": child.get_player_level()
				})
	GameState.nearby_players_for_party = nearby

func _handle_party_invite_ok(payload: Dictionary) -> void:
	if payload.has("inviterId"):
		GameState.pending_party_invite_from = int(payload.get("inviterId", 0))
		GameState.pending_party_invite_name = str(payload.get("inviterName", "旅人"))
		_check_party_invite_prompt()
		return
	_log(payload.get("message", "已送出組隊邀請"))
	if party_layer.visible:
		party_panel.show_message(str(payload.get("message", "")))

func _handle_party_update(payload: Dictionary) -> void:
	GameState.apply_party_state(payload)
	_log("組隊狀態已更新")
	if party_layer.visible:
		party_panel.refresh()
	_update_status()

func _handle_party_state_payload(payload: Dictionary) -> void:
	if payload.has("party"):
		GameState.apply_party_state(payload.get("party", {}))
	_log(payload.get("message", "組隊狀態已更新"))
	if party_layer.visible:
		party_panel.refresh()
	_update_status()

func _handle_party_sync(payload: Dictionary) -> void:
	_awaiting_server = false
	var new_x := int(payload.get("x", GameState.player_x))
	var new_y := int(payload.get("y", GameState.player_y))
	var new_map_id := str(payload.get("mapId", GameState.player_map_id))
	GameState.player_x = new_x
	GameState.player_y = new_y
	GameState.player_map_id = new_map_id

	if payload.get("mapChanged", false):
		player.stop_movement()
		_pending_sync_grid = Vector2i(-9999, -9999)
		other_players.clear_all()
		_pending_other_players = payload.get("otherPlayers", [])
		_log(payload.get("message", "跟隨隊長傳送"))
		if world_map.get_current_map_id() != new_map_id:
			world_map.load_map_by_id(new_map_id)
		else:
			_on_map_loaded()
		return

	player.stop_movement()
	var spawn := world_map.find_spawn_point(Vector2i(new_x, new_y))
	player.setup(world_map, spawn)
	_log(payload.get("message", "跟隨隊長移動"))

	if payload.get("encounter", false):
		_log("隊長遭遇野生怪物！")
	_update_status()

func _check_party_invite_prompt() -> void:
	if GameState.pending_party_invite_from <= 0:
		return
	_log("收到來自 %s 的組隊邀請（組隊面板可接受）" % GameState.pending_party_invite_name)

func _on_party_button_pressed() -> void:
	_toggle_party_panel()

func _toggle_party_panel() -> void:
	if party_layer.visible:
		party_panel.hide()
		party_layer.hide()
	else:
		skill_tree_layer.hide()
		companion_layer.hide()
		quest_layer.hide()
		equipment_layer.hide()
		hotkey_layer.hide()
		status_layer.hide()
		shop_layer.hide()
		party_layer.show()
		party_panel.open()

func _on_party_panel_closed() -> void:
	party_panel.hide()
	party_layer.hide()
	_update_status()

func accept_party_invite() -> void:
	if GameState.pending_party_invite_from > 0:
		NetworkClient.party_accept()

func decline_party_invite() -> void:
	if GameState.pending_party_invite_from > 0:
		NetworkClient.party_decline()
		GameState.pending_party_invite_from = 0
		GameState.pending_party_invite_name = ""

func _on_skills_button_pressed() -> void:
	_toggle_skill_tree()

func _toggle_skill_tree() -> void:
	if skill_tree_layer.visible:
		skill_tree_panel.hide()
		skill_tree_layer.hide()
	else:
		companion_layer.hide()
		companion_panel.hide()
		skill_tree_layer.show()
		skill_tree_panel.open()

func _on_companions_button_pressed() -> void:
	_toggle_companion_panel()

func _toggle_companion_panel() -> void:
	if companion_layer.visible:
		companion_panel.hide()
		companion_layer.hide()
	else:
		skill_tree_layer.hide()
		skill_tree_panel.hide()
		companion_layer.show()
		companion_panel.open()

func _on_skill_tree_closed() -> void:
	skill_tree_panel.hide()
	skill_tree_layer.hide()
	_update_status()

func _on_companion_panel_closed() -> void:
	companion_panel.hide()
	companion_layer.hide()
	_update_status()

func _on_skill_tree_updated() -> void:
	_update_status()

func _on_equipment_button_pressed() -> void:
	_toggle_equipment_panel()

func _toggle_equipment_panel() -> void:
	if equipment_layer.visible:
		equipment_panel.hide()
		equipment_layer.hide()
	else:
		skill_tree_layer.hide()
		companion_layer.hide()
		quest_layer.hide()
		status_layer.hide()
		hotkey_layer.hide()
		shop_layer.hide()
		equipment_layer.show()
		equipment_panel.open()

func _on_equipment_panel_closed() -> void:
	equipment_panel.hide()
	equipment_layer.hide()
	_update_status()

func _on_status_button_pressed() -> void:
	_toggle_status_panel()

func _toggle_status_panel() -> void:
	if status_layer.visible:
		status_panel.hide()
		status_layer.hide()
	else:
		skill_tree_layer.hide()
		companion_layer.hide()
		quest_layer.hide()
		equipment_layer.hide()
		hotkey_layer.hide()
		status_layer.hide()
		shop_layer.hide()
		status_layer.show()
		status_panel.open()

func _on_status_panel_closed() -> void:
	status_panel.hide()
	status_layer.hide()
	_update_status()

func _on_hotkey_button_pressed() -> void:
	_toggle_hotkey_panel()

func _toggle_hotkey_panel() -> void:
	if hotkey_layer.visible:
		hotkey_panel.hide()
		hotkey_layer.hide()
	else:
		skill_tree_layer.hide()
		companion_layer.hide()
		quest_layer.hide()
		equipment_layer.hide()
		hotkey_layer.hide()
		shop_layer.hide()
		hotkey_layer.show()
		hotkey_panel.open()

func _on_hotkey_panel_closed() -> void:
	hotkey_panel.hide()
	hotkey_layer.hide()
	_update_status()

func _show_login_screen(message: String = "") -> void:
	character_create_layer.hide()
	skill_tree_layer.hide()
	companion_layer.hide()
	dialogue_layer.hide()
	quest_layer.hide()
	equipment_layer.hide()
	login_layer.show()
	login_panel.show()
	login_panel.set_status(message if not message.is_empty() else "請登入或註冊新帳號")
	status_label.text = "DeJaBu - 請登入"

func _update_status() -> void:
	var mode_text: String
	match GameState.mode:
		GameState.Mode.BATTLE: mode_text = "戰鬥"
		GameState.Mode.DIALOGUE: mode_text = "對話"
		_: mode_text = "探索"
	var pos := GameState.player_world_position
	var map_name := world_map.get_map_name() if world_map.get_current_map_id() == GameState.player_map_id else MapRegistry.get_map_name(GameState.player_map_id)
	var party_text := ""
	if GameState.in_player_party:
		party_text = " | 組隊 %d人" % GameState.party_members.size()
		if not GameState.is_party_leader:
			party_text += "（跟隨隊長）"
	status_label.text = "%s | Lv.%d | HP %d/%d | MP %d/%d | 金幣 %d | %s | X: %.0f  Y: %.0f | 模式: %s%s%s" % [
		GameState.player_name,
		GameState.player_level,
		GameState.player_current_hp,
		GameState.player_max_hp,
		GameState.player_current_mp,
		GameState.player_max_mp,
		GameState.player_gold,
		map_name,
		pos.x,
		pos.y,
		mode_text,
		GameState.last_message,
		party_text
	]

func _log(text: String) -> void:
	log_label.append_text(text + "\n")
