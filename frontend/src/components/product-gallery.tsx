"use client";

import { useState } from "react";
import type { ProductDetail } from "@/lib/api";
import { CatalogImage } from "./catalog-product-card";

export function ProductGallery({ product }: { product: ProductDetail }) {
  const [selected, setSelected] = useState(0);
  const images = product.images.length ? product.images : product.thumbnailUrl ? [{ imageId: 0, imageUrl: product.thumbnailUrl, altText: product.name }] : [];
  const current = images[selected] ?? images[0];
  return <section className="catalog-gallery" aria-label="상품 이미지">
    <div className="product-gallery"><CatalogImage src={current?.imageUrl ?? null} alt={current?.altText || product.name} className="product-hero-image" eager /></div>
    {images.length > 1 ? <div className="gallery-thumbnails" aria-label="상품 이미지 선택">{images.map((image, index) => <button type="button" key={image.imageId} aria-pressed={index === selected} aria-label={`${index + 1}번 이미지: ${image.altText || product.name}`} onClick={() => setSelected(index)}><CatalogImage src={image.imageUrl} alt="" /><span>{index + 1}{index === selected ? " · 선택" : ""}</span></button>)}</div> : null}
  </section>;
}
