# JMeter API 조회 부하 테스트

이 디렉터리는 인증이 필요한 주요 조회 API를 같은 조건으로 반복 호출하고, 응답 시간·처리량·오류율을 비교하기 위한 JMeter 5.6.3 부하 생성기다.

이 구성의 목적은 로컬 회귀 비교와 캐시 적용 전후의 기준선 측정이다. 실제 사용자 트래픽 비율을 재현하거나 운영 서버의 최대 수용량을 확정하는 도구는 아니다. 서버 내부 지표는 [모니터링 README](../monitoring/README.md)의 Prometheus·Grafana 구성에서 확인한다.

## 1. 처음 보는 사람을 위한 전체 흐름

```text
Taskfile
   └─ performance/jmeter/run.sh
          ├─ users.csv 사전 검증
          ├─ /health 연결 확인
          └─ api-read-load-test.jmx 실행
                    │
                    ├─ JWT로 조회 API 5개 반복 호출
                    ├─ HTTP 200 검증
                    └─ JSON isSuccess=true 검증
                              │
                              └─ result.jtl + jmeter.log + HTML report
```

부하 테스트와 모니터링을 함께 실행할 때는 다음 순서를 사용한다.

```text
MySQL·Redis → Spring Boot → 개발 token → Prometheus·Grafana → Smoke → Load
```

## 2. 팀원이 직접 준비할 것

| 준비 항목 | 확인 방법 | 비고 |
| --- | --- | --- |
| Java 21 | `java -version` | 앱 실행과 개발 token 발급 |
| JMeter 5.6.3 | `jmeter --version` | PATH에 없으면 `JMETER_BIN` 지정 |
| Task | `task --version` | 저장소의 공통 실행 진입점 |
| curl | `curl --version` | 실행 전 `/health` 확인 |
| Docker daemon | `docker info` | MySQL·Redis와 모니터링 컨테이너 |
| xmllint | `xmllint --version` | JMX 정적 문법 검사용, 실제 실행 필수는 아님 |

팀원이 직접 결정하거나 확인해야 하는 값은 다음과 같다.

- 테스트할 commit SHA와 데이터 상태
- 사용할 활성 로컬 회원 ID. 기본값은 더미 회원 `9001`
- 실행할 시나리오와 실행 시간대
- 승인된 대상 주소인지 여부
- token이 실행 도중 만료되지 않는지 여부
- 같은 조건을 비교할 때 Thread·Ramp-up·실행 시간·Think Time을 바꾸지 않았는지 여부

실제 운영 access token, refresh token, OAuth token이나 `.env` 값은 사용하지 않는다.

## 3. 빠른 실행

Docker Desktop 등 Docker daemon을 먼저 실행한다.

터미널 A에서 MySQL과 Redis를 시작한다.

```bash
task docker-up
```

터미널 B에서 Spring Boot를 실행한다.

```bash
JPA_SHOW_SQL=false task run
```

실제 측정에서는 SQL 전체 출력으로 인한 console I/O를 제외하기 위해 `JPA_SHOW_SQL=false`를 명시한다.

앱이 한 번 정상 기동해 local dummy migration이 적용된 뒤, 터미널 C에서 개발 token과 모니터링을 준비한다.

```bash
task performance-token
task monitoring-up
```

Smoke로 인증과 Assertion을 먼저 확인한다.

```bash
task performance-smoke
```

Smoke가 성공한 뒤에만 Load 또는 Spike를 실행한다.

```bash
task performance-load
task performance-spike
```

모니터링 설치·로그인·정상 확인은 [모니터링 README](../monitoring/README.md)를 따른다.

## 4. 파일별 책임

| 파일 | 역할 |
| --- | --- |
| `api-read-load-test.jmx` | 요청 순서, CSV token, Think Time, HTTP·JSON Assertion |
| `run.sh` | 시나리오 값 선택, 사전 조건 확인, JMeter CLI 실행과 결과 요약 |
| `users.csv.example` | 실제 비밀값이 없는 token CSV 형식 예시 |
| `users.csv` | 실행에 쓰는 실제 개발 token. 자동 생성되며 Git 제외 |
| `results/{run-id}/` | JTL·JMeter 로그·HTML report. Git 제외 |
| 저장소 `Taskfile.yml` | token 발급과 Smoke·Load·Spike의 짧은 사용자 명령 |

사용자는 보통 Task 명령만 실행한다. JMX나 긴 JMeter 옵션을 직접 입력할 필요가 없다.

## 5. 대상 API와 요청 모델

한 JMeter Thread는 다음 API를 순서대로 호출하고 실행 시간이 끝날 때까지 반복한다.

```text
GET /api/home
GET /api/routines/categories
GET /api/routines/templates
GET /api/chat/emoticons
GET /api/challenges
```

각 요청은 다음 두 조건을 모두 검사한다.

1. HTTP status가 `200`
2. JSON 응답의 `isSuccess`가 `true`

다섯 API는 JTL과 HTML report에서 서로 다른 sampler로 집계된다. API별 호출 수를 동일하게 얻기 위한 초기 기준선이며, 실제 서비스 트래픽 비율을 의미하지 않는다.

## 6. 개발 token

`task performance-token`은 local DB에서 회원을 조회하고 활성 상태를 확인한 뒤 14일짜리 개발 JWT를 발급한다.

```bash
task performance-token
```

다른 활성 로컬 회원을 사용할 때만 ID를 지정한다.

```bash
task performance-token MEMBER_ID=9002
```

결과는 화면에 출력하지 않고 다음 파일을 새 값으로 교체한다.

```csv
access_token
발급된-개발-token
```

파일 경로:

```text
performance/jmeter/users.csv
```

사전 조건:

- MySQL이 실행 중이어야 한다.
- Spring Boot가 최소 한 번 local 프로파일로 기동해 dummy migration이 적용되어야 한다.
- 지정한 회원이 존재하고 탈퇴하지 않은 활성 회원이어야 한다.

`users.csv`는 JMeter와 Prometheus가 같은 개발 token을 공유하는 원본이다. `task monitoring-up`은 첫 번째 token을 Git에서 제외된 `performance/monitoring/secrets/actuator-token`으로 복사한다.

외부에서 받은 승인된 비운영 token을 사용해야 할 때만 `users.csv.example`을 복사해 값을 직접 넣는다. token을 명령행 인자, JMX, README, 이슈 댓글이나 일반 로그에 넣지 않는다.

## 7. 시나리오

현재 Task 시나리오 값은 `performance/jmeter/run.sh`에 고정되어 있다.

| 시나리오 | Threads | Ramp-up | 실행 시간 | 요청 전 Think Time | 목적 |
| --- | ---: | ---: | ---: | ---: | --- |
| Smoke | 1 | 1초 | 20초 | 500ms | 인증·Assertion·report 확인 |
| Load | 5 | 10초 | 120초 | 500ms | 안정 부하 기준선 |
| Spike | 30 | 3초 | 60초 | 500ms | 급격한 동시 요청의 오류와 회복 확인 |

`JMETER_THREADS`, `JMETER_DURATION` 같은 환경변수는 현재 `run.sh`가 읽지 않는다. 기준 조건을 바꾸려면 측정 목적과 비교 가능성을 먼저 합의한 뒤 스크립트의 시나리오 값을 명시적으로 수정한다.

Spike는 Smoke와 Load가 정상인 뒤 실행한다. 한 번에 30 Threads로 올리기보다 필요하면 `5 → 10 → 20 → 30`처럼 별도 시나리오를 합의해 단계적으로 확인한다.

## 8. 실행 시 바꿀 수 있는 값

`run.sh`는 다음 환경변수만 읽는다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| `JMETER_BIN` | `jmeter` | JMeter 실행 파일 |
| `JMETER_BASE_URL` | `http://localhost:8080` | 마지막 `/`가 없는 API 주소 |
| `JMETER_USERS_FILE` | JMX 옆 `users.csv` | token CSV |
| `JMETER_RESULT_ROOT` | `performance/jmeter/results` | 실행 결과 상위 디렉터리 |
| `JMETER_ALLOW_NON_LOCAL` | `false` | 승인된 비로컬 주소의 기본 차단 해제 |

예:

```bash
JMETER_BIN=/opt/apache-jmeter-5.6.3/bin/jmeter task performance-smoke
```

비로컬 주소는 실수로 운영 서버에 부하를 보내지 않도록 기본 차단한다. 승인된 비운영 환경에서만 대상과 부하 수준을 확인한 뒤 두 값을 함께 명시한다.

```bash
JMETER_BASE_URL=https://approved-test.example.com \
JMETER_ALLOW_NON_LOCAL=true \
task performance-smoke
```

이 허용 값은 안전장치를 해제할 뿐 운영 실행 승인을 대신하지 않는다. 현재 Prometheus 구성은 host의 `8080`을 고정 대상으로 하므로 비로컬 JMeter 실행과 자동으로 연결되지 않는다.

JMX 자체는 `baseUrl`, `usersFile`, `threads`, `rampUpSeconds`, `durationSeconds`, `thinkTimeMillis`, `connectTimeoutMillis`, `responseTimeoutMillis` 속성을 지원한다. 평소에는 결과 저장 안전 옵션까지 포함한 `run.sh`를 사용한다.

## 9. 실행 전 자동 확인

`run.sh`는 HTTP 요청을 보내기 전에 다음을 확인한다.

- JMeter와 curl 명령 존재
- JMX와 token CSV 존재
- 대상 주소가 localhost인지, 비로컬 실행이 명시적으로 허용됐는지
- `{baseUrl}/health`가 3초 안에 응답하는지

JMX의 setUp Thread Group은 CSV header와 token 행을 다시 검사한다. CSV가 잘못되면 실제 API 요청을 보내기 전에 중단한다.

## 10. 결과와 정상 판정

매 실행은 timestamp와 임의 suffix가 붙은 새 `run-id`를 사용한다. 기존 결과를 덮어쓰거나 삭제하지 않는다.

```text
performance/jmeter/results/{run-id}/result.jtl
performance/jmeter/results/{run-id}/jmeter.log
performance/jmeter/results/{run-id}/report/index.html
```

HTML report의 Statistics 표에서 API별 p50·p95·p99, Throughput과 Error %를 확인한다. 그래프에서는 Active Threads, Connect Time, Latency, Transactions per Second와 오류 발생 시점을 함께 본다.

다음 조건을 모두 만족해야 정상 측정으로 채택한다.

1. 다섯 API sampler가 모두 존재한다.
2. Error %가 0이고 Assertion 실패가 없다.
3. HTTP 401, 403 또는 5xx가 없다.
4. 실행 중 token이 만료되지 않았다.
5. JMeter 프로세스 자체의 CPU·메모리가 포화되지 않았다.
6. 같은 시간대의 Prometheus target과 Grafana 지표가 정상 수집됐다.

401이 하나라도 있으면 인증 실패가 응답 시간을 왜곡하므로 해당 실행 전체를 폐기한다. Smoke가 실패한 상태에서는 Thread를 늘리지 않는다.

JTL에는 응답 본문, 요청·응답 header, sampler data와 URL을 저장하지 않는다. `access_token`도 sample variable로 등록하지 않아 결과 컬럼에 포함되지 않는다.

## 11. 서버 내부 지표와 추가 진단

CPU·Heap·GC·Tomcat·Hikari·HTTP 지표는 [모니터링 README](../monitoring/README.md)의 Grafana dashboard에서 같은 시간대를 확인한다.

Grafana에 포함되지 않은 MySQL statement와 Redis 상태가 필요할 때만 다음 명령을 추가로 사용한다. 실행 횟수와 시각도 측정 기록에 남긴다.

```bash
docker exec lirouti-mysql mysql -ulirouti -plirouti1234 -N -e \
  "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Questions','Slow_queries');"

docker exec lirouti-redis redis-cli INFO stats
docker exec lirouti-redis redis-cli INFO memory
docker exec lirouti-redis redis-cli DBSIZE
docker stats --no-stream lirouti-mysql lirouti-redis
```

측정 중 `JPA_SHOW_SQL=true`를 사용하지 않는다. SQL 전체 출력은 로그 I/O와 부하 조건을 바꾼다.

## 12. 비교 측정 원칙

1. Smoke로 인증과 Assertion을 확인한다.
2. Load 직전에 `task performance-smoke`를 한 번 더 실행해 JVM·Hikari·MySQL을 Warm-up한다.
3. Warm-up 결과는 기준선 통계에서 제외한다.
4. 같은 Load를 최소 3회 실행한다.
5. JMeter 결과와 같은 시간대의 Grafana 지표를 함께 기록한다.
6. 환경·commit SHA·데이터 상태·실행 시각·시나리오 값을 고정한다.

앱과 JMeter를 같은 노트북에서 실행한 결과는 변경 전후 회귀 비교에는 사용할 수 있지만 서버의 절대 최대 처리량을 뜻하지 않는다.

캐시 비교에서는 다음 조건 외의 요소를 같게 유지한다.

```text
A. 캐시 미적용
B. 캐시 적용 + Cold cache
C. 캐시 적용 + Warm cache
D. 캐시 무효화 직후
```

Redis에는 refresh token과 blacklist도 있으므로 `FLUSHALL`을 사용하지 않는다. 캐시 전용 prefix만 정리한다.

## 13. 실행 기록 양식

```text
run-id:
scenario:
commit SHA:
date/time/timezone:
application host/spec:
load-generator host/spec:
base URL:
data state:
threads / ramp-up / duration / think time / timeouts:
token expiry checked:
p50 / p95 / p99:
throughput / error rate:
application CPU / Heap / GC:
Tomcat busy / max:
Hikari active / pending / max:
HTTP request rate / 4xx·5xx rate:
MySQL / Redis 추가 진단:
notes:
```

## 14. 문제 해결

### `users.csv`가 없거나 token 오류가 발생함

```bash
task performance-token
```

회원 ID와 local DB의 활성 상태를 확인한다. token을 새로 발급했다면 Prometheus도 같은 token을 사용하도록 `task monitoring-up`을 다시 실행한다.

### 앱에 연결할 수 없음

```bash
task docker-up
task run
```

`http://localhost:8080/health`가 응답하는지 확인한다. 개인 port override를 사용하는 경우 `JMETER_BASE_URL`도 맞춰야 한다.

### JMeter는 성공했지만 Grafana 데이터가 없음

JMeter 성공과 모니터링 성공은 별개다. `http://127.0.0.1:9090/targets`에서 `lirouti`가 `UP`인지 먼저 확인하고 [모니터링 문제 해결](../monitoring/README.md#9-문제-해결)을 따른다.

## 15. 안전 조건

- 승인 없이 운영 서버를 대상으로 실행하지 않는다.
- 실제 token과 비밀번호를 커밋·공유·로그 출력하지 않는다.
- 실제 부하 실행에 GUI Listener를 사용하지 않는다.
- 응답 본문과 인증 header를 JTL에 저장하지 않는다.
- 결과는 Git에서 제외된 `results/{run-id}`에 둔다.
- JWT 만료 시간과 Security 설정을 성능 테스트 편의를 위해 완화하지 않는다.
- Redis 인증 key까지 지우는 `FLUSHALL`을 사용하지 않는다.

## 16. 정적 검증

```bash
xmllint --noout performance/jmeter/api-read-load-test.jmx
bash -n performance/jmeter/run.sh
git diff --check
```

placeholder token으로는 문법과 사전 검증만 확인한다. 정상 성능 결과에는 유효한 비운영 token만 사용한다.

## 17. 공식 문서

- [JMeter Getting Started](https://jmeter.apache.org/usermanual/get-started)
- [JMeter Component Reference](https://jmeter.apache.org/usermanual/component_reference.html)
- [JMeter Dashboard Report](https://jmeter.apache.org/usermanual/generating-dashboard)
- [JMeter Properties Reference](https://jmeter.apache.org/usermanual/properties_reference.html)
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
