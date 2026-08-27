package com.pawcycle.backend.catalog.brand.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Brand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, unique = true, length = 100) private String slug;
    @Column(name = "logo_url", length = 2048) private String logoUrl;
    @Column(nullable = false) private boolean active;
    @Column(name = "display_order", nullable = false) private int displayOrder;

    public Brand(String name, String slug, String logoUrl, boolean active, int displayOrder) {
        this.name = name; this.slug = slug; this.logoUrl = logoUrl; this.active = active; this.displayOrder = displayOrder;
    }

    public void update(String name, String slug, String logoUrl, boolean logoUrlPresent, Boolean active, Integer displayOrder) {
        if (name != null) this.name = name;
        if (slug != null) this.slug = slug;
        if (logoUrlPresent) this.logoUrl = logoUrl;
        if (active != null) this.active = active;
        if (displayOrder != null) this.displayOrder = displayOrder;
    }
}
