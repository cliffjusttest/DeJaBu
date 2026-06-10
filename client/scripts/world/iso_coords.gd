class_name IsoCoords
extends RefCounted

static func map_to_local(cell: Vector2i) -> Vector2:
	var half_w := IsoTileset.TILE_WIDTH * 0.5
	var half_h := IsoTileset.TILE_HEIGHT * 0.5
	return Vector2((cell.x - cell.y) * half_w, (cell.x + cell.y) * half_h)

static func local_to_map(local_pos: Vector2) -> Vector2i:
	var half_w := IsoTileset.TILE_WIDTH * 0.5
	var half_h := IsoTileset.TILE_HEIGHT * 0.5
	var cx := local_pos.x / half_w
	var cy := local_pos.y / half_h
	return Vector2i(
		int(floor((cx + cy) * 0.5)),
		int(floor((cy - cx) * 0.5))
	)

static func contains_point(local_pos: Vector2, cell: Vector2i) -> bool:
	var center := map_to_local(cell)
	var half_w := IsoTileset.TILE_WIDTH * 0.5 - 1.0
	var half_h := IsoTileset.TILE_HEIGHT * 0.5 - 1.0
	var dx := absf(local_pos.x - center.x) / half_w
	var dy := absf(local_pos.y - center.y) / half_h
	return dx + dy <= 1.0
