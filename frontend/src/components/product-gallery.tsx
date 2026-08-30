"use client";

import { useEffect, useRef, useState } from "react";
import type { ProductDetail } from "@/lib/api";
import { CatalogImage } from "./catalog-product-card";

export function ProductGallery({ product }: { product: ProductDetail }) {
  const [selected, setSelected] = useState(0);
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const images = product.images.length ? product.images : product.thumbnailUrl ? [{ imageId: 0, imageUrl: product.thumbnailUrl, altText: product.name }] : [];
  const current = images[selected] ?? images[0];

  useEffect(() => {
    if (!lightboxOpen) return;
    const dialog = dialogRef.current;
    if (!dialog) return;
    const opener = triggerRef.current;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();
    return () => { if (dialog.open) dialog.close(); document.body.style.overflow = previousOverflow; opener?.focus(); };
  }, [lightboxOpen]);

  const move = (delta: number) => setSelected((index) => (index + delta + images.length) % images.length);
  const image = <CatalogImage src={current?.imageUrl ?? null} alt={current?.altText || product.name} className="product-hero-image" eager />;
  return <section className="catalog-gallery" aria-label="상품 이미지">
    <div className="product-gallery">{current ? <button ref={triggerRef} className="product-gallery-trigger" type="button" aria-label={`${product.name} 이미지 크게 보기`} onClick={() => setLightboxOpen(true)}>{image}</button> : image}</div>
    {images.length > 1 ? <div className="gallery-thumbnails" aria-label="상품 이미지 선택">{images.map((item, index) => <button type="button" key={item.imageId} aria-pressed={index === selected} aria-label={`${index + 1}번째 이미지 보기${index === selected ? ", 선택됨" : ""}`} onClick={() => setSelected(index)}><CatalogImage src={item.imageUrl} alt="" /><span aria-hidden="true">{index + 1}</span></button>)}</div> : null}
    {lightboxOpen ? <dialog ref={dialogRef} className="gallery-lightbox" aria-label={`${product.name} 이미지 크게 보기`} onCancel={(event) => { event.preventDefault(); setLightboxOpen(false); }}>
      <CatalogImage src={current?.imageUrl ?? null} alt={current?.altText || product.name} />
      <div className="gallery-lightbox-actions">{images.length > 1 ? <><button className="button button-secondary" type="button" onClick={() => move(-1)}>이전 이미지</button><span role="status">{selected + 1} / {images.length}</span><button className="button button-secondary" type="button" onClick={() => move(1)}>다음 이미지</button></> : <span /> }<button ref={closeRef} className="button button-primary" type="button" onClick={() => setLightboxOpen(false)}>닫기</button></div>
    </dialog> : null}
  </section>;
}
