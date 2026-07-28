#!/usr/bin/env bash
set -Eeuo pipefail
set +x
APPROVED_DOMAIN_FILE="/opt/pawcycle/state/https-domain"

usage() {
  cat <<'EOF'
Usage: verify-production-auth-session-smoke.sh https://<single-label>.duckdns.org

The operator email and password are accepted only through interactive prompts.
EOF
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command is unavailable: $1"
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  set +e
  OPERATOR_EMAIL=""
  OPERATOR_PASSWORD=""
  ESCAPED_EMAIL=""
  ESCAPED_PASSWORD=""
  CSRF_TOKEN_BEFORE=""
  CSRF_TOKEN_AFTER=""
  SESSION_ID_BEFORE=""
  SESSION_ID_AFTER=""
  LOGIN_MEMBER_ID=""
  CURRENT_MEMBER_ID=""
  APPROVED_DOMAIN=""
  unset OPERATOR_EMAIL OPERATOR_PASSWORD ESCAPED_EMAIL ESCAPED_PASSWORD
  unset CSRF_TOKEN_BEFORE CSRF_TOKEN_AFTER SESSION_ID_BEFORE SESSION_ID_AFTER
  unset LOGIN_MEMBER_ID CURRENT_MEMBER_ID
  unset APPROVED_DOMAIN
  if [[ -n "${WORK_DIR:-}" && -d "$WORK_DIR" ]]; then
    rm -rf -- "$WORK_DIR"
  fi
  exit "$status"
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\t'/\\t}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\b'/\\b}"
  value="${value//$'\f'/\\f}"
  printf '%s' "$value"
}

extract_csrf_token() {
  sed -n 's/^[[:space:]]*{[[:space:]]*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)"[[:space:]]*}[[:space:]]*$/\1/p' "$1"
}

extract_member_id() {
  sed -n 's/^[[:space:]]*{[[:space:]]*"memberId"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\)[[:space:]]*}[[:space:]]*$/\1/p' "$1"
}

extract_session_id() {
  awk -F '\t' '$6 == "JSESSIONID" { value = $7; count += 1 } END { if (count == 1) print value }' "$1"
}

assert_session_cookie_attributes() {
  awk -F '\t' '
    $6 == "JSESSIONID" {
      count += 1
      if ($1 ~ /^#HttpOnly_/ && $4 == "TRUE") valid += 1
    }
    END { exit !(count == 1 && valid == 1) }
  ' "$1" || die "session cookie is not Secure and HttpOnly"
}

assert_auth_required() {
  grep -Eq '"code"[[:space:]]*:[[:space:]]*"AUTH_REQUIRED"' "$1" \
    || die "$2 did not return AUTH_REQUIRED"
}

perform_request() {
  local step="$1"
  local method="$2"
  local path="$3"
  local cookie_file="$4"
  local header_file="${5:-}"
  local payload_mode="${6:-none}"
  local -a curl_arguments=(
    --disable
    --silent
    --show-error
    --request "$method"
    --proto '=https'
    --tlsv1.2
    --max-redirs 0
    --connect-timeout 10
    --max-time 30
    --cookie "$cookie_file"
    --cookie-jar "$cookie_file"
    --dump-header "$RESPONSE_HEADERS"
    --output "$RESPONSE_BODY"
    --write-out '%{http_code}'
  )

  : > "$RESPONSE_HEADERS"
  : > "$RESPONSE_BODY"
  : > "$CURL_ERROR"
  if [[ -n "$header_file" ]]; then
    curl_arguments+=(--header "@$header_file")
  fi

  if [[ "$payload_mode" == login ]]; then
    if ! HTTP_STATUS="$(
      printf '{"email":"%s","password":"%s"}' "$ESCAPED_EMAIL" "$ESCAPED_PASSWORD" \
        | curl "${curl_arguments[@]}" --data-binary @- "$BASE_URL$path" 2>"$CURL_ERROR"
    )"; then
      die "HTTPS request failed at $step"
    fi
  elif ! HTTP_STATUS="$(curl "${curl_arguments[@]}" "$BASE_URL$path" 2>"$CURL_ERROR")"; then
    die "HTTPS request failed at $step"
  fi

  [[ "$HTTP_STATUS" =~ ^[0-9]{3}$ ]] || die "invalid HTTP status at $step"
}

expect_status() {
  local expected="$1"
  local step="$2"
  [[ "$HTTP_STATUS" == "$expected" ]] || die "unexpected HTTP status at $step"
}

[[ $# == 1 ]] || { usage >&2; exit 2; }
BASE_URL="${1%/}"
[[ "$BASE_URL" =~ ^https://([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)\.duckdns\.org$ ]] \
  || die "URL must be an approved lowercase single-label DuckDNS HTTPS origin"

for required_command in curl mktemp chmod rm sed awk grep cp stat; do
  require_command "$required_command"
done

[[ -e "$APPROVED_DOMAIN_FILE" || -L "$APPROVED_DOMAIN_FILE" ]] \
  || die "approved HTTPS domain state is missing"
[[ ! -L "$APPROVED_DOMAIN_FILE" && -f "$APPROVED_DOMAIN_FILE" ]] \
  || die "approved HTTPS domain state must be a regular non-symlink file"
[[ "$(stat -c '%a' "$APPROVED_DOMAIN_FILE")" == 600 ]] \
  || die "approved HTTPS domain state mode must be 600"
APPROVED_DOMAIN="$(<"$APPROVED_DOMAIN_FILE")"
[[ "$APPROVED_DOMAIN" =~ ^([a-z0-9]|[a-z0-9][a-z0-9-]{0,61}[a-z0-9])\.duckdns\.org$ ]] \
  || die "approved HTTPS domain state is invalid"
[[ "$BASE_URL" == "https://$APPROVED_DOMAIN" ]] \
  || die "URL does not match the approved production HTTPS domain state"

if ! { exec 3<>/dev/tty; } 2>/dev/null; then
  die "an interactive terminal is required for credentials"
fi
[[ -t 3 ]] || { exec 3>&-; die "an interactive terminal is required for credentials"; }

umask 077
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pawcycle-ops017.XXXXXX")"
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
chmod 700 "$WORK_DIR"
COOKIE_JAR="$WORK_DIR/cookies"
STALE_COOKIE_JAR="$WORK_DIR/stale-cookies"
RESPONSE_HEADERS="$WORK_DIR/response-headers"
RESPONSE_BODY="$WORK_DIR/response-body"
CURL_ERROR="$WORK_DIR/curl-error"
CSRF_HEADER="$WORK_DIR/csrf-header"
for sensitive_file in \
  "$COOKIE_JAR" \
  "$STALE_COOKIE_JAR" \
  "$RESPONSE_HEADERS" \
  "$RESPONSE_BODY" \
  "$CURL_ERROR" \
  "$CSRF_HEADER"; do
  : > "$sensitive_file"
  chmod 600 "$sensitive_file"
done
printf 'Operator email: ' >&3
IFS= read -r -u 3 OPERATOR_EMAIL || die "operator email input was not completed"
printf 'Operator password: ' >&3
IFS= read -r -s -u 3 OPERATOR_PASSWORD || die "operator password input was not completed"
printf '\n' >&3
exec 3>&-
[[ "$OPERATOR_EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}$ ]] \
  || die "operator email format is invalid"
[[ -n "$OPERATOR_PASSWORD" ]] || die "operator password must not be empty"
ESCAPED_EMAIL="$(json_escape "$OPERATOR_EMAIL")"
ESCAPED_PASSWORD="$(json_escape "$OPERATOR_PASSWORD")"

perform_request "public products page" GET "/products" "$COOKIE_JAR"
expect_status 200 "public products page"
perform_request "login page" GET "/login" "$COOKIE_JAR"
expect_status 200 "login page"
perform_request "public products API" GET "/api/products" "$COOKIE_JAR"
expect_status 200 "public products API"
printf '%s\n' "PASS public HTTPS paths"

perform_request "anonymous current member" GET "/api/auth/me" "$COOKIE_JAR"
expect_status 401 "anonymous current member"
assert_auth_required "$RESPONSE_BODY" "anonymous current member"
printf '%s\n' "PASS anonymous session rejection"

perform_request "initial CSRF token" GET "/api/auth/csrf" "$COOKIE_JAR"
expect_status 200 "initial CSRF token"
CSRF_TOKEN_BEFORE="$(extract_csrf_token "$RESPONSE_BODY")"
SESSION_ID_BEFORE="$(extract_session_id "$COOKIE_JAR")"
[[ -n "$CSRF_TOKEN_BEFORE" ]] || die "initial CSRF token is missing"
[[ -n "$SESSION_ID_BEFORE" ]] || die "initial session cookie is missing"
printf 'Content-Type: application/json\nX-CSRF-TOKEN: %s\n' "$CSRF_TOKEN_BEFORE" > "$CSRF_HEADER"

perform_request "session login" POST "/api/auth/login" "$COOKIE_JAR" "$CSRF_HEADER" login
expect_status 200 "session login"
LOGIN_MEMBER_ID="$(extract_member_id "$RESPONSE_BODY")"
SESSION_ID_AFTER="$(extract_session_id "$COOKIE_JAR")"
[[ -n "$LOGIN_MEMBER_ID" ]] || die "login member identity is missing"
[[ -n "$SESSION_ID_AFTER" ]] || die "authenticated session cookie is missing"
assert_session_cookie_attributes "$COOKIE_JAR"
[[ "$SESSION_ID_BEFORE" != "$SESSION_ID_AFTER" ]] || die "session ID did not rotate after login"

perform_request "rotated CSRF token" GET "/api/auth/csrf" "$COOKIE_JAR"
expect_status 200 "rotated CSRF token"
CSRF_TOKEN_AFTER="$(extract_csrf_token "$RESPONSE_BODY")"
[[ -n "$CSRF_TOKEN_AFTER" ]] || die "authenticated CSRF token is missing"
[[ "$CSRF_TOKEN_BEFORE" != "$CSRF_TOKEN_AFTER" ]] || die "CSRF token did not rotate after login"
[[ "$(extract_session_id "$COOKIE_JAR")" == "$SESSION_ID_AFTER" ]] \
  || die "authenticated session changed unexpectedly"
printf '%s\n' "PASS login session and CSRF rotation"

perform_request "authenticated current member" GET "/api/auth/me" "$COOKIE_JAR"
expect_status 200 "authenticated current member"
CURRENT_MEMBER_ID="$(extract_member_id "$RESPONSE_BODY")"
[[ -n "$CURRENT_MEMBER_ID" && "$CURRENT_MEMBER_ID" == "$LOGIN_MEMBER_ID" ]] \
  || die "login and current member identities do not match"
printf '%s\n' "PASS authenticated member identity"

cp "$COOKIE_JAR" "$STALE_COOKIE_JAR"
chmod 600 "$STALE_COOKIE_JAR"
printf 'X-CSRF-TOKEN: %s\n' "$CSRF_TOKEN_AFTER" > "$CSRF_HEADER"
perform_request "session logout" POST "/api/auth/logout" "$COOKIE_JAR" "$CSRF_HEADER"
expect_status 204 "session logout"

perform_request "stale session rejection" GET "/api/auth/me" "$STALE_COOKIE_JAR"
expect_status 401 "stale session rejection"
assert_auth_required "$RESPONSE_BODY" "stale session"
printf '%s\n' "PASS logout and stale session rejection"
