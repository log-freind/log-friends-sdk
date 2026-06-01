package com.logfriends.agent.annotation

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class LogField(
    val description: String = "",
    val type: String = "",
    val required: Boolean = true
)
