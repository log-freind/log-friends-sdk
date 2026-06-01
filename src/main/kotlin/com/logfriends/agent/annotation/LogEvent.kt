package com.logfriends.agent.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LogEvent(
    val value: String = "",
    val name: String = "",
    val description: String = "",
    val apiMethod: String = "",
    val apiPath: String = "",
    val apiDescription: String = ""
)
