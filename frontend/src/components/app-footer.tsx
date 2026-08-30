"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { OrbitMark } from "./orbit-mark";
const groups = [ ["쇼핑",[["/products","상품"],["/subscriptions","정기배송"]]], ["내 일상",[["/orders","주문 내역"],["/my","내 정보"]]], ["도움과 안내",[["/shipping","배송"],["/returns","교환·반품"],["/faq","FAQ"],["/notice","공지사항"]]] ] as const;
export function AppFooter({ compact = false }: { compact?: boolean }) {
 const [mobile,setMobile] = useState(false);
 useEffect(() => { const media=window.matchMedia("(max-width: 767px)"); const update=()=>setMobile(media.matches); update(); media.addEventListener("change",update); return ()=>media.removeEventListener("change",update); },[]);
 if(compact) return <footer className="site-footer compact-footer"><nav aria-label="로그인 도움"><Link href="/support">고객지원</Link><Link href="/shipping">배송 안내</Link><Link href="/returns">교환·반품</Link></nav></footer>;
 return <footer className="site-footer"><div className="footer-inner"><div className="footer-brand-copy"><Link className="brand" href="/"><OrbitMark /><strong>PawCycle</strong></Link><p>함께 사는 일상, 필요한 만큼.</p><Link className="footer-support" href="/support">도움이 필요하세요? 고객지원 →</Link></div><nav className="footer-navigation" aria-label="하단 메뉴">{groups.map(([title,links])=><details key={`${title}-${mobile}`} open={!mobile}><summary onClick={event=>{if(!mobile)event.preventDefault();}}>{title}</summary><div>{links.map(([href,label])=><Link href={href} key={href}>{label}</Link>)}</div></details>)}</nav></div></footer>;
}
