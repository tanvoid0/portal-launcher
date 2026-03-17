package com.tanvoid0.portallauncher.data

/**
 * Simple serialization for AppVisibilityConfig to/from stored string.
 * Format: "primary:id1,id2;secondary:id3;hidden:id4"
 */
object ConfigJson {

    private const val SEP = ";"
    private const val COLON = ":"
    private const val COMMA = ","

    fun appVisibilityToStr(c: AppVisibilityConfig): String = buildString {
        append("primary:").append(c.primaryCategoryIds.joinToString(COMMA))
        append(SEP).append("secondary:").append(c.secondaryCategoryIds.joinToString(COMMA))
        append(SEP).append("hidden:").append(c.hiddenCategoryIds.joinToString(COMMA))
    }

    fun parseAppVisibility(str: String?): AppVisibilityConfig {
        if (str.isNullOrBlank()) return AppVisibilityConfig()
        val primary = mutableListOf<String>()
        val secondary = mutableListOf<String>()
        val hidden = mutableListOf<String>()
        str.split(SEP).forEach { part ->
            val (key, value) = part.split(COLON, limit = 2).let { 
                if (it.size == 2) it[0] to it[1] else return@forEach 
            }
            val list = when (key) {
                "primary" -> primary
                "secondary" -> secondary
                "hidden" -> hidden
                else -> return@forEach
            }
            if (value.isNotBlank()) list.addAll(value.split(COMMA).map { it.trim() }.filter { it.isNotBlank() })
        }
        return AppVisibilityConfig(
            primaryCategoryIds = primary,
            secondaryCategoryIds = secondary,
            hiddenCategoryIds = hidden
        )
    }
}
