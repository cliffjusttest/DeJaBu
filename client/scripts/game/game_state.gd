extends Node

enum Mode { EXPLORE, BATTLE, DIALOGUE }

var player_name := ""
var player_id := 0
var auth_token := ""
var is_authenticated := false
var has_character := false
var player_x := 5
var player_y := 5
var player_map_id := "village"
var player_element := ""
var player_appearance := ""
var player_stats: Dictionary = CharacterStatsData.zero_base()
var skill_points := 10
var player_level := 1
var player_exp := 0
var exp_to_next_level := 100
var player_current_hp := 50
var player_max_hp := 50
var player_world_position := Vector2.ZERO
var mode := Mode.EXPLORE
var battle_data: Dictionary = {}
var last_message := ""
var companions: Array = []
var active_quests: Array = []
var dialogue_npc_id := ""
var dialogue_node_key := ""

func apply_auth(data: Dictionary) -> void:
	auth_token = str(data.get("token", ""))
	player_id = int(data.get("playerId", 0))
	player_name = str(data.get("playerName", ""))
	player_x = int(data.get("playerX", 5))
	player_y = int(data.get("playerY", 5))
	player_map_id = str(data.get("playerMapId", "village"))
	has_character = bool(data.get("hasCharacter", false))
	player_element = str(data.get("element", ""))
	player_appearance = str(data.get("appearance", ""))
	if data.has("stats"):
		player_stats = CharacterStatsData.from_payload(data.get("stats"))
	else:
		player_stats = CharacterStatsData.zero_base()
	is_authenticated = false

func mark_session_authenticated() -> void:
	is_authenticated = true

func clear_auth() -> void:
	player_name = ""
	player_id = 0
	auth_token = ""
	has_character = false
	player_element = ""
	player_appearance = ""
	player_stats = CharacterStatsData.zero_base()
	skill_points = 10
	player_level = 1
	player_exp = 0
	exp_to_next_level = 100
	player_current_hp = 50
	player_max_hp = 50
	companions = []
	active_quests = []
	dialogue_npc_id = ""
	dialogue_node_key = ""
	is_authenticated = false

func reset_battle() -> void:
	battle_data = {}
	mode = Mode.EXPLORE

func reset_dialogue() -> void:
	dialogue_npc_id = ""
	dialogue_node_key = ""
	mode = Mode.EXPLORE
