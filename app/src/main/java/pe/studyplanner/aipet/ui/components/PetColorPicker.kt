package pe.studyplanner.aipet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.studyplanner.aipet.domain.pet.PetOutfits
import pe.studyplanner.aipet.domain.profile.AvatarStyle
import pe.studyplanner.aipet.platform.pet3d.PetStyleStore

/** Qué se está pintando en la hoja de colores. */
private sealed interface Target {
    val label: String
    data class Zone(val id: String, override val label: String) : Target
    data class Accessory(val id: String, override val label: String) : Target
}

/**
 * Personalizar la mascota: cada prenda tiene un botón con su color (al tocarlo se abren todos los colores), la
 * mochila se puede quitar y hay accesorios 3D para poner. Todo se guarda al tocar y la mascota lo muestra al momento.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PetColorPicker(style: AvatarStyle, enabled: Boolean = true) {
    val context = LocalContext.current
    PetStyleStore.init(context)
    val saved by PetStyleStore.colors.collectAsStateWithLifecycle()
    val zones = PetOutfits.zones(style)
    if (zones.isEmpty()) {
        Text("${style.label} todavía no tiene modelo 3D para vestirlo.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val colors = PetOutfits.colorsFor(style, saved)
    val hidden = PetOutfits.hiddenFor(style, saved)
    val worn = PetOutfits.accessoriesFor(style, saved)
    var editing by remember { mutableStateOf<Target?>(null) }
    val changed = colors.isNotEmpty() || hidden.isNotEmpty() || worn.isNotEmpty()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ropa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (changed) TextButton(onClick = { PetStyleStore.resetColors(context, style) }, enabled = enabled) { Text("Restablecer") }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Column {
                zones.forEachIndexed { i, zone ->
                    val hex = colors[zone.id]
                    val isHidden = zone.id in hidden
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(enabled = enabled && !isHidden, role = Role.Button) { editing = Target.Zone(zone.id, zone.label) }
                            .semantics { contentDescription = "${zone.label}: ${PetOutfits.swatchName(hex)}. Cambiar color" }
                            .testTag("colors_${zone.id}")
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(zone.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val part = PetOutfits.parts(style).firstOrNull { it.id == zone.id }
                        if (part != null) {
                            Switch(checked = !isHidden, enabled = enabled,
                                onCheckedChange = { PetStyleStore.setHidden(context, style, part.id, !it) },
                                modifier = Modifier.semantics { contentDescription = if (isHidden) "Poner ${part.label}" else "Quitar ${part.label}" }
                                    .testTag("part_${part.id}"))
                            Spacer(Modifier.width(12.dp))
                        }
                        ColorDot(hex, size = 34, dimmed = isHidden)
                    }
                    if (i < zones.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }

        val accs = PetOutfits.accessories(style)
        if (accs.isNotEmpty()) {
            Text("Accesorios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                accs.forEach { acc ->
                    val on = acc.id in worn
                    val color = worn[acc.id]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.size(width = 92.dp, height = 88.dp)
                                .toggleable(value = on, enabled = enabled, role = Role.Checkbox) {
                                    PetStyleStore.setAccessory(context, style, acc.id, it, color)
                                }
                                .semantics { contentDescription = acc.label }
                                .testTag("acc_${acc.id}"),
                        ) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                AccessoryIcon(acc.id, accessoryTint(acc.id, color), Modifier.size(40.dp))
                                androidx.compose.foundation.text.BasicText(acc.label, maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
                                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp))
                            }
                        }
                        if (on) {
                            Box(Modifier.padding(top = 6.dp).clip(CircleShape)
                                .clickable(enabled = enabled, role = Role.Button) { editing = Target.Accessory(acc.id, acc.label) }
                                .semantics { contentDescription = "Color de ${acc.label}" }) { ColorDot(color, size = 28) }
                        }
                    }
                }
            }
        }
    }

    val target = editing
    if (target != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val chosen = when (target) {
            is Target.Zone -> colors[target.id]
            is Target.Accessory -> worn[target.id]
        }
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(target.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(PetOutfits.swatchName(chosen), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 5) {
                    PetOutfits.PALETTE.forEach { sw ->
                        val sel = sw.hex == chosen
                        Box(
                            Modifier.size(54.dp).clip(CircleShape)
                                .selectable(selected = sel, role = Role.RadioButton) {
                                    when (target) {
                                        is Target.Zone -> PetStyleStore.setColor(context, style, target.id, sw.hex)
                                        is Target.Accessory -> PetStyleStore.setAccessory(context, style, target.id, true, sw.hex)
                                    }
                                    editing = null
                                }
                                .semantics { contentDescription = "${target.label}: ${sw.name}" }
                                .testTag("swatch_${sw.name}"),
                            contentAlignment = Alignment.Center,
                        ) { ColorDot(sw.hex, size = 46, selected = sel) }
                    }
                }
            }
        }
    }
}

/** Círculo de color. Sin color: mitad clara y mitad oscura (el color original del modelo). */
@Composable
private fun ColorDot(hex: String?, size: Int, selected: Boolean = false, dimmed: Boolean = false) {
    val ring = MaterialTheme.colorScheme.primary
    val c = hex?.let { Color(android.graphics.Color.parseColor(it)) }
    Box(Modifier.size(size.dp).then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier).padding(if (selected) 5.dp else 0.dp)) {
        Canvas(Modifier.size((size - if (selected) 10 else 0).dp)) {
            val a = if (dimmed) 0.35f else 1f
            if (c != null) drawCircle(c.copy(alpha = a))
            else drawCircle(Brush.sweepGradient(listOf(Color(0xFFFF9500), Color(0xFFFF2D55), Color(0xFF5856D6), Color(0xFF34C759), Color(0xFFFF9500))), alpha = a)
            drawCircle(Color.Black.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
        }
    }
}

private fun accessoryTint(id: String, hex: String?): Color = hex?.let { Color(android.graphics.Color.parseColor(it)) } ?: when (id) {
    "lentes" -> Color(0xFF1C1C1E)
    "corona" -> Color(0xFFFFB81F)
    else -> Color(0xFFFF2D55)
}

/** Dibujo simple de cada accesorio para su botón. */
@Composable
private fun AccessoryIcon(id: String, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (id) {
            "lentes" -> {
                val r = w * 0.2f; val sw = w * 0.07f
                drawCircle(tint, r, Offset(w * 0.28f, h * 0.55f), style = Stroke(sw))
                drawCircle(tint, r, Offset(w * 0.72f, h * 0.55f), style = Stroke(sw))
                drawLine(tint, Offset(w * 0.46f, h * 0.5f), Offset(w * 0.54f, h * 0.5f), sw)
            }
            "corona" -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.78f); lineTo(w * 0.12f, h * 0.32f); lineTo(w * 0.32f, h * 0.55f); lineTo(w * 0.5f, h * 0.22f)
                    lineTo(w * 0.68f, h * 0.55f); lineTo(w * 0.88f, h * 0.32f); lineTo(w * 0.88f, h * 0.78f); close()
                }
                drawPath(p, tint)
            }
            else -> {
                val l = Path().apply { moveTo(w * 0.5f, h * 0.5f); lineTo(w * 0.1f, h * 0.25f); lineTo(w * 0.1f, h * 0.75f); close() }
                val r = Path().apply { moveTo(w * 0.5f, h * 0.5f); lineTo(w * 0.9f, h * 0.25f); lineTo(w * 0.9f, h * 0.75f); close() }
                drawPath(l, tint); drawPath(r, tint)
                drawCircle(tint, w * 0.12f, Offset(w * 0.5f, h * 0.5f))
            }
        }
    }
}

