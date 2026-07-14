"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { ErrorState, LoadingState } from "./async-state";
import {
  ApiError,
  ProductDetail,
  productApi,
  subscriptionApi,
} from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import {
  buildLoginHref,
  formatPetType,
  formatPrice,
  SubscriptionDraftErrors,
  validateSubscriptionDraft,
} from "@/lib/frontend-utils";

type ProductState =
  | { status: "loading" }
  | { status: "success"; product: ProductDetail }
  | { status: "not-found" }
  | { status: "error"; message: string };

export function ProductDetailScreen({ productId }: { productId: string }) {
  const router = useRouter();
  const auth = useAuth();
  const [retryKey, setRetryKey] = useState(0);
  const [state, setState] = useState<ProductState>({ status: "loading" });
  const [selectedSkuId, setSelectedSkuId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState("1");
  const [deliveryCycleWeeks, setDeliveryCycleWeeks] = useState<number | null>(null);
  const [errors, setErrors] = useState<SubscriptionDraftErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const errorSummaryRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    productApi
      .detail(productId)
      .then((product) => {
        if (active) setState({ status: "success", product });
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.code === "PRODUCT_NOT_FOUND") {
          setState({ status: "not-found" });
        } else {
          setState({
            status: "error",
            message:
              error instanceof ApiError
                ? error.message
                : "상품 정보를 불러오지 못했습니다.",
          });
        }
      });
    return () => {
      active = false;
    };
  }, [productId, retryKey]);

  const loadedProduct = state.status === "success" ? state.product : null;
  const selectableSkus = useMemo(
    () => loadedProduct?.skus.filter((sku) => sku.subscribable) ?? [],
    [loadedProduct],
  );
  const selectedSku = loadedProduct?.skus.find((sku) => sku.skuId === selectedSkuId) ?? null;
  const cycles = selectedSku?.availableDeliveryCycles ?? [];

  function focusErrors() {
    requestAnimationFrame(() => errorSummaryRef.current?.focus());
  }

  function applyServerFieldErrors(error: ApiError): boolean {
    const nextErrors: SubscriptionDraftErrors = {};
    for (const fieldError of error.fieldErrors) {
      if (
        fieldError.field === "skuId" ||
        fieldError.field === "quantity" ||
        fieldError.field === "deliveryCycleWeeks"
      ) {
        nextErrors[fieldError.field] = fieldError.message;
      }
    }
    if (Object.keys(nextErrors).length === 0) return false;
    setErrors(nextErrors);
    focusErrors();
    return true;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!loadedProduct || submitting) return;

    const nextErrors = validateSubscriptionDraft(
      { skuId: selectedSkuId, quantity, deliveryCycleWeeks },
      selectableSkus.map((sku) => sku.skuId),
      cycles,
    );
    setErrors(nextErrors);
    setSubmitError(null);
    if (Object.keys(nextErrors).length > 0) {
      focusErrors();
      return;
    }

    const returnPath = `/products/${loadedProduct.productId}`;
    if (auth.status === "anonymous") {
      router.push(buildLoginHref(returnPath));
      return;
    }
    if (auth.status !== "authenticated") {
      setSubmitError("로그인 상태를 확인한 뒤 다시 시도해 주세요.");
      focusErrors();
      return;
    }

    setSubmitting(true);
    try {
      const csrfToken = auth.csrfToken ?? (await auth.refreshCsrf());
      const response = await subscriptionApi.create(
        {
          skuId: selectedSkuId as number,
          quantity: Number(quantity),
          deliveryCycleWeeks: deliveryCycleWeeks as number,
        },
        csrfToken,
      );
      router.push(`/subscriptions/${response.subscriptionId}?created=1`);
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === "AUTH_REQUIRED") {
          auth.markAnonymous();
          router.push(buildLoginHref(returnPath));
          return;
        }
        if (error.code === "CSRF_INVALID") {
          await auth.refreshCsrf().catch(() => null);
          setSubmitError("보안 정보를 새로 받았습니다. 내용을 확인한 뒤 구독 만들기를 다시 눌러 주세요.");
        } else if (error.code === "SKU_NOT_FOUND" || error.code === "SKU_NOT_SUBSCRIBABLE") {
          setSelectedSkuId(null);
          setDeliveryCycleWeeks(null);
          setSubmitError("선택한 옵션을 구독할 수 없습니다. 상품 정보를 새로 확인해 주세요.");
          setRetryKey((value) => value + 1);
        } else if (!applyServerFieldErrors(error)) {
          setSubmitError(error.message || "구독을 만들지 못했습니다.");
        }
      } else {
        setSubmitError("구독을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
      focusErrors();
    } finally {
      setSubmitting(false);
    }
  }

  if (state.status === "loading") {
    return <LoadingState>상품 정보를 불러오고 있습니다.</LoadingState>;
  }

  if (state.status === "not-found") {
    return (
      <ErrorState title="상품을 확인할 수 없습니다." message="존재하지 않거나 공개되지 않은 상품입니다.">
        <Link className="button button-secondary" href="/products">상품 목록으로</Link>
      </ErrorState>
    );
  }

  if (state.status === "error") {
    return (
      <ErrorState
        title="상품 정보를 불러오지 못했습니다."
        message={state.message}
        onRetry={() => {
          setState({ status: "loading" });
          setRetryKey((value) => value + 1);
        }}
      >
        <Link className="button button-secondary" href="/products">상품 목록으로</Link>
      </ErrorState>
    );
  }

  const product = state.product;

  return (
    <>
      <Link className="breadcrumb" href="/products">← 상품 목록</Link>
      <div className="detail-layout">
        <section className="section-card" aria-labelledby="product-title">
          <p className="eyebrow">Product #{product.productId}</p>
          <h1 id="product-title">{product.name}</h1>
          <p className="tag">대상: {formatPetType(product.petType)}</p>
          <p className="description">{product.description ?? "상세 설명이 준비되지 않았습니다."}</p>
          {product.thumbnailUrl ? (
            <a className="image-link" href={product.thumbnailUrl} target="_blank" rel="noreferrer">
              대표 이미지 보기
            </a>
          ) : null}
        </section>

        <form className="section-card" onSubmit={handleSubmit} noValidate>
          <p className="eyebrow">Subscription setup</p>
          <h2>정기배송 옵션 선택</h2>

          {(Object.values(errors).some(Boolean) || submitError) ? (
            <div className="error-summary" ref={errorSummaryRef} tabIndex={-1} role="alert">
              <h2>입력 내용을 확인해 주세요.</h2>
              {submitError ? <p>{submitError}</p> : null}
              <ul>
                {Object.entries(errors)
                  .filter((entry): entry is [string, string] => Boolean(entry[1]))
                  .map(([field, message]) => <li key={field}>{message}</li>)}
              </ul>
            </div>
          ) : null}

          <fieldset
            className="form-section"
            disabled={submitting}
            aria-describedby={errors.skuId ? "sku-error" : undefined}
          >
            <legend>옵션 선택</legend>
            {product.skus.length === 0 ? <p>선택할 SKU가 없습니다.</p> : null}
            <div className="radio-grid">
              {product.skus.map((sku) => (
                <label className="radio-card" key={sku.skuId}>
                  <span>
                    <input
                      type="radio"
                      name="skuId"
                      value={sku.skuId}
                      checked={selectedSkuId === sku.skuId}
                      disabled={submitting || !sku.subscribable}
                      onChange={() => {
                        setSelectedSkuId(sku.skuId);
                        setDeliveryCycleWeeks(null);
                        setErrors((current) => ({ ...current, skuId: undefined, deliveryCycleWeeks: undefined }));
                      }}
                    />
                    {sku.skuName}
                  </span>
                  <strong>SKU 표시 가격 {formatPrice(sku.price)}</strong>
                  <small>{sku.subscribable ? "구독 가능" : "현재 구독 대상으로 사용할 수 없습니다."}</small>
                </label>
              ))}
            </div>
            {errors.skuId ? <p className="field-error" id="sku-error">{errors.skuId}</p> : null}
          </fieldset>

          <div className="form-section">
            <label className="field-label" htmlFor="quantity">수량</label>
            <input
              className="input"
              id="quantity"
              name="quantity"
              type="number"
              inputMode="numeric"
              min="1"
              max="10"
              step="1"
              value={quantity}
              disabled={submitting}
              aria-invalid={Boolean(errors.quantity)}
              aria-describedby={errors.quantity ? "quantity-help quantity-error" : "quantity-help"}
              onChange={(event) => {
                setQuantity(event.target.value);
                setErrors((current) => ({ ...current, quantity: undefined }));
              }}
            />
            <p className="field-help" id="quantity-help">1개부터 10개까지 입력할 수 있습니다.</p>
            {errors.quantity ? <p className="field-error" id="quantity-error">{errors.quantity}</p> : null}
          </div>

          <fieldset
            className="form-section"
            disabled={submitting || !selectedSku}
            aria-describedby={errors.deliveryCycleWeeks ? "cycle-error" : undefined}
          >
            <legend>배송 주기</legend>
            <div className="cycle-row">
              {cycles.map((cycle) => (
                <label className="cycle-option" key={cycle}>
                  <input
                    type="radio"
                    name="deliveryCycleWeeks"
                    value={cycle}
                    checked={deliveryCycleWeeks === cycle}
                    onChange={() => {
                      setDeliveryCycleWeeks(cycle);
                      setErrors((current) => ({ ...current, deliveryCycleWeeks: undefined }));
                    }}
                  />
                  {cycle}주
                </label>
              ))}
            </div>
            {!selectedSku ? <p className="field-help">옵션을 먼저 선택해 주세요.</p> : null}
            {selectedSku && cycles.length === 0 ? <p className="field-help">선택 가능한 배송 주기가 없습니다.</p> : null}
            {errors.deliveryCycleWeeks ? (
              <p className="field-error" id="cycle-error">{errors.deliveryCycleWeeks}</p>
            ) : null}
          </fieldset>

          <aside className="review-panel" aria-labelledby="review-title">
            <h2 id="review-title">입력 검토</h2>
            {!selectedSku ? (
              <p>옵션을 선택하면 구독 내용을 확인할 수 있습니다.</p>
            ) : (
              <dl className="review-list">
                <dt>상품</dt><dd>{product.name}</dd>
                <dt>SKU</dt><dd>{selectedSku.skuName}</dd>
                <dt>SKU 표시 가격</dt><dd>{formatPrice(selectedSku.price)}</dd>
                <dt>수량</dt><dd>{quantity || "미입력"}</dd>
                <dt>배송 주기</dt><dd>{deliveryCycleWeeks ? `${deliveryCycleWeeks}주마다` : "미선택"}</dd>
              </dl>
            )}
            <p className="field-help">다음 주문 예정일은 구독을 만든 뒤 확인할 수 있습니다.</p>
            <button className="button button-primary" type="submit" disabled={submitting}>
              {submitting ? "구독을 만들고 있습니다." : "구독 만들기"}
            </button>
          </aside>
        </form>
      </div>
    </>
  );
}
