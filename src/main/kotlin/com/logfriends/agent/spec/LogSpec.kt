package com.logfriends.agent.spec

import com.logfriends.agent.annotation.LogCategory
import com.logfriends.agent.annotation.LogLevel

class LogSpec private constructor(private val name: String) {
    private var description: String = ""
    private val levels = mutableListOf<LogLevel>()
    private var category: LogCategory = LogCategory.BUSINESS
    private val fieldBuilders = mutableListOf<LogFieldBuilder>()

    fun description(description: String): LogSpec {
        this.description = description
        return this
    }

    fun level(vararg levels: LogLevel): LogSpec {
        this.levels.addAll(levels)
        return this
    }

    fun category(category: LogCategory): LogSpec {
        this.category = category
        return this
    }

    fun field(fieldName: String): LogFieldBuilder {
        val builder = LogFieldBuilder(fieldName, this)
        fieldBuilders.add(builder)
        return builder
    }

    fun build(): LogSpecDef {
        val levelNames = if (levels.isEmpty()) {
            listOf("INFO")
        } else {
            levels.map { it.name }
        }

        val builtFields = fieldBuilders.map { it.build() }
        val def = LogSpecDef(name, description, levelNames, category.name, builtFields)
        LogSpecRegistry.register(def)
        return def
    }

    companion object {
        @JvmStatic
        fun define(name: String): LogSpec {
            return LogSpec(name)
        }
    }
}
