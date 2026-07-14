export function LoadingState({ children }: { children: React.ReactNode }) {
  return (
    <div className="state-panel" role="status" aria-live="polite">
      <span className="loading-dot" aria-hidden="true" />
      {children}
    </div>
  );
}

interface ErrorStateProps {
  title: string;
  message: string;
  retryLabel?: string;
  onRetry?: () => void;
  children?: React.ReactNode;
}

export function ErrorState({
  title,
  message,
  retryLabel = "다시 시도",
  onRetry,
  children,
}: ErrorStateProps) {
  return (
    <section className="state-panel state-panel-error" aria-labelledby="error-state-title">
      <p className="eyebrow">확인할 수 없음</p>
      <h1 id="error-state-title">{title}</h1>
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
