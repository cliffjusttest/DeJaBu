extends Control

signal closed

const API_LIST := "http://localhost:8080/api/companions/list"
const API_ALLOCATE_STAT := "http://localhost:8080/api/character/allocate-stat"

const PARTY_POSITION_NAMES := ["前方", "左側", "右側", "左外側", "右外側"]

@onready var content: VBoxContainer = $Panel/Margin/VBox/Scroll/Content
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton
@onready var http: HTTPRequest = $HTTPRequest

var _busy := false
var _pending_stat_code := ""

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

func _allocate_stat(stat_code: String) -> void:
	if _busy or GameState.auth_token.is_empty() or GameState.stat_points <= 0:
		return
	_busy = true
	_pending_stat_code = stat_code
	_set_message("分配中...")
	var body := JSON.stringify({"token": GameState.auth_token, "stat": stat_code})
	var headers := ["Content-Type: application/json"]
	http.request(API_ALLOCATE_STAT, headers, HTTPClient.METHOD_POST, body)

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_busy = false
	if result != HTTPRequest.RESULT_SUCCESS:
		_set_message("連線失敗")
		_pending_stat_code = ""
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		_set_message("資料格式錯誤")
		_pending_stat_code = ""
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		_set_message("資料格式錯誤")
		_pending_stat_code = ""
		return

	if response_code != 200:
		_set_message(_extract_error_message(data, response_code))
		_pending_stat_code = ""
		_render()
		return

	if not _pending_stat_code.is_empty():
		_apply_allocation_response(data)
		_pending_stat_code = ""
		_set_message(str(data.get("message", "屬性已分配")))
		_render()
		return

	GameState.companions = data.get("companions", [])
	if data.has("skillPoints"):
		GameState.skill_points = int(data.get("skillPoints"))
	_set_message("")
	_render()

func _apply_allocation_response(data: Dictionary) -> void:
	GameState.stat_points = int(data.get("statPoints", GameState.stat_points))
	if data.has("stats"):
		GameState.player_stats = CharacterStatsData.from_payload(data.get("stats"))
	if data.has("playerMaxHp"):
		GameState.player_max_hp = int(data.get("playerMaxHp"))
	if data.has("playerMaxMp"):
		GameState.player_max_mp = int(data.get("playerMaxMp"))
	if data.has("playerCurrentHp"):
		GameState.player_current_hp = int(data.get("playerCurrentHp"))
	if data.has("playerCurrentMp"):
		GameState.player_current_mp = int(data.get("playerCurrentMp"))

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

	var mp_label := Label.new()
	mp_label.text = "MP  %d / %d" % [GameState.player_current_mp, GameState.player_max_mp]
	content.add_child(mp_label)

	var exp_label := Label.new()
	exp_label.text = "EXP  %d / %d    技能點  %d    屬性點  %d    金幣  %d" % [
		GameState.player_exp, GameState.exp_to_next_level, GameState.skill_points, GameState.stat_points, GameState.player_gold
	]
	content.add_child(exp_label)

	if GameState.stat_points > 0:
		var hint := Label.new()
		hint.text = "點擊 + 分配屬性點"
		content.add_child(hint)

	for code in CharacterStatsData.ALL:
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 8)

		var name_label := Label.new()
		name_label.text = CharacterStatsData.display_name(code)
		name_label.custom_minimum_size = Vector2(48, 0)
		row.add_child(name_label)

		var value_label := Label.new()
		value_label.text = str(int(GameState.player_stats.get(code, 0)))
		value_label.custom_minimum_size = Vector2(32, 0)
		value_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		row.add_child(value_label)

		if GameState.stat_points > 0:
			var plus_button := Button.new()
			plus_button.text = "+"
			plus_button.custom_minimum_size = Vector2(32, 32)
			plus_button.pressed.connect(_allocate_stat.bind(code))
			row.add_child(plus_button)

		content.add_child(row)

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

func _extract_error_message(data: Dictionary, response_code: int) -> String:
	if data.has("message"):
		return str(data.get("message"))
	if data.has("error"):
		return str(data.get("error"))
	return "操作失敗 (HTTP %d)" % response_code
