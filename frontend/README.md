# PawCycle Commerce Frontend

Next.js + TypeScript 기반 Frontend 최소 애플리케이션이다.

## 검증

```bash
npm ci
npm run lint
npm run build
```

## 현재 범위

현재는 App Router 기반 최소 scaffold 상태다.

아직 다음 항목은 구현하지 않았다.

- 상품 목록 화면
- 상품 상세 화면
- 구독 생성 화면
- 마이페이지
- API client
- 인증 UI
- UI 라이브러리
- 상태관리 라이브러리

루트 페이지는 `src/app/page.tsx`에 있다.

자세한 로컬 개발 절차는 `../docs/runbook/FOUNDATION-001-local-development.md`를 따른다.
