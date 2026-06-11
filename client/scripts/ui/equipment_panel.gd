extends Control

signal closed

const API_STATUS := "http://localhost:8080/api/equipment/status"
const API_EQUIP  := "http://localhost:8080/api/equipment/equip"
const API_UNEQUIP := "http://localhost:8080/api/equipment/unequip"

const SLOT_DISPLAY_NAMES := {
	"HEAD":      "頭",
	"FACE":      "臉",
	"SHOULDER":  "肩",
	"HAND":      "手",
	"BODY":      "身",
	"LEG":       "腿",
	"FOOT":      "腳",
	"BACK":      "背部",
	"ACCESSORY": "飾品",
}

const SLOT_ORDER := ["HEAD", "FACE", "SHOULDER", "HAND", "BODY", "LEG", "FOOT", "BACK", "ACCESSORY"]

@onready var equipped_container: VBoxContainer = $Panel/Margin/VBox/Content/EquippedScroll/EquippedList
@onready var items_container: VBoxContainer    = $Panel/Margin/VBox/Content/ItemsScroll/ItemsList
@onready var message_label: Label              = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button              = $Panel/Margin/VBox/CloseButton
@onready var http: HTTPRequest                 = $HTTPRequest

var _busy := false
var _pending_url := ""
var _equipped: Dictionary = {}
var _available: Array = []

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
	_fetch_status()

func _fetch_status() -> void:
	_request(API_STATUS, {"token": GameState.auth_token})

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

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_busy = false
	_pending_url = ""
	close_button.disabled = false

	if result != HTTPRequest.RESULT_SUCCESS:
		_set_message("伺服器連線失敗")
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		_set_message("伺服器回應格式錯誤")
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		_set_message("伺服器回應格式錯誤")
		return

	if response_code != 200:
		var msg := str(data.get("message", data.get("error", "操作失敗 (HTTP %d)" % response_code)))
		_set_message(msg)
		return

	_equipped = data.get("equipped", {})
	_available = data.get("availableItems", [])
	var server_msg := str(data.get("message", ""))
	_set_message(server_msg if not server_msg.is_empty() else "裝備管理")
	_render()

func _render() -> void:
	_render_equipped()
	_render_available()

func _render_equipped() -> void:
	for child in equipped_container.get_children():
		child.queue_free()

	for slot_key in SLOT_ORDER:
		var slot_name: String = SLOT_DISPLAY_NAMES.get(slot_key, slot_key)
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 8)

		var slot_label := Label.new()
		slot_label.text = "【%s】" % slot_name
		slot_label.custom_minimum_size = Vector2(60, 0)
		row.add_child(slot_label)

		if _equipped.has(slot_key):
			var item: Dictionary = _equipped[slot_key]
			var item_label := Label.new()
			item_label.text = str(item.get("name", ""))
			item_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(item_label)

			var stats_label := Label.new()
			stats_label.text = _format_bonuses(item)
			stats_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(stats_label)

			var unequip_btn := Button.new()
			unequip_btn.text = "卸下"
			unequip_btn.pressed.connect(_on_unequip_pressed.bind(slot_key))
			row.add_child(unequip_btn)
		else:
			var empty_label := Label.new()
			empty_label.text = "（空）"
			empty_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(empty_label)

		equipped_container.add_child(row)

func _render_available() -> void:
	for child in items_container.get_children():
		child.queue_free()

	if _available.is_empty():
		var label := Label.new()
		label.text = "目前無可用裝備"
		items_container.add_child(label)
		return

	var by_slot: Dictionary = {}
	for item in _available:
		var s := str(item.get("slot", ""))
		if not by_slot.has(s):
			by_slot[s] = []
		by_slot[s].append(item)

	for slot_key in SLOT_ORDER:
		if not by_slot.has(slot_key):
			continue

		var slot_name: String = SLOT_DISPLAY_NAMES.get(slot_key, slot_key)
		var header := Label.new()
		header.text = "── %s ──" % slot_name
		header.add_theme_font_size_override("font_size", 15)
		items_container.add_child(header)

		for item in by_slot[slot_key]:
			items_container.add_child(_create_item_card(item))

func _create_item_card(item: Dictionary) -> PanelContainer:
	var panel := PanelContainer.new()

	var margin := MarginContainer.new()
	for side in ["margin_left", "margin_top", "margin_right", "margin_bottom"]:
		margin.add_theme_constant_override(side, 6)
	panel.add_child(margin)

	var hbox := HBoxContainer.new()
	hbox.add_theme_constant_override("separation", 8)
	margin.add_child(hbox)

	var vbox := VBoxContainer.new()
	vbox.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hbox.add_child(vbox)

	var name_label := Label.new()
	name_label.text = str(item.get("name", ""))
	name_label.add_theme_font_size_override("font_size", 15)
	vbox.add_child(name_label)

	var desc := str(item.get("description", ""))
	if not desc.is_empty():
		var desc_label := Label.new()
		desc_label.text = desc
		desc_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		vbox.add_child(desc_label)

	var bonus_label := Label.new()
	bonus_label.text = _format_bonuses(item)
	vbox.add_child(bonus_label)

	var req_label := Label.new()
	req_label.text = "需求等級：%d" % int(item.get("requiredLevel", 1))
	vbox.add_child(req_label)

	var equip_btn := Button.new()
	equip_btn.text = "裝備"
	var item_id := int(item.get("id", 0))
	equip_btn.pressed.connect(_on_equip_pressed.bind(item_id))
	hbox.add_child(equip_btn)

	return panel

func _on_equip_pressed(item_id: int) -> void:
	_request(API_EQUIP, {"token": GameState.auth_token, "itemId": item_id})

func _on_unequip_pressed(slot_key: String) -> void:
	_request(API_UNEQUIP, {"token": GameState.auth_token, "slot": slot_key})

func _format_bonuses(item: Dictionary) -> String:
	var parts: Array = []
	var fields := [
		["bonusMight", "武"],
		["bonusIntelligence", "智"],
		["bonusVitality", "體"],
		["bonusDefense", "防"],
		["bonusSpirit", "靈"],
		["bonusLuck", "幸"],
		["bonusAgility", "敏"],
	]
	for pair in fields:
		var val := int(item.get(pair[0], 0))
		if val != 0:
			parts.append("%s%+d" % [pair[1], val])
	return " ".join(parts) if not parts.is_empty() else "無加成"

func _set_message(text: String) -> void:
	message_label.text = text
