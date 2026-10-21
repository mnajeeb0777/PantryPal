# PantryPal

Virginia Tech CS 2114, Project 2, Group 62 — Deliverable 3.

A prototype for managing pantry ingredients and recipes, comparing stock,
cooking with inventory deductions, and Meal Roulette.

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
| `src/PantryPalData.java` | Zolo  | Implemented by GenAI |
| `test/PantryPalDataTest.java` | Zolo  | Written by GenAI; reviewed by Zolo. |
| `src/DataStore.java` | Zolo  | Implemented by GenAI |
| `test/DataStoreTest.java` | Zolo  | Written by GenAI; reviewed by Zolo. |
| `test/PantryPalPersistenceTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `test/EnvironmentSmokeTest.java` | Zolo | Written by GenAI; reviewed by Zolo. |
| `src/Recipe.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/RecipeTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/RecipeBook.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/RecipeBookTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/IngredientMatch.java` | Jamie | Written by Jamie; Javadocs, integration, and review by GenAI. |
| `test/IngredientMatchTest.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/Effort.java` | Jamie | Written by Jamie, reviewed by GenAI. |
| `src/RecipeImporter.java` | Jamie  | TheMealDB client implemented by GenAI |
| `src/RecipeImportDraft.java` | Jamie  | Written by GenAI; reviewed by Jamie. |
| `test/RecipeImporterTest.java`, `test/RecipeHttpStub.java` | Jamie  | Written by GenAI; reviewed by Jamie.|
| `src/RecipeImportPanel.java`, `test/RecipeImportPanelTest.java` | Jamie  | Import UI and workflow tests implemented by GenAI |
| `src/StoreOffer.java`, `test/StoreOfferTest.java` | Jamie  | Written by GenAI; reviewed by Jamie. |
| `test/TestFixtures.java` | Jamie | Written by GenAI; reviewed by Jamie. |
| `src/RecipeMatcher.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatcherTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/RecipeMatchResult.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI. |
| `test/RecipeMatchResultTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalController.java` | Momin | Written by Momin; Javadocs, integration, and review by GenAI.|
| `test/PantryPalControllerTest.java` | Momin | Written by GenAI; reviewed by Momin. |
| `src/PantryPalGUI.java` | Momin | Written by GenAI |
| `test/PantryPalGUITest.java` | Momin | Written by GenAI; reviewed by Momin. Persistence cases added/executed by GenAI; review pending. |
| `src/PriceFinder.java`, `test/PriceFinderTest.java`, `test/PriceFixtures.java` | Momin | Implemented by GenAI |
| `src/BrokeModePanel.java`, `test/BrokeModePanelTest.java` | Momin | Implemented by GenAI
| `src/Json.java`, `test/JsonTest.java` | Moin | Existing JSON parser extracted for shared use and tested by GenAI|
| `test/PantryPalAcceptanceTest.java` | Momin | Final "Review" conducted by GenAI |
| `README.md` | All members | Dependencies written by AI |

**GenAI use:** Codex assisted with skeleton classes, logic recommendations,
integration, tests, classes involving APIs/parsing, documentation, and review.
