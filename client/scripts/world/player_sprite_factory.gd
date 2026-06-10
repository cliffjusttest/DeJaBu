class_name PlayerSpriteFactory
extends RefCounted

const FRAME_W := 20
const FRAME_H := 28
const WALK_FRAME_COUNT := 4

const HAIR := Color(0.45, 0.28, 0.14)
const SKIN := Color(0.95, 0.82, 0.68)
const CLOTHES := Color(0.35, 0.28, 0.55)
const SHOES := Color(0.22, 0.18, 0.32)
const BACK_HAIR := Color(0.38, 0.22, 0.12)

static func create_sprite_frames() -> SpriteFrames:
	var frames := SpriteFrames.new()
	if frames.has_animation("default"):
		frames.remove_animation("default")

	for direction in ["down", "up", "right"]:
		var idle_name := "idle_%s" % direction
		frames.add_animation(idle_name)
		frames.set_animation_speed(idle_name, 1.0)
		frames.set_animation_loop(idle_name, true)
		frames.add_frame(idle_name, _create_frame_texture(direction, 0))

		var walk_name := "walk_%s" % direction
		frames.add_animation(walk_name)
		frames.set_animation_speed(walk_name, 8.0)
		frames.set_animation_loop(walk_name, true)
		for step in range(WALK_FRAME_COUNT):
			frames.add_frame(walk_name, _create_frame_texture(direction, step))

	return frames

static func preview_texture() -> Texture2D:
	return _create_frame_texture("down", 0)

static func _create_frame_texture(direction: String, step: int) -> Texture2D:
	var image := Image.create(FRAME_W, FRAME_H, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))

	match direction:
		"down":
			_draw_down(image, step)
		"up":
			_draw_up(image, step)
		"right":
			_draw_right(image, step)

	return ImageTexture.create_from_image(image)

static func _draw_down(image: Image, step: int) -> void:
	var bob := 1 if step in [1, 3] else 0
	var left_offset := 0
	var right_offset := 0
	match step:
		1:
			left_offset = -1
			right_offset = 1
		2:
			left_offset = 0
			right_offset = 0
		3:
			left_offset = 1
			right_offset = -1

	_fill_rect(image, 6, 2 + bob, 8, 4, HAIR)
	_fill_rect(image, 6, 6 + bob, 8, 6, SKIN)
	_fill_rect(image, 5, 12 + bob, 10, 8, CLOTHES)
	_fill_rect(image, 6 + left_offset, 20 + bob, 3, 5, SHOES)
	_fill_rect(image, 11 + right_offset, 20 + bob, 3, 5, SHOES)

static func _draw_up(image: Image, step: int) -> void:
	var bob := 1 if step in [1, 3] else 0
	var left_offset := 0
	var right_offset := 0
	match step:
		1:
			left_offset = 1
			right_offset = -1
		3:
			left_offset = -1
			right_offset = 1

	_fill_rect(image, 5, 2 + bob, 10, 10, BACK_HAIR)
	_fill_rect(image, 5, 12 + bob, 10, 8, CLOTHES)
	_fill_rect(image, 6 + left_offset, 20 + bob, 3, 5, SHOES)
	_fill_rect(image, 11 + right_offset, 20 + bob, 3, 5, SHOES)

static func _draw_right(image: Image, step: int) -> void:
	var bob := 1 if step in [1, 3] else 0
	var front_leg_x := 10
	var back_leg_x := 8
	var front_leg_y := 20
	match step:
		1:
			front_leg_x = 11
			front_leg_y = 19
			back_leg_x = 7
		2:
			front_leg_x = 10
			front_leg_y = 20
			back_leg_x = 8
		3:
			front_leg_x = 9
			front_leg_y = 21
			back_leg_x = 9

	_fill_rect(image, 9, 2 + bob, 7, 4, HAIR)
	_fill_rect(image, 11, 6 + bob, 5, 5, SKIN)
	_fill_rect(image, 8, 11 + bob, 9, 9, CLOTHES)
	_set_pixel(image, 14, 8 + bob, SKIN)
	_fill_rect(image, back_leg_x, 20 + bob, 2, 5, SHOES)
	_fill_rect(image, front_leg_x, front_leg_y + bob, 3, 5, SHOES)

static func _fill_rect(image: Image, x: int, y: int, width: int, height: int, color: Color) -> void:
	for py in range(height):
		for px in range(width):
			_set_pixel(image, x + px, y + py, color)

static func _set_pixel(image: Image, x: int, y: int, color: Color) -> void:
	if x < 0 or y < 0 or x >= FRAME_W or y >= FRAME_H:
		return
	image.set_pixel(x, y, color)
