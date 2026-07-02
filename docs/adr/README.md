# 아키텍처 결정 기록(Architecture Decision Records)

이 디렉터리는 장기적으로 의미 있는 기술 결정을 ADR(Architecture Decision Record)로 보관한다.

ADR은 아키텍처(Architecture), 의존성(Dependency), 계약(Contract), 영속성 전략(Persistence Strategy), 보안 모델(Security Model), 배포 모델(Deployment Model), 장기 유지보수성에 영향을 주는 결정에 사용한다.

한 작업 내부에만 머무르고 쉽게 바꿀 수 있는 작은 구현 세부 사항에는 ADR을 만들지 않는다.

## 이름 규칙

다음 형식을 사용한다.

```text
ADR-0001-short-title.md
```

새 ADR은 `docs/adr/adr-template.md`를 기준으로 작성한다.
