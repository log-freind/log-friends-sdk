package com.logfriends.agent

import net.bytebuddy.agent.ByteBuddyAgent
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Spring Boot 기동 초기 (Environment 준비 단계)에 ByteBuddy agent를 동적으로 설치한다.
 * META-INF/spring.factories 에 등록되어 자동 실행된다.
 *
 * JVM 옵션 필요: -Djdk.attach.allowAttachSelf=true
 */
class LogFriendsInstaller : EnvironmentPostProcessor {

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication
    ) {
        try {
            val inst = ByteBuddyAgent.install()
            InstrumentationRegistry.installAll(inst)
            println("[Log Friends] Dynamic agent installed successfully")
        } catch (e: Exception) {
            System.err.println("[Log Friends] Failed to install dynamic agent: ${e.message}")
            System.err.println("[Log Friends] Add JVM option: -Djdk.attach.allowAttachSelf=true")
        }
    }
}
