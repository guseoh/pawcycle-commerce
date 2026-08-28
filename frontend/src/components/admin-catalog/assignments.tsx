"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { adminCatalogApi as api, type Facet, type OptionGroup, type Sku } from "@/lib/admin-catalog-api";
import { ApiError } from "@/lib/api";
import { categoryHierarchy, optionAssignment } from "@/lib/admin-catalog-forms";
import { errorMessage, MutationFeedback, ResourceState, useAdminMutation, useAdminResource } from "./shared";

export function ActionError({ error, id }: { error: unknown; id?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error) ref.current?.focus(); }, [error]);
  return error ? <div id={id} ref={ref} tabIndex={-1} role="alert" className="error-summary"><p>{errorMessage(error)}</p>{error instanceof ApiError ? <ul>{error.fieldErrors.map((field, i) => <li key={i}>{field.field}: {field.message}</li>)}</ul> : null}</div> : <span id={id} />;
}

const loadCategoryReferences = () => Promise.all([api.categories.list(), api.facets.list()]);
export function CategoryAssignments() {
  const resource = useAdminResource(loadCategoryReferences);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const categories = resource.data?.[0] ?? [];
  const selectedCategoryId = categoryId !== null && categories.some((category) => category.categoryId === categoryId) ? categoryId : categories[0]?.categoryId ?? null;
  return <section className="admin-resource"><h2>Category Facet 배정</h2><p className="field-help">카테고리를 선택하면 현재 배정값을 읽고, 기존 항목의 순서 변경·해제와 새 Facet 추가를 할 수 있습니다.</p><ResourceState {...resource} onRetry={() => void resource.reload().catch(() => undefined)} />{resource.data && !resource.error ? <><label className="form-field" htmlFor="category-facet-category">카테고리<select id="category-facet-category" className="input" value={selectedCategoryId ?? ""} onChange={(event) => setCategoryId(Number(event.target.value))}>{categoryHierarchy(categories).map(({ category, label }) => <option key={category.categoryId} value={category.categoryId}>{label}</option>)}</select></label>{selectedCategoryId !== null ? <CategoryFacetEditor categoryId={selectedCategoryId} facets={resource.data[1]} /> : null}</> : null}</section>;
}

function CategoryFacetEditor({ categoryId, facets }: { categoryId: number; facets: Facet[] }) {
  const load = useCallback(() => api.categoryFacets(categoryId), [categoryId]);
  const resource = useAdminResource(load); const mutation = useAdminMutation();
  const [newDefinitionId, setNewDefinitionId] = useState(""); const [newOrder, setNewOrder] = useState("0");
  const definitionName = (id: number) => facets.find((facet) => facet.facetDefinitionId === id)?.name ?? `Facet #${id}`;
  const assigned = resource.data?.facets ?? [];
  const unassigned = facets.filter((facet) => !assigned.some((item) => item.facetDefinitionId === facet.facetDefinitionId));
  const refresh = () => resource.reload().catch(() => undefined);
  return <div className="admin-nested"><ResourceState {...resource} onRetry={() => void refresh()} /><ActionError error={mutation.error} /><MutationFeedback mutation={mutation} />{resource.data && !resource.error ? <><div className="admin-current-assignments"><h3>현재 배정</h3>{assigned.length ? <ul>{assigned.map((item) => <li key={item.facetDefinitionId}><div><strong>{definitionName(item.facetDefinitionId)}</strong><span>표시 순서 {item.displayOrder}</span></div><div className="button-row"><button type="button" className="button button-secondary" disabled={mutation.pending} onClick={() => { const raw = window.prompt("새 표시 순서를 입력하세요.", String(item.displayOrder)); if (raw === null) return; const order = Number(raw); if (!Number.isInteger(order) || order < 0) return; void mutation.run((csrf) => api.assignCategoryFacet(categoryId, item.facetDefinitionId, order, csrf), refresh); }}>순서 변경</button><button type="button" className="button button-danger" disabled={mutation.pending} onClick={() => { if (window.confirm(`${definitionName(item.facetDefinitionId)} 배정을 해제하시겠습니까?`)) void mutation.run((csrf) => api.removeCategoryFacet(categoryId, item.facetDefinitionId, csrf), refresh); }}>배정 해제</button></div></li>)}</ul> : <p className="empty-callout">현재 배정된 Facet이 없습니다.</p>}</div><form className="admin-form" onSubmit={(event) => { event.preventDefault(); const definitionId = Number(newDefinitionId); const order = Number(newOrder); if (!definitionId || !Number.isInteger(order) || order < 0) return; void mutation.run((csrf) => api.assignCategoryFacet(categoryId, definitionId, order, csrf), async () => { setNewDefinitionId(""); setNewOrder("0"); await refresh(); }); }}><h3>Facet 추가</h3><div className="admin-fields"><label className="form-field">배정할 Facet<select className="input" value={newDefinitionId} onChange={(event) => setNewDefinitionId(event.target.value)} disabled={mutation.pending}><option value="">선택해 주세요</option>{unassigned.map((facet) => <option key={facet.facetDefinitionId} value={facet.facetDefinitionId}>{facet.name} · {facet.key}</option>)}</select></label><label className="form-field">표시 순서<input className="input" type="number" min="0" step="1" value={newOrder} onChange={(event) => setNewOrder(event.target.value)} disabled={mutation.pending} /></label></div><button className="button button-primary" type="submit" disabled={mutation.pending || !newDefinitionId}>Facet 추가</button></form></> : null}</div>;
}

export function SkuAssignments({ productId }: { productId: number }) {
  const load = useCallback(() => Promise.all([api.skus(productId).list(), api.optionGroups(productId).list()]), [productId]);
  const resource = useAdminResource(load);
  return <section className="admin-resource"><h2>SKU 옵션 배정</h2>
    <ResourceState {...resource} onRetry={() => void resource.reload().catch(() => undefined)} />
    {resource.data && !resource.error ? <SkuAssignmentChooser productId={productId} skus={resource.data[0]} groups={resource.data[1]} /> : null}
  </section>;
}

function SkuAssignmentChooser({ productId, skus, groups }: { productId: number; skus: Sku[]; groups: OptionGroup[] }) {
  const [skuId, setSkuId] = useState(skus[0]?.skuId ?? 0); const id = useId();
  if (!skus.length) return <p>SKU를 먼저 생성해 주세요.</p>;
  return <><label htmlFor={id}>배정할 SKU</label><select id={id} className="input" value={skuId} onChange={(e) => setSkuId(Number(e.target.value))}>{skus.map((s) => <option key={s.skuId} value={s.skuId}>{s.name} · {s.skuCode}</option>)}</select>
    <SkuAssignmentEditor key={skuId} productId={productId} skuId={skuId} groups={groups} />
  </>;
}

function SkuAssignmentEditor({ productId, skuId, groups }: { productId: number; skuId: number; groups: OptionGroup[] }) {
  const load = useCallback(() => api.skus(productId).optionAssignment(skuId), [productId, skuId]);
  const readback = useAdminResource(load); const [selected, setSelected] = useState<Record<number, string>>({}); const mutation = useAdminMutation(); const id = useId();
  useEffect(() => { let active = true; queueMicrotask(() => { const assignment = readback.data; if (!active || !assignment) return; const values: Record<number, string> = {}; groups.forEach((group) => { const match = group.values.find((value) => assignment.optionValueIds.includes(value.optionValueId)); if (match) values[group.optionGroupId] = String(match.optionValueId); }); setSelected(values); }); return () => { active = false; }; }, [groups, readback.data]);
  return <form className="admin-form" onSubmit={(event) => {
    event.preventDefault();
    if (!readback.data || readback.loading || readback.error) return;
    if (!window.confirm("이 SKU의 기존 옵션 배정을 선택한 값 전체로 교체합니다. 계속하시겠습니까?")) return;
    void mutation.run((csrf) => api.skus(productId).assignOptions(skuId, optionAssignment(groups, selected), csrf));
  }}><ResourceState {...readback} onRetry={() => void readback.reload().catch(() => undefined)} /><p className="admin-gap">현재 서버 배정값을 불러온 뒤 선택값 전체를 교체합니다. 조회에 실패하면 저장할 수 없습니다.</p>
    <ActionError error={mutation.error} id={`${id}-error`} /><MutationFeedback mutation={mutation} />
    <fieldset className="admin-fields" disabled={mutation.pending || readback.loading || Boolean(readback.error) || !readback.data} aria-describedby={`${id}-error`}><legend>교체할 옵션 값</legend>
      {groups.length ? groups.map((g) => <div className="form-field" key={g.optionGroupId}><label htmlFor={`${id}-${g.optionGroupId}`}>{g.name}</label><select className="input" id={`${id}-${g.optionGroupId}`} value={selected[g.optionGroupId] ?? ""} onChange={(e) => setSelected((prev) => ({ ...prev, [g.optionGroupId]: e.target.value }))}><option value="">배정 없음</option>{g.values.map((v) => <option key={v.optionValueId} value={v.optionValueId}>{v.value}</option>)}</select></div>) : <p>옵션 그룹이 없습니다.</p>}
    </fieldset><button type="submit" className="button button-primary" disabled={mutation.pending || readback.loading || Boolean(readback.error) || !readback.data}>{mutation.pending ? "처리 중…" : "SKU 옵션 전체 교체"}</button>
  </form>;
}

export function ProductFacetAssignments({ productId }: { productId: number }) {
  const resource = useAdminResource(api.facets.list);
  return <section className="admin-resource"><h2>Product Facet 값</h2><ResourceState {...resource} onRetry={() => void resource.reload().catch(() => undefined)} />
    {resource.data && !resource.error ? <ProductFacetEditor productId={productId} facets={resource.data} /> : null}
  </section>;
}

function ProductFacetEditor({ productId, facets }: { productId: number; facets: Facet[] }) {
  const load = useCallback(() => api.productFacetAssignment(productId), [productId]);
  const readback = useAdminResource(load); const [selected, setSelected] = useState<number[]>([]); const mutation = useAdminMutation(); const id = useId();
  useEffect(() => { let active = true; queueMicrotask(() => { if (active && readback.data) setSelected(readback.data.facetOptionIds); }); return () => { active = false; }; }, [readback.data]);
  return <form className="admin-form" onSubmit={(e) => {
    e.preventDefault();
    if (!readback.data || readback.loading || readback.error) return;
    if (!window.confirm("이 상품의 기존 Facet 값을 선택한 값 전체로 교체합니다. 계속하시겠습니까?")) return;
    void mutation.run((csrf) => api.assignProductFacets(productId, selected, csrf));
  }}><ResourceState {...readback} onRetry={() => void readback.reload().catch(() => undefined)} /><p className="admin-gap">현재 서버 배정값을 불러온 뒤 선택값 전체를 교체합니다. 조회에 실패하면 저장할 수 없습니다.</p>
    <ActionError error={mutation.error} id={`${id}-error`} /><MutationFeedback mutation={mutation} />
    <fieldset disabled={mutation.pending || readback.loading || Boolean(readback.error) || !readback.data} className="admin-facet-options" aria-describedby={`${id}-error`}><legend>교체할 Facet 값</legend>
      {!facets.length ? <p>Facet 정의가 없습니다.</p> : facets.map((f) => <fieldset key={f.facetDefinitionId}><legend>{f.name} · {f.key}</legend>{f.options.length ? f.options.map((o) => <label key={o.facetOptionId}><input type="checkbox" checked={selected.includes(o.facetOptionId)} onChange={(e) => setSelected((current) => e.target.checked ? [...current, o.facetOptionId] : current.filter((value) => value !== o.facetOptionId))} />{o.value}</label>) : <p>옵션이 없습니다.</p>}</fieldset>)}
    </fieldset><button type="submit" className="button button-primary" disabled={mutation.pending || readback.loading || Boolean(readback.error) || !readback.data}>{mutation.pending ? "처리 중…" : "상품 Facet 전체 교체"}</button>
  </form>;
}
