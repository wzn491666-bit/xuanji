package com.xuanji.android

enum class ThinkingDisplay(val wire: String, val label: String) {
    COLLAPSED("collapsed", "全部折叠"),
    PREVIEW("preview", "半折叠"),
    EXPANDED("expanded", "展开思考总览");

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.wire == value } ?: PREVIEW
    }
}
