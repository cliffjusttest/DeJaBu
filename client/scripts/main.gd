extends Node2D

@onready var world_map: WorldMap = $World/WorldMap
@onready var player: PlayerController = $World/Player
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
@onready var skills_button: Button = $UI/SkillsButton
@onready var companions_button: Button = $UI/CompanionsButton
@onready var world: Node2D = $World
@onready var ui: CanvasLayer = $UI

var _last_direction := "down"
var _pending_sync_grid := Vector2i(-9999, -9999)
var _awaiting_server := false
var _game_started := false
func _ready() -> void:
	world_map.map_loaded.connect(_on_map_loaded)
	player.grid_cell_changed.connect(_on_grid_cell_changed)
	battle_scene.battle_action_requested.connect(_on_battle_action_requested)
	login_panel.authenticated.connect(_on_login_authenticated)
	login_panel.auth_failed.connect(_on_login_auth_failed)
	character_create_panel.character_created.connect(_on_character_created)
	character_create_panel.create_failed.connect(_on_character_create_failed)
	skill_tree_panel.closed.connect(_on_skill_tree_closed)
	skill_tree_panel.tree_updated.connect(_on_skill_tree_updated)
	companion_panel.closed.connect(_on_companion_panel_closed)
	skills_button.pressed.connect(_on_skills_button_pressed)
	companions_button.pressed.connect(_on_companions_button_pressed)
	NetworkClient.connected.connect(_on_connected)
	NetworkClient.disconnected.connect(_on_disconnected)
	NetworkClient.connection_failed.connect(_on_connection_failed)
	NetworkClient.message_received.connect(_on_message_received)
	_set_gameplay_visible(false)
	skills_button.hide()
	companions_button.hide()
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
	_update_status()

func _process(_delta: float) -> void:
	if not _game_started or not NetworkClient.is_server_connected() or GameState.mode != GameState.Mode.EXPLORE:
		player.set_input_direction(Vector2.ZERO)
		return

	player.set_input_direction(_read_movement_input())
	GameState.player_world_position = player.global_position

func _unhandled_input(event: InputEvent) -> void:
	if not _game_started or not NetworkClient.is_server_connected():
		return

	if GameState.mode != GameState.Mode.EXPLORE:
		return

	if skill_tree_layer.visible or companion_layer.visible:
		return

	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_K:
				_toggle_skill_tree()
				get_viewport().set_input_as_handled()
				return
			KEY_P:
				_toggle_companion_panel()
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
	GameState.clear_auth()
	_set_gameplay_visible(false)
	character_create_layer.hide()
	skill_tree_layer.hide()
	companion_layer.hide()
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
	elif not GameState.player_stats.is_empty():
		GameState.player_max_hp = CharacterStatsData.max_hp(GameState.player_stats)
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
	_log(payload.get("message", "登入成功"))

	if not GameState.player_appearance.is_empty():
		player.apply_appearance(GameState.player_appearance)

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

func _end_battle() -> void:
	GameState.reset_battle()
	battle_scene.hide_battle()
	_update_status()

func _on_battle_action_requested(
	action: String,
	target_id: int = -1,
	actor_id: int = -1,
	skill_id: int = -1
) -> void:
	if GameState.mode != GameState.Mode.BATTLE:
		return
	if (action == "attack" or action == "capture" or action == "skill") and target_id <= 0:
		return
	if action == "skill" and skill_id <= 0:
		return
	if actor_id <= 0:
		return
	battle_scene.set_actions_enabled(false)
	NetworkClient.battle_action(action, target_id, actor_id, skill_id)

func _set_gameplay_visible(visible: bool) -> void:
	world.visible = visible
	ui.visible = visible
	skills_button.visible = visible
	companions_button.visible = visible

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

func _show_login_screen(message: String = "") -> void:
	character_create_layer.hide()
	skill_tree_layer.hide()
	companion_layer.hide()
	login_layer.show()
	login_panel.show()
	login_panel.set_status(message if not message.is_empty() else "請登入或註冊新帳號")
	status_label.text = "DeJaBu - 請登入"

func _update_status() -> void:
	var mode_text := "戰鬥" if GameState.mode == GameState.Mode.BATTLE else "探索"
	var pos := GameState.player_world_position
	var element_name := ElementData.display_name(GameState.player_element) if not GameState.player_element.is_empty() else ""
	var stats_text := CharacterStatsData.compact_text(GameState.player_stats)
	var name_with_element := "%s（%s）" % [GameState.player_name, element_name] if not element_name.is_empty() else GameState.player_name
	var map_name := world_map.get_map_name() if world_map.get_current_map_id() == GameState.player_map_id else MapRegistry.get_map_name(GameState.player_map_id)
	status_label.text = "%s | %s | Lv.%d | HP %d/%d | EXP %d/%d | 技能點 %d | 夥伴 %d | %s | X: %.0f  Y: %.0f | 模式: %s" % [
		name_with_element,
		stats_text,
		GameState.player_level,
		GameState.player_current_hp,
		GameState.player_max_hp,
		GameState.player_exp,
		GameState.exp_to_next_level,
		GameState.skill_points,
		GameState.companions.size(),
		map_name,
		pos.x,
		pos.y,
		mode_text
	]

func _log(text: String) -> void:
	log_label.append_text(text + "\n")
