package io.github.peol13.unicum

import io.github.peol13.unicum.ui.screens.ConsigliScreen
import io.github.peol13.unicum.ui.screens.DetailScreen
import androidx.activity.compose.BackHandler
import io.github.peol13.unicum.ui.theme.LocalUnicumColors
import androidx.compose.runtime.DisposableEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.peol13.unicum.ui.components.UnicumBrandScreen
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.peol13.unicum.data.AutoBackup
import io.github.peol13.unicum.data.BackgroundSync
import io.github.peol13.unicum.data.MealReminders
import io.github.peol13.unicum.widget.UnicumWidget
import android.content.Intent
import io.github.peol13.unicum.data.UserSettings
import io.github.peol13.unicum.data.UsageStats
import io.github.peol13.unicum.ui.screens.UsageStatsConsentDialog
import io.github.peol13.unicum.ui.screens.DashboardScreen
import io.github.peol13.unicum.ui.screens.DiarioScreen
import io.github.peol13.unicum.ui.screens.ProfiloScreen
import io.github.peol13.unicum.ui.screens.SetupScreen
import io.github.peol13.unicum.ui.screens.StatisticheScreen
import io.github.peol13.unicum.ui.screens.HomeEditorScreen
import io.github.peol13.unicum.ui.screens.AllenamentiScreen
import io.github.peol13.unicum.ui.screens.ShoeScreen
import io.github.peol13.unicum.ui.screens.ShoesScreen
import io.github.peol13.unicum.ui.screens.TrainingPlanScreen
import io.github.peol13.unicum.data.NotionPlan
import io.github.peol13.unicum.data.Shoes
import io.github.peol13.unicum.ui.screens.WorkoutListScreen
import io.github.peol13.unicum.ui.screens.WorkoutScreen
import io.github.peol13.unicum.ui.screens.WeekStoryScreen
import io.github.peol13.unicum.ui.Route
import io.github.peol13.unicum.ui.theme.UnicumTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UserSettings.init(applicationContext)
        NotionPlan.init(applicationContext)
        io.github.peol13.unicum.data.LocalPlan.init(applicationContext)
        Shoes.init(applicationContext)
        io.github.peol13.unicum.data.RunGoalStore.init(applicationContext)
        MealReminders.scheduleAll(applicationContext)
        AutoBackup.init(applicationContext)
        UsageStats.init(applicationContext)
        io.github.peol13.unicum.data.UpdateCheck.init(applicationContext)
        io.github.peol13.unicum.data.FeedbackPrompt.init(applicationContext)
        BackgroundSync.schedule(applicationContext)
        handleIntent(intent)

        lifecycleScope.launch {
            UnicumAppState.initPersistence(applicationContext)
            // Il salto di scala dell'HRV dai dati già salvati, senza aspettare la prima sincronizzazione
            runCatching { io.github.peol13.unicum.data.UnicumRepository.refreshHrvShift(applicationContext) }
            // Ogni volta che l'app torna in primo piano: passa al nuovo giorno se è cambiata la data
            // e aggiorna i dati da Health Connect
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Statistiche anonime (solo se l'utente ha detto sì): riepilogo dei giorni passati, max una volta al giorno
                launch { UsageStats.sendIfDue(usageFeatures()) }
                // Nuova versione su GitHub? Max una volta al giorno; il banner compare nella Home
                launch { io.github.peol13.unicum.data.UpdateCheck.checkIfDue() }
                // Giorni d'uso per la card "Come ti trovi?" (una volta al giorno)
                io.github.peol13.unicum.data.FeedbackPrompt.recordOpen()
                UnicumAppState.followToday(applicationContext)
                UnicumAppState.reloadFromDatabase(applicationContext)
                UnicumAppState.autoSync(applicationContext)
            }
        }

        // Barre di sistema trasparenti; il colore delle icone si adatta al tema qui sotto
        enableEdgeToEdge()

        setContent {
            UnicumTheme {
                // Icone delle barre di sistema chiare o scure in base al tema in uso
                val dark = LocalUnicumColors.current.isDark
                DisposableEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    this@MainActivity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                // All'avvio la schermata di brand (simbolo + nome) resta per circa un secondo, poi sfuma nell'app
                var showBrand by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1100)
                    showBrand = false
                }
                Crossfade(targetState = showBrand, animationSpec = tween(350), label = "brand") { brand ->
                    if (brand) UnicumBrandScreen() else AppContent()
                }
            }
        }
    }

    /** Funzioni in uso, senza valori: solo per le statistiche anonime. */
    private fun usageFeatures(): List<String> = buildList {
        if (UserSettings.lastSyncMillis > 0) add("health_connect")
        when (io.github.peol13.unicum.data.TrainingPlan.source) {
            io.github.peol13.unicum.data.TrainingPlan.Source.NOTION -> add("programma_notion")
            io.github.peol13.unicum.data.TrainingPlan.Source.LOCAL -> add("programma_app")
            io.github.peol13.unicum.data.TrainingPlan.Source.NONE -> {}
        }
        if (Shoes.data.shoes.isNotEmpty()) add("scarpe")
        add("home_" + UserSettings.dashboardMode.name.lowercase())
        add("tema_" + UserSettings.themeMode.name.lowercase())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Dal widget o da un promemoria: apre la registrazione del pasto o la sezione Allenamenti (una volta sola per Intent). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(io.github.peol13.unicum.data.WeeklySummary.EXTRA_OPEN_WEEK, false) == true) {
            intent.removeExtra(io.github.peol13.unicum.data.WeeklySummary.EXTRA_OPEN_WEEK)
            UnicumAppState.pendingOpenWeek = true
        }
        if (intent?.getBooleanExtra(UnicumWidget.EXTRA_OPEN_TRAINING, false) == true) {
            intent.removeExtra(UnicumWidget.EXTRA_OPEN_TRAINING)
            UnicumAppState.requestOpenTraining()
        }
        val type = intent?.getStringExtra(UnicumWidget.EXTRA_ADD_MEAL) ?: return
        intent.removeExtra(UnicumWidget.EXTRA_ADD_MEAL)
        UnicumAppState.requestAddMeal(applicationContext, type)
    }
}

@Composable
private fun AppContent() {
    // Primo avvio: schermata di benvenuto con i dati personali (alla fine chiede anche il permesso delle notifiche)
    if (!UserSettings.onboarded) {
        SetupScreen(onDone = {})
        return
    }

    // Guida rapida dopo il primo avvio (o chiesta da Profilo → Aiuto)
    if (UserSettings.tourPending) {
        io.github.peol13.unicum.ui.screens.TourScreen(onClose = { UserSettings.finishTour() })
        return
    }

    // Chi aveva già l'app: promemoria pasti e avvisi sul recupero partono accesi, ma da Android 13 serve il
    // permesso delle notifiche, chiesto una volta sola
    NotificationPermissionPrompt()

    // Salvata: resta la stessa anche se Android ricrea l'Activity (rotazione, tema del telefono)
    var screen by rememberSaveable(stateSaver = Route.Saver) { mutableStateOf<Route>(Route.Home) }
    // Da dove si è aperto il racconto della settimana (Home o Statistiche), per tornarci alla chiusura
    var storyReturn by rememberSaveable(stateSaver = Route.Saver) { mutableStateOf<Route>(Route.Home) }
    // Da dove si è aperta la pagina di un allenamento (sezione Allenamenti o elenco completo)
    var workoutReturn by rememberSaveable(stateSaver = Route.Saver) { mutableStateOf<Route>(Route.Allenamenti) }
    // Da dove si sono aperte le scarpe (sezione, pagina di una corsa, elenco delle scarpe)
    var shoesReturn by rememberSaveable(stateSaver = Route.Saver) { mutableStateOf<Route>(Route.Allenamenti) }
    var shoeReturn by rememberSaveable(stateSaver = Route.Saver) { mutableStateOf<Route>(Route.Shoes) }
    // Dove tornava la corsa da cui si è aperta una scarpa: si ripristina tornando a quella corsa
    var shoeWorkoutReturn by rememberSaveable(stateSaver = Route.Saver) { mutableStateOf<Route>(Route.Allenamenti) }

    fun navigate(route: Route) {
        val from = screen
        if (route is Route.WeekStory && from !is Route.WeekStory) storyReturn = from
        if (route is Route.Workout && from !is Route.Workout)
            workoutReturn = if (from == Route.WorkoutList || from is Route.Shoe) from else Route.Allenamenti
        if (route == Route.Shoes && from != Route.Shoes && from !is Route.Shoe) {
            shoesReturn = if (from is Route.Workout) from else Route.Allenamenti
            if (from is Route.Workout) shoeWorkoutReturn = workoutReturn
        }
        if (route is Route.Shoe && from !is Route.Shoe) {
            shoeReturn = if (from is Route.Workout || from == Route.Shoes) from else Route.Allenamenti
            if (from is Route.Workout) shoeWorkoutReturn = workoutReturn
        }
        screen = route
    }

    // Una registrazione chiesta dal widget o da un promemoria si apre nella Home
    LaunchedEffect(UnicumAppState.pendingAddMeal) {
        if (UnicumAppState.pendingAddMeal != null) screen = Route.Home
    }
    // La notifica del lunedì apre il racconto della settimana appena finita
    LaunchedEffect(UnicumAppState.pendingOpenWeek) {
        if (UnicumAppState.pendingOpenWeek) {
            UnicumAppState.pendingOpenWeek = false
            navigate(Route.WeekStory(io.github.peol13.unicum.ui.screens.lastCompletedWeek()))
        }
    }
    // Il widget del recupero apre la sezione Allenamenti
    LaunchedEffect(UnicumAppState.pendingOpenTraining) {
        if (UnicumAppState.pendingOpenTraining) {
            screen = Route.Allenamenti
            UnicumAppState.consumeOpenTraining()
        }
    }

    // Statistiche anonime: si conta il nome della schermata aperta (senza id) e si chiede il consenso una volta
    LaunchedEffect(screen) {
        UsageStats.screen(
            when (val s = screen) {
                is Route.Workout -> "Allenamento"
                is Route.Shoe -> "Scarpa"
                is Route.WeekStory -> "Settimana"
                else -> s.key
            }
        )
    }
    // Una richiesta alla volta: dopo il primo avvio e mentre c'è la finestra delle novità aspetta
    if (!UnicumAppState.onboardedThisSession && !io.github.peol13.unicum.ui.screens.whatsNewPending()) UsageStatsConsentDialog()

    // Il tasto Indietro riporta alla Home da qualsiasi schermata; dalla Home chiude l'app
    BackHandler(enabled = screen != Route.Home) {
        screen = Route.Home
    }

    when (val current = screen) {
        Route.Home -> DashboardScreen(onNavigate = ::navigate)
        Route.Diario -> DiarioScreen(onNavigate = ::navigate)
        Route.Statistiche -> StatisticheScreen(onNavigate = ::navigate)
        Route.Allenamenti -> AllenamentiScreen(onNavigate = ::navigate)
        Route.Profilo -> ProfiloScreen(onNavigate = ::navigate)
        Route.Consigli -> ConsigliScreen(onNavigate = ::navigate, onBack = { screen = Route.Home })
        is Route.Workout -> WorkoutScreen(
            externalId = current.id,
            onNavigate = ::navigate,
            onBack = { screen = workoutReturn }
        )
        Route.Shoes -> ShoesScreen(onNavigate = ::navigate, onBack = {
            if (shoesReturn is Route.Workout) workoutReturn = shoeWorkoutReturn
            screen = shoesReturn
        })
        is Route.Shoe -> ShoeScreen(
            id = current.id,
            onNavigate = ::navigate,
            onBack = {
                if (shoeReturn is Route.Workout) workoutReturn = shoeWorkoutReturn
                screen = shoeReturn
            }
        )
        Route.Notion -> TrainingPlanScreen(onNavigate = ::navigate, onBack = { screen = Route.Allenamenti })
        Route.WorkoutList -> WorkoutListScreen(onNavigate = ::navigate, onBack = { screen = Route.Allenamenti })
        Route.HomeEditor -> HomeEditorScreen(onNavigate = ::navigate, onBack = { screen = Route.Home })
        is Route.WeekStory -> WeekStoryScreen(current.monday, onClose = { screen = storyReturn })
        is Route.Detail -> DetailScreen(
            topic = current.topic,
            onNavigate = ::navigate,
            onBack = { screen = Route.Home }
        )
    }
}

/** Chiede il permesso POST_NOTIFICATIONS al primo avvio su Android 13+, se non è già concesso. Una volta sola. */
@Composable
private fun NotificationPermissionPrompt() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Concesso ora: riprogramma i promemoria, così partono già da oggi
        if (granted) MealReminders.scheduleAll(context.applicationContext)
    }
    LaunchedEffect(Unit) {
        if (!UserSettings.needsNotificationPrompt) return@LaunchedEffect
        UserSettings.markNotificationPromptDone()
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
