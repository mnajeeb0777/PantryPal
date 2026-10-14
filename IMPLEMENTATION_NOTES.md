# PantryPal implementation decisions

These notes record the approved changes and the first core implementation
increment. The original README and reference documents remain unchanged.

## Approved third LinkedChain element type

**Decision:** When RecipeMatcher is implemented, its compareRecipe method will
collect comparison entries in a local `LinkedChain<IngredientMatch>`. It will
retrieve those entries as an `IngredientMatch[]` for the existing
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

**Status:** Approved by the user; documented here, but not implemented in this
increment. RecipeMatcher and the result-model classes remain skeletons. No
third-type grading credit is claimed yet.

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

No GUI, controller, persistence, external service, RecipeMatcher, or stretch
feature was implemented. Unit and Effort retain their existing enum values.

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

The supplied LinkedChain.contains is O(n), not the O(1) shown in one spec table.
Neither it nor any other supplied chain method was modified.

## Validation and attribution

Tests extend student.TestCase using the installed course JUnit 4.12 library.
Each implemented public operation is exercised with normal and invalid or
boundary scenarios. For no-argument getters, boundary values, empty collections,
copy isolation, and constructor rejection cover applicable invalid-state risks;
there are no getter input parameters to reject.

The tests include duplicate IDs/names, unknown times, empty collections, absent
removals, atomic failed updates/deductions, repeated requirements, alias/unit
matching, and conversion rounding. The final GUI/console input parsing and
re-prompt behavior remain future work; model tests do not claim to cover them.

| File(s) | Contribution in this increment |
| --- | --- |
| src/Ingredient.java, src/Recipe.java | Codex/GenAI implementation and validation; user supplied and approved the design |
| src/Pantry.java, src/RecipeBook.java | Codex/GenAI implementation, storage ownership, queries, and mutations |
| src/IngredientMatcher.java | Codex/GenAI implementation of the approved dependency |
| src/Unit.java, src/Effort.java | Codex/GenAI added enum-value documentation for course compiler checks; values unchanged |
| test/IngredientTest.java, test/RecipeTest.java | Codex/GenAI authored model tests |
| test/PantryTest.java, test/RecipeBookTest.java | Codex/GenAI authored collection tests |
| test/IngredientMatcherTest.java, test/TestFixtures.java | Codex/GenAI authored matcher tests and shared test support |
| IMPLEMENTATION_NOTES.md | Codex/GenAI recorded the approved decision, implementation details, and limits |

Team review or testing beyond the work shown in this chat is not asserted.
These notes support a later README reflection/attribution update; they do not
replace the README sections required for submission.
