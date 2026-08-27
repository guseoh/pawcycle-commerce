import type { BrandInput, CategoryInput, Category, ProductInput, Brand, SkuInput, ImageInput, OptionGroupInput, OptionValueInput, FacetInput, DetailInput } from "@/lib/admin-catalog-api";
import { categoryHierarchy, categoryParents, type CatalogField } from "@/lib/admin-catalog-forms";

export const brandInitial: BrandInput = { name: "", slug: "", logoUrl: null, active: true, displayOrder: 0 };
export const brandFields: CatalogField<BrandInput>[] = [
  { key: "name", label: "브랜드 이름", required: true, maxLength: 150 },
  { key: "slug", label: "브랜드 slug", required: true, maxLength: 100, help: "소문자 영숫자와 하이픈" },
  { key: "logoUrl", label: "로고 URL", nullable: true, maxLength: 2048 },
  { key: "displayOrder", label: "표시 순서", kind: "number", required: true },
  { key: "active", label: "활성", kind: "checkbox" },
];
export const categoryInitial: CategoryInput = { name: "", slug: "", parentId: null, active: true, displayOrder: 0 };
export function categoryFields(row: Category | null, rows: Category[]): CatalogField<CategoryInput>[] {
  return [
    { key: "name", label: "카테고리 이름", required: true, maxLength: 100 },
    { key: "slug", label: "카테고리 slug", required: true, maxLength: 100, help: "소문자 영숫자와 하이픈" },
    { key: "parentId", label: "상위 카테고리", kind: "select", numeric: true, nullable: true, choices: [{ value: "", label: "최상위 (상위 없음)" }, ...categoryParents(rows, row?.categoryId).map((c) => ({ value: String(c.categoryId), label: c.name }))], help: "최대 2단계. 하위 항목이 있으면 최상위만 선택할 수 있습니다." },
    { key: "displayOrder", label: "표시 순서", kind: "number", required: true },
    { key: "active", label: "활성", kind: "checkbox" },
  ];
}
export function productFields(categories: Category[], brands: Brand[]): CatalogField<ProductInput>[] {
  return [
    { key: "name", label: "상품 이름", required: true, maxLength: 200 },
    { key: "categoryId", label: "카테고리", kind: "select", numeric: true, required: true, choices: [{ value: "", label: "선택해 주세요" }, ...categoryHierarchy(categories).map(({ category, label }) => ({ value: String(category.categoryId), label: `${label}${category.active ? "" : " (비활성)"}` }))] },
    { key: "brandId", label: "브랜드", kind: "select", numeric: true, required: true, choices: [{ value: "", label: "선택해 주세요" }, ...brands.map((b) => ({ value: String(b.brandId), label: `${b.name}${b.active ? "" : " (비활성)"}` }))] },
    { key: "petType", label: "반려동물 유형", required: true, maxLength: 20, help: "예: DOG, CAT" },
    { key: "shortDescription", label: "짧은 설명", required: true, maxLength: 500 },
    { key: "thumbnailUrl", label: "대표 이미지 URL", nullable: true, maxLength: 2048 },
    { key: "description", label: "상품 설명", kind: "textarea", nullable: true, maxLength: 2000 },
  ];
}
export const productInitial: ProductInput = { name: "", categoryId: 0, brandId: 0, petType: "DOG", shortDescription: "", description: null, thumbnailUrl: null };
export const skuInitial: SkuInput = { skuCode: "", name: "", price: 0, compareAtPrice: null, subscribable: false, displayOrder: 0, status: "ACTIVE" };
export const skuFields: CatalogField<SkuInput>[] = [
  { key: "skuCode", label: "SKU 코드", required: true, readOnlyOnEdit: true, maxLength: 100, help: "영숫자로 시작, 영숫자·점·밑줄·하이픈만 사용" },
  { key: "name", label: "SKU 이름", required: true, maxLength: 200 },
  { key: "price", label: "판매가", kind: "money", required: true },
  { key: "compareAtPrice", label: "할인 기준가", kind: "money", nullable: true, help: "설정 시 판매가보다 커야 합니다. 최종 유효성은 서버가 판단합니다." },
  { key: "displayOrder", label: "표시 순서", kind: "number", required: true },
  { key: "status", label: "판매 상태", kind: "select", required: true, choices: [{ value: "ACTIVE", label: "ACTIVE · 활성" }, { value: "INACTIVE", label: "INACTIVE · 비활성" }] },
  { key: "subscribable", label: "정기배송 가능", kind: "checkbox" },
];
export const imageInitial: ImageInput = { imageUrl: "", altText: null, displayOrder: 0, imageType: "DETAIL" };
export const imageFields: CatalogField<ImageInput>[] = [
  { key: "imageUrl", label: "이미지 URL", required: true, maxLength: 2048 },
  { key: "altText", label: "이미지 대체 텍스트", nullable: true, maxLength: 500 },
  { key: "imageType", label: "이미지 유형", kind: "select", required: true, choices: [{ value: "MAIN", label: "MAIN · 대표" }, { value: "DETAIL", label: "DETAIL · 상세" }] },
  { key: "displayOrder", label: "표시 순서", kind: "number", required: true },
];
export const groupInitial: OptionGroupInput = { name: "", displayOrder: 0 };
export const groupFields: CatalogField<OptionGroupInput>[] = [{ key: "name", label: "옵션 그룹 이름", required: true, maxLength: 100 }, { key: "displayOrder", label: "표시 순서", kind: "number", required: true }];
export const valueInitial: OptionValueInput = { value: "", displayOrder: 0 };
export const valueFields: CatalogField<OptionValueInput>[] = [{ key: "value", label: "값", required: true, maxLength: 100 }, { key: "displayOrder", label: "표시 순서", kind: "number", required: true }];
export const facetInitial: FacetInput = { key: "", name: "" };
export const facetFields: CatalogField<FacetInput>[] = [{ key: "key", label: "Facet key", required: true, maxLength: 100, help: "소문자 영숫자와 하이픈" }, { key: "name", label: "Facet 이름", required: true, maxLength: 100 }];
export const detailInitial: DetailInput = { title: "", body: "", displayOrder: 0, visible: true };
export const detailFields: CatalogField<DetailInput>[] = [{ key: "title", label: "섹션 제목", required: true, maxLength: 200 }, { key: "body", label: "상세 본문", kind: "textarea", required: true, maxLength: 10000, help: "일반 텍스트입니다. HTML을 실행하지 않습니다." }, { key: "displayOrder", label: "표시 순서", kind: "number", required: true }, { key: "visible", label: "공개 표시", kind: "checkbox" }];
