# 인수인계(Handoffs)

이 디렉터리는 대화 이후에도 남아야 하는 역할 간 인수인계를 보관한다.

경로 형식은 다음과 같다.

```text
docs/handoffs/<작업 ID>/<source-role>-to-<target-role>.md
```

예시는 다음과 같다.

- `docs/handoffs/PS-001/product-to-design.md`
- `docs/handoffs/PS-001/backend-to-frontend.md`
- `docs/handoffs/PS-001/frontend-to-qa.md`

다른 역할에 실제로 전달할 정보가 있을 때만 인수인계를 만든다. 모든 작업마다 모든 조합의 인수인계를 만들지 않는다.

새 인수인계는 `docs/handoffs/handoff-template.md`를 기준으로 작성한다.
