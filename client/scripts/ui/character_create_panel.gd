extends Control

signal character_created(auth_data: Dictionary)
signal create_failed(message: String)

const API_CREATE := "http://localhost:8080/api/character/create"
const PlayerSpriteFactoryScript = preload("res://scripts/world/player_sprite_factory.gd")

@onready var display_name_input: LineEdit = $Panel/Margin/VBox/NameRow/DisplayNameInput
@onready var preview_sprite: TextureRect = $Panel/Margin/VBox/PreviewRow/PreviewSprite
@onready var element_container: HBoxContainer = $Panel/Margin/VBox/ElementRow/ElementButtons
@onready var points_remaining_label: Label = $Panel/Margin/VBox/PointsRemainingLabel
@onready var stats_container: VBoxContainer = $Panel/Margin/VBox/StatsContainer
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var create_button: Button = $Panel/Margin/VBox/CreateButton
@onready var prev_appearance_button: Button = $Panel/Margin/VBox/PreviewRow/PrevAppearanceButton
@onready var next_appearance_button: Button = $Panel/Margin/VBox/PreviewRow/NextAppearanceButton
@onready var http: HTTPRequest = $HTTPRequest

var _busy := false
var _auth_token := ""
var _appearance_index := 0
var _selected_element := ElementData.FIRE
var _element_buttons: Array[Button] = []
var _allocated_stats: Dictionary = CharacterStatsData.zero_base()
var _stat_value_labels: Dictionary = {}

func _ready() -> void:
	preview_sprite.texture = PlayerSpriteFactoryScript.preview_texture()
	create_button.pressed.connect(_on_create_pressed)
	prev_appearance_button.pressed.connect(_on_prev_appearance_pressed)
	next_appearance_button.pressed.connect(_on_next_appearance_pressed)
	http.request_completed.connect(_on_request_completed)
	_setup_element_buttons()
	_setup_stat_rows()
	_reset_stats()
	_update_appearance_preview()
	_set_message("設定角色名稱、外型、元素與能力配點")

func begin(auth_token: String) -> void:
	_auth_token = auth_token
	display_name_input.text = ""
	_appearance_index = 0
	_selected_element = ElementData.FIRE
	_reset_stats()
	_update_element_selection()
	_update_appearance_preview()
	_set_message("設定角色名稱、外型、元素與能力配點")
	show()

func _setup_element_buttons() -> void:
	for child in element_container.get_children():
		child.queue_free()
	_element_buttons.clear()

	for option in ElementData.selectable_options():
		var button := Button.new()
		button.text = option["label"]
		button.toggle_mode = true
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.pressed.connect(_on_element_button_pressed.bind(str(option["code"])))
		element_container.add_child(button)
		_element_buttons.append(button)

	_update_element_selection()

func _setup_stat_rows() -> void:
	for child in stats_container.get_children():
		child.queue_free()
	_stat_value_labels.clear()

	for code in CharacterStatsData.ALL:
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 8)

		var name_label := Label.new()
		name_label.text = CharacterStatsData.display_name(code)
		name_label.custom_minimum_size = Vector2(48, 0)
		row.add_child(name_label)

		var minus_button := Button.new()
		minus_button.text = "-"
		minus_button.custom_minimum_size = Vector2(32, 32)
		minus_button.pressed.connect(_on_stat_minus_pressed.bind(code))
		row.add_child(minus_button)

		var value_label := Label.new()
		value_label.text = "0"
		value_label.custom_minimum_size = Vector2(24, 0)
		value_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		row.add_child(value_label)
		_stat_value_labels[code] = value_label

		var plus_button := Button.new()
		plus_button.text = "+"
		plus_button.custom_minimum_size = Vector2(32, 32)
		plus_button.pressed.connect(_on_stat_plus_pressed.bind(code))
		row.add_child(plus_button)

		stats_container.add_child(row)

func _reset_stats() -> void:
	_allocated_stats = CharacterStatsData.zero_base()
	_update_stats_display()

func _on_element_button_pressed(code: String) -> void:
	_selected_element = code
	_update_element_selection()

func _update_element_selection() -> void:
	for button in _element_buttons:
		var index := button.get_index()
		var option := ElementData.selectable_options()[index]
		button.button_pressed = str(option["code"]) == _selected_element

func _on_prev_appearance_pressed() -> void:
	_appearance_index = (_appearance_index - 1 + CharacterAppearanceData.OPTIONS.size()) % CharacterAppearanceData.OPTIONS.size()
	_update_appearance_preview()

func _on_next_appearance_pressed() -> void:
	_appearance_index = (_appearance_index + 1) % CharacterAppearanceData.OPTIONS.size()
	_update_appearance_preview()

func _update_appearance_preview() -> void:
	var code := CharacterAppearanceData.OPTIONS[_appearance_index]
	preview_sprite.modulate = CharacterAppearanceData.tint_for(code)

func _on_stat_plus_pressed(code: String) -> void:
	if CharacterStatsData.remaining_points(_allocated_stats) <= 0:
		return
	_allocated_stats[code] = int(_allocated_stats.get(code, 0)) + 1
	_update_stats_display()

func _on_stat_minus_pressed(code: String) -> void:
	if int(_allocated_stats.get(code, 0)) <= 0:
		return
	_allocated_stats[code] = int(_allocated_stats.get(code, 0)) - 1
	_update_stats_display()

func _update_stats_display() -> void:
	for code in CharacterStatsData.ALL:
		var value_label: Label = _stat_value_labels[code]
		value_label.text = str(int(_allocated_stats.get(code, 0)))

	var remaining := CharacterStatsData.remaining_points(_allocated_stats)
	points_remaining_label.text = "剩餘點數：%d" % remaining

func _on_create_pressed() -> void:
	if _busy:
		return

	var display_name := display_name_input.text.strip_edges()
	if display_name.is_empty():
		_set_message("請輸入角色名稱")
		return
	if display_name.length() > 32:
		_set_message("角色名稱長度不可超過 32 字")
		return
	if _auth_token.is_empty():
		_set_message("登入已失效，請重新登入")
		create_failed.emit("登入已失效，請重新登入")
		return

	var remaining := CharacterStatsData.remaining_points(_allocated_stats)
	if remaining > 0:
		_set_message("還有 %d 點能力點數未分配" % remaining)
		return

	var appearance_code := CharacterAppearanceData.OPTIONS[_appearance_index]
	var body := {
		"token": _auth_token,
		"displayName": display_name,
		"element": _selected_element,
		"appearance": appearance_code,
		"stats": _allocated_stats,
	}

	_busy = true
	create_button.disabled = true
	_set_message("創建角色中...")

	var json := JSON.stringify(body)
	var headers := ["Content-Type: application/json"]
	var err := http.request(API_CREATE, headers, HTTPClient.METHOD_POST, json)
	if err != OK:
		_busy = false
		create_button.disabled = false
		var msg := "無法連線至伺服器"
		_set_message(msg)
		create_failed.emit(msg)

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_busy = false
	create_button.disabled = false

	if result != HTTPRequest.RESULT_SUCCESS:
		var msg := "伺服器連線失敗，請確認伺服器已啟動"
		_set_message(msg)
		create_failed.emit(msg)
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		var msg := "伺服器回應格式錯誤"
		_set_message(msg)
		create_failed.emit(msg)
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		var msg := "伺服器回應格式錯誤"
		_set_message(msg)
		create_failed.emit(msg)
		return

	if response_code != 200:
		var msg := _extract_error_message(data, response_code)
		_set_message(msg)
		create_failed.emit(msg)
		return

	_set_message(str(data.get("message", "角色創建成功")))
	character_created.emit(data)

func _extract_error_message(data: Dictionary, response_code: int) -> String:
	if data.has("message"):
		return str(data.get("message"))
	if data.has("error"):
		return str(data.get("error"))
	return "創建角色失敗 (HTTP %d)" % response_code

func _set_message(text: String) -> void:
	message_label.text = text
