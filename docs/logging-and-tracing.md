# 로깅 및 트레이싱 가이드

## 개요

이 프로젝트는 요청 단위 `traceId`, `requestId`를 MDC에 넣고, 응답 헤더와 실패 응답 본문에도 같은 값을 반영합니다.

## 핵심 구성 요소

- `common/logging/TraceContext`
  - MDC 키와 HTTP 헤더 이름을 정의합니다.
- `config/logging/MdcLoggingFilter`
  - 요청 시작 시 `traceId`, `requestId`를 설정합니다.
  - 허용되지 않은 header 값은 새 값으로 대체합니다.
  - 요청 종료 후 이전 MDC 맵을 복원합니다.
- `config/async/MdcTaskDecorator`
  - 비동기 작업 스레드로 MDC를 복사합니다.
- `src/main/resources/logback-spring.xml`
  - 로그 패턴에 `traceId`, `requestId`를 포함합니다.

## 헤더 규약

- 요청 헤더
  - `X-Trace-Id`
  - `X-Request-Id`
- 응답 헤더
  - `X-Trace-Id`
  - `X-Request-Id`

들어온 값이 비어 있거나 허용 문자 규칙을 벗어나면 서버가 새 식별자를 발급합니다.

## 실패 응답과의 연결

`GlobalExceptionHandler`는 실패 응답에 `traceId`를 포함합니다. 운영 중에는 응답의 `traceId`와 애플리케이션 로그의 MDC 값을 연결해 원인 추적에 사용합니다.

## 비동기 전파 원칙

- 호출 스레드의 MDC를 작업 스레드로 복사합니다.
- 작업이 끝나면 원래 스레드의 MDC 상태를 복원합니다.
- 다른 필터나 라이브러리가 넣은 MDC 키를 함부로 지우지 않습니다.

## 관련 문서

- [에러 처리 가이드](./error-handling.md)
- [아키텍처 경계 원칙](./architecture-boundaries.md)
