package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.container
import io.github.f_e_n_y_x.nebula.domain.HomeStyle
import io.github.f_e_n_y_x.nebula.domain.ResolveHomeLayout
import io.github.f_e_n_y_x.nebula.ui.HomeStyleViewModel
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula

/** Settings → Library: Auto, Spotlight (A) or Shelf (B). The help line says what Auto picks here. */
@Composable
internal fun HomeStyleSetting() {
    val container = LocalContext.current.container
    val vm = viewModel(key = "home-style") { HomeStyleViewModel(container) }
    val style by vm.style.collectAsStateWithLifecycle()
    val form = Nebula.form
    val auto = ResolveHomeLayout.autoStyle(form.isTv, form.isLandscape).label
    val help = when (style) {
        HomeStyle.AUTO -> "Spotlight on TV and in landscape, Shelf when held upright. Right now: $auto."
        HomeStyle.SPOTLIGHT -> "The focused game fills the screen, with your games in one row underneath."
        HomeStyle.SHELF -> "Continue playing up top, then your library as a shelf of posters."
    }
    Setting("Home style", help) {
        Segmented(HomeStyle.entries.map { it.label to it }, style) { vm.set(it) }
    }
}
