import type { Pet } from "./subscription-api.ts";

export interface PetDraft {
  name: string;
  breed: string;
  weightKg: string;
}

export type PetProfileLoadState = "auth-loading" | "loading" | "ready" | "error";

export function petProfileLoadState(authStatus: string, pets: Pet[] | null, error: string | null): PetProfileLoadState {
  if (authStatus === "loading") return "auth-loading";
  if (error) return "error";
  return pets === null ? "loading" : "ready";
}

export function petDraft(pet: Pet): PetDraft {
  return { name: pet.name, breed: pet.breed ?? "", weightKg: pet.weightKg === null ? "" : String(pet.weightKg) };
}

export function petWeightError(value: string): string | null {
  if (value.trim() === "") return null;
  const weightKg = Number(value);
  if (!Number.isFinite(weightKg) || weightKg <= 0 || weightKg > 200) return "몸무게는 0보다 크고 200kg 이하의 숫자로 입력해 주세요.";
  return null;
}

export function petPatch(initial: Pet, draft: PetDraft): Partial<Pick<Pet, "name" | "breed" | "weightKg">> {
  const patch: Partial<Pick<Pet, "name" | "breed" | "weightKg">> = {};
  const name = draft.name.trim();
  const breed = draft.breed.trim() || null;
  const weightKg = draft.weightKg.trim() === "" ? null : Number(draft.weightKg);
  if (name !== initial.name) patch.name = name;
  if (breed !== initial.breed) patch.breed = breed;
  if (petWeightError(draft.weightKg) === null && weightKg !== initial.weightKg) patch.weightKg = weightKg;
  return patch;
}
