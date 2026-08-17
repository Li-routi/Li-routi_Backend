# 개발 서버 구축 가이드

운영과 별개로 도는 개발 서버를 만드는 절차다. 운영 가이드([`deploy/README.md`](../README.md))를 대체하지 않고, **다른 점만** 적는다. 여기 없는 항목은 운영 문서를 그대로 따른다.

> **워크플로는 이미 갈라져 있다.** `deploy-prod.yml`(main) 과 `deploy-dev.yml`(develop) 이 들어가
> 있으므로, 남은 것은 아래 1~8단계다. **다 끝나기 전에는 `develop` 에 머지하지 않는다** — 배포가
> 절반쯤 진행된 뒤에 막히면 개발 서버가 어중간한 상태로 남는다.
>
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
| 도메인 | 있음 | **`lirouti-dev.kro.kr`** (무료 도메인) |
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

### 무료 도메인이라 감수하는 것 — 인증서 발급 한도

**`kro.kr` 은 Public Suffix List 에 없다.** PSL 에는 `co.kr`·`ne.kr`·`or.kr` 같은 것들과, 성격이 비슷한 무료 도메인 `duckdns.org` 는 등재돼 있는데 `kro.kr` 은 빠져 있다(직접 대조해 확인했다).

그래서 Let's Encrypt 는 우리 도메인의 "등록 도메인"을 `lirouti-dev.kro.kr` 이 아니라 **`kro.kr` 전체**로 본다. 등록 도메인당 **주 50장** 한도를 `kro.kr` 을 쓰는 모든 사람과 나눠 쓴다는 뜻이다. 막히면 로그에 이렇게 나온다.

```
too many certificates already issued for "kro.kr"
```

**우리가 고칠 수 있는 것이 아니므로 재배포로 풀리지 않는다.** 그때는 배포를 멈추고 `docker compose logs caddy` 를 본다. Caddy 는 Let's Encrypt 가 실패하면 ZeroSSL 로 자동 폴백하므로 대개 거기서 받아 온다. 양쪽 다 계속 실패하면 PSL 에 등재된 무료 도메인으로 옮기는 편이 빠르다.

> **운영은 이 문제가 없다.** 직접 등록한 도메인이라 한도를 우리만 쓴다. 개발에서만 감수하는 제약이다.
>
> 운영 도메인의 서브도메인(`dev.<운영도메인>`)을 쓰면 이 한도도 사라지고 추가 비용도 없다. 지금 그렇게 하지 않은 이유는 문서로 남아 있지 않으니, 옮길지 판단할 때 먼저 확인할 것.

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

### `main` 이 한참 뒤처져 있다

`main` 브랜치는 존재하지만 **초기 세팅 커밋에서 멈춰 있다.** 확인 방법은 이렇다.

```bash
git fetch origin main develop
git rev-list --count origin/main..origin/develop   # develop 에만 있는 커밋 수
```

이 상태로 `main` 에 푸시하면 **그 옛 코드가 운영에 배포된다.** 게다가 운영 DB 는 최신 마이그레이션까지
적용돼 있는데 jar 이 옛것이면 Flyway `validate` 가 어긋남을 잡아 **부팅이 막히고 재시작 루프**가 된다.

그래서 `main` 을 먼저 `develop` 수준으로 올린다 — **릴리스 PR(`develop` → `main`)** 을 연다.
이후로도 운영 배포는 이 경로로만 한다.

### 순서

**시크릿과 DNS 가 브랜치 작업보다 먼저다.**

1. **`DEV_` 시크릿 셋 등록** (위 7단계)
2. 개발 서버 준비 확인 (`dig` 가 EIP 를 뱉는지, 80·443 개방, `redis` healthy, GHCR 로그인, `/opt/app/Caddyfile` 존재)
3. `develop` 에 아무거나 머지 → **개발 서버로 첫 배포**. 여기서 깨져도 운영은 영향이 없다
4. `https://lirouti-dev.kro.kr/health` 200 확인 (첫 인증서 발급에 수십 초 걸릴 수 있다)
5. 개발이 정상인 것을 확인한 뒤 **릴리스 PR(`develop` → `main`)** → 운영 배포

---

# 운영과 달라서 생기는 것들

## 인증서를 우리가 통제하지 못한다

접속 방식은 운영과 같다 — `https://lirouti-dev.kro.kr`. 클라이언트에 평문 HTTP 예외(iOS ATS·Android cleartext)를 요청할 필요가 없고, `X-Forwarded-*` 도 Caddy 가 덮어써 주므로 위조 경로가 없다.

다만 **인증서 발급 한도를 `kro.kr` 전체와 나눠 쓴다**(2단계 참고). 한도에 걸리면 우리가 할 수 있는 일이 없으므로, 개발 서버가 https 로 안 뜰 때 앱 코드부터 뒤지지 말고 `docker compose logs caddy` 를 먼저 본다.

**도메인이 만료되면 같은 증상이 난다.** 무료 도메인이라 갱신 주기가 있다 — 갱신일을 알고 있는 사람이 없으면 그것부터 확인할 것.

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
