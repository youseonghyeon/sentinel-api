# Sentinel API - Python Client Integration Guide

## 개요

이 문서는 Windows Python 프로그램에서 Sentinel API를 연동하는 방법을 설명한다.

---

## Machine GUID 수집

Windows의 Machine GUID는 레지스트리에서 읽는다. UAC 권한이 필요 없으며, 일반 사용자 권한으로 실행된다.

```python
import winreg

def get_machine_guid() -> str:
    key = winreg.OpenKey(
        winreg.HKEY_LOCAL_MACHINE,
        r"SOFTWARE\Microsoft\Cryptography"
    )
    guid, _ = winreg.QueryValueEx(key, "MachineGuid")
    winreg.CloseKey(key)
    return guid
```

---

## API 기본 정보

| 항목 | 값 |
|------|-----|
| Base URL | `http://<서버주소>/api/v1/auth` |
| 공통 헤더 | `X-Client-Id: <앱 식별자>` |
| 공통 파라미터 | `token=<토큰 문자열>` |

---

## 엔드포인트 정의

### 1. 로그인 — `POST /login/token`

프로그램 **최초 실행 시** 호출한다. LoginHistory에 기록되며 기기를 등록한다.

**요청 헤더:**
```
X-Client-Id: <appId>
X-Device-Id: <Machine GUID>
```

**요청 파라미터:**
```
token=<토큰 문자열>
```

**응답 (200 OK):**
```json
{
  "tokenStr": "...",
  "expireDate": "2026-12-31"
}
```

**에러 응답:**

| 상태코드 | code | 의미 |
|----------|------|------|
| 401 | `INVALID_APPLICATION` | 잘못된 X-Client-Id |
| 401 | `INVALID_TOKEN` | 존재하지 않는 토큰 |
| 401 | `EXPIRED_TOKEN` | 만료된 토큰 |
| 400 | `DEVICE_ID_REQUIRED` | X-Device-Id 헤더 누락 (토큰에 PC 제한이 있는 경우) |
| 429 | `DEVICE_LIMIT_EXCEEDED` | 허용된 PC 수 초과 |

---

### 2. 상태 체크 (Heartbeat) — `POST /check/token`

프로그램 **실행 중 주기적으로** 호출한다. LoginHistory에 기록되지 않는다.

**요청 헤더:**
```
X-Client-Id: <appId>
X-Device-Id: <Machine GUID>
```

**요청 파라미터:**
```
token=<토큰 문자열>
```

**응답 (200 OK):** 로그인과 동일

**에러 응답:**

| 상태코드 | code | 의미 | 프로그램 동작 |
|----------|------|------|--------------|
| 401 | `DEVICE_LOGGED_OUT` | 관리자가 이 기기를 로그아웃함 | **프로그램 즉시 종료** |
| 401 | `EXPIRED_TOKEN` | 토큰 만료 | 프로그램 종료 |
| 401 | `INVALID_TOKEN` | 토큰 무효 | 프로그램 종료 |

---

### 3. 로그아웃 — `DELETE /logout`

프로그램 **정상 종료 시** 호출한다. 기기 등록 정보를 삭제한다.

**요청 헤더:**
```
X-Client-Id: <appId>
X-Device-Id: <Machine GUID>
```

**요청 파라미터:**
```
token=<토큰 문자열>
```

**응답:** `204 No Content`

---

### 4. 기기 목록 조회 — `GET /devices`

현재 등록된 기기 목록을 조회한다. (선택적으로 사용)

**요청 헤더:**
```
X-Client-Id: <appId>
```

**요청 파라미터:**
```
token=<토큰 문자열>
```

**응답 (200 OK):**
```json
[
  {
    "deviceId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "registeredAt": "2026-04-01T09:00:00",
    "lastSeenAt": "2026-04-11T14:30:00"
  }
]
```

---

### 5. 기기 삭제 — `DELETE /devices/{deviceId}`

특정 기기 등록 정보를 삭제한다. (선택적으로 사용)

**요청 헤더:**
```
X-Client-Id: <appId>
```

**요청 파라미터:**
```
token=<토큰 문자열>
```

**응답:** `204 No Content`

---

## 전체 프로그램 라이프사이클

```
[프로그램 시작]
      │
      ▼
POST /login/token
   성공(200) ─────────────────────────────────────────┐
   실패(4xx) → 에러 표시 후 종료                        │
                                                       │
                                                  [프로그램 실행 중]
                                                       │
                                              (주기적으로 — 시간 단위)
                                                       │
                                                       ▼
                                              POST /check/token
                                              성공(200) → 계속 실행
                                              DEVICE_LOGGED_OUT(401) → 즉시 종료
                                              EXPIRED_TOKEN(401) → 종료
                                                       │
                                              [프로그램 정상 종료]
                                                       │
                                                       ▼
                                              DELETE /logout
```

---

## Python 구현 예시

```python
import winreg
import requests
import time
import sys

BASE_URL = "http://<서버주소>/api/v1/auth"
APP_ID = "<앱 식별자>"
TOKEN = "<토큰 문자열>"
HEARTBEAT_INTERVAL = 3600  # 1시간 (초)


def get_machine_guid() -> str:
    key = winreg.OpenKey(
        winreg.HKEY_LOCAL_MACHINE,
        r"SOFTWARE\Microsoft\Cryptography"
    )
    guid, _ = winreg.QueryValueEx(key, "MachineGuid")
    winreg.CloseKey(key)
    return guid


def login(machine_guid: str) -> None:
    resp = requests.post(
        f"{BASE_URL}/login/token",
        params={"token": TOKEN},
        headers={
            "X-Client-Id": APP_ID,
            "X-Device-Id": machine_guid,
        }
    )
    if resp.status_code != 200:
        error = resp.json()
        print(f"로그인 실패: {error['code']} - {error['message']}")
        sys.exit(1)


def check(machine_guid: str) -> bool:
    """True: 계속 실행, False: 종료"""
    resp = requests.post(
        f"{BASE_URL}/check/token",
        params={"token": TOKEN},
        headers={
            "X-Client-Id": APP_ID,
            "X-Device-Id": machine_guid,
        }
    )
    if resp.status_code == 200:
        return True
    error = resp.json()
    print(f"체크 실패: {error['code']} - {error['message']}")
    return False


def logout(machine_guid: str) -> None:
    requests.delete(
        f"{BASE_URL}/logout",
        params={"token": TOKEN},
        headers={
            "X-Client-Id": APP_ID,
            "X-Device-Id": machine_guid,
        }
    )


def main():
    machine_guid = get_machine_guid()

    login(machine_guid)
    print("로그인 성공")

    last_check = time.time()

    try:
        while True:
            # 실제 프로그램 작업 수행
            do_work()

            # Heartbeat
            if time.time() - last_check >= HEARTBEAT_INTERVAL:
                if not check(machine_guid):
                    print("기기 인증 실패 — 프로그램 종료")
                    sys.exit(1)
                last_check = time.time()

    except KeyboardInterrupt:
        pass
    finally:
        logout(machine_guid)
        print("로그아웃 완료")


def do_work():
    # 실제 비즈니스 로직
    time.sleep(1)


if __name__ == "__main__":
    main()
```

---

## 에러 코드 전체 목록

| code | HTTP | 설명 |
|------|------|------|
| `INVALID_APPLICATION` | 401 | `X-Client-Id`가 등록되지 않은 앱 |
| `INVALID_TOKEN` | 401 | 존재하지 않는 토큰 |
| `EXPIRED_TOKEN` | 401 | 만료일이 지난 토큰 |
| `DEVICE_ID_REQUIRED` | 400 | PC 제한이 있는 토큰인데 `X-Device-Id` 헤더 없음 |
| `DEVICE_LIMIT_EXCEEDED` | 429 | 허용 PC 수 초과 (다른 PC에서 먼저 로그인 중) |
| `DEVICE_LOGGED_OUT` | 401 | 관리자가 이 기기를 강제 로그아웃함 |

---

## 주의사항

- `X-Device-Id`는 Windows Machine GUID를 사용한다. 이 값은 OS 재설치 시 변경된다.
- 토큰 만료일은 한국 시간(KST) 기준으로 판단된다.
- 관리자가 PC를 로그아웃하면 다음 `/check/token` 호출 시 `DEVICE_LOGGED_OUT`이 반환된다. 프로그램은 이 응답을 받으면 즉시 종료해야 한다.
- `maxDeviceCount = 0`이면 PC 수 제한이 없다.
