import { useId } from "react";

export function CatalogSkeleton() {
  return <div role="status" aria-live="polite"><span className="sr-only">상품을 불러오고 있습니다.</span><div className="catalog-products-grid" aria-hidden="true">{Array.from({ length: 8 }, (_, index) => <div className="catalog-skeleton" key={index}><span /><i /><i /><i /></div>)}</div></div>;
}

export function LoadingState({ children }: { children: React.ReactNode }) {
  return (
    <div className="state-panel" role="status" aria-live="polite">
      <span className="loading-dot" aria-hidden="true" />
      {children}
    </div>
  );
}

interface ErrorStateProps {
  headingLevel?: 1 | 2 | 3;
  title: string;
  message: string;
  retryLabel?: string;
  onRetry?: () => void;
  children?: React.ReactNode;
}

export function ErrorState({
  headingLevel = 1,
  title,
  message,
  retryLabel = "다시 시도",
  onRetry,
  children,
}: ErrorStateProps) {
  const titleId = useId();
  const Heading = headingLevel === 3 ? "h3" : headingLevel === 2 ? "h2" : "h1";
  return (
    <section
      className="state-panel state-panel-error"
      role="alert"
      aria-atomic="true"
      aria-labelledby={titleId}
    >
      <p className="eyebrow">확인할 수 없음</p>
      <Heading id={titleId}>{title}</Heading>
      <p>{message}</p>
      <div className="button-row">
        {onRetry ? (
          <button className="button button-primary" type="button" onClick={onRetry}>
            {retryLabel}
          </button>
        ) : null}
        {children}
      </div>
    </section>
  );
}
