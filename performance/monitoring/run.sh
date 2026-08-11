#!/usr/bin/env bash

set -eu

fail() {
    printf '❌ %s\n' "$1" >&2
    exit 1
}

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
compose_file="$script_dir/docker-compose.yml"
users_file="$project_root/performance/jmeter/users.csv"
secrets_dir="$script_dir/secrets"
actuator_token_file="$secrets_dir/actuator-token"
grafana_password_file="$secrets_dir/grafana-admin-password"
grafana_volume="lirouti-monitoring_grafana-data"

docker_compose() {
    docker compose -f "$compose_file" "$@"
}

require_docker() {
    command -v docker >/dev/null 2>&1 || fail "Docker가 설치되어 있지 않습니다."
    docker info >/dev/null 2>&1 || fail "Docker daemon이 실행 중이 아닙니다."
}

prepare_grafana_password() {
    [ -s "$grafana_password_file" ] && return

    # Grafana admin 비밀번호는 최초 DB 생성 때만 적용되므로 기존 volume과 새 파일을 섞지 않는다.
    require_docker
    if docker volume inspect "$grafana_volume" >/dev/null 2>&1; then
        fail "Grafana data volume은 있지만 비밀번호 파일이 없습니다. 기존 비밀번호 복구 또는 volume 처리 방향을 먼저 결정하세요."
    fi

    command -v openssl >/dev/null 2>&1 || fail "Grafana 비밀번호 생성에 openssl이 필요합니다."
    touch "$grafana_password_file"
    chmod 0600 "$grafana_password_file"
    if ! password="$(openssl rand -hex 24)" || [ -z "$password" ]; then
        fail "Grafana 비밀번호 생성에 실패했습니다."
    fi
    printf '%s' "$password" > "$grafana_password_file"
}

prepare_secrets() {
    [ -s "$users_file" ] || fail "$users_file 이 없습니다. 먼저 task performance-token 을 실행하세요."

    header="$(sed -n '1{s/\r$//;p;}' "$users_file")"
    token="$(sed -n '2{s/\r$//;p;}' "$users_file")"
    if [ "$header" != "access_token" ] || [ -z "$token" ]; then
        fail "$users_file 은 첫 줄 access_token, 둘째 줄 token 형식이어야 합니다."
    fi
    case "$token" in
        *,*) fail "Actuator token에는 CSV 추가 컬럼을 사용할 수 없습니다." ;;
    esac

    mkdir -p "$secrets_dir"
    chmod 0700 "$secrets_dir"
    prepare_grafana_password

    touch "$actuator_token_file"
    chmod 0600 "$actuator_token_file"
    printf '%s' "$token" > "$actuator_token_file"

    # host에서는 0700 디렉터리가 접근을 막고, 파일은 서로 다른 non-root container UID가 읽어야 한다.
    chmod 0444 "$actuator_token_file" "$grafana_password_file"
    printf '✅ 모니터링 secret 파일을 준비했습니다. 값은 출력하지 않았습니다.\n'
}

check_config() {
    require_docker
    prepare_secrets
    docker_compose config --quiet
    docker_compose run --rm --no-deps --entrypoint promtool prometheus \
        check config /etc/prometheus/prometheus.yml
    printf '✅ Compose와 Prometheus 설정이 유효합니다.\n'
}

if [ "$#" -ne 1 ]; then
    fail "사용법: $0 secrets|check|up|status|logs|down"
fi

case "$1" in
    secrets)
        prepare_secrets
        ;;
    check)
        check_config
        ;;
    up)
        check_config
        # token 갱신 뒤에도 credentials file을 확실히 다시 읽도록 로컬 컨테이너를 재생성한다.
        docker_compose up -d --force-recreate
        docker_compose ps
        ;;
    status)
        require_docker
        docker_compose ps
        ;;
    logs)
        require_docker
        docker_compose logs --tail=100 -f
        ;;
    down)
        require_docker
        # volume을 지우지 않아 다음 측정에서도 7일 시계열과 Grafana DB를 이어서 사용한다.
        docker_compose down
        ;;
    *)
        fail "지원하지 않는 명령입니다: $1 (secrets|check|up|status|logs|down)"
        ;;
esac
