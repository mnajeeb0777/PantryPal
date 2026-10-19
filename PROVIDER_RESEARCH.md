# PantryPal provider feasibility

Initially checked October 14, 2026; recipe decision and implementation updated
October 18. TheMealDB import is implemented. `StoreOffer` and `PriceFinder`
remain unimplemented.

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
- Use **Kroger Public Products + Locations** as the first pricing candidate.
  Scope the initial feature to supported Kroger-family locations, rather than
  promising comparison across every grocery chain.
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
replacing a reviewed recipe. Save format version 1 is unchanged.

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

## Implementation implications and next checks

1. Obtain Kroger credentials through its account workflow for the pricing phase.
   Supply them locally through environment variables when that adapter is built;
   do not put keys or client secrets in source code or recipe save files.
2. Review the completed recipe correction screen with the team, including food
   forms and package-size corrections. Live search/lookup, offline save/reopen,
   supported fractions, uncertain quantities, duplicates, and failure recovery
   have been checked; user-supplied quantities still need human judgment.
3. For pricing, use a ZIP/radius to retrieve store IDs, then query an ingredient
   at each returned store. Verify price, package size, and stock fields together.
   Parse simple package units (for example, convert US gallons to liters);
   reject ambiguous multipacks or variable-weight pricing until supported.
4. Match products to ingredients conservatively: a fuzzy result for rice may be
   a prepared dish. Our exact/alias matcher alone cannot reliably interpret every
   branded product description. Use confirmation or documented normalization.
5. `StoreOffer.inStock` is a boolean, while providers can omit inventory. Either
   exclude unverified offers with a clear explanation, or extend the model to
   represent unknown availability before exposing those offers as recommendations.
6. Compare the cost of enough **whole packages** for the shortage, and label the
   selected price basis. Choose promotions only when their conditions are known.
   Reject incompatible mass/volume conversions rather than inventing a density.
7. Exercise authenticated requests and error fixtures for 401/403, quota errors,
   timeouts, malformed responses, and empty offers. Provider calls belong off the
   Swing event thread; tests should use deterministic responses rather than
   depend on live services.

Persistence is independent of these provider choices and is implemented now.
Authenticated provider validation is the remaining prerequisite for steps 3/4.
