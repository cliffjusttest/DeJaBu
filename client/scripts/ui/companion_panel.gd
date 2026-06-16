extends Control

signal closed

const API_LIST := "http://localhost:8080/api/companions/list"
const API_PARTY := "http://localhost:8080/api/companions/party"
const API_SKILL_UPGRADE := "http://localhost:8080/api/companions/skills/upgrade"
const MAX_PARTY := 5
const PARTY_POSITION_NAMES := [
	"前方",
	"左側",
	"右側",
	"左外側",
	"右外側",
]

@onready var party_label: Label = $Panel/Margin/VBox/PartyLabel
@onready var list_container: VBoxContainer = $Panel/Margin/VBox/Scroll/ListContainer
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton
@onready var http: HTTPRequest = $HTTPRequest

var _busy := false
var _pending_companion_id := -1
var _pending_skill_id := -1
var _pending_url := ""

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
	_fetch_list()

func _fetch_list() -> void:
	_request(API_LIST, {"token": GameState.auth_token})

func _request(url: String, body: Dictionary) -> void:
	if _busy:
		return

	_busy = true
	_pending_url = url
	close_button.disabled = true
	_set_message("載入中...")

	var json := JSON.stringify(body)
	var headers := ["Content-Type: application/json"]
	var err := http.request(url, headers, HTTPClient.METHOD_POST, json)
	if err != OK:
		_busy = false
		_pending_url = ""
		close_button.disabled = false
		_set_message("無法連線至伺服器")

func _on_close_pressed() -> void:
	hide()
	closed.emit()

func _on_party_pressed(companion_id: int, active: bool) -> void:
	if _busy:
		return

	_pending_companion_id = companion_id
	_pending_skill_id = -1
	_request(API_PARTY, {
		"token": GameState.auth_token,
		"companionId": companion_id,
		"active": active
	})

func _on_skill_upgrade_pressed(companion_id: int, skill_id: int) -> void:
	if _busy:
		return

	_pending_companion_id = companion_id
	_pending_skill_id = skill_id
	_request(API_SKILL_UPGRADE, {
		"token": GameState.auth_token,
		"companionId": companion_id,
		"skillId": skill_id
	})

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_busy = false
	close_button.disabled = false
	var was_party_request := _pending_url == API_PARTY
	var was_skill_request := _pending_url == API_SKILL_UPGRADE
	_pending_url = ""

	if result != HTTPRequest.RESULT_SUCCESS:
		_set_message("伺服器連線失敗")
		_pending_companion_id = -1
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		_set_message("伺服器回應格式錯誤")
		_pending_companion_id = -1
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		_set_message("伺服器回應格式錯誤")
		_pending_companion_id = -1
		_pending_skill_id = -1
		return

	if response_code != 200:
		_set_message(_extract_error_message(data, response_code))
		_pending_companion_id = -1
		_pending_skill_id = -1
		return

	_apply_list_data(data)
	if (was_party_request or was_skill_request) and _pending_companion_id >= 0:
		_set_message(str(data.get("message", "更新完成")))
	else:
		_set_message(str(data.get("message", "")))
	_pending_companion_id = -1
	_pending_skill_id = -1

func _apply_list_data(data: Dictionary) -> void:
	GameState.companions = data.get("companions", [])
	if data.has("skillPoints"):
		GameState.skill_points = int(data.get("skillPoints"))
	_render_list(GameState.companions)

func _render_list(companions: Variant) -> void:
	for child in list_container.get_children():
		child.queue_free()

	var party_count := _count_party_members(companions)
	var max_party := GameState.party_max_companions if GameState.in_player_party else MAX_PARTY
	party_label.text = "出戰隊伍：%d / %d" % [party_count, max_party]

	if typeof(companions) != TYPE_ARRAY or companions.is_empty():
		var empty_label := Label.new()
		empty_label.text = "尚無夥伴，可在野外戰鬥或探索時捕捉怪物。"
		list_container.add_child(empty_label)
		return

	for entry in companions:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var companion: Dictionary = entry
		var row := HBoxContainer.new()
		row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_theme_constant_override("separation", 8)

		var party_text := "候補"
		var in_party := companion.get("partySlot") != null
		if in_party:
			var slot_index := int(companion.get("partySlot"))
			var position_name: String = "出戰"
			if slot_index >= 0 and slot_index < PARTY_POSITION_NAMES.size():
				position_name = PARTY_POSITION_NAMES[slot_index]
			party_text = "出戰·%s" % position_name

		var info := Label.new()
		info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		info.text = "%s Lv.%d（%s） HP %d/%d | 武%d 智%d 體%d 防%d 精%d 幸%d | %s" % [
			companion.get("nickname", ""),
			int(companion.get("level", 1)),
			companion.get("elementName", ""),
			int(companion.get("currentHp", 0)),
			int(companion.get("maxHp", 0)),
			int(companion.get("might", 0)),
			int(companion.get("intelligence", 0)),
			int(companion.get("vitality", 0)),
			int(companion.get("defense", 0)),
			int(companion.get("spirit", 0)),
			int(companion.get("luck", 0)),
			party_text
		]
		row.add_child(info)

		var action := Button.new()
		var companion_id := int(companion.get("id", 0))
		if in_party:
			action.text = "撤出"
			action.pressed.connect(_on_party_pressed.bind(companion_id, false))
		else:
			action.text = "出戰"
			action.disabled = party_count >= max_party
			action.pressed.connect(_on_party_pressed.bind(companion_id, true))
		row.add_child(action)

		list_container.add_child(row)
		_render_companion_skills(companion)

func _render_companion_skills(companion: Dictionary) -> void:
	var skills: Variant = companion.get("skills", [])
	if typeof(skills) != TYPE_ARRAY or skills.is_empty():
		return

	var companion_id := int(companion.get("id", 0))
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 4)

	for entry in skills:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var skill: Dictionary = entry
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 8)

		var label := Label.new()
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		label.text = "%s Lv.%d / %d（%s）" % [
			skill.get("name", ""),
			int(skill.get("skillLevel", 1)),
			int(skill.get("maxLevel", 1)),
			skill.get("elementName", "")
		]
		row.add_child(label)

		var button := Button.new()
		var skill_id := int(skill.get("skillId", 0))
		if bool(skill.get("canUpgrade", false)):
			button.text = "強化 (-1)"
			button.pressed.connect(_on_skill_upgrade_pressed.bind(companion_id, skill_id))
		else:
			button.text = "已滿"
			button.disabled = true
		row.add_child(button)
		box.add_child(row)

	list_container.add_child(box)

func _count_party_members(companions: Variant) -> int:
	if typeof(companions) != TYPE_ARRAY:
		return 0

	var count := 0
	for entry in companions:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		if entry.get("partySlot") != null:
			count += 1
	return count

func _extract_error_message(data: Dictionary, response_code: int) -> String:
	if data.has("message"):
		return str(data.get("message"))
	return "伺服器錯誤 (%d)" % response_code

func _set_message(text: String) -> void:
	message_label.text = text
