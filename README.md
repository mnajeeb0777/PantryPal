# PantryPal
PantryPal is an application that helps college students keep track of the ingredients they currently have and the recipes they can make with them. Users will be able to add, remove, search for, and view ingredients in their pantry, as well as save and look up recipes.

# Reflection
What changed from spec?
Class & Method Structure: The methods and classes that were specifically described in the spec, were refined to properly function utilizing standardized variables (Ingredient and Recipe).
Where Validation Lives: The validation was primarily kept within application/domain classes (Ingredient, Recipe, Pantry, RecipeBook) rather than in the generic data structure. The check for invalid inputs like empty strings, nonexistent units, negative quantities, etc, is performed at object instantiation. This ensures that invalid data doesn't get stored.
Test Cases: Test conditions that were described in the spec were implemented as well as additional test cases checking for proper returns on getter and setter statements. Ultimately, valid test cases were created to thoroughly test for null checks, invalid inputs, and false units across all public methods.

What changed in the LinkedChain?
We kept our LinkedChain as is and made no alterations

What happened to your unknowns?
Unit Compare: IngredientMatcher.areUnitsComaptible() and convertQuantity() eliminate any confusions with difference in units. Exceptions are thrown when there are invalid conversions.
Similar Names: We created IngredientMatcher, which ties two ingredients that use different aliases under one variable, to eliminate confusion (Ex: Scallions and Green Onions)
Broke Mode: We implemented a broke mode which when setBrokeModeEnabled() is called, prices searches using PriceFinder are more specific.
Where to find datasets: We are still working on harvesting data from public datasets such as KrogerAPI and WalmartAPI to provide necessary elements for our class to work.

What doesn't work yet?
File save: We are still working on writing up complete snapshots to Path filePath using DataStore.save() to save files and loading that back up with DataStore.load() (Very doable).
Updating pantry when cooking: We are still working on a proper deduction system, which removes the ingredient while it is being cooked, so the user isn't provided with a false number of ingredients in their pantry.







# Attribution
Momin:
Jamie:
Zolo: Drafted and Completed README (Reflection, Next Steps)
