# PantryPal

Virginia Tech CS 2114, Project 2, Group 62 — Deliverable 3.

A prototype for managing pantry ingredients and recipes, comparing stock,
cooking with inventory deductions, and Meal Roulette. **Data lasts only for the
current session.** Click **Load demo data** in an empty session to try it.

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

## Reflection

- **Changes from the spec:** We made a few changes to our original project plan as we started developing PantryPal.

  - To meet the project requirements, we decided to use Java Swing for the interface and temporarily store data only while the application is running. Because of this, the app does not automatically save changes yet. **Decided via group discussion.**
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
  - Choosing an external recipe provider and obtaining reliable nearby grocery prices remain open. Pricing is still the largest MVP risk.

- **Unfinished work:**

  - `PantryPalData`, `DataStore`, `RecipeImporter`, `StoreOffer`,
  and `PriceFinder` still need to be completed.
  - Persistence/autosave, external recipe search, and Broke Mode are unfinished.
  - Photo recognition and social media import stretch goals are also unimplemented.

## Next Steps

| Order | Work | Team Member | New tests |
| --- | --- | --- | --- |
| 1 | Implement `PantryPalData`/`DataStore` and connect autosave. | Zolo | `PantryPalDataTest`, `DataStoreTest`: malformed/missing files, I/O failure, and failed-save/load recovery. |
| 2 | Choose a provider; implement `RecipeImporter` and search/import UI. | Jamie | `RecipeImporterTest`: valid/empty responses, bad data/units, duplicates, timeouts, and controller/GUI recovery. |
| 3 | Choose a pricing source; implement `StoreOffer`, `PriceFinder`, and Broke Mode. | Momin | `StoreOfferTest`, `PriceFinderTest`: invalid prices/locations, package rounding, cheapest selection, no offers, and provider failures. |
| 4 | Review overall application, redesign UI, ensure rubric requirements are met. | All three members | - |

**Stretch goals:**
  - **Meal Roulette:** Committed and working.
    - Our existing tests check that it only selects eligible recipes, handles cases where no recipes qualify, supports unit conversions, and does not change pantry inventory.
    - Once saving and recipe importing are complete, we plan to add tests that check how Meal Roulette works with those features.
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
| `src/PantryPalData.java` | Zolo | Skeleton class written by GenAI. |
| `src/DataStore.java` | Zolo | Skeleton class written by GenAI. |
| `test/EnvironmentSmokeTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Recipe.java` | Jamie | Written with GenAI assistance; Jamie's contribution to confirm. |
| `test/RecipeTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/RecipeBook.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/RecipeBookTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/IngredientMatch.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/IngredientMatchTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/Effort.java` | Jamie | Written by Jamie, reviewed by GenAI. |
| `src/RecipeImporter.java` | Jamie | Skeleton class written by GenAI. |
| `src/StoreOffer.java` | Jamie | Skeleton class written by GenAI. |
| `test/TestFixtures.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/RecipeMatcher.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatcherTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/RecipeMatchResult.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatchResultTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalController.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/PantryPalControllerTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalGUI.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/PantryPalGUITest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PriceFinder.java` | Momin | Skeleton class written by GenAI. |
| `src/LinkedChain.java` | Instructor | Provided by instructor; unchanged. |
| `README.md` | Zolo | Drafted Reflection and Next Steps; later edits assisted by GenAI. |

**GenAI use:** Codex assisted with skeleton classes, logic recommendations,
integration, tests, and documentation.
