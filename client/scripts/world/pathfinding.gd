class_name Pathfinding
extends RefCounted

const NEIGHBORS := [
	Vector2i(0, -1),
	Vector2i(0, 1),
	Vector2i(-1, 0),
	Vector2i(1, 0),
]

static func find_path(world_map: WorldMap, from: Vector2i, to: Vector2i) -> Array[Vector2i]:
	if from == to:
		return []
	if not world_map.is_walkable(to.x, to.y):
		return []

	var open_set: Array[Vector2i] = [from]
	var came_from: Dictionary = {}
	var g_score: Dictionary = {from: 0}
	var f_score: Dictionary = {from: _heuristic(from, to)}

	while not open_set.is_empty():
		open_set.sort_custom(func(a: Vector2i, b: Vector2i) -> bool:
			return f_score.get(a, INF) < f_score.get(b, INF)
		)
		var current: Vector2i = open_set[0]
		open_set.remove_at(0)

		if current == to:
			return _reconstruct_path(came_from, current).slice(1)

		for offset in NEIGHBORS:
			var neighbor: Vector2i = current + offset
			if not world_map.is_walkable(neighbor.x, neighbor.y):
				continue

			var tentative_g: float = g_score.get(current, INF) + 1.0
			if tentative_g >= g_score.get(neighbor, INF):
				continue

			came_from[neighbor] = current
			g_score[neighbor] = tentative_g
			f_score[neighbor] = tentative_g + _heuristic(neighbor, to)
			if neighbor not in open_set:
				open_set.append(neighbor)

	return []

static func _heuristic(a: Vector2i, b: Vector2i) -> float:
	return absf(a.x - b.x) + absf(a.y - b.y)

static func _reconstruct_path(came_from: Dictionary, current: Vector2i) -> Array[Vector2i]:
	var path: Array[Vector2i] = [current]
	while came_from.has(current):
		current = came_from[current]
		path.insert(0, current)
	return path
