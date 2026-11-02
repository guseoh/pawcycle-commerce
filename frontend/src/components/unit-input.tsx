import type { InputHTMLAttributes } from "react";

/** Keeps a unit next to the editable value without changing its submitted value. */
export function UnitInput({ unit, ...props }: InputHTMLAttributes<HTMLInputElement> & { unit: string }) {
  return <span className="unit-input"><input {...props} /><span className="unit-input-unit" aria-hidden="true">{unit}</span></span>;
}
