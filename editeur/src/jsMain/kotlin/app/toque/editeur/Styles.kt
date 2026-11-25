package app.toque.editeur

import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.media
import org.jetbrains.compose.web.css.mediaMaxWidth
import org.jetbrains.compose.web.css.px

/** Couleurs et formes de l'appli (app/…/ui/Ui.kt), en variables CSS : thème clair ou sombre. */
object Palette {
    val light = mapOf(
        "bg" to "#F5EFE6", "rail" to "#EBE2D4", "surface" to "#FFFCF8", "surface2" to "#EFE6D9", "surface3" to "#E3D7C6",
        "stroke" to "#E0D3C1", "text" to "#2B211A", "text2" to "#65574B", "text3" to "#857669",
        "accent" to "#B4532F", "accent-soft" to "#F3DED3", "on-accent" to "#FFFFFF",
        "cool" to "#2C766A", "cool-soft" to "#D9ECE7", "ok" to "#2E7D4F", "ok-soft" to "#DCEFE2",
        "warn" to "#94600F", "warn-soft" to "#F6E7C8", "danger" to "#B83A2E", "danger-soft" to "#F7DCD8",
        "shadow" to "rgba(43,33,26,0.10)",
    )
    val dark = mapOf(
        "bg" to "#13110F", "rail" to "#0D0C0A", "surface" to "#1D1A17", "surface2" to "#28241F", "surface3" to "#342E28",
        "stroke" to "#3A332C", "text" to "#F3ECE3", "text2" to "#B6AB9E", "text3" to "#8C8175",
        "accent" to "#E0915F", "accent-soft" to "#3A2A1F", "on-accent" to "#22140A",
        "cool" to "#7CC4B4", "cool-soft" to "#1E302C", "ok" to "#6FCF97", "ok-soft" to "#1C2F23",
        "warn" to "#E9B860", "warn-soft" to "#3A2E17", "danger" to "#F06A5E", "danger-soft" to "#3D1D1A",
        "shadow" to "rgba(0,0,0,0.35)",
    )

    /** Vignette sans photo : comme Ui.placeholderColors. */
    fun placeholder(seed: String, dark: Boolean): Pair<String, String> {
        val p = if (dark) listOf("#4A3424", "#3F3A26", "#2E3A2C", "#47302B", "#3E3324", "#2C3532", "#4A3B22", "#41302A")
        else listOf("#E9C9A8", "#D7B98F", "#CFD9B8", "#E8BFAE", "#D9C6A5", "#C9D5C4", "#E6CFA0", "#DDB9A4")
        val i = kotlin.math.abs(seed.hashCode()) % p.size
        return p[i] to p[(i + 3) % p.size]
    }
}

private fun v(name: String) = "var(--$name)"

object S : StyleSheet() {
    private const val SANS = "Roboto, \"Segoe UI\", system-ui, -apple-system, sans-serif"

    init {
        ":root" style { Palette.light.forEach { (k, c) -> property("--$k", c) } }
        "body.dark" style { Palette.dark.forEach { (k, c) -> property("--$k", c) } }
        "*" style { property("box-sizing", "border-box") }
        "body" style {
            property("margin", "0"); property("background", v("bg")); property("color", v("text"))
            property("font-family", SANS); property("font-size", "16px"); property("line-height", "1.4")
        }
        "input, textarea, select, button" style { property("font", "inherit"); property("color", "inherit") }
        "::placeholder" style { property("color", v("text3")) }
    }

    val shell by style {
        property("display", "flex"); property("min-height", "100vh")
    }
    val rail by style {
        property("width", "112px"); property("flex", "none"); property("background", v("rail"))
        property("display", "flex"); property("flex-direction", "column"); property("align-items", "center")
        property("padding", "18px 8px"); property("gap", "8px"); property("position", "sticky"); property("top", "0"); property("height", "100vh")
        media(mediaMaxWidth(700.px)) { self style { property("display", "none") } }
    }
    val logo by style {
        property("display", "flex"); property("flex-direction", "column"); property("align-items", "center")
        property("color", v("accent")); property("margin-bottom", "18px"); property("text-decoration", "none")
    }
    val logoName by style {
        property("font-family", "Poppins, $SANS"); property("font-weight", "700"); property("font-size", "15px"); property("color", v("text"))
        property("margin-top", "4px")
    }
    val logoSub by style {
        property("font-family", "Poppins, $SANS"); property("font-weight", "300"); property("font-size", "9px"); property("letter-spacing", "3px")
        property("color", v("accent"))
    }
    val navItem by style {
        property("display", "flex"); property("flex-direction", "column"); property("align-items", "center"); property("gap", "6px")
        property("width", "92px"); property("padding", "12px 4px"); property("border-radius", "18px"); property("color", v("text2"))
        property("font-size", "14px"); property("text-decoration", "none"); property("cursor", "pointer"); property("text-align", "center")
        self + hover style { property("background", v("surface2")) }
    }
    val navActive by style {
        property("background", v("accent-soft")); property("color", v("accent"))
    }
    val main by style {
        property("flex", "1"); property("min-width", "0"); property("padding", "24px 32px 48px")
        media(mediaMaxWidth(700.px)) { self style { property("padding", "16px 14px 40px") } }
    }
    val header by style {
        property("display", "flex"); property("align-items", "center"); property("gap", "14px"); property("margin-bottom", "20px")
        property("flex-wrap", "wrap")
    }
    val title by style {
        property("font-family", "\"DM Serif Display\", Georgia, serif"); property("font-weight", "400"); property("font-size", "34px")
        property("margin", "0"); property("line-height", "1.15"); property("flex", "1"); property("min-width", "200px")
        property("overflow", "hidden"); property("text-overflow", "ellipsis")
    }
    val subtitle by style {
        property("font-size", "13px"); property("letter-spacing", "1px"); property("text-transform", "uppercase"); property("color", v("text2"))
        property("font-weight", "600")
    }
    val sectionTitle by style {
        property("font-size", "13px"); property("letter-spacing", "1px"); property("text-transform", "uppercase"); property("color", v("accent"))
        property("font-weight", "700"); property("margin", "0 0 12px")
    }
    val card by style {
        property("background", v("surface")); property("border-radius", "22px"); property("padding", "22px")
        property("box-shadow", "0 2px 6px ${v("shadow")}"); property("margin-bottom", "18px")
    }
    val muted by style { property("color", v("text2")); property("font-size", "14px") }
    val hint by style { property("color", v("text3")); property("font-size", "13px"); property("margin", "6px 0 0") }

    // ---- boutons
    val btn by style {
        property("display", "inline-flex"); property("align-items", "center"); property("justify-content", "center"); property("gap", "8px")
        property("height", "48px"); property("padding", "0 22px"); property("border-radius", "999px"); property("border", "none")
        property("background", v("surface2")); property("color", v("text")); property("font-weight", "600"); property("font-size", "16px")
        property("cursor", "pointer"); property("white-space", "nowrap"); property("text-decoration", "none")
        self + hover style { property("filter", "brightness(0.96)") }
        self + disabled style { property("opacity", "0.5"); property("cursor", "default") }
    }
    val primary by style { property("background", v("accent")); property("color", v("on-accent")) }
    val ghost by style { property("background", "transparent"); property("box-shadow", "inset 0 0 0 1.5px ${v("stroke")}") }
    val danger by style { property("background", v("danger-soft")); property("color", v("danger")) }
    val small by style { property("height", "38px"); property("padding", "0 14px"); property("font-size", "14px") }
    val iconBtn by style {
        property("width", "40px"); property("height", "40px"); property("border-radius", "50%"); property("border", "none")
        property("background", v("surface2")); property("color", v("text2")); property("display", "inline-flex")
        property("align-items", "center"); property("justify-content", "center"); property("cursor", "pointer"); property("flex", "none")
        self + hover style { property("color", v("text")) }
        self + disabled style { property("opacity", "0.35"); property("cursor", "default") }
    }
    val back by style { property("width", "52px"); property("height", "52px") }

    // ---- champs
    val field by style { property("display", "flex"); property("flex-direction", "column"); property("gap", "6px"); property("min-width", "0") }
    val label by style { property("font-size", "13px"); property("color", v("text2")); property("font-weight", "600") }
    val input by style {
        property("background", v("surface2")); property("border", "1.5px solid transparent"); property("border-radius", "14px")
        property("padding", "11px 14px"); property("outline", "none"); property("width", "100%")
        self + focus style { property("border-color", v("accent")); property("background", v("surface")) }
    }
    val textarea by style { property("resize", "vertical"); property("min-height", "72px"); property("line-height", "1.45") }
    val mono by style { property("font-family", "ui-monospace, Menlo, Consolas, monospace"); property("font-size", "14px") }
    val grid by style {
        property("display", "grid"); property("gap", "14px"); property("grid-template-columns", "repeat(auto-fill, minmax(150px, 1fr))")
    }
    val row by style { property("display", "flex"); property("gap", "10px"); property("align-items", "center"); property("flex-wrap", "wrap") }
    val grow by style { property("flex", "1") }
    val search by style {
        property("background", v("surface")); property("border-radius", "999px"); property("height", "60px"); property("padding", "0 24px")
        property("display", "flex"); property("align-items", "center"); property("gap", "12px"); property("box-shadow", "0 2px 6px ${v("shadow")}")
        property("color", v("text2")); property("margin-bottom", "22px")
    }
    val searchInput by style {
        property("border", "none"); property("outline", "none"); property("background", "transparent"); property("flex", "1"); property("font-size", "18px")
    }

    // ---- puces et choix
    val chip by style {
        property("display", "inline-flex"); property("align-items", "center"); property("gap", "6px"); property("padding", "5px 12px")
        property("border-radius", "999px"); property("background", v("surface2")); property("color", v("text2")); property("font-size", "13px")
        property("font-weight", "600"); property("white-space", "nowrap")
    }
    val chipWarn by style { property("background", v("warn-soft")); property("color", v("warn")) }
    val chipCool by style { property("background", v("cool-soft")); property("color", v("cool")) }
    val chipOk by style { property("background", v("ok-soft")); property("color", v("ok")) }
    val segmented by style {
        property("display", "inline-flex"); property("background", v("surface2")); property("border-radius", "999px"); property("padding", "4px")
        property("gap", "2px"); property("flex-wrap", "wrap")
    }
    val segment by style {
        property("border", "none"); property("background", "transparent"); property("border-radius", "999px"); property("padding", "8px 16px")
        property("font-weight", "600"); property("font-size", "14px"); property("color", v("text2")); property("cursor", "pointer")
    }
    val segmentOn by style { property("background", v("surface")); property("color", v("text")); property("box-shadow", "0 1px 3px ${v("shadow")}") }
    val toggle by style {
        property("border", "1.5px solid ${v("stroke")}"); property("background", "transparent"); property("border-radius", "999px")
        property("padding", "8px 14px"); property("font-weight", "600"); property("font-size", "14px"); property("color", v("text2")); property("cursor", "pointer")
    }
    val toggleOn by style { property("background", v("text")); property("border-color", v("text")); property("color", v("bg")) }

    // ---- liste des recettes
    val tiles by style {
        property("display", "grid"); property("gap", "18px"); property("grid-template-columns", "repeat(auto-fill, minmax(240px, 1fr))")
    }
    val tile by style {
        property("background", v("surface")); property("border-radius", "18px"); property("overflow", "hidden"); property("cursor", "pointer")
        property("box-shadow", "0 2px 6px ${v("shadow")}"); property("display", "flex"); property("flex-direction", "column")
        property("text-decoration", "none"); property("color", "inherit")
        self + hover style { property("transform", "translateY(-2px)") }
    }
    val tileImage by style {
        property("height", "150px"); property("background-size", "cover"); property("background-position", "center")
        property("display", "flex"); property("align-items", "center"); property("justify-content", "center")
        property("font-family", "\"DM Serif Display\", Georgia, serif"); property("font-size", "46px"); property("color", "rgba(70,45,25,0.35)")
    }
    val tileBody by style { property("padding", "14px 16px 16px"); property("display", "flex"); property("flex-direction", "column"); property("gap", "6px") }
    val tileTitle by style { property("font-weight", "700"); property("font-size", "18px"); property("line-height", "1.25") }
    val emptyState by style { property("text-align", "center"); property("padding", "60px 20px"); property("color", v("text2")) }

    // ---- éditeur
    val layout by style {
        property("display", "grid"); property("gap", "20px"); property("grid-template-columns", "minmax(0, 1fr) 380px"); property("align-items", "start")
        media(mediaMaxWidth(1100.px)) { self style { property("grid-template-columns", "minmax(0, 1fr)") } }
    }
    val side by style {
        property("position", "sticky"); property("top", "16px")
        media(mediaMaxWidth(1100.px)) { self style { property("position", "static") } }
    }
    val photo by style {
        property("height", "200px"); property("border-radius", "16px"); property("background-size", "cover"); property("background-position", "center")
        property("display", "flex"); property("align-items", "center"); property("justify-content", "center"); property("margin-bottom", "12px")
        property("font-family", "\"DM Serif Display\", Georgia, serif"); property("font-size", "60px"); property("color", "rgba(70,45,25,0.35)")
    }
    val section by style {
        property("border", "1.5px dashed ${v("stroke")}"); property("border-radius", "18px"); property("padding", "14px"); property("margin-bottom", "14px")
    }
    val step by style {
        property("background", v("bg")); property("border-radius", "16px"); property("padding", "14px"); property("margin", "10px 0")
        property("display", "flex"); property("flex-direction", "column"); property("gap", "10px")
    }
    val stepNumber by style {
        property("width", "30px"); property("height", "30px"); property("border-radius", "50%"); property("background", v("accent"))
        property("color", v("on-accent")); property("display", "inline-flex"); property("align-items", "center"); property("justify-content", "center")
        property("font-weight", "700"); property("font-size", "14px"); property("flex", "none")
    }
    val setting by style {
        property("background", v("surface")); property("border-radius", "14px"); property("padding", "14px")
        property("display", "flex"); property("flex-direction", "column"); property("gap", "12px")
    }
    val numberBox by style { property("width", "86px") }
    val textLink by style {
        property("background", "none"); property("border", "none"); property("color", v("accent")); property("font-weight", "600")
        property("cursor", "pointer"); property("padding", "4px 0"); property("font-size", "14px")
    }
    val previewStep by style {
        property("display", "flex"); property("gap", "10px"); property("padding", "10px 0"); property("border-bottom", "1px solid ${v("stroke")}")
        self + lastChild style { property("border-bottom", "none") }
    }
    val previewNumber by style {
        property("width", "24px"); property("height", "24px"); property("border-radius", "50%"); property("background", v("surface2"))
        property("color", v("text2")); property("display", "inline-flex"); property("align-items", "center"); property("justify-content", "center")
        property("font-size", "12px"); property("font-weight", "700"); property("flex", "none")
    }
    val error by style { property("color", v("danger")); property("font-weight", "600") }
    val toast by style {
        property("position", "fixed"); property("bottom", "24px"); property("left", "50%"); property("transform", "translateX(-50%)")
        property("background", v("text")); property("color", v("bg")); property("padding", "12px 22px"); property("border-radius", "999px")
        property("font-weight", "600"); property("box-shadow", "0 4px 14px ${v("shadow")}"); property("z-index", "10")
    }
    val toastError by style { property("background", v("danger")); property("color", "#fff") }
    val login by style {
        property("max-width", "420px"); property("margin", "12vh auto 0"); property("text-align", "center")
    }
    val codeInput by style {
        property("font-size", "30px"); property("letter-spacing", "10px"); property("text-align", "center")
    }
}
