# PawCycle 사용 규칙

이 폴더의 `SKILL.md`, `data/**`, `scripts/**`는 upstream
`nextlevelbuilder/ui-ux-pro-max-skill`의 Codex 산출물을 그대로 둔 보조 Skill이다.
현재 기준 upstream commit은 `c87cdc226f85b28040c6f15c1d29b5684fd32121`이다.

## 권위와 범위

- 이 Skill은 기존 `ux-designer` Skill을 대체하지 않는다.
- PawCycle의 사용자 승인 Design Contract, 제품 요구사항, API 계약, `AGENTS.md`, 역할 Skill이 항상 우선한다.
- 이 Skill의 검색 결과와 제안은 디자인 검토를 돕는 참고 자료이며 승인이나 제품 결정을 대신하지 않는다.
- 충돌 시 승인된 PawCycle 문서와 사용자의 현재 지시를 따른다.

## 실행 경계

- 제품 코드, API/domain 계약, 인프라, dependency를 이 Skill만으로 추가·변경하지 않는다.
- bundled script는 로컬 데이터 검색 보조로만 사용한다. 외부 provider, credential, 네트워크 호출은 사용하지 않는다.
- `--persist` 등으로 프로젝트에 디자인 시스템 파일을 생성하기 전에는 별도 사용자 승인이 필요하다.
- 시각적·브라우저 검토가 필요한 항목은 실제 GUI 관찰 없이 PASS로 선언하지 않는다.
