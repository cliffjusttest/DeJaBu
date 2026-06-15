extends Control

signal closed

const API_LIST := "http://localhost:8080/api/shop/list"
const API_BUY  := "http://localhost:8080/api/shop/buy"
const API_SELL := "http://localhost:8080/api/shop/sell"

const TAB_BUY := 0
const TAB_SELL := 1

@onready var title_label: Label = $Panel/Margin/VBox/TitleLabel
@onready var gold_label: Label = $Panel/Margin/VBox/GoldLabel
@onready var items_list: VBoxContainer = $Panel/Margin/VBox/Scroll/ItemsList
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton
@onready var tab_buy: Button = $Panel/Margin/VBox/TabBar/TabBuy
@onready var tab_sell: Button = $Panel/Margin/VBox/TabBar/TabSell
@onready var http: HTTPRequest = $HTTPRequest

var _busy := false
var _npc_id := ""
var _current_tab := TAB_BUY
var _buy_items: Array = []
var _sell_items: Array = []
var _pending_action := ""

func _ready() -> void:
	close_button.pressed.connect(_on_close_pressed)
	http.request_completed.connect(_on_request_completed)
	tab_buy.pressed.connect(_on_tab_pressed.bind(TAB_BUY))
	tab_sell.pressed.connect(_on_tab_pressed.bind(TAB_SELL))
	hide()

func open_for_npc(npc_id: String, npc_name: String = "") -> void:
	if GameState.auth_token.is_empty():
		_set_message("登入已失效")
		show()
		return
	_npc_id = npc_id
	_current_tab = TAB_BUY
	title_label.text = "%s 的商店" % (npc_name if not npc_name.is_empty() else "商人")
	show()
	_fetch_list()

func _fetch_list() -> void:
	_pending_action = "list"
	_request(API_LIST, {"token": GameState.auth_token, "npcId": _npc_id})

func _buy_item(item_id: int) -> void:
	_pending_action = "buy"
	_request(API_BUY, {"token": GameState.auth_token, "npcId": _npc_id, "itemId": item_id})

func _sell_item(item_id: int) -> void:
	_pending_action = "sell"
	_request(API_SELL, {"token": GameState.auth_token, "npcId": _npc_id, "itemId": item_id})

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

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	var action := _pending_action
	_pending_action = ""
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

	if data.has("playerGold"):
		GameState.player_gold = int(data.get("playerGold"))
		gold_label.text = "持有金幣：%d" % GameState.player_gold

	if action == "list":
		_buy_items = data.get("items", [])
		_sell_items = data.get("sellableItems", [])
		_set_message("")
	elif action == "buy" or action == "sell":
		_set_message(str(data.get("message", "")))
		_fetch_list()
		return

	_update_tab_buttons()
	_render_current_tab()

func _on_tab_pressed(tab: int) -> void:
	_current_tab = tab
	_update_tab_buttons()
	_render_current_tab()

func _update_tab_buttons() -> void:
	tab_buy.disabled = _current_tab == TAB_BUY
	tab_sell.disabled = _current_tab == TAB_SELL

func _render_current_tab() -> void:
	if _current_tab == TAB_SELL:
		_render_sell_items(_sell_items)
	else:
		_render_buy_items(_buy_items)

func _render_buy_items(items: Variant) -> void:
	for child in items_list.get_children():
		child.queue_free()

	if typeof(items) != TYPE_ARRAY or items.is_empty():
		var empty := Label.new()
		empty.text = "目前沒有商品"
		items_list.add_child(empty)
		return

	for entry in items:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var item: Dictionary = entry
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 8)

		var info := Label.new()
		info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		var type_text := "消耗品" if str(item.get("type", "")) == "CONSUMABLE" else "裝備"
		var slot_text := ""
		if item.has("slotDisplayName") and not str(item.get("slotDisplayName", "")).is_empty():
			slot_text = "（%s）" % str(item.get("slotDisplayName"))
		info.text = "%s %s%s\n購買價 %d 金幣\n%s" % [
			item.get("name", ""),
			type_text,
			slot_text,
			int(item.get("price", 0)),
			item.get("description", "")
		]
		row.add_child(info)

		var buy_btn := Button.new()
		buy_btn.text = "購買"
		var item_id := int(item.get("itemId", 0))
		buy_btn.pressed.connect(func(): _buy_item(item_id))
		row.add_child(buy_btn)

		items_list.add_child(row)

func _render_sell_items(items: Variant) -> void:
	for child in items_list.get_children():
		child.queue_free()

	if typeof(items) != TYPE_ARRAY or items.is_empty():
		var empty := Label.new()
		empty.text = "背包中沒有可賣出的道具"
		items_list.add_child(empty)
		return

	for entry in items:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var item: Dictionary = entry
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 8)

		var info := Label.new()
		info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		var type_text := "消耗品" if str(item.get("type", "")) == "CONSUMABLE" else "裝備"
		var qty := int(item.get("quantity", 1))
		var qty_text := " ×%d" % qty if qty > 1 else ""
		info.text = "%s %s%s\n賣出價 %d 金幣\n%s" % [
			item.get("name", ""),
			type_text,
			qty_text,
			int(item.get("sellPrice", 0)),
			item.get("description", "")
		]
		row.add_child(info)

		var sell_btn := Button.new()
		sell_btn.text = "賣出"
		var item_id := int(item.get("itemId", 0))
		sell_btn.pressed.connect(func(): _sell_item(item_id))
		row.add_child(sell_btn)

		items_list.add_child(row)

func _on_close_pressed() -> void:
	hide()
	closed.emit()

func _set_message(text: String) -> void:
	message_label.text = text
