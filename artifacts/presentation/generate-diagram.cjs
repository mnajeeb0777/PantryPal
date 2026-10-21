const fs = require('fs');
const path = require('path');
const sharp = require('C:/Users/Momin/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const out = __dirname;
const p = [];
const esc = s => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;');
function text(x,y,s,size=24,fill='#263F35',weight=400,anchor='start') {
  p.push(`<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" font-weight="${weight}" text-anchor="${anchor}">${esc(s)}</text>`);
}
function rect(x,y,w,h,fill='#FFFFFF',stroke='#D6E4D9',r=22,shadow=false) {
  p.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" stroke="${stroke}" stroke-width="2"${shadow?' filter="url(#shadow)"':''}/>`);
}
function line(d,dashed=false,both=true) {
  p.push(`<path d="${d}" fill="none" stroke="${dashed?'#749682':'#3F7F5B'}" stroke-width="3" ${dashed?'stroke-dasharray="8 8"':''} marker-end="url(#arrow)" ${both?'marker-start="url(#arrow)"':''}/>`);
}
function icon(x,y,type,color='#31734F') {
  const icons={
    leaf:'<path d="M8 31 C3 12 17 4 35 6 C36 23 27 35 8 31 Z"/><path d="M7 35 L27 15"/>',
    user:'<circle cx="20" cy="11" r="7"/><path d="M6 36 C6 20 34 20 34 36"/>',
    screen:'<rect x="3" y="4" width="34" height="25" rx="4"/><path d="M20 29 V36 M11 36 H29"/>',
    pantry:'<rect x="6" y="3" width="28" height="34" rx="4"/><path d="M6 19 H34 M12 10 V14 M12 25 V30"/>',
    check:'<circle cx="20" cy="20" r="16"/><path d="M11 20 L17 26 L29 13"/>',
    meal:'<circle cx="23" cy="21" r="12"/><path d="M3 4 V15 Q3 19 7 19 V37 M7 4 V15 M11 4 V15 M38 4 V37"/>',
    book:'<path d="M20 8 Q10 2 3 6 V34 Q10 30 20 36 Q30 30 37 34 V6 Q30 2 20 8 V36"/><path d="M8 13 L15 15 M25 15 L32 13"/>',
    cart:'<path d="M2 5 H8 L13 28 H33 L38 12 H10"/><circle cx="16" cy="35" r="2"/><circle cx="31" cy="35" r="2"/>',
    save:'<path d="M6 4 H29 L36 11 V36 H4 V4 Z"/><path d="M12 4 V15 H28 V4 M11 36 V23 H29 V36"/>'
  };
  p.push(`<g transform="translate(${x} ${y})" fill="none" stroke="${color}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">${icons[type]}</g>`);
}
p.push(`<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080" role="img" aria-labelledby="title desc">
<title id="title">PantryPal system overview</title>
<desc id="desc">A user interacts with the desktop screens. The application coordinator manages pantry ingredients, recipes and unfinished imports, checks ingredient availability, selects cookable meals, and deducts food when cooking. It saves and loads local data. It searches TheMealDB for recipes and SerpApi for Walmart product estimates. Users review imported measurements and select priced products.</desc>
<defs><filter id="shadow" x="-15%" y="-15%" width="130%" height="140%"><feDropShadow dx="0" dy="7" stdDeviation="10" flood-color="#1F513A" flood-opacity="0.07"/></filter>
<marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M1 1 L9 5 L1 9" fill="none" stroke="#4C805F" stroke-width="1.6" stroke-linejoin="round"/></marker></defs>
<g font-family="Segoe UI, Arial, sans-serif"><rect width="1920" height="1080" fill="#F7FAF5"/>`);
rect(60,55,66,66,'#E2EEDC','#E2EEDC',20); icon(73,68,'leaf');
text(149,106,'PantryPal',57,'#214D36',700);
text(1858,92,'SYSTEM OVERVIEW',18,'#52735D',700,'end');
text(62,164,'From the food you have to the meal you can make.',30,'#5C7061');
rect(60,220,1260,790,'#EEF4EA','#DFE9DA',30);
text(92,268,'ON YOUR COMPUTER',19,'#55735D',700);
text(1460,268,'ONLINE SERVICES',19,'#55735D',700);
// All connectors are drawn first so cards sit cleanly above them.
line('M275 375 V405',false,false);
line('M440 545 H600');
text(520,495,'Your actions',19,'#55735D',500,'middle');
text(520,522,'& results',19,'#55735D',500,'middle');
line('M1160 440 H1460',true);
text(1310,389,'Recipe search',20,'#55735D',500,'middle');
text(1310,417,'& recipe details',20,'#55735D',500,'middle');
line('M1160 700 H1460',true);
text(1310,649,'Missing food',20,'#55735D',500,'middle');
text(1310,677,'& product offers',20,'#55735D',500,'middle');
line('M880 760 V855');
text(904,800,'Save changes',20,'#55735D',500);
text(904,828,'& restore on opening',20,'#55735D',500);
// User and the interface.
rect(110,315,330,60,'#E0EDDA','#D3E4CC',20);
icon(204,325,'user'); text(258,354,'You',25,'#28513A',600);
rect(110,405,330,280,'#FFFFFF','#D6E4D9',24,true);
icon(137,430,'screen'); text(190,460,'Your controls',28,'#254C36',700);
text(138,510,'Manage pantry & recipes',22);
text(138,552,'Review recipe imports',22);
text(138,594,'Choose products to price',22);
text(138,636,'Pick a meal or cook',22);
text(110,727,'The screens you interact with',21,'#647968');
// The actual controller and its collaborating in-memory model objects.
rect(600,310,560,450,'#FFFFFF','#BCD4BE',24,true);
p.push('<path d="M624 310 H1136 Q1160 310 1160 334 V420 H600 V334 Q600 310 624 310" fill="#285D40"/>');
text(632,357,'PantryPal’s coordinator',32,'#FFFFFF',700);
text(632,393,'Connects your choices to the app’s features',22,'#E0EDDF');
icon(629,447,'pantry');
text(689,472,'Pantry & recipe collection',26,'#284C36',600);
text(689,504,'Ingredients, saved meals and import drafts',21,'#65786A');
p.push('<path d="M632 530 H1128 M632 638 H1128" stroke="#E3EBDF" stroke-width="2"/>');
icon(629,555,'check');
text(689,580,'Ingredient checks',26,'#284C36',600);
text(689,612,'Matches names and units; finds shortages',21,'#65786A');
icon(629,663,'meal');
text(689,687,'Meal Roulette & cooking',26,'#284C36',600);
text(689,720,'Picks cookable meals; cooking deducts food',21,'#65786A');
// Persistent local file, not a cloud database.
rect(600,855,560,130,'#FFFFFF','#BCD4BE',22,true);
icon(629,889,'save');
text(690,899,'Saved on your computer',27,'#284C36',600);
text(690,934,'Pantry, recipes and unfinished imports',22,'#65786A');
text(690,963,'Available again when you reopen the app',20,'#65786A');
// External providers are reached through the app's adapters.
rect(1460,335,400,225,'#FFFFFF','#D6E4D9',24,true);
icon(1487,362,'book'); text(1543,393,'TheMealDB',31,'#284C36',700);
text(1488,443,'Recipe search & ingredients',23);
text(1488,490,'Review measurements',22,'#65786A');
text(1488,521,'before adding a recipe',22,'#65786A');
rect(1460,635,400,225,'#FFFFFF','#D6E4D9',24,true);
icon(1487,660,'cart'); text(1543,691,'Walmart estimates',28,'#284C36',700);
text(1488,733,'Product listings via SerpApi',23);
text(1488,779,'Choose products to estimate',22,'#65786A');
text(1488,810,'the cost of missing ingredients',22,'#65786A');
text(1460,905,'Online searches need internet access.',19,'#65786A');
text(1460,934,'Saved pantry and recipes work offline.',19,'#65786A');
text(62,1051,'Simplified from PantryPal’s implemented Java application',18,'#6B7D6B');
p.push('<path d="M1090 1045 H1150" stroke="#3F7F5B" stroke-width="3"/><path d="M1450 1045 H1510" stroke="#749682" stroke-width="3" stroke-dasharray="8 8"/>');
text(1166,1051,'Within your computer',18,'#6B7D6B');
text(1526,1051,'Internet connection',18,'#6B7D6B');
p.push('</g></svg>');
const svg = p.join('\n');
fs.writeFileSync(path.join(out,'pantrypal-system-diagram.svg'),svg);
(async()=>{
  await sharp(Buffer.from(svg)).resize(3840,2160).png().toFile(path.join(out,'pantrypal-system-diagram.png'));
  await sharp(Buffer.from(svg)).resize(1600,900).png().toFile(path.join(out,'preview.png'));
  console.log('Exported editable SVG (1920 × 1080) and PNG (3840 × 2160).');
})().catch(e=>{console.error(e);process.exit(1)});
