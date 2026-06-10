class_name SkillTargetData
extends RefCounted

const ALLY := "ALLY"
const ENEMY := "ENEMY"
const ANY := "ANY"

const SINGLE := "SINGLE"
const ROW_ADJACENT_THREE := "ROW_ADJACENT_THREE"
const CROSS := "CROSS"
const ROW := "ROW"
const ALL := "ALL"

const SIDE_DISPLAY_NAMES := {
	ALLY: "我方",
	ENEMY: "敵方",
	ANY: "皆可",
}

const RANGE_DISPLAY_NAMES := {
	SINGLE: "一人",
	ROW_ADJACENT_THREE: "一行相鄰三人",
	CROSS: "十字",
	ROW: "一整行",
	ALL: "全部",
}

static func side_display_name(code: String) -> String:
	return SIDE_DISPLAY_NAMES.get(code, code)

static func range_display_name(code: String) -> String:
	return RANGE_DISPLAY_NAMES.get(code, code)

static func resolve_slots(anchor_slot: int, range_code: String) -> Array[int]:
	const SLOTS_PER_ROW := 5
	const MAX_SLOTS := 10

	if anchor_slot < 0 or anchor_slot >= MAX_SLOTS:
		return []

	match range_code:
		SINGLE:
			return [anchor_slot]
		ROW_ADJACENT_THREE:
			return _adjacent_three_in_row(anchor_slot, SLOTS_PER_ROW)
		CROSS:
			var slots := _adjacent_three_in_row(anchor_slot, SLOTS_PER_ROW)
			var row := anchor_slot / SLOTS_PER_ROW
			var col := anchor_slot % SLOTS_PER_ROW
			var other_row := 0 if row == 1 else 1
			var vertical_slot := other_row * SLOTS_PER_ROW + col
			if vertical_slot not in slots:
				slots.append(vertical_slot)
			slots.sort()
			return slots
		ROW:
			var row_index := anchor_slot / SLOTS_PER_ROW
			var result: Array[int] = []
			for col in range(SLOTS_PER_ROW):
				result.append(row_index * SLOTS_PER_ROW + col)
			return result
		ALL:
			var all_slots: Array[int] = []
			for slot in range(MAX_SLOTS):
				all_slots.append(slot)
			return all_slots
		_:
			return [anchor_slot]

static func _adjacent_three_in_row(anchor_slot: int, slots_per_row: int) -> Array[int]:
	var row_start := (anchor_slot / slots_per_row) * slots_per_row
	var col := anchor_slot % slots_per_row
	var result: Array[int] = []
	for offset in range(-1, 2):
		var target_col := col + offset
		if target_col < 0 or target_col >= slots_per_row:
			continue
		result.append(row_start + target_col)
	return result
