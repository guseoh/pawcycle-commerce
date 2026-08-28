import { ApiError } from "./api.ts";

export type ProductStatus = "DRAFT" | "PUBLIC" | "INACTIVE";
export type SkuStatus = "ACTIVE" | "INACTIVE";
export interface BrandInput { name: string; slug: string; logoUrl: string | null; active: boolean; displayOrder: number }
export interface Brand extends BrandInput { brandId: number }
export interface CategoryInput { name: string; slug: string; parentId: number | null; active: boolean; displayOrder: number }
export interface Category extends CategoryInput { categoryId: number }
export interface ProductInput { categoryId: number; brandId: number; name: string; shortDescription: string; description: string | null; petType: string; thumbnailUrl: string | null }
export interface Product extends ProductInput { productId: number; status: ProductStatus }
export type ProductPatch = Partial<ProductInput & { status: ProductStatus }>;
export interface SkuInput { skuCode: string; name: string; price: number; compareAtPrice: number | null; subscribable: boolean; displayOrder: number; status: SkuStatus }
export interface Sku extends SkuInput { skuId: number; productId: number }
export type SkuPatch = Partial<Omit<SkuInput, "skuCode">>;
export interface ImageInput { imageUrl: string; altText: string | null; displayOrder: number; imageType: "MAIN" | "DETAIL" }
export interface CatalogImage extends ImageInput { imageId: number; productId: number }
export interface OptionGroupInput { name: string; displayOrder: number }
export interface OptionValueInput { value: string; displayOrder: number }
export interface OptionValue extends OptionValueInput { optionValueId: number; optionGroupId: number }
export interface OptionGroup extends OptionGroupInput { optionGroupId: number; productId: number; values: OptionValue[] }
export interface FacetInput { key: string; name: string }
export interface FacetOption extends OptionValueInput { facetOptionId: number; facetDefinitionId: number }
export interface Facet extends FacetInput { facetDefinitionId: number; options: FacetOption[] }
export interface DetailInput { title: string; body: string; displayOrder: number; visible: boolean }
export interface DetailSection extends DetailInput { sectionId: number; productId: number; createdAt: string; updatedAt: string }
export interface SkuOptionAssignment { skuId: number; optionValueIds: number[] }
export interface ProductFacetAssignment { productId: number; facetOptionIds: number[] }
export interface CategoryFacetAssignment { categoryId: number; facetDefinitionId: number; displayOrder: number }
export interface CategoryFacetList { categoryId: number; facets: CategoryFacetAssignment[] }

async function request<T>(path: string, method = "GET", body?: unknown, csrf?: string): Promise<T> {
  const response = await fetch(`/api/admin${path}`, {
    method, credentials: "same-origin", cache: "no-store",
    headers: { Accept: "application/json", ...(body === undefined ? {} : { "Content-Type": "application/json" }), ...(csrf ? { "X-CSRF-TOKEN": csrf } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await response.text();
  let result: unknown = null;
  try { result = text ? JSON.parse(text) : null; } catch {
    throw new ApiError(response.status, { code: "INVALID_API_RESPONSE", message: "서버 응답을 확인할 수 없습니다.", fieldErrors: [] });
  }
  if (!response.ok) {
    if (result && typeof result === "object" && "code" in result && typeof result.code === "string" && "message" in result && typeof result.message === "string" && "fieldErrors" in result && Array.isArray(result.fieldErrors)) {
      throw new ApiError(response.status, { code: result.code, message: result.message, fieldErrors: result.fieldErrors });
    }
    throw new ApiError(response.status, { code: "INTERNAL_ERROR", message: "요청을 처리하지 못했습니다.", fieldErrors: [] });
  }
  return result as T;
}

function editable<Input, View, Patch = Partial<Input>>(path: string) {
  return {
    create: (body: Input, csrf: string) => request<View>(path, "POST", body, csrf),
    patch: (id: number, body: Patch, csrf: string) => request<View>(`${path}/${id}`, "PATCH", body, csrf),
  };
}
function removable<Input, View>(path: string) {
  return { ...editable<Input, View>(path), remove: (id: number, csrf: string) => request<void>(`${path}/${id}`, "DELETE", undefined, csrf) };
}

export const adminCatalogApi = {
  brands: {
    ...editable<BrandInput, Brand>("/brands"),
    list: () => request<{ brands: Brand[] }>("/brands").then((r) => r.brands),
    get: (id: number) => request<Brand>(`/brands/${id}`),
  },
  categories: {
    ...editable<CategoryInput, Category>("/categories"),
    list: () => request<{ categories: Category[] }>("/categories").then((r) => r.categories),
    get: (id: number) => request<Category>(`/categories/${id}`),
  },
  products: {
    ...editable<ProductInput, Product, ProductPatch>("/products"),
    list: () => request<{ products: Product[] }>("/products").then((r) => r.products),
    get: (id: number) => request<Product>(`/products/${id}`),
  },
  skus: (productId: number) => ({
    ...editable<SkuInput, Sku, SkuPatch>(`/products/${productId}/skus`),
    list: () => request<{ skus: Sku[] }>(`/products/${productId}/skus`).then((r) => r.skus),
    optionAssignment: (skuId: number) => request<SkuOptionAssignment>(`/products/${productId}/skus/${skuId}/option-values`),
    assignOptions: (skuId: number, optionValueIds: number[], csrf: string) => request<SkuOptionAssignment>(`/products/${productId}/skus/${skuId}/option-values`, "PUT", { optionValueIds }, csrf),
  }),
  images: (productId: number) => ({
    ...removable<ImageInput, CatalogImage>(`/products/${productId}/images`),
    list: () => request<{ images: CatalogImage[] }>(`/products/${productId}/images`).then((r) => r.images),
  }),
  optionGroups: (productId: number) => ({
    ...removable<OptionGroupInput, OptionGroup>(`/products/${productId}/option-groups`),
    list: () => request<{ optionGroups: OptionGroup[] }>(`/products/${productId}/option-groups`).then((r) => r.optionGroups),
  }),
  optionValues: (productId: number, groupId: number) => removable<OptionValueInput, OptionValue>(`/products/${productId}/option-groups/${groupId}/values`),
  facets: {
    ...removable<FacetInput, Facet>("/facets"),
    list: () => request<{ facetDefinitions: Facet[] }>("/facets").then((r) => r.facetDefinitions),
    get: (id: number) => request<Facet>(`/facets/${id}`),
  },
  facetOptions: (definitionId: number) => removable<OptionValueInput, FacetOption>(`/facets/${definitionId}/options`),
  assignCategoryFacet: (categoryId: number, definitionId: number, displayOrder: number, csrf: string) => request<CategoryFacetAssignment>(`/categories/${categoryId}/facets/${definitionId}`, "PUT", { displayOrder }, csrf),
  removeCategoryFacet: (categoryId: number, definitionId: number, csrf: string) => request<void>(`/categories/${categoryId}/facets/${definitionId}`, "DELETE", undefined, csrf),
  categoryFacets: (categoryId: number) => request<CategoryFacetList>(`/categories/${categoryId}/facets`),
  productFacetAssignment: (productId: number) => request<ProductFacetAssignment>(`/products/${productId}/facet-values`),
  assignProductFacets: (productId: number, facetOptionIds: number[], csrf: string) => request<ProductFacetAssignment>(`/products/${productId}/facet-values`, "PUT", { facetOptionIds }, csrf),
  details: (productId: number) => ({
    ...removable<DetailInput, DetailSection>(`/products/${productId}/detail-sections`),
    list: () => request<{ detailSections: DetailSection[] }>(`/products/${productId}/detail-sections`).then((r) => r.detailSections),
  }),
};
