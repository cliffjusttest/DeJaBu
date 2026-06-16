extends Node

signal connected
signal disconnected
signal message_received(type: String, payload: Dictionary)
signal connection_failed(reason: String)

const SERVER_URL := "ws://localhost:8080/ws/game"

var _socket: WebSocketPeer = WebSocketPeer.new()
var _connected := false

func _ready() -> void:
	set_process(false)

func connect_to_server() -> void:
	if _connected:
		return

	if _socket.get_ready_state() != WebSocketPeer.STATE_CLOSED:
		_socket.close()

	_socket = WebSocketPeer.new()
	var err := _socket.connect_to_url(SERVER_URL)
	if err != OK:
		connection_failed.emit("無法連線至伺服器 (%s)" % error_string(err))
		return
	set_process(true)

func disconnect_from_server() -> void:
	_socket.close()
	_connected = false
	set_process(false)
	disconnected.emit()

func is_server_connected() -> bool:
	return _connected

func send_message(type: String, payload: Dictionary = {}) -> void:
	if not _connected:
		push_warning("尚未連線，無法送出訊息: %s" % type)
		return

	var message := {
		"type": type,
		"payload": payload
	}
	var json := JSON.stringify(message)
	_socket.send_text(json)

func _process(_delta: float) -> void:
	_socket.poll()

	var state := _socket.get_ready_state()
	if state == WebSocketPeer.STATE_OPEN:
		if not _connected:
			_connected = true
			connected.emit()
	elif state == WebSocketPeer.STATE_CLOSED:
		if _connected:
			_connected = false
			set_process(false)
			disconnected.emit()
		return

	while _socket.get_available_packet_count() > 0:
		var packet := _socket.get_packet()
		if _socket.was_string_packet():
			_handle_raw_message(packet.get_string_from_utf8())

func _handle_raw_message(raw: String) -> void:
	var json := JSON.new()
	var err := json.parse(raw)
	if err != OK:
		push_warning("無法解析伺服器訊息: %s" % raw)
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		return

	var message_type: String = data.get("type", "")
	var payload: Dictionary = data.get("payload", {})
	message_received.emit(message_type, payload)

func ping() -> void:
	send_message("PING")

func login_with_token(token: String) -> void:
	send_message("LOGIN", {"token": token})

func move(x: int, y: int, direction: String, map_id: String = "village") -> void:
	send_message("MOVE", {"x": x, "y": y, "direction": direction, "mapId": map_id})

func start_battle() -> void:
	send_message("BATTLE_START")

func battle_action(action: String, target_id: int = -1, actor_id: int = -1, skill_id: int = -1, item_id: int = -1) -> void:
	var payload := {"action": action}
	if target_id > 0:
		payload["targetId"] = target_id
	if actor_id > 0:
		payload["actorId"] = actor_id
	if skill_id > 0:
		payload["skillId"] = skill_id
	if item_id > 0:
		payload["itemId"] = item_id
	send_message("BATTLE_ACTION", payload)

func npc_interact(npc_id: String, map_id: String) -> void:
	send_message("NPC_INTERACT", {"npcId": npc_id, "mapId": map_id})

func dialogue_choice(npc_id: String, node_key: String, choice_index: int) -> void:
	send_message("DIALOGUE_CHOICE", {"npcId": npc_id, "nodeKey": node_key, "choiceIndex": choice_index})

func request_quest_list() -> void:
	send_message("QUEST_LIST")

func party_invite(player_id: int) -> void:
	send_message("PARTY_INVITE", {"playerId": player_id})

func party_accept() -> void:
	send_message("PARTY_ACCEPT")

func party_decline() -> void:
	send_message("PARTY_DECLINE")

func party_leave() -> void:
	send_message("PARTY_LEAVE")

func party_kick(player_id: int) -> void:
	send_message("PARTY_KICK", {"playerId": player_id})
