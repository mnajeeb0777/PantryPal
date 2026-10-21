# PantryPal provider feasibility

Initially checked October 14, 2026; grocery feasibility rechecked October 19–21.
TheMealDB import, saved drafts, and the first SerpApi Walmart estimate workflow
are implemented. The October 19 terms review changes the earlier Kroger
recommendation: cross-retailer comparison is prohibited under its public API
acceptable-use rules.

**Current scope decision (October 21):** the user chose Walmart-only Broke Mode
for now. Other-chain research below is historical context and optional future
work, not a requirement for the current release. See the
[acceptance walkthrough](ACCEPTANCE_WALKTHROUGH.md) for the completed app checks.

## Recommendation

- Use **TheMealDB** for this educational project. Its
  [terms](https://www.themealdb.com/terms_of_use.php) explicitly allow copying and
  modifying content returned through official API endpoints. Preserve copyright
  notices and attribution; third-party content restrictions still apply. Its
  [documentation](https://www.themealdb.com/documentation) supplies public key `1`
  for development and education. Revisit supporter access before production or
  app-store distribution. The implementation imports text, not artwork.
- Do not use **Spoonacular** for persistent full recipes without appropriate
  permission. Its [terms](https://spoonacular.com/food-api/terms) restrict storage
  (with limited ID/title/image-URL exceptions), and its one-hour caching rules
  do not fit PantryPal's saved offline ingredient/instruction workflow.
- **Prioritize supermarket giants: Walmart, Kroger, and Food Lion.**
  This October 21 preference supersedes choosing stores by Open Prices coverage.
  The October 20 audit found only 39, 2, and 1 recent observations respectively
  for these three. Open Prices is a candidate supplementary source; useful
  primary coverage for this scope remains unproven. Do not add smaller/local
  chains just to fill gaps. Continue targeted provider research before integration.
  Read the
  [national coverage audit and implementation order](OPEN_PRICES_FEASIBILITY.md).
  Open Prices public reads worked without credentials. Preserve actual store location,
  observation date, source attribution, and unknown availability.
- **Walmart via SerpApi passed an initial live technical test on October 21.**
  All 15 grocery searches returned relevant Walmart-sold candidates after manual
  review. The Walmart-only estimate prototype is now implemented, and explicit
  store 1852 searches were verified. Intended public display/storage rights still
  require clarification. This does not establish Kroger/Food Lion coverage. See the
  [live Walmart test](WALMART_SERPAPI_FEASIBILITY.md) for results and limitations.
- The user prefers automatic retrieval and does not want local price collection.
  Do not make a team-maintained price catalog or required user price entry the
  fallback. Show missing data explicitly. Kroger and Instacart restrictions
  remain relevant to their own APIs; neither is the selected comparison source.
- Before declaring either integration ready, validate actual responses with our
  models. In particular, ambiguous quantities must not silently become one unit,
  and unavailable prices or stock information must not become zero/free/in-stock.

## Recipe evidence

### TheMealDB: live public calls verified

The official [API guide](https://www.themealdb.com/api.php) permits its test key
`1` for development and education; public app-store releases require supporter
access. Actual HTTPS GET requests returned:

| Request | Result |
| --- | --- |
| [Search Arrabiata](https://www.themealdb.com/api/json/v1/1/search.php?s=Arrabiata) | HTTP 200; recipe ID `52771`, title, instructions, and eight ingredient/measure pairs. |
| [Fetch recipe 52771](https://www.themealdb.com/api/json/v1/1/lookup.php?i=52771) | HTTP 200; matching recipe details. |
| [Unmatched search](https://www.themealdb.com/api/json/v1/1/search.php?s=pantrypal_no_such_recipe_834957) | HTTP 200 with `meals: null`; map to an empty result list. |

The sample contains fractions and measures such as `1 tin` and `sprinkling`.
It has no prep/cook-time fields, and its source URL is null. Therefore use unknown
times/effort and an empty optional source URL. A complete import requires a
review/correction step for unsupported quantities, or a clear rejection of the
whole recipe. Never omit requirements and then claim the recipe is cookable.

### Spoonacular: structured data, rejected for this storage workflow

The official [recipe API documentation](https://spoonacular.com/food-api/docs)
provides name search and recipe-information endpoints. Its sample has ingredient
names, numeric amounts and units, source identity, preparation/cooking times, and
instruction fields. However, the documented example also contains unsupported
units and empty instructions, so normalization and missing-data handling remain
necessary. Total ready time must not be substituted for cooking time.

A historical unauthenticated request to
`https://api.spoonacular.com/recipes/complexSearch?query=pasta&number=1`
returned HTTP 401. No API key was supplied and no paid tier was purchased.
Subsequent terms review ruled it out for this increment despite its structured
measurements.

### Implemented TheMealDB workflow and live validation (October 18)

`RecipeImporter` uses Java HttpClient with connection/request timeouts and an
injectable transport. Search returns full immutable `RecipeImportDraft` objects;
selecting a search result does not make a second request. A separate lookup method
is available by ID. Bounded JSON decoding inside `RecipeImporter` avoids new
course dependencies. Measurement parsing lives in `RecipeImportDraft`. The
integration adds only that draft class and `RecipeImportPanel` to application
code; separate JSON and measurement utility classes were consolidated.
Ambiguous measurements remain visible and must be corrected before saving.
Times remain null when unknown, effort defaults to UNKNOWN, and no ingredient is
dropped. Namespaced external IDs and deterministic UUIDs prevent reimports from
replacing a reviewed recipe. The October 19 Save for later increment persists
unfinished imports and partial review edits in save format version 2, while
continuing to load version 1. Drafts can reopen offline, but remain outside pantry
calculations and cooking until completed. Promotion saves the validated recipe
and removes its draft together. No additional application classes were added.

The actual Java importer completed six live requests using the educational key:

| Request | Recipes | Ingredient rows | Rows needing review |
| --- | ---: | ---: | ---: |
| Search Arrabiata | 1 | 8 | 4 |
| Search Chicken | 25 | 309 | 114 |
| Search Curry | 18 | 225 | 117 |
| Search Pancakes | 7 | 58 | 22 |
| Deliberately nonexistent name | 0 | 0 | 0 |
| Lookup 52771 | Spicy Arrabiata Penne | — | — |

Counts are observed responses, not catalog-size or completeness guarantees.
Automated tests use controlled JSON and a socket-free HTTP stub; they do not
depend on provider availability or write into a personal save file.

## Grocery evidence

### Kroger: good technical fit, restricted product scope

The official [Products API](https://developer.kroger.com/api-products/api/product-api-public)
was inspected in the browser because its content loads through JavaScript.
Version 1.3.0 documents store-specific prices via `filter.locationId`. Its response
example includes regular/promotional prices, package size, selling basis, and an
inventory stock level. In-store fulfillment does **not** establish that an item
is in stock; inventory can be omitted.

The [Locations API](https://developer.kroger.com/api-products/api/location-api-public)
supports ZIP-code and radius search, plus store address and geographic data.
The [quick start](https://developer.kroger.com/documentation/public/getting-started/quick-start)
requires developer account/application registration and client credentials for
an access token. A real unauthenticated location request for ZIP `24060` returned
HTTP 401. No credentials were supplied, so current nearby stores/prices have
**not** been verified. The published sample is evidence of the schema, not a
current grocery quote.

On October 19, the rendered Products API documentation still listed 10,000 calls
per day. However, the [acceptable-use rules](https://developer.kroger.com/documentation/public/getting-started/acceptable-use)
explicitly prohibit comparing products or prices with other retailers. They also
require preserving displayed product/store data and forbid systematic response
collection into a database. Consequently, obtaining credentials alone does not
make Kroger suitable for the originally planned cross-retailer comparison.
Retailer-specific lookup is a possible reduced scope; comparison across Kroger
banners/locations and derived cost displays should be clarified before relying
on them. Keep computed purchase estimates separate from original offer prices.
No public per-call price was established from the pages reviewed.

### Instacart: useful shopping links, unsuitable standard comparison terms

The documented [shopping-list endpoint](https://docs.instacart.com/developer_platform_api/api/products/create_shopping_list_page)
creates a link to an Instacart shopping page. The [nearby-retailers endpoint](https://docs.instacart.com/developer_platform_api/api/retailers/get_nearby_retailers/)
returns retailer identities, not individual store addresses and priced offers.
These are not sufficient to populate PantryPal's comparison table.

More decisively, [section 3.5(k)(iv) of the developer terms](https://docs.instacart.com/developer_platform_api/guide/terms_and_policies/developer_terms/)
prohibits displaying priced items from multiple retailers on the same screen;
section 3.5(l) restricts comparisons/transfers between retailer baskets.
Do not select the standard offering for Broke Mode. A shopping-list handoff would
be a different feature. [Production access requires review](https://docs.instacart.com/developer_platform_api/guide/concepts/launch_activities/approval_process/),
and its [getting-started guide](https://docs.instacart.com/developer_platform_api/get_started/overview/)
estimates roughly 30-40 days from access request to production approval. No
account, special agreement, or price-data access has been obtained.

### Walmart official OPD APIs: documented capability, access not established

Walmart's [OPD introduction](https://www.walmart.io/docs/opd/stores/grocery-introduction)
describes localized product prices and availability, with store-location and
pricing services. It has a Request Access workflow. Its [onboarding page](https://www.walmart.io/onboarding)
describes affiliate/developer registration. The currently displayed
[Walmart I/O terms](https://www.walmart.io/termsandcondition), dated September 30,
2020, limit standard API use to affiliate advertising of Walmart.com products;
other purposes require express written permission. The legacy wording and OPD
eligibility need clarification from Walmart. Treat this as an approval-dependent
candidate, not an immediately usable student API. Pricing and current OPD-specific
quotas were not established; no authenticated request was made.

### Open Prices: open access works, local coverage does not

[Open Prices](https://openfoodfacts.github.io/open-prices/) records community
observations tied to products, shops, dates, and evidence. Its
[data guide](https://openfoodfacts.github.io/open-prices/guides/data/) describes
ODbL attribution/share-alike obligations. It is a source of observed prices,
not a live stock feed. Product/package information can be incomplete.

Read-only requests to the [live API](https://prices.openfoodfacts.org/api/docs)
succeeded without credentials on October 19, 2026. Filters were checked against
the live `/api/schema?format=json` schema before use:

| Request under `https://prices.openfoodfacts.org` | Observed result |
| --- | --- |
| `/api/v1/locations?osm_address_city__like=Blacksburg&size=5` | 0 locations |
| `/api/v1/locations?osm_address_city__like=Christiansburg&size=5` | 0 locations |
| `/api/v1/locations?osm_address_city__like=Roanoke&size=5` | 0 locations |
| `/api/v1/prices?lat=37.23&lon=-80.41&radius_km=50&size=1` | 0 price observations |
| `/api/v1/prices?currency=USD&size=1&order_by=-date` | Successful populated response; 44,052 USD observations total |

The coordinate is an approximate public Blacksburg reference point, not a user
location. The radius query avoids relying solely on city-name metadata. Counts
are snapshots, not coverage guarantees. Open Prices could support a future
community price catalog, but cannot currently supply our local demo area from
these results. No prices or receipt images were uploaded.

### SerpApi: practical small experiment, scraping dependency and limited budget

The [Walmart search API](https://serpapi.com/walmart-search-api) accepts a store
ID and returns structured search results containing offers. It extracts Walmart
pages rather than providing an official retailer integration. Its default
response cache can be up to one hour old. Seller filtering and conservative
package parsing are now implemented and fixture-tested. Checkout fulfillment
and shelf-price accuracy are not independently verified.

The [pricing page](https://serpapi.com/pricing) currently lists 250 free searches
per month (50/hour), then $25/month for 1,000 searches. The free tier is enough
for a bounded proof of concept, not frequent whole-recipe comparisons: ten
ingredients across three stores consume approximately 30 searches before any
extra detail requests, giving about eight such comparisons from 250 searches.
This is a planning estimate, not a measured request count. Automatic early
renewal is an available billing setting; do not enable it for a capped trial.

Update October 21: a user-supplied key was tested on the Free Plan; all 15 grocery
searches succeeded. See the [live Walmart audit](WALMART_SERPAPI_FEASIBILITY.md).
Store-page changes and data-use rights remain dependencies. A scraping service
subscription does not itself establish retailer authorization. The sample
now supports the implemented course prototype. Additional explicit-store checks
verified 1852 (Lake Ridge, VA); intended public display/storage rights remain
unresolved.

### ShopSavvy and managed grocery feeds: possible paid routes

[ShopSavvy pricing](https://shopsavvy.com/pricing) lists a $49/month REST Data API
starter plan with 1,000 credits and a 14-day trial, but no permanent free REST
tier. All-retailer offers cost three credits per matched product. Its
[offer schema](https://shopsavvy.com/data/documentation/offers-all-retailers)
contains retailer, price, optional availability, URL, and observation timestamp;
the reviewed endpoint does not document a ZIP/radius filter or physical store
address. Therefore local grocery suitability is unverified. Its free MCP
offering should not be confused with the REST plan. The [B2B terms](https://shopsavvy.com/terms/api)
permit displaying/storing data inside customer applications, with restrictions
on bulk redistribution and competing data services.

[FoodDataScrape](https://www.fooddatascrape.com/grocery-pricing-api-for-apps.php)
advertises US grocery prices by ZIP/store, package matching, and a free
1,000-record pilot; ongoing cost is quoted by scope. These are vendor claims,
not validated coverage. Require a sample for the project area, exact store IDs,
timestamps, package quantities, total cost, and contractual display/storage
rights before selecting it. No vendor was contacted or trial started.

Other discovery leads (Nourish and Open Price Engine) could not be reliably
retrieved during this review and are not validated candidates. Nutritional
databases and national average-price datasets would not establish which nearby
store has the cheapest available package.

### Recommended decision and acceptance check

The requested scope now considers supermarket giants without a proximity
requirement or manual price collection. The October 21 live SerpApi test makes
a Walmart-only estimate prototype the strongest tested initial direction;
that prototype is now implemented and supersedes leading with Open Prices. The test
returned relevant candidates for all 15 ingredient phrases, but unscoped calls
used different stores. Explicit-store checks subsequently verified store 1852, now used by the implemented
prototype. Resolve intended display/storage use before public distribution.

The [Open Prices audit](OPEN_PRICES_FEASIBILITY.md) checked 14 chains and 12
ingredient categories, plus alternate name searches. It supports a limited
recorded-price browser across the broader sample, but does not establish useful
coverage for the October 21 priority list of Walmart, Kroger, and Food Lion.
Only 527 of 1,099 sampled observations had positive numeric package
quantities with units, and some of those measurements were contradictory.

Unknown price is never zero; unknown stock is never assumed available. Any
ranking must say "lowest among checked offers," retain observation dates and
store cities, and use sufficient whole packages where quantities are reliable.
Incomplete baskets must remain visibly incomplete.

## Implementation implications and next checks

1. Resolve provider permissions and product scope before requesting credentials.
   For any chosen service, supply credentials locally through environment
   variables; do not put secrets in source code or recipe save files. A desktop
   app distributed to others must not bundle a shared secret; use individual
   credentials or a backend if distribution requires shared API access.
2. Review the completed recipe correction screen with the team, including food
   forms and package-size corrections. Live search/lookup, offline save/reopen,
   supported fractions, uncertain quantities, duplicates, and failure recovery
   have been checked; user-supplied quantities still need human judgment.
3. For pricing, retrieve US store IDs grouped by selected chains, then query
   recent USD observations. ZIP/radius filtering is outside the requested first
   version. Verify food form, package size, price basis, and observation date;
   preserve unknown stock. Parse simple package units and reject ambiguous
   multipacks, conflicting metadata, or unsupported variable-weight pricing.
4. Match products to ingredients conservatively: a fuzzy result for rice may be
   a prepared dish. Our exact/alias matcher alone cannot reliably interpret every
   branded product description. Use confirmation or documented normalization.
5. `StoreOffer.inStock` is a boolean, while providers can omit inventory. Either
   exclude unverified offers with a clear explanation, or extend the model to
   represent unknown availability before exposing those offers as recommendations.
6. Compare the cost of enough **whole packages** for the shortage, and label the
   selected price basis. Choose promotions only when their conditions are known.
   Reject incompatible mass/volume conversions rather than inventing a density.
7. Exercise public reads and error fixtures for 401/403, quota errors,
   timeouts, malformed responses, and empty offers. Provider calls belong off the
   Swing event thread; tests should use deterministic responses rather than
   depend on live services.

Persistence is independent of these provider choices and is implemented now.
Open Prices public reads and national coverage, plus SerpApi Walmart searches,
have now been sampled. The Walmart integration implements revised class contracts,
conservative quantity handling, and explicit coverage/freshness UI. See the README
for configuration and usage. Other-chain coverage and public data-use permissions
remain unresolved; quotes are session-only.
