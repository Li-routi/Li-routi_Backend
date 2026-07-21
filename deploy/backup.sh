#!/usr/bin/env bash
# DB 백업(레퍼런스). 서버 /opt/app/backup.sh 로 복사 후 chmod +x.
#
# 사전 준비:
#   1) EC2에 S3 PutObject 허용 instance profile 부착 (access key를 서버에 두지 않기 위함)
#   2) AWS CLI 설치:  sudo snap install aws-cli --classic
#   3) 아래 BUCKET을 실제 백업 버킷으로 수정
#
# cron 등록 (매일 04:00 KST):
#   crontab -e
#   0 4 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1
#
# 앱·DB가 한 인스턴스에 동거하므로 인스턴스 소멸 = 데이터 소멸. S3 외부 백업이 필수 보완재다.

set -euo pipefail

cd /opt/app
source .env

BUCKET="s3://lirouti-db-backup"      # TODO: 실제 버킷명으로 수정
STAMP="$(date +%F)"

# 덤프 파일은 DB 전체가 담긴 민감 파일이다.
# mktemp는 600 권한으로 생성하고, trap으로 성공·실패 무관하게 반드시 지운다.
FILE="$(mktemp)"
trap 'rm -f "${FILE}"' EXIT

docker compose exec -T db \
  mysqldump -u root -p"${DB_ROOT_PASSWORD}" --single-transaction --routines lirouti \
  | gzip > "${FILE}"

aws s3 cp "${FILE}" "${BUCKET}/${STAMP}.sql.gz"

echo "[$(date '+%F %T')] backup ok: ${STAMP}.sql.gz"
