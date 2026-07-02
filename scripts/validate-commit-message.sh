#!/usr/bin/env sh
set -eu

MAX_TITLE_LENGTH=72
ALLOWED_TYPES="feat fix docs style refactor test build ci chore perf revert"
EXAMPLE="docs: 프로젝트 문서 정리"
DISALLOWED_PREDICATE_ENDINGS='(한다|했다|합니다|하였다|됩니다|추가한다|수정한다|작성한다|구현한다|정리한다|적용한다|연동한다)$'

usage() {
  cat <<'USAGE'
Usage:
  scripts/validate-commit-message.sh <commit-message-file>
  scripts/validate-commit-message.sh --message "<title>"

The same title convention is used for commits and pull requests.
USAGE
}

fail() {
  printf '%s\n' "커밋 메시지 검증 실패: $1" >&2
  printf '%s\n' "올바른 예: $EXAMPLE" >&2
  exit 1
}

has_hangul() {
  COMMIT_DESCRIPTION=$description
  export COMMIT_DESCRIPTION

  for python_bin in python3 python; do
    if command -v "$python_bin" >/dev/null 2>&1 && "$python_bin" -c 'import sys; sys.exit(0)' >/dev/null 2>&1; then
      "$python_bin" -c 'import os, sys; text = os.environ.get("COMMIT_DESCRIPTION", ""); sys.exit(0 if any("\u3131" <= ch <= "\u318e" or "\uac00" <= ch <= "\ud7a3" for ch in text) else 1)'
      return $?
    fi
  done

  printf '%s' "$description" | LC_ALL=C grep -Eq '[^ -~]'
}

message_from_args() {
  if [ "$#" -eq 2 ] && [ "$1" = "--message" ]; then
    printf '%s' "$2"
    return 0
  fi

  if [ "$#" -ne 1 ]; then
    usage >&2
    exit 2
  fi

  if [ ! -f "$1" ]; then
    fail "커밋 메시지 파일을 찾을 수 없음: $1"
  fi

  sed -n '1p' "$1" | tr -d '\r'
}

title=$(message_from_args "$@")

[ -n "$title" ] || fail "제목이 비어 있음"

case "$title" in
  "Merge "*) exit 0 ;;
  "Revert \""*) exit 0 ;;
esac

case "$title" in
  *.) fail "제목 끝에는 마침표를 쓰지 않음" ;;
esac

title_length=$(printf '%s' "$title" | wc -m | tr -d ' ')
if [ "$title_length" -gt "$MAX_TITLE_LENGTH" ]; then
  fail "제목은 ${MAX_TITLE_LENGTH}자 이내여야 함: 현재 ${title_length}자"
fi

if ! printf '%s' "$title" | grep -Eq '^[a-z]+(\([a-z0-9][a-z0-9-]*\))?: .+'; then
  fail "형식은 <type>(<scope>): <한국어 명사형 설명> 또는 <type>: <한국어 명사형 설명>이어야 함"
fi

type=$(printf '%s' "$title" | sed -E 's/^([a-z]+)(\([a-z0-9][a-z0-9-]*\))?: .+$/\1/')
description=$(printf '%s' "$title" | sed -E 's/^[a-z]+(\([a-z0-9][a-z0-9-]*\))?: //')

type_allowed=false
for allowed in $ALLOWED_TYPES; do
  if [ "$type" = "$allowed" ]; then
    type_allowed=true
    break
  fi
done

[ "$type_allowed" = true ] || fail "허용되지 않은 type: $type"
[ -n "$description" ] || fail "설명이 비어 있음"

if ! has_hangul; then
  fail "설명에는 한글이 최소 한 글자 이상 포함되어야 함"
fi

if printf '%s' "$description" | grep -Eq "$DISALLOWED_PREDICATE_ENDINGS"; then
  fail "설명은 '~한다', '~했다', '~합니다' 같은 서술형 종결이 아니라 명사형으로 끝나야 함"
fi

exit 0
