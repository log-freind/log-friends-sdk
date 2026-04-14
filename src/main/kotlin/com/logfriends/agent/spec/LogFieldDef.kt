package com.logfriends.agent.spec

class LogFieldDef(
    val name: String,
    val type: String,
    val isRequired: Boolean,
    val example: String,
    val description: String
) {
    fun toJson(): String {
        return """{"name":"${escape(name)}","type":"${escape(type)}","required":$isRequired,"example":"${escape(example)}","description":"${escape(description)}"}"""
    }

    private fun escape(s: String?): String {
        if (s == null) return ""
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
    }
}
