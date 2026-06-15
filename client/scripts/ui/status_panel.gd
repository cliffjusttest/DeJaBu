extends Control

signal closed

const API_LIST := "http://localhost:8080/api/companions/list"

const PARTY_POSITION_NAMES := ["前方", "左側", "右側", "左外側", "右外側"]

@onready var content: VBoxContainer = $Panel/Margin/VBox/Scroll/Content
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton
@onready var http: HTTPRequest = $HTTPRequest

var _busy := false

func _ready() -> void:
	close_button.pressed.connect(_on_close_pressed)
	http.request_completed.connect(_on_request_completed)
	hide()

func open() -> void:
	show()
	_render()
	_fetch_companions()

func _fetch_companions() -> void:
	if _busy or GameState.auth_token.is_empty():
		return
	_busy = true
	_set_message("載入中...")
	var body := JSON.stringify({"token": GameState.auth_token})
	var headers := ["Content-Type: application/json"]
	http.request(API_LIST, headers, HTTPClient.METHOD_POST, body)

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_busy = false
	if result != HTTPRequest.RESULT_SUCCESS or response_code != 200:
		_set_message("夥伴資料載入失敗")
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		_set_message("資料格式錯誤")
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		_set_message("資料格式錯誤")
		return

	GameState.companions = data.get("companions", [])
	if data.has("skillPoints"):
		GameState.skill_points = int(data.get("skillPoints"))
	_set_message("")
	_render()

func _render() -> void:
	for child in content.get_children():
		child.queue_free()

	_render_player()

	var sep := HSeparator.new()
	content.add_child(sep)

	_render_companions()

func _render_player() -> void:
	var header := Label.new()
	header.text = "── 主角 ──"
	header.add_theme_font_size_override("font_size", 18)
	content.add_child(header)

	var element_name := ElementData.display_name(GameState.player_element) if not GameState.player_element.is_empty() else "無"

	var basic := Label.new()
	basic.text = "%s（%s）  Lv.%d" % [GameState.player_name, element_name, GameState.player_level]
	content.add_child(basic)

	var hp_label := Label.new()
	hp_label.text = "HP  %d / %d" % [GameState.player_current_hp, GameState.player_max_hp]
	content.add_child(hp_label)

	var exp_label := Label.new()
	exp_label.text = "EXP  %d / %d    技能點  %d    金幣  %d" % [
		GameState.player_exp, GameState.exp_to_next_level, GameState.skill_points, GameState.player_gold
	]
	content.add_child(exp_label)

	var stats := GameState.player_stats
	var stats_label := Label.new()
	stats_label.text = CharacterStatsData.summary_text(stats)
	content.add_child(stats_label)

func _render_companions() -> void:
	var header := Label.new()
	header.text = "── 夥伴 ──"
	header.add_theme_font_size_override("font_size", 18)
	content.add_child(header)

	var companions: Variant = GameState.companions
	if typeof(companions) != TYPE_ARRAY or companions.is_empty():
		var empty := Label.new()
		empty.text = "尚無夥伴"
		content.add_child(empty)
		return

	for entry in companions:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var companion: Dictionary = entry

		var sep := HSeparator.new()
		content.add_child(sep)

		var in_party := companion.get("partySlot") != null
		var party_text := "候補"
		if in_party:
			var slot_index := int(companion.get("partySlot"))
			if slot_index >= 0 and slot_index < PARTY_POSITION_NAMES.size():
				party_text = "出戰·%s" % PARTY_POSITION_NAMES[slot_index]
			else:
				party_text = "出戰"

		var name_label := Label.new()
		name_label.text = "%s  Lv.%d（%s）  %s" % [
			companion.get("nickname", ""),
			int(companion.get("level", 1)),
			companion.get("elementName", ""),
			party_text
		]
		content.add_child(name_label)

		var hp_label := Label.new()
		hp_label.text = "HP  %d / %d" % [
			int(companion.get("currentHp", 0)),
			int(companion.get("maxHp", 0))
		]
		content.add_child(hp_label)

		var stats_label := Label.new()
		stats_label.text = "武%d 智%d 體%d 防%d 精%d 幸%d 敏%d" % [
			int(companion.get("might", 0)),
			int(companion.get("intelligence", 0)),
			int(companion.get("vitality", 0)),
			int(companion.get("defense", 0)),
			int(companion.get("spirit", 0)),
			int(companion.get("luck", 0)),
			int(companion.get("agility", 0))
		]
		content.add_child(stats_label)

		var skills: Variant = companion.get("skills", [])
		if typeof(skills) == TYPE_ARRAY and not skills.is_empty():
			for skill_entry in skills:
				if typeof(skill_entry) != TYPE_DICTIONARY:
					continue
				var skill: Dictionary = skill_entry
				var skill_label := Label.new()
				skill_label.text = "  技能：%s Lv.%d/%d（%s）" % [
					skill.get("name", ""),
					int(skill.get("skillLevel", 1)),
					int(skill.get("maxLevel", 1)),
					skill.get("elementName", "")
				]
				content.add_child(skill_label)

func _on_close_pressed() -> void:
	hide()
	closed.emit()

func _set_message(text: String) -> void:
	message_label.text = text
