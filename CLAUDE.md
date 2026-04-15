# CLAUDE.md — log-friends-sdk

## 개요
Spring Boot 앱을 **코드 수정 없이** 계측하는 Observability SDK. ByteBuddy로 런타임 바이트코드를 계측해 HTTP/LOG/JDBC/METHOD_TRACE/@LogEvent 이벤트를 Kafka로 전송한다.

## 핵심 스택
- Kotlin 2.3.20 / JVM 21
- ByteBuddy 1.15.11 / Kafka Clients 3.9.0 / Protobuf 3.25.3
- Spring Boot AutoConfigure 3.3.0 (compileOnly)
- JitPack 배포: `com.github.log-freind:log-friends-sdk:v1.0.0`

## 빌드 & 실행
```bash
./gradlew build                  # 빌드 + 테스트
./gradlew publishToMavenLocal    # 로컬 Maven 배포
git tag vX.Y.Z && git push origin vX.Y.Z  # JitPack 릴리즈
```

## 주요 파일
```
src/main/kotlin/com/logfriends/agent/
├── LogFriendsInstaller.kt          # EnvironmentPostProcessor — ByteBuddy 자동 설치
├── LogFriendsAutoConfiguration.kt  # @AutoConfiguration — SpecScanner 5초 딜레이
├── BatchTransporter.kt             # Event Queue(10k) + Kafka lazy init
├── InstrumentationRegistry.kt      # 5개 인터셉터 일괄 설치
├── SpringInterceptor.kt            # HTTP (DispatcherServlet)
├── LogbackInterceptor.kt           # LOG (callAppenders)
├── JdbcInterceptor.kt              # JDBC (PreparedStatement)
├── MethodTraceInterceptor.kt       # METHOD_TRACE (@Service, ≥10ms)
├── LogEventInterceptor.kt          # @LogEvent 어노테이션
└── spec/                           # LogSpec DSL
proto/agent.proto                   # Protobuf 메시지 정의
```

## 인터셉터 & 이벤트
`HTTP` / `LOG` / `JDBC` / `METHOD_TRACE` (@Service ≥10ms) / `LOG_EVENT` (@LogEvent) → BATCH 100건/500ms → `log-friends.batch`

## 주요 파라미터
| 환경변수/프로퍼티 | 기본값 | 설명 |
|---|---|---|
| `LOGFRIENDS_KAFKA_BROKERS` | `localhost:9092` | Kafka 브로커 |
| `logfriends.batch.size` | `100` | 배치 크기 |
| `logfriends.batch.interval.ms` | `500` | flush 주기 |
| `logfriends.queue.capacity` | `10000` | 큐 최대 용량 |
| `logfriends.trace.threshold.ms` | `10` | METHOD_TRACE 임계값 |
| `logfriends.agent.enabled` | `true` | false 시 계측 전체 비활성화 |

## 주의사항
- 필수 JVM 옵션: `-Djdk.attach.allowAttachSelf=true`
- `KafkaProducer` lazy 초기화 (SLF4J 로드 순서 보장)
- 테스트 환경에서 ByteBuddy+Mockito 충돌 방지: `logfriends.agent.enabled=false`
- 커밋: 한글, `feat:`/`fix:`/`refactor:`/`docs:` prefix
