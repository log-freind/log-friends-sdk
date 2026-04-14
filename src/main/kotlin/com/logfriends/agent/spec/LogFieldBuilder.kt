package com.logfriends.agent.spec

class LogFieldBuilder internal constructor(
    private val name: String,
    private val parent: LogSpec
) {
    private var type: String = "string"
    private var required: Boolean = false
    private var example: String = ""
    private var description: String = ""

    fun type(clazz: Class<*>): LogFieldBuilder {
        this.type = mapType(clazz)
        return this
    }

    fun type(typeName: String): LogFieldBuilder {
        this.type = typeName
        return this
    }

    fun required(): LogFieldBuilder {
        this.required = true
        return this
    }

    fun example(example: String): LogFieldBuilder {
        this.example = example
        return this
    }

    fun description(description: String): LogFieldBuilder {
        this.description = description
        return this
    }

    fun and(): LogSpec {
        return parent
    }

    internal fun build(): LogFieldDef {
        return LogFieldDef(name, type, required, example, description)
    }

    private fun mapType(clazz: Class<*>): String {
        return when (clazz) {
            String::class.java, java.lang.String::class.java -> "string"
            Int::class.java, java.lang.Integer::class.java -> "integer"
            Long::class.java, java.lang.Long::class.java -> "long"
            Boolean::class.java, java.lang.Boolean::class.java -> "boolean"
            Double::class.java, java.lang.Double::class.java -> "number"
            else -> clazz.simpleName.lowercase()
        }
    }
}
