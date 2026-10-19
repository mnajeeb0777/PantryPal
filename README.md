# PantryPal

Virginia Tech CS 2114, Project 2, Group 62 — Deliverable 3.

A prototype for managing pantry ingredients and recipes, comparing stock,
cooking with inventory deductions, and Meal Roulette. **Pantry ingredients and
recipes now load at startup and autosave after changes.** Click **Load demo data**
when both collections are empty to try it.

## Build, run, and test

Requires **JDK 17**, Java 11 source compatibility, and the course
`CS2-Support/student.jar` (`student.TestCase` and JUnit 4.12).
From the repository root in PowerShell:

```powershell
.\scripts\build.ps1  # Compile only
.\scripts\run.ps1    # Build and launch the app
.\scripts\test.ps1   # Build and execute tests
```

The scripts look for course libraries in your Documents `eclipse-workspace`.
For other locations, pass `-JavaHome 'C:\path\to\jdk-17'` and
`-EclipseWorkspace 'C:\path\to\workspace'`.

In Eclipse, import **Existing Projects into Workspace**.
Run `PantryPalGUI.java` as a **Java Application**, or the `test` folder as a
**JUnit Test**.

## Saving and recovery

The desktop app saves to `${user.home}/.pantrypal/data.properties` (normally
`C:\Users\<you>\.pantrypal\data.properties` on Windows). Hover over the save-status
message to see the full path. To use a separate file for a demo:

```powershell
.\scripts\run.ps1 -DataFile '.\build\demo-data.properties'
```

In Eclipse, the equivalent VM argument is
`-Dpantrypal.dataFile=C:\path\to\data.properties`.

- Missing files start an empty pantry and recipe book. They are created on save.
- Saves write a temporary file beside the destination and then replace the
  destination atomically. Unsupported atomic moves or I/O failures report errors.
- If saving fails, changes remain in memory and the GUI refreshes to show them.
  Fix the file/location problem and click **Save now**; do not repeat the edit or
  cooking action. Closing with unsaved changes offers save, discard, or cancel.
- If loading fails, edits and saves are blocked to protect the existing file.
  Restore a valid copy, or move the damaged file aside, then click **Retry loading**.
- The versioned Java properties format preserves IDs, units, quantities, recipe
  metadata, ingredient/step order, Unicode, and multiline text. No library was
  added. Run one app instance per save file; concurrent editing is not supported.
- The no-argument controller remains an isolated session-only option for tests.
  Automated persistence tests use temporary files under `build/test-data`, not
  your personal save file.

## Importing recipes from TheMealDB

Open **Import recipes**, enter a recipe name, and click **Search TheMealDB**.
Select a result to review its original measurements alongside editable ingredient
names, quantities, and units. Recognized quantities (including fractions) are
suggested; blank quantity/unit cells need correction. No ingredient is silently
discarded. Package sizes, cloves, bare numbers, ranges, and "to taste" require
review: do not equate a garlic clove with a whole garlic bulb or guess a tin size.
All quantities use PantryPal's existing units (including US customary volumes).

Leave prep/cook times blank and effort UNKNOWN when unavailable. Unknown cooking
times are excluded by an active maximum-time filter. Click **Import reviewed
recipe** to add the validated recipe and autosave it. Reimporting the same
TheMealDB recipe keeps your existing version; remove it first to import a new
reviewed version. Imported recipes work offline after saving, including pantry
comparison, cooking deductions, and Meal Roulette. Provider and original source
links are retained with the instructions and displayed in recipe details.

Search runs in the background, can be cancelled, and reports network/provider
errors without changing saved data. Click Search to retry. A failed autosave
keeps the import in memory: use **Save now**, as with other edits.

The desktop launcher uses TheMealDB's public educational key `1`; no signup or
new library is needed. HTTP setup is deferred until searching, so startup and
saved recipes work without networking. The no-argument controller still disables
external services for isolated tests. See the [API documentation](https://www.themealdb.com/documentation)
and [terms](https://www.themealdb.com/terms_of_use.php) before public distribution;
production/app-store use requires revisiting the supporter-key setup. This
integration imports text and source links, not artwork.

## Reflection

- **Changes from the spec:** We made a few changes to our original project plan as we started developing PantryPal.

  - The initial prototype used Java Swing and session-only storage. **Decided via group discussion.** Persistence is now implemented through `PantryPalData` and `DataStore`, with startup loading, autosave, and recovery controls. The proposed JSON file was replaced with versioned Java properties to use the existing JDK and preserve all model text without a new dependency. **GenAI implementation choice during the user-requested persistence increment; team review pending.**
  - We also clarified some parts of our original class design. For example, we decided to use unique IDs to compare certain objects and throw IllegalArgumentException when invalid values are provided. **Decided via reccomendation from GenAI.**
  - We also made sure that empty recipes or ingredient lists are handled properly and that ingredients are not counted multiple times when comparing a recipe to the pantry. **Decided via group discussion.**
  - We separated input handling between the interface and our classes. The GUI handles user input and allows users to correct mistakes, while the classes check whether the values are valid. **Decided via group discussion.**

- **LinkedChain:** We kept the instructor-provided LinkedChain class unchanged.

  - We also added IngredientMatch as a third type of object stored using LinkedChain, alongside Ingredient and Recipe. This was suggested with the help of GenAI and approved during development.
  - We only use the public methods provided by LinkedChain, keeping the application's other logic in separate classes.
  - We also corrected some of our original time complexity estimates. For example, contains() takes O(n) time rather than O(1), since it may need to check every node. Comparing a recipe to the pantry takes approximately O(r × p), where r is the number of required ingredients and p is the number of pantry entries.

- **Original unknowns:**
  - Name normalization, aliases, and compatible unit
  conversions now work through `IngredientMatcher`; different food forms are not
  automatically equivalent.
  - TheMealDB is now the implemented recipe provider. An immutable import draft
    preserves unresolved measurements; the importer and external-search controller
    return drafts instead of `Recipe` objects. The review screen creates validated
    recipes only after every row is resolved. These changes from the original
    class contracts are **GenAI-assisted implementation choices; team review
    pending**. JSON decoding lives inside `RecipeImporter`; measurement parsing
    lives inside `RecipeImportDraft`. Only two application classes were added:
    `RecipeImportDraft` and `RecipeImportPanel`. No additional library is needed.
    Provider IDs are
    namespaced (`themealdb:ID`), and deterministic recipe UUIDs prevent duplicate
    imports without changing save format version 1.
  - Spoonacular was not selected because its full-recipe storage restrictions
    conflict with the offline workflow. Kroger pricing still needs credentials
    and live validation. See [provider research](PROVIDER_RESEARCH.md).

- **Unfinished work:**

  - `StoreOffer`, `PriceFinder`, and Broke Mode still need implementation.
  - The team still needs to review the recipe-import workflow and class-contract
    changes against the course requirements.
  - Photo recognition and social media import stretch goals are also unimplemented.

## Next Steps

| Order | Work | Team Member | New tests |
| --- | --- | --- | --- |
| 1 | Review completed persistence and its format/recovery behavior. | Zolo | Added `PantryPalDataTest`, `DataStoreTest`, `PantryPalPersistenceTest`, and GUI persistence/recovery tests. |
| 2 | Review completed TheMealDB search/import UI and updated class contracts. | Jamie | `RecipeImporterTest` and `RecipeImportPanelTest`: parsing, review, duplicates, cancellation, failures, save/reopen/compare/cook. Six live requests also passed. |
| 3 | Validate Kroger pricing access; implement `StoreOffer`, `PriceFinder`, and Broke Mode for supported stores. | Momin | `StoreOfferTest`, `PriceFinderTest`: invalid prices/locations, package rounding, cheapest selection, no offers, and provider failures. |
| 4 | Review overall application, redesign UI, ensure rubric requirements are met. | All three members | Full import/save/reopen/compare/cook workflow. |

**Stretch goals:**
  - **Meal Roulette:** Committed and working.
    - Our existing tests check that it only selects eligible recipes, handles cases where no recipes qualify, supports unit conversions, and does not change pantry inventory.
    - Persistence tests now verify Roulette after reopening and confirm it does
      not rewrite the save. Recipe-provider tests now cover decoding, review,
      cancellation, duplicate imports, and save/reopen/compare/cook behavior.
  - **Social Media Recipe Import:** Dropped to focus on MVP.
  - **Photo Recognition:** Dropped to focus on MVP.
## Attribution

| File | Contributor | How |
| --- | --- | --- |
| `src/Ingredient.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `test/IngredientTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Pantry.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `test/PantryTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/IngredientMatcher.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `test/IngredientMatcherTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Unit.java` | Zolo | Written by Zolo; Javadocs, integration, and review by GenAI. |
| `src/PantryPalData.java` | Zolo (planned owner) | Implemented by GenAI; team review pending. |
| `test/PantryPalDataTest.java` | Zolo (planned owner) | Written and executed by GenAI; team review pending. |
| `src/DataStore.java` | Zolo (planned owner) | Implemented by GenAI; team review pending. |
| `test/DataStoreTest.java` | Zolo (planned owner) | Written and executed by GenAI; team review pending. |
| `test/PantryPalPersistenceTest.java` | Zolo / Momin (planned reviewers) | Controller/store integration tests written and executed by GenAI; team review pending. |
| `test/EnvironmentSmokeTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Recipe.java` | Jamie | Written with GenAI assistance; Jamie's contribution to confirm. |
| `test/RecipeTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/RecipeBook.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/RecipeBookTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/IngredientMatch.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/IngredientMatchTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/Effort.java` | Jamie | Written by Jamie, reviewed by GenAI. |
| `src/RecipeImporter.java` | Jamie (planned reviewer) | TheMealDB client and internal JSON decoding implemented by GenAI; client live-tested; team review pending. |
| `src/RecipeImportDraft.java` | Jamie (planned reviewer) | Import draft and conservative measurement parsing implemented by GenAI; team review pending. |
| `test/RecipeImporterTest.java`, `test/RecipeHttpStub.java` | Jamie (planned reviewer) | Written and executed by GenAI; team review pending. |
| `src/RecipeImportPanel.java`, `test/RecipeImportPanelTest.java` | Momin / Jamie (planned reviewers) | Import UI and workflow tests implemented by GenAI; team review pending. |
| `src/StoreOffer.java` | Jamie | Skeleton class written by GenAI. |
| `test/TestFixtures.java` | Jamie | Written by GenAI; reviewed by Jamie. Persistence fixture helpers added by GenAI; review pending. |
| `src/RecipeMatcher.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatcherTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/RecipeMatchResult.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatchResultTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalController.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. Persistence guards, draft search contract, and import duplicate checks added by GenAI; review pending. |
| `test/PantryPalControllerTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalGUI.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. Startup/recovery controls and import integration added by GenAI; review pending. |
| `test/PantryPalGUITest.java` | Momin | Written by GenAI; reviewed by Momin. Persistence cases added/executed by GenAI; review pending. |
| `src/PriceFinder.java` | Momin | Skeleton class written by GenAI. |
| `src/LinkedChain.java` | Instructor | Provided by instructor; unchanged. |
| `README.md` | Zolo | Drafted Reflection and Next Steps; later edits assisted by GenAI. |

**GenAI use:** Codex assisted with skeleton classes, logic recommendations,
integration, tests, and documentation.
