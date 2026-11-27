package app.toque.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.recipes.Cookomix
import app.toque.recipes.CookomixImport
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap

/**
 * Import Cookomix du design v2 : à gauche le lien et l'aperçu de la recette trouvée, à droite les
 * réglages convertis pour le Smart (identiques, adaptés, ou sans machine), puis l'ajout à « Mes recettes ».
 */
class ImportScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Importer une recette")
    override val section = Section.RECIPES

    private val importer by lazy { CookomixImport(a.app.recipes) }
    private lateinit var input: EditText
    private lateinit var go: View
    private lateinit var previewBox: FrameLayout
    private lateinit var rows: LinearLayout
    private lateinit var count: TextView
    private lateinit var save: View
    private var current: CookomixImport.Preview? = null
    private var busy = false

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(19), Ui.u(2), Ui.u(19), Ui.u(4))

        // Colonne gauche : lien puis aperçu
        val left = Ui.vertical(c)
        val linkCard = Ui.vertical(c)
        linkCard.setPadding(Ui.u(18), Ui.u(16), Ui.u(18), Ui.u(16))
        linkCard.background = Ui.round(Ui.SURFACE, 22f)
        linkCard.addView(Ui.text(c, tr("Lien de la recette"), 12f, Ui.TEXT, Ui.BOLD))
        val field = Ui.horizontal(c)
        field.setPadding(Ui.u(16), 0, Ui.u(5), 0)
        field.background = Ui.round(Ui.SURFACE, 22f, Ui.ACCENT, 1.6f)
        input = EditText(c).apply {
            background = null
            hint = "cookomix.com/recettes/…"
            setHintTextColor(Ui.TEXT3)
            setTextColor(Ui.TEXT)
            typeface = Ui.MEDIUM
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(12f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            maxLines = 1
            setSingleLine(true)
            setPadding(0, 0, 0, 0)
            setOnEditorActionListener { _, _, _ -> fetch(); true }
        }
        field.addView(input, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        go = Ui.button(c, tr("Lire"), Ui.Style.PRIMARY, height = 36, textSize = 12f) { fetch() }
        field.addView(go)
        linkCard.addView(field, Ui.lp(Ui.MATCH, Ui.u(45), t = 10))
        val tools = Ui.horizontal(c)
        tools.addView(Ui.button(c, tr("Coller"), Ui.Style.SECONDARY, Icon.Kind.LINK, height = 36, textSize = 12f) { paste() })
        tools.addView(Ui.text(c, tr("Copiez le lien d'une recette Cookomix, collez-le ici, puis « Lire »."), 11f, Ui.TEXT2, Ui.MEDIUM)
            .apply { setLineSpacing(0f, 1.25f) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = Ui.u(12) })
        linkCard.addView(tools, Ui.lp(t = 10))
        left.addView(linkCard)
        previewBox = FrameLayout(c)
        left.addView(previewBox, Ui.lp(Ui.MATCH, 0, 1f, t = 12))
        root.addView(left, LinearLayout.LayoutParams(Ui.u(336), Ui.MATCH).apply { rightMargin = Ui.u(12) })

        // Colonne droite : réglages convertis
        val right = Ui.vertical(c)
        right.setPadding(Ui.u(20), Ui.u(18), Ui.u(20), Ui.u(18))
        right.background = Ui.round(Ui.SURFACE, 22f)
        val head = Ui.horizontal(c, Gravity.BOTTOM)
        head.addView(Ui.text(c, tr("Réglages convertis pour votre robot"), 16f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        count = Ui.text(c, "", 11.5f, Ui.TEXT2, Ui.MEDIUM)
        head.addView(count)
        right.addView(head)
        rows = Ui.vertical(c)
        right.addView(Ui.scroll(c, rows), Ui.lp(Ui.MATCH, 0, 1f, t = 10))
        save = Ui.button(c, tr("Ajouter à mes recettes"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 52, textSize = 14.5f) { save() }
        right.addView(save, Ui.lp(Ui.MATCH, Ui.WRAP, t = 12))
        root.addView(right, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        showEmpty()
        return root
    }

    private fun paste() {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString().orEmpty().trim()
        if (t.isEmpty()) { a.toast(tr("Le presse-papiers est vide")); return }
        input.setText(t)
        fetch()
    }

    private fun fetch() {
        if (busy) return
        val link = input.text.toString().trim()
        if (link.isEmpty()) { a.toast(tr("Collez d'abord le lien d'une recette")); return }
        (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        busy = true
        current = null
        showMessage(tr("Lecture de la recette…"), Ui.TEXT2)
        importer.preview(link) { p, err ->
            busy = false
            if (p == null) showMessage(err ?: tr("Import impossible"), Ui.DANGER) else show(p)
        }
    }

    private fun showEmpty() {
        previewBox.removeAllViews()
        val empty = Ui.vertical(c, Gravity.CENTER)
        empty.background = Ui.round(Ui.SURFACE2, 22f)
        empty.addView(Icon(c, Icon.Kind.BOOK, Ui.TEXT3), LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)))
        empty.addView(Ui.text(c, tr("L'aperçu de la recette s'affichera ici"), 12f, Ui.TEXT2, Ui.MEDIUM).apply { gravity = Gravity.CENTER }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 10))
        previewBox.addView(empty, Ui.flp())
        rows.removeAllViews()
        count.text = ""
        Ui.setButtonEnabled(save, false)
    }

    private fun showMessage(msg: String, color: Int) {
        showEmpty()
        (previewBox.getChildAt(0) as LinearLayout).apply {
            removeAllViews()
            setPadding(Ui.u(20), 0, Ui.u(20), 0)
            addView(Ui.text(c, msg, 13f, color, Ui.MEDIUM).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f) })
        }
    }

    private fun show(p: CookomixImport.Preview) {
        current = p
        val r = p.recipe
        previewBox.removeAllViews()
        val card = FrameLayout(c)
        card.background = Ui.gradient(Ui.placeholderColors(r.title), 22f)
        card.clipToOutline = true
        if (p.image != null) {
            val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(img, Ui.flp())
            ImageLoader.load(img, p.image, Ui.u(420))
        }
        val body = Ui.vertical(c)
        body.setPadding(Ui.u(15), Ui.u(12), Ui.u(15), Ui.u(13))
        body.background = Ui.round(Ui.SURFACE, 16f)
        val found = Ui.horizontal(c)
        found.addView(Icon(c, Icon.Kind.CHECK, Ui.OK).apply { strokeWidth = 3f }, LinearLayout.LayoutParams(Ui.u(13), Ui.u(13)).apply { rightMargin = Ui.u(6) })
        found.addView(Ui.text(c, tr("Recette trouvée"), 10.5f, Ui.OK, Ui.BOLD))
        body.addView(found)
        body.addView(Ui.ellipsize(Ui.text(c, r.title, 15f, Ui.TEXT, Ui.BOLD).apply { setLineSpacing(0f, 1.1f) }, 2), Ui.lp(t = 4))
        val meta = listOfNotNull(
            r.servings.takeIf { it > 0 }?.let { tr("%d pers.", it) },
            r.totalMinutes.takeIf { it > 0 }?.let { Ui.minutes(it) },
            tr("%d ingrédients", r.allIngredients.size),
        ).joinToString(" · ")
        body.addView(Ui.text(c, meta, 11f, Ui.TEXT2, Ui.MEDIUM), Ui.lp(t = 3))
        card.addView(body, Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM).apply { setMargins(Ui.u(11), 0, Ui.u(11), Ui.u(11)) })
        previewBox.addView(card, Ui.flp())

        rows.removeAllViews()
        var machines = 0
        for ((i, s) in r.steps.withIndex()) {
            val m = s.machine
            val manual = s.text.contains(app.toque.core.recipes.Cookidoo.MANUAL_MARK)
            if (m == null && !manual) continue
            if (m != null) machines++
            val from = Regex("Adapté du Thermomix \\(([^)]*)\\)").find(s.text)?.groupValues?.get(1)?.trim()
            val (state, bg, fg) = when {
                manual -> Triple(tr("À la main"), Ui.SURFACE2, Ui.TEXT2)
                s.needsReview -> Triple(tr("Converti"), Ui.COOL_SOFT, Ui.COOL)
                else -> Triple(tr("Identique"), Ui.OK_SOFT, Ui.OK)
            }
            rows.addView(row(i + 1, from ?: s.text.lineSequence().first().take(70), m?.summary ?: tr("Réglage à faire soi-même"), state, bg, fg), Ui.lp(t = if (rows.childCount > 0) 7 else 0))
        }
        count.text = tr("%d réglages machine", machines)
        Ui.setButtonEnabled(save, true)
    }

    private fun row(n: Int, from: String, to: String, state: String, bg: Int, fg: Int): View {
        val r = Ui.horizontal(c)
        r.setPadding(Ui.u(12), Ui.u(9), Ui.u(10), Ui.u(9))
        r.background = Ui.round(Ui.BG, 14f)
        r.addView(Ui.text(c, n.toString(), 11.5f, Ui.TEXT2, Ui.BOLD), LinearLayout.LayoutParams(Ui.u(26), Ui.WRAP))
        r.addView(Ui.ellipsize(Ui.text(c, from, 11.5f, Ui.TEXT2, Ui.MEDIUM)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        r.addView(Icon(c, Icon.Kind.CHEVRON, Ui.TEXT3).apply { strokeWidth = 2.2f }, LinearLayout.LayoutParams(Ui.u(14), Ui.u(14)).apply { leftMargin = Ui.u(8); rightMargin = Ui.u(8) })
        r.addView(Ui.ellipsize(Ui.text(c, to, 12f, Ui.TEXT, Ui.BOLD)), LinearLayout.LayoutParams(0, Ui.WRAP, 1.2f))
        val chip = Ui.text(c, state, 10.5f, fg, Ui.BOLD).apply { gravity = Gravity.CENTER }
        chip.setPadding(0, Ui.u(4), 0, Ui.u(4))
        chip.background = Ui.round(bg, 10f)
        r.addView(chip, LinearLayout.LayoutParams(Ui.u(86), Ui.WRAP).apply { leftMargin = Ui.u(8) })
        return r
    }

    private fun save() {
        val p = current ?: return
        Ui.setButtonEnabled(save, false)
        importer.save(p) {
            a.toast(tr("« %s » ajoutée à vos recettes", p.recipe.title), Ui.OK)
            val r = a.app.recipes.get(p.recipe.id)
            a.back()
            if (r != null) a.push(RecipeScreen(a, r))
        }
    }

    override fun onShow() {
        if (::input.isInitialized && input.text.isEmpty()) {
            // Lien déjà copié : on le propose directement.
            val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString().orEmpty().trim()
            if (Cookomix.isCookomixUrl(t)) input.setText(t)
        }
    }
}
