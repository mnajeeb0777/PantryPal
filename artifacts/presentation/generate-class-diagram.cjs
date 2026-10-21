const fs = require('fs');
const path = require('path');
const sharp = require('C:/Users/Momin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const p=[];
const esc=s=>s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
function t(x,y,s,size=23,color='#284B37',weight=400,mono=false,anchor='start') {p.push(`<text x="${x}" y="${y}" font-size="${size}" fill="${color}" font-weight="${weight}" font-family="${mono?'Consolas, monospace':'Segoe UI, Arial, sans-serif'}" text-anchor="${anchor}">${esc(s)}</text>`);}
function r(x,y,w,h,fill='#FFFFFF',stroke='#CDDFCD',rad=22){p.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rad}" fill="${fill}" stroke="${stroke}" stroke-width="2"/>`);}
function divider(x,y,w){p.push(`<path d="M${x} ${y} h${w}" stroke="#DFE9DC" stroke-width="2"/>`);}
function arrow(d,dash=false){p.push(`<path d="${d}" fill="none" stroke="#598565" stroke-width="3" ${dash?'stroke-dasharray="8 7"':''} marker-end="url(#a)"/>`);}
function heading(x,y,n,s){r(x,y-25,31,31,'#DEECD8','#DEECD8',9);t(x+15.5,y-3,n,17,'#426C4B',700,false,'middle');t(x+45,y,s,21,'#55775D',600);}
p.push(`<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080" role="img" aria-labelledby="title description"><title id="title">PantryPal class architecture</title><desc id="description">PantryPalGUI contains RecipeImportPanel and BrokeModePanel. These views call PantryPalController. The controller owns Pantry and RecipeBook, coordinates RecipeMatcher and IngredientMatcher, saves PantryPalData through DataStore, and uses RecipeImporter and PriceFinder for external services. Selected class relationships and methods are shown.</desc><defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto"><path d="M1 1 L9 5 L1 9" fill="none" stroke="#598565" stroke-width="1.6" stroke-linejoin="round"/></marker></defs><rect width="1920" height="1080" fill="#F7FAF5"/>`);
r(60,54,12,75,'#285D40','#285D40',6);
t(96,109,'PantryPal',54,'#214D36',700);
t(383,109,'/ Class architecture',40,'#56755D',400);
t(63,157,'Java Swing application • key classes, responsibilities and dependencies',25,'#647C67');
t(1858,99,'CS 2114',21,'#55775D',700,false,'end');
heading(60,210,'1','USER INTERFACE');
heading(600,210,'2','APPLICATION CONTROLLER');
heading(1360,210,'3','DOMAIN MODEL');
// Arrows show callers/references, not inheritance.
arrow('M500 395 H600'); t(550,377,'calls',19,'#55775D',500,false,'middle');
arrow('M1260 395 H1360');t(1310,377,'owns',19,'#55775D',500,false,'middle');
p.push('<path d="M930 565 V620 M330 620 H1560" fill="none" stroke="#598565" stroke-width="3"/>');
arrow('M330 620 V680');arrow('M930 620 V680');arrow('M1560 620 V680');
r(850,600,160,36,'#F7FAF5','#F7FAF5',8);t(930,626,'coordinates',20,'#55775D',500,false,'middle');
// UI classes.
r(60,235,440,330);
t(88,280,'PantryPalGUI',29,'#285D40',700,true);
t(88,315,'Main window, pantry and recipe screens',21,'#647C67');
divider(88,338,384);
t(88,373,'Contains panels:',20,'#647C67');
t(88,411,'RecipeImportPanel',25,'#284B37',600,true);
t(88,442,'Search, review and save import drafts',20,'#647C67');
t(88,483,'BrokeModePanel',25,'#284B37',600,true);
t(88,514,'Review products and show price estimates',20,'#647C67');
t(88,546,'All three views call the controller.',19,'#647C67');
// Central orchestrator.
r(600,235,660,330,'#285D40','#285D40');
t(631,285,'PantryPalController',33,'#FFFFFF',700,true);
t(631,324,'Coordinates state, matching, saving and API access',23,'#DEEDDD');
p.push('<path d="M631 346 H1229" stroke="#548166" stroke-width="2"/>');
t(631,383,'compareRecipeWithPantry()',24,'#FFFFFF',400,true);
t(631,420,'selectMealRouletteRecipe()',24,'#FFFFFF',400,true);
t(631,457,'cookRecipe()   •   saveData()',24,'#FFFFFF',400,true);
t(631,503,'Saved drafts:',20,'#DEEDDD');
t(631,539,'LinkedChain<RecipeImportDraft>',23,'#FFFFFF',400,true);
// Model fields make container relationships explicit.
r(1360,235,500,330);
t(1388,278,'Pantry',27,'#285D40',700,true);
t(1388,309,'LinkedChain<Ingredient>',22,'#647C67',400,true);
t(1388,352,'RecipeBook',27,'#285D40',700,true);
t(1388,383,'LinkedChain<Recipe>',22,'#647C67',400,true);
divider(1388,408,444);
t(1388,446,'Recipe',25,'#284B37',600,true);
t(1510,446,'→ ingredients + Effort',22,'#647C67');
t(1388,483,'Ingredient',25,'#284B37',600,true);
t(1560,483,'→ quantity + Unit',22,'#647C67');
t(1388,535,'Recipe also stores ingredients in LinkedChain.',19,'#647C67');
heading(60,658,'4','MATCHING & RESULTS');
heading(660,658,'5','LOCAL PERSISTENCE');
heading(1260,658,'6','EXTERNAL INTEGRATIONS');
r(60,680,540,325);
t(88,724,'RecipeMatcher',27,'#285D40',700,true);
t(88,755,'compareRecipe() allocates pantry stock',21,'#647C67');
arrow('M110 772 V802');t(137,793,'uses',19,'#647C67');
t(88,836,'IngredientMatcher',26,'#284B37',600,true);
t(88,867,'Name matching and compatible unit conversion',20,'#647C67');
divider(88,887,484);
t(88,917,'RecipeMatcher returns:',19,'#647C67');
t(88,950,'RecipeMatchResult',24,'#284B37',600,true);
t(88,980,'contains IngredientMatch[]; canCook()',20,'#647C67',400,true);
r(660,680,540,325);
t(688,724,'DataStore',27,'#285D40',700,true);
t(688,755,'load() / save() ↔ data.properties',22,'#647C67',400,true);
divider(688,777,484);
t(688,815,'PantryPalData',26,'#284B37',600,true);
t(688,846,'Snapshot read and written by DataStore',20,'#647C67');
t(688,884,'Ingredient[]',22,'#284B37',400,true);
t(688,917,'Recipe[]',22,'#284B37',400,true);
t(688,950,'RecipeImportDraft[]',22,'#284B37',400,true);
t(688,982,'Autosave after changes; reload on startup',20,'#647C67');
r(1260,680,600,325);
t(1288,724,'RecipeImporter',26,'#285D40',700,true);
arrow('M1545 716 H1610',true);t(1624,724,'TheMealDB',24,'#284B37',600);
t(1288,762,'Returns RecipeImportDraft objects for review',21,'#647C67');
divider(1288,786,544);
t(1288,828,'PriceFinder',26,'#285D40',700,true);
arrow('M1500 820 H1570',true);t(1585,828,'SerpApi / Walmart',23,'#284B37',600);
t(1288,865,'Returns offers through PriceFinder.SearchResult',20,'#647C67');
t(1288,898,'StoreOffer holds package, price and store data',20,'#647C67');
divider(1288,919,544);
t(1288,953,'Json',23,'#284B37',600,true);t(1362,953,'Shared API response parser',21,'#647C67');
t(1288,982,'PriceFinder also uses IngredientMatcher.',20,'#647C67');
t(60,1048,'Selected class relationships • arrows show usage, not inheritance',18,'#6B7D6B');
p.push('<path d="M1200 1042 H1250" stroke="#598565" stroke-width="3"/><path d="M1515 1042 H1565" stroke="#598565" stroke-width="3" stroke-dasharray="8 7"/>');
t(1265,1048,'Java dependency',18,'#6B7D6B');t(1580,1048,'External API call',18,'#6B7D6B');
p.push('</svg>');
const svg=p.join('\n');
fs.writeFileSync(path.join(__dirname,'pantrypal-system-diagram.svg'),svg);
(async()=>{
await sharp(Buffer.from(svg)).resize(3840,2160).png().toFile(path.join(__dirname,'pantrypal-system-diagram.png'));
await sharp(Buffer.from(svg)).resize(1600,900).png().toFile(path.join(__dirname,'preview.png'));
console.log('Class diagram exported as SVG and 4K PNG.');
})().catch(e=>{console.error(e);process.exit(1);});
