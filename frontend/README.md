# PawCycle Commerce Frontend

Next.js + TypeScript 기반 Frontend 최소 애플리케이션이다.

## 검증

```bash
npm ci
npm test
npm run typecheck
npm run lint
npm run build
```

## 현재 범위

App Router에서 공개 상품 목록·상세, 세션 로그인, 구독 생성, 내 구독 목록·상세를 제공한다.
API는 same-origin `/api/**` 상대 경로로 호출하며 인증 상태와 CSRF token은 브라우저 메모리에만 보관한다.

별도 UI·상태 관리 라이브러리는 사용하지 않는다.

자세한 로컬 개발 절차는 `../docs/runbook/FOUNDATION-001-local-development.md`를 따른다.
