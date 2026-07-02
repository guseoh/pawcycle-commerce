# 성능 문서(Performance Documents)

이 디렉터리는 성능 실험, 측정 메모, 개선 보고서를 보관한다.

측정 가능한 성능 질문이 있을 때만 사용한다. 추측 기반 최적화(Optimization)를 정당화하는 용도로 사용하지 않는다.

성능 작업은 다음 순서를 따른다.

1. 기준 성능(Baseline) 측정
2. 병목 증거(Bottleneck Evidence) 수집
3. 가설(Hypothesis) 작성
4. 승인된 변경 또는 인수인계
5. 동일 조건에서 재측정
6. 개선 전후 보고

새 실험은 `docs/performance/experiment-template.md`를 기준으로 작성한다.
