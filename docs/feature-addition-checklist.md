# 새 기능 추가 체크리스트

## 개요

이 문서는 새 기능을 추가할 때 로직을 `UseCase / Policy / Entity / Repository` 중 어디에 둘지 빠르게 판단하기 위한 체크리스트입니다.

## 빠른 판단 순서

### 1. `UseCase`에 둘 것인가

- 이 로직이 유스케이스 실행 순서를 조합하는가
- 트랜잭션을 열고 닫는가
- request를 `command`로, 도메인 결과를 `result`로 바꾸는가

그렇다면 `application/*UseCase`를 우선 검토합니다.

### 2. `Policy`에 둘 것인가

- 검증 규칙인가
- 허용/비허용 판단 규칙인가
- 여러 곳에서 재사용될 수 있는가
- 저장소 조회 없이 판정 가능한가

그렇다면 `domain/*/policy`를 우선 검토합니다.

### 3. 엔티티 메서드에 둘 수 있는가

- 자기 상태만으로 변경/판단 가능한가
- aggregate 내부 불변조건인가

그렇다면 엔티티 메서드가 먼저입니다.

### 4. 저장소 포트가 필요한가

- 도메인 규칙 수행에 조회/저장이 필요한가
- 도메인이 JPA 구현 세부사항을 직접 알면 안 되는가

그렇다면 `domain/*/repository`에 메서드를 추가합니다.

## 현재 프로젝트 예시

### 회원가입 중복 username 확인

- 위치:
  - `UserRegistrationPolicy`
- 이유:
  - 등록 정책 성격이 분명하기 때문

### 주문 생성

- request -> `CreateOrderCommand`
- flow 조합 -> `OrderUseCase`
- item 검증 -> `OrderItemPolicy`
- aggregate 상태 반영 -> `Order.replaceLines(...)`

## 금지 신호

- `UseCase`에 검증 `if/else`가 계속 쌓인다
- `domain`이 `presentation` DTO를 import 한다
- repository가 request/response DTO를 직접 받는다

## 관련 문서

- [아키텍처 경계 원칙](./architecture-boundaries.md)
- [프로젝트 구조 가이드](./project-structure.md)
