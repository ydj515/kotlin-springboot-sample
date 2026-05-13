# 에러 처리 가이드

## 개요

이 프로젝트는 도메인/애플리케이션 계층에서 의미 있는 예외를 던지고, API 계층에서는 `GlobalExceptionHandler`가 이를 공통 응답 포맷으로 변환합니다.

## 핵심 구성 요소

- `ApiResult.Failure`
  - 공통 실패 응답 포맷입니다.
- `ResultCode`
  - HTTP 상태와 메시지 코드를 함께 정의합니다.
- `GlobalExceptionHandler`
  - validation, not found, already exists, invalid request, unexpected exception을 공통 처리합니다.

## 현재 처리하는 예외 예시

- `OrderNotFoundException`
- `InvalidOrderItemException`
- `UserAlreadyException`
- `UserException`

## 원칙

- controller는 예외를 직접 잡아 분기하지 않습니다.
- 도메인 예외는 HTTP 상태코드를 직접 몰라도 됩니다.
- presentation 계층의 advice가 `ApiResult`로 변환합니다.

## 예시

- 주문이 없을 때:
  - `404`, `result = failure`
- 주문 item이 비어 있거나 수량/가격이 잘못됐을 때:
  - `400`, `result = failure`
- username 중복 회원가입일 때:
  - `409`, `result = failure`

## 관련 문서

- [아키텍처 경계 원칙](./architecture-boundaries.md)
- [JPA 샘플 개요](./jpa-sample-overview.md)
