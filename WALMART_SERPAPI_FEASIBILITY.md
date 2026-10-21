# Walmart pricing via SerpApi: live feasibility test

Tested October 21, 2026, using the user's Free Plan and locally stored key.
The initial audit below precedes the implemented Walmart estimate workflow.
Kroger and Food Lion were not tested through this provider.

## Implementation update (October 21)

Four follow-up requests with explicit store `1852` (milk, rice, chicken, repeated
milk) all returned that store, labeled **Lake Ridge, VA**, postal code 22192.
This supersedes the earlier Dale City label from the unscoped search. The repeated
milk response had the same search timestamp. The account reported 233 searches
remaining afterward (three additional uses); no paid plan was purchased.

The first application workflow is implemented with reviewed product selection,
whole-package calculations, conservative exclusions, quota checks, a session
cache, cancellation, and stale/partial estimate handling. The implementation's
parser was also replayed offline against these saved responses: it retained
usable fixed packages for all three ingredients without additional API requests.
Automated regression results and setup are recorded in README. The historical
unscoped audit below remains evidence of why an explicit reference store is
required. Intended public display/storage permissions remain unresolved.


## Result and recommendation

**The Walmart search endpoint passes an initial technical feasibility check.**
All 15 first-page grocery searches succeeded. Manual review identified at least
one relevant Walmart-sold product in each search. This supports a Walmart-only
pricing prototype; it does not validate automatic ingredient matching, a
complete basket, or comparison with other chains.

Before integration, validate searches with an explicit reference store. The
unscoped requests returned 15 different store IDs in 11 states, so combining
their prices into one basket would be misleading. The product can focus on the
Walmart chain without a proximity feature, while retaining one clearly labeled
reference store for the estimate. The documented `store_id` parameter is the
next check; it was not exercised in this 15-search run.

## Observed results

- 15/15 searches returned `Success`; 459 result rows, representing 428 distinct
  product IDs across the searches.
- 350 rows explicitly identified `Walmart` or `Walmart.com` as the seller and
  included a numeric USD offer price. These are listings, not 350 distinct
  ingredient matches. Other sellers must be excluded from Walmart comparisons.
- Returned availability fields marked 444 rows not out of stock and 15 out of
  stock. This is provider-reported availability, not independent inventory
  verification or a checkout guarantee.
- Median search response time was 4.41 seconds; range 2.69–9.30 seconds.
  Requests were sequential with no retries and a maximum of 15 searches.
- The account reported 250 searches remaining before the run and 236 afterward
  (14 used). This is the account API's reported usage, not an assumption that
  every call costs one credit; caching or reporting timing can affect counts.
  No subscription or extra credits were purchased.

Examples manually selected from the responses are below. They are not claims
of the cheapest listing, nationwide prices, or independently verified shelf
prices. Search metadata dates were October 21, 2026. Retrieval timestamps
describe the API search, not a retailer-guaranteed last-price-update timestamp.

| Ingredient | Selected product/package | Returned price | Returned store city/state |
| --- | --- | ---: | --- |
| Whole milk | Great Value whole milk, gallon | $3.48 | Dale City, VA |
| Eggs | Great Value large white eggs, 12 count | $1.67 | Tampa, FL |
| White rice | Mahatma extra long grain white rice, 2 lb | $2.88 | Chicago Heights, IL |
| Spaghetti | Barilla spaghetti, 16 oz | $1.88 | Interlachen, FL |
| Flour | Great Value all-purpose flour, 5 lb | $1.97 | Tega Cay, SC |
| Sugar | Great Value granulated sugar, 4 lb | $2.97 | Union Springs, AL |
| Butter | Great Value salted butter, 16 oz / 4 sticks | $3.06 | Newport News, VA |
| Raw chicken breast | Perdue Harvestland Perfect Portions fresh boneless skinless breast, 1.65 lb | $8.84 | Rincon, GA |
| Fresh tomatoes | Beefsteak tomato, each; variable-weight basis | $1.16 estimated each; $1.97/lb | Shelby Township, MI |
| Onions | Fresh yellow onions, 3 lb bag | $2.29 | Apple Valley, CA |
| Potatoes | Fresh russet potatoes, 5 lb bag | $2.47 | Omaha, NE |
| Olive oil | Great Value extra virgin olive oil, 17 fl oz | $6.12 | Tyler, TX |
| Black beans | Great Value canned black beans, 15 oz | $0.92 | Texarkana, TX |
| Oats | Quaker old-fashioned oats, 18 oz | $2.98 | Richmond, VA |
| Cheddar | Great Value sharp cheddar block, 16 oz | $3.58 | Essex, MD |

Fourteen selected titles describe fixed package quantities; the tomato example
requires variable-weight handling. Package text was reviewed manually, not
parsed by application code. The milk title's gallon also needs explicit US
volume normalization. Net can weight does not establish drained weight.

## Issues the implementation must handle

1. **Search results are candidates.** Whole milk also returned chocolate milk
   and yogurt. Fresh tomatoes also returned ketchup and tomato puree. Cheddar
   block search included slices, shreds, and processed cheese. Do not simply
   select the first or lowest-priced result. Match food form and retain product
   selection when uncertainty remains.
2. **Package and price basis must agree.** Among all rows, 439 used `each`, 15
   used `pack_weight`, and 5 used `each_weight`. A tray labeled 0.70–2.90 lb
   cannot be assigned the lower bound, upper bound, or an inferred fixed weight.
   Show the per-pound price and an estimate, or exclude it from fixed-package
   calculations. Keep ounces and fluid ounces distinct. Do not treat a generic
   `quantity` response field as edible package size without verified semantics.
3. **Deduplicate and preserve conditions.** Some searches repeated product IDs.
   Keep the offer/store context and distinguish stock status, variants, ranges,
   and promotions. Unknown fields must remain unknown.
4. **Keep network calls out of Swing's UI thread.** Debounce ingredient searches,
   cache repeated requests within permitted limits, cancel superseded work, and
   bound requests per basket. Fifteen sequential searches took roughly a minute
   and a half including request spacing and account reads.
5. **Store context is required for honest prices.** This need does not require
   local price collection or adding small/local retailers. It means choosing an
   explicit price-reference store within the approved large chain and labeling
   estimates accordingly.

## Access, usage rights, and next step

The [Walmart Search API documentation](https://serpapi.com/walmart-search-api)
describes product search, store filtering, seller fields, availability, and
one-hour provider caching. This is a third-party scraping API, not a Walmart
partner authorization. SerpApi's [terms](https://serpapi.com/legal) preserve
responsibility for downstream data use and third-party rights. This review did
not establish an explicit Walmart license for redistribution or indefinite
storage. Technical success does not resolve those display/storage questions.

The subsequent explicit-store validation passed, and the Walmart-only course
prototype is implemented. Review its UX and course contracts, and clarify
public data use before distributing it. Do not claim
cross-retailer comparisons until an appropriate source for Kroger/Food Lion is
verified. The free quota can support a small course trial, but not an unrestricted
multi-user service. See [current plan limits](https://serpapi.com/pricing).

## Reproduction and credential handling

The working scripts are in ignored `build/audit_serpapi_walmart.py` and
`build/review_serpapi_walmart.py`. Sanitized responses and reviewed selections
are in ignored `build/serpapi-walmart-audit.json` and
`build/serpapi-walmart-reviewed.json`. These working files are not shipped
application dependencies. The script reads only the named `SERPAPI_API_KEY`
environment variable or the project's ignored `.env.local`; it does not print
credentials, authenticated URLs, or account email/identity fields.

Procedure: query `account.json` to confirm quota, then send one `search.json`
request per phrase with `engine=walmart`, `walmart_domain=walmart.com`, and
`page=1`. Query account quota again at the end. Do not force cache bypass.
Requests stop early on errors or repeated empty responses. The exact phrases
are recorded in the sanitized audit. Inspection of the resulting products was
offline and did not use additional search quota.

The initial audit changed research documentation and local audit tooling only.
Subsequent implementation has automated model, provider, parser, controller, and
Swing regression coverage; the README describes the test command.
