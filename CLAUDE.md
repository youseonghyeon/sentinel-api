# Sentinel API - Project Overview

## 목적

Sentinel API는 **토큰 기반 인증 서비스**다. 외부 애플리케이션(클라이언트)이 발급된 토큰이 유효한지(만료 여부 등)를 REST API로 체크할 수 있다. 매니저는 Thymeleaf 기반 웹 UI로
사용자, 앱, 관리자를 관리한다.

---

## 핵심 개념

### 사용자 흐름 (REST)

- 외부 클라이언트가 `X-Client-Id` 헤더(앱 식별자)와 `token` 파라미터를 보낸다
- 서버는 앱 ID를 내부 ID로 해석하고, 해당 토큰의 유효성과 만료 여부를 검사한다
- 토큰이 존재하지 않으면 `INVALID_TOKEN` (401), 만료되었으면 `EXPIRED_TOKEN` (401)을 반환한다
- 정상이면 200 OK를 반환한다

### `/login/token` vs `/check/token` 차이

| 엔드포인트 | 토큰 검사 | LoginHistory 기록 |
|-----------|---------|-----------------|
| `/login/token` | O | O (IP 포함) |
| `/check/token` | O | X |

### 매니저 흐름 (Thymeleaf)

- 매니저는 웹 UI를 통해 로그인한다
- 토큰은 매니저만 생성할 수 있으며, **자동 생성** (직접 입력 불가)
- 앱 등록/관리, 신규 관리자 추가가 가능하다

---

## 기술 스택

| 항목    | 내용                                   |
|-------|--------------------------------------|
| 언어    | Kotlin 2.x                           |
| 프레임워크 | Spring Boot 4.x                      |
| JDK   | Java 21                              |
| DB    | PostgreSQL                           |
| 보안    | Spring Security (BCrypt, RSA JWT 예정) |
| 템플릿   | Thymeleaf (매니저 UI)                   |
| 빌드    | Gradle (Kotlin DSL)                  |

---

## 도메인 모델

### `App` - 등록된 외부 애플리케이션

| 필드            | 설명                                    |
|---------------|---------------------------------------|
| `id`          | PK (자동 생성)                            |
| `name`        | 앱 이름                                  |
| `description` | 앱 설명                                  |
| `appId`       | 앱 고유 식별자 (클라이언트가 X-Client-Id로 전달하는 값) |

### `Token` - 앱에 속한 인증 토큰

| 필드            | 설명                              |
|---------------|---------------------------------|
| `id`          | PK                              |
| `application` | 소속 App (ManyToOne)              |
| `tokenStr`    | 자동 생성된 토큰 문자열                   |
| `expireDate`  | 만료일 (LocalDate) — 매니저가 설정, 추후 정의 |

- `app_id + token_str` 조합에 유니크 제약 있음
- 토큰은 매니저 UI에서 생성 요청 시 서버가 자동 생성

### `Manager` - 관리자 계정

| 필드         | 설명        |
|------------|-----------|
| `id`       | PK        |
| `username` | 로그인 ID    |
| `password` | 암호화된 비밀번호 |

### `LoginHistory` - 인증 시도 로그

| 필드      | 설명         |
|---------|------------|
| `id`    | PK         |
| `token` | 사용된 토큰 문자열 |
| `appId` | 앱 식별자      |
| `ip`    | 요청 IP 주소   |

---

## API 구조

### REST (사용자용)

**Base:** `/api/v1/auth`

| Method | Path                       | 설명                          |
|--------|----------------------------|-----------------------------|
| POST   | `/api/v1/auth/login/token` | 토큰 인증 + LoginHistory 기록 (IP 포함) |
| POST   | `/api/v1/auth/check/token` | 토큰 유효성 검사만 수행 (이력 미기록)      |

**공통 헤더/파라미터:**

- `X-Client-Id` (Header, required): 앱 식별자
- `token` (Query Param, required): 검사할 토큰 문자열

### Thymeleaf (매니저용)

**로그인 페이지 (`/`)**
- 화면 중앙에 ID, PW 입력 필드 + 로그인 버튼만 존재
- 로그인 성공 시 대시보드로 이동

**대시보드 레이아웃 (로그인 후)**
- 왼쪽: 사이드바 탭 메뉴
- 오른쪽: 탭에 따라 변경되는 콘텐츠 화면

**사이드바 탭 구성**

| 탭 이름 | 설명 |
|--------|------|
| 사용자 등록 / 매니저 등록 | 새 토큰(사용자) 자동 생성 등록, 새 매니저 계정 추가 |
| 사용자 관리 | 등록된 토큰(사용자) 목록 조회 및 관리 |
| 사용자 히스토리 | LoginHistory 조회 (IP, 토큰, 앱 식별자 등) |

---

## 에러 처리

| ErrorCode             | HTTP | 메시지                   |
|-----------------------|------|-----------------------|
| `INVALID_APPLICATION` | 401  | 유효하지 않은 애플리케이션 키입니다. |
| `INVALID_TOKEN`       | 401  | 유효하지 않은 토큰입니다.       |
| `EXPIRED_TOKEN`       | 401  | 만료된 토큰입니다.            |
| `MANAGER_NOT_FOUND`   | 404  | 사용자를 찾을 수 없습니다.       |

에러 응답 형식:

```json
{
  "code": "EXPIRED_TOKEN",
  "message": "만료된 토큰입니다."
}
```

---

## 보안 설정

- `/api/v1/auth/**`, `/actuator/**` → 인증 없이 접근 가능 (permitAll)
- 나머지 → 인증 필요
- CSRF 비활성화
- 비밀번호: BCrypt 인코딩
- JWT: 미사용 (세션 로그인만 사용)

---

## 환경 설정 (application.yaml)

| 설정             | 기본값                                         | 환경변수             |
|----------------|---------------------------------------------|------------------|
| DB URL         | `jdbc:postgresql://localhost:5432/sentinel` | `DB_URL`         |
| DB Username    | `sentinel`                                  | `DB_USERNAME`    |
| DB Password    | `sentinel`                                  | `DB_PASSWORD`    |
| Server Port    | `8080`                                      | `SERVER_PORT`    |
| Actuator Port  | `8081`                                      | `ACTUATOR_PORT`  |
| DDL Mode       | `update`                                    | `JPA_DDL_AUTO`   |

---

## 미완성 / 구현 예정 항목

- [ ] `TokenAuthService`: 토큰 조회 → 만료일 비교(`expireDate < today`) → INVALID_TOKEN / EXPIRED_TOKEN 분기 구현
- [ ] 토큰 자동 생성 로직 (UUID or 랜덤 문자열, 만료일 정책 결정 필요)
- [ ] `MangerController`: 매니저 UI 엔드포인트 구현
- [ ] `ManagerService`: 매니저 인증 로직 구현
- [ ] `LoginHistory` 기록: `/login/token` 호출 시 IP 저장

---

## 패키지 구조

```
com.seonghyeon.sentinelapi
├── SentinelApiApplication.kt
├── common/exception/
│   ├── SentinelException.kt
│   └── GlobalExceptionHandler.kt
├── config/
│   └── SecurityConfig.kt
├── controller/auth/
│   ├── UserController.kt       # REST API (사용자용)
│   └── MangerController.kt     # Thymeleaf (매니저용, 미구현)
├── domain/
│   ├── App.kt
│   ├── Token.kt
│   ├── Manager.kt
│   └── LoginHistory.kt
├── repository/
│   ├── AppRepository.kt
│   ├── TokenRepository.kt
│   ├── ManagerRepository.kt
│   └── LoginHistoryRepository.kt
└── service/
    ├── ApplicationService.kt   # 앱 ID 조회
    ├── TokenAuthService.kt     # 토큰 인증 로직
    └── ManagerService.kt       # 매니저 인증 (미구현)
```
