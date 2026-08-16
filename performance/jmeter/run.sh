#!/usr/bin/env bash

set -eu

fail() {
    printf '❌ %s\n' "$1" >&2
    exit 1
}

if [ "$#" -ne 1 ]; then
    fail "사용법: $0 smoke|load|spike|stress100"
fi

scenario="$1"

case "$scenario" in
    smoke)
        threads=1
        ramp_up_seconds=1
        duration_seconds=20
        think_time_millis=500
        ;;
    load)
        threads=5
        ramp_up_seconds=10
        duration_seconds=120
        think_time_millis=500
        ;;
    spike)
        threads=30
        ramp_up_seconds=3
        duration_seconds=60
        think_time_millis=500
        ;;
    stress100)
        threads=100
        ramp_up_seconds=30
        duration_seconds=120
        think_time_millis=500
        ;;
    *)
        fail "지원하지 않는 시나리오입니다: $scenario (smoke|load|spike|stress100)"
        ;;
esac

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
test_plan="$script_dir/api-read-load-test.jmx"
users_file="${JMETER_USERS_FILE:-$script_dir/users.csv}"
base_url="${JMETER_BASE_URL:-http://localhost:8080}"
result_root="${JMETER_RESULT_ROOT:-$script_dir/results}"
jmeter_command="${JMETER_BIN:-jmeter}"

command -v "$jmeter_command" >/dev/null 2>&1 || fail "JMeter를 찾지 못했습니다. jmeter --version을 확인해 주세요."
command -v curl >/dev/null 2>&1 || fail "서버 상태 확인에 필요한 curl을 찾지 못했습니다."
[ -f "$test_plan" ] || fail "JMX 파일이 없습니다: $test_plan"
[ -r "$users_file" ] || fail "access token CSV가 없습니다: $users_file"

case "$base_url" in
    http://localhost|http://localhost:*|http://127.0.0.1|http://127.0.0.1:*|https://localhost|https://localhost:*|https://127.0.0.1|https://127.0.0.1:*)
        ;;
    *)
        [ "${JMETER_ALLOW_NON_LOCAL:-false}" = "true" ] || fail "비로컬 서버 실행을 차단했습니다. 승인된 환경이면 JMETER_ALLOW_NON_LOCAL=true를 명시해 주세요."
        ;;
esac

printf '🔎 실행 전 확인: %s/health\n' "$base_url"
curl --fail --silent --show-error --max-time 3 "$base_url/health" >/dev/null \
    || fail "애플리케이션에 연결할 수 없습니다. task docker-up과 task run 상태를 확인해 주세요."

mkdir -p "$result_root"
run_dir="$(mktemp -d "$result_root/${scenario}-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
result_file="$run_dir/result.jtl"
log_file="$run_dir/jmeter.log"
report_dir="$run_dir/report"

printf '🚀 %s 테스트를 시작합니다: threads=%s, ramp-up=%ss, duration=%ss\n' \
    "$scenario" "$threads" "$ramp_up_seconds" "$duration_seconds"

cd "$project_root"
set +e
"$jmeter_command" -n \
    -t "$test_plan" \
    -JbaseUrl="$base_url" \
    -JusersFile="$users_file" \
    -Jthreads="$threads" \
    -JrampUpSeconds="$ramp_up_seconds" \
    -JdurationSeconds="$duration_seconds" \
    -JthinkTimeMillis="$think_time_millis" \
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
    -l "$result_file" \
    -j "$log_file" \
    -e \
    -o "$report_dir"
jmeter_status=$?
set -e

[ "$jmeter_status" -eq 0 ] || fail "JMeter 실행에 실패했습니다. 로그: $log_file"
[ -s "$result_file" ] || fail "JMeter 결과 파일이 비어 있습니다. 로그: $log_file"
[ -f "$report_dir/index.html" ] || fail "HTML 리포트가 생성되지 않았습니다. 로그: $log_file"

total_samples="$(awk 'END { print (NR > 0 ? NR - 1 : 0) }' "$result_file")"
failed_samples="$(awk -F, 'NR > 1 && $8 == "false" { failed++ } END { print failed + 0 }' "$result_file")"

printf '\n📊 결과: total=%s, failed=%s\n' "$total_samples" "$failed_samples"
printf '   JTL: %s\n' "$result_file"
printf '   로그: %s\n' "$log_file"
printf '   HTML: %s/index.html\n' "$report_dir"

if [ "$failed_samples" -gt 0 ]; then
    printf '\n실패 요약:\n' >&2
    awk -F, '
        NR > 1 && $8 == "false" {
            key = $3 " | " $4
            failures[key]++
        }
        END {
            for (key in failures) {
                printf "  %s건 | %s\n", failures[key], key
            }
        }
    ' "$result_file" >&2

    if awk -F, 'NR > 1 && $4 == "401" { found = 1 } END { exit(found ? 0 : 1) }' "$result_file"; then
        printf '  access token이 만료됐거나 유효하지 않습니다. 새 token으로 다시 실행해 주세요.\n' >&2
    fi

    exit 1
fi

printf '✅ %s 테스트가 오류 없이 끝났습니다.\n' "$scenario"
