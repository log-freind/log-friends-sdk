plugins {
    kotlin("jvm") version "2.3.20"
    id("com.google.protobuf") version "0.9.4"
    `maven-publish`
}

group   = "com.logfriends"
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // ByteBuddy - 바이트코드 계측
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")

    // Protobuf - 직렬화
    implementation("com.google.protobuf:protobuf-java:3.25.3")

    // Spring Boot AutoConfigure - 컴파일 전용 (Spring 없는 환경도 지원)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.1")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("${rootProject.projectDir}/proto")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
