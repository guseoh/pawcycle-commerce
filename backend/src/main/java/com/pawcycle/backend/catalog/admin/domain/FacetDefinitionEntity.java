package com.pawcycle.backend.catalog.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facet_definitions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FacetDefinitionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "`key`", nullable = false, unique = true, length = 100)
  private String key;

  @Column(nullable = false, length = 100)
  private String name;

  public FacetDefinitionEntity(String key, String name) {
    this.key = key;
    this.name = name;
  }

  public void update(String key, String name) {
    this.key = key;
    this.name = name;
  }
}
