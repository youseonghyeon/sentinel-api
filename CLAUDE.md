# Sentinel API - Project Overview

## 목적

Sentinel API는 **토큰 기반 인증 서비스**다. 외부 애플리케이션(클라이언트)이 발급된 토큰이 유효한지(만료 여부, 기기 등록 여부 등)를 REST API로 체크할 수 있다. 매니저는 Thymeleaf 기반 웹 UI로 사용자, 앱, 관리자를 관리한다.

---

## 핵심 개념

### 사용자 흐름 (REST)

- 외부 클라이언트가 `X-Client-Id` 헤더(앱 식별자)와 `token` 파라미터를 보낸다
- 서버는 앱 ID를 내부 ID로 해석하고, 해당 토큰의 유효성과 만료 여부를 검사한다
- `maxDeviceCount > 0`인 토큰은 `X-Device-Id` 헤더(Windows Machine GUID)가 필수이며, 등록된 기기 수를 초과하면 거부된다
- 토큰이 존재하지 않으면 `INVALID_TOKEN` (401), 만료되었으면 `EXPIRED_TOKEN` (401)을 반환한다
- 정상이면 200 OK를 반환한다

### `/login/token` vs `/check/token` 차이

| 엔드포인트 | 토큰 검사 | 기기 등록 | LoginHistory 기록 |
|-----------|---------|---------|-----------------|
| `/login/token` | O | O (신규 기기 등록 + 한도 체크) | O (IP, deviceId 포함) |
| `/check/token` | O | O (등록 여부 확인 + lastSeenAt 갱신) | X |

### PC 개수 제한

- 토큰 생성 시 `maxDeviceCount` 설정 (기본값 1, 0 = 무제한)
- `DeviceRegistration` 테이블로 등록된 기기를 관리
- 클라이언트는 프로그램 구동 중 주기적으로 `/check/token`을 호출하며, `DEVICE_LOGGED_OUT` 수신 시 프로그램을 종료한다
- 사용자는 `/api/v1/auth/devices` API로 기기 목록 조회 및 개별 로그아웃이 가능하다

### 매니저 흐름 (Thymeleaf)

- 매니저는 웹 UI를 통해 로그인한다
- 토큰은 매니저만 생성할 수 있으며, **자동 생성** (직접 입력 불가)
- 앱 등록/관리, 신규 관리자 추가, 기기 관리가 가능하다

---

## 기술 스택

| 항목    | 내용                          |
|-------|-------------------------------|
| 언어    | Kotlin 2.x                    |
| 프레임워크 | Spring Boot 4.x               |
| JDK   | Java 21                       |
| DB    | PostgreSQL                    |
| 보안    | Spring Security (BCrypt)      |
| 템플릿   | Thymeleaf (매니저 UI)            |
| 빌드    | Gradle (Kotlin DSL)           |

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

| 필드               | 설명                              |
|------------------|-------------------------------|
| `id`             | PK                            |
| `application`    | 소속 App (ManyToOne)            |
| `tokenStr`       | 자동 생성된 토큰 문자열                 |
| `expireDate`     | 만료일 (LocalDate, KST 기준 비교)    |
| `maxDeviceCount` | 허용 PC 최대 수 (기본값 1, 0 = 무제한)  |

- `app_id + token_str` 조합에 유니크 제약 있음
- 토큰은 매니저 UI에서 생성 요청 시 서버가 자동 생성

### `DeviceRegistration` - 등록된 기기

| 필드             | 설명                              |
|----------------|-------------------------------|
| `id`           | PK                            |
| `token`        | 소속 Token (ManyToOne)          |
| `deviceId`     | Windows Machine GUID          |
| `registeredAt` | 최초 로그인 시각 (UTC)               |
| `lastSeenAt`   | 마지막 check/token 호출 시각 (UTC)   |

- `(token_id, device_id)` 유니크 제약

### `LoginHistory` - 인증 시도 로그

| 필드         | 설명                    |
|------------|----------------------|
| `id`       | PK                   |
| `token`    | 사용된 토큰 문자열           |
| `appId`    | 앱 식별자                |
| `ip`       | 요청 IP 주소             |
| `deviceId` | Machine GUID (nullable) |
| `createdAt`| 기록 시각 (UTC)          |

### `Manager` - 관리자 계정

| 필드         | 설명        |
|------------|-----------|
| `id`       | PK        |
| `username` | 로그인 ID    |
| `password` | 암호화된 비밀번호 |

---

## API 구조

### REST (사용자용)

**Base:** `/api/v1/auth`

#### 토큰 인증

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/login/token` | 토큰 인증 + 기기 등록 + LoginHistory 기록 |
| POST | `/api/v1/auth/check/token` | 토큰 및 기기 등록 상태 확인 (이력 미기록) |

**헤더/파라미터:**

| 이름 | 위치 | 필수 | 설명 |
|---|---|---|---|
| `X-Client-Id` | Header | 필수 | 앱 식별자 |
| `X-Device-Id` | Header | maxDeviceCount > 0 이면 필수 | Windows Machine GUID |
| `token` | Query Param | 필수 | 검사할 토큰 문자열 |

#### 기기 관리

인증: `X-Client-Id` + `token` (토큰 본인 확인)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/auth/devices` | 내 토큰에 등록된 기기 목록 조회 |
| DELETE | `/api/v1/auth/devices/{deviceId}` | 특정 기기 로그아웃 |
| DELETE | `/api/v1/auth/logout` | 현재 기기 로그아웃 (`X-Device-Id` 헤더 필수) |

### Thymeleaf (매니저용)

**로그인 페이지 (`/login`)**

**대시보드 사이드바 탭**

| 탭 | 설명 |
|---|---|
| 사용자/매니저 등록 | 토큰 자동 생성(최대 PC 수 포함), 매니저 계정 추가 |
| 사용자 관리 | 토큰 목록, 만료일/최대 PC 수 수정, 기기 관리 |
| 애플리케이션 관리 | 앱 등록/삭제 |
| 사용자 히스토리 | LoginHistory 조회 (검색, 페이징, KST 표시) |
| 매니저 API Key 관리 | REST API용 API Key 발급/삭제 |

---

## 에러 처리

| ErrorCode               | HTTP | 메시지                    |
|-------------------------|------|--------------------------|
| `INVALID_APPLICATION`   | 401  | 유효하지 않은 애플리케이션 키입니다. |
| `INVALID_TOKEN`         | 401  | 유효하지 않은 토큰입니다.        |
| `EXPIRED_TOKEN`         | 401  | 만료된 토큰입니다.             |
| `DEVICE_ID_REQUIRED`    | 400  | PC 식별 정보가 필요합니다.       |
| `DEVICE_LIMIT_EXCEEDED` | 429  | 허용된 PC 수를 초과하였습니다.    |
| `DEVICE_LOGGED_OUT`     | 401  | 기기가 로그아웃되었습니다.        |
| `MANAGER_NOT_FOUND`     | 404  | 사용자를 찾을 수 없습니다.        |
| `INVALID_API_KEY`       | 401  | 유효하지 않은 API 키입니다.      |

에러 응답 형식:

```json
{
  "code": "EXPIRED_TOKEN",
  "message": "만료된 토큰입니다."
}
```

---

## 시간대 정책

- 서버(K8s), DB, 로그: **UTC**
- 토큰 만료일(`expireDate`) 비교: **KST** 기준 (`Asia/Seoul`)
- 매니저 UI 일시 표시: **KST** 변환 후 표시
- REST API 응답 timestamp: **UTC** (`LocalDateTime`)

---

## 보안 설정

- `/api/v1/auth/**`, `/api/v1/manager/**`, `/actuator/**` → 인증 없이 접근 가능 (permitAll)
- 나머지 → 세션 인증 필요
- CSRF 비활성화
- 비밀번호: BCrypt 인코딩

---

## 환경 설정 (application.yaml)

| 설정           | 기본값                                         | 환경변수            |
|--------------|---------------------------------------------|-----------------|
| DB URL       | `jdbc:postgresql://localhost:5432/sentinel` | `DB_URL`        |
| DB Username  | `sentinel`                                  | `DB_USERNAME`   |
| DB Password  | `sentinel`                                  | `DB_PASSWORD`   |
| Server Port  | `8080`                                      | `SERVER_PORT`   |
| Actuator Port| `8081`                                      | `ACTUATOR_PORT` |
| DDL Mode     | `update`                                    | `JPA_DDL_AUTO`  |

---

## 패키지 구조

```
com.seonghyeon.sentinelapi
├── SentinelApiApplication.kt
├── common/exception/
│   ├── SentinelException.kt        # ErrorCode enum 포함
│   └── GlobalExceptionHandler.kt
├── config/
│   ├── SecurityConfig.kt
│   ├── ApiKeyAuthFilter.kt
│   └── DataInitializer.kt
├── controller/
│   ├── ManagerApiController.kt     # REST API (매니저용, API Key 인증)
│   └── auth/
│       ├── UserController.kt       # REST API (사용자용)
│       ├── MangerController.kt     # Thymeleaf (매니저 UI)
│       └── dto/
│           ├── UserLoginResponse.kt
│           ├── LoginHistoryView.kt
│           └── DeviceView.kt
├── domain/
│   ├── App.kt
│   ├── Token.kt                    # maxDeviceCount 포함
│   ├── Manager.kt
│   ├── LoginHistory.kt             # deviceId 포함
│   ├── DeviceRegistration.kt
│   └── ApiKey.kt
├── repository/
│   ├── AppRepository.kt
│   ├── TokenRepository.kt
│   ├── ManagerRepository.kt
│   ├── LoginHistoryRepository.kt
│   ├── DeviceRegistrationRepository.kt
│   └── ApiKeyRepository.kt
├── service/
│   ├── ApplicationService.kt
│   ├── TokenAuthService.kt
│   ├── LoginHistoryService.kt
│   ├── DeviceService.kt
│   ├── ManagerService.kt
│   └── ApiKeyService.kt
└── utils/
    └── RequestHelper.kt
```
