import { UnitInput } from "./unit-input";

export function QuantityInput({ id, value, onChange, disabled, max, invalid, describedBy }: { id: string; value: string; onChange: (value: string) => void; disabled: boolean; max?: number; invalid: boolean; describedBy?: string }) {
  const number = Number(value);
  const valid = value.trim() !== "" && Number.isInteger(number) && number >= 1;
  return <span className="quantity-control">
    <button className="quantity-step" type="button" aria-label="수량 감소" disabled={disabled || !valid || number <= 1} onClick={() => onChange(String(number - 1))}>−</button>
    <UnitInput unit="개" id={id} className="input" type="number" min="1" step="1" max={max} disabled={disabled} aria-invalid={invalid} aria-describedby={describedBy} value={value} onChange={event => onChange(event.target.value)} />
    <button className="quantity-step" type="button" aria-label="수량 증가" disabled={disabled || !valid || (max !== undefined && number >= max)} onClick={() => onChange(String(number + 1))}>+</button>
  </span>;
}
