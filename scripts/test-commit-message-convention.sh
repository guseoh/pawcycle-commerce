#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
validator="$script_dir/validate-commit-message.sh"

valid_messages='
docs: 프로젝트 문서 정리
docs(harness): 역할별 보고 체계 추가
docs(harness): 역할별 작업 흐름 추가
ci(commit): 명사형 제목 검증 추가
ci(discord): 협업 알림 워크플로 구성
fix(obsidian): PR 기록 감지 오류 수정
docs(repository): 초기 저장소 문서 구성
'

invalid_messages='
docs: 프로젝트 문서를 정리한다
docs(harness): 작업 흐름을 추가한다
ci(commit): 검증을 추가합니다
ci(discord): 알림을 추가합니다
fix(obsidian): 오류를 수정했다
feat: 기능을 구현하였다
docs: 문서를 작성했다
'

printf '%s\n' "$valid_messages" | while IFS= read -r message; do
  [ -n "$message" ] || continue
  sh "$validator" --message "$message"
done

printf '%s\n' "$invalid_messages" | while IFS= read -r message; do
  [ -n "$message" ] || continue
  if sh "$validator" --message "$message" >/dev/null 2>&1; then
    printf '%s\n' "expected invalid message to fail: $message" >&2
    exit 1
  fi
done

printf '%s\n' "commit message convention tests passed"
