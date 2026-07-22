# 배포 가이드 (EC2 · Docker · GHCR)

챌린지 백엔드는 **GitHub Actions에서 arm64 Docker 이미지를 빌드 → GHCR에 push → EC2(t4g.small)가 pull해서 docker compose로 실행**한다. 서버에서 Gradle 빌드를 하지 않는다(2GB 메모리 보호).

## 파일 배치

| 위치 | 파일 | 커밋 | 역할 |
| --- | --- | --- | --- |
| 레포 | `Dockerfile` | O | 실행 jar를 담는 이미지 정의(빌드 스테이지 없음) |
| 레포 | `.github/workflows/test.yml` | O | PR·push 테스트 게이팅 |
| 레포 | `.github/workflows/deploy.yml` | O | 태그(v*) push 시 이미지 빌드·배포 |
| 레포 | `docker-compose.local.yml` | O | 로컬 개발용 DB·Redis (앱은 IDE/bootRun) |
| 레포 | `deploy/docker-compose.prod.yml` | O(레퍼런스) | 서버 운영 compose 템플릿 |
| 레포 | `deploy/.env.example` | O | 서버 `.env` 키 템플릿(실값 없음) |
| 레포 | `deploy/backup.sh` | O(레퍼런스) | DB 백업 스크립트 |
| **서버** | `/opt/app/docker-compose.yml` | **X** | 위 prod 템플릿을 복사한 실제 파일 |
| **서버** | `/opt/app/.env` | **X** | 실제 비밀값 (DB 비번·JWT_SECRET 등) |
| **서버** | `/opt/app/backup.sh` | **X** | 위 스크립트 복사 |

> 비밀 분리 원칙: **파이프라인 비밀 = GitHub Secrets**, **런타임 비밀 = 서버 `.env`**. DB 비밀번호는 GitHub 어디에도 두지 않는다.

## GitHub 설정 (1회)

1. **Secrets** (repo → Settings → Secrets and variables → Actions):
   - `SERVER_HOST` — EC2 퍼블릭 IP(EIP)
   - `SERVER_SSH_KEY` — 접속용 pem 개인키 전문
2. **GHCR 이미지 공개 범위**: **기본은 private 유지 + 서버에서 1회 로그인**(팀 합의). 이미지를 공개하지 않고, 첫 배포부터 바로 pull된다. `read:packages` 권한 PAT를 하나 만들어, **토큰을 명령행/히스토리에 남기지 않도록** 프롬프트로 입력받아 로그인한다:
   ```bash
   read -rsp 'GHCR PAT(read:packages): ' PAT && echo "$PAT" | docker login ghcr.io -u <github사용자명> --password-stdin; unset PAT
   ```
   > ⚠️ 로그인하면 PAT가 서버 `~/.docker/config.json`에 **평문(base64)으로 저장**된다(EC2엔 credential store가 없음). **`read:packages` 최소권한**으로만 만들고 **만료를 짧게(예: 90일) 두어 주기적으로 교체**할 것. 유출돼도 이미지 읽기 권한뿐이다.
   - (대안) 관리 편의를 원하면 패키지를 **public으로 전환**(repo → Packages → 해당 패키지 → Package settings → Change visibility → Public). 이 경우 서버 로그인은 필요 없지만, 컴파일된 이미지가 공개되고 패키지는 첫 배포 push 후에 생기므로 "첫 배포 → public 전환 → 재실행" 순서가 한 번 필요하다.

## 서버 최초 세팅 (1회, Ubuntu 24.04 arm64)

> **전제**: EC2(t4g.small)가 떠 있고, 아래 [AWS / 네트워크] 절의 **IAM Role(instance profile)**·**보안그룹**을 먼저 맞춰둔다. 여기부터는 서버에 SSH로 접속(`ssh -i <pem> ubuntu@<서버IP>`)해서 하는 작업이다.

### 1) Docker + compose 플러그인 설치 · swap 2GB

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker ubuntu     # 그룹 반영을 위해 로그아웃 후 재접속(또는 `newgrp docker`)
docker compose version             # v2 플러그인 정상 확인

# swap 2GB — 메모리 예산(≈1.6GB+OS)이 빠듯해 피크 시 OOM killer가 컨테이너를 죽이는 것 방지(완충재).
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅에도 유지
```

### 2) 앱 폴더 생성

```bash
sudo mkdir -p /opt/app && sudo chown ubuntu:ubuntu /opt/app
cd /opt/app
```

### 3) 배포 파일 3개를 서버로 올린다

레포 `deploy/`의 템플릿을 `/opt/app`에 **아래 이름으로** 배치한다. 레포가 private이라 서버에서 직접 clone하면 인증이 필요하므로, **로컬 PC에서 scp**가 가장 간단하다.

```bash
# 로컬 PC의 레포 루트에서 실행 (<pem>·<서버IP>는 본인 값)
scp -i <pem> deploy/docker-compose.prod.yml ubuntu@<서버IP>:/opt/app/docker-compose.yml
scp -i <pem> deploy/backup.sh               ubuntu@<서버IP>:/opt/app/backup.sh
scp -i <pem> deploy/.env.example            ubuntu@<서버IP>:/opt/app/.env
```

> 파일명 매핑 주의: `docker-compose.prod.yml` → 서버에선 **`docker-compose.yml`**, `.env.example` → **`.env`**. 배포 워크플로가 `cd /opt/app` 후 이 파일명 그대로를 쓴다.

### 4) `.env` 실제 값 채우기 (런타임 비밀 — 서버에만 둔다)

```bash
# .env엔 DB 비번·JWT·OAuth 시크릿이 들어있다. scp 직후 보통 0644이므로 편집 전에 소유자만 읽게 잠근다.
cd /opt/app && chmod 600 .env && nano .env && chmod +x backup.sh
```

채워야 하는 값(빈/기본값이면 부팅 실패 = fail-fast):

| 키 | 설명 |
| --- | --- |
| `DB_ROOT_PASSWORD` · `DB_PASSWORD` | MySQL 초기화 비밀번호(임의 강문자열). 최초 기동 때 볼륨에 각인되므로 이후 변경하려면 볼륨 초기화 필요 |
| `JWT_SECRET` | 32바이트 이상 랜덤 — `openssl rand -base64 48` |
| `KAKAO_APP_ID` | 카카오 앱 ID |
| `GOOGLE_WEB_CLIENT_ID` · `GOOGLE_ALLOWED_ISSUERS` | 구글 OAuth |
| `AWS_S3_BUCKET` · `AWS_REGION` | S3 (자격증명은 IAM Role로 자동 획득 — 여기 두지 않는다) |
| `APP_IMAGE_TAG` | `latest` 그대로 둔다. 배포 워크플로가 이번 릴리스 버전으로 덮어쓴다 |

### 5) GHCR 로그인 (B안, 1회)

위 [GitHub 설정] 2번과 동일하다. `read:packages` PAT로 한 번 로그인해두면 이후 `docker compose pull`이 계속 동작한다. 자격은 `~/.docker/config.json`에 **평문(base64)으로 저장**되므로(→ 재부팅해도 유지되지만 at-rest 위험 존재), **최소권한·짧은 만료로 만들고 주기적으로 교체**한다.

```bash
read -rsp 'GHCR PAT(read:packages): ' PAT && echo "$PAT" | docker login ghcr.io -u <github사용자명> --password-stdin; unset PAT
```

### 6) DB·Redis만 먼저 기동

앱 이미지는 **첫 릴리스 태그를 push해야 GHCR에 생긴다.** 그래서 최초에는 앱(`app`)을 빼고 데이터 컨테이너만 올린다 — 여기서 `docker compose up -d`(전체)를 돌리면 존재하지 않는 앱 이미지 pull로 실패한다.

```bash
cd /opt/app
docker compose up -d db redis
docker compose ps        # db·redis 가 healthy 인지 확인
```

### 7) 첫 배포는 태그 push로 (서버에서 `up app` 수동 실행 안 함)

**로컬에서 릴리스 태그를 push**하면 Actions가 이미지 빌드 → GHCR push → 서버 SSH 접속 → `.env`의 `APP_IMAGE_TAG` 고정 → `docker compose pull && up -d`까지 자동으로 한다.

```bash
# 로컬 PC(레포)에서
git tag v0.0.1 && git push origin v0.0.1
```

워크플로가 배포 후 `http://localhost:8080/api/challenges` 응답을 최대 ~90초 확인하고, 안 뜨면 배포 실패로 처리한다. 서버에서 직접 로그를 보려면:

```bash
cd /opt/app && docker compose logs -f app
```

### 8) 백업 cron 등록

아래 [AWS / 네트워크] 절의 cron 항목 참고 (매일 04:00 KST).

## AWS / 네트워크

- **EC2 instance profile(IAM Role)** 부착: S3 `PutObject`(백업) + 앱 미디어 업로드용 `PutObject/GetObject`. access key를 서버에 두지 않는다.
- **보안그룹 인바운드**:
  - `8080` — 베타 HTTP 직접 노출.
  - `22`(SSH) — **`0.0.0.0/0` 개방 + 키 인증 전용(비밀번호 로그인 비활성 확인)**. 배포가 GitHub Actions 러너에서 SSH로 접속하는데 러너 IP가 유동적이라 대역 제한이 어렵다. ED25519 키 인증만 허용하는 전제로 1개월 한시 운영에 한해 전체 개방한다.
    > ⚠️ **한시 조치다.** 데모데이 이후 유지보수를 이어가 실 운영으로 전환하는 시점에 **IP 화이트리스트 정책을 도입**한다(배포 시 러너의 현재 IP만 보안그룹에 임시 허용 → 배포 종료 후 회수하는 동적 화이트리스트). 그때까지는 개방 종료일을 트래킹하고, 철거 시 인스턴스와 함께 정리한다.
  - `3306`(MySQL)·`6379`(Redis) — **열지 않는다**(compose 내부 네트워크 전용).
- **S3 버킷**: `lirouti-prod-bucket`(미디어)·`lirouti-db-backup`(백업) 이름으로 생성(비공개). 서버 `.env`의 `AWS_S3_BUCKET`·`backup.sh`의 `BUCKET`을 이 이름과 일치시킨다.
- **백업 cron** (매일 04:00 KST): `crontab -e` → `0 4 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1`

## 배포 · 롤백

> **머지만으로는 배포되지 않는다.** develop/main에 머지한 뒤, 릴리스 시점에 **`v*` 태그를 push**해야 배포된다. 수동 실행(Actions → Deploy → Run workflow)도 가능하지만 **ref 드롭다운에서 `v*` 태그를 선택**해야 한다 — 브랜치를 고르면 `Validate release tag` 스텝에서 실패한다(브랜치명이 이미지 태그·`APP_IMAGE_TAG`로 박혀 롤백 기준이 깨지는 것을 막기 위함). 태그는 semver(`vMAJOR.MINOR.PATCH`, 예: `v0.0.1`)를 따른다. 팀은 "머지=통합, 태그 push=릴리스"로 구분한다.

이미지는 push 시 **`:latest` + `:<버전>`(git 태그) + `:<커밋 sha>`** 세 태그로 GHCR에 올라간다.
운영 compose는 `${APP_IMAGE_TAG:-latest}`를 참조하고, **배포 워크플로가 이번에 push한 버전을 서버 `.env`의 `APP_IMAGE_TAG`에 고정**한다.
즉 서버는 항상 "latest"가 아니라 **정확히 그 버전 이미지**를 돌린다(결정적 배포).

```bash
# 배포: 릴리스 태그를 push하면 Actions가 이미지 빌드→GHCR→서버가 그 버전으로 고정 배포
git tag v1.0.2 && git push origin v1.0.2      # → 서버가 :v1.0.2 이미지로 구동
```

**롤백** — 두 가지 방법:

```bash
# (A) 빠름: 서버에서 직접 이전 버전으로 (이미지가 이미 GHCR에 있으므로 재빌드 불필요)
cd /opt/app
# APP_IMAGE_TAG가 없으면 latest로 새므로, 없으면 추가하고 있으면 교체한다(fail-closed)
grep -q '^APP_IMAGE_TAG=' .env \
  && sed -i 's/^APP_IMAGE_TAG=.*/APP_IMAGE_TAG=v1.0.1/' .env \
  || echo 'APP_IMAGE_TAG=v1.0.1' >> .env
docker compose pull && docker compose up -d

# (B) CI로: Actions 탭 → Deploy 워크플로 → Run workflow에서 이전 태그(v1.0.1) 선택
#     → 서버 .env가 v1.0.1로 다시 고정되어 재배포됨
```

> 참고: 데이터(MySQL·Redis)는 named volume(`dbdata`·`redisdata`)에 있어 이미지 교체와 무관하게 보존된다.
> `docker compose up -d`는 이미지가 바뀐 app 컨테이너만 재생성하며, db·redis는 그대로 유지된다.

## 메모리 예산 (t4g.small 2GB)

JVM(힙 768m + 메타 192m) + MySQL(buffer 256m) + Redis(≤200m) ≈ 1.6GB + OS. 빠듯하므로 `docker-compose.prod.yml`의 상한들을 임의로 늘리지 말 것. OOM 시 `dmesg`·`docker stats`로 확인.
