#!/usr/bin/env bash
# DB 백업(레퍼런스). 서버 /opt/app/backup.sh 로 복사 후 chmod +x.
#
# 사전 준비:
#   1) EC2에 S3 PutObject 허용 instance profile 부착 (access key를 서버에 두지 않기 위함)
#   2) AWS CLI 설치:  sudo snap install aws-cli --classic
#   3) 아래 BUCKET을 실제 백업 버킷으로 수정
#
# cron 등록 (매일 04:00 KST = 19:00 UTC):
#   crontab -e
#   0 19 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1
#
# 서버 시간대가 UTC다. 스케줄을 04로 적으면 새벽이 아니라 낮 1시에 돈다.
#
# 사용법:
#   backup.sh                      백업 (cron이 인자 없이 이 형태로 부른다)
#   backup.sh --check-versioning   버킷 버전 관리가 켜져 있는지 확인
#
# 앱·DB가 한 인스턴스에 동거하므로 인스턴스 소멸 = 데이터 소멸. S3 외부 백업이 필수 보완재다.

set -euo pipefail

# cron은 PATH를 /usr/bin:/bin 으로만 준다. aws는 snap으로 설치돼 /snap/bin에 있으므로,
# 이 줄이 없으면 덤프까지 다 끝낸 뒤 마지막 업로드에서 "aws: command not found"로 죽는다.
export PATH="${PATH}:/snap/bin"

# 서버는 UTC인데 파일 이름은 한국 날짜여야 한다.
# 04:00 KST 실행분이 UTC 기준으론 전날이라, 이 줄이 없으면 백업 이름이 하루씩 밀린다.
export TZ="Asia/Seoul"

MODE="${1:-backup}"

cd /opt/app
source .env

BUCKET="lirouti-db-backup"           # 확정 버킷명. AWS에 이 이름으로 비공개 버킷을 생성해 둘 것.
STAMP="$(date +%F)"
KEY="${STAMP}.sql.gz"

# 덤프 파일은 DB 전체가 담긴 민감 파일이다.
# mktemp는 600 권한으로 생성하고, trap으로 성공·실패 무관하게 반드시 지운다.
FILE="$(mktemp)"
trap 'rm -f "${FILE}"' EXIT

docker compose exec -T db \
  mysqldump -u root -p"${DB_ROOT_PASSWORD}" --single-transaction --routines lirouti \
  < /dev/null | gzip > "${FILE}"

if [ "${MODE}" = "--check-versioning" ]; then
  # 버전 관리 여부는 설정 조회로 못 본다 — 역할에 s3:GetBucketVersioning이 없다.
  # 대신 켜진 버킷은 PutObject 응답에 VersionId를 실어 보내므로 그것으로 판별한다.
  # aws s3 cp는 이 응답을 출력하지 않아 s3api를 쓴다.
  #
  # 올리는 것이 방금 뜬 진짜 덤프여야 한다. 전용 확인용 키를 쓰면 역할에 DeleteObject가
  # 없어 지우지 못하고 남고, 아무 파일이나 이 키에 올리면 그 키의 최신 버전이 그 파일이
  # 되어 복구 때 그것을 집는다. 그래서 확인도 백업과 같은 경로로 돈다.
  VERSION_ID="$(aws s3api put-object \
    --bucket "${BUCKET}" --key "${KEY}" --body "${FILE}" \
    --query VersionId --output text)"

  if [ "${VERSION_ID}" = "None" ] || [ -z "${VERSION_ID}" ]; then
    echo "[$(date '+%F %T')] 버전 관리 꺼짐 — 덮어쓰기가 복구 불가다. 콘솔에서 켤 것"
    exit 1
  fi
  echo "[$(date '+%F %T')] 버전 관리 켜짐 (${KEY} VersionId=${VERSION_ID})"
  exit 0
fi

aws s3 cp "${FILE}" "s3://${BUCKET}/${KEY}"

echo "[$(date '+%F %T')] backup ok: ${KEY}"
