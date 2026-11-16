package io.github.peol13.unicum.data

/**
 * Database alimentare di base (valori medi per 100 g di prodotto, fonti tipo CREA/USDA).
 *
 * Ogni riga: nome | kcal | proteine | carboidrati | grassi | categoria
 * Per cibi cotti il nome lo indica esplicitamente ("cotto/a"); altrimenti il valore è a crudo/secco.
 */
internal object FoodDatabase {

    private const val RAW = """
Pasta secca|350|12.5|72|1.5|Carboidrati
Pasta integrale secca|335|13|65|2.5|Carboidrati
Pasta di semola cotta|158|5.8|31|0.9|Carboidrati
Pasta all'uovo secca|365|14|70|3|Carboidrati
Spaghetti secchi|350|12.5|72|1.5|Carboidrati
Penne secche|350|12.5|72|1.5|Carboidrati
Fusilli secchi|350|12.5|72|1.5|Carboidrati
Gnocchi di patate|150|3.5|33|0.5|Carboidrati
Riso bianco crudo|355|7|79|0.6|Carboidrati
Riso basmati crudo|360|7|78|0.9|Carboidrati
Riso integrale crudo|350|7.5|74|2.5|Carboidrati
Riso bianco cotto|130|2.7|28|0.3|Carboidrati
Riso integrale cotto|123|2.7|25.6|1|Carboidrati
Couscous secco|365|13|73|0.6|Carboidrati
Quinoa cruda|370|14|64|6|Carboidrati
Orzo perlato crudo|340|10|73|1.5|Carboidrati
Farro perlato crudo|335|15|68|2.5|Carboidrati
Avena fiocchi|370|13|60|7|Carboidrati
Muesli|370|10|66|6|Carboidrati
Corn flakes|375|7|84|0.9|Carboidrati
Cereali integrali da colazione|365|9|68|3.5|Carboidrati
Farina 00|340|11|73|1|Carboidrati
Farina integrale|335|13|62|2.5|Carboidrati
Polenta istantanea secca|355|8|77|1|Carboidrati
Polenta cotta|70|1.6|15|0.4|Carboidrati
Pane bianco|265|8.5|56|1|Carboidrati
Pane integrale|245|9|45|2.5|Carboidrati
Pane di segale|240|8|48|1.5|Carboidrati
Pane in cassetta|275|8|50|4|Carboidrati
Pane tostato|300|10|56|3|Carboidrati
Pane carasau|345|11|72|1.5|Carboidrati
Piadina|295|8|50|7|Carboidrati
Tortillas di grano|310|8|52|7|Carboidrati
Grissini|410|12|70|8|Carboidrati
Crackers|430|10|68|13|Carboidrati
Fette biscottate|410|11|72|7|Carboidrati
Gallette di riso|385|8|80|3|Carboidrati
Patate crude|80|2|17|0.1|Carboidrati
Patate bollite|75|2|16.5|0.1|Carboidrati
Patate al forno|95|2.5|21|0.1|Carboidrati
Patate fritte|300|3.5|38|15|Carboidrati
Patate dolci|86|1.6|20|0.1|Carboidrati
Mais in scatola|85|2.8|17|1.2|Carboidrati
Pizza margherita|255|11|33|9|Piatti pronti
Petto di pollo crudo|110|23|0|1.5|Proteine
Petto di pollo cotto|165|31|0|3.6|Proteine
Coscia di pollo con pelle|210|18|0|15|Proteine
Petto di tacchino|105|24|0|1|Proteine
Fesa di tacchino cotta|110|22|1|2|Proteine
Manzo magro|140|21|0|6|Proteine
Macinato di manzo|230|18|0|18|Proteine
Bistecca di manzo|180|22|0|10|Proteine
Filetto di manzo|165|21|0|9|Proteine
Vitello fettina|110|21|0|3|Proteine
Maiale lonza|150|21|0|7|Proteine
Salsiccia di maiale|300|15|1|26|Proteine
Prosciutto cotto|140|20|1|6|Proteine
Prosciutto crudo|240|27|0|14|Proteine
Bresaola|150|32|0|2.5|Proteine
Speck|300|28|0|21|Proteine
Mortadella|310|15|1|28|Proteine
Salame|420|25|1|35|Proteine
Pancetta|440|14|0|43|Proteine
Wurstel|270|12|3|24|Proteine
Agnello|215|18|0|16|Proteine
Coniglio|135|21|0|6|Proteine
Uova intere|143|12.6|0.7|9.5|Proteine
Albume d'uovo|50|11|0.7|0.2|Proteine
Tuorlo d'uovo|320|16|0.6|27|Proteine
Salmone fresco|208|20|0|13|Proteine
Salmone affumicato|180|25|0|8|Proteine
Tonno fresco|135|24|0|4.5|Proteine
Tonno al naturale in scatola|105|25|0|1|Proteine
Tonno sott'olio sgocciolato|195|27|0|9.5|Proteine
Merluzzo|82|18|0|0.7|Proteine
Nasello|75|16|0|1|Proteine
Orata|120|20|0|4.5|Proteine
Branzino|100|19|0|2.5|Proteine
Sogliola|85|17|0|1.5|Proteine
Trota|120|20|0|4|Proteine
Sgombro|205|19|0|14|Proteine
Sardine fresche|135|20|0|6|Proteine
Acciughe sott'olio|210|29|0|10|Proteine
Gamberi|85|18|0.5|1|Proteine
Calamari|80|15|2|1.5|Proteine
Polpo|82|15|2|1|Proteine
Cozze|75|12|3|2|Proteine
Vongole|70|12|3|1|Proteine
Surimi|95|8|13|0.5|Proteine
Tofu|120|13|2|7|Proteine
Tempeh|195|19|9|11|Proteine
Seitan|120|24|4|1.5|Proteine
Burger vegetale|210|18|8|11|Proteine
Proteine whey in polvere|390|78|8|6|Proteine
Latte intero|64|3.3|4.8|3.6|Latticini
Latte parzialmente scremato|46|3.4|4.9|1.6|Latticini
Latte scremato|35|3.4|5|0.2|Latticini
Latte di soia|40|3.3|2.5|1.8|Latticini
Latte di mandorla|24|0.7|3|1.1|Latticini
Latte di avena|45|1|8|1.5|Latticini
Yogurt bianco intero|66|3.8|5|3.5|Latticini
Yogurt greco 0%|59|10|3.6|0.4|Latticini
Yogurt greco intero|115|5|4|9|Latticini
Yogurt magro|38|4|5|0.2|Latticini
Yogurt alla frutta|90|3.5|15|2|Latticini
Skyr|63|11|4|0.2|Latticini
Kefir|52|3.4|4.5|2|Latticini
Ricotta vaccina|146|9|3.5|10.5|Latticini
Mozzarella|253|18|1|19|Latticini
Mozzarella light|165|20|1|9|Latticini
Mozzarella di bufala|290|17|0.5|24|Latticini
Burrata|290|15|1|25|Latticini
Parmigiano reggiano|390|33|0|28|Latticini
Grana padano|385|33|0|28|Latticini
Pecorino|390|26|0|32|Latticini
Gorgonzola|330|19|0|28|Latticini
Fontina|340|23|0|27|Latticini
Provolone|370|26|2|29|Latticini
Emmental|390|28|0|30|Latticini
Asiago|355|26|0|28|Latticini
Scamorza|330|25|1|25|Latticini
Feta|265|14|4|21|Latticini
Philadelphia|250|6|4|24|Latticini
Stracchino|290|18|2|23|Latticini
Fiocchi di latte|98|11|3.5|4.3|Latticini
Panna da cucina|290|2.5|3.5|30|Latticini
Mascarpone|430|4.5|4|44|Latticini
Burro|745|0.8|0.8|82|Latticini
Mela|52|0.3|14|0.2|Frutta
Pera|57|0.4|15|0.1|Frutta
Banana|89|1.1|23|0.3|Frutta
Arancia|47|0.9|12|0.1|Frutta
Mandarino|53|0.8|13|0.3|Frutta
Clementine|47|0.9|12|0.2|Frutta
Pompelmo|42|0.8|11|0.1|Frutta
Limone|29|1.1|6.5|0.3|Frutta
Fragole|32|0.7|7.7|0.3|Frutta
Mirtilli|57|0.7|14|0.3|Frutta
Lamponi|52|1.2|12|0.7|Frutta
More|43|1.4|10|0.5|Frutta
Ciliegie|63|1.1|16|0.2|Frutta
Albicocche|48|1.4|11|0.4|Frutta
Pesca|39|0.9|10|0.3|Frutta
Nettarine|44|1.1|11|0.3|Frutta
Prugne|46|0.7|11|0.3|Frutta
Uva|69|0.7|18|0.2|Frutta
Anguria|30|0.6|8|0.2|Frutta
Melone|34|0.8|8|0.2|Frutta
Kiwi|61|1.1|15|0.5|Frutta
Ananas|50|0.5|13|0.1|Frutta
Mango|60|0.8|15|0.4|Frutta
Papaya|43|0.5|11|0.3|Frutta
Fichi freschi|74|0.8|19|0.3|Frutta
Cachi|70|0.6|19|0.2|Frutta
Melograno|83|1.7|19|1.2|Frutta
Avocado|160|2|9|15|Frutta
Datteri secchi|282|2.5|75|0.4|Frutta
Uvetta|299|3|79|0.5|Frutta
Albicocche secche|241|3.4|63|0.5|Frutta
Fichi secchi|249|3.3|64|0.9|Frutta
Prugne secche|240|2.2|64|0.4|Frutta
Succo di frutta|45|0.3|11|0.1|Frutta
Spremuta d'arancia|45|0.7|10|0.2|Frutta
Insalata mista|20|1.5|3|0.2|Verdura
Lattuga|15|1.4|2.2|0.2|Verdura
Rucola|25|2.6|3.7|0.7|Verdura
Spinaci|23|2.9|3.6|0.4|Verdura
Spinaci cotti|25|3|3.5|0.4|Verdura
Bietole|19|1.8|3.7|0.2|Verdura
Cavolo cappuccio|25|1.3|6|0.1|Verdura
Cavolfiore|25|2|5|0.3|Verdura
Broccoli|34|2.8|7|0.4|Verdura
Zucchine|17|1.2|3.1|0.3|Verdura
Melanzane|25|1|6|0.2|Verdura
Peperoni|31|1|6|0.3|Verdura
Pomodori|18|0.9|3.9|0.2|Verdura
Pomodori pelati|24|1.2|4.5|0.2|Verdura
Passata di pomodoro|32|1.5|6|0.2|Verdura
Carote|41|0.9|10|0.2|Verdura
Cipolle|40|1.1|9|0.1|Verdura
Aglio|149|6.4|33|0.5|Verdura
Cetrioli|15|0.7|3.6|0.1|Verdura
Finocchi|31|1.2|7|0.2|Verdura
Sedano|16|0.7|3|0.2|Verdura
Asparagi|20|2.2|3.9|0.1|Verdura
Carciofi|47|3.3|11|0.2|Verdura
Fagiolini|31|1.8|7|0.2|Verdura
Funghi champignon|22|3.1|3.3|0.3|Verdura
Funghi porcini|26|3|3|0.4|Verdura
Zucca|26|1|6.5|0.1|Verdura
Radicchio|23|1.4|4.5|0.3|Verdura
Barbabietola|43|1.6|10|0.2|Verdura
Ravanelli|16|0.7|3.4|0.1|Verdura
Piselli freschi|81|5.4|14|0.4|Verdura
Verdure grigliate|45|1.5|5|2|Verdura
Minestrone di verdure|35|1.5|5|1|Verdura
Fagioli borlotti cotti|120|8|20|0.5|Legumi
Fagioli cannellini cotti|110|7.5|19|0.5|Legumi
Fagioli neri cotti|132|8.9|23.7|0.5|Legumi
Ceci cotti|130|8.5|21|2.5|Legumi
Ceci secchi|330|20|51|6|Legumi
Lenticchie cotte|100|8|16|0.5|Legumi
Lenticchie secche|325|23|51|2|Legumi
Piselli in scatola|70|5|11|0.5|Legumi
Fagioli secchi|315|21|50|1.5|Legumi
Fave fresche|70|5.5|10|0.6|Legumi
Hummus|230|7.5|14|16|Legumi
Edamame|120|11|9|5|Legumi
Soia gialla secca|390|36|20|18|Legumi
Olio extravergine d'oliva|884|0|0|100|Grassi e frutta secca
Olio di semi|884|0|0|100|Grassi e frutta secca
Olio di cocco|862|0|0|100|Grassi e frutta secca
Margarina|720|0.2|0.7|80|Grassi e frutta secca
Maionese|680|1|2|75|Grassi e frutta secca
Mandorle|579|21|22|50|Grassi e frutta secca
Noci|654|15|14|65|Grassi e frutta secca
Nocciole|628|15|17|61|Grassi e frutta secca
Anacardi|553|18|30|44|Grassi e frutta secca
Pistacchi|560|20|28|45|Grassi e frutta secca
Arachidi|567|26|16|49|Grassi e frutta secca
Noci pecan|690|9|14|72|Grassi e frutta secca
Pinoli|670|14|13|68|Grassi e frutta secca
Semi di chia|486|17|42|31|Grassi e frutta secca
Semi di lino|534|18|29|42|Grassi e frutta secca
Semi di zucca|560|30|11|49|Grassi e frutta secca
Semi di girasole|584|21|20|51|Grassi e frutta secca
Semi di sesamo|573|18|23|50|Grassi e frutta secca
Burro di arachidi|600|25|20|50|Grassi e frutta secca
Crema di nocciole|540|6|58|31|Grassi e frutta secca
Olive verdi|145|1|4|15|Grassi e frutta secca
Olive nere|115|0.8|6|10.7|Grassi e frutta secca
Cioccolato fondente 70%|580|8|45|40|Dolci e snack
Cioccolato al latte|535|7.5|58|30|Dolci e snack
Cioccolato bianco|540|6|59|32|Dolci e snack
Nutella|539|6.3|57.5|30.9|Dolci e snack
Biscotti secchi|430|7|75|11|Dolci e snack
Biscotti frollini|480|6.5|70|19|Dolci e snack
Biscotti al cioccolato|490|6|67|22|Dolci e snack
Cornetto|410|8|50|20|Dolci e snack
Cornetto vuoto|380|8|48|17|Dolci e snack
Brioche|380|8|50|16|Dolci e snack
Merendina|400|6|58|16|Dolci e snack
Plumcake|400|6|55|18|Dolci e snack
Torta margherita|390|6|57|16|Dolci e snack
Crostata alla marmellata|390|5|62|14|Dolci e snack
Tiramisù|285|5|28|17|Dolci e snack
Gelato alla crema|200|4|24|10|Dolci e snack
Gelato alla frutta|130|1|31|0.5|Dolci e snack
Budino|125|3.5|20|3.5|Dolci e snack
Marmellata|250|0.4|62|0.1|Dolci e snack
Miele|304|0.3|82|0|Dolci e snack
Zucchero|392|0|100|0|Dolci e snack
Sciroppo d'acero|260|0|67|0|Dolci e snack
Caramelle|380|0|95|0|Dolci e snack
Patatine in busta|535|6.5|50|35|Dolci e snack
Popcorn|385|12|74|4.5|Dolci e snack
Barretta proteica|370|30|35|13|Dolci e snack
Barretta di cereali|400|6|68|12|Dolci e snack
Acqua|0|0|0|0|Bevande
Caffè espresso|2|0.1|0|0|Bevande
Cappuccino|55|3|5|2.5|Bevande
Tè senza zucchero|1|0|0.2|0|Bevande
Coca Cola|42|0|10.6|0|Bevande
Coca Cola Zero|0.4|0|0|0|Bevande
Aranciata|45|0.1|11|0|Bevande
Bevanda isotonica|26|0|6.2|0|Bevande
Birra|43|0.5|3.6|0|Alcolici
Vino rosso|85|0.1|2.6|0|Alcolici
Vino bianco|82|0.1|2.6|0|Alcolici
Spritz|90|0|9|0|Alcolici
Superalcolici|235|0|0|0|Alcolici
Ketchup|110|1.2|26|0.2|Condimenti
Senape|66|4|6|3.5|Condimenti
Salsa di soia|60|8|6|0|Condimenti
Aceto balsamico|78|0.5|17|0|Condimenti
Pesto alla genovese|460|5|5|47|Condimenti
Ragù di carne|130|7|5|9|Condimenti
Sugo al pomodoro|60|1.5|9|2|Condimenti
Besciamella|120|3.5|10|7.5|Condimenti
Lasagne al forno|165|8|16|7|Piatti pronti
Pizza prosciutto e funghi|240|11|29|9|Piatti pronti
Pasta al pomodoro|150|5|28|2|Piatti pronti
Pasta alla carbonara|190|8|20|8|Piatti pronti
Pasta al ragù|170|8|21|6|Piatti pronti
Risotto ai funghi|140|3|23|4|Piatti pronti
Insalata di riso|155|4|24|4.5|Piatti pronti
Panino con prosciutto e formaggio|260|13|30|9|Piatti pronti
Hamburger con panino|255|13|24|12|Piatti pronti
Toast prosciutto e formaggio|270|14|26|12|Piatti pronti
Sushi nigiri|150|6|28|1.5|Piatti pronti
Kebab|215|13|20|9|Piatti pronti
Frittata|190|12|2|15|Piatti pronti
Cotoletta impanata|240|18|11|13|Piatti pronti
Parmigiana di melanzane|160|7|7|11|Piatti pronti
Zuppa di legumi|85|5|13|1.5|Piatti pronti
"""

    val foods: List<Food> by lazy {
        RAW.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val p = line.split("|")
                Food(
                    name = p[0],
                    caloriesPer100g = p[1].toFloat().let { kotlin.math.round(it).toInt() },
                    proteinPer100g = p[2].toFloat(),
                    carbsPer100g = p[3].toFloat(),
                    fatsPer100g = p[4].toFloat(),
                    category = p[5]
                )
            }
            .toList()
    }
}
