"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useMemo, useState } from "react";
import { adminCatalogApi as api, type Product, type Category, type Brand } from "@/lib/admin-catalog-api";
import { categoryHierarchy, productStatusAction } from "@/lib/admin-catalog-forms";
import { AdminGate, CatalogForm, MutationFeedback, ResourcePanel, ResourceState, useAdminMutation, useAdminResource } from "./shared";
import { CategoryAssignments, ProductFacetAssignments, SkuAssignments } from "./assignments";
import * as fields from "./fields";

const loadReferences = () => Promise.all([api.categories.list(), api.brands.list()]);
const loadCategories = () => api.categories.list().then((rows) => categoryHierarchy(rows).map((entry) => entry.category));

function SectionNavigation<T extends string>({ items, active, select, label }: { items: readonly { key: T; label: string }[]; active: T; select: (key: T) => void; label: string }) {
  return <nav className="admin-sections" aria-label={label}>{items.map((item) => <button key={item.key} type="button" aria-pressed={active === item.key} onClick={() => select(item.key)}>{item.label}</button>)}</nav>;
}

const catalogSections = [{ key: "products", label: "상품" }, { key: "brands", label: "브랜드" }, { key: "categories", label: "카테고리" }, { key: "facets", label: "Facet" }] as const;
export function AdminCatalogScreen() {
  const [section, setSection] = useState<typeof catalogSections[number]["key"]>("products");
  return <AdminGate><div className="admin-catalog"><header className="admin-heading"><p className="eyebrow">ADMIN CATALOG</p><h1>Catalog 관리</h1><p>상품과 판매 옵션, 고객에게 보이는 콘텐츠를 관리합니다.</p></header>
    <SectionNavigation items={catalogSections} active={section} select={setSection} label="Catalog 영역" />
    <div key={section}>
      {section === "products" ? <Products /> : section === "brands" ? <ResourcePanel title="브랜드" load={api.brands.list} idOf={(r) => r.brandId} labelOf={(r) => `${r.name} · #${r.brandId} · ${r.active ? "활성" : "비활성"}`} initial={fields.brandInitial} fields={fields.brandFields} create={api.brands.create} patch={api.brands.patch} />
        : section === "categories" ? <ResourcePanel title="카테고리" load={loadCategories} idOf={(r) => r.categoryId} labelOf={(r) => `${r.parentId === null ? "" : "↳ "}${r.name} · #${r.categoryId} · ${r.active ? "활성" : "비활성"}`} initial={fields.categoryInitial} fields={fields.categoryFields} create={api.categories.create} patch={api.categories.patch} /> : <Facets />}
    </div>
  </div></AdminGate>;
}

function Products() {
  const references = useAdminResource(loadReferences); const router = useRouter();
  return <><ResourceState {...references} onRetry={() => void references.reload().catch(() => undefined)} />
    {references.data && !references.error ? <ResourcePanel title="상품" load={api.products.list} idOf={(r) => r.productId} labelOf={(r) => `${r.name} · #${r.productId} · ${r.status}`} linkTo={(r) => `/admin/catalog/products/${r.productId}`} initial={fields.productInitial} fields={fields.productFields(references.data[0], references.data[1])} create={async (body, csrf) => { const product = await api.products.create(body, csrf); router.push(`/admin/catalog/products/${product.productId}`); return product; }} /> : null}
  </>;
}

function Facets() {
  const [section, setSection] = useState<"definitions" | "categories">("definitions");
  return <><SectionNavigation items={[{ key: "definitions", label: "Facet 정의 / 옵션" }, { key: "categories", label: "Category 배정" }]} active={section} select={setSection} label="Facet 관리" />
    {section === "definitions" ? <ResourcePanel title="Facet 정의" load={api.facets.list} idOf={(r) => r.facetDefinitionId} labelOf={(r) => `${r.name} · ${r.key} · #${r.facetDefinitionId}`} initial={fields.facetInitial} fields={fields.facetFields} create={api.facets.create} patch={api.facets.patch} remove={api.facets.remove} renderSelected={(row) => <FacetOptions definitionId={row.facetDefinitionId} />} /> : <CategoryAssignments />}
  </>;
}
function FacetOptions({ definitionId }: { definitionId: number }) {
  const load = useCallback(() => api.facets.get(definitionId).then((r) => r.options), [definitionId]);
  const client = useMemo(() => api.facetOptions(definitionId), [definitionId]);
  return <ResourcePanel title="Facet 옵션" load={load} idOf={(r) => r.facetOptionId} labelOf={(r) => `${r.value} · #${r.facetOptionId}`} initial={fields.valueInitial} fields={fields.valueFields} {...client} />;
}

const workspaceSections = [{ key: "basic", label: "기본 정보" }, { key: "skus", label: "SKU" }, { key: "images", label: "이미지" }, { key: "options", label: "옵션" }, { key: "facets", label: "Facet" }, { key: "details", label: "상세 콘텐츠" }] as const;
export function AdminProductWorkspace({ productId }: { productId: number }) {
  return <AdminGate><Workspace key={productId} productId={productId} /></AdminGate>;
}
function Workspace({ productId }: { productId: number }) {
  const load = useCallback(() => Promise.all([api.products.get(productId), api.categories.list(), api.brands.list()]), [productId]);
  const resource = useAdminResource(load);
  const [section, setSection] = useState<typeof workspaceSections[number]["key"]>("basic");
  return <div className="admin-catalog">
    <Link className="breadcrumb" href="/admin/catalog">← Catalog 목록</Link>
    <ResourceState {...resource} onRetry={() => void resource.reload().catch(() => undefined)} />
    {resource.data ? <><header className="admin-heading"><p className="eyebrow">PRODUCT WORKSPACE · #{productId}</p><h1>{resource.data[0].name}</h1><span className="status-badge">{resource.data[0].status}</span></header>
      <SectionNavigation items={workspaceSections} active={section} select={setSection} label="상품 workspace 섹션" />
      <div key={section}>
        {section === "basic" ? <ProductBasics product={resource.data[0]} categories={resource.data[1]} brands={resource.data[2]} reload={resource.reload} blocked={resource.loading || Boolean(resource.error)} />
          : section === "skus" ? <Skus productId={productId} /> : section === "images" ? <Images productId={productId} /> : section === "options" ? <Options productId={productId} /> : section === "facets" ? <ProductFacetAssignments productId={productId} /> : <Details productId={productId} />}
      </div>
    </> : null}
  </div>;
}
function ProductBasics({ product, categories, brands, reload, blocked }: { product: Product; categories: Category[]; brands: Brand[]; reload: () => Promise<void>; blocked: boolean }) {
  const mutation = useAdminMutation(); const [revision, setRevision] = useState(0);
  const refresh = async () => { await reload(); setRevision((r) => r + 1); };
  const retry = () => void refresh().then(mutation.reset).catch(() => undefined);
  const action = productStatusAction(product.status);
  return <section className="admin-resource"><h2>기본 정보</h2>
    <MutationFeedback mutation={mutation} retry={retry} />
    <CatalogForm key={revision} title="상품 정보 수정" baseline={product} fields={fields.productFields(categories, brands)} editing pending={blocked || mutation.pending || mutation.refreshFailed} error={mutation.error} onSubmit={(_, changes) => void mutation.run((csrf) => api.products.patch(product.productId, changes, csrf), refresh)} />
    <div className="admin-status-action"><h3>상품 노출 상태</h3><p>현재 상태: <strong>{product.status}</strong>. 공개 상품은 활성 Brand와 Category 조건도 충족해야 고객 화면에 노출됩니다.</p>
      <button type="button" className={`button ${product.status === "PUBLIC" ? "button-danger" : "button-secondary"}`} disabled={blocked || mutation.pending || mutation.refreshFailed} onClick={() => { if (window.confirm(`${product.name}: ${action.label} 작업을 진행하시겠습니까?`)) void mutation.run((csrf) => api.products.patch(product.productId, { status: action.status }, csrf), refresh); }}>{action.label}</button>
      {product.status === "PUBLIC" ? <Link className="button button-secondary" href={`/products/${product.productId}`}>고객 상품 화면 확인</Link> : null}
    </div>
  </section>;
}
function Skus({ productId }: { productId: number }) {
  const client = useMemo(() => api.skus(productId), [productId]);
  return <ResourcePanel title="SKU" load={client.list} idOf={(r) => r.skuId} labelOf={(r) => `${r.name} · ${r.skuCode} · ${r.status}`} initial={fields.skuInitial} fields={fields.skuFields} create={client.create} patch={client.patch} />;
}
function Images({ productId }: { productId: number }) {
  const client = useMemo(() => api.images(productId), [productId]);
  return <ResourcePanel title="이미지" load={client.list} idOf={(r) => r.imageId} labelOf={(r) => `${r.imageType} · ${r.altText ?? r.imageUrl} · #${r.imageId}`} initial={fields.imageInitial} fields={fields.imageFields} create={client.create} patch={client.patch} remove={client.remove} />;
}
function Details({ productId }: { productId: number }) {
  const client = useMemo(() => api.details(productId), [productId]);
  return <ResourcePanel title="상세 섹션" load={client.list} idOf={(r) => r.sectionId} labelOf={(r) => `${r.title} · ${r.visible ? "공개" : "숨김"} · #${r.sectionId}`} initial={fields.detailInitial} fields={fields.detailFields} create={client.create} patch={client.patch} remove={client.remove} />;
}
function Options({ productId }: { productId: number }) {
  const [section, setSection] = useState<"groups" | "assignments">("groups");
  const client = useMemo(() => api.optionGroups(productId), [productId]);
  return <><SectionNavigation items={[{ key: "groups", label: "그룹 / 값 관리" }, { key: "assignments", label: "SKU 옵션 배정" }]} active={section} select={setSection} label="옵션 관리" />
    {section === "groups" ? <ResourcePanel title="옵션 그룹" load={client.list} idOf={(r) => r.optionGroupId} labelOf={(r) => `${r.name} · #${r.optionGroupId}`} initial={fields.groupInitial} fields={fields.groupFields} create={client.create} patch={client.patch} remove={client.remove} renderSelected={(row) => <OptionValues productId={productId} groupId={row.optionGroupId} />} /> : <SkuAssignments productId={productId} />}
  </>;
}
function OptionValues({ productId, groupId }: { productId: number; groupId: number }) {
  const load = useCallback(() => api.optionGroups(productId).list().then((rows) => {
    const group = rows.find((g) => g.optionGroupId === groupId);
    if (!group) throw new Error("옵션 그룹이 더 이상 존재하지 않습니다.");
    return group.values;
  }), [productId, groupId]);
  const client = useMemo(() => api.optionValues(productId, groupId), [productId, groupId]);
  return <ResourcePanel title="옵션 값" load={load} idOf={(r) => r.optionValueId} labelOf={(r) => `${r.value} · #${r.optionValueId}`} initial={fields.valueInitial} fields={fields.valueFields} {...client} />;
}
