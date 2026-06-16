extends Control

signal closed

@onready var status_label: Label = $Panel/Margin/VBox/StatusLabel
@onready var invite_prompt: HBoxContainer = $Panel/Margin/VBox/InvitePromptHBox
@onready var accept_invite_button: Button = $Panel/Margin/VBox/InvitePromptHBox/AcceptInviteButton
@onready var decline_invite_button: Button = $Panel/Margin/VBox/InvitePromptHBox/DeclineInviteButton
@onready var invite_list: VBoxContainer = $Panel/Margin/VBox/InviteScroll/InviteList
@onready var member_list: VBoxContainer = $Panel/Margin/VBox/MemberScroll/MemberList
@onready var message_label: Label = $Panel/Margin/VBox/MessageLabel
@onready var leave_button: Button = $Panel/Margin/VBox/LeaveButton
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton

func _ready() -> void:
	close_button.pressed.connect(_on_close_pressed)
	leave_button.pressed.connect(_on_leave_pressed)
	accept_invite_button.pressed.connect(_on_accept_invite_pressed)
	decline_invite_button.pressed.connect(_on_decline_invite_pressed)
	hide()

func open() -> void:
	show()
	refresh()

func refresh() -> void:
	_populate_members()
	_populate_invites()
	_update_status()

func show_message(text: String) -> void:
	message_label.text = text

func _update_status() -> void:
	if GameState.pending_party_invite_from > 0:
		invite_prompt.show()
		accept_invite_button.text = "接受 %s 的邀請" % GameState.pending_party_invite_name
	else:
		invite_prompt.hide()

	if GameState.in_player_party:
		var count := GameState.party_members.size()
		var leader_text := "你是隊長" if GameState.is_party_leader else "跟隨隊長"
		status_label.text = "組隊中（%d / %d）— %s" % [count, GameState.party_max_size, leader_text]
		leave_button.show()
	else:
		status_label.text = "尚未組隊（最多 %d 人）" % GameState.party_max_size
		leave_button.hide()

func _populate_members() -> void:
	for child in member_list.get_children():
		child.queue_free()

	if not GameState.in_player_party:
		var lbl := Label.new()
		lbl.text = "目前沒有隊伍成員。"
		member_list.add_child(lbl)
		return

	for member in GameState.party_members:
		if typeof(member) != TYPE_DICTIONARY:
			continue
		var row := HBoxContainer.new()
		var name_label := Label.new()
		var member_name := str(member.get("playerName", "旅人"))
		var level := int(member.get("playerLevel", 1))
		var leader_mark := "★ " if bool(member.get("isLeader", false)) else ""
		name_label.text = "%s%s Lv.%d" % [leader_mark, member_name, level]
		name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(name_label)

		if GameState.is_party_leader and not bool(member.get("isLeader", false)):
			var kick_btn := Button.new()
			kick_btn.text = "踢出"
			var target_id := int(member.get("playerId", 0))
			kick_btn.pressed.connect(func(): NetworkClient.party_kick(target_id))
			row.add_child(kick_btn)

		member_list.add_child(row)

func _populate_invites() -> void:
	for child in invite_list.get_children():
		child.queue_free()

	if GameState.in_player_party and GameState.party_members.size() >= GameState.party_max_size:
		var lbl := Label.new()
		lbl.text = "隊伍已滿。"
		invite_list.add_child(lbl)
		return

	var nearby: Array = GameState.nearby_players_for_party
	if nearby.is_empty():
		var empty := Label.new()
		empty.text = "附近（同地圖）沒有其他玩家。"
		invite_list.add_child(empty)
		return

	for entry in nearby:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var player_id := int(entry.get("playerId", 0))
		if player_id <= 0 or player_id == GameState.player_id:
			continue
		if _is_in_party(player_id):
			continue

		var row := HBoxContainer.new()
		var name_label := Label.new()
		name_label.text = "%s Lv.%d" % [str(entry.get("playerName", "旅人")), int(entry.get("playerLevel", 1))]
		name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(name_label)

		var invite_btn := Button.new()
		invite_btn.text = "邀請"
		invite_btn.pressed.connect(func(): NetworkClient.party_invite(player_id))
		row.add_child(invite_btn)

		invite_list.add_child(row)

func _is_in_party(player_id: int) -> bool:
	for member in GameState.party_members:
		if typeof(member) == TYPE_DICTIONARY and int(member.get("playerId", 0)) == player_id:
			return true
	return false

func _on_leave_pressed() -> void:
	NetworkClient.party_leave()

func _on_accept_invite_pressed() -> void:
	NetworkClient.party_accept()

func _on_decline_invite_pressed() -> void:
	NetworkClient.party_decline()

func _on_close_pressed() -> void:
	hide()
	closed.emit()
