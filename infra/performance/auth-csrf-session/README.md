# AUTH-006 local CSRF session baseline

이 harness는 익명 `GET /api/auth/csrf`의 fresh-client와 same-session cohort를 동일한 요청 수로 비교한다. Production 또는 외부 endpoint를 호출하지 않고, 고정된 isolated Compose project와 loopback-only 임의 포트를 사용한다.

Tomcat session metric은 measurement overlay에서만 `SERVER_TOMCAT_MBEANREGISTRY_ENABLED=true`로 활성화한다. 실제 runtime의 `/actuator/prometheus`에서 metric 이름을 발견하며 Production 기본 설정이나 metric contract를 변경하지 않는다.

```powershell
python infra/performance/auth-csrf-session/measure_auth_csrf_sessions.py --validate-only
python infra/performance/auth-csrf-session/measure_auth_csrf_sessions.py --run
```

기본 workload는 cohort당 50회와 reused-session warm-up 3회다. `--request-count`는 10~200 범위에서만 허용한다. aggregate JSON은 OS 임시 디렉터리에만 기록하며 response body, CSRF token, cookie와 session ID는 저장하거나 출력하지 않는다.

실패 후 isolated project 정리가 필요하면 다음을 실행한다.

```powershell
python infra/performance/auth-csrf-session/measure_auth_csrf_sessions.py --cleanup
```

이 결과는 session creation 특성 확인용 local baseline이며 Production capacity, 비용, SLO 또는 rate-limit threshold 근거로 단독 사용하지 않는다.
