extends Control

signal closed

const API_SKILLS     := "http://localhost:8080/api/skills/tree"
const API_COMPANIONS := "http://localhost:8080/api/companions/list"
const API_BACKPACK   := "http://localhost:8080/api/backpack/status"

const PLAYER_BATTLE_SLOT    := 7
const PARTY_TO_BATTLE_SLOT  := [2, 1, 3, 0, 4]

@onready var actor_tabs_row: HBoxContainer = $Panel/Margin/VBox/ActorTabsRow
@onready var skill_list: VBoxContainer     = $Panel/Margin/VBox/ContentRow/SkillSection/SkillScroll/SkillList
@onready var item_list: VBoxContainer      = $Panel/Margin/VBox/ContentRow/ItemSection/ItemScroll/ItemList
@onready var message_label: Label          = $Panel/Margin/VBox/MessageLabel
@onready var close_button: Button          = $Panel/Margin/VBox/TitleRow/CloseButton
@onready var http_skills: HTTPRequest      = $HTTPSkills
@onready var http_companions: HTTPRequest  = $HTTPCompanions
@onready var http_backpack: HTTPRequest    = $HTTPBackpack

var _player_skills: Array        = []
var _companions_in_party: Array  = []
var _consumables: Array          = []
var _selected_actor_slot: int    = PLAYER_BATTLE_SLOT
var _selected_actor_skills: Array = []

func _ready() -> void:
	close_button.pressed.connect(_on_close_pressed)
	http_skills.request_completed.connect(_on_skills_loaded)
	http_companions.request_completed.connect(_on_companions_loaded)
	http_backpack.request_completed.connect(_on_backpack_loaded)
	hide()

func open() -> void:
	if GameState.auth_token.is_empty():
		return
	_set_message("載入中...")
	show()
	_post(http_skills, API_SKILLS)

func _post(http: HTTPRequest, url: String) -> void:
	var body := JSON.stringify({"token": GameState.auth_token})
	http.request(url, ["Content-Type: application/json"], HTTPClient.METHOD_POST, body)

func _on_skills_loaded(result: int, code: int, _h: PackedStringArray, body: PackedByteArray) -> void:
	if result == HTTPRequest.RESULT_SUCCESS and code == 200:
		var json := JSON.new()
		if json.parse(body.get_string_from_utf8()) == OK and typeof(json.data) == TYPE_DICTIONARY:
			var all: Array = json.data.get("skills", [])
			_player_skills = all.filter(func(s: Variant) -> bool: return bool(s.get("learned", false)))
	_post(http_companions, API_COMPANIONS)

func _on_companions_loaded(result: int, code: int, _h: PackedStringArray, body: PackedByteArray) -> void:
	_companions_in_party = []
	if result == HTTPRequest.RESULT_SUCCESS and code == 200:
		var json := JSON.new()
		if json.parse(body.get_string_from_utf8()) == OK and typeof(json.data) == TYPE_DICTIONARY:
			var all: Array = json.data.get("companions", [])
			_companions_in_party = all.filter(func(c: Variant) -> bool: return c.get("partySlot") != null)
	_post(http_backpack, API_BACKPACK)

func _on_backpack_loaded(result: int, code: int, _h: PackedStringArray, body: PackedByteArray) -> void:
	_consumables = []
	if result == HTTPRequest.RESULT_SUCCESS and code == 200:
		var json := JSON.new()
		if json.parse(body.get_string_from_utf8()) == OK and typeof(json.data) == TYPE_DICTIONARY:
			var inv: Array = json.data.get("inventory", [])
			_consumables = inv.filter(func(i: Variant) -> bool: return str(i.get("type", "")) == "CONSUMABLE")
	_set_message("")
	_selected_actor_slot = PLAYER_BATTLE_SLOT
	_selected_actor_skills = _player_skills
	_render_all()

func _render_all() -> void:
	_render_actor_tabs()
	_render_skill_section()
	_render_item_section()

func _render_actor_tabs() -> void:
	for c in actor_tabs_row.get_children():
		c.queue_free()
	_add_actor_tab("玩家", PLAYER_BATTLE_SLOT, _player_skills)
	for companion in _companions_in_party:
		var party_slot := int(companion.get("partySlot", -1))
		if party_slot < 0 or party_slot >= PARTY_TO_BATTLE_SLOT.size():
			continue
		var battle_slot: int = PARTY_TO_BATTLE_SLOT[party_slot]
		var cname := str(companion.get("nickname", companion.get("templateName", "夥伴")))
		var raw: Array = companion.get("skills", [])
		var skills: Array = raw.map(func(s: Variant) -> Dictionary:
			return {"id": int(s.get("skillId", 0)), "name": str(s.get("name", ""))}
		)
		_add_actor_tab(cname, battle_slot, skills)

func _add_actor_tab(label: String, battle_slot: int, skills: Array) -> void:
	var btn := Button.new()
	btn.text = label
	btn.disabled = (battle_slot == _selected_actor_slot)
	if not btn.disabled:
		btn.pressed.connect(func() -> void:
			_selected_actor_slot = battle_slot
			_selected_actor_skills = skills
			_render_actor_tabs()
			_render_skill_section()
		)
	actor_tabs_row.add_child(btn)

func _render_skill_section() -> void:
	for c in skill_list.get_children():
		c.queue_free()
	for i in range(12):
		var skill_id := BattleHotkeys.get_skill_hotkey(_selected_actor_slot, i)
		var sname := _skill_name(_selected_actor_skills, skill_id)
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 6)
		var assign_btn := Button.new()
		assign_btn.text = "F%d: %s" % [i + 1, sname if sname != "" else "未設定"]
		assign_btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		assign_btn.pressed.connect(_show_skill_popup.bind(i))
		row.add_child(assign_btn)
		var clear_btn := Button.new()
		clear_btn.text = "清除"
		clear_btn.disabled = (skill_id <= 0)
		var idx := i
		clear_btn.pressed.connect(func() -> void:
			BattleHotkeys.set_skill_hotkey(_selected_actor_slot, idx, -1)
			_render_skill_section()
		)
		row.add_child(clear_btn)
		skill_list.add_child(row)

func _show_skill_popup(hotkey_index: int) -> void:
	if _selected_actor_skills.is_empty():
		_set_message("此角色尚未習得任何技能")
		return
	var popup := PopupMenu.new()
	add_child(popup)
	for skill in _selected_actor_skills:
		popup.add_item(str(skill.get("name", "")), int(skill.get("id", 0)))
	popup.popup_hide.connect(popup.queue_free)
	popup.id_pressed.connect(func(id: int) -> void:
		BattleHotkeys.set_skill_hotkey(_selected_actor_slot, hotkey_index, id)
		_render_skill_section()
		popup.hide()
	)
	popup.popup_centered()

func _render_item_section() -> void:
	for c in item_list.get_children():
		c.queue_free()
	for i in range(12):
		var item_id := BattleHotkeys.get_item_hotkey(i)
		var entry := _find_consumable(item_id)
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 6)
		var assign_btn := Button.new()
		if entry.is_empty():
			assign_btn.text = "Shift+F%d: 未設定" % (i + 1)
		else:
			assign_btn.text = "Shift+F%d: %s ×%d" % [i + 1, str(entry.get("name", "")), int(entry.get("quantity", 0))]
		assign_btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		assign_btn.pressed.connect(_show_item_popup.bind(i))
		row.add_child(assign_btn)
		var clear_btn := Button.new()
		clear_btn.text = "清除"
		clear_btn.disabled = (item_id <= 0)
		var idx := i
		clear_btn.pressed.connect(func() -> void:
			BattleHotkeys.set_item_hotkey(idx, -1)
			_render_item_section()
		)
		row.add_child(clear_btn)
		item_list.add_child(row)

func _show_item_popup(hotkey_index: int) -> void:
	if _consumables.is_empty():
		_set_message("背包中沒有消耗道具")
		return
	var popup := PopupMenu.new()
	add_child(popup)
	for item in _consumables:
		popup.add_item("%s ×%d" % [str(item.get("name", "")), int(item.get("quantity", 0))], int(item.get("id", 0)))
	popup.popup_hide.connect(popup.queue_free)
	popup.id_pressed.connect(func(id: int) -> void:
		BattleHotkeys.set_item_hotkey(hotkey_index, id)
		_render_item_section()
		popup.hide()
	)
	popup.popup_centered()

func _skill_name(skills: Array, skill_id: int) -> String:
	if skill_id <= 0:
		return ""
	for s in skills:
		if int(s.get("id", 0)) == skill_id:
			return str(s.get("name", ""))
	return "（已失效）"

func _find_consumable(item_id: int) -> Dictionary:
	if item_id <= 0:
		return {}
	for item in _consumables:
		if int(item.get("id", 0)) == item_id:
			return item
	return {}

func _on_close_pressed() -> void:
	hide()
	closed.emit()

func _set_message(text: String) -> void:
	message_label.text = text
