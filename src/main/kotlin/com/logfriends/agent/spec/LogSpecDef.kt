package com.logfriends.agent.spec

class LogSpecDef(
    val name: String,
    val description: String,
    val levels: List<String>,
    val category: String,
    val fields: List<LogFieldDef>
) {
    fun toJson(): String {
        val levelsJson = levels.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
        val fieldsJson = fields.joinToString(separator = ",", prefix = "[", postfix = "]") { it.toJson() }
        
        return """{"name":"${escape(name)}","description":"${escape(description)}","levels":$levelsJson,"category":"${escape(category)}","fields":$fieldsJson}"""
    }

    private fun escape(s: String?): String {
        if (s == null) return ""
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
    }
}
