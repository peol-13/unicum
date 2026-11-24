// Traduzioni della pagina web. L'italiano è il testo scritto in index.html; qui le altre 5 lingue dell'app.
// Lingua scelta: ?lang=xx, poi la scelta salvata dal piè di pagina, poi la lingua del telefono/browser; altrimenti inglese.
// Ogni elemento con data-t="chiave" riceve il testo (HTML ammesso); data-t-alt e data-t-aria traducono alt e aria-label.
(function () {
  var FORM = '<a href="https://docs.google.com/forms/d/e/1FAIpQLSejujvTR2J8jWqYPyPMps9YFfEggqCUqc0QjBhuOft_-t1yxA/viewform" target="_blank" rel="noopener" data-feedback>';
  var PRIVACY = '<a href="privacy.html">';

  var D = {
    en: {
      'meta.title': 'UNICUM · Ring, watch, meals and running plan in one app',
      'meta.desc': "Free, open source Android app for runners: sleep, recovery, meals, runs and your coach's plan on one screen. No account, your data stays on your phone.",
      'nav.how': 'How it works', 'nav.faq': 'FAQ', 'nav.get': 'Download',
      'hero.h1': 'Ring, watch, meals and running plan. In one app.',
      'hero.lead': "Open one screen and you know how you're doing today and which run is next. Free, open source, no account: your data stays on your phone.",
      'hero.btn': 'Download for Android',
      'hero.small': 'Free · Android 8 or later · No ads',
      'hero.alt': "UNICUM's main screen: the day's calories, deficit and macros",
      'tape.pdf': "Coach's PDF",
      'intro.h': 'Five apps to know if you can run today?',
      'intro.p': 'Sleep lives in the ring app, runs in the watch app, meals in another one, the plan in a PDF. UNICUM reads it all and puts it together. Scroll to see how.',
      's1.num': '01 · Your day', 's1.h': 'Everything that matters, on one screen',
      's1.p': "How much you ate, how much you burned and how much is left. The deficit is the real one: your resting burn builds up during the day, it isn't all counted at dawn.",
      's1.l1': 'Calories and macros', 's1.l2': 'Deficit hour by hour', 's1.l3': 'Barcode scanner',
      's2.num': '02 · The plan', 's2.h': "Your coach's PDF becomes a plan",
      's2.p': 'Pick the file and in ten seconds the sessions are split by day, type, distance and pace. Check the preview, fix anything if needed, save.',
      's2.l1': 'From PDF', 's2.l2': 'From Notion', 's2.l3': 'Written by hand',
      's3.num': '03 · The run', 's3.h': 'Run. The session ticks itself off',
      's3.p': "Record the run with your watch, as always. When you open the app, today's session is already done, with your numbers next to the target.",
      's3.l1': 'No manual entry', 's3.l2': 'Target and result', 's3.l3': 'Weights and bodyweight too, with exercises and loads',
      's4.num': '04 · The analysis', 's4.h': 'Map, pace and heart rate for every run',
      's4.p': 'Route, splits per kilometre, heart-rate zones, cadence and elevation. If two apps record the same run, UNICUM merges them into one.',
      's4.l1': 'Splits and intervals', 's4.l2': 'Heart-rate zones', 's4.l3': 'Shoes and mileage',
      's5.num': '05 · Recovery', 's5.h': 'Know if you can push today',
      's5.p': "A recovery estimate from nightly HRV, sleep and training load: it tells you how many hours until you're ready for the next hard session.",
      's5.l1': 'Nightly HRV', 's5.l2': 'Sleep', 's5.l3': 'Measured load',
      'data.h': 'Your data is yours. Really.',
      'c1.h': 'Accounts to create', 'c1.p': 'No email, no password. Install it and use it.',
      'c2.h': 'Servers', 'c2.p': 'Your data stays on your phone, in a local database and in Health Connect. Anonymous usage stats are optional.',
      'c3.big': '€0', 'c3.h': 'Forever', 'c3.p': 'No subscription and no ads.',
      'c4.h': 'Public code', 'c4.p': 'Free software: anyone can check what it does. ' + PRIVACY + 'Privacy</a>',
      'prova.tag': 'Try it now', 'prova.h': 'Download it and tell me what you think',
      'prova.p': "For now UNICUM installs from the APK on GitHub, and feedback comes straight to me. All you need is an Android phone: you don't have to be an expert, or even a runner.",
      'how1.h': 'Download the APK', 'how1.p': 'On your phone, tap the button: GitHub opens with the file of the latest version.',
      'how1.btn': 'Download the APK', 'how1.meta': 'Latest version · about 30 MB',
      'how2.h': 'Install it', 'how2.p': 'Android asks for permission to install apps from this source: allow it, just this once. Then answer the first-launch questions.',
      'how2.btn': 'Is it safe?',
      'how3.h': 'Tell me how it goes', 'how3.p': "A problem, a PDF it can't read, an idea or just how it went: write to me privately or on GitHub, whichever you prefer.",
      'how3.btn': 'Write to me',
      'play.tag': 'Next step', 'play.h': 'Would you join the Play Store test?',
      'play.p': "Before an app can go on the Play Store, Google requires a test with at least 12 people for 14 days: you'd install the app from the Play Store and keep it for two weeks. Answer the survey and I'll let you know when it starts.",
      'play.btn': 'Take the survey', 'play.small': '1 minute · Google Form in Italian, answer in English',
      'faq.h': 'Frequently asked questions',
      'q1.q': 'Is it safe to install an APK?',
      'q1.a': "Yes, if it comes from a source you know. This one is built from the public code on GitHub and signed by me: anyone can check what it does. Android only asks for permission because the file doesn't come from the Play Store.",
      'q2.q': 'Does it work with my watch or ring?',
      'q2.a': "Yes, if its app writes data to Health Connect: Samsung Health, Fitbit, Oura, Ultrahuman and many others do it on their own. Garmin doesn't do it directly: you need a bridge app such as Health Sync.",
      'q3.q': 'What is Health Connect, and do I need it?',
      'q3.a': "It's the place in Android where health apps share data. Since Android 14 it's built in; on older versions it's a free install from the Play Store. Without it UNICUM still works for meals and the plan, but it can't see sleep and runs.",
      'q4.q': 'How do I update it?',
      'q4.a': "Download the new APK from the same page and install it over the old one: your data stays. Once it's on the Play Store it will update by itself.",
      'q5.q': 'Is there an iPhone version?',
      'q5.a': "No, Android only for now. If you'd like it on iPhone, " + FORM + 'tell me</a>: it helps me understand how many people are looking for it.',
      'q6.q': 'Does it cost anything?', 'q6.a': 'No. No subscription, no ads, no in-app purchases.',
      'f.survey': 'Play Store survey', 'f.privacy': 'Privacy', 'f.code': 'Code on GitHub', 'f.lang': 'Language',
      'f.credit': 'UNICUM is a personal project by Paolo Gobbetti (peol-13), free software under the GPL-3.0 licence.<br>The estimates in the app (recovery, calories, times) are guidance only: UNICUM is not a medical device.',
      'dlg.h': 'How would you like to write to me?', 'dlg.close': 'Close',
      'dlg.sub': 'I read everything, and every message ends up in a better version.',
      'dlg.c1h': 'Privately', 'dlg.c1p': 'A one-minute form, no account. The form is in Italian, just write in English: it comes straight to my email.',
      'dlg.c2h': 'On GitHub, in public', 'dlg.c2p': 'Others can read it and add comments. You need a free GitHub account.'
    },

    es: {
      'meta.title': 'UNICUM · Anillo, reloj, comidas y plan de carrera en una sola app',
      'meta.desc': 'App Android gratuita y de código abierto para runners: sueño, recuperación, comidas, carreras y el plan de tu entrenador en una pantalla. Sin cuenta, tus datos se quedan en el móvil.',
      'nav.how': 'Cómo funciona', 'nav.faq': 'Preguntas', 'nav.get': 'Descargar',
      'hero.h1': 'Anillo, reloj, comidas y plan de carrera. En una sola app.',
      'hero.lead': 'Abres una pantalla y sabes cómo estás hoy y qué carrera te toca. Gratis, de código abierto, sin cuenta: tus datos se quedan en tu móvil.',
      'hero.btn': 'Descargar para Android',
      'hero.small': 'Gratis · Android 8 o superior · Sin publicidad',
      'hero.alt': 'La pantalla principal de UNICUM: calorías, déficit y macros del día',
      'tape.pdf': 'PDF del entrenador',
      'intro.h': '¿Cinco apps para saber si hoy puedes correr?',
      'intro.p': 'El sueño está en la app del anillo, las carreras en la del reloj, las comidas en otra y el plan en un PDF. UNICUM lo lee todo y lo junta. Desliza para ver cómo.',
      's1.num': '01 · El día', 's1.h': 'Todo lo que importa, en una pantalla',
      's1.p': 'Cuánto has comido, cuánto has gastado y cuánto te queda. El déficit es el real: el gasto en reposo se acumula a lo largo del día, no todo al amanecer.',
      's1.l1': 'Calorías y macros', 's1.l2': 'Déficit hora a hora', 's1.l3': 'Código de barras',
      's2.num': '02 · El plan', 's2.h': 'El PDF del entrenador se convierte en un plan',
      's2.p': 'Eliges el archivo y en diez segundos las sesiones ya están divididas por día, tipo, kilómetros y ritmo. Revisas la vista previa, corriges si hace falta y guardas.',
      's2.l1': 'Desde PDF', 's2.l2': 'Desde Notion', 's2.l3': 'Escrito a mano',
      's3.num': '03 · La carrera', 's3.h': 'Corre. La sesión se marca sola',
      's3.p': 'Registras la carrera con tu reloj, como siempre. Cuando abres la app, la sesión de hoy ya está hecha, con tus números junto al objetivo.',
      's3.l1': 'Sin introducir nada a mano', 's3.l2': 'Objetivo y resultado', 's3.l3': 'También pesas y peso corporal, con ejercicios y cargas',
      's4.num': '04 · El análisis', 's4.h': 'Mapa, ritmo y pulso de cada salida',
      's4.p': 'Recorrido, parciales por kilómetro, zonas cardíacas, cadencia y desnivel. Si dos apps registran la misma carrera, UNICUM las une en una.',
      's4.l1': 'Parciales y series', 's4.l2': 'Zonas cardíacas', 's4.l3': 'Zapatillas y kilómetros',
      's5.num': '05 · La recuperación', 's5.h': 'Sabes si hoy puedes apretar',
      's5.p': 'Una estimación de la recuperación a partir de la HRV nocturna, el sueño y la carga de entrenamiento: te dice en cuántas horas estarás listo para la próxima sesión intensa.',
      's5.l1': 'HRV nocturna', 's5.l2': 'Sueño', 's5.l3': 'Carga medida',
      'data.h': 'Los datos son tuyos. De verdad.',
      'c1.h': 'Cuentas que crear', 'c1.p': 'Sin email, sin contraseña. La instalas y la usas.',
      'c2.h': 'Servidores', 'c2.p': 'Tus datos se quedan en el móvil, en una base de datos local y en Health Connect. Las estadísticas de uso anónimas son opcionales.',
      'c3.big': '0 €', 'c3.h': 'Para siempre', 'c3.p': 'Sin suscripción y sin publicidad.',
      'c4.h': 'Código público', 'c4.p': 'Software libre: cualquiera puede comprobar lo que hace. ' + PRIVACY + 'Privacidad</a>',
      'prova.tag': 'Pruébala ya', 'prova.h': 'Descárgala y dime qué te parece',
      'prova.p': 'Por ahora UNICUM se instala desde el APK en GitHub, y las opiniones me llegan directamente a mí. Solo necesitas un móvil Android: no hace falta ser experto, ni siquiera runner.',
      'how1.h': 'Descarga el APK', 'how1.p': 'Desde el móvil, toca el botón: se abre la página de GitHub con el archivo de la última versión.',
      'how1.btn': 'Descargar el APK', 'how1.meta': 'Última versión · unos 30 MB',
      'how2.h': 'Instálala', 'how2.p': 'Android te pide permiso para instalar apps de este origen: acéptalo, solo esta vez. Luego responde a las preguntas del primer inicio.',
      'how2.btn': '¿Es seguro?',
      'how3.h': 'Dime qué tal', 'how3.p': 'Un problema, un PDF que no lee, una idea o simplemente cómo te ha ido: escríbeme en privado o en GitHub, como prefieras.',
      'how3.btn': 'Escríbeme',
      'play.tag': 'Próximo paso', 'play.h': '¿Te apuntarías a la prueba en Play Store?',
      'play.p': 'Para publicarla en Play Store, Google pide antes una prueba con al menos 12 personas durante 14 días: instalarías la app desde Play Store y la mantendrías dos semanas. Responde a la encuesta y te aviso cuando empiece.',
      'play.btn': 'Responder a la encuesta', 'play.small': '1 minuto · Formulario de Google en italiano, responde en tu idioma',
      'faq.h': 'Preguntas frecuentes',
      'q1.q': '¿Es seguro instalar un APK?',
      'q1.a': 'Sí, si viene de una fuente que conoces. Este está compilado a partir del código público de GitHub y firmado por mí: cualquiera puede comprobar lo que hace. Android pide permiso solo porque el archivo no viene de Play Store.',
      'q2.q': '¿Funciona con mi reloj o anillo?',
      'q2.a': 'Sí, si su app escribe los datos en Health Connect: Samsung Health, Fitbit, Oura, Ultrahuman y muchas otras lo hacen solas. Garmin no lo hace directamente: hace falta una app puente como Health Sync.',
      'q3.q': '¿Qué es Health Connect y lo necesito?',
      'q3.a': 'Es el espacio de Android donde las apps de salud comparten datos. Desde Android 14 ya viene en el móvil; antes se instala gratis desde Play Store. Sin él, UNICUM funciona igual para comidas y plan, pero no ve el sueño ni las carreras.',
      'q4.q': '¿Cómo la actualizo?',
      'q4.a': 'Descargas el nuevo APK de la misma página y lo instalas encima: tus datos se mantienen. Cuando esté en Play Store se actualizará sola.',
      'q5.q': '¿Hay versión para iPhone?',
      'q5.a': 'No, por ahora solo Android. Si la quieres en iPhone, ' + FORM + 'dímelo</a>: me ayuda a saber cuántas personas la buscan.',
      'q6.q': '¿Cuesta algo?', 'q6.a': 'No. Sin suscripción, sin publicidad, sin compras dentro de la app.',
      'f.survey': 'Encuesta Play Store', 'f.privacy': 'Privacidad', 'f.code': 'Código en GitHub', 'f.lang': 'Idioma',
      'f.credit': 'UNICUM es un proyecto personal de Paolo Gobbetti (peol-13), software libre con licencia GPL-3.0.<br>Las estimaciones de la app (recuperación, calorías, tiempos) son orientativas: UNICUM no es un dispositivo médico.',
      'dlg.h': '¿Cómo prefieres escribirme?', 'dlg.close': 'Cerrar',
      'dlg.sub': 'Lo leo todo, y cada mensaje acaba en una versión mejor.',
      'dlg.c1h': 'En privado', 'dlg.c1p': 'Un formulario de un minuto, sin cuenta. Está en italiano, pero escribe en tu idioma: me llega directamente al email.',
      'dlg.c2h': 'En GitHub, en público', 'dlg.c2p': 'Los demás pueden leerlo y añadir comentarios. Necesitas una cuenta de GitHub, gratuita.'
    },

    fr: {
      'meta.title': 'UNICUM · Bague, montre, repas et plan de course dans une seule app',
      'meta.desc': "App Android gratuite et open source pour les coureurs : sommeil, récupération, repas, sorties et plan de ton coach sur un seul écran. Sans compte, tes données restent sur ton téléphone.",
      'nav.how': 'Comment ça marche', 'nav.faq': 'Questions', 'nav.get': 'Télécharger',
      'hero.h1': 'Bague, montre, repas et plan de course. Dans une seule app.',
      'hero.lead': "Tu ouvres un écran et tu sais comment tu vas aujourd'hui et quelle sortie t'attend. Gratuite, open source, sans compte : tes données restent sur ton téléphone.",
      'hero.btn': 'Télécharger pour Android',
      'hero.small': 'Gratuite · Android 8 ou plus récent · Sans publicité',
      'hero.alt': "L'écran principal d'UNICUM : calories, déficit et macros de la journée",
      'tape.pdf': 'PDF du coach',
      'intro.h': "Cinq apps pour savoir si tu peux courir aujourd'hui ?",
      'intro.p': "Le sommeil est dans l'app de la bague, les sorties dans celle de la montre, les repas dans une autre, le plan dans un PDF. UNICUM lit tout et rassemble le tout. Fais défiler pour voir comment.",
      's1.num': '01 · La journée', 's1.h': 'Tout ce qui compte, sur un seul écran',
      's1.p': "Ce que tu as mangé, ce que tu as dépensé et ce qu'il te reste. Le déficit est le vrai : la dépense au repos s'accumule au fil de la journée, pas tout d'un coup à l'aube.",
      's1.l1': 'Calories et macros', 's1.l2': 'Déficit heure par heure', 's1.l3': 'Code-barres',
      's2.num': '02 · Le plan', 's2.h': 'Le PDF du coach devient un plan',
      's2.p': "Tu choisis le fichier et en dix secondes les séances sont déjà réparties par jour, type, kilomètres et allure. Tu vérifies l'aperçu, tu corriges si besoin, tu enregistres.",
      's2.l1': 'Depuis un PDF', 's2.l2': 'Depuis Notion', 's2.l3': 'Écrit à la main',
      's3.num': '03 · La sortie', 's3.h': 'Cours. La séance se coche toute seule',
      's3.p': "Tu enregistres ta sortie avec ta montre, comme d'habitude. Quand tu ouvres l'app, la séance du jour est déjà faite, avec tes chiffres à côté de l'objectif.",
      's3.l1': 'Aucune saisie manuelle', 's3.l2': 'Objectif et résultat', 's3.l3': 'Musculation et poids du corps aussi, avec exercices et charges',
      's4.num': "04 · L'analyse", 's4.h': 'Carte, allure et fréquence cardiaque de chaque sortie',
      's4.p': "Parcours, temps au kilomètre, zones cardiaques, cadence et dénivelé. Si deux apps enregistrent la même sortie, UNICUM les fusionne en une seule.",
      's4.l1': 'Temps intermédiaires et fractionnés', 's4.l2': 'Zones cardiaques', 's4.l3': 'Chaussures et kilomètres',
      's5.num': '05 · La récupération', 's5.h': "Tu sais si tu peux forcer aujourd'hui",
      's5.p': "Une estimation de la récupération à partir de la VFC nocturne, du sommeil et de la charge d'entraînement : elle te dit dans combien d'heures tu seras prêt pour la prochaine séance intense.",
      's5.l1': 'VFC nocturne', 's5.l2': 'Sommeil', 's5.l3': 'Charge mesurée',
      'data.h': 'Tes données sont à toi. Vraiment.',
      'c1.h': 'Compte à créer', 'c1.p': "Pas d'email, pas de mot de passe. Tu l'installes et tu l'utilises.",
      'c2.h': 'Serveur', 'c2.p': "Tes données restent sur ton téléphone, dans une base de données locale et dans Health Connect. Les statistiques d'utilisation anonymes sont facultatives.",
      'c3.big': '0 €', 'c3.h': 'Pour toujours', 'c3.p': "Pas d'abonnement et pas de publicité.",
      'c4.h': 'Code public', 'c4.p': 'Logiciel libre : tout le monde peut vérifier ce qu\'il fait. ' + PRIVACY + 'Confidentialité</a>',
      'prova.tag': "Essaie-la maintenant", 'prova.h': "Télécharge-la et dis-moi ce que tu en penses",
      'prova.p': "Pour l'instant UNICUM s'installe depuis l'APK sur GitHub, et les avis m'arrivent directement. Il te faut juste un téléphone Android : pas besoin d'être expert, ni même coureur.",
      'how1.h': "Télécharge l'APK", 'how1.p': "Depuis ton téléphone, touche le bouton : la page GitHub s'ouvre avec le fichier de la dernière version.",
      'how1.btn': "Télécharger l'APK", 'how1.meta': 'Dernière version · environ 30 Mo',
      'how2.h': 'Installe-la', 'how2.p': "Android te demande l'autorisation d'installer des apps depuis cette source : accepte, juste cette fois. Puis réponds aux questions du premier lancement.",
      'how2.btn': "C'est sûr ?",
      'how3.h': 'Dis-moi ce que ça donne', 'how3.p': "Un problème, un PDF qu'elle ne lit pas, une idée ou juste ton ressenti : écris-moi en privé ou sur GitHub, comme tu préfères.",
      'how3.btn': 'Écris-moi',
      'play.tag': 'Prochaine étape', 'play.h': 'Tu participerais au test sur le Play Store ?',
      'play.p': "Pour la publier sur le Play Store, Google demande d'abord un test avec au moins 12 personnes pendant 14 jours : tu installerais l'app depuis le Play Store et tu la garderais deux semaines. Réponds au sondage et je te préviens quand ça commence.",
      'play.btn': 'Répondre au sondage', 'play.small': '1 minute · Formulaire Google en italien, réponds dans ta langue',
      'faq.h': 'Questions fréquentes',
      'q1.q': 'Est-ce sûr d\'installer un APK ?',
      'q1.a': "Oui, s'il vient d'une source que tu connais. Celui-ci est compilé à partir du code public sur GitHub et signé par moi : tout le monde peut vérifier ce qu'il fait. Android demande l'autorisation seulement parce que le fichier ne vient pas du Play Store.",
      'q2.q': 'Ça marche avec ma montre ou ma bague ?',
      'q2.a': "Oui, si son app écrit les données dans Health Connect : Samsung Health, Fitbit, Oura, Ultrahuman et beaucoup d'autres le font toutes seules. Garmin ne le fait pas directement : il faut une app passerelle comme Health Sync.",
      'q3.q': "C'est quoi Health Connect, et j'en ai besoin ?",
      'q3.a': "C'est l'espace d'Android où les apps de santé partagent leurs données. Depuis Android 14 il est intégré au téléphone ; avant, il s'installe gratuitement depuis le Play Store. Sans lui, UNICUM fonctionne quand même pour les repas et le plan, mais ne voit ni le sommeil ni les sorties.",
      'q4.q': 'Comment la mettre à jour ?',
      'q4.a': "Tu télécharges le nouvel APK sur la même page et tu l'installes par-dessus : tes données restent. Quand elle sera sur le Play Store, elle se mettra à jour toute seule.",
      'q5.q': 'Elle existe sur iPhone ?',
      'q5.a': "Non, pour l'instant seulement sur Android. Si tu la voudrais sur iPhone, " + FORM + 'dis-le-moi</a> : ça m\'aide à savoir combien de personnes la cherchent.',
      'q6.q': 'Elle coûte quelque chose ?', 'q6.a': "Non. Pas d'abonnement, pas de publicité, pas d'achats intégrés.",
      'f.survey': 'Sondage Play Store', 'f.privacy': 'Confidentialité', 'f.code': 'Code sur GitHub', 'f.lang': 'Langue',
      'f.credit': "UNICUM est un projet personnel de Paolo Gobbetti (peol-13), logiciel libre sous licence GPL-3.0.<br>Les estimations de l'app (récupération, calories, temps) sont indicatives : UNICUM n'est pas un dispositif médical.",
      'dlg.h': 'Comment préfères-tu m\'écrire ?', 'dlg.close': 'Fermer',
      'dlg.sub': 'Je lis tout, et chaque message finit dans une meilleure version.',
      'dlg.c1h': 'En privé', 'dlg.c1p': "Un formulaire d'une minute, sans compte. Il est en italien, mais écris dans ta langue : il arrive directement dans ma boîte mail.",
      'dlg.c2h': 'Sur GitHub, en public', 'dlg.c2p': 'Les autres peuvent lire et ajouter des commentaires. Il faut un compte GitHub, gratuit.'
    },

    de: {
      'meta.title': 'UNICUM · Ring, Uhr, Mahlzeiten und Laufplan in einer App',
      'meta.desc': 'Kostenlose Open-Source-Android-App für Läufer: Schlaf, Erholung, Mahlzeiten, Läufe und der Plan deines Trainers auf einem Bildschirm. Ohne Konto, deine Daten bleiben auf dem Handy.',
      'nav.how': "So funktioniert's", 'nav.faq': 'Fragen', 'nav.get': 'Herunterladen',
      'hero.h1': 'Ring, Uhr, Mahlzeiten und Laufplan. In einer App.',
      'hero.lead': 'Ein Bildschirm, und du weißt, wie es dir heute geht und welcher Lauf ansteht. Kostenlos, Open Source, ohne Konto: deine Daten bleiben auf deinem Handy.',
      'hero.btn': 'Für Android herunterladen',
      'hero.small': 'Kostenlos · Android 8 oder neuer · Keine Werbung',
      'hero.alt': 'Der Hauptbildschirm von UNICUM: Kalorien, Defizit und Makros des Tages',
      'tape.pdf': 'PDF vom Trainer',
      'intro.h': 'Fünf Apps, um zu wissen, ob du heute laufen kannst?',
      'intro.p': 'Der Schlaf steckt in der Ring-App, die Läufe in der Uhr-App, die Mahlzeiten in einer anderen, der Plan in einem PDF. UNICUM liest alles und führt es zusammen. Scroll weiter, um zu sehen wie.',
      's1.num': '01 · Der Tag', 's1.h': 'Alles, was zählt, auf einem Bildschirm',
      's1.p': 'Wie viel du gegessen hast, wie viel du verbraucht hast und wie viel noch fehlt. Das Defizit ist das echte: der Ruheumsatz baut sich über den Tag auf, nicht komplett am Morgen.',
      's1.l1': 'Kalorien und Makros', 's1.l2': 'Defizit Stunde für Stunde', 's1.l3': 'Barcode-Scanner',
      's2.num': '02 · Der Plan', 's2.h': 'Das PDF vom Trainer wird zum Plan',
      's2.p': 'Datei auswählen, und nach zehn Sekunden sind die Einheiten nach Tag, Art, Kilometern und Tempo aufgeteilt. Vorschau prüfen, bei Bedarf korrigieren, speichern.',
      's2.l1': 'Aus PDF', 's2.l2': 'Aus Notion', 's2.l3': 'Von Hand geschrieben',
      's3.num': '03 · Der Lauf', 's3.h': 'Lauf los. Die Einheit hakt sich selbst ab',
      's3.p': 'Du zeichnest den Lauf wie immer mit deiner Uhr auf. Wenn du die App öffnest, ist die heutige Einheit schon erledigt, mit deinen Zahlen neben dem Ziel.',
      's3.l1': 'Keine manuelle Eingabe', 's3.l2': 'Ziel und Ergebnis', 's3.l3': 'Auch Krafttraining und Eigengewicht, mit Übungen und Gewichten',
      's4.num': '04 · Die Analyse', 's4.h': 'Karte, Tempo und Puls jedes Laufs',
      's4.p': 'Strecke, Kilometerzeiten, Herzfrequenzzonen, Schrittfrequenz und Höhenmeter. Wenn zwei Apps denselben Lauf aufzeichnen, führt UNICUM sie zu einem zusammen.',
      's4.l1': 'Zwischenzeiten und Intervalle', 's4.l2': 'Herzfrequenzzonen', 's4.l3': 'Schuhe und Kilometer',
      's5.num': '05 · Die Erholung', 's5.h': 'Du weißt, ob du heute Gas geben kannst',
      's5.p': 'Eine Schätzung der Erholung aus nächtlicher HRV, Schlaf und Trainingsbelastung: sie sagt dir, in wie vielen Stunden du für die nächste harte Einheit bereit bist.',
      's5.l1': 'Nächtliche HRV', 's5.l2': 'Schlaf', 's5.l3': 'Gemessene Belastung',
      'data.h': 'Deine Daten gehören dir. Wirklich.',
      'c1.h': 'Konten zu erstellen', 'c1.p': 'Keine E-Mail, kein Passwort. Installieren und loslegen.',
      'c2.h': 'Server', 'c2.p': 'Deine Daten bleiben auf dem Handy, in einer lokalen Datenbank und in Health Connect. Anonyme Nutzungsstatistiken sind freiwillig.',
      'c3.big': '0 €', 'c3.h': 'Für immer', 'c3.p': 'Kein Abo und keine Werbung.',
      'c4.h': 'Offener Code', 'c4.p': 'Freie Software: jeder kann prüfen, was sie tut. ' + PRIVACY + 'Datenschutz</a>',
      'prova.tag': 'Jetzt ausprobieren', 'prova.h': 'Lade sie herunter und sag mir, wie du sie findest',
      'prova.p': 'Im Moment wird UNICUM über die APK auf GitHub installiert, und das Feedback kommt direkt bei mir an. Du brauchst nur ein Android-Handy: du musst kein Experte sein, nicht einmal Läufer.',
      'how1.h': 'APK herunterladen', 'how1.p': 'Tippe auf dem Handy auf den Button: die GitHub-Seite mit der Datei der neuesten Version öffnet sich.',
      'how1.btn': 'APK herunterladen', 'how1.meta': 'Neueste Version · etwa 30 MB',
      'how2.h': 'Installieren', 'how2.p': 'Android fragt nach der Erlaubnis, Apps aus dieser Quelle zu installieren: erlaube es, nur dieses eine Mal. Dann beantworte die Fragen beim ersten Start.',
      'how2.btn': 'Ist das sicher?',
      'how3.h': 'Sag mir, wie es läuft', 'how3.p': 'Ein Problem, ein PDF, das sie nicht liest, eine Idee oder einfach, wie es war: schreib mir privat oder auf GitHub, wie du willst.',
      'how3.btn': 'Schreib mir',
      'play.tag': 'Nächster Schritt', 'play.h': 'Wärst du beim Test im Play Store dabei?',
      'play.p': 'Bevor eine App in den Play Store darf, verlangt Google einen Test mit mindestens 12 Personen über 14 Tage: du würdest die App aus dem Play Store installieren und zwei Wochen behalten. Beantworte die Umfrage, und ich sage dir Bescheid, wenn es losgeht.',
      'play.btn': 'Zur Umfrage', 'play.small': '1 Minute · Google-Formular auf Italienisch, antworte in deiner Sprache',
      'faq.h': 'Häufige Fragen',
      'q1.q': 'Ist es sicher, eine APK zu installieren?',
      'q1.a': 'Ja, wenn sie aus einer Quelle stammt, die du kennst. Diese wird aus dem öffentlichen Code auf GitHub gebaut und von mir signiert: jeder kann prüfen, was sie tut. Android fragt nur deshalb nach der Erlaubnis, weil die Datei nicht aus dem Play Store kommt.',
      'q2.q': 'Funktioniert sie mit meiner Uhr oder meinem Ring?',
      'q2.a': 'Ja, wenn deren App die Daten in Health Connect schreibt: Samsung Health, Fitbit, Oura, Ultrahuman und viele andere machen das von selbst. Garmin macht es nicht direkt: dafür brauchst du eine Brücken-App wie Health Sync.',
      'q3.q': 'Was ist Health Connect, und brauche ich das?',
      'q3.a': 'Das ist der Bereich in Android, in dem Gesundheits-Apps ihre Daten teilen. Ab Android 14 ist es schon auf dem Handy, davor installierst du es kostenlos aus dem Play Store. Ohne funktioniert UNICUM trotzdem für Mahlzeiten und Plan, sieht aber weder Schlaf noch Läufe.',
      'q4.q': 'Wie aktualisiere ich sie?',
      'q4.a': 'Lade die neue APK von derselben Seite herunter und installiere sie darüber: deine Daten bleiben erhalten. Sobald sie im Play Store ist, aktualisiert sie sich von selbst.',
      'q5.q': 'Gibt es sie für iPhone?',
      'q5.a': 'Nein, im Moment nur für Android. Wenn du sie gern auf dem iPhone hättest, ' + FORM + 'schreib es mir</a>: so sehe ich, wie viele Leute danach suchen.',
      'q6.q': 'Kostet sie etwas?', 'q6.a': 'Nein. Kein Abo, keine Werbung, keine In-App-Käufe.',
      'f.survey': 'Umfrage Play Store', 'f.privacy': 'Datenschutz', 'f.code': 'Code auf GitHub', 'f.lang': 'Sprache',
      'f.credit': 'UNICUM ist ein persönliches Projekt von Paolo Gobbetti (peol-13), freie Software unter der Lizenz GPL-3.0.<br>Die Schätzungen der App (Erholung, Kalorien, Zeiten) sind nur Richtwerte: UNICUM ist kein Medizinprodukt.',
      'dlg.h': 'Wie möchtest du mir schreiben?', 'dlg.close': 'Schließen',
      'dlg.sub': 'Ich lese alles, und jede Nachricht fließt in eine bessere Version ein.',
      'dlg.c1h': 'Privat', 'dlg.c1p': 'Ein Formular, eine Minute, ohne Konto. Es ist auf Italienisch, aber schreib in deiner Sprache: es landet direkt in meinem Postfach.',
      'dlg.c2h': 'Auf GitHub, öffentlich', 'dlg.c2p': 'Andere können es lesen und kommentieren. Du brauchst ein kostenloses GitHub-Konto.'
    },

    pt: {
      'meta.title': 'UNICUM · Anel, relógio, refeições e plano de corrida numa só app',
      'meta.desc': 'App Android gratuita e de código aberto para corredores: sono, recuperação, refeições, corridas e o plano do teu treinador num só ecrã. Sem conta, os teus dados ficam no telemóvel.',
      'nav.how': 'Como funciona', 'nav.faq': 'Perguntas', 'nav.get': 'Descarregar',
      'hero.h1': 'Anel, relógio, refeições e plano de corrida. Numa só app.',
      'hero.lead': 'Abres um ecrã e sabes como estás hoje e que corrida te espera. Gratuita, de código aberto, sem conta: os teus dados ficam no teu telemóvel.',
      'hero.btn': 'Descarregar para Android',
      'hero.small': 'Gratuita · Android 8 ou superior · Sem publicidade',
      'hero.alt': 'O ecrã principal da UNICUM: calorias, défice e macros do dia',
      'tape.pdf': 'PDF do treinador',
      'intro.h': 'Cinco apps para saber se hoje podes correr?',
      'intro.p': 'O sono está na app do anel, as corridas na do relógio, as refeições noutra, o plano num PDF. A UNICUM lê tudo e junta tudo. Desliza para ver como.',
      's1.num': '01 · O dia', 's1.h': 'Tudo o que importa, num só ecrã',
      's1.p': 'Quanto comeste, quanto gastaste e quanto te falta. O défice é o verdadeiro: o gasto em repouso acumula-se ao longo do dia, não todo de madrugada.',
      's1.l1': 'Calorias e macros', 's1.l2': 'Défice hora a hora', 's1.l3': 'Código de barras',
      's2.num': '02 · O plano', 's2.h': 'O PDF do treinador torna-se um plano',
      's2.p': 'Escolhes o ficheiro e em dez segundos as sessões já estão divididas por dia, tipo, quilómetros e ritmo. Confirmas a pré-visualização, corriges se for preciso e guardas.',
      's2.l1': 'A partir de PDF', 's2.l2': 'A partir do Notion', 's2.l3': 'Escrito à mão',
      's3.num': '03 · A corrida', 's3.h': 'Corre. A sessão marca-se sozinha',
      's3.p': 'Registas a corrida com o teu relógio, como sempre. Quando abres a app, a sessão de hoje já está feita, com os teus números ao lado do objetivo.',
      's3.l1': 'Nada para escrever à mão', 's3.l2': 'Objetivo e resultado', 's3.l3': 'Também musculação e peso corporal, com exercícios e cargas',
      's4.num': '04 · A análise', 's4.h': 'Mapa, ritmo e frequência cardíaca de cada saída',
      's4.p': 'Percurso, parciais por quilómetro, zonas cardíacas, cadência e desnível. Se duas apps registarem a mesma corrida, a UNICUM junta-as numa só.',
      's4.l1': 'Parciais e séries', 's4.l2': 'Zonas cardíacas', 's4.l3': 'Sapatilhas e quilómetros',
      's5.num': '05 · A recuperação', 's5.h': 'Sabes se hoje podes puxar',
      's5.p': 'Uma estimativa da recuperação a partir da HRV noturna, do sono e da carga de treino: diz-te daqui a quantas horas estarás pronto para a próxima sessão intensa.',
      's5.l1': 'HRV noturna', 's5.l2': 'Sono', 's5.l3': 'Carga medida',
      'data.h': 'Os dados são teus. A sério.',
      'c1.h': 'Contas para criar', 'c1.p': 'Sem email, sem palavra-passe. Instalas e usas.',
      'c2.h': 'Servidores', 'c2.p': 'Os teus dados ficam no telemóvel, numa base de dados local e no Health Connect. As estatísticas de uso anónimas são opcionais.',
      'c3.big': '0 €', 'c3.h': 'Para sempre', 'c3.p': 'Sem subscrição e sem publicidade.',
      'c4.h': 'Código público', 'c4.p': 'Software livre: qualquer pessoa pode verificar o que faz. ' + PRIVACY + 'Privacidade</a>',
      'prova.tag': 'Experimenta já', 'prova.h': 'Descarrega-a e diz-me o que achas',
      'prova.p': 'Por agora a UNICUM instala-se a partir do APK no GitHub, e as opiniões chegam diretamente a mim. Só precisas de um telemóvel Android: não tens de ser especialista, nem sequer corredor.',
      'how1.h': 'Descarrega o APK', 'how1.p': 'No telemóvel, toca no botão: abre-se a página do GitHub com o ficheiro da última versão.',
      'how1.btn': 'Descarregar o APK', 'how1.meta': 'Última versão · cerca de 30 MB',
      'how2.h': 'Instala-a', 'how2.p': 'O Android pede autorização para instalar apps desta origem: aceita, só desta vez. Depois responde às perguntas do primeiro arranque.',
      'how2.btn': 'É seguro?',
      'how3.h': 'Diz-me como correu', 'how3.p': 'Um problema, um PDF que não lê, uma ideia ou só como correu: escreve-me em privado ou no GitHub, como preferires.',
      'how3.btn': 'Escreve-me',
      'play.tag': 'Próximo passo', 'play.h': 'Entravas no teste da Play Store?',
      'play.p': 'Para a publicar na Play Store, a Google pede primeiro um teste com pelo menos 12 pessoas durante 14 dias: instalavas a app pela Play Store e ficavas com ela duas semanas. Responde ao inquérito e aviso-te quando começar.',
      'play.btn': 'Responder ao inquérito', 'play.small': '1 minuto · Formulário Google em italiano, responde na tua língua',
      'faq.h': 'Perguntas frequentes',
      'q1.q': 'É seguro instalar um APK?',
      'q1.a': 'Sim, se vier de uma fonte que conheces. Este é compilado a partir do código público no GitHub e assinado por mim: qualquer pessoa pode verificar o que faz. O Android pede autorização só porque o ficheiro não vem da Play Store.',
      'q2.q': 'Funciona com o meu relógio ou anel?',
      'q2.a': 'Sim, se a app dele escrever os dados no Health Connect: Samsung Health, Fitbit, Oura, Ultrahuman e muitas outras fazem-no sozinhas. A Garmin não o faz diretamente: é preciso uma app ponte como o Health Sync.',
      'q3.q': 'O que é o Health Connect, e preciso dele?',
      'q3.a': 'É o espaço do Android onde as apps de saúde partilham dados. A partir do Android 14 já vem no telemóvel; antes instala-se grátis pela Play Store. Sem ele, a UNICUM funciona na mesma para refeições e plano, mas não vê o sono nem as corridas.',
      'q4.q': 'Como a atualizo?',
      'q4.a': 'Descarregas o novo APK da mesma página e instalas por cima: os teus dados ficam. Quando estiver na Play Store, atualiza-se sozinha.',
      'q5.q': 'Há para iPhone?',
      'q5.a': 'Não, por agora só Android. Se a quiseres no iPhone, ' + FORM + 'diz-me</a>: ajuda-me a perceber quantas pessoas a procuram.',
      'q6.q': 'Custa alguma coisa?', 'q6.a': 'Não. Sem subscrição, sem publicidade, sem compras na app.',
      'f.survey': 'Inquérito Play Store', 'f.privacy': 'Privacidade', 'f.code': 'Código no GitHub', 'f.lang': 'Idioma',
      'f.credit': 'A UNICUM é um projeto pessoal de Paolo Gobbetti (peol-13), software livre com licença GPL-3.0.<br>As estimativas da app (recuperação, calorias, tempos) são indicativas: a UNICUM não é um dispositivo médico.',
      'dlg.h': 'Como preferes escrever-me?', 'dlg.close': 'Fechar',
      'dlg.sub': 'Leio tudo, e cada mensagem acaba numa versão melhor.',
      'dlg.c1h': 'Em privado', 'dlg.c1p': 'Um formulário de um minuto, sem conta. Está em italiano, mas escreve na tua língua: chega diretamente ao meu email.',
      'dlg.c2h': 'No GitHub, em público', 'dlg.c2p': 'Os outros podem ler e acrescentar comentários. Precisas de uma conta GitHub, gratuita.'
    }
  };

  var SUPPORTED = ['it', 'en', 'es', 'fr', 'de', 'pt'];
  var KEY = 'unicum-lang';
  var nodes = [].slice.call(document.querySelectorAll('[data-t],[data-t-alt],[data-t-aria]'));
  // Il testo italiano originale di ogni elemento, per poter tornare all'italiano senza ricaricare
  var orig = nodes.map(function (n) {
    return { html: n.innerHTML, alt: n.getAttribute('alt'), aria: n.getAttribute('aria-label') };
  });
  var meta = document.querySelector('meta[name="description"]');
  var origTitle = document.title, origDesc = meta ? meta.content : '';
  var buttons = [].slice.call(document.querySelectorAll('[data-lang]'));

  function ok(l) { return l && SUPPORTED.indexOf(l) >= 0; }

  function pick() {
    try {
      var q = (new URLSearchParams(location.search).get('lang') || '').toLowerCase();
      if (ok(q)) return q;
      var saved = localStorage.getItem(KEY);
      if (ok(saved)) return saved;
    } catch (e) {}
    var list = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || ''];
    for (var i = 0; i < list.length; i++) {
      var c = String(list[i] || '').slice(0, 2).toLowerCase();
      if (ok(c)) return c;
    }
    return 'en';
  }

  function apply(lang) {
    var t = lang === 'it' ? {} : (D[lang] || {});
    nodes.forEach(function (n, i) {
      var o = orig[i], k;
      if ((k = n.getAttribute('data-t'))) n.innerHTML = t[k] != null ? t[k] : o.html;
      if ((k = n.getAttribute('data-t-alt'))) n.setAttribute('alt', t[k] != null ? t[k] : o.alt);
      if ((k = n.getAttribute('data-t-aria'))) n.setAttribute('aria-label', t[k] != null ? t[k] : o.aria);
    });
    document.title = t['meta.title'] || origTitle;
    if (meta) meta.content = t['meta.desc'] || origDesc;
    document.documentElement.lang = lang;
    buttons.forEach(function (b) { b.setAttribute('aria-pressed', b.getAttribute('data-lang') === lang ? 'true' : 'false'); });
  }

  buttons.forEach(function (b) {
    b.addEventListener('click', function () {
      var l = b.getAttribute('data-lang');
      try { localStorage.setItem(KEY, l); } catch (e) {}
      apply(l);
    });
  });

  window.UNICUM_I18N = D;
  apply(pick());
})();
