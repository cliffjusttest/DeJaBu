class_name OtherPlayersManager
extends Node2D

const OtherPlayerScript = preload("res://scripts/world/other_player.gd")

var _world_map: WorldMap
var _players: Dictionary = {}

func setup(world_map: WorldMap) -> void:
	_world_map = world_map
	y_sort_enabled = true

func sync_players(payloads: Array) -> void:
	clear_all()
	for entry in payloads:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		_add_or_update(entry)

func handle_join(payload: Dictionary) -> void:
	if _world_map == null:
		return
	var map_id := str(payload.get("mapId", ""))
	if map_id != _world_map.get_current_map_id():
		return
	_add_or_update(payload)

func handle_leave(payload: Dictionary) -> void:
	var player_id := int(payload.get("playerId", -1))
	if player_id > 0:
		remove_player(player_id)

func handle_move(payload: Dictionary) -> void:
	handle_join(payload)

func _add_or_update(payload: Dictionary) -> void:
	var player_id := int(payload.get("playerId", -1))
	if player_id <= 0 or player_id == GameState.player_id:
		return
	if _world_map == null:
		return
	if str(payload.get("mapId", _world_map.get_current_map_id())) != _world_map.get_current_map_id():
		remove_player(player_id)
		return

	var node: OtherPlayer = _players.get(player_id)
	if node == null:
		node = OtherPlayerScript.new()
		node.setup(_world_map)
		add_child(node)
		_players[player_id] = node
	node.apply_payload(payload)

func remove_player(player_id: int) -> void:
	if not _players.has(player_id):
		return
	var node: OtherPlayer = _players[player_id]
	_players.erase(player_id)
	node.queue_free()

func clear_all() -> void:
	for node in _players.values():
		node.queue_free()
	_players.clear()
