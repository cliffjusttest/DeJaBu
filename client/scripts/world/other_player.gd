class_name OtherPlayer
extends Node2D

const IsoGroundRendererScript = preload("res://scripts/world/iso_ground_renderer.gd")
const PlayerSpriteFactoryScript = preload("res://scripts/world/player_sprite_factory.gd")

const MOVE_DURATION := 0.25

var player_id: int = -1
var player_level: int = 1

var _world_map: WorldMap
var _facing := "down"
var _is_walking := false
var _grid := Vector2i(-9999, -9999)
var _tween: Tween

var sprite: AnimatedSprite2D
var shadow: Sprite2D
var name_label: Label

func setup(world_map: WorldMap) -> void:
	_world_map = world_map
	y_sort_enabled = true

	sprite = AnimatedSprite2D.new()
	sprite.offset = Vector2(0, -12)
	sprite.sprite_frames = PlayerSpriteFactoryScript.create_sprite_frames()
	add_child(sprite)

	shadow = Sprite2D.new()
	shadow.texture = IsoGroundRendererScript.create_shadow_texture()
	shadow.z_index = -1
	shadow.position = Vector2(0, 4)
	shadow.modulate = Color(1, 1, 1, 0.85)
	add_child(shadow)
	move_child(shadow, 0)

	name_label = Label.new()
	name_label.add_theme_font_size_override("font_size", 11)
	name_label.modulate = Color(1.0, 0.95, 0.85)
	name_label.position = Vector2(-24, -42)
	add_child(name_label)

	_play_directional_animation(false)

func apply_payload(payload: Dictionary) -> void:
	player_id = int(payload.get("playerId", player_id))
	player_level = int(payload.get("playerLevel", player_level))
	name_label.text = str(payload.get("playerName", ""))
	if payload.has("playerAppearance"):
		sprite.modulate = CharacterAppearanceData.tint_for(str(payload.get("playerAppearance")))
	move_to(
		int(payload.get("x", _grid.x)),
		int(payload.get("y", _grid.y)),
		str(payload.get("direction", _facing))
	)

func move_to(grid_x: int, grid_y: int, direction: String) -> void:
	if _world_map == null:
		return

	var new_grid := Vector2i(grid_x, grid_y)
	var moving := new_grid != _grid and _grid != Vector2i(-9999, -9999)
	_update_facing(direction)

	if not moving:
		_grid = new_grid
		global_position = _world_map.grid_to_world(grid_x, grid_y)
		_play_directional_animation(false)
		return

	_grid = new_grid
	var target := _world_map.grid_to_world(grid_x, grid_y)
	if _tween != null and _tween.is_valid():
		_tween.kill()
	_tween = create_tween()
	_play_directional_animation(true)
	_tween.tween_property(self, "global_position", target, MOVE_DURATION)
	_tween.tween_callback(func(): _play_directional_animation(false))

func get_player_id() -> int:
	return player_id

func get_player_name() -> String:
	return name_label.text

func get_player_level() -> int:
	return player_level

func _update_facing(direction: String) -> void:
	if direction.is_empty():
		return
	_facing = direction
	_play_directional_animation(_is_walking)

func _play_directional_animation(walking: bool) -> void:
	_is_walking = walking
	var anim_direction := _facing
	sprite.flip_h = false
	if anim_direction == "left":
		anim_direction = "right"
		sprite.flip_h = true

	var animation_name := ("walk_" if walking else "idle_") + anim_direction
	if sprite.sprite_frames == null or not sprite.sprite_frames.has_animation(animation_name):
		return
	if sprite.animation != animation_name:
		sprite.play(animation_name)
