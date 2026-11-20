package io.github.peol13.unicum.ui.components

import io.github.peol13.unicum.ui.Route

import io.github.peol13.unicum.ui.theme.UnicumSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.peol13.unicum.ui.theme.UnicumAccent
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import io.github.peol13.unicum.ui.theme.UnicumType

private data class NavItem(val route: Route.Tab, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Route.Home, Icons.Rounded.Home),
    NavItem(Route.Allenamenti, Icons.AutoMirrored.Rounded.DirectionsRun),
    NavItem(Route.Statistiche, Icons.Rounded.BarChart),
    NavItem(Route.Diario, Icons.AutoMirrored.Rounded.MenuBook),
    NavItem(Route.Profilo, Icons.Rounded.Person)
)

@Composable
fun BottomNavigationBar(
    selectedItem: Route,
    onItemSelected: (Route) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(UnicumSurface)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(UnicumAccent.copy(alpha = .35f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val selected = selectedItem == item.route
                val tint = if (selected) UnicumPrimary else UnicumDark.copy(alpha = .5f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(item.route) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, item.route.label, tint = tint, modifier = Modifier.size(26.dp))
                    Text(
                        item.route.label, maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                        color = tint,
                        style = UnicumType.Note,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
