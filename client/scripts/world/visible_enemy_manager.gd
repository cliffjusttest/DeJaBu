class_name VisibleEnemyManager
extends Node2D

var _world_map: WorldMap
var _encounter_cooldown: EncounterCooldown
var _enemy_nodes: Dictionary = {}
var _enemy_tex_wolf: ImageTexture
var _enemy_tex_wisp: ImageTexture

func setup(world_map: WorldMap, encounter_cooldown: EncounterCooldown) -> void:
	_world_map = world_map
	_encounter_cooldown = encounter_cooldown
	_enemy_tex_wolf = _create_enemy_texture(Color(0.75, 0.28, 0.22))
	_enemy_tex_wisp = _create_enemy_texture(Color(0.45, 0.35, 0.85))
	y_sort_enabled = true

func clear_all() -> void:
	for child in get_children():
		child.queue_free()
	_enemy_nodes.clear()

func sync_enemies(enemies: Array) -> void:
	if _world_map == null:
		return

	var seen: Dictionary = {}
	for entry in enemies:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var enemy_id := str(entry.get("id", ""))
		if enemy_id.is_empty():
			continue
		seen[enemy_id] = true
		var x := int(entry.get("x", 0))
		var y := int(entry.get("y", 0))
		var template_id := str(entry.get("templateId", "wild_wolf"))
		var node: Node2D = _enemy_nodes.get(enemy_id, null)
		if node == null:
			node = _create_enemy_sprite(template_id)
			_enemy_nodes[enemy_id] = node
			add_child(node)
		node.position = _world_map.grid_to_world(x, y)
		_update_visibility(enemy_id, node)

	for enemy_id in _enemy_nodes.keys():
		if not seen.has(enemy_id):
			var stale: Node = _enemy_nodes[enemy_id]
			stale.queue_free()
			_enemy_nodes.erase(enemy_id)

func _create_enemy_sprite(template_id: String) -> Sprite2D:
	var sprite := Sprite2D.new()
	sprite.texture = _enemy_tex_wisp if template_id == "shadow_wisp" else _enemy_tex_wolf
	sprite.offset = Vector2(0, -18)
	sprite.z_index = 1
	return sprite

func _update_visibility(enemy_id: String, node: Sprite2D) -> void:
	if _encounter_cooldown != null and _encounter_cooldown.is_enemy_masked(enemy_id):
		node.visible = false
	else:
		node.visible = true

func refresh_mask_visibility() -> void:
	for enemy_id in _enemy_nodes.keys():
		var node: Sprite2D = _enemy_nodes[enemy_id]
		_update_visibility(enemy_id, node)

func _create_enemy_texture(color: Color) -> ImageTexture:
	var image := Image.create(24, 28, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))
	for py in range(10, 24):
		for px in range(6, 18):
			image.set_pixel(px, py, color)
	for py in range(4, 12):
		for px in range(7, 17):
			var dx := absf(px - 12.0) / 5.0
			var dy := absf(py - 8.0) / 5.0
			if dx * dx + dy * dy <= 1.0:
				image.set_pixel(px, py, color.lightened(0.15))
	for py in range(24, 28):
		for px in range(7, 11):
			image.set_pixel(px, py, color.darkened(0.2))
		for px in range(13, 17):
			image.set_pixel(px, py, color.darkened(0.2))
	return ImageTexture.create_from_image(image)
