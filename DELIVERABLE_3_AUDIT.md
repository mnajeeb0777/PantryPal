# Deliverable 3 audit

Audit date: October 14, 2026.

Scope: the implemented, session-only Swing prototype and Meal Roulette.
This is an evidence review against the supplied Deliverable 3 rubric, not an
instructor-awarded grade. README work and unfinished services were explicitly
excluded from this increment. No README, docs/ file, or instructor chain source
was changed.

## Criterion results

| Criterion | Implemented-slice finding | Evidence and remaining boundary |
| --- | --- | --- |
| Working Application Slice | Exceeds technical conditions verified | All 18 source files compile. The real main entry point opens a desktop window. GUI tests exercise pantry entry, recipe creation, comparison, cooking, and recovery. Meal Roulette is a working stretch goal listed in the original scope. |
| LinkedChain Is Reused | Exceeds technical conditions verified | Pantry/Recipe store and retrieve Ingredient; RecipeBook stores and retrieves Recipe; RecipeMatcher stores and retrieves IngredientMatch. One generic LinkedChain implementation serves all three types. |
| Used Through Its Public API | API requirement verified; Exceeds explanation prepared | Application code uses public chain methods only, with no Node access, reflection, subclass, or type-specific chain copies. Big-O and differences from spec estimates are recorded in IMPLEMENTATION_NOTES.md. The team must still be able to explain these costs during grading; a code audit cannot certify team understanding. |
| JUnit Tests | Exceeds conditions verified for implemented operations | 113 tests execute in 11 test classes, with zero failures, ignored tests, or assumption skips in the clean Windows audit run. Normal and invalid/boundary coverage is mapped below. Empty collections and absent removals are covered. Unfinished service paths remain explicitly excluded. |
| Bad Input Survives | Exceeds technical conditions verified for scoped pantry cases | Exact specified messages, model exceptions, GUI correction/retry, unchanged state on rejection, empty searches, absent updates/removals, and empty-pantry removal are exercised. |
| Reflection | Deferred; not claimed as Meets/Exceeds | Required README section is not written. Implementation notes provide supporting facts but do not replace the README requirement. |
| Next Steps | Deferred; not claimed as Meets/Exceeds | Required README section, actual team ownership, and stretch-goal commitments/drops still need to be completed. |
| Attribution and usage instructions | Deferred submission requirements | Attribution and launch/test information exist in implementation notes. README still needs the requested per-class/test-file attribution, team contributions, GenAI note, and instructions. |

The supplied scored rubric lists seven criteria. Attribution and compile/run
instructions are also required in its submission directions, even though they
do not have separate scored rows in the supplied text.

## Fixes and new evidence from this audit

1. Added exact GUI assertions for the specification's error messages, including
   oversized numeric text, NaN, both infinities, invalid/control characters, and
   overlong ingredient names. Previously many GUI tests only checked that an
   error appeared.
2. The new test exposed negative infinity receiving "Quantity must be greater
   than 0." Ingredient now checks nonfinite/oversized amounts first, so all
   nonfinite quantities receive the specified finite/range message. Finite
   negative values and zero keep their original message. Validation remains in
   Ingredient and failed updates remain atomic.
3. Added stale-selection tests: when a displayed entry has since been removed,
   update/remove return the specified "Ingredient not found." response and
   preserve the other pantry entries.
4. Added a normal-path test for PantryPalGUI.main: it actually opens the desktop
   window, verifies the empty pantry display, and closes the unchanged session.
   Existing tests already reject invalid main arguments and invalid GUI
   construction. The new native test reports an assumption skip on a headless
   host; it did not skip in this audit. The changed-session confirmation dialog
   itself is not driven by this test.
5. Corrected addCustomRecipe's effort Javadoc: effort is required, with UNKNOWN
   allowed; only the filtering method uses null to disable a filter.

## Public application-method test map

Test names below are in the corresponding class's test file unless another
file is named. This lists authored application methods, not inherited Swing or
Object operations that the application does not override.

For no-argument getters, constructors, and primitive-boolean operations, an
invalid argument does not exist. Boundary states, false outcomes, empty
collections, unknown values, ownership checks, and invalid construction cover
the meaningful cases. The specification itself marks several result getters'
bad-input cases as not applicable.

### Ingredient and Recipe

| Public method(s) | Normal coverage | Invalid/boundary coverage |
| --- | --- | --- |
| Ingredient(full arguments) | testConstructorAndGetters | testConstructorRejectsBadInput |
| Ingredient(copy) | testCopyConstructor | testCopyConstructor rejects null |
| Ingredient.getId/getName/getQuantity/getUnit | testConstructorAndGetters | Same test checks limits/tiny quantities; invalid instances rejected by construction |
| Ingredient.updateDetails | testUpdateDetails | testUpdateDetailsIsAtomic |
| Ingredient.setQuantity | testSetQuantityAndInvalidUpdates | Same test checks invalid quantities and unchanged state |
| Ingredient.equals/hashCode | testIdentityAndHashCode | Different ID, null, unrelated object, and stable hash after mutation |
| Recipe(full arguments) | testConstructorAndGetters | testInvalidRequirements, testInvalidMetadata |
| Recipe(copy) | testCopyConstructor | Same test rejects null and checks nested ownership |
| Recipe.getId/getName/getPrepTimeMinutes/getCookTimeMinutes/getEffort/getSourceUrl/getExternalRecipeId | testConstructorAndGetters | testUnknownAndBoundaryMetadata |
| Recipe.getRequiredIngredients/getInstructions | testDefensiveCopiesAndOrder | testUnknownAndBoundaryMetadata, testInvalidRequirements, testInvalidMetadata |
| Recipe.equals/hashCode | testEqualityAndHashCode | Different ID, null, and unrelated object |

### Pantry and RecipeBook

| Public method(s) | Normal coverage | Invalid/boundary coverage |
| --- | --- | --- |
| Pantry() | testEmptyPantry | Empty queries, missing update/removal, empty snapshots |
| Pantry(initial entries) | testInitialEntries | Null array/entry and duplicate IDs; empty array in testEmptyPantry |
| Pantry.addIngredient | testAddAndDuplicateIdentity | Duplicate ID and null entry; repeated names remain valid |
| Pantry.getAllIngredients | testSnapshotOwnership | testEmptyPantry; returned data cannot mutate pantry |
| Pantry.findIngredientById | testFindAndRemove | Missing, null, blank IDs; testEmptyPantry |
| Pantry.searchIngredients | testSearch | Null, blank, unmatched queries; testEmptyPantry |
| Pantry.updateIngredient | testUpdateAndBadInput | Invalid fields/ID, absent ID, unchanged state |
| Pantry.removeIngredient | testFindAndRemove | Absent/invalid IDs, repeated removal, testEmptyPantry |
| Pantry.deductIngredients | testDeductAcrossEntriesAndUnits, testMassDeduction | testFailedDeductionLeavesStockUnchanged, testDeductionValidationAndEmptyRequirements, repeated requirements and floating-point boundary tests |
| Pantry.size/isEmpty | testInitialEntries, testFindAndRemove | testEmptyPantry, depletion in testRepeatedRequirements |
| RecipeBook() | testEmptyBook | Empty search/filter/snapshot and absent removal |
| RecipeBook(initial recipes) | testConstructorAndInvalidLists | Null array/entry, duplicate IDs, empty initial array |
| RecipeBook.addRecipe | testAddAndDuplicates | Duplicate IDs and null; duplicate names accepted |
| RecipeBook.getAllRecipes | testSnapshots | testEmptyBook and copy isolation |
| RecipeBook.findRecipeById | testFindAndRemove | Absent/null/blank IDs and empty book |
| RecipeBook.searchRecipes | testSearch | Blank/null/unmatched queries and empty book |
| RecipeBook.filterRecipes | testFilters | Null filters, unknown times, no matches, negative time, empty book |
| RecipeBook.removeRecipe | testFindAndRemove | Repeated/invalid/absent removal and testEmptyBook |
| RecipeBook.size/isEmpty | testConstructorAndInvalidLists, testFindAndRemove | testEmptyBook and removal of last entry |

### Matching and result classes

| Public method(s) | Normal coverage | Invalid/boundary coverage |
| --- | --- | --- |
| IngredientMatcher() | testDefaultNormalizationAndMatching | Default matcher rejects invalid names; no constructor arguments |
| IngredientMatcher(alias map) | testCustomAliasesAndCopying | testInvalidAliasMaps |
| IngredientMatcher.normalizeName/namesMatch | testDefaultNormalizationAndMatching, testLocaleIndependentNormalization | Null/blank names, distinct foods, no raw/cooked substitution |
| IngredientMatcher.areUnitsCompatible | testCompatibilityGroups | Every cross-category combination and null units |
| IngredientMatcher.convertQuantity | testConversions | testInvalidConversions, zero, underflow/overflow |
| IngredientMatch constructor | testKnownShortage, testRequirementOwnership | testInvalidInput, testUncomparableQuantity |
| IngredientMatch.getRequiredIngredient/getAvailableQuantity | testKnownShortage | testRequirementOwnership, testUncomparableQuantity, testZeroAvailable |
| IngredientMatch.getMissingQuantity/canCompareQuantities/isSufficient | testKnownShortage, testExactAndExcessAvailability | Unknown, zero stock, invalid construction, real/subnormal shortage, tolerance parity |
| RecipeMatchResult constructor | testIdentityAndEntryOrder | testInvalidArrays, testInvalidAndBoundaryRecipeIds, testDuplicateRequirementIds |
| RecipeMatchResult.getRecipeId/getIngredientMatches | testIdentityAndEntryOrder | testDefensiveCopies, uppercase UUID boundary |
| RecipeMatchResult.getMissingIngredients | testMissingIngredients | testAllRequirementsSatisfied, testCookingFailures, testRoundoffAndRealShortages |
| RecipeMatchResult.getUncomparableIngredients | testUncomparableIngredients | testAllRequirementsSatisfied, testCookingFailures |
| RecipeMatchResult.canCook | testAllRequirementsSatisfied | testCookingFailures, invalid empty-array construction, real/tiny shortages |
| RecipeMatcher constructor | testSuppliedMatcher and other matcher tests | testInvalidInputs rejects null service |
| RecipeMatcher.compareRecipe | testCappedAllocation, testMultipleEntriesAndConversions, testDefaultAliases, testPantryAndRecipeBookIntegration | testInvalidInputs, testAbsentStock, testRepeatedRequirements, testMixedUnits, testIncompatibleOnly, testUnrepresentableConversion, numeric parity |

### Session controller and GUI

| Public method(s) | Normal coverage | Invalid/boundary coverage |
| --- | --- | --- |
| PantryPalController(), four-argument constructor | testSessionConstruction, testSuppliedMatcher | Null matcher rejected; optional absent services explicitly guarded |
| hasUnsavedChanges | testSessionConstruction | Invalid actions leave a fresh session clean; unsupported saving cannot clear dirty state |
| addPantryIngredient | testPantryWorkflow | testInvalidPantryActions |
| getPantryIngredients/findPantryIngredientById/searchPantryIngredients | testPantryWorkflow | testSessionConstruction, testInvalidPantryActions, absent queries and snapshot ownership |
| updatePantryIngredient/removePantryIngredient | testPantryWorkflow | testInvalidPantryActions, including absent IDs and atomic invalid updates |
| addCustomRecipe | testRecipeWorkflow | testInvalidRecipeActions |
| getSavedRecipes/findSavedRecipeById/searchSavedRecipes/filterSavedRecipes | testRecipeWorkflow | testInvalidRecipeActions, no-result searches/filters, testSessionConstruction |
| removeSavedRecipe | testRecipeWorkflow | testInvalidRecipeActions and repeated removal |
| importRecipe (already-shaped recipe data) | testImportSnapshot | Duplicate ID and null recipe |
| compareRecipeWithPantry/cookRecipe | testCompareThenCook, testSuppliedMatcher | testFailedCooking, null recipe, stale comparison, shortage without partial deduction |
| selectMealRouletteRecipe | testRouletteMultipleCandidatesAndIsolation, testRouletteAliasesAndConversions | testRouletteWithoutCookableRecipes, testRouletteEligibilityRules, testRouletteConversionFailure |
| setBrokeModeEnabled/isBrokeModeEnabled | testUnavailableExternalServices toggles true/false | Starts false and does not dirty collections; service absence stays guarded |
| PantryPalGUI(controller) | setUp plus all GUI button workflows | testConstructionAndMainArguments rejects null and wrong thread |
| PantryPalGUI.main | testDesktopEntryPoint | testConstructionAndMainArguments rejects null/nonempty arguments |

Controller loadData/saveData, searchExternalRecipes, findNearbyOffers,
findCheapestOffer, calculateRequiredPackages, and calculatePurchaseCost have
tests for unavailable-service recovery only. Their successful configured-service
paths depend on unfinished classes and are not counted as fully tested features.

The GUI's additional tests exercise input parsing, expected messages, sorting
and selection identity, invalid draft recovery, recipe filters, known/unknown
comparison states, and Roulette. These are behavior tests through actual Swing
buttons, rather than tests that duplicate event-handler implementation.

## Bad-input response trace

GUI errors add the common prefix "Please try again: " to these model/parser
messages and leave entered values available for correction.

| Scoped case | Validation/recovery location | Verified response |
| --- | --- | --- |
| Negative finite or zero quantity | Ingredient validation, caught by GUI | "Quantity must be greater than 0." |
| Text instead of quantity | GUI numeric parsing | "Enter a valid number." |
| Oversized number, NaN, either infinity | Ingredient validation, caught by GUI | "Enter a finite quantity greater than 0 and no greater than 1,000,000." |
| Blank/invalid/control-character ingredient name | Ingredient validation, caught by GUI | "Enter a valid ingredient name." |
| Ingredient name longer than 100 | Ingredient validation, caught by GUI | "Ingredient names cannot exceed 100 characters." |
| Unmatched pantry search | Pantry returns empty array; GUI displays outcome | "No ingredients found." |
| Missing update/removal target | Pantry returns false; GUI displays outcome | "Ingredient not found."; other entries unchanged |
| Removal from empty pantry | Pantry safely returns false; GUI guards empty selection | "Your pantry is empty." |

Evidence: PantryPalGUITest.testExactSpecificationMessages,
testStalePantryActions, testPantryWorkflow, testBadPantryInputRecovery, and
testInvalidUpdateAndMissingSelection, supported by IngredientTest/PantryTest.

## Chain and complexity evidence

- The instructor reference and src/LinkedChain.java match after normalizing
  CRLF/LF line endings. No instructor implementation changes were made.
- Pantry uses public add, contains, remove, size, isEmpty, and toArray.
  Recipe uses add/toArray; RecipeBook uses add/contains/remove/size/isEmpty/
  toArray; RecipeMatcher uses add/size/toArray.
- Application traversal uses array snapshots instead of repeatedly indexing a
  linked chain. Restoring result order is a linear array reversal.
- The Big-O table in IMPLEMENTATION_NOTES.md includes constructors, snapshots,
  queries, mutations, matching, comparison, controller delegation, GUI refresh,
  and Roulette. Flags/ID/scalar getters are constant-time; snapshot getters
  include copying costs. Ingredient names and UUIDs have bounded lengths.
- Be ready to explain: chain contains is O(n), not the spec table's O(1);
  snapshot access is not O(1); recipe copying includes nested data; repeated
  addToEnd would be quadratic, so bulk loading uses reverse front insertion;
  comparing r requirements with p pantry entries costs O(r*p*L + p + r),
  including name normalization of length L; Roulette compares every saved
  recipe, rather than choosing first and merely hoping it can be cooked.

The spec's contains/count return descriptions also conflict with the supplied
implementation. The actual public API is authoritative: contains returns
boolean, count returns an occurrence count, and absent membership is false.
The test plan's value-based equals and Pantry NullPointerException descriptions
were superseded by the user-approved class reference: stable-ID equality and
IllegalArgumentException. Empty recipe/comparison rejection and session-only
storage are previously approved deviations, not hidden missing behavior.

## Verification and remaining exclusions

- Clean, newly created output directories: 18 application/support Java sources,
  12 test/support sources, 11 test classes.
- Runtime/compiler: JDK 17.0.8.1; source/API target: Java 11; course JUnit 4.12
  through student.jar.
- Tests: 113 executed, zero failures, zero ignored, zero assumption skips.
- Course Eclipse compiler preferences: implemented classes and all tests checked
  with compiled future skeleton dependencies on the classpath. The skeletons'
  pre-existing unused-field warnings remain outside the completed-slice checks.
- Course student.jar still emits its known Java 17 SecurityManager deprecation
  warning. It does not prevent execution.
- Remaining implementation: DataStore, PantryPalData, RecipeImporter,
  PriceFinder, StoreOffer, their successful controller paths, and corresponding
  normal/error tests. Photo recognition and social-media import are not built.
- Remaining submission: README Reflection, Next Steps, actual teammate owners
  and contributions, per-file attribution/GenAI note, and usage instructions.
- Remaining human verification: team explanation of complexity/design, and
  review of the final deliverable. No overall Exceeds score is claimed for
  excluded or unfinished work.
