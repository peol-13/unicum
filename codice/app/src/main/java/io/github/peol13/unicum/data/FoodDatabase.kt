package io.github.peol13.unicum.data

/**
 * Database alimentare di base (valori medi per 100 g di prodotto, fonti tipo CREA/USDA).
 * Le righe dopo "Zuppa di legumi" e i piatti pronti principali (lasagne, pasta al pomodoro/carbonara/ragù, parmigiana,
 * pesto, ragù, sugo, frittata, besciamella, pizza margherita) sono presi dalle Tabelle di composizione degli alimenti
 * del CREA Alimenti e Nutrizione (alimentinutrizione.it, aggiornamento dicembre 2019): la fonte va citata (NOTICE.md,
 * crediti dell'app). Per aggiungerne altre: stessi valori della scheda CREA (energia, proteine, carboidrati disponibili,
 * lipidi) e nome italiano semplice, con traduzioni in foods.psv.
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
Pizza margherita|235|10.7|27.4|9.3|Piatti pronti
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
Pesto alla genovese|250|11|1.8|21.8|Condimenti
Ragù di carne|196|12|1.9|15.4|Condimenti
Sugo al pomodoro|74|1.3|3.6|5.7|Condimenti
Besciamella|139|3.9|9.5|9.5|Condimenti
Lasagne al forno|196|11.7|13.4|10.8|Piatti pronti
Pizza prosciutto e funghi|240|11|29|9|Piatti pronti
Pasta al pomodoro|113|3.3|19.5|2.6|Piatti pronti
Pasta alla carbonara|245|10.8|28.4|10.3|Piatti pronti
Pasta al ragù|168|8.4|16|8|Piatti pronti
Risotto ai funghi|140|3|23|4|Piatti pronti
Insalata di riso|155|4|24|4.5|Piatti pronti
Panino con prosciutto e formaggio|260|13|30|9|Piatti pronti
Hamburger con panino|255|13|24|12|Piatti pronti
Toast prosciutto e formaggio|270|14|26|12|Piatti pronti
Sushi nigiri|150|6|28|1.5|Piatti pronti
Kebab|215|13|20|9|Piatti pronti
Frittata|185|16.1|0|13.4|Piatti pronti
Cotoletta impanata|240|18|11|13|Piatti pronti
Parmigiana di melanzane|242|7|4|21|Piatti pronti
Zuppa di legumi|85|5|13|1.5|Piatti pronti
Alici al forno|172|18.2|7.1|8|Piatti pronti
Alici marinate|120|14.2|1.4|6.4|Piatti pronti
Baccalà alla vicentina|520|21.5|0.3|47.9|Piatti pronti
Brasato al Barolo|108|13.8|0.3|5.6|Piatti pronti
Cannolo siciliano|325|7.2|42.7|14.6|Dolci e snack
Castagnaccio|234|3.4|39.3|6.8|Dolci e snack
Frittata con prosciutto cotto e formaggio|205|16.2|0.3|15.4|Piatti pronti
Gateau di patate|156|9.1|11.6|7.7|Piatti pronti
Gnocchi al pesto|186|6.4|21.7|8.4|Piatti pronti
Gnocchi al ragù|175|8.4|15.4|9|Piatti pronti
Gnocchi al pomodoro|112|2.5|19.4|2.8|Piatti pronti
Merluzzo in umido|86|9.7|1.7|4.3|Piatti pronti
Pan di Spagna|275|10.1|49.7|5.1|Dolci e snack
Pasta aglio, olio e peperoncino|190|4.2|26.5|7.9|Piatti pronti
Pasta al pesto|175|6.7|21.4|7.2|Piatti pronti
Pasta all'amatriciana|241|7.5|28.7|11|Piatti pronti
Pasta alle vongole|157|6.7|28.3|2.4|Piatti pronti
Pasta burro e parmigiano|184|6|27.3|6.1|Piatti pronti
Pasta cacio e pepe|167|6.9|27.4|3.8|Piatti pronti
Pasta e fagioli|116|4.9|12.2|5.1|Piatti pronti
Pasta integrale al pomodoro|132|4.6|21.3|3.2|Piatti pronti
Pastiera|295|7.2|43.6|11|Dolci e snack
Polenta al ragù|114|4.6|12.4|5.3|Piatti pronti
Polenta al pomodoro|73|1|14.2|1.5|Piatti pronti
Pomodori ripieni di riso|93|2|14.5|2.8|Piatti pronti
Ravioli burro e salvia|238|6.5|24.7|12.9|Piatti pronti
Ravioli al pomodoro|135|4.8|17.6|5.1|Piatti pronti
Riso al ragù|145|6.7|13.6|7.4|Piatti pronti
Riso al pomodoro|90|1.7|16.5|2.2|Piatti pronti
Riso burro e parmigiano|144|3.4|22.1|5.2|Piatti pronti
Risotto alla parmigiana|181|4.1|25.4|7.5|Piatti pronti
Scaloppine di tacchino al limone|170|20.5|11.5|4.9|Piatti pronti
Sogliola al vino bianco|188|19|5.6|10.1|Piatti pronti
Spezzatino di manzo al vino rosso|161|18.1|3|8.6|Piatti pronti
Spigola al cartoccio|126|16.2|0.9|6.4|Piatti pronti
Torta di mele|300|5|46.3|11.5|Dolci e snack
Trota al forno|186|20.8|5.8|8.9|Piatti pronti
Zucchine grigliate|75|4.3|5.5|3.8|Verdura
Gelato fior di latte|218|4.2|20.7|13.7|Dolci e snack
Gelato alla nocciola|183|3.3|25.2|8.4|Dolci e snack
Gelato al cioccolato|173|3.6|25.3|7.1|Dolci e snack
Gelato stracciatella|193|2.9|31.5|7|Dolci e snack
Gelato alla vaniglia|167|2.7|25.7|6.7|Dolci e snack
Gelato al caffè|245|5.1|23.9|15|Dolci e snack
Sorbetto al limone|132|0.9|34.2|0|Dolci e snack
Ghiacciolo|137|0|36.5|0|Dolci e snack
Cono gelato panna e cioccolato|291|5.1|34.9|15.5|Dolci e snack
Merendina farcita al cacao|410|6.2|65.5|15.1|Dolci e snack
Panettone|337|6.4|56.2|10.7|Dolci e snack
Savoiardi|395|11.9|69.6|9.2|Dolci e snack
Torrone|483|10.8|52|26.8|Dolci e snack
Wafer ricoperto di cioccolato|498|8.2|60.3|26.6|Dolci e snack
Wafer|452|7.1|76.2|15|Dolci e snack
Yogurt di soia|72|5|3.9|4.2|Latticini
Crema di funghi|56|1|4.2|4|Piatti pronti
Crema di pomodoro|36|0.8|6.4|1|Piatti pronti
Cacao amaro in polvere|355|20.4|11.5|25.6|Condimenti
Cheeseburger|300|16.3|30.2|13.1|Piatti pronti
Chicken nuggets|242|17.7|15.4|12.4|Piatti pronti
Patatine fritte del fast food|283|3.3|37.6|13.7|Carboidrati
Falafel|198|7.1|19.8|9.5|Piatti pronti
Involtini primavera|238|3.5|21.6|15.4|Piatti pronti
Maiale in agrodolce|233|8.7|17.7|14.6|Piatti pronti
Nachos|512|6.5|63.8|26.1|Dolci e snack
Pollo alle mandorle|182|16.5|2.4|11.9|Piatti pronti
Ravioli cinesi al vapore|270|7.2|21.4|17.5|Piatti pronti
Riso alla cantonese|207|5.5|29.8|7.8|Piatti pronti
Spaghetti cinesi saltati|165|4.6|13.6|10.4|Piatti pronti
Tabulè|95|3|14|2.4|Piatti pronti
Nuvolette di gamberi|559|0.8|57|38|Dolci e snack
Caciocavallo|431|35.7|2.3|31.1|Latticini
Crescenza|281|16.1|1.9|23.3|Latticini
Taleggio|315|19|0.9|26.2|Latticini
Pecorino romano|409|26|1.8|33.1|Latticini
Brie|320|19.3|0.2|26.9|Latticini
Camembert|307|20.9|0.2|24.7|Latticini
Robiola|338|20|2.3|27.7|Latticini
Ricotta di pecora|157|9.5|4.2|11.5|Latticini
Formaggino|309|11.2|6|26.9|Latticini
Edam|326|25.5|1.4|24.3|Latticini
Cheddar|388|26.7|0.5|31|Latticini
Groviera|417|30.6|1.5|32.1|Latticini
Caciotta|368|24.6|0.8|29.6|Latticini
Formaggio spalmabile light|179|9.2|3.1|14.5|Latticini
Coppa|400|28.9|0|31.6|Proteine
Capocollo|344|29.1|0.9|24.9|Proteine
Cotechino cotto|253|23.6|3.2|16.3|Proteine
Zampone cotto|262|23.7|2.6|17.5|Proteine
Soppressata|377|34.2|0.9|26.3|Proteine
Salsiccia cotta in padella|324|22.2|0|26.1|Proteine
Prosciutto cotto sgrassato|107|17|1.9|3.5|Proteine
Prosciutto crudo sgrassato|176|29.1|0.4|6.4|Proteine
Paté di fegato|341|12.7|1.3|31.7|Proteine
Pizza bianca (focaccia)|306|7.9|57.9|5.9|Carboidrati
Pizza rossa|248|7.6|40.7|6.6|Carboidrati
Pizza al taglio pomodoro e mozzarella|255|12.1|35.5|7.6|Carboidrati
Tortellini freschi|302|12.2|49.9|7.1|Carboidrati
Tortellini cotti|158|6.4|26|3.7|Carboidrati
Ravioli freschi|265|10.6|40.7|7.3|Carboidrati
Ravioli cotti|177|7.2|27.5|4.6|Carboidrati
Rosetta (pane)|275|9|57.6|1.9|Carboidrati
Panini al latte|298|9|48.1|8.7|Carboidrati
Pane azzimo|365|10.7|81.7|1|Carboidrati
Pangrattato|357|10.1|77.8|2.1|Carboidrati
Riso Venere crudo|355|7.8|80.4|1.3|Carboidrati
Riso Venere cotto|160|3.5|36.9|0.3|Carboidrati
Riso parboiled crudo|338|7.4|81.3|0.3|Carboidrati
Grano saraceno|329|12.4|61.2|3.3|Carboidrati
Miglio|342|11|67.8|4.2|Carboidrati
Farro perlato cotto|178|6.2|37.3|1|Carboidrati
Orzo perlato cotto|118|2.6|26.4|0.6|Carboidrati
Couscous cotto|179|3.6|39.7|1.1|Carboidrati
Quinoa cotta|140|4.8|24.3|2.5|Carboidrati
Gnocchi di patate cotti|147|3.5|34|0.2|Carboidrati
Farina di mais|341|8.7|73.5|2.7|Carboidrati
Biscotti integrali|419|7.8|70.8|12.3|Dolci e snack
Crackers integrali|402|12|61.7|11.4|Carboidrati
Fette biscottate integrali|394|14.2|63|10|Carboidrati
Riso soffiato|354|6|85.1|1|Carboidrati
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
