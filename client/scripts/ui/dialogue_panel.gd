extends Control

signal choice_made(choice_index: int)
signal dialogue_closed

@onready var backdrop: ColorRect = $Backdrop
@onready var npc_name_label: Label = $Panel/Margin/VBox/NpcNameLabel
@onready var dialogue_text: Label = $Panel/Margin/VBox/DialogueText
@onready var choices_box: VBoxContainer = $Panel/Margin/VBox/ChoicesBox
@onready var close_button: Button = $Panel/Margin/VBox/CloseButton

func _ready() -> void:
	close_button.pressed.connect(_on_close)
	hide()

func show_dialogue(npc_name: String, text: String, choices: Array) -> void:
	npc_name_label.text = npc_name
	dialogue_text.text = text

	for child in choices_box.get_children():
		child.queue_free()

	if choices.is_empty():
		close_button.show()
	else:
		close_button.hide()
		for choice in choices:
			var btn := Button.new()
			btn.text = str(choice.get("text", ""))
			btn.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
			var idx := int(choice.get("index", 0))
			btn.pressed.connect(func(): choice_made.emit(idx))
			choices_box.add_child(btn)

	show()

func hide_dialogue() -> void:
	hide()
	for child in choices_box.get_children():
		child.queue_free()

func _on_close() -> void:
	hide_dialogue()
	dialogue_closed.emit()
