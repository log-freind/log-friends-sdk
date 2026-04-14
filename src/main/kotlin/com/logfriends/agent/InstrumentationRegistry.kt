package com.logfriends.agent

import com.logfriends.agent.annotation.LogEvent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import java.lang.instrument.Instrumentation

object InstrumentationRegistry {

    fun installAll(inst: Instrumentation) {
        installSpring(inst)
        installLogback(inst)
        installJdbc(inst)
        installMethodTrace(inst)
        installLogEvent(inst)
    }

    private fun installSpring(inst: Instrumentation) {
        AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
            .type(ElementMatchers.named("org.springframework.web.servlet.DispatcherServlet"))
            .transform { builder, _, _, _, _ ->
                builder.method(ElementMatchers.named("doService"))
                    .intercept(MethodDelegation.to(SpringInterceptor::class.java))
            }.installOn(inst)
        println("[Log Friends] DispatcherServlet instrumented")
    }

    private fun installLogback(inst: Instrumentation) {
        AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
            .type(ElementMatchers.named("ch.qos.logback.classic.Logger"))
            .transform { builder, _, _, _, _ ->
                builder.method(ElementMatchers.named("callAppenders"))
                    .intercept(MethodDelegation.to(LogbackInterceptor::class.java))
            }.installOn(inst)
        println("[Log Friends] Logback Logger instrumented")
    }

    private fun installJdbc(inst: Instrumentation) {
        AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
            .type(ElementMatchers.hasSuperType<net.bytebuddy.description.type.TypeDescription>(
                ElementMatchers.named("java.sql.PreparedStatement")))
            .transform { builder, _, _, _, _ ->
                builder.method(
                    ElementMatchers.named<net.bytebuddy.description.method.MethodDescription>("executeQuery")
                        .or(ElementMatchers.named("executeUpdate"))
                        .or(ElementMatchers.named("executeBatch"))
                        .or(ElementMatchers.named("execute"))
                ).intercept(MethodDelegation.to(JdbcInterceptor::class.java))
            }.installOn(inst)
        println("[Log Friends] JDBC PreparedStatement instrumented")
    }

    private fun installMethodTrace(inst: Instrumentation) {
        AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
            .type(ElementMatchers.isAnnotatedWith<net.bytebuddy.description.type.TypeDescription>(
                ElementMatchers.named("org.springframework.stereotype.Service")))
            .transform { builder, _, _, _, _ ->
                builder.method(
                    ElementMatchers.isPublic<net.bytebuddy.description.method.MethodDescription>()
                        .and(ElementMatchers.not(ElementMatchers.isConstructor()))
                ).intercept(MethodDelegation.to(MethodTraceInterceptor::class.java))
            }.installOn(inst)
        println("[Log Friends] @Service methods instrumented (threshold: ${System.getProperty("logfriends.trace.threshold.ms", "10")}ms)")
    }

    private fun installLogEvent(inst: Instrumentation) {
        AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly())
            .type(ElementMatchers.declaresAnnotation<net.bytebuddy.description.type.TypeDescription>(
                ElementMatchers.annotationType(ElementMatchers.named(LogEvent::class.java.name)))
                .or(ElementMatchers.declaresMethod<net.bytebuddy.description.type.TypeDescription>(
                    ElementMatchers.isAnnotatedWith(LogEvent::class.java))))
            .transform { builder, _, _, _, _ ->
                builder.method(ElementMatchers.isAnnotatedWith<net.bytebuddy.description.method.MethodDescription>(LogEvent::class.java))
                    .intercept(MethodDelegation.to(LogEventInterceptor::class.java))
            }.installOn(inst)
        println("[Log Friends] @LogEvent methods instrumented")
    }
}
