package io.github.peol13.unicum.data

/** Una quantità tipica di un alimento (un frutto, una fetta, un bicchiere…) con il suo peso medio. */
data class Portion(val label: String, val grams: Int)

/**
 * Porzioni medie per gli alimenti del catalogo, così non serve pesarli: "1 mela media = 180 g".
 * Pesi indicativi della parte edibile. Chi non è in elenco resta con i soli grammi.
 *
 * Ogni riga: nome alimento | porzione=grammi ; porzione=grammi … (la prima è quella proposta).
 */
object FoodPortions {

    private const val RAW = """
Pasta secca|porzione=80;porzione abbondante=100
Pasta integrale secca|porzione=80
Pasta all'uovo secca|porzione=80
Spaghetti secchi|porzione=80;porzione abbondante=100
Penne secche|porzione=80;porzione abbondante=100
Fusilli secchi|porzione=80;porzione abbondante=100
Pasta di semola cotta|piatto=200
Gnocchi di patate|porzione=200
Riso bianco crudo|porzione=80
Riso basmati crudo|porzione=80
Riso integrale crudo|porzione=80
Riso bianco cotto|piatto=200
Riso integrale cotto|piatto=200
Couscous secco|porzione=80
Quinoa cruda|porzione=80
Orzo perlato crudo|porzione=80
Farro perlato crudo|porzione=80
Avena fiocchi|porzione=40;cucchiaio=10
Muesli|porzione=40
Corn flakes|porzione=30
Cereali integrali da colazione|porzione=40
Polenta cotta|porzione=200
Pane bianco|fetta=30;panino=50
Pane integrale|fetta=30;panino=50
Pane di segale|fetta=30
Pane in cassetta|fetta=25
Pane tostato|fetta=20
Pane carasau|foglio=15
Piadina|piadina=100
Tortillas di grano|tortilla=40
Grissini|grissino=5;confezione=30
Crackers|confezione=25
Fette biscottate|fetta=8
Gallette di riso|galletta=8
Patate crude|patata media=150
Patate bollite|patata media=150
Patate al forno|porzione=200
Patate fritte|porzione=150
Patate dolci|patata dolce=200
Mais in scatola|scatoletta=140
Pizza margherita|pizza intera=350;trancio=120
Pizza prosciutto e funghi|pizza intera=380;trancio=120
Petto di pollo crudo|petto=150
Petto di pollo cotto|porzione=120
Coscia di pollo con pelle|coscia=120
Petto di tacchino|fetta=150
Fesa di tacchino cotta|porzione=100
Manzo magro|porzione=150
Macinato di manzo|porzione=150;hamburger=120
Bistecca di manzo|bistecca=200
Filetto di manzo|filetto=200
Vitello fettina|fettina=120
Maiale lonza|porzione=150
Salsiccia di maiale|salsiccia=100
Prosciutto cotto|fetta=20;porzione=50
Prosciutto crudo|fetta=15;porzione=50
Bresaola|fetta=15;porzione=50
Speck|fetta=15;porzione=50
Mortadella|fetta=20;porzione=50
Salame|fetta=10;porzione=40
Pancetta|fetta=10
Wurstel|wurstel=70
Agnello|porzione=150
Coniglio|porzione=150
Uova intere|uovo=60
Albume d'uovo|albume=35
Tuorlo d'uovo|tuorlo=18
Salmone fresco|trancio=150
Salmone affumicato|porzione=50
Tonno fresco|trancio=150
Tonno al naturale in scatola|scatoletta=60
Tonno sott'olio sgocciolato|scatoletta=60
Merluzzo|filetto=150
Nasello|filetto=150
Orata|filetto=150
Branzino|filetto=150
Sogliola|filetto=150
Trota|trota=200
Sgombro|filetto=120
Sardine fresche|porzione=100
Gamberi|porzione=100
Calamari|porzione=150
Polpo|porzione=150
Cozze|porzione=200
Vongole|porzione=150
Surimi|bastoncino=15;porzione=100
Tofu|porzione=100;panetto=200
Tempeh|porzione=100
Seitan|porzione=100
Burger vegetale|burger=100
Proteine whey in polvere|misurino=30
Latte intero|bicchiere=200;tazza=150
Latte parzialmente scremato|bicchiere=200;tazza=150
Latte scremato|bicchiere=200;tazza=150
Latte di soia|bicchiere=200;tazza=150
Latte di mandorla|bicchiere=200;tazza=150
Latte di avena|bicchiere=200;tazza=150
Yogurt bianco intero|vasetto=125
Yogurt greco 0%|vasetto=150
Yogurt greco intero|vasetto=150
Yogurt magro|vasetto=125
Yogurt alla frutta|vasetto=125
Skyr|vasetto=150
Kefir|bicchiere=200
Ricotta vaccina|porzione=100;cucchiaio=25
Mozzarella|mozzarella=125
Mozzarella light|mozzarella=125
Mozzarella di bufala|mozzarella=125
Burrata|burrata=125
Parmigiano reggiano|porzione=30;cucchiaio grattugiato=8
Grana padano|porzione=30;cucchiaio grattugiato=8
Pecorino|porzione=30
Gorgonzola|porzione=50
Fontina|porzione=50
Provolone|porzione=50
Emmental|fetta=25
Asiago|porzione=50
Scamorza|porzione=50
Feta|porzione=50
Philadelphia|porzione=30;cucchiaio=15
Stracchino|porzione=50
Fiocchi di latte|porzione=100
Panna da cucina|cucchiaio=15
Mascarpone|cucchiaio=25
Burro|noce=10;cucchiaino=5
Mela|mela media=180;mela piccola=130;mela grande=230
Pera|pera media=180;pera piccola=130;pera grande=230
Banana|banana media=120;banana piccola=90;banana grande=150
Arancia|arancia media=150;arancia grande=220
Mandarino|mandarino=70
Clementine|clementina=60
Pompelmo|pompelmo=250
Limone|limone=80
Fragole|fragola=15;porzione=150
Mirtilli|manciata=50;porzione=100
Lamponi|manciata=50;porzione=100
More|manciata=50;porzione=100
Ciliegie|ciliegia=10;porzione=150
Albicocche|albicocca=40
Pesca|pesca media=150
Nettarine|nettarina media=150
Prugne|prugna=50
Uva|acino=5;grappolo piccolo=150
Anguria|fetta=300
Melone|fetta=200
Kiwi|kiwi=75
Ananas|fetta=80
Mango|mango=250
Papaya|porzione=150
Fichi freschi|fico=50
Cachi|cachi=150
Melograno|melograno=200
Avocado|mezzo avocado=100;avocado intero=200
Datteri secchi|dattero=10
Uvetta|manciata=30
Albicocche secche|albicocca secca=8
Fichi secchi|fico secco=20
Prugne secche|prugna secca=8
Succo di frutta|bicchiere=200;brick=200
Spremuta d'arancia|bicchiere=200
Insalata mista|porzione=80;busta=100
Lattuga|porzione=80
Rucola|manciata=20
Spinaci|porzione=100
Spinaci cotti|porzione=150
Bietole|porzione=150
Cavolfiore|porzione=200
Broccoli|porzione=200
Zucchine|zucchina media=200
Melanzane|melanzana media=250
Peperoni|peperone medio=200
Pomodori|pomodoro medio=120;pomodorino=15
Pomodori pelati|scatola=240
Passata di pomodoro|porzione=100
Carote|carota media=80
Cipolle|cipolla media=100
Aglio|spicchio=5
Cetrioli|cetriolo=200
Finocchi|finocchio=250
Carciofi|carciofo=100
Fagiolini|porzione=200
Funghi champignon|porzione=100
Zucca|porzione=200
Verdure grigliate|porzione=150
Minestrone di verdure|piatto=300
Fagioli borlotti cotti|porzione=150;scatola sgocciolata=250
Fagioli cannellini cotti|porzione=150;scatola sgocciolata=250
Fagioli neri cotti|porzione=150
Ceci cotti|porzione=150;scatola sgocciolata=250
Ceci secchi|porzione=50
Lenticchie cotte|porzione=150;scatola sgocciolata=250
Lenticchie secche|porzione=50
Piselli in scatola|scatoletta=150
Fagioli secchi|porzione=50
Hummus|cucchiaio=20;porzione=50
Edamame|porzione=100
Olio extravergine d'oliva|cucchiaio=10;cucchiaino=5
Olio di semi|cucchiaio=10;cucchiaino=5
Olio di cocco|cucchiaio=10;cucchiaino=5
Margarina|cucchiaino=5
Maionese|cucchiaio=15
Mandorle|manciata=30
Noci|manciata=30;noce=5
Nocciole|manciata=30
Anacardi|manciata=30
Pistacchi|manciata=30
Arachidi|manciata=30
Noci pecan|manciata=30
Pinoli|cucchiaio=10
Semi di chia|cucchiaio=10
Semi di lino|cucchiaio=10
Semi di zucca|cucchiaio=10
Semi di girasole|cucchiaio=10
Semi di sesamo|cucchiaio=10
Burro di arachidi|cucchiaio=15
Crema di nocciole|cucchiaio=15
Olive verdi|oliva=4;porzione=30
Olive nere|oliva=4;porzione=30
Cioccolato fondente 70%|quadretto=10;tavoletta=100
Cioccolato al latte|quadretto=10;tavoletta=100
Cioccolato bianco|quadretto=10;tavoletta=100
Nutella|cucchiaio=15;cucchiaino=7
Biscotti secchi|biscotto=8
Biscotti frollini|biscotto=10
Biscotti al cioccolato|biscotto=10
Cornetto|cornetto=60
Cornetto vuoto|cornetto=55
Brioche|brioche=60
Merendina|merendina=35
Plumcake|plumcake=40
Torta margherita|fetta=80
Crostata alla marmellata|fetta=80
Tiramisù|porzione=100
Gelato alla crema|pallina=50;cono medio=150
Gelato alla frutta|pallina=50
Budino|budino=125
Marmellata|cucchiaino=15;cucchiaio=25
Miele|cucchiaino=7;cucchiaio=20
Zucchero|cucchiaino=5;bustina=5
Sciroppo d'acero|cucchiaio=20
Caramelle|caramella=4
Patatine in busta|sacchetto=30
Popcorn|porzione=30
Barretta proteica|barretta=50
Barretta di cereali|barretta=25
Acqua|bicchiere=200;bottiglia=500
Caffè espresso|tazzina=30
Cappuccino|tazza=180
Tè senza zucchero|tazza=200
Coca Cola|lattina=330;bottiglia=500;bicchiere=200
Coca Cola Zero|lattina=330;bottiglia=500;bicchiere=200
Aranciata|lattina=330;bicchiere=200
Bevanda isotonica|bottiglia=500
Birra|lattina=330;media=400;piccola=200
Vino rosso|calice=125
Vino bianco|calice=125
Spritz|bicchiere=150
Superalcolici|shot=40
Ketchup|cucchiaio=15
Salsa di soia|cucchiaio=15
Pesto alla genovese|cucchiaio=15
Ragù di carne|porzione=100
Sugo al pomodoro|porzione=100
Besciamella|porzione=100
Lasagne al forno|porzione=250
Pasta al pomodoro|piatto=300
Pasta alla carbonara|piatto=300
Pasta al ragù|piatto=300
Risotto ai funghi|piatto=300
Insalata di riso|porzione=250
Panino con prosciutto e formaggio|panino=150
Hamburger con panino|panino=200
Toast prosciutto e formaggio|toast=150
Sushi nigiri|pezzo=30
Kebab|kebab=300
Frittata|frittata di 2 uova=130
Cotoletta impanata|cotoletta=120
Parmigiana di melanzane|porzione=250
Zuppa di legumi|piatto=300
Alici al forno|porzione=145
Alici marinate|porzione=100
Baccalà alla vicentina|porzione=230
Brasato al Barolo|porzione=130
Cannolo siciliano|pezzo=66
Castagnaccio|fetta=100
Frittata con prosciutto cotto e formaggio|porzione=120
Gateau di patate|porzione=200
Gnocchi al pesto|piatto=230
Gnocchi al ragù|piatto=330
Gnocchi al pomodoro|piatto=270
Merluzzo in umido|porzione=175
Pan di Spagna|fetta=50
Pasta aglio, olio e peperoncino|piatto=190
Pasta al pesto|piatto=240
Pasta all'amatriciana|piatto=270
Pasta alle vongole|piatto=250
Pasta burro e parmigiano|piatto=180
Pasta cacio e pepe|piatto=200
Pasta e fagioli|piatto=350
Pasta integrale al pomodoro|piatto=265
Pastiera|fetta=100
Polenta al ragù|piatto=400
Polenta al pomodoro|piatto=400
Pomodori ripieni di riso|porzione=350
Ravioli burro e salvia|piatto=190
Ravioli al pomodoro|piatto=290
Riso al ragù|piatto=380
Riso al pomodoro|piatto=320
Riso burro e parmigiano|piatto=220
Risotto alla parmigiana|piatto=280
Scaloppine di tacchino al limone|porzione=155
Sogliola al vino bianco|porzione=150
Spezzatino di manzo al vino rosso|porzione=150
Spigola al cartoccio|porzione=170
Torta di mele|fetta=100
Trota al forno|porzione=155
Zucchine grigliate|porzione=200
Gelato fior di latte|pallina=50
Gelato alla nocciola|pallina=50
Gelato al cioccolato|pallina=50
Gelato stracciatella|pallina=50
Gelato alla vaniglia|pallina=50
Gelato al caffè|pallina=50
Sorbetto al limone|pallina=50
Merendina farcita al cacao|merendina=35
Panettone|fetta=80
Savoiardi|biscotto=10
Torrone|pezzo=30
Wafer ricoperto di cioccolato|barretta=30
Yogurt di soia|vasetto=125
Crema di funghi|piatto=250
Crema di pomodoro|piatto=250
Cacao amaro in polvere|cucchiaino=5
Cheeseburger|hamburger=130
Chicken nuggets|porzione=110
Patatine fritte del fast food|porzione=90
Falafel|porzione=140
Involtini primavera|porzione=115
Maiale in agrodolce|piatto=290
Nachos|porzione=30
Pollo alle mandorle|piatto=230
Ravioli cinesi al vapore|porzione=140
Riso alla cantonese|piatto=280
Spaghetti cinesi saltati|piatto=300
Tabulè|piatto=300
Nuvolette di gamberi|porzione=40
Caciocavallo|porzione=50
Crescenza|porzione=100
Taleggio|porzione=50
Pecorino romano|porzione=50
Brie|porzione=50
Camembert|porzione=50
Robiola|porzione=50
Ricotta di pecora|porzione=100
Edam|porzione=50
Cheddar|porzione=50
Groviera|porzione=50
Caciotta|porzione=50
Formaggio spalmabile light|porzione=30
Coppa|fetta=15;porzione=50
Capocollo|fetta=15;porzione=50
Cotechino cotto|porzione=100
Zampone cotto|porzione=100
Soppressata|fetta=10;porzione=40
Salsiccia cotta in padella|salsiccia=80
Prosciutto cotto sgrassato|fetta=20;porzione=50
Prosciutto crudo sgrassato|fetta=15;porzione=50
Paté di fegato|cucchiaio=15
Pizza bianca (focaccia)|trancio=100
Pizza rossa|trancio=100
Pizza al taglio pomodoro e mozzarella|trancio=100
Tortellini freschi|porzione=125
Tortellini cotti|piatto=200
Ravioli freschi|porzione=125
Ravioli cotti|piatto=200
Rosetta (pane)|panino=50
Panini al latte|panino=50
Pangrattato|cucchiaio=10
Riso Venere crudo|porzione=80
Riso Venere cotto|piatto=200
Riso parboiled crudo|porzione=80
Grano saraceno|porzione=80
Miglio|porzione=80
Farro perlato cotto|piatto=200
Orzo perlato cotto|piatto=200
Couscous cotto|piatto=200
Quinoa cotta|piatto=200
Gnocchi di patate cotti|piatto=200
Biscotti integrali|biscotto=10
Crackers integrali|confezione=25
Fette biscottate integrali|fetta=8
Riso soffiato|porzione=30
"""

    private val byName: Map<String, List<Portion>> by lazy {
        RAW.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { line ->
                val (name, rest) = line.split("|")
                name to rest.split(";").map { p ->
                    val (label, grams) = p.split("=")
                    Portion(label.trim(), grams.trim().toInt())
                }
            }
    }

    /** Nomi con porzioni, per il test di coerenza con il catalogo. */
    internal val names: Set<String> get() = byName.keys

    /** Le porzioni dell'alimento; vuote se non ne ha (si usano solo i grammi). */
    fun of(food: Food): List<Portion> = byName[food.name].orEmpty()
}
