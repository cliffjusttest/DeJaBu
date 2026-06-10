class_name IsoTileset
extends RefCounted

const TILE_GRASS := Vector2i(0, 0)
const TILE_PATH := Vector2i(1, 0)
const TILE_WIDTH := 64
const TILE_HEIGHT := 32

const TILE_COLORS := {
	Vector2i(0, 0): Color(0.35, 0.62, 0.28),  # grass
	Vector2i(1, 0): Color(0.72, 0.58, 0.38),  # path
	Vector2i(2, 0): Color(0.22, 0.45, 0.78),   # water
	Vector2i(3, 0): Color(0.45, 0.45, 0.48),  # wall
	Vector2i(4, 0): Color(0.18, 0.42, 0.20),  # tree
}

static func create_tileset() -> TileSet:
	var tileset := TileSet.new()
	tileset.tile_shape = TileSet.TILE_SHAPE_ISOMETRIC
	tileset.tile_size = Vector2i(TILE_WIDTH, TILE_HEIGHT)
	tileset.tile_layout = TileSet.TILE_LAYOUT_STACKED

	var atlas := TileSetAtlasSource.new()
	atlas.texture = _build_texture()
	atlas.texture_region_size = Vector2i(TILE_WIDTH, TILE_HEIGHT)

	for x in range(TILE_COLORS.size()):
		atlas.create_tile(Vector2i(x, 0))

	tileset.add_source(atlas, 0)
	return tileset

static func _build_texture() -> ImageTexture:
	var tile_count := TILE_COLORS.size()
	var image := Image.create(TILE_WIDTH * tile_count, TILE_HEIGHT, false, Image.FORMAT_RGBA8)
	image.fill(Color(0, 0, 0, 0))

	for i in range(tile_count):
		var coords := Vector2i(i, 0)
		_draw_diamond(image, i * TILE_WIDTH, TILE_COLORS[coords])

	return ImageTexture.create_from_image(image)

static func _draw_diamond(image: Image, offset_x: int, fill: Color) -> void:
	var cx := TILE_WIDTH / 2.0
	var cy := TILE_HEIGHT / 2.0
	var hw := TILE_WIDTH / 2.0 - 1.0
	var hh := TILE_HEIGHT / 2.0 - 1.0

	for y in range(TILE_HEIGHT):
		for x in range(TILE_WIDTH):
			var dx: float = abs(x - cx) / hw
			var dy: float = abs(y - cy) / hh
			if dx + dy <= 1.0:
				var edge: bool = dx + dy > 0.93
				var pixel_color := fill.darkened(0.10) if edge else fill
				image.set_pixel(x + offset_x, y, pixel_color)
