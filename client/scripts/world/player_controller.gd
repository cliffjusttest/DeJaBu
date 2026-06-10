class_name PlayerController
extends CharacterBody2D

const IsoGroundRendererScript = preload("res://scripts/world/iso_ground_renderer.gd")
const PlayerSpriteFactoryScript = preload("res://scripts/world/player_sprite_factory.gd")

signal grid_cell_changed(grid_x: int, grid_y: int, direction: String)

@export var move_speed := 180.0
@export var click_arrival_distance := 6.0

@onready var sprite: AnimatedSprite2D = $AnimatedSprite2D
@onready var shadow: Sprite2D = $Shadow

var _world_map: WorldMap
var _input_direction := Vector2.ZERO
var _click_target: Vector2 = Vector2.INF
var _last_grid := Vector2i(-9999, -9999)
var _facing_direction := "down"
var _is_walking := false

func setup(world_map: WorldMap, spawn_grid: Vector2i) -> void:
	_world_map = world_map
	y_sort_enabled = true
	if sprite.sprite_frames == null:
		sprite.sprite_frames = PlayerSpriteFactoryScript.create_sprite_frames()
	if shadow.texture == null:
		shadow.texture = IsoGroundRendererScript.create_shadow_texture()
	shadow.z_index = -1
	shadow.position = Vector2(0, 4)
	global_position = world_map.grid_to_world(spawn_grid.x, spawn_grid.y)
	_last_grid = spawn_grid
	_facing_direction = "down"
	_play_directional_animation(false)

func apply_appearance(appearance_code: String) -> void:
	sprite.modulate = CharacterAppearanceData.tint_for(appearance_code)

func is_moving() -> bool:
	return velocity.length() > 1.0 or _click_target != Vector2.INF

func set_input_direction(direction: Vector2) -> void:
	_input_direction = direction
	if direction != Vector2.ZERO:
		_click_target = Vector2.INF

func set_click_target(world_pos: Vector2) -> void:
	if _world_map == null:
		return
	if not _world_map.is_world_walkable(world_pos):
		return
	_click_target = world_pos
	_input_direction = Vector2.ZERO

func stop_movement() -> void:
	_input_direction = Vector2.ZERO
	_click_target = Vector2.INF
	velocity = Vector2.ZERO
	_is_walking = false
	_play_directional_animation(false)

func get_facing_direction() -> String:
	return _facing_direction

func _physics_process(_delta: float) -> void:
	if _world_map == null:
		return

	var direction := _input_direction
	if direction == Vector2.ZERO and _click_target != Vector2.INF:
		var to_target := _click_target - global_position
		if to_target.length() <= click_arrival_distance:
			_click_target = Vector2.INF
		else:
			direction = to_target.normalized()

	var moving := direction != Vector2.ZERO
	if moving:
		velocity = direction * move_speed
		_update_facing_from_vector(direction)
	else:
		velocity = Vector2.ZERO

	move_and_slide()
	_update_animation(moving)
	_emit_grid_if_changed()

func _emit_grid_if_changed() -> void:
	var grid := _world_map.world_to_grid(global_position)
	if grid == _last_grid:
		return
	if not _world_map.is_walkable(grid.x, grid.y):
		return
	_last_grid = grid
	grid_cell_changed.emit(grid.x, grid.y, _facing_direction)

func _update_facing_from_vector(direction: Vector2) -> void:
	if absf(direction.x) >= absf(direction.y):
		_update_facing("right" if direction.x > 0.0 else "left")
	else:
		_update_facing("down" if direction.y > 0.0 else "up")

func _update_facing(direction: String) -> void:
	if _facing_direction == direction:
		return
	_facing_direction = direction
	_play_directional_animation(_is_walking)

func _update_animation(moving: bool) -> void:
	if _is_walking == moving:
		return
	_is_walking = moving
	_play_directional_animation(moving)

func _play_directional_animation(walking: bool) -> void:
	var anim_direction := _facing_direction
	sprite.flip_h = false
	if anim_direction == "left":
		anim_direction = "right"
		sprite.flip_h = true

	var animation_name := ("walk_" if walking else "idle_") + anim_direction
	if sprite.sprite_frames == null or not sprite.sprite_frames.has_animation(animation_name):
		return
	if sprite.animation != animation_name:
		sprite.play(animation_name)
