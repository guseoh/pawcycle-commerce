import type { Category, OptionGroup, ProductStatus } from "./admin-catalog-api.ts";

export function dirtyPayload<T extends object>(before: T, after: Partial<T>, nullable: readonly (keyof T)[] = []): Partial<T> {
  const result: Partial<T> = {};
  for (const key of Object.keys(after) as (keyof T)[]) {
    const value = after[key];
    if (value === undefined || Object.is(before[key], value)) continue;
    if (value === null && !nullable.includes(key)) throw new Error(`${String(key)}은 해제할 수 없습니다.`);
    result[key] = value;
  }
  return result;
}

export function productStatusAction(status: ProductStatus): { status: ProductStatus; label: string } {
  return status === "PUBLIC" ? { status: "INACTIVE", label: "상품 비공개로 전환" } : { status: "PUBLIC", label: status === "DRAFT" ? "상품 공개" : "상품 다시 공개" };
}

export function categoryHierarchy(categories: Category[]): { category: Category; depth: number; label: string }[] {
  const ordered = [...categories].sort((a, b) => a.displayOrder - b.displayOrder || a.categoryId - b.categoryId);
  const roots = ordered.filter((c) => c.parentId === null);
  return roots.flatMap((root) => [
    { category: root, depth: 0, label: root.name },
    ...ordered.filter((c) => c.parentId === root.categoryId).map((child) => ({ category: child, depth: 1, label: `${root.name} / ${child.name}` })),
  ]);
}

export function categoryParents(categories: Category[], editingId?: number): Category[] {
  if (categories.some((c) => c.parentId === editingId)) return [];
  return categories.filter((c) => c.parentId === null && c.categoryId !== editingId);
}

export function normalizeMoney(value: string, nullable = false): number | null {
  const trimmed = value.trim();
  if (!trimmed && nullable) return null;
  if (!/^\d{1,10}(?:\.\d{1,2})?$/.test(trimmed)) throw new Error("0 이상, 소수점 둘째 자리까지 입력해 주세요.");
  return Number(trimmed);
}

export function optionAssignment(groups: OptionGroup[], selected: Record<number, string>): number[] {
  return groups.flatMap((group) => {
    const value = selected[group.optionGroupId];
    if (!value) return [];
    const id = Number(value);
    if (!group.values.some((option) => option.optionValueId === id)) throw new Error("현재 상품의 옵션 값을 선택해 주세요.");
    return [id];
  });
}

export interface Choice { value: string; label: string; disabled?: boolean }
export interface CatalogField<T extends object> {
  key: Extract<keyof T, string>; label: string; kind?: "text" | "textarea" | "number" | "money" | "checkbox" | "select";
  required?: boolean; nullable?: boolean; numeric?: boolean; readOnlyOnEdit?: boolean; maxLength?: number; choices?: Choice[]; help?: string;
}
export type FormValues = Record<string, string | boolean>;

export function formValues<T extends object>(value: T, fields: CatalogField<T>[]): FormValues {
  return Object.fromEntries(fields.map((field) => [field.key, field.kind === "checkbox" ? Boolean(value[field.key]) : String(value[field.key] ?? "")]));
}

export function dirtyFormPayload<T extends object>(baseline: T, raw: FormValues, parsed: T, fields: CatalogField<T>[]): Partial<T> {
  const original = formValues(baseline, fields);
  const changed: Partial<T> = {};
  for (const field of fields) {
    if (!field.readOnlyOnEdit && raw[field.key] !== original[field.key]) changed[field.key] = parsed[field.key];
  }
  return dirtyPayload(baseline, changed, fields.filter((f) => f.nullable).map((f) => f.key));
}

export function parseForm<T extends object>(values: FormValues, fields: CatalogField<T>[], editing: boolean): { value: T; errors: { field: string; message: string }[] } {
  const parsed: Record<string, unknown> = {};
  const errors: { field: string; message: string }[] = [];
  for (const field of fields) {
    if (editing && field.readOnlyOnEdit) continue;
    const raw = values[field.key];
    try {
      if (field.kind === "checkbox") parsed[field.key] = Boolean(raw);
      else {
        const text = String(raw ?? "");
        if (field.required && !text.trim()) throw new Error("필수 입력입니다.");
        if (field.maxLength && text.length > field.maxLength) throw new Error(`${field.maxLength}자 이하로 입력해 주세요.`);
        if (!text && field.nullable) parsed[field.key] = null;
        else if (field.kind === "money") parsed[field.key] = normalizeMoney(text, field.nullable);
        else if (field.kind === "number" || field.numeric) {
          if (!/^\d+$/.test(text) || !Number.isSafeInteger(Number(text))) throw new Error("0 이상의 정수를 입력해 주세요.");
          parsed[field.key] = Number(text);
        } else parsed[field.key] = text;
      }
    } catch (error) { errors.push({ field: field.key, message: (error as Error).message }); }
  }
  return { value: parsed as T, errors };
}
