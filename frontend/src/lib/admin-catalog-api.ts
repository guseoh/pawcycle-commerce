import { requestJson, requestVoid } from "./http/client.ts";

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

function requestInit(method: string, body?: unknown, csrf?: string): RequestInit {
  const headers = new Headers();
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (csrf) headers.set("X-CSRF-TOKEN", csrf);
  return { method, headers, ...(body === undefined ? {} : { body: JSON.stringify(body) }) };
}

function adminPath(path: string): string { return `/api/admin${path}`; }

function editable<Input, View, Patch = Partial<Input>>(path: string) {
  return {
    create: (body: Input, csrf: string) => requestJson<View>(adminPath(path), requestInit("POST", body, csrf)),
    patch: (id: number, body: Patch, csrf: string) => requestJson<View>(adminPath(`${path}/${id}`), requestInit("PATCH", body, csrf)),
  };
}
function removable<Input, View>(path: string) {
  return { ...editable<Input, View>(path), remove: (id: number, csrf: string) => requestVoid(adminPath(`${path}/${id}`), { method: "DELETE", headers: { "X-CSRF-TOKEN": csrf } }) };
}

export const adminCatalogApi = {
  brands: {
    ...editable<BrandInput, Brand>("/brands"),
    list: () => requestJson<{ brands: Brand[] }>(adminPath("/brands")).then((r) => r.brands),
    get: (id: number) => requestJson<Brand>(adminPath(`/brands/${id}`)),
  },
  categories: {
    ...editable<CategoryInput, Category>("/categories"),
    list: () => requestJson<{ categories: Category[] }>(adminPath("/categories")).then((r) => r.categories),
    get: (id: number) => requestJson<Category>(adminPath(`/categories/${id}`)),
  },
  products: {
    ...editable<ProductInput, Product, ProductPatch>("/products"),
    list: () => requestJson<{ products: Product[] }>(adminPath("/products")).then((r) => r.products),
    get: (id: number) => requestJson<Product>(adminPath(`/products/${id}`)),
  },
  skus: (productId: number) => ({
    ...editable<SkuInput, Sku, SkuPatch>(`/products/${productId}/skus`),
    list: () => requestJson<{ skus: Sku[] }>(adminPath(`/products/${productId}/skus`)).then((r) => r.skus),
    optionAssignment: (skuId: number) => requestJson<SkuOptionAssignment>(adminPath(`/products/${productId}/skus/${skuId}/option-values`)),
    assignOptions: (skuId: number, optionValueIds: number[], csrf: string) => requestJson<SkuOptionAssignment>(adminPath(`/products/${productId}/skus/${skuId}/option-values`), requestInit("PUT", { optionValueIds }, csrf)),
  }),
  images: (productId: number) => ({
    ...removable<ImageInput, CatalogImage>(`/products/${productId}/images`),
    list: () => requestJson<{ images: CatalogImage[] }>(adminPath(`/products/${productId}/images`)).then((r) => r.images),
  }),
  optionGroups: (productId: number) => ({
    ...removable<OptionGroupInput, OptionGroup>(`/products/${productId}/option-groups`),
    list: () => requestJson<{ optionGroups: OptionGroup[] }>(adminPath(`/products/${productId}/option-groups`)).then((r) => r.optionGroups),
  }),
  optionValues: (productId: number, groupId: number) => removable<OptionValueInput, OptionValue>(`/products/${productId}/option-groups/${groupId}/values`),
  facets: {
    ...removable<FacetInput, Facet>("/facets"),
    list: () => requestJson<{ facetDefinitions: Facet[] }>(adminPath("/facets")).then((r) => r.facetDefinitions),
    get: (id: number) => requestJson<Facet>(adminPath(`/facets/${id}`)),
  },
  facetOptions: (definitionId: number) => removable<OptionValueInput, FacetOption>(`/facets/${definitionId}/options`),
  assignCategoryFacet: (categoryId: number, definitionId: number, displayOrder: number, csrf: string) => requestJson<CategoryFacetAssignment>(adminPath(`/categories/${categoryId}/facets/${definitionId}`), requestInit("PUT", { displayOrder }, csrf)),
  removeCategoryFacet: (categoryId: number, definitionId: number, csrf: string) => requestVoid(adminPath(`/categories/${categoryId}/facets/${definitionId}`), requestInit("DELETE", undefined, csrf)),
  categoryFacets: (categoryId: number) => requestJson<CategoryFacetList>(adminPath(`/categories/${categoryId}/facets`)),
  productFacetAssignment: (productId: number) => requestJson<ProductFacetAssignment>(adminPath(`/products/${productId}/facet-values`)),
  assignProductFacets: (productId: number, facetOptionIds: number[], csrf: string) => requestJson<ProductFacetAssignment>(adminPath(`/products/${productId}/facet-values`), requestInit("PUT", { facetOptionIds }, csrf)),
  details: (productId: number) => ({
    ...removable<DetailInput, DetailSection>(`/products/${productId}/detail-sections`),
    list: () => requestJson<{ detailSections: DetailSection[] }>(adminPath(`/products/${productId}/detail-sections`)).then((r) => r.detailSections),
  }),
};
