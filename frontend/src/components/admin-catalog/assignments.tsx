"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { adminCatalogApi as api, type Facet, type OptionGroup, type Sku } from "@/lib/admin-catalog-api";
import { ApiError } from "@/lib/api";
import { categoryHierarchy, optionAssignment, type CatalogField } from "@/lib/admin-catalog-forms";
import { CatalogForm, errorMessage, MutationFeedback, ResourceState, useAdminMutation, useAdminResource } from "./shared";

export function ActionError({ error, id }: { error: unknown; id?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error) ref.current?.focus(); }, [error]);
  return error ? <div id={id} ref={ref} tabIndex={-1} role="alert" className="error-summary"><p>{errorMessage(error)}</p>{error instanceof ApiError ? <ul>{error.fieldErrors.map((field, i) => <li key={i}>{field.field}: {field.message}</li>)}</ul> : null}</div> : <span id={id} />;
}

const loadCategoryFacets = () => Promise.all([api.categories.list(), api.facets.list()]);
export function CategoryAssignments() {
  const resource = useAdminResource(loadCategoryFacets); const mutation = useAdminMutation();
  const initial = { categoryId: 0, definitionId: 0, displayOrder: 0, action: "assign" };
  const fields: CatalogField<typeof initial>[] = [
    { key: "categoryId", label: "대상 카테고리", kind: "select", numeric: true, required: true, choices: [{ value: "", label: "선택해 주세요" }, ...categoryHierarchy(resource.data?.[0] ?? []).map(({ category, label }) => ({ value: String(category.categoryId), label }))] },
    { key: "definitionId", label: "Facet 정의", kind: "select", numeric: true, required: true, choices: [{ value: "", label: "선택해 주세요" }, ...(resource.data?.[1] ?? []).map((f) => ({ value: String(f.facetDefinitionId), label: f.name }))] },
    { key: "displayOrder", label: "배정 표시 순서", kind: "number", required: true },
    { key: "action", label: "배정 작업", kind: "select", choices: [{ value: "assign", label: "배정 또는 순서 변경" }, { value: "remove", label: "배정 해제" }] },
  ];
  return <section className="admin-resource"><h2>Category Facet 배정</h2><p className="field-help">현재 배정 목록을 읽는 Admin API는 없습니다. 대상과 작업을 직접 지정합니다. 배정 해제가 거부되면 사용 중인 상품의 Facet 값을 먼저 확인해 주세요.</p>
    <ResourceState {...resource} onRetry={() => void resource.reload().catch(() => undefined)} />
    <MutationFeedback mutation={mutation} />
    {resource.data && !resource.error ? <CatalogForm title="Category Facet 배정 작업" baseline={initial} fields={fields} editing={false} pending={mutation.pending || resource.loading} error={mutation.error} onSubmit={(value) => {
      if (value.action === "remove" && !window.confirm("선택한 카테고리의 Facet 배정을 해제하시겠습니까?")) return;
      void mutation.run((csrf) => value.action === "remove" ? api.removeCategoryFacet(value.categoryId, value.definitionId, csrf) : api.assignCategoryFacet(value.categoryId, value.definitionId, value.displayOrder, csrf));
    }} /> : null}
  </section>;
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
  const [selected, setSelected] = useState<Record<number, string>>({}); const mutation = useAdminMutation(); const id = useId();
  return <form className="admin-form" onSubmit={(event) => {
    event.preventDefault();
    if (!window.confirm("이 SKU의 기존 옵션 배정을 선택한 값 전체로 교체합니다. 계속하시겠습니까?")) return;
    void mutation.run((csrf) => api.skus(productId).assignOptions(skuId, optionAssignment(groups, selected), csrf));
  }}><p className="admin-gap">현재 배정값 조회 API가 없어 기존 선택은 표시하지 않습니다. 저장하면 선택값 전체로 교체하며, 모두 ‘배정 없음’이면 기존 배정을 해제합니다.</p>
    <ActionError error={mutation.error} id={`${id}-error`} /><MutationFeedback mutation={mutation} />
    <fieldset className="admin-fields" disabled={mutation.pending} aria-describedby={`${id}-error`}><legend>교체할 옵션 값</legend>
      {groups.length ? groups.map((g) => <div className="form-field" key={g.optionGroupId}><label htmlFor={`${id}-${g.optionGroupId}`}>{g.name}</label><select className="input" id={`${id}-${g.optionGroupId}`} value={selected[g.optionGroupId] ?? ""} onChange={(e) => setSelected((prev) => ({ ...prev, [g.optionGroupId]: e.target.value }))}><option value="">배정 없음</option>{g.values.map((v) => <option key={v.optionValueId} value={v.optionValueId}>{v.value}</option>)}</select></div>) : <p>옵션 그룹이 없습니다.</p>}
    </fieldset><button type="submit" className="button button-primary" disabled={mutation.pending}>{mutation.pending ? "처리 중…" : "SKU 옵션 전체 교체"}</button>
  </form>;
}

export function ProductFacetAssignments({ productId }: { productId: number }) {
  const resource = useAdminResource(api.facets.list);
  return <section className="admin-resource"><h2>Product Facet 값</h2><ResourceState {...resource} onRetry={() => void resource.reload().catch(() => undefined)} />
    {resource.data && !resource.error ? <ProductFacetEditor productId={productId} facets={resource.data} /> : null}
  </section>;
}

function ProductFacetEditor({ productId, facets }: { productId: number; facets: Facet[] }) {
  const [selected, setSelected] = useState<number[]>([]); const mutation = useAdminMutation(); const id = useId();
  return <form className="admin-form" onSubmit={(e) => {
    e.preventDefault();
    if (!window.confirm("이 상품의 기존 Facet 값을 선택한 값 전체로 교체합니다. 계속하시겠습니까?")) return;
    void mutation.run((csrf) => api.assignProductFacets(productId, selected, csrf));
  }}><p className="admin-gap">현재 배정값 조회 API가 없어 기존 선택은 표시하지 않습니다. 상품 카테고리에 허용된 Facet만 서버가 수락합니다. 빈 선택으로 저장하면 기존 값을 모두 해제합니다.</p>
    <ActionError error={mutation.error} id={`${id}-error`} /><MutationFeedback mutation={mutation} />
    <fieldset disabled={mutation.pending} className="admin-facet-options" aria-describedby={`${id}-error`}><legend>교체할 Facet 값</legend>
      {!facets.length ? <p>Facet 정의가 없습니다.</p> : facets.map((f) => <fieldset key={f.facetDefinitionId}><legend>{f.name} · {f.key}</legend>{f.options.length ? f.options.map((o) => <label key={o.facetOptionId}><input type="checkbox" checked={selected.includes(o.facetOptionId)} onChange={(e) => setSelected((current) => e.target.checked ? [...current, o.facetOptionId] : current.filter((value) => value !== o.facetOptionId))} />{o.value}</label>) : <p>옵션이 없습니다.</p>}</fieldset>)}
    </fieldset><button type="submit" className="button button-primary" disabled={mutation.pending}>{mutation.pending ? "처리 중…" : "상품 Facet 전체 교체"}</button>
  </form>;
}
