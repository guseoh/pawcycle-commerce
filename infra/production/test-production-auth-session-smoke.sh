#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SMOKE_SCRIPT="$SCRIPT_DIR/verify-production-auth-session-smoke.sh"

TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT
mkdir -p "$TEST_ROOT/bin"
APPROVED_STATE_DIR="$TEST_ROOT/approved-state"
APPROVED_DOMAIN_FILE="$APPROVED_STATE_DIR/https-domain"
SMOKE_UNDER_TEST="$TEST_ROOT/verify-production-auth-session-smoke.sh"
mkdir -m 700 "$APPROVED_STATE_DIR"
printf '%s\n' 'ops017-test.duckdns.org' > "$APPROVED_DOMAIN_FILE"
chmod 600 "$APPROVED_DOMAIN_FILE"
sed "s|^APPROVED_DOMAIN_FILE=.*$|APPROVED_DOMAIN_FILE=\"$APPROVED_DOMAIN_FILE\"|" \
  "$SMOKE_SCRIPT" > "$SMOKE_UNDER_TEST"
chmod 700 "$SMOKE_UNDER_TEST"
bash -n "$SMOKE_SCRIPT" "$SMOKE_UNDER_TEST"

cat > "$TEST_ROOT/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

method=GET
output_file=""
header_output=""
cookie_jar=""
cookie_input=""
request_header=""
url=""
proto=""
max_redirs=""
has_tls=0
has_stdin_payload=0
arguments=("$@")
[[ "${arguments[0]:-}" == --disable ]] || exit 88
disable_count=0
for ((index = 0; index < ${#arguments[@]}; index += 1)); do
  argument="${arguments[$index]}"
  case "$argument" in
    --request) method="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --output) output_file="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --dump-header) header_output="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --cookie-jar) cookie_jar="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --cookie) cookie_input="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --header) request_header="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --proto) proto="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --max-redirs) max_redirs="${arguments[$((index + 1))]}"; ((index += 1)) ;;
    --connect-timeout|--max-time|--write-out) ((index += 1)) ;;
    --data-binary)
      [[ "${arguments[$((index + 1))]}" == @- ]] || exit 91
      has_stdin_payload=1
      ((index += 1))
      ;;
    --tlsv1.2) has_tls=1 ;;
    --disable) ((disable_count += 1)) ;;
    --silent|--show-error) ;;
    --insecure|-k|--location|-L) exit 92 ;;
    https://*) url="$argument" ;;
    *) exit 93 ;;
  esac
done

[[ "$disable_count" == 1 ]] || exit 88
[[ "$proto" == =https && "$max_redirs" == 0 && "$has_tls" == 1 ]] || exit 94
[[ -n "$output_file" && -n "$header_output" && -n "$cookie_jar" && -n "$cookie_input" && -n "$url" ]] || exit 95
[[ "$cookie_jar" == "$cookie_input" ]] || exit 96
[[ "$(stat -c '%a' "$(dirname -- "$output_file")")" == 700 ]] || exit 97
for file in "$output_file" "$header_output" "$cookie_jar"; do
  [[ "$(stat -c '%a' "$file")" == 600 ]] || exit 97
done
if [[ -n "$request_header" ]]; then
  [[ "$request_header" == @* ]] || exit 98
  request_header_file="${request_header#@}"
  [[ "$(stat -c '%a' "$request_header_file")" == 600 ]] || exit 97
fi

if [[ "$has_stdin_payload" == 1 ]]; then
  payload="$(cat)"
  [[ "$payload" == *'"email":"operator@example.test"'* && "$payload" == *'"password":"test-password-only"'* ]] || exit 99
fi

call_count=0
[[ ! -f "$FAKE_CURL_COUNT" ]] || call_count="$(<"$FAKE_CURL_COUNT")"
((call_count += 1))
printf '%s' "$call_count" > "$FAKE_CURL_COUNT"
path="${url#https://ops017-test.duckdns.org}"
printf '%s %s\n' "$method" "$path" >> "$FAKE_CURL_CALLS"
: > "$header_output"

write_session_cookie() {
  local value="$1"
  local domain="${2:-#HttpOnly_ops017-test.duckdns.org}"
  local secure="${3:-TRUE}"
  printf '# Netscape HTTP Cookie File\n%s\tFALSE\t/\t%s\t0\tJSESSIONID\t%s\n' \
    "$domain" "$secure" "$value" > "$cookie_jar"
}

status=200
body='{}'
case "$call_count:$method:$path" in
  1:GET:/products|2:GET:/login|3:GET:/api/products) body='{}' ;;
  4:GET:/api/auth/me)
    status=401
    if [[ "$FAKE_CURL_SCENARIO" == auth-code-missing ]]; then
      body='{"code":"ACCESS_DENIED","message":"test-only","fieldErrors":[]}'
    else
      body='{"code":"AUTH_REQUIRED","message":"test-only","fieldErrors":[]}'
    fi
    ;;
  5:GET:/api/auth/csrf)
    write_session_cookie 'session-before-test-only'
    if [[ "$FAKE_CURL_SCENARIO" == csrf-missing ]]; then
      body='{}'
    else
      body='{"token":"token-before-test-only"}'
    fi
    ;;
  6:POST:/api/auth/login)
    [[ "$request_header" == @* ]] || exit 98
    grep -Fq 'X-CSRF-TOKEN: token-before-test-only' "${request_header#@}" || exit 98
    if [[ "$FAKE_CURL_SCENARIO" == session-not-rotated ]]; then
      write_session_cookie 'session-before-test-only'
    elif [[ "$FAKE_CURL_SCENARIO" == cookie-not-secure ]]; then
      write_session_cookie 'session-after-test-only' '#HttpOnly_ops017-test.duckdns.org' 'FALSE'
    elif [[ "$FAKE_CURL_SCENARIO" == cookie-not-http-only ]]; then
      write_session_cookie 'session-after-test-only' 'ops017-test.duckdns.org' 'TRUE'
    else
      write_session_cookie 'session-after-test-only'
    fi
    body='{"memberId":771234}'
    ;;
  7:GET:/api/auth/csrf)
    if [[ "$FAKE_CURL_SCENARIO" == csrf-not-rotated ]]; then
      body='{"token":"token-before-test-only"}'
    else
      body='{"token":"token-after-test-only"}'
    fi
    ;;
  8:GET:/api/auth/me)
    if [[ "$FAKE_CURL_SCENARIO" == member-mismatch ]]; then
      body='{"memberId":882345,"role":"USER"}'
    else
      body='{"memberId":771234,"role":"USER"}'
    fi
    ;;
  9:POST:/api/auth/logout)
    [[ "$request_header" == @* ]] || exit 98
    grep -Fq 'X-CSRF-TOKEN: token-after-test-only' "${request_header#@}" || exit 98
    if [[ "$FAKE_CURL_SCENARIO" == logout-failure ]]; then
      status=500
      body='{"code":"INTERNAL_ERROR","message":"test-only","fieldErrors":[]}'
    else
      status=204
      body=''
    fi
    ;;
  10:GET:/api/auth/me)
    if [[ "$FAKE_CURL_SCENARIO" == authenticated-after-logout ]]; then
      status=200
      body='{"memberId":771234,"role":"USER"}'
    else
      status=401
      body='{"code":"AUTH_REQUIRED","message":"test-only","fieldErrors":[]}'
    fi
    ;;
  *) exit 90 ;;
esac

if [[ "$FAKE_CURL_SCENARIO" == mid-request-failure && "$call_count" == 3 ]]; then
  exit 7
fi
printf '%s' "$body" > "$output_file"
printf '%s' "$status"
EOF
chmod +x "$TEST_ROOT/bin/curl"

assert_clean_runtime() {
  local runtime_root="$1"
  [[ -z "$(find "$runtime_root" -mindepth 1 -print -quit)" ]] \
    || { printf 'sensitive runtime files were not cleaned\n' >&2; exit 1; }
}

assert_no_sensitive_output() {
  local output_file="$1"
  local error_file="$2"
  local calls_file="$3"
  if grep -Eq 'operator@example\.test|test-password-only|token-(before|after)-test-only|session-(before|after)-test-only|771234|882345' \
    "$output_file" "$error_file" "$calls_file"; then
    printf 'sensitive test value was written to output or logs\n' >&2
    exit 1
  fi
}

run_case() {
  local scenario="$1"
  local expected_status="$2"
  local expected_message="$3"
  local expected_calls="$4"
  local case_root="$TEST_ROOT/$scenario"
  local runtime_root="$case_root/runtime"
  local output_file="$case_root/stdout"
  local error_file="$case_root/stderr"
  local calls_file="$case_root/calls"
  local count_file="$case_root/count"
  local home_root="$case_root/home"
  local terminal_command
  local status
  mkdir -p "$runtime_root" "$home_root"
  : > "$calls_file"
  printf '%s\n' 'insecure' 'trace-ascii = test-only-forbidden-trace' > "$home_root/.curlrc"
  printf -v terminal_command \
    'env PATH=%q HOME=%q TMPDIR=%q FAKE_CURL_SCENARIO=%q FAKE_CURL_CALLS=%q FAKE_CURL_COUNT=%q bash %q %q >%q 2>%q' \
    "$TEST_ROOT/bin:$PATH" \
    "$home_root" \
    "$runtime_root" \
    "$scenario" \
    "$calls_file" \
    "$count_file" \
    "$SMOKE_UNDER_TEST" \
    'https://ops017-test.duckdns.org' \
    "$output_file" \
    "$error_file"

  set +e
  printf '%s\n%s\n' 'operator@example.test' 'test-password-only' \
    | script -qefc "$terminal_command" /dev/null >/dev/null 2>&1
  status=$?
  set -e

  [[ "$status" == "$expected_status" ]] \
    || { printf 'unexpected status for %s: %s\n' "$scenario" "$status" >&2; exit 1; }
  if [[ -n "$expected_message" ]]; then
    grep -Fq "$expected_message" "$error_file" \
      || { printf 'expected error was missing for %s\n' "$scenario" >&2; exit 1; }
  fi
  [[ "$(<"$count_file")" == "$expected_calls" ]] \
    || { printf 'unexpected curl call count for %s\n' "$scenario" >&2; exit 1; }
  assert_clean_runtime "$runtime_root"
  assert_no_sensitive_output "$output_file" "$error_file" "$calls_file"
}

run_invalid_url_case() {
  local name="$1"
  local url="$2"
  local expected_message="${3:-approved lowercase single-label DuckDNS HTTPS origin}"
  local case_root="$TEST_ROOT/$name"
  local runtime_root="$case_root/runtime"
  local output_file="$case_root/stdout"
  local error_file="$case_root/stderr"
  local calls_file="$case_root/calls"
  local count_file="$case_root/count"
  local status
  mkdir -p "$runtime_root"
  : > "$calls_file"

  set +e
  env \
    PATH="$TEST_ROOT/bin:$PATH" \
    TMPDIR="$runtime_root" \
    FAKE_CURL_SCENARIO=success \
    FAKE_CURL_CALLS="$calls_file" \
    FAKE_CURL_COUNT="$count_file" \
    bash "$SMOKE_UNDER_TEST" "$url" > "$output_file" 2> "$error_file"
  status=$?
  set -e

  [[ "$status" == 1 ]] || { printf 'invalid URL was accepted: %s\n' "$name" >&2; exit 1; }
  grep -Fq "$expected_message" "$error_file" || exit 1
  [[ ! -s "$calls_file" && ! -f "$count_file" ]] || { printf 'invalid URL reached curl: %s\n' "$name" >&2; exit 1; }
  assert_clean_runtime "$runtime_root"
}

run_non_tty_case() {
  local case_root="$TEST_ROOT/non-tty"
  local runtime_root="$case_root/runtime"
  local output_file="$case_root/stdout"
  local error_file="$case_root/stderr"
  local calls_file="$case_root/calls"
  local count_file="$case_root/count"
  local status
  mkdir -p "$runtime_root"
  : > "$calls_file"

  set +e
  env \
    PATH="$TEST_ROOT/bin:$PATH" \
    TMPDIR="$runtime_root" \
    FAKE_CURL_SCENARIO=success \
    FAKE_CURL_CALLS="$calls_file" \
    FAKE_CURL_COUNT="$count_file" \
    setsid bash "$SMOKE_UNDER_TEST" 'https://ops017-test.duckdns.org' \
      < /dev/null > "$output_file" 2> "$error_file"
  status=$?
  set -e

  [[ "$status" == 1 ]] || { printf 'non-TTY credential input was accepted\n' >&2; exit 1; }
  grep -Fq 'an interactive terminal is required for credentials' "$error_file" || exit 1
  [[ ! -s "$calls_file" && ! -f "$count_file" ]] || { printf 'non-TTY input reached curl\n' >&2; exit 1; }
  assert_clean_runtime "$runtime_root"
}

run_case success 0 '' 10
[[ "$(grep -c '^PASS ' "$TEST_ROOT/success/stdout")" == 5 ]] || { printf 'success PASS output is incomplete\n' >&2; exit 1; }
[[ -z "$(grep -Ev '^PASS ' "$TEST_ROOT/success/stdout")" ]] || { printf 'success output contains non-PASS data\n' >&2; exit 1; }

run_invalid_url_case non-https 'http://ops017-test.duckdns.org'
run_invalid_url_case unapproved-host 'https://ops017-test.example.com'
run_invalid_url_case multi-label 'https://ops017.test.duckdns.org'
run_invalid_url_case uppercase-host 'https://OPS017-test.duckdns.org'
run_invalid_url_case \
  different-duckdns-host \
  'https://other-ops017-test.duckdns.org' \
  'URL does not match the approved production HTTPS domain state'
run_non_tty_case
run_case csrf-missing 1 'initial CSRF token is missing' 5
run_case auth-code-missing 1 'anonymous current member did not return AUTH_REQUIRED' 4
run_case csrf-not-rotated 1 'CSRF token did not rotate after login' 7
run_case session-not-rotated 1 'session ID did not rotate after login' 6
run_case cookie-not-secure 1 'session cookie is not Secure and HttpOnly' 6
run_case cookie-not-http-only 1 'session cookie is not Secure and HttpOnly' 6
run_case member-mismatch 1 'login and current member identities do not match' 8
run_case logout-failure 1 'unexpected HTTP status at session logout' 9
run_case authenticated-after-logout 1 'unexpected HTTP status at stale session rejection' 10
run_case mid-request-failure 1 'HTTPS request failed at public products API' 3

grep -Fq 'APPROVED_DOMAIN_FILE="/opt/pawcycle/state/https-domain"' "$SMOKE_SCRIPT"
grep -Fq 'URL does not match the approved production HTTPS domain state' "$SMOKE_SCRIPT"
grep -Fq 'exec 3<>/dev/tty' "$SMOKE_SCRIPT"
grep -Fq '[[ -t 3 ]]' "$SMOKE_SCRIPT"
grep -Fq -- '--disable' "$SMOKE_SCRIPT"
grep -Fq -- "--proto '=https'" "$SMOKE_SCRIPT"
grep -Fq -- '--max-redirs 0' "$SMOKE_SCRIPT"
grep -Fq -- '--header "@$header_file"' "$SMOKE_SCRIPT"
grep -Fq -- '--data-binary @-' "$SMOKE_SCRIPT"
grep -Fq 'trap cleanup EXIT' "$SMOKE_SCRIPT"
grep -Fq "trap 'exit 130' INT" "$SMOKE_SCRIPT"
grep -Fq "trap 'exit 143' TERM" "$SMOKE_SCRIPT"
if grep -Eq '^[[:space:]]+(-k|--insecure|-L|--location)([[:space:]]|$)|set -x' "$SMOKE_SCRIPT"; then
  printf 'forbidden curl or shell tracing option is present\n' >&2
  exit 1
fi
printf '%s\n' 'OPS-017 production auth session smoke fake HTTP contract tests passed'
