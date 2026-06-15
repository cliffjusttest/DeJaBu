extends Control

signal closed
signal tree_updated

const API_TREE := "http://localhost:8080/api/skills/tree"
const API_LEARN := "http://localhost:8080/api/skills/learn"
const API_UPGRADE := "http://localhost:8080/api/skills/upgrade"

@onready var skill_points_label: Label = $Panel/Margin/VBox/InfoRow/SkillPointsLabel
@onready var player_level_label: Label = $Panel/Margin/VBox/InfoRow/PlayerLevelLabel
@onready var tree_container: HBoxContainer = $Panel/Margin/VBox/Scroll/TreeContainer
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton
@onready var http: HTTPRequest = $HTTPRequest

var _busy := false
var _skills: Array = []
var _pending_learn_id := -1
var _pending_upgrade_id := -1

func _ready() -> void:
	close_button.pressed.connect(_on_close_pressed)
	http.request_completed.connect(_on_request_completed)
	hide()

func open() -> void:
	if GameState.auth_token.is_empty():
		_set_message("登入已失效")
		show()
		return

	show()
	_fetch_tree()

func _fetch_tree() -> void:
	_request(API_TREE, {"token": GameState.auth_token})

func _request(url: String, body: Dictionary) -> void:
	if _busy:
		return

	_busy = true
	close_button.disabled = true
	_set_message("載入中...")

	var json := JSON.stringify(body)
	var headers := ["Content-Type: application/json"]
	var err := http.request(url, headers, HTTPClient.METHOD_POST, json)
	if err != OK:
		_busy = false
		close_button.disabled = false
		_set_message("無法連線至伺服器")

func _on_close_pressed() -> void:
	hide()
	closed.emit()

func _on_skill_pressed(skill_id: int) -> void:
	if _busy:
		return

	var skill := _find_skill(skill_id)
	if skill.is_empty():
		return
	if not bool(skill.get("canLearn", false)):
		_set_message(str(skill.get("statusText", "無法學習此技能")))
		return

	_pending_learn_id = skill_id
	_pending_upgrade_id = -1
	_request(API_LEARN, {"token": GameState.auth_token, "skillId": skill_id})

func _on_skill_upgrade_pressed(skill_id: int) -> void:
	if _busy:
		return

	var skill := _find_skill(skill_id)
	if skill.is_empty():
		return
	if not bool(skill.get("canUpgrade", false)):
		_set_message(str(skill.get("statusText", "無法強化此技能")))
		return

	_pending_learn_id = -1
	_pending_upgrade_id = skill_id
	_request(API_UPGRADE, {"token": GameState.auth_token, "skillId": skill_id})

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_busy = false
	close_button.disabled = false

	if result != HTTPRequest.RESULT_SUCCESS:
		_set_message("伺服器連線失敗")
		_pending_learn_id = -1
		_pending_upgrade_id = -1
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		_set_message("伺服器回應格式錯誤")
		_pending_learn_id = -1
		_pending_upgrade_id = -1
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		_set_message("伺服器回應格式錯誤")
		_pending_learn_id = -1
		_pending_upgrade_id = -1
		return

	if response_code != 200:
		_set_message(_extract_error_message(data, response_code))
		_pending_learn_id = -1
		_pending_upgrade_id = -1
		return

	_apply_tree_data(data)
	if _pending_learn_id >= 0 or _pending_upgrade_id >= 0:
		_set_message(str(data.get("message", "操作成功")))
	else:
		_set_message(str(data.get("message", "")))
	_pending_learn_id = -1
	_pending_upgrade_id = -1

func _apply_tree_data(data: Dictionary) -> void:
	GameState.skill_points = int(data.get("skillPoints", GameState.skill_points))
	GameState.player_level = int(data.get("playerLevel", GameState.player_level))
	_skills = data.get("skills", [])
	skill_points_label.text = "技能點：%d" % GameState.skill_points
	player_level_label.text = "等級：%d" % GameState.player_level
	_render_tree()
	tree_updated.emit()

func _render_tree() -> void:
	for child in tree_container.get_children():
		child.queue_free()

	if _skills.is_empty():
		var empty_label := Label.new()
		empty_label.text = "尚無技能資料"
		tree_container.add_child(empty_label)
		return

	var skills_by_id := {}
	for skill in _skills:
		skills_by_id[int(skill.get("id", 0))] = skill

	var depth_memo := {}
	var tiers := {}
	for skill in _skills:
		var depth := _skill_depth(skill, skills_by_id, depth_memo)
		if not tiers.has(depth):
			tiers[depth] = []
		tiers[depth].append(skill)

	var depths: Array = tiers.keys()
	depths.sort()

	for depth in depths:
		var column := VBoxContainer.new()
		column.add_theme_constant_override("separation", 10)
		column.custom_minimum_size = Vector2(180, 0)

		var title := Label.new()
		title.text = "第 %d 階" % (int(depth) + 1)
		title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		column.add_child(title)

		var tier_skills: Array = tiers[depth]
		tier_skills.sort_custom(func(a, b): return str(a.get("name", "")) < str(b.get("name", "")))

		for skill in tier_skills:
			column.add_child(_create_skill_card(skill))

		tree_container.add_child(column)

func _create_skill_card(skill: Dictionary) -> PanelContainer:
	var panel := PanelContainer.new()
	panel.custom_minimum_size = Vector2(190, 0)

	var margin := MarginContainer.new()
	margin.add_theme_constant_override("margin_left", 8)
	margin.add_theme_constant_override("margin_top", 8)
	margin.add_theme_constant_override("margin_right", 8)
	margin.add_theme_constant_override("margin_bottom", 8)
	panel.add_child(margin)

	var vbox := VBoxContainer.new()
	vbox.add_theme_constant_override("separation", 4)
	margin.add_child(vbox)

	var name_label := Label.new()
	name_label.text = str(skill.get("name", ""))
	name_label.add_theme_font_size_override("font_size", 16)
	vbox.add_child(name_label)

	var element_name := SkillElementData.display_name(str(skill.get("element", "")))
	var element_label := Label.new()
	element_label.text = "元素：%s" % element_name
	vbox.add_child(element_label)

	var coeff_label := Label.new()
	coeff_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	coeff_label.text = "武 %.2f / 智 %.2f" % [
		float(skill.get("mightCoefficient", 0)),
		float(skill.get("intelligenceCoefficient", 0)),
	]
	vbox.add_child(coeff_label)

	var mp_cost := int(skill.get("mpCost", skill.get("mp_cost", 0)))
	var mp_label := Label.new()
	mp_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	mp_label.text = "消耗 MP：%d" % mp_cost if mp_cost > 0 else "消耗 MP：無"
	vbox.add_child(mp_label)

	var req_label := Label.new()
	req_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	req_label.text = "需求等級 %d | CD %d 回合" % [
		int(skill.get("requiredLevel", 1)),
		int(skill.get("cooldownTurns", 0)),
	]
	vbox.add_child(req_label)

	var target_side := SkillTargetData.side_display_name(str(skill.get("targetSide", "")))
	var target_range := SkillTargetData.range_display_name(str(skill.get("targetRange", "")))
	var target_label := Label.new()
	target_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	target_label.text = "目標：%s | 範圍：%s" % [target_side, target_range]
	vbox.add_child(target_label)

	var status_label := Label.new()
	var status_text := str(skill.get("statusText", ""))
	if bool(skill.get("learned", false)):
		status_text = "已學習 Lv.%d" % int(skill.get("skillLevel", 1))
	status_label.text = status_text
	vbox.add_child(status_label)

	var button := Button.new()
	var skill_id := int(skill.get("id", 0))
	var can_learn := bool(skill.get("canLearn", false))
	var can_upgrade := bool(skill.get("canUpgrade", false))
	var learned := bool(skill.get("learned", false))

	if learned and can_upgrade:
		button.text = "強化 (-1)"
		button.pressed.connect(_on_skill_upgrade_pressed.bind(skill_id))
	elif learned:
		button.text = "已掌握"
		button.disabled = true
	elif can_learn:
		button.text = "學習 (-1)"
		button.pressed.connect(_on_skill_pressed.bind(skill_id))
	else:
		button.text = "未解鎖"
		button.disabled = true

	vbox.add_child(button)
	return panel

func _skill_depth(skill: Dictionary, skills_by_id: Dictionary, memo: Dictionary) -> int:
	var skill_id := int(skill.get("id", 0))
	if memo.has(skill_id):
		return int(memo[skill_id])

	var prereqs: Array = skill.get("prerequisiteIds", [])
	if prereqs.is_empty():
		memo[skill_id] = 0
		return 0

	var max_depth := 0
	for prereq_id in prereqs:
		var parent = skills_by_id.get(int(prereq_id))
		if parent:
			max_depth = max(max_depth, _skill_depth(parent, skills_by_id, memo) + 1)

	memo[skill_id] = max_depth
	return max_depth

func _find_skill(skill_id: int) -> Dictionary:
	for skill in _skills:
		if int(skill.get("id", 0)) == skill_id:
			return skill
	return {}

func _extract_error_message(data: Dictionary, response_code: int) -> String:
	if data.has("message"):
		return str(data.get("message"))
	if data.has("error"):
		return str(data.get("error"))
	return "操作失敗 (HTTP %d)" % response_code

func _set_message(text: String) -> void:
	message_label.text = text
