"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/async-state";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { buildLoginHref } from "@/lib/frontend-utils";
import { commerceFinalApi, type WishlistItem } from "@/lib/commerce-final-api";
export default function WishlistPage() { const auth=useAuth(); const [items,setItems]=useState<WishlistItem[]|null>(null); const [error,setError]=useState<string|null>(null); const load=()=>{void commerceFinalApi.wishlist().then((r)=>{setItems(r.items);setError(null);}).catch((e:unknown)=>setError(e instanceof ApiError?e.message:"위시리스트를 불러오지 못했습니다."));}; useEffect(()=>{if(auth.status==="anonymous") return; load();},[auth.status]); if(auth.status==="anonymous") return <ErrorState title="로그인이 필요합니다." message="위시리스트를 보려면 로그인해 주세요."><Link className="button button-primary" href={buildLoginHref("/wishlist")}>로그인</Link></ErrorState>; if(items===null&&!error)return <LoadingState>위시리스트를 불러오고 있습니다.</LoadingState>; if(error)return <ErrorState title="위시리스트를 불러오지 못했습니다." message={error} onRetry={load}/>; return <section className="section-card"><h1>위시리스트</h1>{items?.length?<ul className="history-list">{items.map((item)=><li key={item.productId}><Link href={`/products/${item.productId}`}>{item.productName}</Link><button className="button button-secondary" type="button" onClick={()=>void auth.executeWithCsrf((csrf)=>commerceFinalApi.deleteWishlist(item.productId,csrf)).then(load).catch((e:unknown)=>setError(e instanceof ApiError?e.message:"삭제하지 못했습니다."))}>삭제</button></li>)}</ul>:<p>저장한 상품이 없습니다.</p>}</section>; }
