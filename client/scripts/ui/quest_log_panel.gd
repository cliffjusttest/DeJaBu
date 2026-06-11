extends Control

signal closed

@onready var quest_list: VBoxContainer = $Panel/Margin/VBox/ScrollContainer/QuestList
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton
@onready var empty_label: Label = $Panel/Margin/VBox/ScrollContainer/QuestList/EmptyLabel

func _ready() -> void:
	close_button.pressed.connect(func(): closed.emit())
	hide()

func open() -> void:
	NetworkClient.request_quest_list()
	show()

func populate(quests: Array) -> void:
	for child in quest_list.get_children():
		child.queue_free()

	if quests.is_empty():
		var lbl := Label.new()
		lbl.text = "目前沒有進行中的任務。"
		lbl.add_theme_font_size_override("font_size", 14)
		quest_list.add_child(lbl)
		return

	for quest in quests:
		var panel := PanelContainer.new()
		var margin := MarginContainer.new()
		margin.add_theme_constant_override("margin_left", 8)
		margin.add_theme_constant_override("margin_top", 6)
		margin.add_theme_constant_override("margin_right", 8)
		margin.add_theme_constant_override("margin_bottom", 6)
		var vbox := VBoxContainer.new()

		var name_label := Label.new()
		var status := str(quest.get("status", "IN_PROGRESS"))
		var ready := bool(quest.get("readyToClaim", false))
		var status_text := "✓ 可領取" if ready else ("完成" if status == "COMPLETED" else "進行中")
		name_label.text = "%s  [%s]" % [str(quest.get("name", "")), status_text]
		name_label.add_theme_font_size_override("font_size", 15)
		if ready:
			name_label.modulate = Color(0.4, 1.0, 0.4)

		var desc_label := Label.new()
		desc_label.text = str(quest.get("description", ""))
		desc_label.add_theme_font_size_override("font_size", 13)
		desc_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		desc_label.modulate = Color(0.85, 0.85, 0.85)

		var quest_type := str(quest.get("type", "KILL"))
		var progress_text := ""
		if quest_type == "KILL":
			progress_text = "進度：%d / %d" % [int(quest.get("progress", 0)), int(quest.get("requiredCount", 1))]
		var progress_label := Label.new()
		progress_label.text = progress_text
		progress_label.add_theme_font_size_override("font_size", 13)
		progress_label.modulate = Color(0.9, 0.8, 0.5)

		var reward_label := Label.new()
		reward_label.text = "報酬：%d EXP + %d 技能點" % [int(quest.get("rewardExp", 0)), int(quest.get("rewardSkillPoints", 0))]
		reward_label.add_theme_font_size_override("font_size", 12)
		reward_label.modulate = Color(0.7, 0.9, 1.0)

		vbox.add_child(name_label)
		vbox.add_child(desc_label)
		if not progress_text.is_empty():
			vbox.add_child(progress_label)
		vbox.add_child(reward_label)
		margin.add_child(vbox)
		panel.add_child(margin)
		quest_list.add_child(panel)
