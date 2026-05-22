# log-friends-sdk

Kotlin SDK that instruments Spring Boot applications with ByteBuddy and sends HTTP JSON event batches to `log-friends-console`.

```text
Spring Boot App
  -> log-friends-sdk
  -> HTTP POST /ingest
  -> log-friends-console
```

The first-phase SDK captures `HTTP`, `LOG`, `JDBC`, `METHOD_TRACE`, and `LOG_EVENT` and sends HTTP JSON batches to the Console ingest endpoint.

## Quick Start

```kotlin
dependencies {
    implementation("com.logfriends:log-friends-sdk:0.1.0")
}
```

Required runtime configuration:

```bash
export LOGFRIENDS_WORKER_ID=order-service-local-1
export LOGFRIENDS_INGEST_URL=http://localhost:8082/ingest
```

At startup the SDK sends Agent registration to the Console using `workerId` and `appName`.
`appName` resolves from `LOGFRIENDS_APP_NAME`, `logfriends.app.name`, or `spring.application.name`.
If `appName` is missing or registration fails, the target app keeps running and the SDK logs a warning.

Required JVM option for runtime attach:

```bash
-Djdk.attach.allowAttachSelf=true
```

Business `LOG_EVENT` names must use camelCase:

```kotlin
@LogEvent("userRegistered")
fun registerUser(userId: String, @LogMasked email: String)
```

## Documentation

Detailed SDK docs live in the GitHub Wiki:

- [English Wiki Home](https://github.com/log-freind/log-friends-sdk/wiki)
- [Korean Wiki Home](https://github.com/log-freind/log-friends-sdk/wiki/KO-Home)
- [Japanese Wiki Home](https://github.com/log-freind/log-friends-sdk/wiki/JA-Home)
- [Simplified Chinese Wiki Home](https://github.com/log-freind/log-friends-sdk/wiki/ZH-Home)
- [Spanish Wiki Home](https://github.com/log-freind/log-friends-sdk/wiki/ES-Home)
- [Portuguese Wiki Home](https://github.com/log-freind/log-friends-sdk/wiki/PT-Home)

Start there for runtime configuration, masking, `LOG_EVENT` contract details, package layout, and troubleshooting.
