class_name CharacterAppearanceData
extends RefCounted

const STYLE_1 := "STYLE_1"
const STYLE_2 := "STYLE_2"
const STYLE_3 := "STYLE_3"
const STYLE_4 := "STYLE_4"
const STYLE_5 := "STYLE_5"

const OPTIONS: Array[String] = [STYLE_1, STYLE_2, STYLE_3, STYLE_4, STYLE_5]

const TINTS := {
	STYLE_1: Color(1.0, 1.0, 1.0),
	STYLE_2: Color(1.0, 0.78, 0.68),
	STYLE_3: Color(0.72, 0.86, 1.0),
	STYLE_4: Color(0.78, 1.0, 0.78),
	STYLE_5: Color(0.86, 0.76, 1.0),
}

static func tint_for(code: String) -> Color:
	return TINTS.get(code, Color.WHITE)
