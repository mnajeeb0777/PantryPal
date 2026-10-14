# PantryPal implementation decisions

These notes record the approved changes and the core implementation increments.
The original README and reference documents remain unchanged.

## Approved third LinkedChain element type

**Decision:** RecipeMatcher's compareRecipe method collects comparison entries
in a local `LinkedChain<IngredientMatch>`. It retrieves those entries as an
`IngredientMatch[]` for the existing
`RecipeMatchResult` constructor.

This makes the same generic chain useful for three application element types:
`Ingredient`, `Recipe`, and `IngredientMatch`. The third use serves the
Deliverable 3 reuse criterion; multiple chains holding Ingredient alone would
still count as one element type.

- Keep all existing public method signatures and result arrays.
- Keep the supplied LinkedChain source unchanged.
- Use only the chain's public API, including `add` and `toArray`.
- Preserve recipe requirement order in the output. Front insertion followed by
  reversing the extracted array takes O(r) time for r results; repeatedly using
  `addToEnd` would take O(r^2).
- Preserve the requirement that comparison never double-counts pantry stock.
  Restoring output order does not replace the stock-allocation rules.

**Status:** Implemented and tested. RecipeMatcher stores and retrieves actual
IngredientMatch objects through the public chain API, completing the third
application element type. This addresses the reuse criterion; the remaining
Deliverable 3 prototype and submission requirements still need completion.

## Implemented increment

- Ingredient: validated identity/details, independent copies, atomic updates,
  and equality/hash codes based on ID.
- Recipe: validated metadata, an owned ingredient chain, ordered instructions,
  defensive copies, and equality/hash codes based on local ID.
- Pantry: owned ingredient storage, queries, updates, removal, and atomic
  deductions that may consume multiple entries.
- RecipeBook: owned recipe storage, queries, cook-time/effort filtering, and
  removal.
- IngredientMatcher: added with the user's explicit approval because pantry
  deductions require its name matching and unit conversions.
- IngredientMatch: immutable requirement snapshot, validated available quantity,
  and shortage/sufficiency calculations using the pantry's roundoff policy.
- RecipeMatchResult: owned comparison snapshots, separate shortage/unknown
  arrays, and a canCook result requiring every supplied match to be sufficient.
- RecipeMatcher: ordered allocation across a private pantry snapshot, including
  repeated requirements, aliases, compatible conversions, and a local result
  chain holding IngredientMatch entries.

- PantryPalController: session construction, core pantry/recipe actions, UUID
  creation, fresh comparison before cooking, unsaved-change tracking, and
  random selection of a currently cookable saved recipe.
- PantryPalGUI: Swing pantry management, custom recipe creation, saved-recipe
  browsing/search/filtering, comparison, cooking, and Meal Roulette.

Meal Roulette is now the implemented scope stretch goal. Persistence, external
recipe/price services, photo recognition, and social-media importing remain
future work. Unit and Effort retain their existing enum values.

## Approved session-only Swing prototype

The user chose a functional Swing desktop interface for this increment, with
the option to redesign it later, and explicitly approved session-only storage.
Swing uses the existing JDK; no dependency or Eclipse classpath changes are
needed. The GUI calls the controller rather than accessing chain internals or
owning the live Pantry/RecipeBook. Temporary GUI draft and display lists do not
replace the model's LinkedChain storage.

The GUI has three tabs:

- **Pantry:** add, view, search, update, and remove ingredient entries. Names may
  repeat; selections resolve to stable IDs even after sorting the table.
- **Recipes:** view details and instructions, search by title, combine cook-time
  and effort filters, compare current pantry stock, remove a recipe, and cook
  with an all-or-nothing deduction. The comparison labels are Required,
  Allocated, Missing, Unit, and Status; incompatible-only stock displays Unknown.
- **Create recipe:** enter metadata, add/remove ingredient requirements, and
  enter one instruction per line. Blank time fields mean unknown. Requirements
  retain creation order even if the draft table is sorted.

The GUI parses numeric text; the models continue to validate domain values.
Invalid input produces an inline error and leaves the form available for
correction. Text quantities show "Enter a valid number." Model messages cover
zero/negative, nonfinite/oversized quantities, invalid names, and long names.
Time fields accept nonnegative integers or blank. No-result searches and
missing selections produce feedback rather than exceptions escaping the event
handler. Rejected edits, invalid recipe drafts, and failed cooking leave live
data unchanged.

### Temporary departures and boundaries

- Added a no-argument PantryPalController constructor for the session-only
  prototype. The original constructor signature remains; its matcher is
  required, while null persistence/import/price services disable those paths.
- Successful session mutations set hasUnsavedChanges to true. They do not
  autosave. Explicit save/load calls without a configured service fail with
  IllegalStateException instead of pretending to succeed. This is the
  user-approved temporary departure from the controller's autosave contract.
- Persistence and external-service delegation are wired in the controller for
  later use, but DataStore, PantryPalData, RecipeImporter, PriceFinder, and
  StoreOffer remain skeletons. These configured-service paths are not functional
  or verified end to end yet. The GUI exposes only working session operations.
- The header always says that closing loses session data. Closing a changed
  session asks for confirmation. No sample data is inserted automatically,
  and no files or network requests are made by the session workflow.
- The GUI requires Swing's event-dispatch thread. main schedules startup there.
  This keeps presentation code separate so a future GUI redesign can reuse the
  same controller and models.
- Meal Roulette was added in the following increment. Its working stretch
  feature addresses the Working Application Slice Exceeds addition; the
  remaining rubric checks and required README sections still need completion.

### Launch and check

From the repository root in PowerShell:

```powershell
.\scripts\run.ps1
.\scripts\test.ps1
```

run.ps1 resolves the existing JDK 17 toolchain, compiles with Java 11 source/API
compatibility, and starts PantryPalGUI. In Eclipse, refresh the project, then
right-click src/PantryPalGUI.java and choose **Run As > Java Application**.
No command-line arguments are needed.

A short manual walkthrough: add 3 cups of rice in Pantry; create a recipe
requiring 2 cups of rice; select it in Recipes and compare; click **Cook &
deduct ingredients**. The pantry should retain 1 cup, and the refreshed
comparison should show a 1-cup shortage. Try an invalid quantity such as "abc"
or 0, correct it, and confirm the session remains usable. Closing loses data.

## Meal Roulette increment

The user approved implementing the scope's Meal Roulette stretch goal. The
feature stays in the existing controller/GUI architecture; no application class
or dependency was added.

- Added public PantryPalController.selectMealRouletteRecipe(): Recipe. The
  scope described the behavior but the original UML/class reference did not
  provide a method for it; this additive signature is recorded here.
- Each call takes current pantry and saved-recipe snapshots and checks every
  saved recipe with RecipeMatcher and RecipeMatchResult.canCook(). Each
  comparison allocates
  independently, so considering one candidate does not reserve stock against
  another. Names, units, repeated requirements, and roundoff follow the existing
  comparison rules.
- The method collects eligible recipe snapshots in an array and selects one
  with ThreadLocalRandom.nextInt(eligibleCount). Every eligible saved recipe ID
  has equal probability. Same-name recipes remain distinct; consecutive draws
  may repeat. There is no persistent history or forced rotation.
- No candidates returns null, including an empty recipe book, empty pantry, or
  only insufficient/unknown comparisons. The no-argument method has no invalid
  input parameters; tests cover those boundary states and the existing
  IllegalArgumentException contract for an unrepresentable conversion. Such an
  error is surfaced rather than silently hiding the problematic recipe.
- Selection returns an independent saved-recipe snapshot and changes neither
  inventory, saved recipes, nor session flags. Cooking remains a separate action
  that rechecks stock before deducting.
- The Recipes tab has a Meal Roulette button. It searches all saved recipes,
  regardless of browse filters. A successful draw clears those filters, selects
  and scrolls to the chosen recipe by ID, and shows its comparison. A draw with
  no eligible recipes leaves the view/data intact and explains that ingredients
  or recipes need to be added. Errors use the existing recoverable feedback.
- Shared GUI selection code now reveals both newly created recipes and roulette
  results, correctly converting model/view indices when the table is sorted.

Five new controller tests cover empty/short-stock cases, incompatible units,
repeated requirements, aliases/conversions, multiple candidates, snapshot
ownership, fresh state after removal/cooking, and conversion failure. Three
GUI tests exercise the button, hidden/sorted candidates, invalid browse-filter
text, no-candidate feedback, separate cooking, and error recovery. Repeated-draw
tests assert eligibility and nonmutation, not a particular random sequence or
statistical distribution, avoiding probabilistic test failures.

To demonstrate: save at least two recipes and stock enough ingredients for
them; click Meal Roulette in Recipes. The chosen recipe is displayed without
deducting anything. Click Cook & deduct ingredients to consume stock, then draw
again to confirm eligibility reflects the remaining pantry.

## Approved result-model decisions

The user approved rejecting an empty comparison array, consistent with rejecting
recipes that have no ingredients. A null array, null match, or repeated
requirement ID is also invalid. Distinct requirements may still share a name.
The recipe ID follows Recipe's UUID validation and is retained as supplied.

IngredientMatch copies its required Ingredient. Known available quantities must
be finite and nonnegative; null means the quantities cannot be compared. Zero
means no stock, not unknown stock. Available amounts have no 1,000,000 cap,
because they can represent combined or converted stock. Unknown amounts produce
null shortages and are never sufficient.

The user also approved using Pantry's exact four-ULP/relative-difference rule
for comparison roundoff. A shortage within that tolerance is reported as zero
by getMissingQuantity and is sufficient. A real positive shortage is retained,
including tiny/subnormal values. Tests compare IngredientMatch decisions with
Pantry.deductIngredients for single-requirement boundary cases. The tolerance
formula is private in each class; parity tests guard against future drift.
RecipeMatcher tests also check multi-entry allocations against deduction.

RecipeMatchResult copies each supplied match into an owned immutable match and
returns a copy of the match array. Ingredient getters return independent copies.
Known shortages preserve the requirement's ID, name, and unit while replacing
its amount with only the missing quantity. Unknown requirements retain their
full required quantity and appear only in getUncomparableIngredients. Output
order follows input order. Empty result arrays are returned when no requirements
belong in the requested category; zero-quantity Ingredients are never created.

canCook describes the supplied comparisons; it does not look up a Recipe or
allocate pantry stock. RecipeMatcher now supplies every requirement once and
avoids reusing stock across equivalent requirements.

## Approved recipe-comparison decisions

The user approved both recommended behaviors before implementation:

- Allocate in recipe requirement order, visiting pantry entries in snapshot
  order. Each result's available quantity means the amount allocated to that
  requirement, capped at its required amount. For two requirements of 2 cups
  and stock of 3 cups, report allocations of 2 and 1. With 5 cups in stock,
  report 2 and 2, leaving 1 unused. Allocations are local to one comparison;
  they never change the pantry or persist into later comparisons.
- Use compatible stock whenever matching compatible entries exist. Ignore
  incompatible units in that case, even when compatible stock is short. If
  matching entries exist only in incompatible units, report unknown (null).
  No matching name means known zero. An entry depleted by an earlier
  requirement still establishes compatible units, so later requirements
  receive known zero rather than unknown.

RecipeMatcher validates non-null inputs and pantry entries and rejects repeated
pantry IDs, consistent with Pantry's constructor. It copies the stock before
allocation, tracks remaining quantities in each entry's native unit, and uses
the supplied IngredientMatcher for aliases and conversion. Conversion results
that cannot be represented still throw IllegalArgumentException, matching
deduction's existing behavior. Public UML/class-reference signatures and fields
are preserved; private helpers perform snapshot validation and allocation.

Integration testing exposed an existing roundoff inconsistency when a
requirement consumed multiple stock entries. Pantry's last subtraction used
the smaller remaining amount as its scale, while IngredientMatch evaluated the
full requirement. Pantry now also evaluates the total allocated amount using
the approved four-ULP/relative tolerance before rejecting a residual shortage.
This is a numerical consistency fix, with no new conversion or matching rules.
Both comparison and deduction retain the existing per-subtraction tolerance;
comparison reports the full required amount when that tolerance fulfills it.
Tests cover the total tolerance boundary, an amount just outside it, and
subnormal shortages. Failed deductions remain atomic.

## Contracts and resolved discrepancies

The UML and class reference take precedence over contradictory method-table
entries in the specification, as approved by the user.

- IDs are UUID strings, as the class reference specifies. Constructors validate
  full UUID spelling and retain the supplied string. They do not generate new
  IDs. Identity compares the stored strings exactly.
- Ingredient names must contain a letter, have no control characters, and be
  at most 100 characters. Ordinary punctuation is allowed. Stored display names
  are preserved; matching/searching normalize only their comparisons.
- Ingredient quantities must be finite, greater than zero, and at most
  1,000,000. Failed updates leave all previous details unchanged.
- Recipes with no ingredients are rejected, explicitly approved by the user.
  Requirement entries must have distinct IDs; repeated ingredient names are
  allowed and can be allocated together during cooking.
- Recipe names must be nonblank and contain no control characters. Instruction
  arrays must be non-null, and supplied steps must be nonblank. An empty step
  array is permitted; no minimum step count was specified.
- Recipe times may be null (unknown) or nonnegative. Effort must be non-null;
  UNKNOWN represents unavailable effort information. Source URL and external
  ID must be non-null and can be empty for custom recipes. Source strings are
  preserved; this increment does not validate or contact external providers.
- Collections permit duplicate names and reject duplicate IDs. Add methods
  return false for an existing ID. Initial arrays containing duplicate IDs are
  invalid. Null entries/arrays throw IllegalArgumentException, following the
  class reference rather than conflicting false/NullPointerException examples.
- Lookups with an absent nonblank ID return null; update/remove return false.
  Null/blank IDs throw IllegalArgumentException. Updates of absent entries
  return false before inspecting replacement details.
- Searches use case-insensitive substrings with Locale.ROOT. Leading/trailing
  query whitespace is ignored, blank queries return all entries, and null
  queries throw IllegalArgumentException.
- Filters combine using AND. Null disables that filter; an active time limit
  excludes unknown cook times. Negative time limits are invalid.
- Collections return independent snapshots. Pantry changes cannot mutate a
  recipe's requirements, and external callers cannot change stored entries
  through returned arrays or objects.
- Collection storage is unordered as described in the scope. New entries are
  added at the front. Bulk loading and recipe requirements retain array order
  by visiting inputs backward and calling add, avoiding quadratic appends.

## Matching and deduction details

The default alias map recognizes scallion, scallions, and green onions as
green onion. Custom maps are copied, normalized, and checked for conflicting
keys or cycles. Canonical names resolve alias chains; self-aliases are valid.
Normalization uses lowercase and collapsed whitespace. No raw/cooked food
substitution or general singular/plural guessing is performed.

Conversions stay within mass, volume, or count. Mass uses grams and volume uses
milliliters as base units. Customary volume measures are US measures, as the
class reference proposes. Null units, incompatible dimensions, negative or
nonfinite quantities, and unrepresentable conversion results are rejected.
Zero is allowed for conversion calculations, while stored Ingredient quantities
remain strictly positive. COUNT conversion does not establish ingredient
identity; callers must also check names.

Pantry deductions allocate against a private snapshot and remaining-quantity
array. Repeated requirements consume that shared remaining stock. A shortage
returns false without changing live entries. Only after every requirement is
satisfied is a replacement chain installed, excluding depleted entries.
An empty requirement array is a successful no-op; null requirements or matcher
are invalid. Conversion roundoff is limited to four ULPs and a relative
difference of at most 1e-12; tiny/subnormal shortages are tested explicitly.

## Complexity compared with the specification

Let p be pantry entry count, n saved recipe count, r requirements in a recipe,
s instruction count, L text length, and D the total nested recipe data copied.
Hash-set/map operations below have expected constant-time lookup, excluding
text hashing costs. Recipe snapshots include deep copies, not just references.

| Operations | Actual time | Comparison with spec estimates |
| --- | --- | --- |
| Ingredient construction/update | O(L); quantity update O(1) | Includes name validation; names are capped at 100 characters |
| Ingredient copy/getters/equality/hash | O(1) with bounded UUID/name data | Matches the model's constant-size fields |
| Recipe construction/copy | O(r + s + text validation) | Matches O(r+s) when text lengths are bounded |
| Recipe requirement/instruction getters | O(r) / O(s) | Requirement snapshots cannot be O(1) |
| Recipe scalar getters/equality/hash | O(1) for fixed UUID identity | Matches the intended constant-time operations |
| Pantry construction | Expected O(p) | Duplicate-ID set plus reverse front insertion |
| Pantry add/find/update/remove/snapshot | O(p), plus input validation | No indexed traversal of a linked chain |
| Pantry search | O(p * L) | Spec O(p) suppresses string-comparison work |
| Pantry deduction | O(r * p * L + p) | Spec O(r*p) suppresses name work and final snapshot/commit |
| RecipeBook construction/snapshot | O(n + D) | Spec O(n) omits copying nested recipe contents |
| RecipeBook add/find | O(n + copied recipe data) | Snapshots add work beyond ID lookup |
| RecipeBook remove | O(n) | Matches the intended scan/removal behavior |
| RecipeBook search/filter | O(n * L + D) / O(n + D) | D includes only returned recipes; filters use scalar checks |
| Collection size/isEmpty | O(1) | Delegates to the stored chain count |
| Matcher name normalization/comparison | O(L), expected map lookup | Matches the normalization estimate |
| Matcher compatibility/conversion | O(1) | Fixed set of enum units |
| Custom matcher construction | O(a^2 * L) worst case for a chained aliases | More than the spec's O(a) copy-only estimate because chains/cycles are validated |
| IngredientMatch construction and getters | O(1) with bounded Ingredient fields | Includes an independent ingredient snapshot |
| RecipeMatchResult construction | Expected O(r) | Copies matches and checks unique requirement IDs |
| RecipeMatchResult array getters/canCook | O(r) worst case | Scans or copies comparison entries; recipe ID getter is O(1) |
| RecipeMatcher comparison | Expected O(r * p * L + p + r) time; O(p + r) extra space | Snapshot validation, shared remaining quantities, and linear chain extraction/order restoration |
| Session controller pantry/recipe operations | Same leading order as their delegated model operation | UUID generation/dirty flag do not change the dominant traversal/copy costs; no persistence work in session mode |
| Controller compare/cook | O(r * p * L + p + r) | Cooking rechecks comparison and then deducts; two linear nested scans retain the same leading order |
| Controller Meal Roulette | O(n + D + p + n * (r * p * L + p + r)) time, with r the maximum requirements per saved recipe; O(n + D + p + r) extra space | Copies saved recipes/pantry and compares each recipe independently; picking an eligible index is O(1); no earlier spec method estimate existed |
| GUI recipe filtering/refresh | O(n * L + D) before table sorting | Delegated filters plus name matching and display snapshots; user-requested sorting adds O(k log k) for k visible rows |
| GUI selected-recipe display | O(r * p * L + p + r + s + text length) before table sorting | Includes current comparison and rendering the recipe's requirements/instructions |

The supplied LinkedChain.contains is O(n), not the O(1) shown in one spec table.
Neither it nor any other supplied chain method was modified.

## Validation and attribution

Tests extend student.TestCase using the installed course JUnit 4.12 library.
Implemented core operations are exercised with normal and invalid or boundary
scenarios. Configured persistence/external-service paths and native window
startup/close-dialog interaction remain outside the automated coverage.
For no-argument getters, boundary values, empty collections,
copy isolation, and constructor rejection cover applicable invalid-state risks;
there are no getter input parameters to reject.

The tests include duplicate IDs/names, unknown times, empty collections, absent
removals, atomic failed updates/deductions, repeated requirements, alias/unit
matching, and conversion rounding. Result-model tests also cover null quantities,
known shortages, excess stock, invalid/empty match arrays, and snapshot isolation.
RecipeMatcher tests cover capped allocations, repeated/aliased requirements,
multi-entry conversions, mixed/incompatible units, depleted stock, input/output
isolation, integration with Pantry and RecipeBook, and deduction parity.
The full suite passes 110 tests after Meal Roulette: the existing
81 plus 16 controller tests and 13 GUI tests. GUI tests create actual Swing
controls on the EDT and click their buttons without opening desktop windows.
They cover pantry CRUD, sorting/selection identity, recipe creation and draft
recovery, combined filters, statuses, cooking, and invalid-input recovery.
All 18 source files compile with JDK 17 and Java 11 source/API compatibility.
The implemented classes and all tests also compile with the course Eclipse
compiler preferences without diagnostics, using compiled future-service
skeletons on the classpath. Compiling every skeleton directly with Eclipse
still reports its pre-existing unused-field warnings. The course library emits
its known
Java 17 SecurityManager deprecation warning during otherwise successful tests.
The three tabs were also rendered offscreen and visually inspected at two
widths. Render artifacts and the temporary preview harness are ignored under
build/. This checks component layout, not native window-manager behavior.

| File(s) | Contribution in this increment |
| --- | --- |
| src/Ingredient.java, src/Recipe.java | Codex/GenAI implementation and validation; user supplied and approved the design |
| src/Pantry.java, src/RecipeBook.java | Codex/GenAI implementation, storage ownership, queries, and mutations |
| src/IngredientMatcher.java | Codex/GenAI implementation of the approved dependency |
| src/IngredientMatch.java, src/RecipeMatchResult.java | Codex/GenAI implementation of result models with user-approved empty-array and roundoff decisions |
| src/RecipeMatcher.java | Codex/GenAI implementation of user-approved allocation and mixed-unit rules, using the third chain element type |
| src/Pantry.java | Codex/GenAI aligned the total-allocation roundoff check with comparison results during this increment |
| src/Unit.java, src/Effort.java | Codex/GenAI added enum-value documentation for course compiler checks; values unchanged |
| test/IngredientTest.java, test/RecipeTest.java | Codex/GenAI authored model tests |
| test/PantryTest.java, test/RecipeBookTest.java | Codex/GenAI authored collection tests |
| test/IngredientMatcherTest.java, test/TestFixtures.java | Codex/GenAI authored matcher tests and shared test support |
| test/IngredientMatchTest.java, test/RecipeMatchResultTest.java | Codex/GenAI authored result validation, snapshot, shortage, and pantry-parity tests |
| test/RecipeMatcherTest.java | Codex/GenAI authored 17 allocation, ownership, conversion, and integration tests |
| src/PantryPalController.java | Codex/GenAI implemented session coordination, optional-service guards, and Meal Roulette; user approved session-only storage and the stretch feature |
| src/PantryPalGUI.java | Codex/GenAI designed and implemented the user-approved Swing prototype, input recovery, session-loss warning, and Meal Roulette action |
| test/PantryPalControllerTest.java | Codex/GenAI authored 16 session, ownership, validation, cooking, unavailable-service, and Meal Roulette tests |
| test/PantryPalGUITest.java | Codex/GenAI authored 13 EDT-based tests using actual Swing controls, including roulette selection and recovery |
| scripts/run.ps1 | Codex/GenAI added a launcher using the existing course-compatible toolchain |
| IMPLEMENTATION_NOTES.md | Codex/GenAI recorded the approved decision, implementation details, and limits |

Team review or testing beyond the work shown in this chat is not asserted.
These notes support a later README reflection/attribution update; they do not
replace the README sections required for submission.
