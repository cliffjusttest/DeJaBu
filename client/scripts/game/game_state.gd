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
var player_current_mp := 20
var player_max_mp := 20
var player_gold := 100
var player_world_position := Vector2.ZERO
var mode := Mode.EXPLORE
var battle_data: Dictionary = {}
var last_message := ""
var companions: Array = []
var active_quests: Array = []
var dialogue_npc_id := ""
var dialogue_node_key := ""

var in_player_party := false
var is_party_leader := false
var party_leader_id := 0
var party_members: Array = []
var party_max_size := 5
var party_max_companions := 1
var pending_party_invite_from := 0
var pending_party_invite_name := ""
var nearby_players_for_party: Array = []

func apply_party_state(party: Dictionary) -> void:
	in_player_party = bool(party.get("inParty", false))
	is_party_leader = bool(party.get("isLeader", false))
	party_leader_id = int(party.get("leaderId", 0))
	party_max_size = int(party.get("maxSize", 5))
	party_max_companions = int(party.get("maxCompanionsPerPlayer", 1))
	party_members = party.get("members", [])
	if party.has("pendingInviteFrom"):
		pending_party_invite_from = int(party.get("pendingInviteFrom", 0))
		pending_party_invite_name = str(party.get("pendingInviteFromName", ""))
	else:
		pending_party_invite_from = 0
		pending_party_invite_name = ""

func clear_party_state() -> void:
	in_player_party = false
	is_party_leader = false
	party_leader_id = 0
	party_members = []
	pending_party_invite_from = 0
	pending_party_invite_name = ""

func can_control_movement() -> bool:
	if not in_player_party:
		return true
	return is_party_leader

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
	player_current_mp = 20
	player_max_mp = 20
	player_gold = 100
	companions = []
	active_quests = []
	dialogue_npc_id = ""
	dialogue_node_key = ""
	clear_party_state()
	is_authenticated = false

func reset_battle() -> void:
	battle_data = {}
	mode = Mode.EXPLORE

func reset_dialogue() -> void:
	dialogue_npc_id = ""
	dialogue_node_key = ""
	mode = Mode.EXPLORE
