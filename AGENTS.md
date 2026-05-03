# AGENTS.md — log-friends-sdk

## 책임

`log-friends-sdk`는 대상 Spring Boot 앱 내부에서 런타임 이벤트를 캡처하고 Console의 `POST /ingest`로 HTTP JSON batch를 전송하는 Kotlin SDK다.

SDK 책임은 여기서 끝난다.

```text
Spring Boot App + log-friends-sdk
  -> ByteBuddy 계측
  -> AgentEvent 캡처
  -> HTTP JSON batch POST /ingest
```

TimescaleDB 저장, 통계 생성, Log Catalog 응답 조립, Agent 등록 관리는 Console 책임이다.

## 핵심 기준

- ByteBuddy 5종 계측: `HTTP`, `LOG`, `JDBC`, `METHOD_TRACE`, `LOG_EVENT`
- 전송: Kafka/NATS가 아니라 HTTP POST `/ingest`
- `workerId`는 앱 설정에서 고정 주입한다.
- `LOGFRIENDS_WORKER_ID` 또는 `logfriends.worker.id`를 사용한다.
- `LOGFRIENDS_INGEST_URL` 또는 `logfriends.ingest.url`로 Console ingest endpoint를 설정한다.
- `LogSpec`은 SDK가 자동 등록하지 않는다. Console API로 등록/수정한다.

## 빌드

```bash
./gradlew build
./gradlew publishToMavenLocal
```

## 주의사항

- 필수 JVM 옵션: `-Djdk.attach.allowAttachSelf=true`
- 테스트 환경에서는 `logfriends.agent.enabled=false` 권장
- Kafka/NATS/Spark/ClickHouse 관련 전제를 추가하지 않는다.
- 문서 기준은 `docs/sdk/*`, `docs/system/runtime-flow.md`, `docs/console/ingest-api.md`를 따른다.
