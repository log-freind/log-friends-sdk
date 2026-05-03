# log-friends-sdk

Pure Kotlin SDK for instrumenting Spring Boot applications without application code changes. It uses ByteBuddy for runtime interception and sends JSON batches directly to `log-friends-console` over HTTP.

## Current Architecture

```text
Spring Boot App
  -> log-friends-sdk
  -> HTTP POST /ingest
  -> log-friends-console
  -> TimescaleDB event tables
```

Kafka, broker topics, and Protobuf transport are not part of the current ingest path.

## Key Features

- **5 interception points**: HTTP requests, Logback logs, JDBC queries, `@Service` method traces, and `@LogEvent` methods
- **Dynamic installation**: installs through Spring Boot `EnvironmentPostProcessor`
- **ByteBuddy retransformation**: non-invasive runtime instrumentation
- **Batched HTTP transport**: flushes after 100 events or 500ms by default
- **Backpressure behavior**: bounded in-memory queue; events are dropped when the queue is full
- **LogSpec DSL**: structured event schema registration for business events

## Runtime Flow

```text
LogFriendsInstaller
  -> ByteBuddyAgent.install()
  -> InstrumentationRegistry.installAll()
  -> interceptors enqueue AgentEvent
  -> BatchTransporter
  -> POST http://localhost:8082/ingest
```

## Quick Start

### 1. Add Dependency

```kotlin
dependencies {
    implementation("com.logfriends:log-friends-sdk:1.2.0")
}
```

### 2. Configure Console Ingest URL

Environment variable:

```bash
export LOGFRIENDS_INGEST_URL=http://localhost:8082/ingest
```

Or JVM system property:

```bash
-Dlogfriends.ingest.url=http://localhost:8082/ingest
```

### 3. Enable JVM Attach

```bash
-Djdk.attach.allowAttachSelf=true
```

Then start the Spring Boot app normally. The SDK installs itself during Spring Boot startup.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `logfriends.ingest.url` | `http://localhost:8082/ingest` | Console ingest endpoint |
| `logfriends.batch.size` | `100` | Events per batch before flushing |
| `logfriends.batch.interval.ms` | `500` | Scheduled flush interval |
| `logfriends.queue.capacity` | `10000` | Max in-memory queued events |
| `logfriends.trace.threshold.ms` | `10` | Min duration for `METHOD_TRACE` events |
| `logfriends.agent.enabled` | `true` | Disable all instrumentation when `false` |
| `logfriends.worker.id` | none | Fixed Worker identifier. Must match the Console Agent registration |

Environment variable:

| Variable | Description |
|----------|-------------|
| `LOGFRIENDS_INGEST_URL` | Overrides `logfriends.ingest.url` |
| `LOGFRIENDS_WORKER_ID` | Overrides `logfriends.worker.id` |

## Interceptor Toggles

| Property | Default |
|----------|---------|
| `logfriends.interceptor.http.enabled` | `true` |
| `logfriends.interceptor.logback.enabled` | `true` |
| `logfriends.interceptor.jdbc.enabled` | `true` |
| `logfriends.interceptor.method_trace.enabled` | `true` |
| `logfriends.interceptor.log_event.enabled` | `true` |

## Event Types

The SDK sends JSON events with one of these `type` values:

| Type | Source |
|------|--------|
| `HTTP` | `DispatcherServlet.doService()` |
| `LOG` | Logback `Logger.callAppenders()` |
| `JDBC` | `PreparedStatement.execute*()` |
| `METHOD_TRACE` | Spring `@Service` public methods over threshold |
| `LOG_EVENT` | Methods annotated with `@LogEvent` |

## Example `@LogEvent`

```kotlin
import com.logfriends.agent.annotation.LogEvent

@Service
class UserService {
    @LogEvent("user_registered")
    fun registerUser(userId: String, email: String) {
        // Parameters are captured as event fields.
    }
}
```

## Troubleshooting

### Dynamic Agent Install Failed

Add:

```bash
-Djdk.attach.allowAttachSelf=true
```

### Events Are Not Reaching Console

Check:

```bash
export LOGFRIENDS_INGEST_URL=http://localhost:8082/ingest
curl http://localhost:8082/actuator/health
```

### Events Are Dropped

Increase queue capacity or reduce event volume:

```bash
-Dlogfriends.queue.capacity=20000
```

## Related Docs

- `../docs/system/overview.md`
- `../docs/system/runtime-flow.md`
- `../docs/sdk/overview.md`
- `../docs/sdk/transport.md`
