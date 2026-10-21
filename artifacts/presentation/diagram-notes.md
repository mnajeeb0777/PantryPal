# PantryPal class architecture diagram

The current diagram shows actual class names, selected methods, collection types and dependencies in the configured desktop application. Related classes are grouped into six areas. This is a class architecture overview rather than a complete UML diagram: getters, most fields and some cross-dependencies are omitted for readability.

| Diagram element | Source of truth |
| --- | --- |
| Your controls | `PantryPalGUI`, `RecipeImportPanel`, `BrokeModePanel` delegate actions to `PantryPalController`. |
| Coordinator | `PantryPalController` owns pantry, recipe book, saved drafts, matching services and configured storage/import/pricing services. |
| Pantry & recipe collection | `Pantry`, `RecipeBook`, `RecipeImportDraft`; snapshots are used for comparisons and saving. |
| Ingredient checks | `IngredientMatcher` normalizes names and converts compatible units; `RecipeMatcher` allocates available pantry quantities and reports shortages. |
| Meal Roulette & cooking | Controller selects randomly among cookable recipes; cooking rechecks availability and deducts ingredients through `Pantry`. |
| Saved on your computer | `DataStore` saves/loads `PantryPalData` containing pantry, recipes and unfinished imports. Mutations trigger autosave; failures have recovery controls. |
| TheMealDB | `RecipeImporter` calls TheMealDB. Import results remain drafts until measurements are reviewed and validated. |
| Walmart estimates | `PriceFinder` uses SerpApi's Walmart data; `BrokeModePanel` requires product selection. Costs are purchase estimates, not purchases. Quotes are not persisted. |

Solid arrows show calls, ownership or coordination as labeled, not inheritance. Dashed arrows show external API calls. The controller owns both matchers; RecipeMatcher also uses IngredientMatcher. PriceFinder and pantry deduction also use IngredientMatcher. The diagram groups these relationships to avoid crossed connectors.

SVG: editable text and vector shapes, 16:9 at 1920 × 1080.
PNG: 3840 × 2160, same 16:9 layout.

Regenerate the current version with `node artifacts/presentation/generate-class-diagram.cjs` in this workspace. The script uses the bundled Sharp library path on this computer. The earlier `generate-diagram.cjs` script regenerates the original plain-language overview and writes to the same export paths; use the class script for the revised version.
