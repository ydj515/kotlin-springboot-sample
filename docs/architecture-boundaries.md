# 아키텍처 경계 원칙

## 개요

이 문서는 현재 프로젝트의 `presentation / application / domain / infrastructure` 경계 원칙을 정리합니다.

## 이 문서를 보면 좋은 경우

- 로직을 어느 패키지에 둘지 헷갈릴 때
- `UseCase`, `policy`, 엔티티 메서드, 저장소 포트의 역할 차이를 정리하고 싶을 때
- JPA 샘플 도메인을 어떤 의도로 나눴는지 알고 싶을 때

## 핵심 원칙

### 1. `application`은 흐름 제어와 트랜잭션을 담당합니다

- 클래스명은 `*UseCase`를 사용합니다.
- `UseCase`는 `command`를 받고 흐름을 조합합니다.
- 트랜잭션 경계를 선언하고 `result` DTO를 반환합니다.
- 세부 검증 규칙과 정책 판단은 직접 많이 가지지 않습니다.

예:

- `PostUseCase`
- `UserUseCase`
- `OrderUseCase`

### 2. 실제 규칙은 `domain`에 둡니다

- 엔티티 자신의 상태 변경 규칙은 엔티티 메서드로 둡니다.
- 재사용 가능한 검증/판단 규칙은 `policy`로 분리합니다.

예:

- `PostAuthorPolicy`
- `UserRegistrationPolicy`
- `OrderItemPolicy`

### 3. `presentation`은 HTTP 입출력만 담당합니다

- request DTO를 `command`로 변환합니다.
- `result`를 response DTO로 변환합니다.
- HTTP 상태 코드는 `presentation`에서 결정합니다.

### 4. 저장소 포트는 `domain`, 구현은 Spring Data JPA가 담당합니다

- 현재 프로젝트는 repository interface를 `domain/*/repository`에 둡니다.
- 실제 구현은 Spring Data JPA가 생성하므로 별도 adapter 클래스는 두지 않았습니다.
- 그래도 repository의 의미는 여전히 “도메인 계층이 바라보는 포트”로 취급합니다.

### 5. `infrastructure`는 프레임워크/보안/부트스트랩 코드를 담당합니다

- JWT 발급과 검증
- Security filter
- 초기 샘플 데이터 적재

## 의존 방향

허용 방향:

- `presentation -> application`
- `application -> domain`
- `infrastructure -> application/domain`
- `config -> infrastructure/application`

피해야 하는 방향:

- `domain -> application`
- `domain -> presentation`
- `application -> presentation`

## 현재 프로젝트에서 특히 보는 포인트

- `Post`
  - application이 dirty checking 흐름만 제어하고, soft delete는 엔티티가 담당합니다.
- `User`
  - 중복 username 판단은 `UserRegistrationPolicy`가 담당합니다.
- `Order`
  - aggregate root가 `OrderLine` 목록과 `totalAmount`를 관리합니다.
  - `OrderUseCase`는 buyer 조회, item validation, 저장 순서만 조합합니다.

## 관련 문서

- [프로젝트 구조 가이드](./project-structure.md)
- [JPA 사용 기준 가이드](./jpa-pattern-selection-guide.md)
