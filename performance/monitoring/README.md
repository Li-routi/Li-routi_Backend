# 로컬 부하 테스트 모니터링

이 문서는 Li-routi 조회 API 부하 테스트와 서버 내부 모니터링의 전체 구조, 처음 실행하는 방법, 팀원이 직접 준비할 값과 문제 해결 순서를 설명한다.

핵심 진입점은 다음 두 문서와 명령이다.

- 부하 생성기: [JMeter README](../jmeter/README.md)
- 모니터링 실행: `task monitoring-up`
- Grafana: `http://127.0.0.1:3000`
- Prometheus target: `http://127.0.0.1:9090/targets`

이 구성은 로컬 회귀 비교와 캐시 적용 전후 측정용이다. 운영 배포, 장기 보관, 운영 용량 산정, APM·tracing·로그 수집을 포함하지 않는다.

## 1. 전체 시스템 구조

```text
                              5초마다 JWT scrape
                         ┌─────────────────────────┐
                         │                         ▼
JMeter ── 인증 API 부하 ──> Spring Boot ──> /actuator/prometheus
   │                         │                         │
   │                         │                         ▼
   │                         │                    Prometheus
   │                         │                         │
   │                         │                         ▼
   │                         └─ MySQL·Redis       Grafana
   │
   └─ JTL·JMeter log·HTML report
```

두 관측 결과의 책임은 다르다.

| 관점 | 도구 | 확인하는 값 |
| --- | --- | --- |
| 사용자 관점 | JMeter | API 응답 시간, p50·p95·p99, 처리량, HTTP·Assertion 오류 |
| 서버 내부 | Actuator·Micrometer | CPU, Heap, GC, Tomcat thread, Hikari connection, HTTP meter |
| 시계열 저장 | Prometheus | 5초 단위 meter 수집, 최대 7일 보관 |
| 시각화 | Grafana | JMeter 실행 시간대와 서버 지표 비교 |

JMeter가 성공해도 Prometheus target이 실패할 수 있고, Grafana가 정상이어도 JMeter Assertion이 실패할 수 있다. 기준선은 두 결과가 모두 정상일 때만 채택한다.

## 2. 구성 요소와 파일 책임

### 애플리케이션 계측

| 파일 | 역할 |
| --- | --- |
| 저장소 `build.gradle` | Actuator와 Prometheus registry dependency, 개발 token 발급 Gradle task |
| 저장소 `src/main/resources/application.yaml` | local 프로파일의 Actuator endpoint, `application=lirouti` tag, Tomcat MBean registry |
| 저장소 `Taskfile.yml` | 사람이 사용하는 짧은 실행 명령 |

local 프로파일에서만 `health`, `metrics`, `prometheus` endpoint를 노출한다. `/actuator/prometheus`는 기존 Spring Security와 JWT 보호를 그대로 사용한다.

### 부하 생성기

| 파일 | 역할 |
| --- | --- |
| `../jmeter/api-read-load-test.jmx` | 조회 API 5개, Think Time과 Assertion |
| `../jmeter/run.sh` | Smoke·Load·Spike 값, 사전 확인과 JMeter CLI 실행 |
| `../jmeter/users.csv` | JMeter와 Prometheus가 공유하는 개발 JWT 원본. Git 제외 |
| `../jmeter/results/` | JTL·로그·HTML report. Git 제외 |

자세한 실행 옵션과 결과 판정은 [JMeter README](../jmeter/README.md)를 따른다.

### 모니터링 실행과 수집

| 파일 | 역할 |
| --- | --- |
| `run.sh` | `secrets`, `check`, `up`, `status`, `logs`, `down` 실제 실행 로직 |
| `docker-compose.yml` | Prometheus·Grafana image, port, volume과 read-only mount |
| `prometheus.yml` | 5초 scrape, JWT credentials file과 Spring Boot target |
| `secrets/actuator-token` | Prometheus가 Actuator에 보낼 JWT. 자동 생성·Git 제외 |
| `secrets/grafana-admin-password` | Grafana 최초 admin 비밀번호. 자동 생성·Git 제외 |

`Taskfile.yml`은 사용자 명령만 제공하고, 검증 순서와 예외 처리는 `performance/monitoring/run.sh`가 담당한다.

### Grafana provisioning

| 파일 | 역할 |
| --- | --- |
| `grafana/provisioning/datasources/prometheus.yml` | UID `prometheus` datasource 자동 등록 |
| `grafana/provisioning/dashboards/dashboard.yml` | dashboard JSON 위치와 30초 polling 설정 |
| `grafana/dashboards/lirouti-load-test.json` | 한국어 panel 12개, PromQL, 단위와 배치 |

Grafana UI에서 변경한 내용은 source JSON에 저장되지 않는다. Git의 provisioning 파일을 단일 원본으로 유지하기 위해 UI 저장을 막았다.

## 3. 팀원이 직접 준비하거나 결정할 것

### 최초 1회 준비

| 항목 | 팀원이 할 일 |
| --- | --- |
| Java | Java 21 설치와 `java -version` 확인 |
| Task | 설치 후 `task --version` 확인 |
| Docker | Docker Desktop 또는 Docker daemon을 직접 실행 |
| JMeter | 5.6.3 설치와 `jmeter --version` 확인 |
| OpenSSL | 최초 Grafana 비밀번호 생성에 필요. `openssl version` 확인 |
| image | 첫 실행에서 Prometheus·Grafana image를 받을 수 있는 네트워크 필요 |

현재 고정 image:

```text
prom/prometheus:v3.13.1
grafana/grafana:13.1.1
```

### 실행할 때마다 직접 확인

- 측정할 commit SHA와 working tree 상태
- local DB의 데이터 상태와 사용할 회원 ID
- Spring Boot가 host `8080`에서 local 프로파일로 실행 중인지
- 실제 측정 앱에서 `JPA_SHOW_SQL=false`인지
- token이 실행 도중 만료되지 않는지
- 실행 시각과 Grafana 조회 시간대가 맞는지
- 운영 서버가 아닌 승인된 대상인지

### 자동으로 처리되는 것

- 활성 local 회원의 개발 JWT 발급과 `users.csv` 교체
- JMeter token을 Prometheus credentials file로 복사
- 최초 Grafana 비밀번호 무작위 생성
- secret 디렉터리와 파일 권한 설정
- Compose 문법과 Prometheus 설정 검사
- Prometheus·Grafana 컨테이너 생성
- datasource와 dashboard 자동 provisioning

자동화가 데이터 상태, 대상 승인, 시나리오 선택과 결과 채택까지 대신 결정하지는 않는다.

## 4. 처음부터 실행하기

### 4.1 Docker daemon 시작

Docker Desktop을 직접 실행한 뒤 확인한다.

```bash
docker info
```

### 4.2 MySQL·Redis 시작

```bash
task docker-up
```

### 4.3 Spring Boot 실행

별도 터미널에서 실행한다.

```bash
JPA_SHOW_SQL=false task run
```

기본 active profile은 `local`이다. 실제 측정에서 SQL 전체 로그를 끄는 이유는 console I/O가 응답 시간과 CPU 사용량을 바꿀 수 있기 때문이다.

앱이 처음 기동될 때 local dummy migration이 적용된다. token을 발급하기 전에 앱이 정상 기동했는지 확인한다.

### 4.4 개발 token 발급

```bash
task performance-token
```

기본 회원은 `9001`이다. 다른 활성 local 회원을 사용할 때만 지정한다.

```bash
task performance-token MEMBER_ID=9002
```

이 명령은 token을 화면에 출력하지 않고 `performance/jmeter/users.csv`에 저장한다.

### 4.5 모니터링 검사와 시작

```bash
task monitoring-up
```

처음부터 문제를 나눠 확인하고 싶으면 검사만 먼저 실행한다.

```bash
task monitoring-check
```

`monitoring-up`은 설정 검사까지 포함하므로 평소에는 한 명령만 실행하면 된다.

### 4.6 정상 상태 확인

```bash
task monitoring-status
```

브라우저:

- Prometheus target: `http://127.0.0.1:9090/targets`
- Grafana: `http://127.0.0.1:3000`

Prometheus의 `lirouti` target이 `UP`인지 먼저 확인한다.

Grafana 로그인:

```text
사용자: admin
비밀번호 파일: performance/monitoring/secrets/grafana-admin-password
```

비밀번호가 필요할 때만 개인 터미널에서 확인한다.

```bash
cat performance/monitoring/secrets/grafana-admin-password
```

화면 공유, shell log, 메신저, 이슈나 PR에 값을 남기지 않는다.

Grafana의 `Li-routi` folder에서 `Li-routi 부하 테스트 모니터링` dashboard를 연다.

### 4.7 부하 실행

Smoke로 인증과 결과 생성을 먼저 확인한다.

```bash
task performance-smoke
```

Smoke가 정상일 때만 Load를 실행한다.

```bash
task performance-load
```

Spike는 Smoke와 Load가 모두 정상이고 목적이 명확할 때만 실행한다.

```bash
task performance-spike
```

## 5. `monitoring-up` 내부 실행 흐름

```text
task monitoring-up
   ↓
Taskfile.yml
   ↓
performance/monitoring/run.sh up
   ↓
1. Docker 설치·daemon 확인
2. users.csv header와 첫 token 확인
3. secrets/actuator-token 갱신
4. Grafana 비밀번호 파일 확인 또는 최초 생성
5. docker compose config --quiet
6. Prometheus image의 promtool check config
7. docker compose up -d --force-recreate
8. docker compose ps
```

`--force-recreate`는 개발 JWT를 다시 발급했을 때 Prometheus가 새 credentials file을 확실히 다시 읽도록 사용한다. named volume은 삭제하지 않는다.

## 6. 실행 중 데이터 흐름

### JMeter 요청

```text
users.csv JWT
   ↓
JMeter Thread
   ↓
조회 API 5개
   ↓
HTTP 200 + isSuccess=true 검사
   ↓
JTL·HTML report
```

### Prometheus scrape

```text
users.csv 첫 token
   ↓ monitoring/run.sh
secrets/actuator-token
   ↓ read-only mount
Prometheus
   ↓ Authorization: Bearer
host.docker.internal:8080/actuator/prometheus
```

Prometheus는 5초마다 scrape한다. 인증 요청마다 기존 JWT 검증과 Redis blacklist 확인이 실행되므로 1초처럼 과도하게 짧은 주기를 사용하지 않는다.

### Grafana 조회

```text
Dashboard JSON
   ↓ file provisioning
Grafana ── http://prometheus:9090 ──> Prometheus
```

브라우저가 Prometheus에 직접 접근하지 않는다. Grafana 컨테이너가 Compose network의 `prometheus` 서비스 이름으로 조회한다.

## 7. 주소, port와 보관 정책

| 구성 | 주소 | 노출 범위 | 보관 |
| --- | --- | --- | --- |
| Spring Boot | `http://localhost:8080` | host | 앱 실행 동안 |
| Prometheus | `http://127.0.0.1:9090` | local loopback | 최대 7일 |
| Grafana | `http://127.0.0.1:3000` | local loopback | named volume |

Prometheus와 Grafana는 외부 interface에 공개하지 않는다. 운영 Compose에는 이 모니터링 구성을 추가하지 않는다.

## 8. Dashboard 구성과 해석

Dashboard 기본 범위는 최근 15분이고 5초마다 새로 고친다.

| 영역 | Panel | 확인할 내용 |
| --- | --- | --- |
| CPU | 애플리케이션 CPU 사용률 | Spring Boot 프로세스 연산 포화 |
| CPU | 시스템 CPU 사용률 | 머신 전체 부하와 앱 부하 구분 |
| JVM | Heap 사용량 | 지속 증가와 부하 종료 후 회복 |
| JVM | Heap 최대 용량 | Heap 사용량의 상한 기준 |
| JVM | GC 발생률 | 초당 GC 발생 빈도 |
| JVM | 초당 GC 정지 시간 | p95·p99 상승과 GC 정지의 상관관계 |
| HTTP | Tomcat 사용 중 스레드 | 실제 요청 처리 worker 사용량 |
| HTTP | Tomcat 최대 스레드 | worker 포화 기준 |
| DB | Hikari 활성 커넥션 | 사용 중인 DB connection |
| DB | Hikari 대기 커넥션 | connection pool 대기 발생 |
| DB | Hikari 최대 커넥션 | pool 상한 기준 |
| HTTP | HTTP 요청/오류율 | 전체 요청률과 4xx·5xx 요청률 |

JMeter의 `Active Threads`는 요청을 보내는 가상 사용자 수이고, `Tomcat 사용 중 스레드`는 서버가 실제로 요청을 처리하는 worker 수다. 같은 지표로 해석하지 않는다.

현재 PromQL은 `application="lirouti"` tag를 사용한다. 첫 실행에서는 12개 panel 모두 값이 들어오는지 실제 `/actuator/prometheus` 출력과 대조한다. target은 `UP`인데 특정 panel만 `No data`라면 meter 이름이나 label 차이를 먼저 확인한다.

## 9. 문제 해결

### Docker daemon 오류

Docker Desktop 또는 Docker daemon을 직접 실행한 뒤 다시 확인한다.

```bash
docker info
task monitoring-check
```

### `users.csv` 오류

```bash
task performance-token
```

MySQL, local dummy data와 회원 활성 상태를 확인한다.

### Prometheus target이 `DOWN`

다음 순서로 확인한다.

1. Spring Boot가 host `8080`에서 실행 중인지 확인
2. `task performance-token`으로 token 갱신
3. `task monitoring-up`으로 credentials와 컨테이너 갱신
4. `task monitoring-logs`에서 scrape 오류 확인

익명 `/actuator/prometheus` 요청이 `401`인 것은 정상이다. 인증을 풀거나 운영 프로파일에 endpoint를 노출해 해결하지 않는다.

### Dashboard가 자동 등록되지 않음

다음을 확인한다.

- Grafana log에 provisioning 오류가 없는지
- datasource UID가 `prometheus`인지
- dashboard UID가 `lirouti-load-test`인지
- provider path가 `/var/lib/grafana/dashboards`인지

```bash
task monitoring-logs
```

파일 수정은 최대 30초 polling 뒤 반영된다. dashboard title은 `Li-routi 부하 테스트 모니터링`이다.

### 일부 panel만 `No data`

Prometheus target이 `UP`인지 먼저 확인한다. 이후 실제 meter 이름과 label을 확인한다.

- CPU·Heap·GC: Actuator JVM·system meter
- Tomcat: local 프로파일의 Tomcat MBean registry
- Hikari: 실제 datasource와 pool 초기화 상태
- HTTP: API 요청이 실제로 발생했는지

query를 바꿀 때는 표시 제목이 아니라 `grafana/dashboards/lirouti-load-test.json`의 PromQL만 실제 meter에 맞춘다.

### Grafana 로그인이 되지 않음

Grafana admin 비밀번호 파일은 `grafana-data` volume이 처음 만들어질 때만 초기 비밀번호로 사용된다. 기존 DB가 있는 상태에서 파일만 바꿔도 로그인 비밀번호는 바뀌지 않는다.

`run.sh`는 기존 volume이 있는데 비밀번호 파일이 없으면 자동 생성을 중단한다. volume을 삭제하면 Grafana 내부 DB도 사라지므로 보존 여부를 먼저 결정한다.

## 10. 설정을 변경해야 할 때

기본값을 바꾸기 전에 비교 결과가 달라지는 설정인지 확인하고 팀과 합의한다.

| 바꿀 내용 | 수정 위치 | 함께 확인할 것 |
| --- | --- | --- |
| Spring Boot host·port | `prometheus.yml` target | JMeter `JMETER_BASE_URL`, Docker host 접근 |
| scrape 주기 | `prometheus.yml` | datasource `timeInterval`, dashboard query interval·refresh |
| Prometheus 보관 기간 | `docker-compose.yml`의 retention | disk 사용량과 기존 비교 기간 |
| Prometheus·Grafana 버전 | `docker-compose.yml` | provisioning·dashboard schema 호환성 |
| host 공개 port | `docker-compose.yml` | loopback 제한 유지 |
| dashboard panel·PromQL | `grafana/dashboards/lirouti-load-test.json` | datasource UID와 실제 meter |
| dashboard scan 주기 | dashboard provider YAML | Docker bind mount에서는 10초 초과 polling 유지 |
| JMeter 대상 주소 | `JMETER_BASE_URL` | 승인된 비운영 대상과 모니터링 대상 일치 |
| JMeter Thread·시간 | `../jmeter/run.sh` | 기존 기준선과 비교 가능성 |

운영 적용, 익명 Actuator 접근, wildcard endpoint 노출, secret의 설정 파일 하드코딩은 허용되는 변경 방법이 아니다.

## 11. 반복 실행과 종료

개발 token을 새로 발급하면 모니터링도 다시 실행한다.

```bash
task performance-token
task monitoring-up
```

상태와 log:

```bash
task monitoring-status
task monitoring-logs
```

`monitoring-logs`는 `Ctrl+C`로 log follow만 종료한다. 컨테이너는 계속 실행된다.

종료:

```bash
task monitoring-down
```

`monitoring-down`은 컨테이너와 network만 제거하고 `prometheus-data`, `grafana-data` volume은 보존한다.

`docker compose down -v`는 시계열과 Grafana DB를 삭제한다. 초기화를 명확히 의도한 경우가 아니면 사용하지 않는다.

## 12. 현재 범위와 남은 확인

현재 저장소에 구현된 범위:

- local 전용 Actuator·Micrometer·Prometheus registry
- JWT로 보호된 Prometheus endpoint
- Prometheus·Grafana Compose
- datasource·dashboard 자동 provisioning
- 한국어 panel 12개
- secret·검사·시작·상태·log·종료 Task
- JMeter Smoke·Load·Spike와 개발 token 발급

실제 환경에서 반드시 확인할 항목:

- 고정 image의 `promtool check config`
- Prometheus `lirouti` target `UP`
- datasource와 dashboard provisioning log
- 12개 panel의 실제 meter 이름과 label
- 동일 조건 Load 3회 기준선

MySQL Exporter, Redis Exporter, Node Exporter, OpenTelemetry, tracing, Loki와 APM은 현재 범위가 아니다.

## 13. 안전 조건

- 실제 access token, Grafana 비밀번호와 `.env` 값을 커밋하거나 공유하지 않는다.
- 운영 서버를 승인 없이 scrape하거나 부하 테스트하지 않는다.
- 측정 중 `JPA_SHOW_SQL=true`를 사용하지 않는다.
- 인증 문제를 Security 설정 완화나 JWT 수명 연장으로 우회하지 않는다.
- Redis 인증 key까지 지우는 `FLUSHALL`을 사용하지 않는다.
- JMeter와 앱을 같은 노트북에서 실행한 결과를 서버 절대 최대 처리량으로 해석하지 않는다.

## 14. 정적 검증

```bash
bash -n performance/monitoring/run.sh
docker compose -f performance/monitoring/docker-compose.yml config --quiet
xmllint --noout performance/jmeter/api-read-load-test.jmx
git diff --check
```

Docker daemon과 image가 준비된 환경에서는 다음 검사까지 수행한다.

```bash
task monitoring-check
```

## 15. 공식 문서

- [Spring Boot Actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Prometheus configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Prometheus promtool](https://prometheus.io/docs/prometheus/latest/command-line/promtool/)
- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Grafana Docker configuration](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/)
- [Task Guide](https://taskfile.dev/docs/guide)
