package com.pawcycle.backend.catalog.discovery.api;

import com.pawcycle.backend.catalog.discovery.application.CatalogDiscoveryQueryService;
import com.pawcycle.backend.catalog.discovery.application.CatalogDiscoveryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogDiscoveryController {
  private final CatalogDiscoveryQueryService catalogDiscoveryQueryService;

  @GetMapping("/discovery")
  CatalogDiscoveryResponse discovery() {
    return catalogDiscoveryQueryService.findPublicDiscovery();
  }
}
