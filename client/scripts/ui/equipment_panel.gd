extends Control

signal closed

const API_STATUS  := "http://localhost:8080/api/backpack/status"
const API_EQUIP   := "http://localhost:8080/api/backpack/equip"
const API_UNEQUIP := "http://localhost:8080/api/backpack/unequip"

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

const TAB_EQUIP := 0
const TAB_ITEMS := 1

@onready var unit_list_container: VBoxContainer  = $Panel/Margin/VBox/Content/UnitListSection/UnitListScroll/UnitList
@onready var equipped_container: VBoxContainer   = $Panel/Margin/VBox/Content/EquippedSection/EquippedScroll/EquippedList
@onready var inventory_container: VBoxContainer  = $Panel/Margin/VBox/Content/InventorySection/InventoryScroll/InventoryList
@onready var equipped_title: Label               = $Panel/Margin/VBox/Content/EquippedSection/SectionTitle
@onready var content_area: HBoxContainer         = $Panel/Margin/VBox/Content
@onready var items_placeholder: Label            = $Panel/Margin/VBox/ItemsPlaceholder
@onready var message_label: Label                = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button                = $Panel/Margin/VBox/CloseButton
@onready var tab_equip: Button                   = $Panel/Margin/VBox/TabBar/TabEquip
@onready var tab_items: Button                   = $Panel/Margin/VBox/TabBar/TabItems
@onready var http: HTTPRequest                   = $HTTPRequest

var _busy := false
var _current_tab := TAB_EQUIP

# Server data
var _inventory: Array = []
var _player_equipped: Dictionary = {}
var _companions: Array = []

# Selection: player = id -1, companion = companion's companionId
var _selected_id: int = -1

func _ready() -> void:
	close_button.pressed.connect(_on_close_pressed)
	http.request_completed.connect(_on_request_completed)
	tab_equip.pressed.connect(_on_tab_pressed.bind(TAB_EQUIP))
	tab_items.pressed.connect(_on_tab_pressed.bind(TAB_ITEMS))
	hide()

func open() -> void:
	if GameState.auth_token.is_empty():
		_set_message("登入已失效")
		show()
		return
	_selected_id = -1  # default to player
	show()
	_fetch_status()

func _fetch_status() -> void:
	_request(API_STATUS, {"token": GameState.auth_token})

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
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK or typeof(json.data) != TYPE_DICTIONARY:
		_set_message("伺服器回應格式錯誤")
		return

	var data: Dictionary = json.data

	if response_code != 200:
		_set_message(str(data.get("message", data.get("error", "操作失敗 (HTTP %d)" % response_code))))
		return

	_inventory       = data.get("inventory", [])
	_player_equipped = data.get("playerEquipped", {})
	_companions      = data.get("companions", [])

	var server_msg := str(data.get("message", ""))
	_set_message(server_msg if not server_msg.is_empty() else "背包")

	_render()

func _on_tab_pressed(tab: int) -> void:
	_current_tab = tab
	_render()

func _render() -> void:
	_update_tab_buttons()
	if _current_tab == TAB_ITEMS:
		content_area.hide()
		items_placeholder.show()
		return

	content_area.show()
	items_placeholder.hide()
	_render_unit_list()

	var is_player := _selected_id == -1
	var equipped  := _player_equipped if is_player else _get_companion_equipped(_selected_id)
	var unit_name := _get_unit_display_name(_selected_id)

	equipped_title.text = "已裝備 · %s" % unit_name
	_render_equipped(equipped)
	_render_inventory(is_player)

func _update_tab_buttons() -> void:
	tab_equip.modulate = Color.WHITE if _current_tab == TAB_EQUIP else Color(0.65, 0.65, 0.65)
	tab_items.modulate = Color.WHITE if _current_tab == TAB_ITEMS else Color(0.65, 0.65, 0.65)

func _render_unit_list() -> void:
	for child in unit_list_container.get_children():
		child.queue_free()

	# Player entry
	_add_unit_button(-1,
		GameState.player_name if GameState.player_name != "" else "角色",
		GameState.player_level)

	# Companion entries
	for entry in _companions:
		var cid  := int(entry.get("companionId", -1))
		var nick := str(entry.get("nickname", "夥伴"))
		var lv   := int(entry.get("level", 1))
		_add_unit_button(cid, nick, lv)

func _add_unit_button(unit_id: int, display_name: String, level: int) -> void:
	var btn := Button.new()
	btn.text = "%s  Lv.%d" % [display_name, level]
	btn.alignment = HORIZONTAL_ALIGNMENT_LEFT
	btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL

	if unit_id == _selected_id:
		btn.modulate = Color(1.0, 0.85, 0.25)
	else:
		btn.modulate = Color.WHITE

	btn.pressed.connect(func():
		_selected_id = unit_id
		_render()
	)
	unit_list_container.add_child(btn)

func _get_unit_display_name(unit_id: int) -> String:
	if unit_id == -1:
		return GameState.player_name if GameState.player_name != "" else "角色"
	for entry in _companions:
		if int(entry.get("companionId", -1)) == unit_id:
			return str(entry.get("nickname", "夥伴"))
	return "夥伴"

func _get_companion_equipped(companion_id: int) -> Dictionary:
	for entry in _companions:
		if int(entry.get("companionId", -1)) == companion_id:
			return entry.get("equipped", {})
	return {}

func _render_equipped(equipped: Dictionary) -> void:
	for child in equipped_container.get_children():
		child.queue_free()

	for slot_key in SLOT_ORDER:
		var slot_name: String = SLOT_DISPLAY_NAMES.get(slot_key, slot_key)
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 6)

		var slot_label := Label.new()
		slot_label.text = "【%s】" % slot_name
		slot_label.custom_minimum_size = Vector2(52, 0)
		row.add_child(slot_label)

		if equipped.has(slot_key):
			var item: Dictionary = equipped[slot_key]
			var item_label := Label.new()
			item_label.text = str(item.get("name", ""))
			item_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(item_label)

			var bonus_label := Label.new()
			bonus_label.text = _format_bonuses(item)
			bonus_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(bonus_label)

			var unequip_btn := Button.new()
			unequip_btn.text = "卸下"
			if _selected_id == -1:
				unequip_btn.pressed.connect(_on_unequip_pressed.bind(slot_key, -1))
			else:
				unequip_btn.pressed.connect(_on_unequip_pressed.bind(slot_key, _selected_id))
			row.add_child(unequip_btn)
		else:
			var empty := Label.new()
			empty.text = "（空）"
			empty.modulate = Color(0.6, 0.6, 0.6)
			empty.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(empty)

		equipped_container.add_child(row)

func _render_inventory(is_player: bool) -> void:
	for child in inventory_container.get_children():
		child.queue_free()

	var equipment_items: Array = _inventory.filter(
		func(i): return str(i.get("type", "EQUIPMENT")) == "EQUIPMENT"
	)

	if equipment_items.is_empty():
		var label := Label.new()
		label.text = "背包中沒有裝備"
		label.modulate = Color(0.6, 0.6, 0.6)
		inventory_container.add_child(label)
		return

	var by_slot: Dictionary = {}
	for item in equipment_items:
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
		header.add_theme_font_size_override("font_size", 12)
		header.modulate = Color(0.8, 0.8, 0.8)
		inventory_container.add_child(header)

		for item in by_slot[slot_key]:
			inventory_container.add_child(_create_item_card(item))

func _create_item_card(item: Dictionary) -> PanelContainer:
	var panel := PanelContainer.new()

	var margin := MarginContainer.new()
	for side in ["margin_left", "margin_top", "margin_right", "margin_bottom"]:
		margin.add_theme_constant_override(side, 5)
	panel.add_child(margin)

	var hbox := HBoxContainer.new()
	hbox.add_theme_constant_override("separation", 6)
	margin.add_child(hbox)

	var vbox := VBoxContainer.new()
	vbox.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hbox.add_child(vbox)

	var name_label := Label.new()
	name_label.text = str(item.get("name", ""))
	name_label.add_theme_font_size_override("font_size", 13)
	vbox.add_child(name_label)

	var bonus_label := Label.new()
	bonus_label.text = _format_bonuses(item)
	vbox.add_child(bonus_label)

	var req := int(item.get("requiredLevel", 1))
	var qty := int(item.get("quantity", 1))
	var meta_label := Label.new()
	meta_label.text = "需求 Lv.%d%s" % [req, ("  ×%d" % qty) if qty > 1 else ""]
	meta_label.modulate = Color(0.75, 0.75, 0.75)
	vbox.add_child(meta_label)

	var equip_btn := Button.new()
	equip_btn.text = "裝備"
	var item_id := int(item.get("id", 0))
	var cid     := _selected_id
	equip_btn.pressed.connect(_on_equip_pressed.bind(item_id, cid))
	hbox.add_child(equip_btn)

	return panel

func _on_equip_pressed(item_id: int, companion_id: int) -> void:
	var body := {"token": GameState.auth_token, "itemId": item_id}
	if companion_id != -1:
		body["companionId"] = companion_id
	_request(API_EQUIP, body)

func _on_unequip_pressed(slot_key: String, companion_id: int) -> void:
	var body := {"token": GameState.auth_token, "slot": slot_key}
	if companion_id != -1:
		body["companionId"] = companion_id
	_request(API_UNEQUIP, body)

func _format_bonuses(item: Dictionary) -> String:
	var parts: Array = []
	var fields := [
		["bonusMight", "武"], ["bonusIntelligence", "智"], ["bonusVitality", "體"],
		["bonusDefense", "防"], ["bonusSpirit", "靈"], ["bonusLuck", "幸"], ["bonusAgility", "敏"],
	]
	for pair in fields:
		var val := int(item.get(pair[0], 0))
		if val != 0:
			parts.append("%s%+d" % [pair[1], val])
	return " ".join(parts) if not parts.is_empty() else "無加成"

func _set_message(text: String) -> void:
	message_label.text = text
