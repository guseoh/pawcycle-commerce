"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from "react";
import { ApiError, type FieldError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref } from "@/lib/frontend-utils";
import { CsrfRefreshError } from "@/lib/csrf-lifecycle";
import { dirtyFormPayload, formValues, parseForm, type CatalogField } from "@/lib/admin-catalog-forms";

export function AdminNavigation() {
  const path = usePathname();
  return <nav className="admin-navigation" aria-label="관리자 메뉴">
    <Link href="/admin/catalog" aria-current={path.startsWith("/admin/catalog") ? "page" : undefined}>Catalog 관리</Link>
    <Link href="/admin/operations" aria-current={path.startsWith("/admin/operations") ? "page" : undefined}>운영 작업</Link>
  </nav>;
}

export function AdminGate({ children }: { children: ReactNode }) {
  const auth = useAuth(); const router = useRouter(); const path = usePathname();
  useEffect(() => { if (auth.status === "anonymous") router.replace(buildLoginHref(path)); }, [auth.status, path, router]);
  if (auth.status === "loading" || auth.status === "anonymous") return <p role="status">로그인 상태를 확인하고 있습니다.</p>;
  if (auth.status === "error") return <div role="alert"><p>{auth.errorMessage}</p><button type="button" className="button button-secondary" onClick={() => void auth.refresh()}>로그인 상태 다시 확인</button></div>;
  return children;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "로그인이 만료되었습니다. 다시 로그인해 주세요.";
    if (error.code === "CSRF_INVALID") return "보안 토큰이 만료되었습니다. 입력을 확인하고 다시 저장해 주세요.";
    if (error.status === 403) return "ADMIN 권한이 필요합니다. 계정 권한을 확인해 주세요.";
    return `${error.message} (${error.code})`;
  }
  if (error instanceof CsrfRefreshError) return "보안 정보를 갱신하지 못했습니다. 다시 시도해 주세요.";
  return "요청을 완료하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.";
}

export function useAdminResource<T>(loader: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const generation = useRef(0);
  const { markAnonymous } = useAuth();
  const reload = useCallback(async () => {
    const current = ++generation.current;
    setLoading(true); setError(null);
    try {
      const value = await loader();
      if (current === generation.current) setData(value);
    } catch (reason) {
      if (current !== generation.current) return;
      setError(reason);
      if (reason instanceof ApiError && reason.status === 401) markAnonymous();
      throw reason;
    } finally { if (current === generation.current) setLoading(false); }
  }, [loader, markAnonymous]);
  useEffect(() => {
    const timer = window.setTimeout(() => void reload().catch(() => undefined), 0);
    return () => { window.clearTimeout(timer); generation.current += 1; };
  }, [reload]);
  return { data, loading, error, reload };
}

export function useAdminMutation() {
  const { executeWithCsrf, markAnonymous } = useAuth();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const generation = useRef(0); const locked = useRef(false);
  useEffect(() => () => { generation.current += 1; }, []);
  function reset() { setError(null); setNotice(null); setRefreshFailed(false); }
  async function run(action: (csrf: string) => Promise<unknown>, refresh?: () => Promise<void>) {
    if (locked.current || refreshFailed) return;
    const current = ++generation.current;
    locked.current = true; setPending(true); reset();
    try {
      await executeWithCsrf(action);
      if (current !== generation.current) return;
      setNotice("저장 완료. 서버에 변경을 반영했습니다.");
      if (refresh) {
        try { await refresh(); }
        catch {
          if (current === generation.current) {
            setRefreshFailed(true);
            setNotice("저장은 완료됐지만 최신 정보 조회에 실패했습니다. 다시 저장하지 말고 조회를 재시도해 주세요.");
          }
        }
      }
    } catch (reason) {
      if (current !== generation.current) return;
      setError(reason);
      if (reason instanceof ApiError && reason.status === 401) markAnonymous();
    } finally {
      if (current === generation.current) { locked.current = false; setPending(false); }
    }
  }
  return { pending, error, notice, refreshFailed, run, reset };
}

export function ResourceState({ loading, error, onRetry }: { loading: boolean; error: unknown; onRetry: () => void }) {
  return <>{loading ? <p role="status">불러오는 중…</p> : null}{error ? <div role="alert" className="admin-feedback"><p>{errorMessage(error)}</p><button type="button" className="button button-secondary" onClick={onRetry}>조회 다시 시도</button></div> : null}</>;
}

export function MutationFeedback({ mutation, retry }: { mutation: ReturnType<typeof useAdminMutation>; retry?: () => void }) {
  return <>{mutation.notice ? <div role={mutation.refreshFailed ? "alert" : "status"} className="admin-feedback"><p>{mutation.notice}</p>{mutation.refreshFailed && retry ? <button type="button" className="button button-secondary" disabled={mutation.pending} onClick={retry}>최신 정보 다시 조회</button> : null}</div> : null}</>;
}

export function CatalogForm<T extends object>({ baseline, fields, editing, pending, error, onSubmit, title }: {
  baseline: T; fields: CatalogField<T>[]; editing: boolean; pending: boolean; error: unknown;
  onSubmit: (value: T, patch: Partial<T>) => void; title: string;
}) {
  const id = useId(); const errorRef = useRef<HTMLDivElement>(null);
  const [values, setValues] = useState(() => formValues(baseline, fields));
  const [localErrors, setLocalErrors] = useState<FieldError[]>([]);
  const [unchanged, setUnchanged] = useState(false);
  const errors = [...localErrors, ...(error instanceof ApiError ? error.fieldErrors : [])];
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  function submit(event: React.FormEvent) {
    event.preventDefault();
    const parsed = parseForm(values, fields, editing);
    setLocalErrors(parsed.errors); setUnchanged(false);
    if (parsed.errors.length) { requestAnimationFrame(() => errorRef.current?.focus()); return; }
    const patch = editing ? dirtyFormPayload(baseline, values, parsed.value, fields) : {};
    if (editing && Object.keys(patch).length === 0) { setUnchanged(true); return; }
    onSubmit(parsed.value, patch);
  }
  return <form onSubmit={submit} className="admin-form" aria-label={title}>
    <h3>{title}</h3>
    {(error || errors.length > 0) ? <div ref={errorRef} tabIndex={-1} role="alert" className="error-summary"><p>{error ? errorMessage(error) : "입력 내용을 확인해 주세요."}</p>{errors.length ? <ul>{errors.map((item, index) => <li key={`${item.field}-${index}`}><a href={`#${id}-${item.field}`}>{fields.find((f) => f.key === item.field)?.label ?? item.field}: {item.message}</a></li>)}</ul> : null}</div> : null}
    <fieldset disabled={pending} className="admin-fields">
      {fields.map((field) => {
        const fieldId = `${id}-${field.key}`; const fieldErrors = errors.filter((e) => e.field === field.key);
        const disabled = Boolean(editing && field.readOnlyOnEdit);
        const common = { id: fieldId, name: field.key, className: "input", required: field.required, "aria-invalid": fieldErrors.length > 0, "aria-describedby": `${fieldId}-help ${fieldId}-error` };
        const change = (value: string | boolean) => { setValues((current) => ({ ...current, [field.key]: value })); setLocalErrors((current) => current.filter((e) => e.field !== field.key)); setUnchanged(false); };
        return <div className={`form-field ${field.kind === "textarea" ? "admin-wide" : ""}`} key={field.key}>
          <label htmlFor={fieldId}>{field.label}{field.required ? " *" : ""}{disabled ? " (생성 후 변경 불가)" : ""}</label>
          {field.kind === "checkbox" ? <input {...common} className="admin-checkbox" type="checkbox" checked={Boolean(values[field.key])} onChange={(e) => change(e.target.checked)} />
            : field.kind === "select" ? <select {...common} value={String(values[field.key] ?? "")} onChange={(e) => change(e.target.value)}>{field.choices?.map((choice) => <option key={choice.value} value={choice.value} disabled={choice.disabled}>{choice.label}</option>)}</select>
              : field.kind === "textarea" ? <textarea {...common} rows={5} maxLength={field.maxLength} value={String(values[field.key] ?? "")} onChange={(e) => change(e.target.value)} />
                : <input {...common} type="text" inputMode={field.kind === "money" ? "decimal" : field.kind === "number" ? "numeric" : undefined} maxLength={field.maxLength} readOnly={disabled} value={String(values[field.key] ?? "")} onChange={(e) => change(e.target.value)} />}
          <small id={`${fieldId}-help`} className="field-help">{field.help}{field.nullable ? " 비우면 값을 해제합니다." : ""}</small>
          <span id={`${fieldId}-error`} className="field-error">{fieldErrors.map((e) => e.message).join(" ")}</span>
        </div>;
      })}
    </fieldset>
    {unchanged ? <p role="status">변경된 필드가 없습니다.</p> : null}
    <button className="button button-primary" type="submit" disabled={pending}>{pending ? "처리 중…" : editing ? "변경 저장" : "생성"}</button>
  </form>;
}

export function ResourcePanel<Input extends object, Row extends Input>({ title, load, idOf, labelOf, initial, fields, create, patch, remove, renderSelected, linkTo }: {
  title: string; load: () => Promise<Row[]>; idOf: (row: Row) => number; labelOf: (row: Row) => string;
  initial: Input; fields: CatalogField<Input>[] | ((row: Row | null, rows: Row[]) => CatalogField<Input>[]);
  create: (input: Input, csrf: string) => Promise<unknown>; patch?: (id: number, input: Partial<Input>, csrf: string) => Promise<unknown>;
  remove?: (id: number, csrf: string) => Promise<unknown>; renderSelected?: (row: Row) => ReactNode; linkTo?: (row: Row) => string;
}) {
  const resource = useAdminResource(load); const mutation = useAdminMutation();
  const [selectedId, setSelectedId] = useState<number | null>(null); const [revision, setRevision] = useState(0); const [filter, setFilter] = useState(""); const filterId = useId();
  const selected = resource.data?.find((row) => idOf(row) === selectedId) ?? null;
  const reload = async () => { await resource.reload(); setRevision((r) => r + 1); };
  const retry = () => void reload().then(mutation.reset).catch(() => undefined);
  const rows = resource.data?.filter((row) => labelOf(row).toLocaleLowerCase().includes(filter.toLocaleLowerCase())) ?? [];
  return <section className="admin-resource" aria-label={title}>
    <div className="admin-section-heading"><h2>{title}</h2><span>{resource.data?.length ?? "—"}개</span></div>
    <ResourceState loading={resource.loading} error={resource.error} onRetry={retry} />
    <MutationFeedback mutation={mutation} retry={retry} />
    {resource.data && !resource.error ? <>
      <div className="admin-resource-grid">
        <div className="admin-resource-list">
          <label htmlFor={filterId}>{title} 찾기</label><input className="input" id={filterId} value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="이름 또는 ID" />
          <button type="button" className="button button-secondary" disabled={mutation.pending} onClick={() => { setSelectedId(null); setRevision((r) => r + 1); mutation.reset(); }}>새 {title} 만들기</button>
          {!rows.length ? <p>표시할 {title}이 없습니다.</p> : <ul>{rows.map((row) => <li key={idOf(row)}>{linkTo ? <Link href={linkTo(row)}>{labelOf(row)}</Link> : <button type="button" aria-pressed={selectedId === idOf(row)} disabled={mutation.pending} onClick={() => { setSelectedId(idOf(row)); mutation.reset(); }}>{labelOf(row)}</button>}</li>)}</ul>}
        </div>
        <div>
          <CatalogForm<Input> key={`${selectedId}-${revision}`} title={selected ? `${title} 수정` : `${title} 생성`} baseline={selected ?? initial} fields={typeof fields === "function" ? fields(selected, resource.data) : fields} editing={Boolean(selected)} pending={mutation.pending || mutation.refreshFailed || resource.loading} error={mutation.error} onSubmit={(value, changes) => void mutation.run((csrf) => selected && patch ? patch(idOf(selected), changes, csrf) : create(value, csrf), reload)} />
          {selected && remove ? <button type="button" className="button button-danger" disabled={mutation.pending || mutation.refreshFailed || resource.loading} onClick={() => { if (window.confirm(`${labelOf(selected)} 항목을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`)) void mutation.run((csrf) => remove(idOf(selected), csrf), async () => { await reload(); setSelectedId(null); }); }}>선택한 {title} 삭제</button> : null}
        </div>
      </div>
      {selected && renderSelected ? <div key={selectedId} className="admin-nested">{renderSelected(selected)}</div> : null}
    </> : null}
  </section>;
}
