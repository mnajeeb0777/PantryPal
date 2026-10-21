# Open Prices: national US chain feasibility

Research completed October 20, 2026. Read-only public API observations were
retrieved October 19–20, using a fixed observation cutoff of October 19.
Application pricing code has not been implemented.

## Decision

**Scope revised October 21: focus on supermarket giants, initially Walmart,
Kroger, and Food Lion, as explicitly named by the user.** Do not expand the
store list to smaller/local chains merely to compensate for sparse data.
Additional large chains can be evaluated against this preference later.

The October 20 recommendation to proceed based on the broader 14-chain sample
is conditional under this narrower scope. The named three had only 39, 2, and 1
recent observations respectively. Open Prices remains a possible supplementary
source, but this audit does not establish it as a sufficient primary provider
for those chains. The next step is targeted provider/coverage research before
committing to a pricing integration. Individual store observations must still
not be presented as national chain prices.

This follows the requested direction: automatically retrieve existing prices
from major US chains, without asking users or the team to collect prices.
Missing data should produce an unavailable result, not a mandatory price-entry
form. Selecting an appropriate product from search results may still be needed.

## Chain coverage measured

The table below preserves the broader research sample; it is not the proposed
store list. Higher counts at excluded chains do not establish coverage for the
three priority chains.

Recent means observation dates **July 21–October 19, 2026**, inclusive. Counts are
price observations, not unique products, ingredient coverage, or available stock.
They can include repeat observations and non-food products. Rows explicitly
flagged as duplicates were excluded. Store counts include historical locations.

| Chain | US locations with historical data | All-time observations through cutoff | Recent observations |
| --- | ---: | ---: | ---: |
| Safeway | 12 | 15,921 | 2,325 |
| Whole Foods Market | 20 | 5,669 | 1,174 |
| Smart & Final | 2 | 1,570 | 1,089 |
| Sprouts Farmers Market | 6 | 979 | 549 |
| Costco | 34 | 1,455 | 200 |
| Target | 16 | 534 | 148 |
| Grocery Outlet | 8 | 560 | 127 |
| Trader Joe's | 22 | 877 | 120 |
| ALDI | 13 | 179 | 77 |
| Lidl | 3 | 51 | 41 |
| Walmart | 37 | 606 | 39 |
| Kroger | 4 | 4 | 2 |
| Publix | 7 | 31 | 2 |
| Food Lion | 5 | 13 | 1 |

These chain labels use store brand/name matching; Kroger-owned banners were not
merged into Kroger. Counts are limited to locations found by the country query
and confirmed with country code US, so incomplete location metadata can cause
omissions. They are a reproducible audit snapshot, not comprehensive coverage
guarantees or a measure of chain popularity.

The newest 100 observations at Safeway were all in Mountain View; Whole Foods'
newest 100 were all in Sunnyvale. Smart & Final and Sprouts showed the same
concentration in those respective cities. Display the actual city and date
alongside every price. Never label these as chain-wide prices or current prices
at the user's nearest store.

## Ingredient and package checks

Exact category queries across the 14 chains returned the following recent
candidates. Both packaged-product categories and loose-item category tags were
queried. All returned pages were read.

| Ingredient query | Category | Candidate observations | Chains represented |
| --- | --- | ---: | ---: |
| Milk | en:milks | 40 | 10 |
| Eggs | en:eggs | 15 | 6 |
| Rice | en:rices | 34 | 2 |
| Pasta | en:pastas | 107 | 7 |
| Wheat flour | en:wheat-flours | 5 | 2 |
| Sugar | en:sugars | 2 | 1 |
| Butter | en:butters | 9 | 3 |
| Chicken breast | en:chicken-breasts | 1 | 1 |
| Tomatoes | en:tomatoes | 80 | 4 |
| Onions | en:onions | 0 | 0 |
| Potatoes | en:potatoes | 3 | 2 |
| Olive oil | en:olive-oils | 28 | 2 |

Nine categories appeared at two or more chains, but this is **not a 75% usable
ingredient match rate**. Inspection exposed material false matches:

- Sugar returned stevia products, not plain sugar.
- Eggs included plant substitutes and hard-boiled eggs.
- Milk included coconut milk and other products unsuitable as automatic dairy
  milk substitutions.
- Chicken breast returned cooked chicken bites. Its text quantity said
  `10 3.2 oz pouches (32oz)`, but its numeric quantity was only `90 g`.
- Potatoes included potato puffs. One other result was a 10 lb bag of russets.

A second search paginated all products with recorded prices whose names
contained `onion` (250 products), `sugar` (434), or `chicken breast` (77), then
checked their recent USD observations at the selected US chains. It found 32,
39, and 2 observations respectively. Red onions appeared at Lidl on July 22 for
$1.99, but without a package quantity. No usable plain-sugar or raw-chicken-breast
offer was established by these searches. Alternative names, missing metadata,
other categories, and other dates may contain additional data; these results
do not prove those ingredients are absent from the whole service.

Across the newest up-to-100 observations per chain (1,099 rows total), only
**527 (48%)** had a positive numeric product quantity and a quantity unit.
This is a deliberately bounded sample, not a random estimate of the database.
Even present numeric fields can conflict with package text, as above. Some
missing numeric quantities can be recovered from unambiguous text such as
`12pk` or `18 count` for eggs, but unsupported or conflicting cases must remain
unpriced for recipe-cost calculations.

Same-barcode comparisons do exist. For example, product `0191011000872`,
"Folded plant eggs", 227 g, had a $5.99 discounted observation at Sprouts in
Sunnyvale on October 3 and a $6.29 non-discounted observation at Whole Foods in
Rochester Hills on September 26. These are historical observations with different
conditions, not a current savings guarantee or a substitute for ordinary eggs.
Source rows: [341755](https://prices.openfoodfacts.org/api/v1/prices/341755)
and [336069](https://prices.openfoodfacts.org/api/v1/prices/336069).

## Access and implementation order

Public location, product, and price reads worked without an account or API key.
No subscription, scraping service, or user price collection was needed for the
audit. Do not infer unlimited capacity or an uptime guarantee from this test.
The official [API guide](https://openfoodfacts.github.io/open-prices/guides/API/)
describes authentication for endpoints that require it and a pre-production
environment for development. The [data guide](https://openfoodfacts.github.io/open-prices/guides/data/)
documents exports and ODbL reuse requirements. Include attribution and review
applicable database-sharing obligations before distributing a derived database.
Keep provider cache data separate from private pantry and recipe records.

Recommended implementation sequence:

1. **Revise the class contracts for observed prices.** Extend `StoreOffer` to
   carry source identity, observation date, country/currency, actual store
   location, price basis, and discount information. Make distance optional and
   availability explicitly unknown; the current `inStock` boolean cannot
   represent this source honestly. Rename or replace `findNearbyOffers` with a
   chain-filtered search. A shared API secret is unnecessary for tested reads.
2. **Build retrieval and product review.** Search category aliases and product
   names, filter to US locations/USD, paginate with bounds, exclude flagged
   duplicates, and retain the newest observation per product/location. Restrict
   results to the approved large-chain list, starting with Walmart, Kroger, and
   Food Lion; do not silently substitute better-covered smaller chains.
   Show product name, package, chain, city, date, source, and discount conditions.
3. **Add conservative cost comparison.** Confirm food form, parse reliable
   package quantities, reject contradictory measurements and unsupported
   conversions, then compute the cost of enough whole packages. Do not compare
   package prices directly with per-weight prices or unconditional prices with
   unexplained promotions. Missing matches remain visibly unpriced and can be
   skipped. Never rank an incomplete basket as the cheapest complete basket.
4. **Handle operational failures and validate the UI.** Run network calls off
   the Swing event thread, debounce/cache requests, use timeouts and bounded
   retries, and distinguish no match from network failure. Test using fixtures
   for wrong food forms, stale dates, missing quantities, conflicting multipacks,
   unknown availability, promotions, and partial basket coverage.

This implementation sequence is conditional on establishing useful coverage for
the priority chains. A source-backed price browser is a possible first release;
even that experience may be too sparse with Open Prices alone. Dependable
automatic recipe-to-basket pricing remains unproven.

## Reproduction and limitations

Use the [live API schema](https://prices.openfoodfacts.org/api/docs) and
`https://prices.openfoodfacts.org/api/v1/` as the base. Audit procedure:

1. Paginate `locations` with `osm_address_country__like=United States`,
   `price_count__gte=1`, `size=100`, `order_by=-price_count`. Deduplicate IDs,
   retain country code `US`, and group by brand/name. This retained 367 locations.
2. For each chain's IDs, query `prices` with `location_id__in`, `currency=USD`,
   `duplicate_of__isnull=true`, `date__lte=2026-10-19`. Read the total and newest
   100 rows; add `date__gte=2026-07-21` for the recent count.
3. For ingredient checks, use all selected location IDs and the recent dates.
   Query each tag separately through `product__categories_tags__contains` and
   `category_tag`; combine and deduplicate price IDs.
4. For text fallbacks, paginate `products` with `product_name__like`,
   `price_count__gte=1`, then request matching `prices` using `product_id__in`
   batches of at most 100. A long combined ID query returned HTTP 400; batching
   completed the check. Production code must bound request sizes.

Local working scripts and JSON responses are in ignored `build/` files named
`audit_open_prices*.py` and `open-prices-*-audit.json`. They are research artifacts,
not shipped application dependencies. Live counts will change; category/name
searches can miss data, and contributor observations are not retailer inventory.
See the official [data-quality documentation](https://openfoodfacts.github.io/open-prices/topics/data-quality/)
for validation and duplicate handling. No application tests were rerun because
this continuation changed research documentation only.
