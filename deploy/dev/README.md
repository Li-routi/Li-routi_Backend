# 개발 서버 구축 가이드

운영과 별개로 도는 개발 서버를 만드는 절차다. 운영 가이드([`deploy/README.md`](../README.md))를 대체하지 않고, **다른 점만** 적는다. 여기 없는 항목은 운영 문서를 그대로 따른다.

> **이 서버는 구축이 끝나 돌고 있다.** 아래 1~8단계는 이제 *재구축* 절차다 — 인스턴스를
> 갈아엎거나 두 번째 개발 서버를 만들 때 그대로 밟으면 된다. 처음 세울 때 밟은 순서 그대로이고,
> 첫 배포에서 실제로 확인된 값은 각 절에 적어 두었다.
>
> 순서를 지키는 이유는 남아 있다. 배포가 절반쯤 진행된 뒤에 막히면 서버가 어중간한 상태로 남는다.
> `deploy-dev.yml` 은 SSH 전에 `Verify dev target`(주소가 비었는지·운영과 같은지),
> `Reject production bucket`, `Check Caddy env vars are set` 을 차례로 확인한다. 하나라도 빠뜨리면
> 거기서 멈추므로 운영이 다치지는 않지만, 그 상황을 만들지 않는 편이 낫다.

---

## 왜 두는가

지금까지는 **`develop` 머지가 곧 운영 배포**였다. 그 사이에 완충재가 없어서 두 가지가 곧장 운영을 때렸다.

1. **Flyway 마이그레이션 오류.** `ddl-auto` 가 `validate` 라 엔티티와 스키마가 어긋나면 앱이 부팅하지 못하고 재시작 루프에 빠진다. 운영에서 실제로 겪었다.
2. **시드(`R__`) 사고.** 내용이 바뀌면 매 배포마다 다시 실행되므로, 한 글자 고친 값이 조용히 운영 마스터 데이터를 덮어쓴다.

개발 서버는 **이 둘을 먼저 맞는 자리**다. 그래서 개발 서버의 프로파일·`ddl-auto`·이미지를 운영과 같게 유지한다 — 여기서만 느슨하게 두면 먼저 맞는 의미가 사라진다.

## 확정된 구성

| | 운영 | 개발 |
| --- | --- | --- |
| 브랜치 | `main` | `develop` |
| 인스턴스 | t4g.small (2GB) | t4g.small (2GB), 별도 |
| 스토리지 | 30 GiB | **gp3 20 GiB** |
| 도메인 | `lirouti.kro.kr` | `lirouti-dev.kro.kr` |
| 인증서 | ZeroSSL (LE 한도 초과) | 동일 — [2단계](#2-도메인) 참고 |
| 앞단 프록시 | Caddy (80·443) | 동일 |
| S3 버킷 | `lirouti-prod-bucket` | `lirouti-dev-bucket` |
| 백업 버킷 | `lirouti-db-backup` | **없음** |
| 백업 cron | 매일 04:00 KST | **걸지 않는다** |
| IAM 역할 | `lirouti-ec2-role` | `lirouti-dev-ec2-role` |
| DB · Redis | compose 내부 | 동일 |
| 이미지 | 같은 GHCR 이미지 | 동일 |
| compose | `deploy/docker-compose.prod.yml` | **동일 파일** |
| 프로파일 | `prod` | **`prod`** (의도적으로 같게) |

**RDS 를 쓰지 않는 것은 선택이 아니다.** 조직 SCP 의 서비스 화이트리스트에 RDS 가 없어 켤 수 없다. 개발 DB 도 운영과 같이 compose 안 MySQL 이다.

## 이 폴더의 파일

| 파일 | 용도 |
| --- | --- |
| `.env.dev.example` | `DEV_ENV_FILE` 시크릿 템플릿 |
| `iam-policy.dev.json` | 앱이 쓰는 IAM 정책 (`lirouti-dev-app-s3`) |
| `bucket-policy-media.dev.json` | 개발 버킷의 공개 prefix 정책 |

**개발 전용 compose 는 두지 않는다.** 한때 Caddy 를 뺀 `docker-compose.dev.yml` 을 여기 뒀었는데, 도메인을 붙이면서 지웠다. 파일이 둘이면 운영 compose 를 고칠 때 개발 쪽이 조용히 뒤처지고, 그 어긋남을 개발 서버에서 재현하지 못하게 된다 — 먼저 깨지는 자리를 두는 목적과 정반대다. 서버에 올리는 것은 [`deploy/docker-compose.prod.yml`](../docker-compose.prod.yml) 과 [`deploy/Caddyfile`](../Caddyfile) 이고, 환경 차이는 전부 `.env` 가 흡수한다.

---

# 절차

**순서를 지킨다.** 특히 두 가지다.

- **`DEV_` 시크릿 등록(7단계)이 `develop` 머지보다 먼저**여야 한다. 빠지면 배포가 `Verify dev target` 에서 멈춘다.
- **DNS(2단계)·80·443(3단계)이 첫 배포보다 먼저**여야 한다. 뒤집으면 인증서 발급이 실패하는데, Let's Encrypt 는 인증 실패를 계정·도메인 조합당 **시간당 5회**로 제한한다. 원인을 고치기 전에 재배포를 반복하면 그 한도부터 태운다.

## 1) EC2 인스턴스 + 탄력적 IP

운영 문서의 "서버 최초 세팅" 1~2번(Docker·compose 설치, **swap 4GB**, `/opt/app` 생성)을 그대로 따른다. swap 은 건너뛰지 않는다 — 2GB 인스턴스라 피크에 OOM killer 가 컨테이너를 죽인다. **운영과 같은 4GB 로 잡는다** — 운영이 평상시에도 700MB 안팎을 실제로 쓰고 있어, 여기만 작게 두면 메모리 압박 상황이 재현되지 않는다.

- AMI: Ubuntu 24.04 **arm64** (t4g 는 Graviton 이라 x86 이미지는 못 돌린다)
- 스토리지: **gp3 20 GiB**. gp2 가 아니다 — gp3 가 더 싸고, 작은 볼륨에서 기본 IOPS 가 훨씬 높다
- 탄력적 IP 를 **할당하고 연결까지** 한다. 이 주소가 곧 `SERVER_HOST` 시크릿 값이 된다

> 공인 IPv4 는 연결 여부와 무관하게 시간당 과금된다. 개발 서버를 접을 때 인스턴스만 종료하고 EIP 를 남기면 요금이 계속 나간다.

## 2) 도메인

`lirouti-dev.kro.kr` 을 내도메인.한국에서 무료로 받아 쓴다. 관리 화면에서 **고급설정(DNS) → IP연결(A)** 한 줄만 쓴다.

| 칸 | 값 |
| --- | --- |
| 호스트(왼쪽) | **비워둔다** — 도메인 자체를 가리킨다 |
| IP(오른쪽) | 개발 서버 EIP |

웹포워딩·단일페이지는 쓰지 않는다. HTTP 리다이렉트/정적 호스팅이라 TLS 를 우리 서버에서 종단할 수 없다. CNAME 도 아니다 — 도메인 자체(apex)에는 붙일 수 없고, 대상이 IP 라 A 가 맞다.

**전파를 확인하기 전에는 다음으로 넘어가지 않는다.**

```bash
dig +short lirouti-dev.kro.kr          # EIP 가 나와야 한다
dig +short @1.1.1.1 lirouti-dev.kro.kr # 캐시가 아닌 외부 리졸버로도 확인
```

### 인증서는 Let's Encrypt 가 아니라 ZeroSSL 이 발급했다

**첫 배포부터 Let's Encrypt 가 막혔다.** 우려가 아니라 실측이다.

**`kro.kr` 은 Public Suffix List 에 없다.** PSL 에는 `co.kr`·`ne.kr`·`or.kr` 같은 것들과, 성격이 비슷한 무료 도메인 `duckdns.org` 는 등재돼 있는데 `kro.kr` 은 빠져 있다(PSL 원본을 받아 대조했다).

그래서 Let's Encrypt 는 우리 도메인의 "등록 도메인"을 `lirouti-dev.kro.kr` 이 아니라 **`kro.kr` 전체**로 본다. 등록 도메인당 **168시간당 50장** 한도를 `kro.kr` 을 쓰는 모든 사람과 나눠 쓴다. 첫 배포에서 그 한도가 이미 차 있었다.

```
HTTP 429 urn:ietf:params:acme:error:rateLimited
too many certificates (50) already issued for "kro.kr" in the last 168h0m0s,
retry after 2026-08-17 15:23:42 UTC
```

우리가 쓴 것은 이번 1장인데 나머지 49장은 남들이 썼다. **우리 쪽에서 고칠 수 있는 것이 없고, 재배포로도 풀리지 않는다.**

살아난 것은 **Caddy 가 0.1초 만에 ZeroSSL 로 넘어갔기 때문**이다. EAB 자격을 자동으로 발급받고 HTTP-01 챌린지를 통과해 받아왔다. 발급까지 총 30초.

```
issuer   ZeroSSL ECC DV SSL CA 2
subject  CN=lirouti-dev.kro.kr
```

이 폴백은 Caddy 의 기본 동작이라 우리가 설정한 것이 없다. **설정한 적 없는 것에 기대고 있다는 뜻이기도 하다.**

### 그래서 갱신 때마다 같은 도박을 한다

**한 번 성공했다고 끝난 문제가 아니다.** 인증서 수명은 90일이고, 갱신 시점에 두 가지가 동시에 어긋날 수 있다.

1. Let's Encrypt 는 그때도 `kro.kr` 한도에 걸려 있을 가능성이 높다 — 우리가 통제하지 못한다
2. ZeroSSL 마저 실패하면 **폴백할 곳이 없다**

그 상황은 조용히 온다. 배포가 없어도 갱신은 백그라운드에서 돌고, 실패해도 기존 인증서가 만료 전까지는 살아 있어 아무도 눈치채지 못한다. **만료 당일에 개발 서버가 https 로 죽는다.**

`LETSENCRYPT_EMAIL` 로 만료 임박 알림이 오게 해 둔 것이 이 때문이다. 그 메일을 무시하지 않는 것이 유일한 조기 경보다.

**확인은 이렇게 한다.**

```bash
echo | openssl s_client -connect lirouti-dev.kro.kr:443 -servername lirouti-dev.kro.kr 2>/dev/null \
  | openssl x509 -noout -issuer -dates
```

### ⚠️ 운영도 똑같다 — 개발만의 문제가 아니다

**운영 도메인도 `lirouti.kro.kr` 이다.** 같은 무료 서비스이고 같은 등록 도메인(`kro.kr`) 아래에 있다.

운영은 개발보다 먼저 이미 같은 일을 겪었다. 운영 Caddy 로그에 그대로 남아 있다.

```
{"level":"error","logger":"tls.obtain","identifier":"lirouti.kro.kr",
 "issuer":"acme-v02.api.letsencrypt.org-directory",
 "error":"HTTP 429 ... too many certificates (50) already issued for \"kro.kr\""}
{"level":"info","logger":"tls.obtain","identifier":"lirouti.kro.kr",
 "msg":"certificate obtained successfully","issuer":"acme.zerossl.com-v2-DV90"}
```

**두 서버 모두 Let's Encrypt 가 아니라 ZeroSSL 로 돌고 있다.**

| | 발급자 | 만료 |
| --- | --- | --- |
| 운영 `lirouti.kro.kr` | ZeroSSL ECC DV SSL CA 2 | **2026-10-28** |
| 개발 `lirouti-dev.kro.kr` | ZeroSSL ECC DV SSL CA 2 | 2026-11-15 |

**운영 갱신이 먼저 온다.** ZeroSSL 마저 실패하면 개발이 아니라 **운영이 https 로 죽는다.** 위 "갱신 때마다 같은 도박을 한다" 는 운영에 그대로, 그리고 더 큰 무게로 해당한다.

> **`dev.<운영도메인>` 으로 옮기는 것은 해결이 아니다.** 운영 도메인이 이미 `kro.kr` 이라 서브도메인을 파도 같은 등록 도메인 안이고 한도도 그대로 공유한다.
>
> 실제 해결은 **`kro.kr` 을 벗어나는 것**뿐이다 — 유료 도메인을 등록하거나, PSL 에 등재된 무료 서비스(`duckdns.org` 등)로 옮긴다. 운영 도메인이 바뀌는 일이라 클라이언트 배포와 맞물리므로, **급하지 않을 때 미리 정해 둘 것.** 만료 직전에 결정하면 선택지가 없다.

**두 서버의 `LETSENCRYPT_EMAIL` 을 모두 사람이 읽는 주소로 둔다.** 만료 임박 알림이 유일한 조기 경보이고, 이제 그 경보는 개발이 아니라 운영을 지키는 장치다.

## 3) 보안그룹

**운영 보안그룹을 재사용하지 않는다.** 공유하면 개발에서 디버깅하려고 연 규칙이 운영에도 그대로 열린다.

새로 만들고(`lirouti-dev-sg`) 인바운드를 이렇게 둔다.

| 유형 | 포트 | 소스 | 비고 |
| --- | --- | --- | --- |
| SSH | 22 | `0.0.0.0/0` | 배포가 GitHub Actions 러너에서 붙는다. 러너 IP 가 유동적이라 대역 제한이 안 된다. **키 인증 전용 전제** |
| HTTP | **80** | `0.0.0.0/0` | Let's Encrypt HTTP-01 챌린지 + https 리다이렉트 |
| HTTPS | **443** | `0.0.0.0/0` | Caddy |

**8080 은 열지 않는다.** 외부 트래픽은 Caddy 를 거치고, compose 도 `127.0.0.1` 에만 바인딩한다. 운영과 같은 이유이며 근거는 [운영 문서의 8080 은 두 겹으로 닫혀 있다](../README.md#8080-은-두-겹으로-닫혀-있다)에 있다.

**80 은 인증서를 받은 뒤에도 닫으면 안 된다.** 갱신도 같은 챌린지를 쓰므로, 닫으면 조용히 실패하다 90일 뒤 인증서가 만료된다.

**3306·6379 도 열지 않는다** — compose 내부 전용이고, DB 는 SSH 터널로 붙는다.

아웃바운드는 기본(전체 허용)을 유지한다. 서버가 나가서 붙는 곳이 GHCR·S3·카카오·구글·포트원·Anthropic 으로 여럿이라, 좁히면 부팅은 되는데 특정 기능만 조용히 실패한다.

## 4) S3 버킷

### 4-1. 버킷 생성

| 항목 | 값 |
| --- | --- |
| 이름 | `lirouti-dev-bucket` |
| 리전 | **`ap-northeast-2`** (SCP 리전 잠금) |
| 퍼블릭 액세스 차단 | 일단 전부 유지 |
| 기본 암호화 | SSE-S3 (기본값) |
| 버전 관리 | 끔 |

**SSE-KMS 를 고르지 않는다.** 고객 관리형 키를 쓰면 IAM 정책만으로 부족하고 키 정책까지 손봐야 해서, `PutObject` 에서 원인 모를 `AccessDenied` 가 난다.

버전 관리는 운영 **백업** 버킷에만 필요하다(앱이 백업을 덮어쓸 수 있어서 켠 것). 개발에는 백업 버킷 자체가 없다.

### 4-2. 퍼블릭 액세스 차단 부분 해제

`권한 → 퍼블릭 액세스 차단 → 편집`에서 **두 개만** 끈다.

```
☐ BlockPublicPolicy       ← 끈다
☐ RestrictPublicBuckets   ← 끈다
☑ BlockPublicAcls         ← 켜둔다
☑ IgnorePublicAcls        ← 켜둔다
```

**안 끄면 다음 단계 정책이 조용히 무시된다.** 오류가 나지 않고 그냥 안 먹는다. ACL 은 쓰지 않으므로 나머지 둘은 켜 둔다.

### 4-3. 버킷 정책

`권한 → 버킷 정책`에 [`bucket-policy-media.dev.json`](./bucket-policy-media.dev.json) 을 붙여넣는다.

`GetObject` 만 주고 `ListBucket` 은 주지 않는다. **외부인은 버킷을 훑을 수 없고**, 키가 UUID 라 추측도 되지 않는다.

### 4-4. `avatar/` 자산 복사 — 빠뜨리기 쉬운 단계

**`R__` 시드가 `avatar/` 아래 키를 45개 참조한다**(`avatar/character/` 26, `avatar/item/` 19). 시드는 마이그레이션이라 개발 DB 에도 그대로 들어가므로, 버킷이 비어 있으면 **DB 에는 캐릭터·아이템이 있는데 이미지가 전부 404** 가 된다.

상점·캐릭터 화면이 통째로 깨지는데 **서버 로그에는 아무 오류도 남지 않는다** — 앱은 URL 만 조립해 내려줄 뿐이다.

본인 SSO 세션(`AdministratorAccess`)으로 로컬에서 복사한다.

```bash
aws s3 sync s3://lirouti-prod-bucket/avatar/ s3://lirouti-dev-bucket/avatar/
```

인증 사진(`challenge-verifications/`)은 **옮기지 않는다.** 사용자 데이터이고 개발 DB 에는 그 행이 없어 참조되지도 않는다.

## 5) IAM 역할

1. `IAM → 정책 → 생성 → JSON` 에 [`iam-policy.dev.json`](./iam-policy.dev.json) 붙여넣기. 이름 `lirouti-dev-app-s3`
2. `IAM → 역할 → 생성` → AWS 서비스 → **EC2** → 위 정책 → 이름 `lirouti-dev-ec2-role`
3. `EC2 → 개발 인스턴스 → 작업 → 보안 → IAM 역할 수정` → 부착 (재부팅 불필요)

**운영 정책의 `BackupObjectWrite` 블록이 빠져 있다.** 백업을 안 하는데 권한만 남기면, 나중에 누가 `backup.sh` 를 복사해 cron 에 걸었을 때 **하드코딩된 `lirouti-db-backup` 으로 운영 백업을 덮어쓴다.** 그 스크립트는 키를 `<날짜>.sql.gz` 로 만들어서 목록 권한 없이도 덮어쓸 수 있다.

**운영 역할(`lirouti-ec2-role`)을 실수로 붙이지 않는다.** 이름이 비슷해 목록에서 헷갈린다. 붙는 순간 개발이 운영 버킷에 쓰고 지운다.

## 6) compose · Caddyfile 배치 + Redis 기동

**운영과 같은 파일 둘을 올린다.** 개발 전용 compose 는 없다.

```bash
scp deploy/docker-compose.prod.yml ubuntu@<개발서버EIP>:/tmp/dc.yml
scp deploy/Caddyfile               ubuntu@<개발서버EIP>:/tmp/Caddyfile
ssh ubuntu@<개발서버EIP> 'sudo mv /tmp/dc.yml /opt/app/docker-compose.yml \
  && sudo mv /tmp/Caddyfile /opt/app/Caddyfile'
```

**`Caddyfile` 을 빠뜨리면 알아채기 어렵다.** Caddy 는 설정 없이 떠서 80·443 이 아무것도 응답하지 않는데, 앱은 정상이라 배포 자체는 성공으로 끝난다. 워크플로가 https 확인을 한 번 더 하지만 그건 경고일 뿐 배포를 되돌리지 않는다.

### FCM 서비스 계정 키

compose 가 `/opt/app/secrets/firebase-adminsdk.json` 을 마운트한다. **소스 파일이 없으면 Docker 가
그 자리에 빈 디렉터리를 만들어 버리므로**, 켜지 않더라도 자리는 있어야 한다.

이 키는 `ENV_FILE` 에 넣지 않는다 — 평문 `key=value` 라 JSON 을 담기 부적합하다. **운영에 있는
것을 그대로 옮기면 된다.** 내용을 화면에 띄우지 않고 넘기는 방법은 이렇다.

```bash
mkdir -p /opt/app/secrets      # 서버에서 먼저

# 로컬 PC 에서. 운영 → 개발로 파이프만 통과한다.
ssh li-routi 'sudo cat /opt/app/secrets/firebase-adminsdk.json' \
  | ssh -T li-routi-dev 'sudo tee /opt/app/secrets/firebase-adminsdk.json >/dev/null
      sudo chown 10001:10001 /opt/app/secrets/firebase-adminsdk.json
      sudo chmod 400 /opt/app/secrets/firebase-adminsdk.json'
```

**소유자를 `10001` 로 맞추는 것이 중요하다.** 컨테이너의 `appuser` UID 이고, 운영도 같다.
`ubuntu` 소유로 두면 컨테이너가 읽지 못한다.

> **개발에서 FCM 을 켜도 된다.** 푸시 대상은 `fcm_device` 테이블에 등록된 기기인데 그 표는
> 개발 DB 에 있고 운영과 별개다. 즉 **개발 서버에 등록한 테스터 기기로만 가고 운영 사용자에게는
> 가지 않는다.** 켜려면 `ENV_FILE` 에 세 줄을 더한다.
>
> ```
> FCM_ENABLED=true
> FCM_PROJECT_ID=lirouti
> FCM_CREDENTIALS_HOST_PATH=/opt/app/secrets/firebase-adminsdk.json
> ```
>
> 감수할 점은 **운영과 같은 Firebase 프로젝트를 쓴다**는 것이다. 한 기기가 양쪽에 등록하면
> 알림을 두 번 받는다. 나누려면 별도 프로젝트와 서비스 계정이 필요한데 테스트 목적에는 과하다.

`.env` 는 아직 없다 — 첫 배포 때 `ENV_FILE` 시크릿으로 생성된다. 그래서 **여기서는 `redis` 만 올려
컨테이너 런타임이 정상인지 확인한다.**

```bash
cd /opt/app
docker compose up -d redis     # .env 가 없어 변수 경고가 뜨지만 redis 는 자격증명을 안 쓴다
docker compose ps              # healthy 확인
docker compose exec -T redis redis-cli ping    # PONG
```

> ⚠️ **`db` 를 여기서 올리면 안 된다.** MySQL 은 `MYSQL_ROOT_PASSWORD`·`MYSQL_PASSWORD` 를
> **데이터 디렉터리를 처음 만들 때 한 번만** 읽는다. `.env` 없이 올리면 빈 비밀번호로 초기화를
>시도하다 죽고, 임시 값으로 올리면 **그 값이 볼륨에 굳는다.** 나중에 배포가 `.env` 를 진짜 값으로
>덮어써도 DB 는 옛 비밀번호를 그대로 갖고 있어 앱이 접속하지 못한다.
>
> 이미 그렇게 올렸다면 볼륨을 지우고 다시 시작해야 한다 — `docker compose down -v`.
>
> **`db` 와 `app` 은 첫 배포가 함께 올린다.** 이미지 태그(`APP_IMAGE_TAG`)도 배포 워크플로가
> `.env` 에 써 넣는 구조라, 손으로 올리면 다음 배포와 어긋난다.

이어서 **GHCR 로그인**을 한다. 없으면 배포가 `docker compose pull` 에서 실패한다.
`ubuntu` 사용자로 해야 한다 — 배포 워크플로가 그 사용자로 SSH 해서 pull 하기 때문이다.

새 PAT(`read:packages`)를 발급해도 되고, **운영 서버에 이미 있는 자격을 그대로 옮겨도 된다.**
아래는 토큰을 화면에 띄우지 않고 옮기는 방법이다.

```bash
# 로컬 PC 에서. 운영 → 개발로 파이프만 통과하고 어디에도 남지 않는다.
ssh li-routi 'python3 -c "
import json,base64,os
d=json.load(open(os.path.expanduser(\"~/.docker/config.json\")))
print(base64.b64decode(d[\"auths\"][\"ghcr.io\"][\"auth\"]).decode(), end=\"\")
"' | ssh -T li-routi-dev 'read -r line; printf "%s" "${line#*:}" \
    | docker login ghcr.io -u "${line%%:*}" --password-stdin'
```

확인은 이미지를 받지 않고 매니페스트만 본다.

```bash
ssh li-routi-dev 'docker manifest inspect ghcr.io/li-routi/li-routi_backend:latest | grep architecture'
#   "architecture": "arm64"   ← 이 서버가 aarch64 라 이 값이어야 실행된다
```

> ⚠️ **이제 같은 PAT 가 서버 두 곳에 있다.** 자격은 `~/.docker/config.json` 에 평문(base64)으로
> 저장되므로, **토큰을 교체할 때 운영·개발 두 곳을 함께 갱신해야 한다.** 한쪽만 바꾸면 다른 쪽
> 배포가 `pull` 단계에서 조용히 실패한다 — 빌드까지는 성공하고 서버에서만 죽는다.
>
> 두 서버를 완전히 분리하고 싶으면 개발용 PAT 를 따로 발급한다. 그때는 만료·교체 주기를
> 각각 관리하게 되므로, 어느 쪽이든 **"두 곳"이라는 사실을 잊지 않는 것이 요점이다.**

## 7) GitHub 시크릿

`Settings → Secrets and variables → Actions` 의 **리포지토리 시크릿**에 셋을 넣는다.
Environments 는 쓰지 않는다 — 이유는 [운영 문서의 배포 대상은 둘이다](../README.md#배포-대상은-둘이다) 참고.

| 시크릿 | 값 |
| --- | --- |
| `DEV_SERVER_HOST` | 개발 서버 EIP |
| `DEV_SERVER_SSH_KEY` | 개발 키 파일 **내용 전체** (`-----BEGIN` ~ `-----END OPENSSH PRIVATE KEY-----`) |
| `DEV_ENV_FILE` | 아래 [`.env.dev.example`](./.env.dev.example) 기준으로 작성 |

```bash
gh secret set DEV_SERVER_HOST --body "<개발 EIP>"
gh secret set DEV_SERVER_SSH_KEY < ~/.ssh/li-routi-dev-key.pem
gh secret set DEV_ENV_FILE < <작성한 파일>       # 등록 후 그 파일은 지운다
```

**운영 시크릿(`SERVER_HOST`·`SERVER_SSH_KEY`·`ENV_FILE`)은 건드리지 않는다.** 이름이 달라 서로 섞이지 않고, 운영 배포 경로는 지금 그대로 동작한다.

> `deploy-dev.yml` 의 첫 스텝이 `DEV_SERVER_HOST` 가 비었는지와 **운영 주소와 같지 않은지**를 대조한다. 실수로 운영 EIP 를 넣으면 SSH 전에 멈춘다.

`DISCORD_WEBHOOK` 은 두 워크플로가 함께 쓰므로 그대로 둔다.

## 8) `main` 브랜치 정리 + 첫 배포

워크플로 자체는 이미 갈라져 있다(`deploy-prod.yml` · `deploy-dev.yml` · 공용 `build.yml`).
남은 것은 브랜치 상태와 실행 순서다.

### `main` 은 배포 소스였던 적이 없다

`main` 브랜치는 존재하지만 **초기 세팅 커밋에서 멈춰 있다.** Flyway 도입 이전이라 마이그레이션
파일이 한 장도 없다.

**여기서 커밋 수를 세면 위험도를 거꾸로 읽게 된다.**

```bash
git rev-list --count origin/main..origin/develop   # 320 — 겁나는 숫자지만 오해를 부른다
```

옛 `deploy.yml` 이 **`develop` 머지마다 운영에 배포**했으므로, **운영은 이미 `develop` 수준이다.**
`main` 은 그동안 아무 데도 쓰이지 않고 방치된 브랜치다. 실제로 봐야 할 것은 `main` 이 아니라
**운영이 지금 돌리는 커밋**이다.

```bash
ssh li-routi 'grep ^APP_IMAGE_TAG /opt/app/.env'          # 운영 실행본 sha
git log --oneline <그sha>..origin/develop                  # 진짜로 새로 나갈 것
git diff --name-status <그sha> origin/develop -- src/main/resources/db/migration/
```

첫 릴리스 PR 때 이렇게 재어 보니 **커밋 1개, 마이그레이션 0개, Java 변경 0개**였다. 320 이 아니었다.

그러니 `main` 을 `develop` 수준으로 올리는 **릴리스 PR(`develop` → `main`)** 은 "밀린 320개를
운영에 쏟는 일"이 아니라 **`main` 을 배포 가능한 기준선으로 세우는 일**이다. 이후로도 운영 배포는
이 경로로만 한다.

> **다음부터는 이 오해가 생기지 않는다.** `main` 이 한 번 정렬되고 나면 `origin/main..origin/develop`
> 이 곧 "운영에 새로 나갈 것" 과 같아진다. 위의 `APP_IMAGE_TAG` 대조가 필요한 것은 이번 한 번뿐이다.

### 순서

**시크릿과 DNS 가 브랜치 작업보다 먼저다.**

1. **`DEV_` 시크릿 셋 등록** (위 7단계)
2. 개발 서버 준비 확인 (`dig` 가 EIP 를 뱉는지, 80·443 개방, `redis` healthy, GHCR 로그인, `/opt/app/Caddyfile` 존재)
3. `develop` 에 아무거나 머지 → **개발 서버로 첫 배포**. 여기서 깨져도 운영은 영향이 없다
4. `https://lirouti-dev.kro.kr/health` 200 확인 (첫 인증서 발급에 수십 초 걸릴 수 있다)
5. 개발이 정상인 것을 확인한 뒤 **릴리스 PR(`develop` → `main`)** → 운영 배포

### 첫 배포에서 실제로 나온 값

재구축할 때 "이 정도면 정상"의 기준으로 쓴다.

| 구간 | 값 |
| --- | --- |
| 머지 → 이미지 push 완료 | 약 3분 (arm64 크로스 빌드) |
| `.env` 기록 → `db` healthy | 약 20초 |
| Flyway | **70개 적용**, 5.7초 (`V__` 전체 + `R__` 시드 8개) |
| 앱 부팅 | 40.6초 |
| 첫 인증서 발급 | 30초 (Let's Encrypt 429 → ZeroSSL 폴백 포함) |
| `https://.../health` | 200, 응답 0.2초 |

Flyway 로그의 `Integer display width is deprecated` 경고는 정상이다. MySQL 8.4 가 `int(11)` 같은 표기에 내는 것이고 동작에 영향이 없다.

> **`app` 과 `caddy` 에는 healthcheck 가 없다.** `docker compose ps` 의 상태만 보고 "떴다"고 판단하면 안 된다 — `Up` 은 프로세스가 살아 있다는 뜻일 뿐이다. 실제 확인은 `curl https://lirouti-dev.kro.kr/health` 로 한다.

### 이 검증이 덮지 못하는 것 — `R__` 덮어쓰기

개발 DB 는 **빈 상태에서** 70개를 처음부터 적용했다. 운영 DB 는 **이미 데이터가 있는 상태에서** 밀린 것만 이어 붙인다. **성격이 다른 실행이다.**

특히 `R__` 시드가 그렇다. 빈 표에 `INSERT` 하는 것과, 값이 들어 있는 행을 `ON DUPLICATE KEY UPDATE` 로 갈아치우는 것은 다른 일이다. **개발 서버는 앞의 것만 해 보고, 운영은 뒤의 것을 한다.**

그리고 뒤의 것은 가정이 아니다. 운영 `flyway_schema_history` 를 세어 보면 이미 여러 번 일어났다.

```sql
SELECT script, COUNT(*) AS runs, MIN(installed_on), MAX(installed_on)
FROM flyway_schema_history WHERE version IS NULL GROUP BY script ORDER BY runs DESC;
```

| 시드 | 재실행 | 기간 |
| --- | --- | --- |
| `R__seed_challenge.sql` | **4회** | 2026-07-26 ~ 08-13 |
| `R__seed_avatar_item.sql` | **4회** | 2026-08-11 ~ 08-13 |
| `R__seed_character.sql` | **2회** | 2026-08-13 |
| 나머지 넷 | 각 1회 | |

**한 달도 안 되는 사이에 마스터 데이터를 열 번 넘게 갈아엎었다는 뜻이다.** `R__` 는 파일 내용이 바뀌면 다시 도는 것이 정상 동작이므로, 이 기록 자체는 사고가 아니다. 요점은 **그 경로가 운영에서 상시로 열려 있고, 개발 서버가 그것을 재현하지 못한다**는 것이다.

그래서 **시드를 고친 배포를 운영에 넘길 때는 개발이 초록불이라는 사실이 근거가 되지 않는다.** 최소한 이것을 손으로 본다.

- 바뀐 시드가 건드리는 표에 **운영에만 있는 행**(백오피스 입력, 사용자 생성분)이 있는가
- `id` 를 바꾸지 않았는가 — 남의 보유·참여 행이 다른 것을 가리키게 된다
- 지우는 행이 있다면, 그 행을 참조하는 데이터가 운영에 있는가

> 개발 서버로 이것까지 잡으려면 **운영 백업을 복원한 DB 위에서** 배포해 봐야 한다. 그때 반드시 `fcm_device` 를 비워야 하는 이유는 아래 [운영 데이터를 개발로 가져올 때](#운영-데이터를-개발로-가져올-때) 를 볼 것.

---

# 운영과 달라서 생기는 것들

## 인증서를 우리가 통제하지 못한다

접속 방식은 운영과 같다 — `https://lirouti-dev.kro.kr`. 클라이언트에 평문 HTTP 예외(iOS ATS·Android cleartext)를 요청할 필요가 없고, `X-Forwarded-*` 도 Caddy 가 덮어써 주므로 위조 경로가 없다.

다만 **인증서를 Let's Encrypt 에서 받지 못하고 있다.** `kro.kr` 의 주 50장 한도가 차 있어 429 로 거절당했고, Caddy 가 ZeroSSL 로 폴백해 받아왔다. **이것은 개발과 운영의 차이가 아니다** — 운영도 같은 상태이고 만료가 더 빠르다. 자세한 것은 [2단계](#2-도메인) 참고.

그래서 **서버가 https 로 안 뜰 때 앱 코드부터 뒤지지 않는다.** 순서는 이렇다(운영도 같다).

```bash
docker compose logs caddy | grep -iE "obtain|error|rate"   # 1. 발급이 됐는지
echo | openssl s_client -connect lirouti-dev.kro.kr:443 \
  -servername lirouti-dev.kro.kr 2>/dev/null \
  | openssl x509 -noout -issuer -dates                     # 2. 누가 언제까지 발급했는지
dig +short lirouti-dev.kro.kr                              # 3. 도메인이 아직 우리 것인지
```

**3번을 빼먹기 쉽다.** 무료 도메인이라 갱신 주기가 있고, 만료되면 인증서 문제와 똑같은 증상이 난다. 갱신일을 아는 사람이 없으면 그것부터 확인할 것.

## 백업이 없다

개발 데이터는 잃어도 되는 것으로 본다. 되살려야 할 상황이면 DB 를 비우고 마이그레이션을 다시 태우는 편이 빠르다.

**`backup.sh` 를 개발 서버에 올리지 않는다.** 버킷명이 하드코딩(`lirouti-db-backup`)이고 키가 날짜뿐이라, 올려서 돌리는 순간 같은 키로 **운영 백업을 덮어쓴다.**

---

# 운영 데이터를 개발로 가져올 때

개발 서버에 쓸 만한 데이터가 없어 운영 백업을 복원하고 싶어지는 때가 온다. **그때 반드시 손봐야 하는 표가 있다.**

## `fcm_device` 는 비우고 넣는다

**푸시 알림의 격리는 구조가 아니라 데이터가 만들어 준다.**

FCM 토큰은 `(앱 설치본, Firebase 프로젝트)` 당 하나이고, **백엔드별로 다르지 않다.** 운영과 개발이 같은
Firebase 프로젝트를 쓰므로, 두 서버 모두 "그 프로젝트의 토큰이면 무엇이든" 보낼 자격이 있다.
서로에게 알림이 가지 않는 유일한 이유는 **각자 자기 DB 의 `fcm_device` 만 보기 때문**이다.

```java
findAllByMemberIdAndActiveTrue(memberId)   // 발송 대상은 자기 DB 에서만 나온다
```

그래서 운영 백업을 그대로 복원하면 **그 순간 개발 서버가 실사용자 전원의 토큰을 손에 쥔다.**
FCM 은 이것을 막지 못한다 — 자격이 유효하므로 배달은 정상적으로 이뤄진다. 개발에서 알림 기능을
건드리다 실사용자에게 테스트 알림이 나가는 경로가 여기다.

```sql
-- 복원 후 즉시. 개발 서버를 띄우기 전에 한다.
TRUNCATE TABLE fcm_device;
```

> **Firebase 프로젝트를 개발용으로 따로 파면 이 실수가 원천 차단된다**(자격이 달라 전송이 실패한다).
> 다만 그러려면 앱이 flavor 별로 `google-services.json` 을 나눠 가져야 하므로 클라이언트 작업이 앞선다.
> 그 요구가 생기기 전까지는 위 `TRUNCATE` 가 유일한 방어다.

## 개인정보를 옮기는 일이라는 점

전송 구간은 이제 운영과 같이 https 지만, **개발 서버가 운영보다 무른 곳이라는 사실은 그대로다.**
DB 비밀번호가 다르고(그래야 한다), 접근하는 사람이 더 많고, 실험적인 코드가 먼저 올라간다.
실사용자의 이메일·닉네임·인증 사진 기록을 그런 곳에 옮기는 것이므로, **필요한 표만 골라 넣거나
개인정보를 치환해서 넣는 편이 낫다.** 전체 복원이 편하다는 이유로 습관이 되면 곤란하다.

## 미디어는 따라오지 않는다

`challenge_verification.image_url` 등은 S3 **키**를 들고 있는데, 개발 버킷에는 그 오브젝트가 없다.
복원해도 사진은 전부 깨져 보인다. 정상이며, 이것 때문에 운영 버킷을 가리키게 만들면 안 된다 —
`deploy-dev.yml` 의 `Reject production bucket` 이 배포를 막는다.

# 체크리스트

- [ ] EC2 (Ubuntu 24.04 arm64, t4g.small, gp3 20GiB) + EIP 연결
- [ ] swap **4GB**, Docker + compose, `/opt/app`
- [ ] `lirouti-dev.kro.kr` A 레코드 → EIP, `dig` 로 전파 확인
- [ ] 보안그룹 신규 생성 — 22 · **80 · 443** 만 (8080 은 열지 않는다)
- [ ] S3 `lirouti-dev-bucket` 생성 (서울, SSE-S3)
- [ ] 퍼블릭 액세스 차단에서 `BlockPublicPolicy`·`RestrictPublicBuckets` 해제
- [ ] 버킷 정책 적용
- [ ] **`avatar/` 자산 sync**
- [ ] IAM 정책 → 역할 → 인스턴스 부착 (`BackupObjectWrite` 없음 확인)
- [ ] **`docker-compose.prod.yml` + `Caddyfile`** 배치 + FCM 키 배치(소유자 `10001`, `400`), `redis` healthy 확인 (`db`·`app`·`caddy` 는 첫 배포가 올린다)
- [ ] GHCR 로그인
- [ ] 리포지토리 시크릿 `DEV_SERVER_HOST` · `DEV_SERVER_SSH_KEY` · `DEV_ENV_FILE` (`LIROUTI_DOMAIN`·`LETSENCRYPT_EMAIL` 포함)
- [ ] (운영 데이터를 복원했다면) `fcm_device` 비웠는지 확인
- [ ] `develop` 머지로 개발 첫 배포 → `https://lirouti-dev.kro.kr/health` 200 확인
- [ ] 릴리스 PR(`develop` → `main`)로 `main` 따라잡기 → 운영 배포 확인
