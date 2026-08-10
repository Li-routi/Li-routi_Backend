# JMeter API 조회 부하 테스트

이 디렉터리는 인증이 필요한 주요 조회 API의 응답 시간, 처리량과 오류율을 같은 조건에서 반복 측정하기 위한 JMeter 5.6.3 Test Plan을 제공한다. Redis 캐시를 구현하거나 운영 용량을 확정하는 도구가 아니며, 최초 기준선과 이후 최적화 결과를 비교하는 용도다.

## 대상과 실행 구조

한 Thread는 아래 API를 순서대로 호출하고, 설정된 실행 시간이 끝날 때까지 같은 순서를 반복한다.

```text
GET /api/home
GET /api/routines/categories
GET /api/routines/templates
GET /api/chat/emoticons
GET /api/challenges
```

각 요청은 HTTP 200과 JSON `isSuccess=true`를 모두 검사한다. 다섯 API는 HTML Dashboard와 JTL에서 서로 다른 sampler 이름으로 집계된다. 실제 트래픽 비율을 재현하는 모델이 아니라 Endpoint별로 같은 수의 표본을 얻기 위한 초기 기준선이다.

## 준비

필요한 도구를 확인한다.

```bash
jmeter --version
xmllint --version
task --version
```

로컬 MySQL과 Redis를 실행한 뒤 애플리케이션을 별도 터미널에서 실행한다.

```bash
task docker-up
task run
```

기본 대상 주소는 `http://localhost:8080`이다. `baseUrl`에는 마지막 `/`를 붙이지 않는다.

### 인증 CSV

로컬 MySQL에 더미 회원이 적용되어 있는 상태에서 개발 토큰을 발급한다. 기본 회원은 개인 루틴과 챌린지 데이터가 연결된 `9001`이다.

```bash
task performance-token
```

다른 활성 로컬 회원을 사용하려면 회원 ID만 바꾼다.

```bash
task performance-token MEMBER_ID=9002
```

이 명령은 로컬 DB에서 회원 존재 여부와 활성·탈퇴 상태를 확인하고, JMeter 조회 테스트용 14일짜리 access token을 만든다. 토큰은 터미널이나 로그에 출력하지 않고 아래 Git 미추적 파일에 저장한다.

```csv
access_token
발급된-개발-토큰
```

- MySQL이 실행 중이어야 하며, 애플리케이션을 한 번 실행해 로컬 더미 데이터가 적용되어 있어야 한다.
- 토큰 발급 도구는 웹 서버를 열거나 Flyway 마이그레이션을 실행하지 않는다.
- 첫 행은 반드시 `access_token` 하나여야 한다.
- 빈 행, 앞뒤 공백, 추가 컬럼은 허용하지 않는다.
- 모든 Thread가 하나의 CSV 읽기 순서를 공유하고 EOF에서 처음으로 돌아간다. 한 줄이면 모든 Thread가 같은 token을 사용하며, 여러 줄이면 반복마다 다음 token이 실행 순서에 따라 배정된다.
- 파일이 없거나 읽을 수 없거나 token 행이 비어 있으면 setUp Thread Group에서 중단하며 HTTP 요청은 보내지 않는다.
- `users.csv`는 `.gitignore` 대상이다. 실제 소셜 로그인 token, refresh token이나 `.env` 값을 사용하지 않는다.

외부에서 받은 비운영 access token을 사용해야 할 때만 `users.csv.example`을 복사하고 값을 직접 교체한다. 토큰을 명령행 인자, JMX, README 또는 이슈 댓글에 넣지 않는다.

## 외부 속성

모든 값은 `-J이름=값`으로 주입한다.

| 속성 | 기본값 | 의미 |
| --- | ---: | --- |
| `baseUrl` | `http://localhost:8080` | 끝에 `/`가 없는 대상 주소 |
| `usersFile` | JMX 옆 `users.csv` | token CSV 경로. 실행 명령에서는 절대 경로 권장 |
| `threads` | `1` | 동시 사용자 수 |
| `rampUpSeconds` | `1` | 모든 Thread를 시작하는 데 걸리는 시간 |
| `durationSeconds` | `20` | 측정 실행 시간 |
| `thinkTimeMillis` | `500` | 각 요청 전에 기다리는 시간 |
| `connectTimeoutMillis` | `3000` | 연결 수립 제한 시간 |
| `responseTimeoutMillis` | `5000` | 응답 대기 제한 시간 |

## Smoke, Load, Spike 실행

실제 부하는 GUI가 아닌 CLI로 실행한다. 평소에는 아래 Task 명령만 사용한다.

```bash
task performance-smoke
task performance-load
task performance-spike
```

Thread 수는 환경에 맞춰 낮은 값부터 단계적으로 올린다. 각 명령의 기본 설정은 다음과 같다.

Smoke Test:

```bash
JMETER_SCENARIO=smoke
JMETER_THREADS=1
JMETER_RAMP_UP=1
JMETER_DURATION=20
JMETER_THINK_TIME=500
```

Load Test:

```bash
JMETER_SCENARIO=load
JMETER_THREADS=5
JMETER_RAMP_UP=10
JMETER_DURATION=120
JMETER_THINK_TIME=500
```

Spike Test:

```bash
JMETER_SCENARIO=spike
JMETER_THREADS=30
JMETER_RAMP_UP=3
JMETER_DURATION=60
JMETER_THINK_TIME=500
```

아래 직접 실행 블록은 기본값을 바꿔야 할 때만 사용한다.

```bash
JMETER_PLAN=performance/jmeter/api-read-load-test.jmx
JMETER_USERS_FILE="$(pwd)/performance/jmeter/users.csv"
JMETER_BASE_URL=http://localhost:8080
JMETER_RUN_PARENT="$(pwd)/performance/jmeter/results"

test -f "$JMETER_USERS_FILE"
mkdir -p "$JMETER_RUN_PARENT"
JMETER_RUN_DIR="$(mktemp -d "${JMETER_RUN_PARENT}/${JMETER_SCENARIO}-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
JMETER_RUN_ID="$(basename "$JMETER_RUN_DIR")"

jmeter -n \
  -t "$JMETER_PLAN" \
  -JbaseUrl="$JMETER_BASE_URL" \
  -JusersFile="$JMETER_USERS_FILE" \
  -Jthreads="$JMETER_THREADS" \
  -JrampUpSeconds="$JMETER_RAMP_UP" \
  -JdurationSeconds="$JMETER_DURATION" \
  -JthinkTimeMillis="$JMETER_THINK_TIME" \
  -JconnectTimeoutMillis=3000 \
  -JresponseTimeoutMillis=5000 \
  -Jaggregate_rpt_pct1=50 \
  -Jaggregate_rpt_pct2=95 \
  -Jaggregate_rpt_pct3=99 \
  -Jjmeter.reportgenerator.overall_granularity=2000 \
  -Jjmeter.save.saveservice.output_format=csv \
  -Jjmeter.save.saveservice.print_field_names=true \
  -Jjmeter.save.saveservice.connect_time=true \
  -Jjmeter.save.saveservice.sent_bytes=true \
  -Jjmeter.save.saveservice.assertion_results_failure_message=true \
  -Jjmeter.save.saveservice.response_data=false \
  -Jjmeter.save.saveservice.response_data.on_error=false \
  -Jjmeter.save.saveservice.samplerData=false \
  -Jjmeter.save.saveservice.requestHeaders=false \
  -Jjmeter.save.saveservice.responseHeaders=false \
  -Jjmeter.save.saveservice.url=false \
  -l "$JMETER_RUN_DIR/result.jtl" \
  -j "$JMETER_RUN_DIR/jmeter.log" \
  -e \
  -o "$JMETER_RUN_DIR/report"
```

매 실행은 timestamp와 임의 suffix가 붙은 새로운 `run-id`를 사용한다. `-f` 옵션이나 기존 결과 디렉터리를 삭제하는 방식으로 덮어쓰지 않는다.

Warm-up은 Load 측정 전에 별도 `run-id`로 1~3 Thread, 20~30초 정도 실행하고 결과에서 제외한다. 기준선은 같은 Load 설정으로 최소 3회 실행한다. Spike는 Smoke와 Load가 정상인 뒤 `5 → 10 → 20 → 30`처럼 단계적으로 올린다.

## 결과 확인

```text
performance/jmeter/results/{run-id}/result.jtl
performance/jmeter/results/{run-id}/jmeter.log
performance/jmeter/results/{run-id}/report/index.html
```

HTML Dashboard의 Statistics 표에서 Endpoint별 p50, p95, p99, Throughput과 Error %를 확인한다. 그래프에서는 Active Threads, Connect Time, Latency, Transactions per Second와 오류 발생 시점을 함께 본다.

다음 조건을 모두 만족해야 정상 측정으로 채택한다.

1. 다섯 `GET /api/...` sampler가 모두 존재한다.
2. Error %가 0이고 Assertion 실패가 없다.
3. HTTP 401, 403 또는 5xx가 없다.
4. 실행 중 token이 만료되지 않았다.
5. JMeter 부하 발생기 자체의 CPU·메모리가 포화되지 않았다.

401이 하나라도 있으면 인증 실패가 응답 시간을 왜곡하므로 해당 실행 전체를 폐기한다. Smoke 실패 상태에서 Thread를 늘리지 않는다. JTL은 CSV 형식이며 응답 본문, 요청·응답 헤더, sampler data와 URL을 저장하지 않는다. `access_token`을 sample variable로 등록하지 않았으므로 결과 컬럼에도 포함되지 않는다.

## 함께 기록할 시스템 지표

각 실행의 시작 전, 실행 중, 종료 직후에 같은 명령과 간격으로 기록한다. 현재 Actuator/Micrometer가 없으므로 JVM과 컨테이너의 로컬 지표를 사용한다.
모니터링 명령도 CPU·I/O와 command 수를 늘리므로 수집 간격과 실행 횟수를 결과 메모에 함께 남긴다.

### 애플리케이션

애플리케이션 PID를 찾고 CPU, RSS와 GC를 관찰한다.

```bash
jps -lv
ps -p <app-pid> -o pid,%cpu,%mem,rss,etime,command
jstat -gcutil <app-pid> 1000
```

HTTP 4xx·5xx와 예외 수는 애플리케이션 로그의 같은 시간 구간에서 확인한다. Hikari active/idle/pending을 자동 수집하는 endpoint는 현재 없으므로 자동 계측이 필요하면 Actuator/Micrometer 도입을 별도 이슈로 진행한다.

### MySQL

연결 수, 실행량과 slow query 수를 실행 전후에 기록한다.

```bash
docker exec lirouti-mysql mysql -ulirouti -plirouti1234 -N -e \
  "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Questions','Slow_queries');"
```

누적 시간이 큰 statement digest를 확인한다.

```bash
docker exec lirouti-mysql mysql -ulirouti -plirouti1234 lirouti -e \
  "SELECT DIGEST_TEXT, COUNT_STAR, ROUND(SUM_TIMER_WAIT / 1000000000000, 3) AS total_seconds FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME = 'lirouti' ORDER BY SUM_TIMER_WAIT DESC LIMIT 10;"
```

측정을 위해 `JPA_SHOW_SQL=true`를 켜지 않는다. SQL 전체 출력은 로그 I/O와 부하를 바꾼다.

### Redis와 컨테이너

Redis 처리량, 오류, 메모리와 key 수를 확인한다.

```bash
docker exec lirouti-redis redis-cli INFO stats
docker exec lirouti-redis redis-cli INFO memory
docker exec lirouti-redis redis-cli DBSIZE
docker stats --no-stream lirouti-mysql lirouti-redis
```

Redis에는 refresh token과 blacklist도 있으므로 캐시 비교 시에도 `FLUSHALL`을 사용하지 않는다. 향후 캐시가 추가되면 캐시 전용 prefix만 정리하고 hit/miss, eviction과 key 수를 함께 기록한다.

## 실행 기록 양식

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
application CPU / RSS / GC / 4xx / 5xx:
MySQL connections / questions / slow queries / top statements:
Redis commands / rejected connections / memory / keys:
notes:
```

앱과 JMeter를 같은 노트북에서 실행한 결과는 변경 전후 회귀 비교에는 쓸 수 있지만 서버의 최대 처리량을 뜻하지 않는다. 용량 한계를 측정할 때는 부하 발생기와 애플리케이션을 다른 호스트에 두고 서버 사양, 네트워크와 데이터 상태를 고정한다.

## 안전 조건

- 운영 서버에서는 명시적 승인 없이 실행하지 않는다.
- 실제 access token, refresh token, OAuth token과 `.env` 값을 커밋하거나 공유하지 않는다.
- 실제 부하 실행에 GUI Listener를 사용하지 않는다.
- 응답 본문과 인증 헤더를 JTL 또는 일반 로그에 남기지 않는다.
- 결과는 Git에서 제외된 `performance/jmeter/results/{run-id}`에 둔다.
- JWT 만료 시간, Security Filter와 production 설정을 성능 테스트 편의를 위해 완화하지 않는다.
- Cold/Warm cache 비교에서 인증 Redis key를 삭제하거나 `FLUSHALL`하지 않는다.

## 정적 검증

```bash
xmllint --noout performance/jmeter/api-read-load-test.jmx
git diff --check
```

JMX 로딩과 CSV 사전 검증은 실제 Smoke 전에 확인할 수 있지만, placeholder token을 정상 성능 결과로 사용하지 않는다. 실제 Smoke는 로컬 애플리케이션과 유효한 비운영 token을 준비한 뒤 실행한다.

## 공식 문서

- [JMeter Getting Started](https://jmeter.apache.org/usermanual/get-started) — 실제 부하는 CLI 모드로 실행하고 `-l`, `-e`, `-o`로 CSV 결과와 HTML Dashboard를 생성한다.
- [JMeter Component Reference](https://jmeter.apache.org/usermanual/component_reference.html) — CSV Data Set, HTTP Request, Assertion, Timer와 JSR223 동작 기준이다.
- [JMeter Dashboard Report](https://jmeter.apache.org/usermanual/generating-dashboard) — Dashboard 필수 CSV 필드와 percentile 설정 기준이다.
- [JMeter Properties Reference](https://jmeter.apache.org/usermanual/properties_reference.html) — 결과 저장과 `aggregate_rpt_pct1/2/3` 속성 기준이다.
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html) — CLI 실행, 데이터 외부화와 결과 저장 최소화 기준이다.
