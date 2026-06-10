class_name IsoGroundRenderer
extends RefCounted

const IsoCoordsScript = preload("res://scripts/world/iso_coords.gd")

const TERRAIN_COLORS := {
	".": Color(0.34, 0.60, 0.28),
	"P": Color(0.70, 0.58, 0.38),
	"=": Color(0.66, 0.54, 0.34),
	"#": Color(0.50, 0.50, 0.54),
	"T": Color(0.34, 0.60, 0.28),
	"W": Color(0.24, 0.48, 0.76),
	"@": Color(0.58, 0.42, 0.82),
}

static func bake_ground(lines: PackedStringArray, map_size: Vector2i) -> Dictionary:
	var min_pos := Vector2(INF, INF)
	var max_pos := Vector2(-INF, -INF)
	var half_w := IsoTileset.TILE_WIDTH * 0.5
	var half_h := IsoTileset.TILE_HEIGHT * 0.5

	for y in range(map_size.y):
		for x in range(map_size.x):
			var center: Vector2 = IsoCoordsScript.map_to_local(Vector2i(x, y))
			min_pos.x = minf(min_pos.x, center.x - half_w)
			min_pos.y = minf(min_pos.y, center.y - half_h)
			max_pos.x = maxf(max_pos.x, center.x + half_w)
			max_pos.y = maxf(max_pos.y, center.y + half_h)

	var width := int(ceilf(max_pos.x - min_pos.x)) + 2
	var height := int(ceilf(max_pos.y - min_pos.y)) + 2
	var image := Image.create(width, height, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))

	for py in range(height):
		for px in range(width):
			var local := Vector2(px, py) + min_pos
			var cell: Vector2i = IsoCoordsScript.local_to_map(local)
			if cell.x < 0 or cell.y < 0 or cell.x >= map_size.x or cell.y >= map_size.y:
				continue
			if not IsoCoordsScript.contains_point(local, cell):
				continue

			var ch := _char_at(lines, cell)
			image.set_pixel(px, py, _sample_ground_pixel(ch, local, cell))

	return {
		"texture": ImageTexture.create_from_image(image),
		"offset": min_pos,
	}

static func create_tree_texture() -> ImageTexture:
	var image := Image.create(36, 52, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))

	for y in range(30, 52):
		for x in range(14, 22):
			image.set_pixel(x, y, Color(0.40, 0.26, 0.14))

	for y in range(52):
		for x in range(36):
			var dx := absf(x - 18.0) / 16.0
			var dy := absf(y - 18.0) / 18.0
			if dx + dy <= 1.0:
				var shade := 0.90 + sin(x * 0.55 + y * 0.35) * 0.08
				image.set_pixel(x, y, Color(0.12 * shade, 0.44 * shade, 0.16 * shade, 1.0))

	return ImageTexture.create_from_image(image)

static func create_wall_texture() -> ImageTexture:
	var image := Image.create(40, 44, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))

	for y in range(44):
		for x in range(40):
			var top_dx := absf(x - 20.0) / 18.0
			var top_dy := absf(y - 14.0) / 10.0
			if top_dx + top_dy <= 1.0:
				var shade := 0.92 + ((x + y) % 4) * 0.025
				image.set_pixel(x, y, Color(0.56 * shade, 0.56 * shade, 0.60 * shade, 1.0))
				continue

			if x >= 4 and x < 20 and y >= 14 and y < 40:
				var side_shade := 0.78 + (y - 14.0) / 52.0
				image.set_pixel(x, y, Color(0.38 * side_shade, 0.38 * side_shade, 0.42 * side_shade, 1.0))
			elif x >= 20 and x < 36 and y >= 14 and y < 40:
				var side_shade := 0.88 + (y - 14.0) / 52.0
				image.set_pixel(x, y, Color(0.48 * side_shade, 0.48 * side_shade, 0.52 * side_shade, 1.0))

	return ImageTexture.create_from_image(image)

static func create_portal_texture() -> ImageTexture:
	var image := Image.create(32, 40, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))

	for y in range(40):
		for x in range(32):
			var dx := absf(x - 16.0) / 14.0
			var dy := absf(y - 20.0) / 16.0
			if dx + dy <= 1.0:
				var pulse := 0.85 + sin(x * 0.4 + y * 0.3) * 0.12
				image.set_pixel(x, y, Color(0.52 * pulse, 0.34 * pulse, 0.92 * pulse, 0.95))

	for y in range(40):
		for x in range(32):
			var ring_dx := absf(x - 16.0) / 12.0
			var ring_dy := absf(y - 20.0) / 14.0
			var ring_dist := absf(ring_dx + ring_dy - 0.82)
			if ring_dist < 0.08:
				image.set_pixel(x, y, Color(0.92, 0.88, 1.0, 0.9))

	return ImageTexture.create_from_image(image)

static func create_shadow_texture() -> ImageTexture:
	var image := Image.create(28, 12, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))

	for y in range(12):
		for x in range(28):
			var dx := absf(x - 14.0) / 13.0
			var dy := absf(y - 6.0) / 5.0
			if dx + dy <= 1.0:
				var alpha := lerpf(0.28, 0.08, dx + dy)
				image.set_pixel(x, y, Color(0, 0, 0, alpha))

	return ImageTexture.create_from_image(image)

static func _char_at(lines: PackedStringArray, cell: Vector2i) -> String:
	if cell.y < 0 or cell.y >= lines.size():
		return "."
	var row := lines[cell.y]
	if cell.x < 0 or cell.x >= row.length():
		return "."
	return row[cell.x]

static func _sample_ground_pixel(ch: String, local_pos: Vector2, cell: Vector2i) -> Color:
	var base: Color = TERRAIN_COLORS.get(ch, TERRAIN_COLORS["."])
	var center: Vector2 = IsoCoordsScript.map_to_local(cell)
	var half_h := IsoTileset.TILE_HEIGHT * 0.5

	var rel_y: float = (local_pos.y - center.y) / half_h
	var light := clampf(1.0 - (rel_y + 1.0) * 0.5 * 0.14, 0.82, 1.0)
	var noise := sin(local_pos.x * 0.073 + local_pos.y * 0.061) * 0.025
	noise += sin(local_pos.x * 0.021 - local_pos.y * 0.017) * 0.015

	if ch == "W":
		noise += sin(local_pos.x * 0.22 + local_pos.y * 0.17) * 0.03
	elif ch in ["P", "="]:
		light = clampf(light + 0.04, 0.85, 1.0)
		noise *= 0.35

	var color := base.lightened(noise)
	color.r *= light
	color.g *= light
	color.b *= light
	return color
