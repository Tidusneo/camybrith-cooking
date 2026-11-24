package app.toque.editeur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/** Icônes au trait, dessinées comme celles de l'appli (Icons.kt). */
object Icon {
    private fun svg(body: String, size: Int) =
        """<svg width="$size" height="$size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">$body</svg>"""

    const val POT = """<path d="M4 10h16v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z"/><path d="M2 10h20"/><path d="M9 6c0-1 1-1 1-2M14 6c0-1 1-1 1-2"/>"""
    const val BACK = """<path d="M15 5l-7 7 7 7"/>"""
    const val PLUS = """<path d="M12 5v14M5 12h14"/>"""
    const val SEARCH = """<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>"""
    const val UP = """<path d="M6 15l6-6 6 6"/>"""
    const val DOWN = """<path d="M6 9l6 6 6-6"/>"""
    const val COPY = """<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>"""
    const val TRASH = """<path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"/>"""
    const val CHECK = """<path d="M5 12l5 5L20 7"/>"""
    const val BOOK = """<path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z"/><path d="M4 19V5"/>"""
    const val CAMERA = """<path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/>"""
    const val WARN = """<path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18v.01"/>"""
    const val HAND = """<path d="M8 13V5a1.5 1.5 0 0 1 3 0v6M11 11V4a1.5 1.5 0 0 1 3 0v7M14 11V5.5a1.5 1.5 0 0 1 3 0V14a7 7 0 0 1-7 7 6 6 0 0 1-5-3l-2.5-4a1.5 1.5 0 0 1 2.5-1.6L8 15"/>"""
    const val LOGOUT = """<path d="M15 4h4v16h-4M10 16l-4-4 4-4M6 12h10"/>"""

    @Composable
    fun Show(body: String, size: Int = 22) {
        Span({
            style { property("display", "inline-flex"); property("line-height", "0") }
            ref { el -> el.innerHTML = svg(body, size); onDispose { } }
        })
    }
}

@Composable
fun PillButton(
    text: String,
    icon: String? = null,
    kind: String? = null,
    small: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button({
        classes(S.btn)
        when (kind) {
            "primary" -> classes(S.primary)
            "ghost" -> classes(S.ghost)
            "danger" -> classes(S.danger)
        }
        if (small) classes(S.small)
        if (!enabled) disabled()
        onClick { onClick() }
    }) {
        if (icon != null) Icon.Show(icon, if (small) 18 else 20)
        Text(text)
    }
}

@Composable
fun IconButton(icon: String, title: String, enabled: Boolean = true, big: Boolean = false, onClick: () -> Unit) {
    Button({
        classes(S.iconBtn)
        if (big) classes(S.back)
        attr("title", title)
        attr("aria-label", title)
        if (!enabled) disabled()
        onClick { it.stopPropagation(); onClick() }
    }) { Icon.Show(icon, if (big) 26 else 18) }
}

@Composable
fun Chip(text: String, kind: String? = null, icon: String? = null) {
    Span({
        classes(S.chip)
        when (kind) {
            "warn" -> classes(S.chipWarn)
            "cool" -> classes(S.chipCool)
            "ok" -> classes(S.chipOk)
        }
    }) {
        if (icon != null) Icon.Show(icon, 14)
        Text(text)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Div({ classes(S.segmented) }) {
        options.forEach { (value, label) ->
            Button({
                classes(S.segment)
                if (value == selected) classes(S.segmentOn)
                onClick { onSelect(value) }
            }) { Text(label) }
        }
    }
}

@Composable
fun Toggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Button({
        classes(S.toggle)
        if (on) classes(S.toggleOn)
        attr("aria-pressed", on.toString())
        onClick { onChange(!on) }
    }) { Text(text) }
}

@Composable
fun Field(label: String, content: @Composable () -> Unit) {
    Label(attrs = { classes(S.field) }) {
        Span({ classes(S.label) }) { Text(label) }
        content()
    }
}

/**
 * Champ numérique qui garde ce qui est tapé (« 0. », vide…) et ne remonte une valeur que si elle se lit.
 * Une valeur changée ailleurs (autre mode, recette rechargée) remplace le texte.
 */
@Composable
fun NumberField(value: Double?, placeholder: String = "", decimals: Boolean = false, wide: Boolean = false, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(show(value)) }
    LaunchedEffect(value) {
        if (read(text, decimals) != value) text = show(value)
    }
    Input(InputType.Text) {
        classes(S.input)
        if (!wide) classes(S.numberBox)
        attr("inputmode", if (decimals) "decimal" else "numeric")
        if (placeholder.isNotEmpty()) attr("placeholder", placeholder)
        value(text)
        onInput { e ->
            text = e.value
            val n = read(e.value, decimals)
            if (n != null || e.value.isBlank()) onChange(n)
        }
    }
}

private fun show(v: Double?): String = when {
    v == null -> ""
    v == kotlin.math.floor(v) -> v.toLong().toString()
    else -> v.toString()
}

private fun read(s: String, decimals: Boolean): Double? {
    val t = s.trim().replace(',', '.')
    if (t.isEmpty()) return null
    val n = t.toDoubleOrNull() ?: return null
    if (n < 0 || (!decimals && n != kotlin.math.floor(n))) return null
    return n
}

fun HTMLElement.scrollIntoViewSoft() {
    asDynamic().scrollIntoView(js("({behavior: 'smooth', block: 'nearest'})"))
}
