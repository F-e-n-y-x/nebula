package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.f_e_n_y_x.nebula.ui.components.NebulaIconButton
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/*
 * Favourites UI pieces, kept apart from the home layouts so a new home (nebula/launcher) can reuse
 * them: the star badge on a pinned card, the long-press card menu and the star toggle button.
 */

private val FavouriteGold = Color(0xFFFFC857)

fun favouriteLabel(favourite: Boolean) = if (favourite) "Remove from favourites" else "Add to favourites"

/** Small gold star in a dark disc, for the corner of a pinned game's card. */
@Composable
fun FavouriteBadge(modifier: Modifier = Modifier) {
    val s = Nebula.scale
    Box(modifier.padding(s.dp(6)).size(s.dp(24)).background(Color(0xCC0A0A0B), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Star, "Favourite", tint = FavouriteGold, modifier = Modifier.size(s.dp(16)))
    }
}

/** Star toggle for hero and details headers; reachable with the D-pad like the buttons beside it. */
@Composable
fun FavouriteButton(favourite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    NebulaIconButton(
        if (favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, favouriteLabel(favourite), onToggle, modifier,
        tint = if (favourite) FavouriteGold else NebulaColors.text,
    )
}

/** What a long press (or held D-pad centre) on a game card opens. Anchor it inside the card's Box. */
@Composable
fun GameCardMenu(expanded: Boolean, favourite: Boolean, onDismiss: () -> Unit, onToggleFavourite: () -> Unit, onDetails: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = NebulaColors.raised) {
        DropdownMenuItem(
            text = { Text(favouriteLabel(favourite), style = Nebula.type.body, color = NebulaColors.text) },
            leadingIcon = { Icon(if (favourite) Icons.Rounded.StarBorder else Icons.Rounded.Star, null, tint = FavouriteGold) },
            onClick = { onDismiss(); onToggleFavourite() },
        )
        DropdownMenuItem(
            text = { Text("Details", style = Nebula.type.body, color = NebulaColors.text) },
            leadingIcon = { Icon(Icons.Outlined.Info, null, tint = NebulaColors.textSecondary) },
            onClick = { onDismiss(); onDetails() },
        )
    }
}
