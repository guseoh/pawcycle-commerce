# QA 문서(QA Documents)

이 디렉터리는 검증 계획, 테스트 케이스, 버그 리포트, 재검증 결과를 보관한다.

사용 범위는 다음과 같다.

- 테스트 계획(Test Plan)
- 인수 조건(Acceptance Criteria) 매핑
- 수동 테스트 케이스(Manual Test Case)
- 자동화 테스트(Automated Test) 메모
- 버그 리포트(Bug Report)
- 재검증 기록(Retest Record)
- 회귀 위험(Regression Risk) 메모

QA는 결함을 숨기거나 직접 고치기 위해 제품 코드를 변경하지 않는다.

## 최소 테스트 계획 구조

```markdown
# [작업 ID] 테스트 계획

## 승인된 입력

## 인수 조건 매핑

## 정상 흐름 테스트

## 예외 흐름 테스트

## 경계값 테스트

## 권한 테스트

## 중복 요청 테스트

## 상태 전이 테스트

## 회귀 위험

## 차단 사유
```
