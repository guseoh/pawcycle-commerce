param(
    [string]$BaseUri = "http://localhost:8082"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$target = [uri]$BaseUri
if (-not $target.IsLoopback -or $target.Scheme -ne "http") {
    throw "Only a local HTTP QA stack is allowed."
}
$base = $BaseUri.TrimEnd("/")

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Get-PublicJson([string]$Path) {
    # Never print response bodies or credential-bearing request diagnostics.
    try { Invoke-RestMethod -Uri "$base$Path" -Method Get -TimeoutSec 30 -MaximumRedirection 0 }
    catch { throw "Public QA request failed: $Path" }
}

function Get-ProductIdSet([object]$Response) {
    $ids = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($item in @($Response.items)) {
        [void]$ids.Add([string]$item.productId)
    }
    return $ids
}

function Get-SetIntersection([System.Collections.Generic.HashSet[string]]$Left, [System.Collections.Generic.HashSet[string]]$Right) {
    $result = [System.Collections.Generic.HashSet[string]]::new($Left)
    $result.IntersectWith($Right)
    return $result
}

function Assert-SameSet([System.Collections.Generic.HashSet[string]]$Actual, [System.Collections.Generic.HashSet[string]]$Expected, [string]$Message) {
    Assert-Condition ($Actual.Count -eq $Expected.Count -and $Actual.SetEquals($Expected)) $Message
}

try { $frontend = Invoke-WebRequest -Uri "$base/" -UseBasicParsing -TimeoutSec 30 -MaximumRedirection 0 }
catch { throw "Frontend readiness request failed." }
Assert-Condition ($frontend.StatusCode -eq 200) "Frontend must return 200."

$discovery = Get-PublicJson "/api/catalog/discovery"
$categories = @($discovery.categories)
$children = @($categories | ForEach-Object { $_.children })
Assert-Condition ($categories.Count -eq 9 -and $children.Count -eq 18) "Expected V3 9 top-level and 18 child categories."
Assert-Condition (@($categories + $children | Where-Object { $_.slug -eq "__pawcycle_uncategorized__" }).Count -eq 0) "System category must not be public."
Assert-Condition (@($discovery.brands).Count -eq 10) "Expected 10 active brands."
Assert-Condition (@($discovery.categoryFacets).Count -gt 0) "Category facets are missing."

$products = Get-PublicJson "/api/products?size=100"
Assert-Condition ($products.totalElements -eq 100 -and @($products.items).Count -eq 100) "Clean mode requires exactly 100 products; use a fresh dedicated volume with auth bootstrap disabled."
foreach ($pet in @("DOG", "CAT")) {
    $result = Get-PublicJson "/api/products?petType=$pet"
    Assert-Condition ($result.totalElements -eq 50) "Expected 50 $pet products."
}
$categoryProducts = Get-PublicJson "/api/products?category=food&subcategory=food-dry"
Assert-Condition ($categoryProducts.totalElements -gt 0) "Category/subcategory filter returned no products."
Assert-Condition (@($categoryProducts.items | Where-Object { $_.category.slug -ne "food-dry" }).Count -eq 0) "Subcategory filter returned a different category."
$brandProducts = Get-PublicJson "/api/products?brand=grain-tail"
Assert-Condition ($brandProducts.totalElements -gt 0) "Brand filter returned no products."
Assert-Condition (@($brandProducts.items | Where-Object { $_.brand.slug -ne "grain-tail" }).Count -eq 0) "Brand filter returned a different brand."

# Choose real options from public discovery and verify repeated facet parameters as an exact intersection.
$foodFacets = @($discovery.categoryFacets | Where-Object { $_.categorySlug -eq "food-dry" })
Assert-Condition ($foodFacets.Count -eq 1) "Dry food facet assignment is missing."
$facetParts = @()
foreach ($choice in @(@{ Key = "protein"; Value = "연어" }, @{ Key = "life-stage"; Value = "성견" })) {
    $facet = @($foodFacets[0].facets | Where-Object { $_.key -eq $choice.Key })
    Assert-Condition ($facet.Count -eq 1) "Expected V3 facet is not allowed for dry food."
    $option = @($facet[0].options | Where-Object { $_.value -eq $choice.Value })
    Assert-Condition ($option.Count -eq 1) "Expected V3 option is missing from discovery."
    $facetParts += "facet=" + [uri]::EscapeDataString("$($facet[0].key):$($option[0].value)")
}
$baseFilter = "/api/products?category=food&subcategory=food-dry&size=100&"
$proteinOnly = Get-PublicJson ($baseFilter + $facetParts[0])
$lifeStageOnly = Get-PublicJson ($baseFilter + $facetParts[1])
$filtered = Get-PublicJson ($baseFilter + ($facetParts -join "&"))
$expectedFacetIds = Get-SetIntersection (Get-ProductIdSet $proteinOnly) (Get-ProductIdSet $lifeStageOnly)
$actualFacetIds = Get-ProductIdSet $filtered
Assert-Condition ($actualFacetIds.Count -gt 0) "Repeated facet query returned no products."
Assert-SameSet $actualFacetIds $expectedFacetIds "Repeated facet query did not match the exact intersection of both facet filters."

$representativeName = "스몰테일 연어 작은 알갱이"
$search = Get-PublicJson ("/api/products?q=" + [uri]::EscapeDataString($representativeName))
$matches = @($search.items | Where-Object { $_.name -eq $representativeName })
Assert-Condition ($matches.Count -eq 1) "Expected one representative product from public search."
$product = Get-PublicJson "/api/products/$($matches[0].productId)"
Assert-Condition ($null -ne $product.brand) "Representative product brand is missing."
Assert-Condition (@($product.images | Where-Object { $_.imageType -eq "MAIN" }).Count -eq 1) "Expected one MAIN image."
Assert-Condition (@($product.images | Where-Object { $_.imageType -eq "DETAIL" }).Count -eq 3) "Expected three DETAIL images."
Assert-Condition (@($product.optionGroups).Count -eq 2 -and @($product.skus).Count -eq 4) "Expected two option groups and four SKUs."
Assert-Condition (@($product.skus | Where-Object { @($_.selectedOptions).Count -ne 2 }).Count -eq 0) "Every representative SKU must have two selected options."
Assert-Condition (@($product.skus | Where-Object { $_.compareAtPrice -gt $_.price -and $_.discountRate -gt 0 }).Count -gt 0) "Discount state is missing."
Assert-Condition (@($product.skus | Where-Object { $_.purchasable }).Count -gt 0) "Purchasable SKU is missing."
Assert-Condition (@($product.skus | Where-Object { -not $_.purchasable -and $_.availableQuantity -eq 0 }).Count -gt 0) "Sold-out SKU is missing."
Assert-Condition (@($product.skus | Where-Object { $_.subscribable }).Count -gt 0) "Subscribable SKU is missing."
Assert-Condition (@($product.detailSections).Count -eq 3) "Expected three detail sections."

# Count the small disposable catalog through public detail APIs, without DB credentials.
$skuCount = 0
foreach ($item in $products.items) {
    $detail = if ($item.productId -eq $product.productId) { $product } else { Get-PublicJson "/api/products/$($item.productId)" }
    $skuCount += @($detail.skus).Count
}
Assert-Condition ($skuCount -eq 166) "Expected 166 public SKUs in clean V1 + V3."
Write-Output "MVP4-QA-002 clean API preflight passed: Product=100 DOG=50 CAT=50 Brand=10 Category=27 (9+18) SKU=$skuCount"
Write-Output "Representative detail: $base/products/$($product.productId) (2 groups / 4 SKUs / MAIN+3 DETAIL)"