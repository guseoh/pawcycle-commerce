"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "./async-state";
import { ApiError, type ProductDetail, productApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { commerceFinalApi } from "@/lib/commerce-final-api";
import { buildLoginHref, formatPetType, formatPrice } from "@/lib/frontend-utils";

type ProductState = { status:"loading" } | { status:"success"; product:ProductDetail } | { status:"not-found" } | { status:"error"; message:string };
export const CANONICAL_SUBSCRIPTION_START_HREF = "/subscriptions/new";

export function ProductDetailScreen({ productId }:{productId:string}) {
  const auth=useAuth(); const [state,setState]=useState<ProductState>({status:"loading"}); const [retry,setRetry]=useState(0);
  const [cartSkuId,setCartSkuId]=useState<number|null>(null); const [quantity,setQuantity]=useState("1"); const [busy,setBusy]=useState(false); const [message,setMessage]=useState<string|null>(null);
  useEffect(()=>{let active=true;void productApi.detail(productId).then(product=>{if(active)setState({status:"success",product});}).catch((error:unknown)=>{if(!active)return;if(error instanceof ApiError&&error.code==="PRODUCT_NOT_FOUND")setState({status:"not-found"});else setState({status:"error",message:error instanceof ApiError?error.message:"상품 정보를 불러오지 못했습니다."});});return()=>{active=false;};},[productId,retry]);
  const product=state.status==="success"?state.product:null;
  function canMutate(){if(auth.status==="anonymous"&&product){location.assign(buildLoginHref(`/products/${product.productId}`));return false;}if(auth.status!=="authenticated"){setMessage(auth.status==="loading"?"로그인 상태를 확인하고 있습니다. 잠시 후 다시 시도해 주세요.":"로그인 상태를 확인하지 못했습니다. 다시 확인한 뒤 시도해 주세요.");return false;}return true;}
  async function addWishlist(){if(!product||busy||!canMutate())return;setBusy(true);setMessage(null);try{await auth.executeWithCsrf(csrf=>commerceFinalApi.addWishlist(product.productId,csrf));setMessage("위시리스트에 담았습니다.");}catch(error){setMessage(error instanceof ApiError?error.message:"위시리스트에 담지 못했습니다.");}finally{setBusy(false);}}
  async function addCart(){if(!product||!cartSkuId||busy){setMessage("일반 구매 옵션을 선택해 주세요.");return;}if(!canMutate())return;const parsed=Number(quantity);if(!Number.isInteger(parsed)||parsed<1){setMessage("수량을 확인해 주세요.");return;}setBusy(true);setMessage(null);try{await auth.executeWithCsrf(csrf=>commerceFinalApi.addCart(cartSkuId,parsed,csrf));setMessage("장바구니에 담았습니다.");}catch(error){setMessage(error instanceof ApiError?error.message:"장바구니에 담지 못했습니다.");}finally{setBusy(false);}}
  if(state.status==="loading")return <LoadingState>상품 정보를 불러오고 있습니다.</LoadingState>;
  if(state.status==="not-found")return <ErrorState title="상품을 확인할 수 없습니다." message="존재하지 않거나 공개되지 않은 상품입니다."><Link className="button button-secondary" href="/products">상품 목록으로</Link></ErrorState>;
  if(state.status==="error")return <ErrorState title="상품을 불러오지 못했습니다." message={state.message} onRetry={()=>{setState({status:"loading"});setRetry(value=>value+1);}}><Link className="button button-secondary" href="/products">상품 목록으로</Link></ErrorState>;
  return <><Link className="breadcrumb" href="/products">← 상품 목록</Link><div className="detail-layout"><section className="section-card"><p className="eyebrow">Product #{product!.productId}</p><h1>{product!.name}</h1><p className="tag">대상: {formatPetType(product!.petType)}</p><p className="tag">카테고리: {product!.category.name}</p><p className="description">{product!.description??"상세 설명이 준비되지 않았습니다."}</p>{product!.thumbnailUrl?<img className="product-thumbnail" src={product!.thumbnailUrl} alt=""/>:null}</section><section className="section-card"><h2>일반 구매</h2>{message?<p className="field-help" role="status">{message}</p>:null}<label className="form-field">일반 구매 옵션<select className="input" value={cartSkuId??""} onChange={event=>setCartSkuId(Number(event.target.value)||null)}><option value="">선택하세요</option>{product!.skus.map(sku=><option key={sku.skuId} value={sku.skuId}>{sku.skuName} · {formatPrice(sku.price)}</option>)}</select></label><label className="form-field">수량<input className="input" type="number" min="1" value={quantity} onChange={event=>setQuantity(event.target.value)}/></label><div className="button-row"><button className="button button-secondary" type="button" disabled={busy} onClick={()=>void addWishlist()}>위시리스트에 담기</button><button className="button button-primary" type="button" disabled={busy||cartSkuId===null} onClick={()=>void addCart()}>장바구니에 담기</button></div></section><section className="section-card"><h2>정기배송</h2>{product!.skus.some(sku=>sku.subscribable)?<><p>정기배송은 반려동물과 플랜을 선택해 시작합니다.</p><Link className="button button-secondary" href={CANONICAL_SUBSCRIPTION_START_HREF}>정기배송 시작</Link></>:<p>현재 정기배송 가능한 옵션이 없습니다.</p>}</section></div></>;
}
