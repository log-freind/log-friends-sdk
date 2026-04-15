# log-friends-sdk

Pure Kotlin SDK for instrumenting Spring Boot applications without code changes, using ByteBuddy for runtime bytecode manipulation and Kafka for event streaming.

## Overview

log-friends-sdk is an observability agent that automatically captures runtime events from Spring Boot applications and streams them to Kafka in Protobuf format. It requires no source code modifications and installs dynamically via Spring's `EnvironmentPostProcessor` hook.

## Key Features

- **5 Interception Points**: HTTP requests, Logback logs, JDBC queries, @Service method calls, and @LogEvent annotations
- **LogSpec DSL**: Structured logging with schema definition and validation
- **Batched Transport**: Automatically groups events (100 events or 500ms) for efficient Kafka publishing
- **Dynamic Installation**: No javaagent flag required; integrates via Spring Boot's configuration processor
- **ByteBuddy-Powered**: Non-invasive bytecode instrumentation with RETRANSFORMATION strategy
- **Zero Configuration**: Sensible defaults work out-of-the-box; override via system properties or environment variables

## Architecture

```
App JVM
  ├─ LogFriendsInstaller (EnvironmentPostProcessor)
  │   └─ ByteBuddyAgent.install()
  │       └─ InstrumentationRegistry.installAll()
  │           ├─ SpringInterceptor → DispatcherServlet.doService()
  │           │   └─ HTTP request/response capture
  │           │
  │           ├─ LogbackInterceptor → Logger.callAppenders()
  │           │   └─ LOG event capture with MDC
  │           │
  │           ├─ JdbcInterceptor → PreparedStatement.execute*()
  │           │   └─ SQL query and duration capture
  │           │
  │           ├─ MethodTraceInterceptor → @Service methods (≥10ms)
  │           │   └─ Method execution tracing
  │           │
  │           └─ LogEventInterceptor → @LogEvent methods
  │               └─ Custom event capture with arguments
  │
  ├─ BatchTransporter (Singleton, lazy KafkaProducer)
  │   └─ Event Queue (LinkedBlockingQueue, capacity 10,000)
  │       ├─ Scheduled flush (every 500ms)
  │       └─ Size-triggered flush (≥100 events)
  │           └─ Kafka Producer → log-friends.batch topic
  │
  └─ LogFriendsAutoConfiguration (Spring Bean)
      ├─ 5-second delayed SpecScanner
      └─ Shutdown hook (flush remaining events)
```

## Quick Start

### 1. Add Dependency

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.logfriends:log-friends-sdk:1.0.0")
}
```

### 2. Configure Kafka Broker

Set via environment variable (recommended):
```bash
export LOGFRIENDS_KAFKA_BROKERS=kafka-broker-1:9092,kafka-broker-2:9092
```

Or system property:
```bash
-Dlogfriends.kafka.brokers=localhost:9092
```

### 3. Enable JVM Attach (Required)

Add to JVM startup options:
```bash
-Djdk.attach.allowAttachSelf=true
```

### 4. No Code Changes Needed

Just run your Spring Boot app. The agent installs automatically during application startup.

## Configuration

### System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `logfriends.kafka.brokers` | `localhost:9092` | Kafka bootstrap servers (comma-separated) |
| `logfriends.batch.size` | `100` | Number of events before flushing |
| `logfriends.batch.interval.ms` | `500` | Flush interval in milliseconds |
| `logfriends.queue.capacity` | `10000` | Max events in queue (excess dropped) |
| `logfriends.trace.threshold.ms` | `10` | Min method duration for @Service tracing |
| `logfriends.worker.name` | `unknown` | Worker identifier (fallback to `spring.application.name`) |

### Environment Variables

| Variable | Overrides |
|----------|-----------|
| `LOGFRIENDS_KAFKA_BROKERS` | `logfriends.kafka.brokers` |

## File Structure

```
src/main/kotlin/com/logfriends/agent/
├── LogFriendsInstaller.kt          # Spring EnvironmentPostProcessor
├── LogFriendsAutoConfiguration.kt  # Spring @AutoConfiguration bean
├── BatchTransporter.kt             # Event queue + Kafka producer
├── InstrumentationRegistry.kt      # ByteBuddy instrumentation setup
├── SpringInterceptor.kt            # HTTP event capture
├── LogbackInterceptor.kt           # Logback event capture
├── JdbcInterceptor.kt              # JDBC event capture
├── MethodTraceInterceptor.kt       # @Service method tracing
├── LogEventInterceptor.kt          # @LogEvent annotation support
├── annotation/
│   ├── LogEvent.kt                 # Custom event annotation
│   ├── LogCategory.kt              # Log category enum
│   └── LogLevel.kt                 # Log level enum
└── spec/
    ├── LogSpec.kt                  # DSL for log spec definition
    ├── LogSpecBuilder.kt           # Builder pattern implementation
    ├── LogSpecRegistry.kt          # Global registry of specs
    ├── LogFieldDef.kt              # Field definition
    ├── LogFieldBuilder.kt          # Field builder
    └── SpecScanner.kt              # Scheduled spec scanner
```

## Protobuf Messages

The SDK sends Protobuf-serialized `AgentMessage` to Kafka topic `log-friends.batch`:

```proto
message AgentMessage {
  string worker_id = 1;           // Worker identifier
  
  oneof payload {
    BatchPayload batch = 2;       // Container of events
  }
}

message BatchPayload {
  repeated AgentEvent events = 1; // Up to 100 events per batch
}

message AgentEvent {
  oneof event {
    LogEvent log = 1;             // Logback logs
    HttpEvent http = 2;           // HTTP requests
    LogEventCapture log_event = 3; // @LogEvent captures
    JdbcEvent jdbc = 4;           // SQL queries
    MethodTraceEvent method_trace = 5; // @Service methods
  }
}
```

## Example Usage

### Using @LogEvent Annotation

```kotlin
import com.logfriends.agent.annotation.LogEvent

@Service
class UserService {
    
    @LogEvent("user_registered")
    fun registerUser(userId: String, email: String, role: String) {
        // Parameters automatically captured as event fields
    }
}
```

### Custom Log Specification

```kotlin
import com.logfriends.agent.spec.LogSpec

val userSpec = LogSpec.define("user_activity")
    .description("User interaction events")
    .level("INFO")
    .field("user_id").required().example("user-123")
    .field("action").required().example("login")
    .field("ip_address").optional().example("192.168.1.1")
    .build()
```

## Troubleshooting

### "Failed to install dynamic agent"

**Issue**: `JVM option -Djdk.attach.allowAttachSelf=true` is missing.

**Solution**: Add the JVM option before startup.

```bash
java -Djdk.attach.allowAttachSelf=true -jar your-app.jar
```

### Events Dropped

**Issue**: Queue capacity exceeded, events are dropped.

**Check**: Increase `logfriends.queue.capacity` or reduce event volume. Monitor `BatchTransporter.stats`.

```bash
-Dlogfriends.queue.capacity=20000
```

### Kafka Connection Failed

**Issue**: Producer can't reach broker.

**Check**: Verify `LOGFRIENDS_KAFKA_BROKERS` or `-Dlogfriends.kafka.brokers` is correct.

```bash
export LOGFRIENDS_KAFKA_BROKERS=kafka:9092
```

## Related Repositories

- [log-friends-pipeline](../log-friends-pipeline) - Spark/Flink pipeline for event processing
- [log-friends-console](../log-friends-console) - Web UI for event visualization
- [log-friends-examples](../log-friends-examples) - Example Spring Boot applications
- [docs](../docs) - Architecture documentation and ADRs

## Development

### Build

```bash
./gradlew build
```

### Test

```bash
./gradlew test
```

### Publish to Maven Local

```bash
./gradlew publishToMavenLocal
```

## Design Decisions

- **SDK is `compileOnly`**: Prevents singleton duplication if consumer also uses the SDK
- **Shadow JAR with relocation**: java-agent module relocates all dependencies to `com.logfriends.shaded.*`
- **RETRANSFORMATION strategy**: Allows safe instrumentation of already-loaded classes
- **5-second handshake delay**: Ensures Spring is fully initialized before spec scanning
- **Lazy KafkaProducer**: Initialized on first event to avoid SLF4J loading issues
- **Queue-based batching**: Decouples event capture from network I/O

## License

Log Friends SDK is part of the Log Friends project. See parent repository for license details.
