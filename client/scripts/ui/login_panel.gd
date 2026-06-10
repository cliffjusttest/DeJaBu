extends Control

signal authenticated(auth_data: Dictionary)
signal auth_failed(message: String)

const API_BASE := "http://localhost:8080/api/auth"
const USERNAME_PATTERN := "^[a-zA-Z0-9_]+$"

@onready var username_input: LineEdit = $Panel/Margin/VBox/UsernameInput
@onready var password_input: LineEdit = $Panel/Margin/VBox/PasswordInput
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var login_button: Button = $Panel/Margin/VBox/ButtonRow/LoginButton
@onready var register_button: Button = $Panel/Margin/VBox/ButtonRow/RegisterButton
@onready var http: HTTPRequest = $HTTPRequest

var _busy := false
var _username_regex: RegEx

func _ready() -> void:
	_username_regex = RegEx.new()
	_username_regex.compile(USERNAME_PATTERN)
	login_button.pressed.connect(_on_login_pressed)
	register_button.pressed.connect(_on_register_pressed)
	http.request_completed.connect(_on_request_completed)
	password_input.secret = true
	_set_message("請登入或註冊新帳號")

func _on_login_pressed() -> void:
	_submit("login")

func _on_register_pressed() -> void:
	_submit("register")

func _validate_inputs() -> String:
	var username := username_input.text.strip_edges()
	var password := password_input.text

	if username.is_empty() or password.is_empty():
		return "請輸入帳號與密碼"
	if username.length() < 3 or username.length() > 32:
		return "帳號長度需為 3 到 32 字"
	if _username_regex.search(username) == null:
		return "帳號只能包含英文字母、數字與底線"
	if password.length() < 6 or password.length() > 64:
		return "密碼至少需要 6 個字元"

	return ""

func _submit(action: String) -> void:
	if _busy:
		return

	var validation_error := _validate_inputs()
	if not validation_error.is_empty():
		_set_message(validation_error)
		return

	var username := username_input.text.strip_edges()
	var password := password_input.text
	var body := {
		"username": username,
		"password": password,
	}

	_busy = true
	_set_buttons_enabled(false)
	_set_message("處理中...")

	var json := JSON.stringify(body)
	var headers := ["Content-Type: application/json"]
	var err := http.request("%s/%s" % [API_BASE, action], headers, HTTPClient.METHOD_POST, json)
	if err != OK:
		_busy = false
		_set_buttons_enabled(true)
		auth_failed.emit("無法連線至登入 API")

func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_busy = false
	_set_buttons_enabled(true)

	if result != HTTPRequest.RESULT_SUCCESS:
		var msg := "登入 API 連線失敗，請確認伺服器已啟動"
		_set_message(msg)
		auth_failed.emit(msg)
		return

	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		var msg := "伺服器回應格式錯誤"
		_set_message(msg)
		auth_failed.emit(msg)
		return

	var data: Variant = json.data
	if typeof(data) != TYPE_DICTIONARY:
		var msg := "伺服器回應格式錯誤"
		_set_message(msg)
		auth_failed.emit(msg)
		return

	if response_code != 200:
		var msg := _extract_error_message(data, response_code)
		_set_message(msg)
		auth_failed.emit(msg)
		return

	_set_message(str(data.get("message", "登入成功")))
	authenticated.emit(data)

func _extract_error_message(data: Dictionary, response_code: int) -> String:
	if data.has("message"):
		return str(data.get("message"))
	if data.has("error"):
		return str(data.get("error"))
	return "登入失敗 (HTTP %d)" % response_code

func set_status(text: String) -> void:
	_set_message(text)
	show()

func _set_message(text: String) -> void:
	message_label.text = text

func _set_buttons_enabled(enabled: bool) -> void:
	login_button.disabled = not enabled
	register_button.disabled = not enabled
