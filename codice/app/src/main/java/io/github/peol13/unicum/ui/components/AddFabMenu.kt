package io.github.peol13.unicum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.UnicumAppState
import io.github.peol13.unicum.data.Activity
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumSurface
import io.github.peol13.unicum.ui.theme.UnicumType

/** Pulsante + che apre un piccolo menu: aggiungi un pasto o un allenamento. */
@Composable
fun AddFabMenu(onAddMeal: () -> Unit, onAddActivity: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (open) {
            AddMenuItem(Icons.Rounded.Restaurant, "Aggiungi pasto") {
                open = false
                onAddMeal()
            }
            AddMenuItem(Icons.Rounded.FitnessCenter, "Aggiungi allenamento") {
                open = false
                onAddActivity()
            }
        }
        UnicumFab(onClick = { open = !open }, expanded = open)
    }
}

@Composable
private fun AddMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .shadow(4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(UnicumSurface)
            .clickable { onClick() }
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(10.dp))
        Text(label, color = UnicumDark, style = UnicumType.Body, fontWeight = FontWeight.Medium)
    }
}

/** Quale dialog di inserimento è aperto (pasto o allenamento) per il giorno selezionato. */
class AddEntryState {
    var mealOpen by mutableStateOf(false)
        private set
    /** Tipo di pasto proposto; null = lo sceglie il dialog in base all'ora. */
    var mealType by mutableStateOf<String?>(null)
        private set
    var activityOpen by mutableStateOf(false)
        private set

    fun addMeal(type: String? = null) {
        mealType = type
        mealOpen = true
    }

    fun addActivity() {
        activityOpen = true
    }

    fun close() {
        mealOpen = false
        activityOpen = false
    }
}

@Composable
fun rememberAddEntryState() = remember { AddEntryState() }

/** I dialog per registrare pasti e allenamenti, condivisi da Home, Diario e pagine di dettaglio. */
@Composable
fun AddEntryDialogs(state: AddEntryState) {
    val context = LocalContext.current
    if (state.mealOpen) {
        AddMealDialog(
            initialMealType = state.mealType,
            onDismiss = state::close,
            onConfirmMeals = {
                UnicumAppState.addMeals(context, it)
                state.close()
            }
        )
    }
    if (state.activityOpen) {
        AddActivityDialog(
            onDismiss = state::close,
            onConfirm = { type, minutes, kcal, start ->
                UnicumAppState.addActivity(context, Activity(type, minutes, kcal, startMinute = start))
                state.close()
            }
        )
    }
}
