package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * A PC text field took focus while the on-screen controls are up. Rather than cover them with a
 * keyboard, this small chip at the top offers one: tap it to type, or the cross to dismiss.
 */
@Composable
fun TypeChip(kind: TextFieldKeyboard, password: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val s = Nebula.scale
    val label = if (password) "Type password" else "Type on PC"
    Row(
        modifier.systemBarsPadding().padding(top = s.dp(12))
            .clip(RoundedCornerShape(50))
            .background(Color(0xE60A0A0B))
            .border(s.dp(1), NebulaColors.accentText.copy(alpha = 0.5f), RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = "$label with the ${kind.label.lowercase()}" }
                .padding(start = s.dp(14), end = s.dp(8), top = s.dp(8), bottom = s.dp(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.dp(8)),
        ) {
            Icon(if (password) Icons.Outlined.Lock else Icons.Outlined.Keyboard, contentDescription = null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(18)))
            Text(label, style = Nebula.type.label, color = NebulaColors.text)
        }
        Box(
            Modifier.padding(end = s.dp(6)).size(s.dp(30)).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "Dismiss" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = NebulaColors.textSecondary, modifier = Modifier.size(s.dp(16)))
        }
    }
}
