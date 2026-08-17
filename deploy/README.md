# 배포 가이드 (EC2 · Docker · GHCR)

챌린지 백엔드는 **GitHub Actions에서 arm64 Docker 이미지를 빌드 → GHCR에 push → EC2(t4g.small)가 pull해서 docker compose로 실행**한다. 서버에서 Gradle 빌드를 하지 않는다(2GB 메모리 보호).

## 파일 배치

| 위치 | 파일 | 커밋 | 역할 |
| --- | --- | --- | --- |
| 레포 | `Dockerfile` | O | 실행 jar를 담는 이미지 정의(빌드 스테이지 없음) |
| 레포 | `.github/workflows/test.yml` | O | 테스트 + 워크플로 lint (브랜치 push + 배포 워크플로가 workflow_call로 호출) |
| 레포 | `.github/workflows/build.yml` | O | 이미지 빌드·push (배포 워크플로 둘이 공유하는 재사용 워크플로) |
| 레포 | `.github/workflows/deploy-prod.yml` | O | **main** 머지 시 운영 배포 |
| 레포 | `.github/workflows/deploy-dev.yml` | O | **develop** 머지 시 개발 배포 |
| 레포 | `docker-compose.local.yml` | O | 로컬 개발용 DB·Redis (앱은 IDE/bootRun) |
| 레포 | `deploy/docker-compose.prod.yml` | O(레퍼런스) | 서버 운영 compose 템플릿 |
| 레포 | `deploy/.env.example` | O | 서버 `.env` 키 템플릿(실값 없음) |
| 레포 | `deploy/backup.sh` | O(레퍼런스) | DB 백업 스크립트 |
| 레포 | `deploy/dev/` | O | **개발 서버** 전용 IAM·버킷 정책과 구축 가이드 — [`deploy/dev/README.md`](./dev/README.md) |
| **서버** | `/opt/app/docker-compose.yml` | **X** | 위 prod 템플릿을 복사한 실제 파일 |
| **서버** | `/opt/app/.env` | **X** | **배포 때 자동 생성됨** — GitHub Secret `ENV_FILE` 내용 + `APP_IMAGE_TAG` |
| **서버** | `/opt/app/backup.sh` | **X** | 위 스크립트 복사 |

> **런타임 설정의 단일 진실 공급원은 GitHub Secret `ENV_FILE`이다.** 배포할 때마다 워크플로가 서버 `/opt/app/.env`를 이 내용으로 새로 쓴다.
> 따라서 **서버에서 `.env`를 직접 고쳐도 다음 배포에서 원복된다.** 값을 바꾸려면 `ENV_FILE` 시크릿을 수정하고 재배포한다.
> :warning: 시크릿 변경은 자동 배포 트리거가 아니다(GitHub은 시크릿 변경 이벤트를 제공하지 않는다). 수정 후 해당 브랜치에 머지하거나 Actions에서 배포를 수동 실행해야 반영된다.

> **`ENV_FILE`은 배포 대상마다 따로 있다.** 아래 [배포 대상은 둘이다](#배포-대상은-둘이다) 참고 — 운영은 `ENV_FILE`, 개발은 `DEV_ENV_FILE` 이고 **둘 다 리포지토리 시크릿**이다(GitHub Environments 를 쓰지 않는다).

### 배포 대상은 둘이다

| 대상 | 브랜치 | 워크플로 | 시크릿 | 서버 |
| --- | --- | --- | --- | --- |
| 운영 | `main` | `deploy-prod.yml` | `SERVER_HOST` · `SERVER_SSH_KEY` · `ENV_FILE` | 운영 EC2 (도메인·Caddy) |
| 개발 | `develop` | `deploy-dev.yml` | `DEV_SERVER_HOST` · `DEV_SERVER_SSH_KEY` · `DEV_ENV_FILE` | 개발 EC2 (도메인·Caddy, 구성 동일) |

**두 서버의 구성은 같다.** compose 도 `deploy/docker-compose.prod.yml` 하나를 양쪽에 복사해 쓰고, 차이는 전부 `.env`(도메인·버킷·시크릿)가 흡수한다. 개발만 느슨하게 두면 "운영보다 먼저 깨지는 자리"라는 목적이 사라지기 때문이다.

**둘 다 리포지토리 시크릿이고, 이름으로 갈린다.** GitHub Environments 를 쓰지 않는다.

> **왜 Environments 가 아닌가.** Environment 시크릿은 리포지토리 시크릿을 **덮어쓰는** 구조라, 두 환경에 같은 이름(`SERVER_HOST` 등)을 두면 **환경 설정을 빠뜨렸을 때 덮어쓸 것이 없어 리포지토리 값이 조용히 쓰인다.** 이 저장소의 리포지토리 시크릿은 운영 서버 주소와 키라, 그 실수가 곧 "개발 배포가 운영 서버에 붙는" 사고가 된다.
>
> `DEV_` 접두어로 이름을 갈라 두면 그 경로 자체가 없다 — 시크릿을 빠뜨리면 폴백이 아니라 **빈 값**이 되고, `deploy-dev.yml` 의 첫 스텝(`Verify dev target`)이 막는다. 그 스텝은 `DEV_SERVER_HOST` 가 비었는지, 그리고 **운영 주소와 같지 않은지**까지 대조한다.
>
> 대신 Environments 가 주는 것(배포 승인자 지정, 환경별 배포 이력)은 포기했다. 필요해지면 그때 옮긴다 — 그때는 위 함정을 알고 옮기게 된다.

## GitHub 설정 (1회)

1. **Secrets** (repo → Settings → Secrets and variables → Actions):
   - `SERVER_HOST` — EC2 퍼블릭 IP(EIP)
   - `SERVER_SSH_KEY` — 접속용 pem 개인키 전문
   - `ENV_FILE` — **서버 `.env` 내용 전체**(`deploy/.env.example` 참고). 배포 때 서버에 그대로 기록된다.
     - `APP_IMAGE_TAG` 줄은 **넣지 않는다** — 워크플로가 배포 커밋 sha로 덧붙인다.
     - `DB_ROOT_PASSWORD`·`DB_PASSWORD`는 **현재 서버에서 쓰고 있는 값 그대로** 넣어야 한다. MySQL은 볼륨 최초 초기화 때의 비밀번호를 유지하므로, 다른 값을 넣으면 앱만 새 비밀번호로 접속하다 실패한다.
     - 여러 줄 값이 전달 과정에서 깨지면 base64로 우회한다. `base64 -i .env | pbcopy`로 인코딩해 등록하고, 워크플로의 `printf '%s\n' "$ENV_FILE" > "$tmp"` 줄을 아래로 바꾼다(디코딩 실패 시 기존 `.env`가 남도록 임시 파일에 먼저 쓴다):
       ```bash
       printf '%s' "$ENV_FILE" | base64 --decode > "$tmp"
       ```
   - `DISCORD_WEBHOOK` — (선택) 배포 결과 알림. 없으면 알림만 건너뛴다.
2. **GHCR 이미지 공개 범위**: **기본은 private 유지 + 서버에서 1회 로그인**(팀 합의). 이미지를 공개하지 않고, 첫 배포부터 바로 pull된다. `read:packages` 권한 PAT를 하나 만들어, **토큰을 명령행/히스토리에 남기지 않도록** 프롬프트로 입력받아 로그인한다:
   ```bash
   read -rsp 'GHCR PAT(read:packages): ' PAT && echo "$PAT" | docker login ghcr.io -u <github사용자명> --password-stdin; unset PAT
   ```
   > ⚠️ 로그인하면 PAT가 서버 `~/.docker/config.json`에 **평문(base64)으로 저장**된다(EC2엔 credential store가 없음). **`read:packages` 최소권한**으로만 만들고 **만료를 짧게(예: 90일) 두어 주기적으로 교체**할 것. 유출돼도 이미지 읽기 권한뿐이다.
   - (대안) 관리 편의를 원하면 패키지를 **public으로 전환**(repo → Packages → 해당 패키지 → Package settings → Change visibility → Public). 이 경우 서버 로그인은 필요 없지만, 컴파일된 이미지가 공개되고 패키지는 첫 배포 push 후에 생기므로 "첫 배포 → public 전환 → 재실행" 순서가 한 번 필요하다.

## 서버 최초 세팅 (1회, Ubuntu 24.04 arm64)

> **전제**: EC2(t4g.small)가 떠 있고, 아래 [AWS / 네트워크] 절의 **IAM Role(instance profile)**·**보안그룹**을 먼저 맞춰둔다. 여기부터는 서버에 SSH로 접속(`ssh -i <pem> ubuntu@<서버IP>`)해서 하는 작업이다.

### 1) Docker + compose 플러그인 설치 · swap 4GB

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker ubuntu     # 그룹 반영을 위해 로그아웃 후 재접속(또는 `newgrp docker`)
docker compose version             # v2 플러그인 정상 확인

# swap 4GB — 메모리 예산(≈1.6GB+OS)이 빠듯해 피크 시 OOM killer가 컨테이너를 죽이는 것 방지(완충재).
# 처음에는 2GB로 잡았다가 늘렸다. 운영이 평상시에도 700MB 안팎을 실제로 쓰고 있어 2GB로는 여유가 없다.
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅에도 유지
```

### 2) 앱 폴더 생성

```bash
sudo mkdir -p /opt/app && sudo chown ubuntu:ubuntu /opt/app
cd /opt/app
```

### 3) 배포 파일 2개를 서버로 올린다

레포 `deploy/`의 템플릿을 `/opt/app`에 **아래 이름으로** 배치한다. 레포가 private이라 서버에서 직접 clone하면 인증이 필요하므로, **로컬 PC에서 scp**가 가장 간단하다.

```bash
# 로컬 PC의 레포 루트에서 실행 (<pem>·<서버IP>는 본인 값)
scp -i <pem> deploy/docker-compose.prod.yml ubuntu@<서버IP>:/opt/app/docker-compose.yml
scp -i <pem> deploy/Caddyfile               ubuntu@<서버IP>:/opt/app/Caddyfile
scp -i <pem> deploy/backup.sh               ubuntu@<서버IP>:/opt/app/backup.sh
```

> **compose 와 Caddyfile 은 배포 워크플로가 갱신하지 않는다.** `.env` 와 달리 이 둘은 서버에 있는 파일을 그대로 쓴다. 레포에서 고쳤다면 **머지 전에 다시 scp** 해야 반영된다.
>
> 파일명 매핑 주의: `docker-compose.prod.yml` → 서버에선 **`docker-compose.yml`**. 배포 워크플로가 `cd /opt/app` 후 이 파일명 그대로를 쓴다.
> `.env`는 올리지 않는다 — 배포 때 `ENV_FILE` 시크릿 내용으로 자동 생성된다(4번 참고).

### 4) `ENV_FILE` 시크릿 작성 (런타임 설정)

서버 `.env`는 **배포가 만들어 준다.** 손으로 만들지 말고, 아래 내용을 GitHub Secret `ENV_FILE`에 등록한다(repo → Settings → Secrets and variables → Actions).

`deploy/.env.example`을 기준으로 값을 채우되, **`APP_IMAGE_TAG` 줄은 넣지 않는다**(워크플로가 배포 커밋 sha로 덧붙인다).

| 키 | 설명 |
| --- | --- |
| `DB_ROOT_PASSWORD` · `DB_PASSWORD` | MySQL 비밀번호(임의 강문자열). **볼륨 최초 초기화 때 각인**되므로, 이미 DB를 띄운 뒤라면 그때 쓴 값과 반드시 같아야 한다 |
| `JWT_SECRET` | 32바이트 이상 랜덤 — `openssl rand -base64 48` |
| `KAKAO_APP_ID` | 카카오 앱 ID |
| `GOOGLE_WEB_CLIENT_ID` · `GOOGLE_ALLOWED_ISSUERS` | 구글 OAuth |
| `AWS_S3_BUCKET` · `AWS_REGION` | S3 (자격증명은 IAM Role로 자동 획득 — 여기 두지 않는다) |
| `FCM_ENABLED` · `FCM_PROJECT_ID` | (선택) 켜려면 8번 섹션에서 서비스 계정 키 파일도 함께 배치해야 한다 |

서버 쪽에서는 백업 스크립트 실행 권한만 준다:

```bash
cd /opt/app && chmod +x backup.sh
```

### 5) GHCR 로그인 (B안, 1회)

> **이 자격은 서버마다 따로 필요하다.** 개발 서버를 만든 뒤로는 운영·개발 두 곳에 같은(또는 각각의) PAT 가 저장돼 있다. **교체할 때 두 곳을 함께 갱신한다** — 한쪽만 바꾸면 다른 쪽 배포가 `pull` 에서 실패한다. 옮기는 방법은 [`deploy/dev/README.md`](./dev/README.md) 6단계 참고.

위 [GitHub 설정] 2번과 동일하다. `read:packages` PAT로 한 번 로그인해두면 이후 `docker compose pull`이 계속 동작한다. 자격은 `~/.docker/config.json`에 **평문(base64)으로 저장**되므로(→ 재부팅해도 유지되지만 at-rest 위험 존재), **최소권한·짧은 만료로 만들고 주기적으로 교체**한다.

```bash
read -rsp 'GHCR PAT(read:packages): ' PAT && echo "$PAT" | docker login ghcr.io -u <github사용자명> --password-stdin; unset PAT
```

### 6) 첫 배포는 머지로 (서버에서 `up app` 수동 실행 안 함)

**대상 브랜치에 머지**되면 Actions가 이미지 빌드 → GHCR push → 서버 SSH 접속 → `.env` 생성(`ENV_FILE` 시크릿) → `APP_IMAGE_TAG`를 그 커밋 sha로 고정 → `docker compose pull && up -d`까지 자동으로 한다. 운영은 `main`, 개발은 `develop`이다.

서버를 준비만 해두고 머지하면 첫 배포가 진행된다. 즉시 한 번 돌리고 싶으면 Actions → `Deploy to production`(또는 `Deploy to development`) → Run workflow로 수동 실행해도 된다. **다른 브랜치를 골라 실행하면 `Validate deploy ref`가 막는다.**

워크플로가 배포 후 `http://localhost:8080/health` 응답을 최대 ~90초 확인하고, 안 뜨면 배포 실패로 처리한다. 이 경로를 쓰는 이유는 인증이 필요 없기 때문이다 — 업무 API는 전부 JWT를 요구하므로(#77) 헬스체크에 쓰면 정상 기동한 앱도 403으로 실패 처리된다. 서버에서 직접 로그를 보려면:

```bash
cd /opt/app && docker compose logs -f app
```

### 7) 백업 cron 등록

아래 [AWS / 네트워크] 절의 cron 항목 참고 (매일 04:00 KST = **19:00 UTC**).

**서버 시간대가 UTC다.** 스케줄을 `0 4`로 적으면 새벽이 아니라 낮 1시에 돈다. 백업 파일 이름이 한국 날짜가 되도록 `backup.sh` 안에서 `TZ`를 한국 시간으로 고정한다.

**cron은 PATH를 `/usr/bin:/bin`으로만 준다.** `aws`는 snap으로 설치돼 `/snap/bin`에 있어 그대로 두면 덤프를 다 뜬 뒤 마지막 업로드에서 죽는다. `backup.sh`가 PATH에 `/snap/bin`을 더해 이 문제를 스스로 막는다.

**백업 버킷의 버전 관리를 켠다** (2026-08-03 KST 활성화 완료). 앱과 백업이 같은 인스턴스 역할을 쓰기 때문에 앱이 침해되면 백업을 덮어쓸 수 있다 — 이유와 켜는 경로, 서버에서 확인하는 방법은 [2) 정책 생성](#2-정책-생성)의 경고를 볼 것.

등록은 **손으로 `crontab -e`를 열지 말고 아래를 붙여넣는다.** 두 번 실행해도 같은 줄이 겹치지 않는다. 인스턴스를 교체하면 cron은 그 인스턴스와 함께 사라지므로, **재구축 절차에서 이 단계를 반드시 다시 밟아야 한다.**

```bash
( crontab -l 2>/dev/null | grep -vF '/opt/app/backup.sh'; \
  echo '0 19 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1' ) | crontab -
crontab -l                      # 등록 확인
/opt/app/backup.sh              # 한 번 돌려서 업로드까지 되는지 확인
```

> 2026-08-03 KST **등록 완료.** `0 19 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1`
> 수동 실행과 cron 최소 환경(`env -i PATH=/usr/bin:/bin`) 양쪽에서 업로드까지 성공을 확인했다(`2026-08-03.sql.gz`, gzip 11.7 KiB).
>
> **이 문서의 날짜는 모두 KST다.** 서버·GitHub·S3 로그가 UTC라 아홉 시간 차이로 하루가 어긋나 보일 수 있다.

### 8) FCM 서비스 계정 키 배치 (선택, Push 알림을 켤 때만)

`FCM_ENABLED=true`로 켜려면, Firebase 콘솔(프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성)에서 받은
JSON 키 파일을 서버에 올려야 한다. `.env`(`ENV_FILE`)에는 넣지 않는다 — 평문 key=value라 JSON을
담기 부적합해서 Caddyfile처럼 별도 scp로 관리한다.

```bash
# 로컬 PC에서 실행 (<pem>·<서버IP>·<로컬키파일경로>는 본인 값 — Firebase가 준 파일명을
# 그대로 써도 되고, 다른 이름이어도 상관없다. 서버 쪽 대상 파일명만 firebase-adminsdk.json으로 고정)
ssh -i <pem> ubuntu@<서버IP> 'mkdir -p /opt/app/secrets'
scp -i <pem> <로컬키파일경로>.json ubuntu@<서버IP>:/opt/app/secrets/firebase-adminsdk.json
```

권한을 조인다. 컨테이너 안의 앱은 `appuser`(UID **10001**, [Dockerfile](../Dockerfile) 참고)로 도는데,
바인드 마운트는 호스트의 숫자 UID를 그대로 컨테이너에 넘긴다. `chmod 600`만 해두면 소유자(`ubuntu`,
보통 UID 1000)만 읽을 수 있어 **컨테이너 안의 appuser(10001)는 못 읽고 부팅이 실패한다** — 반드시
파일 소유자를 10001로 바꿔야 한다:

```bash
# 서버에서 실행
sudo chown 10001:10001 /opt/app/secrets/firebase-adminsdk.json
sudo chmod 400 /opt/app/secrets/firebase-adminsdk.json   # 그 UID만 읽기 전용으로 접근 가능
```

> **`FCM_ENABLED=false`인 동안은 이 파일이 없어도 무방하다.** 다만 `docker-compose.prod.yml`이
> 이 경로를 **파일 볼륨**으로 마운트하므로, 파일을 올리기 전에 먼저 `docker compose up`을 돌리면
> Docker가 그 경로에 **빈 디렉터리**를 만들어 버린다. 그 상태에서는 나중에 진짜 키 파일을 올려도
> 컨테이너 안에서 디렉터리로 보여 앱이 못 읽는다 — `sudo rm -rf /opt/app/secrets/firebase-adminsdk.json`으로
> 지우고 위 scp를 다시 해야 한다.

## AWS / 네트워크

- **EC2 instance profile(IAM Role)** 부착: 아래 [IAM instance profile 부착] 절 참고. access key를 서버에 두지 않는다.
- **보안그룹 인바운드**:
  - `80`·`443` — Caddy. `80` 은 인증서 발급(HTTP-01)과 https 리다이렉트에 필요하다.
  - `8080` — **열지 않는다.** 외부 트래픽은 Caddy 를 거친다(compose 도 `127.0.0.1` 에만 바인딩).
  - `22`(SSH) — **`0.0.0.0/0` 개방 + 키 인증 전용(비밀번호 로그인 비활성 확인)**. 배포가 GitHub Actions 러너에서 SSH로 접속하는데 러너 IP가 유동적이라 대역 제한이 어렵다. ED25519 키 인증만 허용하는 전제로 1개월 한시 운영에 한해 전체 개방한다.
    > ⚠️ **한시 조치다.** 데모데이 이후 유지보수를 이어가 실 운영으로 전환하는 시점에 **IP 화이트리스트 정책을 도입**한다(배포 시 러너의 현재 IP만 보안그룹에 임시 허용 → 배포 종료 후 회수하는 동적 화이트리스트). 그때까지는 개방 종료일을 트래킹하고, 철거 시 인스턴스와 함께 정리한다.
  - `3306`(MySQL)·`6379`(Redis) — **열지 않는다**(compose 내부 네트워크 전용).
- **S3 버킷**: `lirouti-prod-bucket`(미디어)·`lirouti-db-backup`(백업) 이름으로 생성(비공개). 서버 `.env`의 `AWS_S3_BUCKET`·`backup.sh`의 `BUCKET`을 이 이름과 일치시킨다.
- **백업 cron** (매일 04:00 KST = 19:00 UTC): `crontab -e` → `0 19 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1`
  > 서버가 UTC라 시각을 UTC로 적는다. `0 4`로 적으면 낮 1시에 돈다.

## 도메인 · HTTPS (Caddy)

`Caddy(80/443) → app(8080)` 구성이다. Caddy 가 Let's Encrypt 인증서를 자동 발급·갱신하므로 인증서 관리 부담은 없다.

**개발 서버도 같은 구성이다.** 도메인만 다르고(`LIROUTI_DOMAIN`), Caddyfile·compose·절차는 아래 그대로다.

> ⚠️ **운영 인증서는 Let's Encrypt 가 아니라 ZeroSSL 이 발급했다.** 운영 도메인 `lirouti.kro.kr` 이 무료 도메인이고, `kro.kr` 이 Public Suffix List 에 없어 Let's Encrypt 가 등록 도메인을 `kro.kr` 전체로 본다. 주 50장 한도를 그 서비스 사용자 전원과 나눠 쓰는데, 발급 시점에 이미 차 있어 429 를 맞았다(운영 Caddy 로그에 남아 있다).
>
> Caddy 가 자동으로 ZeroSSL 로 넘어가 받아왔지만, **그 폴백은 우리가 설정한 것이 아니라 Caddy 의 기본 동작이다.** 갱신 때 양쪽 다 실패하면 운영이 https 로 죽는다. 개발보다 운영의 만료가 먼저 온다.
>
> 배경·확인 방법·해결 선택지는 [`deploy/dev/README.md`](./dev/README.md#2-도메인) 에 한데 적었다. 개발 서버 문서에 있지만 **운영에 그대로, 더 큰 무게로 해당한다.**

### 순서를 지켜야 한다

인증서 발급은 **DNS 와 방화벽이 먼저 맞아 있어야** 성공한다. 순서를 어기면 발급이 실패하는데, Let's Encrypt 는 인증 실패를 **계정·도메인 조합마다 시간당 5회**로 제한한다(12분에 하나씩 회복). 원인을 고치기 전에 재시도를 반복하면 그 한도를 다 써버린다.

> 흔히 말하는 "주당 5회"는 **다른 제한**이다 — 같은 도메인 조합의 인증서를 중복 발급할 때 걸린다. 발급이 반복 실패하는 상황에서 걸리는 것은 위의 시간당 제한이다.

```text
1. 도메인 발급 → A 레코드를 서버 EIP 로 연결
2. dig +short <도메인> 이 EIP 를 뱉는지 확인      ← 여기서 안 맞으면 진행 금지
3. 보안그룹 인바운드 80 · 443 개방               ← 80 은 HTTP-01 챌린지에 필요
4. ENV_FILE 에 LIROUTI_DOMAIN 추가
5. Caddyfile · docker-compose.yml 을 서버로 scp
6. 배포
```

4번을 빠뜨리면 배포 워크플로가 **시작 전에 막는다**(`Check Caddy env vars are set`). Caddy 가 뜨지 못한 채 앱만 올라가 80·443 이 먹통이 되는 상태를 피하려는 것이다.

### 환경변수

| 키 | 필수 | 설명 |
| --- | --- | --- |
| `LIROUTI_DOMAIN` | ✅ | 서비스 도메인. 없거나 형식이 틀리면 배포가 중단된다 |
| `LETSENCRYPT_EMAIL` | ✅ | 만료 임박·발급 실패 알림 주소 |

> **`LETSENCRYPT_EMAIL` 은 선택이 아니다.** 값이 비면 인자 없는 `email` 지시어가 되어 Caddy 가 설정 파싱에서 죽는다. 실제 Caddy 로 확인한 동작이다.
>
> ```
> Error: adapting config using caddyfile: parsing caddyfile tokens for 'email':
> wrong argument count or unexpected line ending after 'email'
> ```
>
> 배포 워크플로가 두 값의 존재와 도메인 형식을 먼저 확인하고, CI 는 `caddy validate` 로 Caddyfile 자체를 검사한다.

### 8080 은 두 겹으로 닫혀 있다

외부 트래픽은 Caddy(**80**·**443**) 로만 들어온다. 앱 포트는 **보안그룹**과 **compose 의 `127.0.0.1` 바인딩** 두 곳에서 막는다.

> `80` 도 외부 진입점이다. 인증서 발급(HTTP-01)과 https 리다이렉트를 받으므로 **발급이 끝난 뒤에도 닫으면 안 된다** — 닫으면 갱신이 조용히 실패하고 90일 뒤 인증서가 만료된다.

```yaml
    ports:
      - "127.0.0.1:8080:8080"
```

**한 겹으로 두지 않는 이유가 있다.** 앱에 `server.forward-headers-strategy: framework` 가 켜져 있는데, 이 설정은 `X-Forwarded-Proto`·`X-Forwarded-Host` 를 **보낸 주체를 따지지 않고 그대로 믿는다.** Caddy 를 거친 요청은 `header_up` 이 그 헤더를 덮어써서 안전하지만, 앱에 **직접** 닿을 수 있으면 위조가 가능하다.

즉 이 설정의 안전성은 "앱에 직접 닿을 수 없다"에 기대고 있다. 보안그룹 하나에만 의존하면, 나중에 누가 디버깅하려고 8080 을 잠깐 열었다가 그대로 두는 것만으로 위조 경로가 조용히 되살아난다. 커널 수준 바인딩이 그 실수를 막는다.

**`8080` 을 다시 공개로 여는 변경은 `forward-headers-strategy` 를 함께 검토하고 해야 한다.**

배포 헬스체크는 서버 안에서 `http://localhost:8080/health` 를 치므로 루프백 바인딩으로도 그대로 동작한다. Caddy 는 compose 내부 네트워크(`app:8080`)로 붙어 이 매핑과 무관하다.

### 확인

```bash
curl -I https://<도메인>/health          # 200, 인증서 경고 없음
curl -I http://<도메인>/health           # 308 → https 리다이렉트
docker compose logs caddy | tail -20     # certificate obtained 류의 줄
```

인증서가 안 나오면 **재시작을 반복하지 말고** 원인부터 본다. 대개 A 레코드 미전파 아니면 80 번 미개방이다.

## 미디어 서빙 (#66)

**업로드와 조회는 다른 경로다.** 업로드는 presigned URL의 서명이 권한을 들고 있어 비공개 버킷에도 쓸 수 있지만, 조회는 사용자 기기가 S3에 보내는 **익명 GET**이라 아무 권한이 없다. IAM 역할(#58)은 *서버*의 권한이라 여기에 개입하지 못한다 — 역할을 아무리 잘 붙여도 사진은 403이다.

### 미디어마다 청중이 다르다

| 미디어 | 앱에서 의도한 청중 | **실제 접근 가능 범위** | 서빙 방식 |
| --- | --- | --- | --- |
| 챌린지 인증 | 로그인 사용자 전체(공개 피드) | **URL을 아는 누구나**(비로그인 포함) | 공개 prefix |
| 프로필 | 앱 전체(닉네임 옆 아바타) | 〃 (구현 시) | 공개 prefix 예정 |
| 그룹 루틴 인증 | 그 방 멤버만 | 서명 URL을 받은 사람, 만료 전까지 | presigned GET |
| 개인 루틴 인증 | 본인만 | **아무도 못 본다** | **조회 경로 없음** |
| 그룹 채팅 이미지 | 그 방 멤버만 | (구현 시) 서명 URL을 받은 사람 | presigned GET 예정 |

> **개인 루틴 인증에는 조회 API를 두지 않았다.** 그 사진을 보여 주는 화면이 없다는 기획 판단이다. 그래서 사진은 필수로 받는데(`image_url NOT NULL`) 꺼내 보는 경로가 없어 **S3 용량만 차지한다.** 완료 판정은 인증 행의 존재로 하므로 사진이 없어도 화면은 똑같이 동작한다 — 화면을 만들든 사진을 선택으로 낮추든 정리가 필요하다.

> ⚠️ **가운데 두 열이 다르다는 점이 중요하다.** 공개 prefix는 버킷 정책이 `Principal: "*"`에 `s3:GetObject`를 허용하는 것이라, **로그인 여부와 무관하게 URL을 아는 모든 인터넷 주체가 읽을 수 있다.** 앱이 피드를 로그인 사용자에게만 보여주는 것과 별개다 — 그건 *목록*을 제한할 뿐 *오브젝트*를 제한하지 않는다.
>
> 인증된 사용자에게만 보여야 하는 미디어라면 공개 prefix를 쓰면 안 된다. 아래 presigned GET으로 간다.

한 방식으로 통일할 수 없다. 공개 피드에 올리는 사진과 방 안에서만 보여야 할 사진은 요구가 정반대다.

### 공개 미디어 — 버킷 정책으로 prefix만 연다

[`deploy/bucket-policy-media.json`](./bucket-policy-media.json)을 S3 → 버킷 → 권한 → 버킷 정책에 붙여넣는다. `s3:GetObject`만, **`challenge-verifications/*` 와 `avatar/*` 두 prefix만** 허용한다. 이 정책은 **익명 접근자**에게 적용되며 `ListBucket`을 주지 않으므로 **목록으로 훑는 것은 불가능**하다.

| prefix | 무엇 | 왜 여는가 |
| --- | --- | --- |
| `challenge-verifications/*` | 사용자가 올린 인증 사진 | 피드에 그대로 뜬다. key 가 UUID 라 추측되지 않는다 |
| `avatar/*` | 운영이 올린 마스터 자산(캐릭터·알·둥지·의상) | 모두에게 같은 그림이라 숨길 것이 없다. **presign 을 쓸 이유가 없다** |

> **`avatar/` 는 최상위 하나로 모았다.** 캐릭터·알·둥지·의상이 전부 이 아래에 있어 **정책 문장이 한 줄로 끝난다.** 자산 종류가 늘어도 정책을 다시 고치지 않는다.

> **주체를 구분할 것.** 위 문장은 버킷 정책(익명 접근자) 이야기다. 앱이 쓰는 IAM 역할은 별개이며, 미참조 이미지 정리를 위해 `challenge-verifications/` 아래에 한해 `ListBucket`을 갖는다(아래 [미참조 미디어 정리] 절). 즉 **URL을 아는 외부인은 여전히 열거할 수 없고, 열거할 수 있는 것은 서버뿐이다.**
>
> **퍼블릭 액세스 차단을 먼저 푼다.** S3 → 버킷 → 권한 → "퍼블릭 액세스 차단"에서 **`BlockPublicPolicy`·`RestrictPublicBuckets` 두 개를 끈다.** 켜져 있으면 정책을 붙여도 무시된다. 나머지 두 개(ACL 관련)는 켜둔 채로 둔다 — ACL은 쓰지 않는다.

앱 설정은 바꿀 게 없다. `AWS_S3_PUBLIC_BASE_URL` 미설정 시 `application.yaml`이 S3 path-style 주소를 계산해 쓰고, 그 주소가 그대로 열린다.

**남는 위험은 URL이 사람 손을 타고 새는 경로다.** 한 번 새면 영구히 유효하고, 탈퇴해도 그렇다(챌린지 인증은 탈퇴 시 피드에서 사라지지만 S3 오브젝트는 남는다). 실사용자를 받기 전에 탈퇴 시 사진 삭제를 넣어야 한다.

### 비공개 미디어 — 권한을 확인하고 서명해 내려준다

**구현돼 있다.** 그룹 루틴 인증 사진이 이 방식으로 나간다.

```text
① 앱 → 서버   그 그룹 루틴의 인증 목록을 달라
② 서버        권한 확인(그 방의 활성 멤버인가) → 통과하면
              presignGetObject 로 서명해 응답의 imageUrl 에 담는다
③ 앱 → S3     서명된 URL로 GET
```

```text
GET /api/groups/{gid}/routines/{rid}/verifications         그 방의 활성 멤버만
```

방 멤버 확인은 그룹 도메인의 기존 `GroupValidationService` 를 그대로 쓴다. 접근 규칙이 두 벌이 되면 한쪽만 고쳐진다.

> **개인 루틴 인증과 그룹 채팅에는 아직 조회 경로가 없다.** 개인 인증은 사진을 보여 주는 화면이 없다는 기획 판단으로 조회 API 를 두지 않았고(사진은 저장만 된다), 채팅 이미지는 기능 자체가 없다. 둘 다 붙일 때 위 방식을 그대로 쓴다.

**핵심은 ②의 권한 확인이 어차피 필요하다는 점이다.** 방 멤버인지 보지 않고는 목록조차 줄 수 없으므로 서버가 이미 개입한다. 거기에 서명을 얹는 것은 추가 왕복이 아니다 — 서명은 네트워크 호출 없이 로컬에서 계산된다.

공개 피드에 이 방식이 안 맞았던 이유가 여기서는 대부분 사라진다.

| 공개 피드에서 문제였던 것 | 비공개 미디어에서는 |
| --- | --- |
| URL 수명이 몇 시간(임시 자격증명에 묶임) | 한 번 보고 마는 성격이라 무방 |
| URL이 매번 달라 캐싱이 죽음 | 조회 빈도가 낮아 영향이 작음 |
| 한 페이지에 서명 N번 | 서명은 로컬 계산이라 저렴 |

만료는 **짧게** 잡는다. `AWS_S3_VIEW_URL_EXPIRATION`(기본 `PT15M`)이고 **업로드용(`PT5M`)과 별개 설정이다** — 업로드 URL은 발급 직후 PUT 한 번에 쓰이고 끝나지만, 조회 URL은 목록 응답에 실려 화면에 머무는 동안 계속 쓰인다. 업로드와 같은 5분을 주면 잠깐 다른 앱을 보고 돌아왔을 때 사진이 전부 깨진다.

반대로 길게 잡아도 실익이 없다. instance profile의 임시 자격증명이 갱신되면 서명이 어차피 죽고, 유출 시 노출 시간만 늘어난다.

> **클라이언트는 이 URL을 저장해 두면 안 된다.** 만료되면 403이므로, 필요할 때 목록 API를 다시 불러 새 주소를 받는다. Swagger 설명에도 같은 문구를 적어 두었다.

> **비공개 prefix는 버킷 정책에 절대 넣지 않는다.** 공개 목록은 위 정책 파일 하나뿐이며, 새 용도를 추가할 때 그 용도가 공개인지 먼저 정하고 정책을 고친다.
>
> 같은 이유로 **`profiles/`도 지금은 넣지 않았다.** 프로필 이미지는 아직 없고(`Member`에 이미지 컬럼이 없다), 미리 열어 두면 "언제 왜 열었는지 모르는 공개 prefix"가 남는다. 프로필을 구현할 때 공개 여부를 확정하고 그때 정책에 추가한다.

### CloudFront는 열어둔 선택지다

버킷을 전부 비공개로 두고 **signed cookie**로 공개·비공개를 한 구조로 처리하는 방법이 있다. 쿠키 한 장이 여러 오브젝트를 커버하면서 캐싱도 유지된다.

다만 **조직 SCP의 서비스 화이트리스트에 CloudFront가 없어**(아래 [SCP] 표) 지금은 시도할 수 없다. 화이트리스트에 추가되면 그때 옮기면 된다.

공개 prefix 방식은 `AWS_S3_PUBLIC_BASE_URL` 축 위에 있어 **오리진 교체가 환경변수 한 줄**이다. 지금 선택이 나중을 막지 않는다.

## 인증 사진 AI 심사

사진이 그 챌린지의 의도에 맞는지 Claude 에게 물어 통과 여부를 정한다. 맞지 않으면 **422 로 반려하고 저장하지 않는다** — 스트릭도 오르지 않는다.

### 필수 환경변수 — 없으면 앱이 부팅하지 못한다

| 키 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `ANTHROPIC_API_KEY` | ✅ | 없음 | **기본값을 두지 않았다.** 키 없이 배포되는 것을 부팅 실패로 막는다 |
| `AI_REVIEW_ENABLED` | | `true` | 끄면 자가인증으로 되돌아간다 |
| `AI_REVIEW_MODEL` | | `claude-haiku-4-5-20251001` | |
| `AI_REVIEW_TIMEOUT` | | `PT20S` | 인증 요청 하나가 이만큼 늦어질 수 있다 |
| `AI_REVIEW_MAX_IMAGE_DIMENSION` | | `1568` | AI 에 보내기 전 줄일 긴 변 픽셀 |

배포 워크플로가 `ANTHROPIC_API_KEY` 존재를 **시작 전에** 확인한다. 값 자체는 비밀이라 로그에 찍지 않고 있는지만 본다.

> ⚠️ **`.env`(=`ENV_FILE`)에 넣는 것만으로는 앱에 도달하지 않는다.** compose 의 `app` 서비스는 `environment:` 에 **나열한 것만** 컨테이너로 넘긴다. 새 환경변수를 추가할 때 `docker-compose.prod.yml` 의 그 목록도 함께 봐야 한다.
>
> 실제로 이 실수를 했다. `ENV_FILE` 에는 키가 있는데 compose 에 적지 않아 컨테이너에 들어가지 않았고, **해석되지 못한 플레이스홀더가 문자열 그대로 바인딩되어 앱이 정상 부팅했다.** 호출은 401 로 실패하고 fail-open 이라 전부 통과 — **심사가 꺼진 것과 같은 상태로 조용히 돌았다.**
>
> 지금은 키 형식을 검증해 그 경우 부팅이 실패한다. 같은 실수가 배포 시점에 바로 드러난다.

### 심사가 실제로 돌고 있는지 확인하는 법

**인증을 한 번 하고** 아래를 본다. 심사는 통과·반려·장애 세 경우 모두 로그를 남기므로, 인증했는데 아무것도 안 찍히면 그 자체가 이상 신호다.

```bash
docker compose logs app | grep "AI 심사"
```

| 찍히는 줄 | 뜻 |
| --- | --- |
| `AI 심사를 통과했습니다` | 정상 동작 |
| `AI 심사에서 반려했습니다` | 정상 동작(판정이 반려) |
| `AI 심사 없이 인증을 통과시켰습니다` | ⚠️ **심사기가 답을 못 줬다.** 키·네트워크·크레딧을 확인한다 |
| `AI 심사가 꺼져 있어 건너뜁니다` | `AI_REVIEW_ENABLED=false` 상태(DEBUG 레벨) |

키가 컨테이너에 들어갔는지는 서비스 이름으로 확인한다. 컨테이너 이름(`app-app-1`)은 compose 프로젝트 이름과 인덱스에 따라 달라진다.

```bash
docker compose exec -T app printenv ANTHROPIC_API_KEY >/dev/null && echo "키 주입됨" || echo "키 없음"
```


### 두 가지를 심사한다

| 기준 | 반려 코드 | 판단 방향 |
| --- | --- | --- |
| 챌린지 내용과 맞는가 | `CHALLENGE422_1` | **애매하면 통과.** 정상 인증을 반려하는 손해가 더 크다 |
| 공개 피드에 올려도 되는가<br>(선정적·폭력적·타인 개인정보) | `CHALLENGE422_2` | **애매하면 반려.** 공개 prefix라 되돌릴 수 없다 |

방향이 반대라 한 기준으로 뭉뚱그리지 않는다. 뭉뚱그리면 어느 한쪽 방침을 잘못 적용하게 된다.

> ⚠️ **심사는 "공개 전에 거르는" 장치가 아니다.**
>
> 사진은 심사 전에 이미 공개 prefix 에 올라가 있고, presigned URL 발급 응답이 그 공개 URL 을 함께 준다. 업로더는 심사 전에 그 URL 을 열거나 공유할 수 있고, 반려 후 지워도 이미 받아 간 사본은 회수되지 않는다.
>
> 다만 key 가 UUID 라 추측할 수 없고 외부에서 열거도 불가능하므로, 그 URL 을 아는 사람은 **업로더 본인과 그가 공유한 상대뿐**이다. 낮지만 0 은 아니다.
>
> 제대로 막으려면 심사 전 사진을 비공개 staging 에 두고 통과한 뒤 승격해야 한다. 클라이언트 변경이 딸려 있어 별도로 다룬다.

**반려된 사진은 S3에서도 즉시 지운다.** 저장하지 않으므로 DB가 그 key를 참조하지 않아 미참조 정리가 며칠 뒤 가져가긴 하지만, 그동안 공개 prefix라 key를 아는 사람은 계속 볼 수 있다. 유해로 반려된 것일 수 있어 그 자리에서 치운다. 삭제가 실패해도 반려 응답은 그대로고, 못 지운 것은 미참조 정리가 결국 가져간다.

### 모델이 응답을 거부하면 반려로 다룬다

노골적으로 유해한 이미지에는 모델이 도구 호출 대신 거부 응답을 낸다. 이것을 "심사 못 함"으로 흘리면 fail-open 때문에 **통과**가 되는데, 그러면 가장 걸러야 할 사진이 가장 확실하게 통과한다.

거부는 장애가 아니라 판단이므로 `stop_reason` 을 보고 **`CHALLENGE422_2` 로 반려**한다. fail-open 원칙의 유일한 예외다.

### 장애가 나면 통과시킨다 (fail-open)

심사기가 답을 못 주는 경우 — API 장애 · 타임아웃 · 크레딧 소진 · 응답 형식 이상 · 사진을 못 읽음 — 는 **전부 통과**다. 이때는 사진도 지우지 않는다(반려가 아니므로).

막는 쪽으로 두면 Anthropic 이 죽는 순간 아무도 인증을 못 한다. 그때 어차피 킬 스위치를 켜서 통과시키게 되므로, 처음부터 통과시키고 로그로 드러내는 편이 정직하다. 부적절한 사진의 방어선이 이것 하나가 아니라는 점도 근거다 — 신고가 임계값만큼 쌓이면 전체 회원에게 가려진다.

**심사 없이 통과한 건은 로그에 남는다.** 이 로그가 몰려 찍히면 심사가 사실상 꺼진 상태로 도는 것이다.

```bash
docker compose logs app | grep "AI 심사 없이"
```

### 오작동하면 끈다

모델이 정상 인증을 무더기로 반려하면 코드를 되돌리는 것보다 이 값을 끄고 재배포하는 편이 빠르다.

```
AI_REVIEW_ENABLED=false
```

끄면 자가인증으로 돌아간다 — 사진만 올리면 통과다.

### 이미지는 줄여서 보낸다

원본은 S3 에 그대로 두고, **AI 에 보낼 때만** 긴 변을 `1568px` 로 줄인다. 심사 쪽이 그보다 큰 이미지를 어차피 자기가 축소해서 보므로 원본을 그대로 보내면 판정은 같고 전송만 느려진다. 이미지 한 장에 크기 상한도 있어 큰 사진은 요청 자체가 거부된다.

WEBP 는 표준 ImageIO 로 디코딩되지 않아 줄이지 못하고 **원본 그대로 보낸다.** 심사 쪽이 그 형식을 받아주므로 크기만 감당되면 동작한다.

## 미참조 미디어 정리 (#19)

업로드는 됐지만 **DB 어디서도 참조하지 않는 오브젝트**를 매일 새벽 4시(KST)에 지운다. presigned URL로 S3에 올린 뒤 저장 API를 호출하지 않으면 그 파일은 아무도 모르는 채 영원히 남는다 — 앱이 죽거나 사용자가 이탈하면 생기고, 당일 재인증으로 `image_url`을 덮어쓸 때도 이전 key가 남는다. 운영 이모티콘도 S3 업로드 뒤 DB 저장이 실패하고 즉시 삭제까지 실패하면 같은 고아 상태가 된다. 비용보다 **개인정보와 운영 자산 관리** 문제다.

### 라이프사이클 규칙이 아니라 DB 대조인 이유

S3 라이프사이클(“N일 지난 객체 자동 삭제”)이 가장 단순해 보이지만 **그대로 하면 살아 있는 사진이 지워진다.** 라이프사이클은 객체의 **나이만** 알고 “DB가 참조하는지”는 모르는데, 미참조 파일과 정상 인증 사진이 같은 날짜 prefix에 섞여 있다.

그래서 **S3 목록과 DB를 대조**한다. key에 날짜 구간이 있어서(#39) 버킷 전체가 아니라 날짜 하나씩만 훑으면 된다.

### 필요한 IAM 권한 — 이게 없으면 동작하지 않는다

`deploy/iam-policy.json`에 두 개를 추가했다. **콘솔에서 역할 정책을 갱신해야 실제로 열린다.**

| Sid | 액션 | 리소스 | 왜 |
| --- | --- | --- | --- |
| `MediaCleanupList` | `s3:ListBucket` | `arn:aws:s3:::lirouti-prod-bucket` (버킷 자체)<br>+ `Condition: s3:prefix = challenge-verifications/* 또는 chat-emoticons/*` | 두 용도의 날짜 prefix 아래 오브젝트 목록을 얻는다 |
| `MediaCleanupDelete` | `s3:DeleteObject` | `arn:aws:s3:::lirouti-prod-bucket/challenge-verifications/*`<br>`arn:aws:s3:::lirouti-prod-bucket/challenge-verifications-staging/*`<br>`arn:aws:s3:::lirouti-prod-bucket/chat-emoticons/*` | 미참조 오브젝트와 등록 실패 이모티콘을 지운다. **대기본은 승격 직후 지운다** |

> `ListBucket`의 리소스는 **버킷 ARN이지 `/*`가 아니다.** 오브젝트 액션과 리소스 형태가 다르다 — `/*`를 붙이면 조용히 권한이 안 먹는다.

> **이 파일과 콘솔은 일치한다**(2026-08-13 확인). 그전까지 두 방향으로 어긋나 있었고, 그 이력을 남겨 둔다.
>
> | | 어긋나 있던 상태 | 지금 |
> | --- | --- | --- |
> | `challenge-verifications-staging/*` 삭제 | 콘솔에만 있었다 | 파일에 더했다 |
> | `chat-emoticons/*` 열거·삭제 | 파일에만 있었다 | 콘솔에 적용했다 |
>
> **`-staging` 삭제가 파일에서 빠져 있던 것이 위험했다.** 심사를 통과한 사진을 공개 prefix 로 복사한 뒤 대기본을 지우는, **지금 실제로 쓰이는 권한**이다. 파일을 그대로 붙여넣었으면 승격 때마다 대기본이 쌓이기 시작했을 것이다.
>
> **`chat-emoticons/*` 삭제는 정리 배치 전용이 아니다.** 이모티콘 등록 중 DB 저장이 실패하면 방금 올린 객체를 그 자리에서 지우는데(`ChatEmoticonAdminService`), 그 경로가 권한이 없어 조용히 실패하고 있었다. 열거 쪽만 배치 전용이라 배치를 켤 때까지 놀지만, 삭제와 갈라 두면 다음에 또 어긋난다.

**열거는 두 prefix 조건 안에서만 동작한다.** 버킷 루트를 훑으면 `AccessDenied` 가 나는 것이 정상이며, 이것으로 정책이 좁게 걸렸는지 확인할 수 있다.

```bash
aws s3 ls s3://lirouti-prod-bucket/chat-emoticons/   # 된다
aws s3 ls s3://lirouti-prod-bucket/                  # AccessDenied ← 정상
```

**둘 다 `challenge-verifications/`와 `chat-emoticons/` 아래로만 좁혀 뒀다.** 앞으로 추가될 다른 비공개 미디어(개인 루틴·그룹 채팅 사진)는 **앱 역할로도 열거·삭제할 수 없다.**

채팅 이모티콘에는 삭제 안전망이 두 겹이다.

1. S3 업로드 뒤 URL 생성이나 DB 저장이 실패하면, 같은 요청 안에서 방금 만든 정확한 key를 즉시 지운다. 이 경로에는 `DeleteObject`만 필요하다.
2. 즉시 삭제도 실패하면 원래 등록 오류를 유지하고 key를 로그에 남긴다. 이후 정리 배치가 `ListBucket`으로 날짜 prefix를 훑고 DB와 대조해 고아 key만 다시 지운다.

`ChatMediaReferenceSource`는 활성 여부와 관계없이 `chat_emoticon` 행이 참조하는 key를 모두 반환한다. 비활성 이모티콘도 과거 메시지에서 보여야 하므로 **비활성이라는 이유로 정리하지 않는다.** `chat-emoticons/`는 비공개 prefix이며 공개 버킷 정책에는 추가하지 않는다.

정리 배치는 앱 안의 스케줄러라 앱과 같은 인스턴스 프로필을 쓴다. 즉 **앱이 침해되면 이 권한도 함께 넘어간다.** 배치를 별도 역할로 떼어내는 것이 이론상 더 안전하지만, 그러려면 배치를 앱 밖(Lambda·별도 컨테이너)으로 옮겨야 하고 지금은 그 실행 기반이 없다. 게다가 `DeleteObject`는 **탈퇴 시 사진 삭제가 앱 안에서 지워야 해서** 어차피 앱 역할에 남는다. 그래서 분리 대신 **리소스를 좁히는 쪽**으로 위험을 줄였다.

`s3:DeleteObject`가 열리면 **탈퇴 시 사진 삭제(#70)도 같은 권한을 쓴다.** 한 번에 넓히는 게 낫다.

> ⚠️ **심사 대기 prefix가 도입되면 `DeleteObject`를 한 번 더 넓혀야 한다(#105).**
>
> 심사 전 사진을 `challenge-verifications-staging/`으로 받고 통과한 뒤 승격하는 규칙이다(위 [미디어 서빙] 절의 경고와 짝). **`challenge-verifications/*`는 `challenge-verifications-staging/…`을 포함하지 않는다** — 접두사 뒤의 `/` 때문이다. 그래서 반려·승격 후 대기본을 지우려면 리소스를 배열로 늘려야 한다.
>
> ```jsonc
> // MediaCleanupDelete
> "Resource": [
>   "arn:aws:s3:::lirouti-prod-bucket/challenge-verifications/*",
>   "arn:aws:s3:::lirouti-prod-bucket/challenge-verifications-staging/*"
> ]
> ```
>
> `PutObject`·`GetObject`는 이미 버킷 전체라 손댈 것이 없고, `ListBucket`도 넓히지 않는다 — 대기본은 나이 기반 수명 주기로 지우므로 앱이 목록을 훑을 일이 없다.
>
> **정책이 코드보다 먼저다.** 정책은 콘솔에서 사람이 넓히는 것이라 배포와 함께 나가지 않는다. 순서가 뒤바뀌면 승격은 되는데 **대기본만 계속 쌓인다** — 수명 주기가 결국 지우므로 장애는 아니지만, 그동안 지워야 할 것이 안 지워진다. 규칙 전문은 `database-schema.md`의 [심사가 붙는 용도는 대기 prefix로 먼저 받는다] 절에 있다.

### 켜는 순서 — 기본값은 “아무것도 안 함”이다

되돌릴 수 없는 작업이라 두 단계로 잠가 두었다.

```bash
# 1단계. IAM 권한을 넓힌 뒤, 흉내내기로 켠다 (dry-run 기본값이 true라 값은 enabled만 주면 된다)
MEDIA_CLEANUP_ENABLED=true

# 2단계. 며칠 로그를 보고 삭제 예정 건수가 납득되면 실제 삭제로 전환
MEDIA_CLEANUP_DRY_RUN=false
```

실행할 때마다 **지울 게 없어도** 요약 한 줄이 남는다. 이 줄이 안 보이면 배치가 돌지 않은 것이다.

```
미참조 미디어 정리 완료. 대상 기간=2026-07-16 ~ 2026-07-23, 훑은 오브젝트=0건, 삭제 예정=0건
```

지울 것이 있으면 건별 로그가 함께 찍힌다.

```
[흉내내기] 미참조 미디어 3건을 지웠을 것입니다. prefix=challenge-verifications/2026/07/23/, 예시=...
미참조 미디어 정리 완료. 대상 기간=2026-07-16 ~ 2026-07-23, 훑은 오브젝트=12건, 삭제 예정=3건
```

목록 조회가 막히면 요약이 `일부 실패`로 바뀌고 훑지 못한 구간 수가 붙는다. **"훑었는데 없었다"와 "권한이 없어 못 훑었다"가 둘 다 0건으로 보이면 안 되므로 구분해 둔 것이다.**

**건수가 예상보다 크면 켜지 말 것.** 참조처(`MediaReferenceSource`)가 빠졌다는 신호다. 그대로 `dry-run: false`로 넘기면 살아 있는 사진이 지워진다.

### 조정할 수 있는 값

| 환경변수 | 기본 | 뜻 |
| --- | --- | --- |
| `MEDIA_CLEANUP_ENABLED` | `false` | 배치 자체를 켤지 |
| `MEDIA_CLEANUP_DRY_RUN` | `true` | 실제로 지울지, 로그만 남길지 |
| `MEDIA_CLEANUP_GRACE_DAYS` | `7` | 업로드 후 며칠 지나야 대상이 되는지 |
| `MEDIA_CLEANUP_CATCH_UP_DAYS` | `7` | 배치를 거른 날을 며칠까지 소급해 훑을지 |
| `MEDIA_CLEANUP_MAX_DELETIONS_PER_RUN` | `1000` | 1회 삭제 상한(폭주 방지) |

### 지우지 않는 것들

- **참조처가 없는 용도** — `MediaReferenceSource` 구현체가 담당하지 않는 prefix는 목록조차 훑지 않는다. 지금 `profiles/`가 그렇다. 나중에 프로필 업로드를 붙이는 사람이 참조처를 등록하지 않으면 **아무것도 안 지운다**(안전한 방향으로 실패한다).
- **우리가 발급하지 않은 형식** — 콘솔에서 사람이 올린 파일처럼 `{UUID}.{확장자}`가 아닌 오브젝트는 남긴다.
- **탈퇴·신고로 숨겨진 인증의 사진** — 행이 남아 있으면 파일도 살아 있는 것으로 본다. 탈퇴 회원 사진 삭제는 별개 정책이다(#70).
- **날짜 없는 옛 key** — #39 이전에 발급된 `challenge-verifications/{UUID}.jpg`는 날짜 prefix 목록에 안 잡힌다. 수가 적고 전부 정상 저장된 것들이라 둔다.

## AWS 조직·접근 구조

배포 대상이 어떤 환경에 있는지, 콘솔 작업 시 무엇이 막히고 왜 막히는지 알아두면 삽질이 줄어든다.

```text
Root (조직 최상위 컨테이너 — SCP를 붙이는 지점)
├── management 계정  (조직 관리 전용, 워크로드 없음. OU로 옮길 수 없다)
├── team502 OU   (별개 프로젝트, LiRouti와 무관)
├── dvely OU     (별개 프로젝트, LiRouti와 무관)
└── umc OU
    └── umc 계정  ← LiRouti 배포 대상 (member account, EC2: ap-northeast-2)
```

**Root는 계정이 아니라 컨테이너다.** management 계정은 그 아래 별도 노드로 존재하며, OU 안으로 옮길 수 없다.

SCP는 Root와 OU에 붙고 **member account가 상속받는다.** 즉 `umc` 계정은 Root의 리전 잠금과 umc OU의 서비스 화이트리스트를 **둘 다** 받는다(아래 표). **management 계정만 SCP 적용에서 면제된다** — 그래서 거기에 워크로드를 두지 않는다.

LiRouti는 **umc 계정 하나에만** 있고 다른 계정과 리소스를 공유하지 않는다. 운영 종료 시 리소스만 지우고 계정·OU는 보존한다.

### 로그인 — IAM 사용자를 만들지 않는다

사람의 접근은 전부 **IAM Identity Center(SSO)** 를 통한다. 콘솔 로그인 시 실제 주체는 `assumed-role/AWSReservedSSO_...` 형태의 **임시 자격증명**이고 장기 액세스 키가 존재하지 않는다.

- permission set: 배포 담당 `AdministratorAccess`, 열람 인원 `ReadOnlyAccess`
  > ⚠️ **`AdministratorAccess`는 최소 권한이 아니다.** 현재 조직에 설정된 값을 그대로 적은 것이고, 데모 기간(1개월) 한시 운영 전제다.
  > 실 운영으로 전환하는 시점에는 **역할 생성·부착과 버킷 설정만 담은 전용 permission set**을 만들어 배포 담당을 그쪽으로 옮긴다. `ReadOnlyAccess`는 지금도 열람 전용이라 그대로 둔다.
  > SSH `0.0.0.0/0` 개방과 같은 성격의 한시 조치이므로, 철거·전환 시 함께 정리한다.
- **IAM 역할 생성 같은 작업이 막히면 SCP보다 permission set을 먼저 의심한다.** ReadOnly로 로그인한 경우가 대부분이다.
- 서버(EC2)의 AWS 권한은 사람 계정과 무관하게 **instance profile**로 부여한다(아래 절).

### SCP — IAM 권한 위에 걸린 천장

계정 안에서 어떤 권한을 받아도 이 천장은 넘지 못한다. 실무에서 부딪히는 건 둘이다.

| 제약 | 내용 | 실무에서 뜻하는 것 |
| --- | --- | --- |
| **리전 잠금** (Root) | `ap-northeast-2`·`us-east-1` 외 Deny | **버킷·리소스를 서울에 만들어야 한다.** 다른 리전은 `UnauthorizedOperation` |
| **서비스 화이트리스트** (umc OU) | EC2·S3·IAM/STS·CloudWatch/Logs·SSM·KMS·Budgets/CE만 허용 | 목록 밖 서비스(RDS·Lambda 등)는 **켤 수 없다.** 보안 경계이자 비용 안전장치 |

`iam:*`·`sts:*`는 리전 잠금의 예외로 빠져 있고 화이트리스트에도 있어, IAM 작업은 이중으로 안전하다.

> 콘솔이 자동 호출하는 부가 서비스(`compute-optimizer` 등)에서 간헐적으로 뜨는 `AccessDenied`는 **정상이며 무해하다.** 실제 작업이 막힐 때만 화이트리스트에 추가한다.

## IAM instance profile 부착 (#58)

서버에 access key를 두지 않기 위해 EC2에 IAM 역할을 붙여 S3 권한을 준다. **역할이 없으면 presigned URL 발급부터 실패하고**(서명에 자격증명이 필요하다) 백업 스크립트도 동작하지 않는다.

> ✅ **2026-07-27 부착 완료.** 역할 `lirouti-ec2-role`, 인스턴스 프로필 `arn:aws:iam::058114477749:instance-profile/lirouti-ec2-role`. 아래 §6 검증을 전부 통과했다(결과는 그 절 끝에 기록).
> 아래 절차는 **인스턴스를 새로 띄우거나 다른 환경에 재구축할 때** 그대로 다시 쓰는 용도로 남긴다.

접근은 IAM 사용자가 아니라 **IAM Identity Center(SSO)**로 한다. 콘솔 작업은 **AdministratorAccess permission set**으로 로그인해야 한다 — ReadOnly로는 역할을 만들 수 없고, 이때 실패 원인은 SCP가 아니라 permission set이다.

### 1) 버킷 확인·생성

`lirouti-prod-bucket`(미디어)·`lirouti-db-backup`(백업)이 있는지 본다. 없으면 **리전 `ap-northeast-2`, 퍼블릭 액세스 차단 유지, 기본 암호화(SSE-S3)** 로 만든다.

- 리전을 다른 곳으로 잡으면 조직 SCP의 리전 잠금(`ap-northeast-2`·`us-east-1`만 허용)에 걸린다.
- **SSE-KMS를 고르면** 아래 정책에 `kms:GenerateDataKey`·`kms:Decrypt`가 추가로 필요하다. 특별한 이유가 없으면 기본값을 쓴다.
  - 여기서 **customer-managed 키(CMK)를 쓰면 IAM 정책만으로는 부족하다.** KMS는 키 정책(key policy)이 IAM 위임을 허용해야 IAM 쪽 권한이 효력을 갖는다. 허용 문구가 없으면 권한을 붙여도 S3 PUT에서 `AccessDenied`가 난다. CMK를 쓸 경우 키 정책에 `lirouti-ec2-role`의 ARN을 직접 넣거나, 계정 위임(`arn:aws:iam::<계정>:root` 허용) 문구가 있는지 확인한다.
  - AWS 관리형 키(`aws/s3`)는 키 정책이 이미 계정 위임을 허용하므로 이 문제가 없다.
- **미디어 버킷은 `challenge-verifications/`·`avatar/` 두 prefix만 공개 읽기로 연다**(#66, #231). 나머지 prefix와 백업 버킷은 비공개 그대로다. 근거와 절차는 아래 [미디어 서빙] 절을 볼 것.
- **라이프사이클 규칙은 걸지 않는다.** 라이프사이클은 객체의 나이만 알고 DB가 참조하는지는 모르는데, 미참조 파일과 정상 인증 사진이 같은 날짜 prefix에 섞여 있다. 자동 삭제를 걸면 정상 인증 사진이 지워진다. 고아 파일은 **S3 목록과 DB를 대조**해 지운다 — 아래 [미참조 미디어 정리 (#19)] 절.

### 2) 정책 생성

IAM → 정책 → 정책 생성 → JSON 탭에 [`deploy/iam-policy.json`](./iam-policy.json)의 내용을 붙여넣는다. 이름은 `lirouti-app-s3`.

미디어 버킷에는 `PutObject`·`GetObject`(업로드·검증·서빙, 버킷 전체)에 더해 **`ListBucket`·`DeleteObject`(미참조 이미지 정리와 이모티콘 등록 실패 보상 삭제)** 를 허용하되, 뒤의 둘은 **`challenge-verifications/`와 `chat-emoticons/` 아래로만** 좁힌다. 백업 버킷은 `PutObject`만 준다 — 백업은 쓰기만 하면 되고, 읽기·삭제까지 주면 앱 침해 시 백업을 지울 수 있다.

> **`ListBucket`만 리소스가 버킷 ARN(`arn:aws:s3:::lirouti-prod-bucket`)이고 나머지는 오브젝트(`/*`)다.** 버킷 수준 액션과 오브젝트 수준 액션은 리소스 형태가 다르다. `ListBucket`에 `/*`를 붙이면 **오류 없이 조용히 권한이 안 먹는다.** prefix 제한은 리소스가 아니라 `Condition`의 `s3:prefix`로 건다.
>
> 이 두 권한을 붙였다고 정리 배치가 바로 도는 것은 아니다. 앱 쪽 기본값이 꺼짐이라 `MEDIA_CLEANUP_ENABLED=true`까지 넣어야 시작한다. 순서와 근거는 아래 [미참조 미디어 정리] 절을 볼 것.
>
> ⚠️ **앱이 백업을 덮어쓸 수 있다 — 버킷 버전 관리로 막아야 한다.**
>
> `BackupObjectWrite`는 `lirouti-ec2-role`에 붙고, 앱 컨테이너는 IMDS로 그 역할을 그대로 쓴다. 즉 **앱이 침해되면 백업 파일을 덮어쓸 수 있다.** 2026-07-28 실측으로 확인했다 — 앱 역할로 백업 버킷의 기존 오브젝트를 `PutObject`로 덮어쓰는 데 성공했다.
>
> `backup.sh`가 키를 `<날짜>.sql.gz`로 만들기 때문에 더 나쁘다. `ListBucket`이 거부돼도 **날짜만 찍으면 키를 맞힐 수 있어서**, 최근 며칠치를 순서대로 덮어쓰는 데 목록 권한이 필요 없다. 게다가 이 역할에는 백업 버킷 `GetObject`가 없어 **덮어써졌는지 앱 쪽에서 확인할 방법도 없다.**
>
> **역할을 나누는 것으로는 거의 해결되지 않는다.** 앱과 백업 cron이 같은 EC2에 살아서, 그 호스트에서 코드 실행이 되는 공격자는 어느 역할이든 쓸 수 있다. 컴퓨팅이 분리돼야 의미가 생긴다.
>
> **실효가 있는 건 버킷 버전 관리다.** 덮어쓰기가 새 버전을 만들 뿐 이전 버전이 남으므로 복구가 된다. 콘솔에서 S3 → `lirouti-db-backup` → 속성 → 버전 관리 → 활성화. 비용은 이전 버전 만료 라이프사이클(예: 30일)로 잡는다.
>
> **2026-08-03 KST 활성화 확인.** 백업 cron 등록과 같은 날 켰다.
>
> 설정 조회(`get-bucket-versioning`)는 인스턴스 역할에 `s3:GetBucketVersioning`이 없어 `AccessDenied`가 나지만, **켜졌는지 여부는 서버에서도 확인할 수 있다.** 버전 관리가 켜진 버킷은 `PutObject` 응답에 `VersionId`를 실어 보내기 때문이다. 꺼져 있으면 그 필드가 오지 않는다.
>
> ```bash
> /opt/app/backup.sh --check-versioning
> #   버전 관리 켜짐 (2026-08-03.sql.gz VersionId=ms4nc4...)     ← 켜져 있음
> #   버전 관리 꺼짐 — 덮어쓰기가 복구 불가다. 콘솔에서 켤 것       ← 꺼져 있음 (exit 1)
> ```
>
> **이 확인은 손으로 `s3api put-object`를 치지 않는다.** 무엇을 올리느냐에 함정이 두 개 있어서, 스크립트가 대신 정하게 했다.
>
> - **전용 확인용 키를 쓰면 지우지 못하고 남는다.** 이 역할에는 백업 버킷 `DeleteObject`가 없어 콘솔에서만 치울 수 있다. 실제로 예전 확인용 오브젝트가 몇 주째 남아 있었다.
> - **그날 백업 키에 아무 파일이나 올리면 그 키의 최신 버전이 그 파일이 된다.** 이전 버전은 남지만, 복구할 때 집는 것은 최신 버전이다. 확인해보려다 그날 백업을 못 쓰게 만드는 셈이다.
>
> 그래서 `--check-versioning`은 **백업과 똑같이 새 덤프를 떠서 그날 키에 올린다.** 새 오브젝트가 생기지 않고, 올라간 최신 버전도 정상 백업이다.
>
> 참고로 `aws s3 cp`(백업 경로가 쓰는 명령)는 이 응답을 출력하지 않는다. 그래서 확인 경로만 `s3api put-object`를 쓴다.
>
> 콘솔 경로는 이렇다. 버전 관리는 **켠 시점 이후에 올라온 오브젝트부터** 버전을 남기므로(그 전 것들은 버전 ID가 `null`인 한 벌로 남는다), 빨리 켤수록 보호 범위가 넓어진다.
>
> ```text
> S3 → 버킷 lirouti-db-backup → [속성] 탭 → 버킷 버전 관리 → [편집] → 활성화 → 변경 사항 저장
> S3 → 버킷 lirouti-db-backup → [관리] 탭 → 수명 주기 규칙 생성
>   → 버킷의 모든 객체에 적용 → "객체의 이전 버전 영구 삭제" → 30일
> ```
>
> 수명 주기 규칙을 같이 거는 이유는, 버전 관리를 켜면 덮어쓰기·삭제가 오브젝트를 지우지 않고 **이전 버전으로 계속 쌓기** 때문이다. 지금은 덤프가 gzip 12KB 남짓이라 비용이 사실상 없지만, 데이터가 늘어도 알아서 정리되도록 규칙을 먼저 걸어두는 편이 낫다.

### 3) 역할 생성

IAM → 역할 → 역할 생성

| 항목 | 값 |
| --- | --- |
| 신뢰할 수 있는 엔터티 | AWS 서비스 |
| 사용 사례 | **EC2** |
| 권한 정책 | `lirouti-app-s3` |
| 역할 이름 | `lirouti-ec2-role` |

EC2 사용 사례를 고르면 신뢰 정책과 인스턴스 프로필이 함께 만들어진다.

### 4) 인스턴스에 부착

EC2 → 인스턴스 → 대상 인스턴스 → **작업 → 보안 → IAM 역할 수정** → `lirouti-ec2-role` → 업데이트.

재부팅은 필요 없다. 몇 초 안에 IMDS로 자격증명이 내려온다.

### 5) 앱 컨테이너 재시작

```bash
ssh <서버> 'cd /opt/app && sudo docker compose restart app'
```

AWS SDK는 실패한 자격증명 조회를 영구 캐시하지 않아 재시작 없이도 붙을 가능성이 높지만, 비용이 없으므로 확실히 하고 넘어간다.

### 6) 검증

**실제로 S3를 호출하는 주체는 호스트가 아니라 `app` 컨테이너다.** 호스트에서만 확인하면 두 가지를 놓친다 — 컨테이너가 IMDS에 닿는지(Docker 브리지가 홉을 하나 더 쓴다), 그리고 호스트에 남은 자격증명 때문에 거짓 통과하는지. 그래서 ①은 컨테이너 안에서 돌린다.

**세 단계 모두 실패하면 즉시 멈추도록 썼다.** 검증 절차가 조용히 통과하면 "역할이 붙었다"고 잘못 결론내리게 되므로, `set -euo pipefail`과 `curl -fsS`로 fail-closed를 강제한다. 특히 `curl -s`는 **HTTP 404에도 종료 코드 0**을 주기 때문에 `-f`가 없으면 미부착 상태가 성공으로 보인다.

```bash
# ① 컨테이너가 역할 자격증명을 받는가 — 부착 전에는 404가 나온다
#    app 이미지(eclipse-temurin:21-jre)에 curl이 들어 있어 그대로 쓸 수 있다.
cd /opt/app && sudo docker compose exec app bash -c '
  set -euo pipefail
  TOKEN=$(curl -fsS --max-time 5 -X PUT "http://169.254.169.254/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
  test -n "$TOKEN"
  curl -fsS --max-time 5 -H "X-aws-ec2-metadata-token: $TOKEN" \
    http://169.254.169.254/latest/meta-data/iam/info'
```

```bash
# ② 권한 범위가 의도대로인가 (aws-cli 필요: sudo snap install aws-cli --classic)
#    컨테이너에는 aws-cli가 없으므로 호스트에서 돌린다. 대신 주체부터 확인한다.
set -euo pipefail

ARN="$(aws sts get-caller-identity --query Arn --output text)"
[[ "$ARN" =~ :assumed-role/lirouti-ec2-role/ ]] || { echo "역할이 아니다: $ARN" >&2; exit 1; }

echo t > /tmp/t.txt
aws s3 cp /tmp/t.txt s3://lirouti-db-backup/t.txt          # 성공해야 정상 (PutObject)

# ListBucket은 거부돼야 정상이다. "실패했다"로 끝내지 말고 거부 사유까지 확인한다 —
# 네트워크 오류나 오타로 실패한 것도 똑같이 비정상 종료라 구분이 안 되기 때문이다.
if aws s3api list-objects-v2 --bucket lirouti-db-backup >/tmp/ls.out 2>&1; then
  echo "ListBucket이 허용돼 있다 — 정책이 넓다" >&2; exit 1
fi
grep -q "AccessDenied" /tmp/ls.out || { echo "거부됐지만 사유가 AccessDenied가 아니다" >&2; exit 1; }
echo "② OK"
```

올린 테스트 객체는 인스턴스 역할로 지울 수 없으므로 콘솔에서 지운다. 백업 버킷에는 `DeleteObject`가 없고, 미디어 버킷은 `challenge-verifications/` 아래에만 있어서 그 밖의 경로(`iam-check/` 등)는 역할로 못 지운다.

> ⚠️ **`aws sts get-caller-identity`를 건너뛰지 않는다.** AWS 자격증명 체인은 환경변수 → 공유 credential 파일 → instance profile 순으로 본다. 호스트에 `AWS_ACCESS_KEY_ID` 같은 변수나 `~/.aws/credentials`가 남아 있으면 **역할이 안 붙었는데도 ②가 성공해버린다.**
> (2026-07-27 실측: 이 서버는 `AWS_*` 환경변수 0개, `~/.aws` 없음 — 현재는 문제없다.)
>
> ⚠️ `.../iam/security-credentials/<역할명>`은 **실제 임시 액세스 키를 반환한다.** 조회하지 않는다. 끝에 슬래시만 붙인 목록 경로와 `iam/info`는 역할 이름·ARN만 나와 안전하다.

```bash
# ③ 앱의 실제 경로가 도는가 — 여기까지 봐야 완결이다
#    로그인이 필요한 API라 액세스 토큰이 하나 있어야 한다.
set -euo pipefail
TOKEN="<로그인해서 받은 accessToken>"
BASE="http://localhost:8080"

# 발급 — 역할이 없으면 여기서 PRESIGNED_URL_ISSUE_FAILED로 떨어진다
RES="$(curl -fsS -X POST "$BASE/api/media/presigned-url" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"purpose":"CHALLENGE_VERIFICATION","contentType":"image/jpeg","contentLength":3}')"
echo "$RES" | grep -q '"isSuccess":true' || { echo "발급 실패: $RES" >&2; exit 1; }

UPLOAD_URL="$(echo "$RES" | sed -n 's/.*"uploadUrl":"\([^"]*\)".*/\1/p')"

# 업로드 — 서명한 Content-Type·Content-Length와 정확히 일치해야 S3가 받는다
printf '\xFF\xD8\xFF' > /tmp/probe.jpg
curl -fsS -X PUT "$UPLOAD_URL" -H 'Content-Type: image/jpeg' --data-binary @/tmp/probe.jpg
echo "③ OK"
```

①이 컨테이너의 자격증명 수령을, ③이 그 자격증명으로 서명까지 되는지를 본다. **③이 최종 판정이다** — presigned URL 발급은 서명에 자격증명이 필요하므로, 역할이 없으면 여기서 먼저 실패한다.

> ③으로 올린 `probe.jpg`는 현재 `challenge-verifications/` 삭제 권한 범위에 있다. 테스트 직후 exact key를 확인해 지우거나, 찾지 못했다면 정리 배치가 grace 기간 뒤 DB 미참조 파일로 정리한다.

#### 채팅 이모티콘 권한 갱신 확인 (#161)

레포의 정책 파일을 고치는 것만으로 AWS 역할은 바뀌지 않는다. IAM 콘솔에서 `lirouti-app-s3` 정책 JSON을 갱신한 뒤 EC2 호스트에서 아래를 실행한다. 허용된 `chat-emoticons/`에서는 업로드·목록·삭제가 모두 성공하고, 범위 밖인 `profiles/` 목록은 `AccessDenied`여야 한다.

```bash
set -euo pipefail
export AWS_DEFAULT_REGION=ap-northeast-2

PROBE_FILE=/tmp/chat-emoticon-iam-probe.txt
PROBE_DATE="$(TZ=Asia/Seoul date +%Y/%m/%d)"
PROBE_UUID="$(tr -d '\n' < /proc/sys/kernel/random/uuid)"
PROBE_KEY="chat-emoticons/$PROBE_DATE/$PROBE_UUID.png"
printf 'permission-probe' > "$PROBE_FILE"

aws s3 cp "$PROBE_FILE" "s3://lirouti-prod-bucket/$PROBE_KEY"
aws s3api list-objects-v2 \
  --bucket lirouti-prod-bucket \
  --prefix "chat-emoticons/$PROBE_DATE/" \
  --max-keys 1 >/dev/null
aws s3api delete-object \
  --bucket lirouti-prod-bucket \
  --key "$PROBE_KEY" >/dev/null

if aws s3api list-objects-v2 \
  --bucket lirouti-prod-bucket \
  --prefix profiles/ \
  --max-keys 1 >/tmp/chat-emoticon-list-denied.out 2>&1; then
  echo "허용하지 않은 prefix도 조회된다 — 정책 범위가 너무 넓다" >&2
  exit 1
fi
grep -q "AccessDenied" /tmp/chat-emoticon-list-denied.out || {
  echo "거부됐지만 사유가 AccessDenied가 아니다" >&2
  exit 1
}
echo "채팅 이모티콘 IAM 권한 OK"
```

이 검증은 테스트 오브젝트를 마지막에 삭제한다. 중간 실패로 남더라도 실제 발급 규칙과 같은 날짜·UUID key라 정리 배치가 grace 기간 뒤 회수할 수 있다. 실패하면 관리자 이모티콘 등록을 열기 전에 IAM 정책 적용 상태부터 바로잡는다.

#### 실측 결과 (2026-07-27)

부착 직후 위 절차로 확인한 값이다. **거부돼야 하는 셋이 모두 거부된 것**이 최소 권한이 제대로 걸렸다는 증거다 — 권한이 넓게 새면 이 중 하나는 통과한다.

| 확인 | 기대 | 결과 |
| --- | --- | --- |
| 호스트 IMDS `iam/info` | 200 | ✅ `Code: Success`, `instance-profile/lirouti-ec2-role` |
| **app 컨테이너** IMDS `iam/info` | 200 | ✅ `Code: Success` (부착 전에는 404였다) |
| `sts get-caller-identity` | assumed-role | ✅ `assumed-role/lirouti-ec2-role/i-0cbaa8bf4e7f45dd8` |
| 백업 버킷 `PutObject` | 성공 | ✅ |
| 백업 버킷 `ListBucket` | **거부** | ✅ `AccessDenied` |
| 미디어 버킷 `PutObject` | 성공 | ✅ |
| 미디어 버킷 `GetObject` | 성공 | ✅ 내용까지 일치 (#22가 쓸 권한) |
| 백업 버킷 `GetObject` | **거부** | ✅ `403 Forbidden` (Put만 부여했으므로) |
| 미디어 버킷 `DeleteObject` | **거부** | ✅ `AccessDenied` <br>⚠️ 미참조 정리 도입 전 기록이다. 현재 정책 문서상 `challenge-verifications/`와 `chat-emoticons/` 아래만 **허용**이고 그 밖의 경로는 여전히 거부다 |

앱 상태도 함께 확인했다 — 컨테이너 `running`, `GET /api/challenges` 200, 재시작 후 로그에 자격증명·S3 오류 0건.

> 검증에 올린 테스트 객체(`s3://lirouti-db-backup/iam-check.txt`, `s3://lirouti-prod-bucket/iam-check/probe.txt`, `s3://lirouti-prod-bucket/iam-check/tiny.bin`)는 **인스턴스 역할로 지울 수 없다**(DeleteObject 미부여). 콘솔에서 지운다. 미디어 쪽은 `iam-check/` prefix에 두어 실제 인증 사진(`challenge-verifications/`)과 섞이지 않게 했다.

#### 추가 실측 (2026-07-28 KST)

> 날짜는 KST다. 서버·GitHub·S3 로그는 UTC라 아홉 시간 차이로 하루가 어긋나 보일 수 있다.

| 확인 | 결과 |
| --- | --- |
| 백업 버킷 기존 오브젝트 **덮어쓰기** | ⚠️ **성공** — 앱 역할로 덮어써진다. [2) 정책 생성](#2-정책-생성) 경고 참고 |
| 백업 버킷 `HeadObject` | ✅ `403` (GetObject 미부여 — 덮어쓴 결과를 읽어볼 수도 없다) |
| `s3:GetBucketVersioning` | ✅ `AccessDenied` — 설정 조회는 막힌다. 다만 **켜졌는지는 `PutObject` 응답의 `VersionId`로 확인된다**(2026-08-03 KST 확인, 활성화됨 — `backup.sh --check-versioning`) |
| 미디어 공개 URL 익명 GET | ✅ `403` — 버킷 비공개가 유지되고 있다(사진 서빙은 #39) |
| 백업 cron 등록 여부 | ⚠️ 당시 **미등록** — `backup.sh`는 있으나 `crontab` 비어 있었음. **2026-08-03 KST 등록 완료**(`0 19 * * *`) |
| 호스트에서 `localhost:8080` | ✅ `GET /api/challenges` 200, `POST /api/media/presigned-url` 403(인증 필요) — ③ 명령의 전제 확인 |

> 위 표는 2026-07-28 시점의 기록이다. **#77 이후 `GET /api/challenges`는 403이다** — 챌린지 조회에도 로그인이 필요해졌다. 기동만 확인하려면 인증이 필요 없는 `/health`를 쓴다.

③(presigned URL 발급)은 로그인 토큰이 필요해 이번에 직접 호출하지는 않았다. 다만 컨테이너가 자격증명을 받고(②) 그 역할로 미디어 버킷 `PutObject`가 되는 것(위 표)까지 확인됐으므로, 서명 경로가 막힐 이유는 남아 있지 않다.

### 알아둘 것

- **컨테이너에서 IMDS 도달 여부** — Docker 브리지가 홉을 하나 더 쓰기 때문에 인스턴스의 `http-put-response-hop-limit`이 1이면 컨테이너가 자격증명을 못 받는다. 이 인스턴스는 **도달 가능한 것을 확인했다** — 2026-07-27 `app` 컨테이너(`app_default` 브리지) 안에서 `169.254.169.254:80` TCP 연결 성공, 호스트에서는 토큰·메타데이터 모두 200. 다른 인스턴스로 옮길 때는 위 §6 ①로 다시 확인한다.
- **presigned URL 만료** — instance profile 자격증명은 임시 자격증명이라, 그것으로 서명한 URL은 **자격증명이 만료되면 함께 무효가 된다.** 현재 설정은 `PT5M`(5분)이라 문제없지만, `S3Properties`가 최대 7일까지 허용하므로 길게 잡으면 원인 불명의 403이 난다.
- **`aws-cli`는 서버에 설치돼 있다** — 2026-07-27 검증 때 `sudo snap install aws-cli --classic`으로 깔았다(v2). §6 ②와 `backup.sh` 둘 다 이걸 쓴다. 인스턴스를 새로 띄우면 다시 깔아야 한다.
- **리전을 명시해야 하는 경우** — 서버에 `AWS_REGION`이 앱 컨테이너 환경변수로만 있고 호스트 셸에는 없다. 호스트에서 `aws` 명령을 직접 쓸 때는 `AWS_DEFAULT_REGION=ap-northeast-2`를 함께 준다(`backup.sh`는 버킷 URL로 해결되므로 영향 없다).

## 배포 · 롤백

> **`develop` 머지는 개발 서버로, `main` 머지는 운영 서버로 배포된다.** 별도 릴리스 절차(태그)는 없고, 운영에 내보내려면 `develop` → `main` 릴리스 PR을 연다. **배포 워크플로가 `test.yml`을 첫 잡으로 호출해, 배포될 그 커밋의 테스트가 통과해야 빌드·배포가 진행된다.** PR 단계에서도 브랜치 push로 같은 테스트가 돌고, 그 결과(`Build & Test`)가 브랜치 보호의 required status check로 걸려 있다.
> 수동 배포가 필요하면 Actions → `Deploy to production` / `Deploy to development` → Run workflow로 실행한다.
>
> :information_source: **`develop`·`main`의 테스트는 "Test" 워크플로에 단독으로 뜨지 않는다.** `test.yml`은 그 둘의 push를 무시하고(`branches-ignore: [develop, main]`), 대신 배포 워크플로가 이를 호출한다. 그래서 **그 테스트 결과는 해당 배포 실행 안의 `test` 잡에서 확인**한다. 같은 커밋을 두 번 테스트하지 않기 위한 것이다(피처 브랜치·PR은 "Test"에 그대로 단독으로 뜬다).

이미지는 빌드 시 **`:<커밋 sha>`** 로 GHCR에 올라가고, **운영 배포에서만 `:latest`가 함께 붙는다.**
운영 compose는 `${APP_IMAGE_TAG:-latest}`를 참조하고, **배포 워크플로가 그 배포의 커밋 sha를 서버 `.env`의 `APP_IMAGE_TAG`에 고정**한다.
`latest`는 사람이 보기 위한 이동형 태그이고, 서버가 실제로 받는 것은 **커밋 sha 이미지**다 — 그래야 "정확히 그 커밋으로 되돌리기"가 성립한다(결정적 배포).

> 개발 배포가 `:latest`를 옮기지 않는 이유는, 그 태그가 **"지금 운영에 나가 있는 것"** 을 가리켜야 하기 때문이다. 개발 배포까지 옮기면 그 뜻이 사라진다.

**롤백**

되돌릴 커밋 sha는 Actions의 Deploy 실행 이력에서 찾는다(각 실행에 커밋이 표시된다).

```bash
# (A) 빠름: 서버에서 직접 이전 커밋으로 (이미지가 이미 GHCR에 있으므로 재빌드 불필요)
cd /opt/app
grep -q '^APP_IMAGE_TAG=' .env \
  && sed -i 's/^APP_IMAGE_TAG=.*/APP_IMAGE_TAG=<이전_커밋sha>/' .env \
  || echo 'APP_IMAGE_TAG=<이전_커밋sha>' >> .env
docker compose pull && docker compose up -d
```

> :warning: (A)는 **다음 배포까지만 유효하다.** 배포할 때마다 `.env`가 `ENV_FILE` 시크릿 기준으로 새로 쓰이고 `APP_IMAGE_TAG`도 그 배포의 커밋으로 덮어써진다. 되돌린 상태를 유지하려면 해당 브랜치(운영은 `main`)에서 문제 커밋을 revert하고 다시 머지한다(= 정석 경로).

```bash
# (B) 정석: 문제 커밋을 revert 해서 대상 브랜치(운영은 main)에 머지 → 자동 재배포
git revert <문제_커밋> && git push
```

> 참고: 데이터(MySQL·Redis)는 named volume(`dbdata`·`redisdata`)에 있어 이미지 교체와 무관하게 보존된다.
> `docker compose up -d`는 이미지가 바뀐 app 컨테이너만 재생성하며, db·redis는 그대로 유지된다.

## 런타임 설정 변경(`.env`)

`.env`는 배포 때 GitHub Secret `ENV_FILE`을 기준으로 새로 생성된다. 값을 바꾸려면:

1. repo → Settings → Secrets and variables → Actions → `ENV_FILE` 수정
2. 대상 브랜치에 머지하거나 Actions에서 해당 배포를 수동 실행 (시크릿 변경만으로는 배포가 트리거되지 않는다)
   > 환경마다 `ENV_FILE`이 따로 있다. 운영 값을 고쳤으면 `production` 환경의 시크릿인지 확인한다.

> :warning: 서버에서 `.env`를 직접 고치는 것은 **긴급 임시 조치로만** 쓴다. 다음 배포에서 시크릿 내용으로 원복되므로, 반드시 `ENV_FILE`에도 반영해 둘 것.
> 서버에서 임시로 고쳤을 때 반영하려면 `docker compose up -d`(재생성)를 쓴다. `docker compose restart`는 **기존 컨테이너를 그대로 재시작해 환경변수가 갱신되지 않는다.**

## Flyway 운영 (#48 · #49)

스키마는 Flyway가 만들고 Hibernate(`ddl-auto: validate`)는 대조만 한다. 그래서 **마이그레이션이 어긋나면 앱이 아예 뜨지 않는다** — 의도된 게이트지만, 막혔을 때 풀 방법을 알고 있어야 한다.

### 상태 확인 · 복구 (로컬)

```bash
task db-info      # 파일과 DB를 대조 — Pending / Missing / Failed 구분
task db-repair    # 체크섬 재계산 + 실패한 이력 행 정리
task db-history   # 이력 테이블을 그대로 조회
```

`db-info`와 `db-history`는 다르다. `db-history`는 **DB에 뭐가 적혀 있는지**만 보여주고, `db-info`는 **파일과 대조**해 `Pending`(파일은 있는데 미적용)·`Missing`(적용됐는데 파일이 없음)·`Failed`를 구분한다. 부팅이 막혔으면 `db-info`부터 본다.

> **`repair`는 이력 테이블만 고친다.** 실제 스키마는 건드리지 않는다.

두 경우를 구분해야 한다. **섞으면 스키마가 조용히 어긋난다.**

**(가) 체크섬 불일치** — 이미 적용된 `V__` 파일을 고쳐서 `Migration checksum mismatch`가 난 경우다. DB 상태는 멀쩡하고 기록만 어긋나 있다.

1. 파일을 **원래대로 되돌린다**(적용된 마이그레이션은 수정하지 않는 것이 원칙이다)
2. 되돌릴 수 없으면 `db-repair`로 체크섬을 재계산한다
3. **바꾸려던 스키마 변경은 새 `V__`로 추가한다** — `repair`는 파일 내용을 DB에 반영해 주지 않는다

**(나) 마이그레이션 실패** — 중간에 죽어 `success = 0` 행이 남은 경우다. **여기서는 `repair`부터 돌리면 안 된다.**

MySQL은 DDL이 트랜잭션으로 롤백되지 않는다. 즉 **죽기 전까지 실행된 문장은 이미 반영돼 있다.** 이 상태로 `repair`만 돌려 실패 기록을 지우고 재실행하면 `Table already exists` 같은 오류로 다시 죽거나, 더 나쁘게는 절반만 적용된 스키마 위에 나머지가 얹힌다.

1. `db-info`로 어느 버전이 `Failed`인지 확인한다
2. **그 `V__` 파일을 열어 어디까지 적용됐는지 DB와 대조한다**
3. 적용된 부분을 **수동으로 되돌리거나**, 마이그레이션을 재실행해도 안전하도록(`IF NOT EXISTS` 등) 고친다
4. 그 다음 `db-repair`로 실패 기록을 지운다
5. 앱을 다시 띄워 **같은 `V__`를 다시 실행**시킨다 (새 번호를 만들지 않는다 — 그 버전은 아직 성공한 적이 없다)

### 운영에서 복구가 필요할 때

서버에는 `task`가 없다. 같은 일을 도커로 직접 한다.

```bash
ssh <서버>
cd /opt/app
source .env    # DB_ROOT_PASSWORD 등

# 상태 확인 (repair 전에 반드시 먼저 본다)
sudo docker run --rm --network container:$(sudo docker compose ps -q db) \
  -v /opt/app/migration:/flyway/sql:ro flyway/flyway:11 \
  -url=jdbc:mysql://localhost:3306/lirouti -user=root -password="$DB_ROOT_PASSWORD" info
```

운영 이미지에는 마이그레이션 파일이 jar 안에 들어 있어 호스트 경로로 바로 마운트할 수 없다. **복구가 필요하면 해당 커밋의 `src/main/resources/db/migration`을 서버 `/opt/app/migration`에 scp로 올린 뒤** 위 명령을 쓴다. `info`로 원인을 확인하고, 필요할 때만 마지막 인자를 `repair`로 바꾼다.

> **운영에서 `repair`를 돌리기 전에 백업을 확인한다.** 이력 테이블을 고치는 작업이라 되돌리기 어렵다.

대부분의 경우 **더 안전한 길은 롤백**이다(위 [배포 · 롤백]). 이전 커밋 이미지로 되돌려 서비스를 살린 뒤, 마이그레이션을 고쳐 새로 배포한다.

> ⚠️ **다만 롤백이 통하지 않는 마이그레이션이 있다.** 새 컬럼을 `NOT NULL`(기본값 없음)로 만드는 것이 그렇다 — 옛 앱은 그 컬럼을 모르니 INSERT 에서 빼고, STRICT 모드가 `errno 1364`로 거절한다.
>
> **가장 나쁜 점은 앱이 멀쩡히 뜬다는 것이다.** Hibernate 의 `validate` 는 DB 에만 있는 여분 컬럼을 문제 삼지 않아 기동도 헬스체크도 통과하고, **해당 쓰기 요청만 전부 500** 이 된다. 지표만 보면 정상으로 보인다.
>
> `challenge_verification.period_start_date`(V20260809202546)가 그 예다. **이 뒤로 문제가 생기면 되돌리지 말고 앞으로 배포한다.** 새 마이그레이션을 쓸 때는 롤백 가능성을 함께 판단하고, 못 되돌리는 것이면 그 파일 맨 위에 적어 둔다.

### 버전 번호 중복은 CI가 막는다

두 브랜치가 각각 `V4__a.sql`·`V4__b.sql`을 추가하면 **파일명이 달라 git이 충돌로 보지 않는다.** 양쪽 다 조용히 머지되고, 배포 후 부팅에서 죽는다 — `Found more than one migration with version 4`.

`test.yml`의 **Check migration version duplicates** 스텝이 이걸 잡는다. 로컬에서 미리 보려면 `task db-check-duplicates`.

### `ddl-auto` 우회를 되돌리기 (#49)

마이그레이션 문제로 부팅이 막혔을 때 `ENV_FILE`에 `JPA_DDL_AUTO=update`를 넣어 한시적으로 넘길 수 있다. **막아두지 않은 것은 의도다** — 머지가 곧 배포인 구조에서 긴급 우회 경로가 없는 쪽이 더 위험하다.

> 개발 서버가 생긴 뒤로는 이 우회를 쓸 일이 줄어야 한다. 마이그레이션 문제는 `develop` 배포에서 먼저 걸리므로, **운영에서 우회를 켜는 상황 자체가 개발 서버를 건너뛰었다는 뜻**이다.

대신 우회가 방치되지 않도록 두 군데서 드러낸다.

| 어디서 | 무엇을 보는가 |
| --- | --- |
| 배포 Discord 알림 | `ENV_FILE`의 `JPA_DDL_AUTO`가 **비어 있지 않고 `validate`도 아니면** 경고 한 줄 추가(대소문자 무시). 배포 성패와 무관하게 붙는다 |
| 앱 기동 로그 | `prod` 프로파일에서 `ddl-auto != validate`면 WARN (`SchemaManagementGuard`) |

**둘 다 필요하다.** 워크플로는 시크릿만 보므로 **서버 `.env`를 직접 고친 경우를 못 잡고**, 그건 앱 로그만 잡아낸다.

되돌리는 절차:

1. 막혔던 마이그레이션을 고친다 (새 `V__` 추가 — 이미 적용된 파일은 수정하지 않는다)
2. `ENV_FILE` 시크릿에서 `JPA_DDL_AUTO` 줄을 **지운다**
3. 대상 브랜치 머지 또는 해당 배포 수동 실행
4. Discord 알림에 경고가 사라졌는지, 앱 로그에 WARN이 없는지 확인

## 메모리 예산 (t4g.small 2GB)

JVM(힙 768m + 메타 192m) + MySQL(buffer 256m) + Redis(≤200m) ≈ 1.6GB + OS. 빠듯하므로 `docker-compose.prod.yml`의 상한들을 임의로 늘리지 말 것. OOM 시 `dmesg`·`docker stats`로 확인.
