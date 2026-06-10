class_name WorldMap
extends Node2D

const IsoGroundRendererScript = preload("res://scripts/world/iso_ground_renderer.gd")
const IsoCoordsScript = preload("res://scripts/world/iso_coords.gd")

signal map_loaded

@onready var ground: Sprite2D = $Ground
@onready var props: Node2D = $Props
@onready var obstacles: StaticBody2D = $Obstacles

var _walkable: Dictionary = {}
var _map_size := Vector2i.ZERO
var _map_lines: PackedStringArray = []
var _ground_offset := Vector2.ZERO
var _current_map_id := ""

func _ready() -> void:
	load_map_by_id(MapRegistry.get_default_map_id())

func get_current_map_id() -> String:
	return _current_map_id

func get_map_name() -> String:
	return MapRegistry.get_map_name(_current_map_id)

func load_map_by_id(map_id: String) -> void:
	_current_map_id = map_id
	_load_map(MapRegistry.get_map_path(map_id))

func get_tile_width() -> int:
	return IsoTileset.TILE_WIDTH

func get_tile_height() -> int:
	return IsoTileset.TILE_HEIGHT

func get_map_size() -> Vector2i:
	return _map_size

func is_walkable(grid_x: int, grid_y: int) -> bool:
	if grid_x < 0 or grid_y < 0 or grid_x >= _map_size.x or grid_y >= _map_size.y:
		return false
	return _walkable.get(Vector2i(grid_x, grid_y), false)

func is_world_walkable(world_pos: Vector2) -> bool:
	var grid := world_to_grid(world_pos)
	return is_walkable(grid.x, grid.y)

func grid_to_world(grid_x: int, grid_y: int) -> Vector2:
	return _ground_offset + IsoCoordsScript.map_to_local(Vector2i(grid_x, grid_y))

func world_to_grid(world_pos: Vector2) -> Vector2i:
	return IsoCoordsScript.local_to_map(to_local(world_pos) - _ground_offset)

func find_spawn_point(preferred: Vector2i = Vector2i(5, 5)) -> Vector2i:
	if is_walkable(preferred.x, preferred.y):
		return preferred
	for y in range(_map_size.y):
		for x in range(_map_size.x):
			var cell := Vector2i(x, y)
			if _walkable.get(cell, false):
				return cell
	return Vector2i.ZERO

func _load_map(path: String) -> void:
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null:
		push_error("無法讀取地圖: %s" % path)
		return

	_map_lines.clear()
	while not file.eof_reached():
		var line := file.get_line()
		if line.length() > 0:
			_map_lines.append(line)
	file.close()

	if _map_lines.is_empty():
		push_error("地圖檔案為空: %s" % path)
		return

	_map_size = Vector2i(_map_lines[0].length(), _map_lines.size())
	_walkable.clear()

	for y in range(_map_lines.size()):
		var row := _map_lines[y]
		for x in range(row.length()):
			_walkable[Vector2i(x, y)] = _char_walkable(row[x])

	var baked: Dictionary = IsoGroundRendererScript.bake_ground(_map_lines, _map_size)
	ground.texture = baked["texture"]
	ground.centered = false
	_ground_offset = baked["offset"]
	ground.position = _ground_offset
	ground.z_index = -10

	_build_props()
	_build_obstacle_collisions()
	call_deferred("emit_signal", "map_loaded")

func _build_props() -> void:
	for child in props.get_children():
		child.queue_free()

	var tree_tex: ImageTexture = IsoGroundRendererScript.create_tree_texture()
	var wall_tex: ImageTexture = IsoGroundRendererScript.create_wall_texture()

	for y in range(_map_lines.size()):
		var row := _map_lines[y]
		for x in range(row.length()):
			var ch := row[x]
			var cell := Vector2i(x, y)
			var pos := grid_to_world(cell.x, cell.y)

			match ch:
				"T":
					_add_prop(tree_tex, pos, Vector2(0, -18))
				"#":
					_add_prop(wall_tex, pos, Vector2(0, -10))
				"@":
					_add_prop(IsoGroundRendererScript.create_portal_texture(), pos, Vector2(0, -14))

func _add_prop(texture: Texture2D, position: Vector2, offset: Vector2) -> void:
	var sprite := Sprite2D.new()
	sprite.texture = texture
	sprite.position = position
	sprite.offset = offset
	props.add_child(sprite)

func _build_obstacle_collisions() -> void:
	for child in obstacles.get_children():
		child.queue_free()

	var shape_size := Vector2(
		IsoTileset.TILE_WIDTH - 6.0,
		IsoTileset.TILE_HEIGHT - 4.0
	)

	for y in range(_map_size.y):
		for x in range(_map_size.x):
			var cell := Vector2i(x, y)
			if _walkable.get(cell, false):
				continue

			var shape_node := CollisionShape2D.new()
			var shape := RectangleShape2D.new()
			shape.size = shape_size
			shape_node.shape = shape
			shape_node.position = grid_to_world(cell.x, cell.y)
			obstacles.add_child(shape_node)

func _char_walkable(ch: String) -> bool:
	return ch in [".", "P", "=", "@"]
