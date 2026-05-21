# log-friends-sdk

Kotlin SDK that instruments Spring Boot applications with ByteBuddy and sends HTTP JSON event batches to `log-friends-console`.

```text
Spring Boot App
  -> log-friends-sdk
  -> HTTP POST /ingest
  -> log-friends-console
```

The first-phase SDK captures `HTTP`, `LOG`, `JDBC`, `METHOD_TRACE`, and `LOG_EVENT`. Kafka, broker topics, and Protobuf transport are not part of the current ingest path.

## Quick Start

```kotlin
dependencies {
    implementation("com.logfriends:log-friends-sdk:1.3.0")
}
```

Required runtime configuration:

```bash
export LOGFRIENDS_WORKER_ID=order-service-local-1
export LOGFRIENDS_INGEST_URL=http://localhost:8082/ingest
```

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

Start there for runtime configuration, masking, `LOG_EVENT` contract details, package layout, and troubleshooting.
