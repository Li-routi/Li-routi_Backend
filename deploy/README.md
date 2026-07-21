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
2. **GHCR 이미지 공개 범위**: 첫 배포 후 GHCR 패키지가 생기면 **public으로 전환**(repo → Packages → 해당 패키지 → Package settings → Change visibility → Public). public이면 서버에서 별도 로그인이 필요 없다.
   - private로 두려면 서버에서 1회: `echo <PAT(read:packages)> | docker login ghcr.io -u <github사용자명> --password-stdin`

## 서버 최초 세팅 (1회, Ubuntu 24.04 arm64)

```bash
# 1) Docker + compose 플러그인 설치
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker ubuntu    # 재로그인 후 sudo 없이 docker 사용

# 2) 앱 폴더 준비
sudo mkdir -p /opt/app && sudo chown ubuntu:ubuntu /opt/app
cd /opt/app

# 3) compose·백업 스크립트·env 배치 (로컬에서 scp로 올리거나 레포에서 받아 복사)
#    - deploy/docker-compose.prod.yml → /opt/app/docker-compose.yml
#    - deploy/backup.sh              → /opt/app/backup.sh   (chmod +x)
#    - deploy/.env.example           → /opt/app/.env        (실제 값 입력!)
cp .env.example .env && nano .env      # DB_PASSWORD·JWT_SECRET 등 실제 값 채우기
chmod +x backup.sh

# 4) 최초 기동 (이후는 GitHub Actions가 자동 배포)
docker compose up -d
docker compose logs -f app             # 부팅 로그 확인
```

## AWS / 네트워크

- **EC2 instance profile(IAM Role)** 부착: S3 `PutObject`(백업) + 앱 미디어 업로드용 `PutObject/GetObject`. access key를 서버에 두지 않는다.
- **보안그룹 인바운드**: `8080`(베타 HTTP), `22`(SSH, 내 IP만). **`3306`은 열지 않는다**(MySQL은 compose 내부 전용).
- **백업 cron** (매일 04:00 KST): `crontab -e` → `0 4 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1`

## 배포 · 롤백

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
sed -i 's/^APP_IMAGE_TAG=.*/APP_IMAGE_TAG=v1.0.1/' .env
docker compose pull && docker compose up -d

# (B) CI로: Actions 탭 → Deploy 워크플로 → Run workflow에서 이전 태그(v1.0.1) 선택
#     → 서버 .env가 v1.0.1로 다시 고정되어 재배포됨
```

> 참고: 데이터(MySQL·Redis)는 named volume(`dbdata`·`redisdata`)에 있어 이미지 교체와 무관하게 보존된다.
> `docker compose up -d`는 이미지가 바뀐 app 컨테이너만 재생성하며, db·redis는 그대로 유지된다.

## 메모리 예산 (t4g.small 2GB)

JVM(힙 768m + 메타 192m) + MySQL(buffer 256m) + Redis(≤200m) ≈ 1.6GB + OS. 빠듯하므로 `docker-compose.prod.yml`의 상한들을 임의로 늘리지 말 것. OOM 시 `dmesg`·`docker stats`로 확인.
