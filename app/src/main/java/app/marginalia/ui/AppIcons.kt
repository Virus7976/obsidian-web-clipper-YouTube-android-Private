package app.marginalia.ui

import androidx.compose.runtime.Composable
import app.obsi.core.ui.AppIcon
import app.marginalia.R

/** The same glass icon in five colours. Each is an activity-alias in the manifest, named Icon plus its id. */
val AppIcons = listOf(
    AppIcon("Amethyst", "Amethyst", R.drawable.ic_obsi_glass_preview),
    AppIcon("Ruby", "Ruby", R.drawable.ic_obsi_ruby_preview),
    AppIcon("Sapphire", "Sapphire", R.drawable.ic_obsi_sapphire_preview),
    AppIcon("Emerald", "Emerald", R.drawable.ic_obsi_emerald_preview),
    AppIcon("Amber", "Amber", R.drawable.ic_obsi_amber_preview),
)

/** The picker itself is shared by every Obsi app, in obsi-core. "Classic" is an old choice, kept so it can be turned off. */
@Composable
fun AppIconPicker() = app.obsi.core.ui.AppIconPicker(AppIcons, hidden = listOf("Classic"))
