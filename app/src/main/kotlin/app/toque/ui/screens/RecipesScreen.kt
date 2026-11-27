package app.toque.ui.screens

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.Prefs
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.RecipeFilter
import app.toque.core.recipes.SearchIndex
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.RecipeCard
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap
import java.util.concurrent.Executors

/**
 * Catalogue des recettes Lidl : recherche instantanée tolérante (accents, pluriels, fautes de
 * frappe), filtres, et recherche par ingrédients (séparés par des virgules).
 */
class RecipesScreen(
    a: MainActivity,
    private var focusSearch: Boolean = false,
    favorites: Boolean = false,
    /** Onglet des recettes Thermomix converties ; sinon, catalogue Lidl seul. */
    private val thermomix: Boolean = false,
) : Screen(a) {
    override val title = when {
        thermomix -> tr("Recettes Thermomix")
        favorites -> tr("Favoris")
        else -> tr("Recettes")
    }
    override val section = if (thermomix) Section.THERMOMIX else Section.RECIPES

    private val base = if (thermomix) RecipeFilter(idPrefix = Cookidoo.ID_PREFIX) else RecipeFilter(excludePrefix = Cookidoo.ID_PREFIX)
    private var filter = base.copy(favoritesOnly = favorites)
    private var query = ""
    private var results: List<SearchIndex.Hit> = emptyList()
    private var generation = 0
    private lateinit var input: EditText
    private lateinit var chips: LinearLayout
    private lateinit var grid: GridView
    private lateinit var count: TextView
    private lateinit var empty: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var clear: FrameLayout
    private val adapter = Adapter()
    private var unsub: (() -> Unit)? = null
    private val runSearch = Runnable { search() }

    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(24), Ui.u(2), Ui.u(22), 0)

        val bar = Ui.horizontal(c)
        val field = Ui.horizontal(c)
        field.setPadding(Ui.u(20), 0, Ui.u(8), 0)
        field.background = Ui.round(Ui.SURFACE, 29f)
        Ui.lift(field, 2f)
        field.addView(Icon(c, Icon.Kind.SEARCH, Ui.TEXT2).apply { strokeWidth = 2.1f }, LinearLayout.LayoutParams(Ui.u(23), Ui.u(23)).apply { rightMargin = Ui.u(12) })
        input = EditText(c).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(18.5f))
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT3)
            hint = tr("Recette, ingrédient… (ex. : poulet, curry)")
            background = null
            setPadding(0, 0, 0, 0)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c1: Int, a1: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c1: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    clear.visibility = if (query.isEmpty()) View.INVISIBLE else View.VISIBLE
                    view.removeCallbacks(runSearch)
                    view.postDelayed(runSearch, 120)
                }
            })
            setOnEditorActionListener { _, _, _ -> a.hideKeyboard(); true }
        }
        field.addView(input, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        clear = FrameLayout(c)
        clear.background = Ui.pressable(Ui.round(Ui.SURFACE2, 20f), 20f)
        clear.addView(Icon(c, Icon.Kind.CLOSE, Ui.TEXT2).apply { strokeWidth = 2.3f }, Ui.flp(Ui.u(17), Ui.u(17), Gravity.CENTER))
        clear.isClickable = true
        clear.contentDescription = tr("Effacer")
        clear.tap { input.setText("") }
        clear.visibility = View.INVISIBLE
        field.addView(clear, LinearLayout.LayoutParams(Ui.u(40), Ui.u(40)))
        bar.addView(field, LinearLayout.LayoutParams(0, Ui.u(58), 1f))
        count = Ui.text(c, "", 15.5f, Ui.TEXT2)
        bar.addView(count, Ui.lp(Ui.WRAP, Ui.WRAP, l = 16))
        bar.addView(Ui.button(c, tr("Importer"), Ui.Style.SECONDARY, Icon.Kind.LINK, height = 48, textSize = 13f) { a.push(ImportScreen(a)) },
            Ui.lp(Ui.WRAP, Ui.WRAP, l = 12))
        root.addView(bar)

        val scroller = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        chips = Ui.horizontal(c)
        chips.setPadding(0, Ui.u(2), 0, Ui.u(2))
        scroller.addView(chips)
        root.addView(scroller, Ui.lp(t = 12, b = 12))

        val body = FrameLayout(c)
        grid = GridView(c).apply {
            numColumns = 4
            horizontalSpacing = Ui.u(14)
            verticalSpacing = Ui.u(14)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = android.graphics.drawable.ColorDrawable(0)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(Ui.u(2), Ui.u(2), Ui.u(2), Ui.u(20))
            clipToPadding = false
            adapter = this@RecipesScreen.adapter
            setOnItemClickListener { _, _, pos, _ ->
                val hit = results.getOrNull(pos) ?: return@setOnItemClickListener
                val r = a.app.recipes.get(hit.entry.id)
                if (r != null) a.push(RecipeScreen(a, r))
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(v: AbsListView?, state: Int) {
                    if (state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) a.hideKeyboard()
                }
                override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) {}
            })
        }
        body.addView(grid, Ui.flp())
        empty = Ui.vertical(c, Gravity.CENTER)
        empty.addView(Ui.badge(c, Icon.Kind.BOOK, Ui.TEXT3, Ui.SURFACE2, 64))
        emptyText = Ui.text(c, "", 18f, Ui.TEXT2).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.22f) }
        empty.addView(emptyText, Ui.lp(Ui.u(600), Ui.WRAP, t = 16))
        body.addView(empty, Ui.flp())
        root.addView(body, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        return root
    }

    /** Donne la main à la recherche (depuis l'accueil). */
    fun focusSearch() {
        focusSearch = true
        if (view.isAttachedToWindow) showKeyboard()
    }

    private fun showKeyboard() {
        focusSearch = false
        view.postDelayed({
            input.requestFocus()
            (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun buildChips() {
        chips.removeAllViews()
        fun chip(label: String, selected: Boolean, f: RecipeFilter, icon: Icon.Kind? = null) {
            chips.addView(Ui.toggleChip(c, label, selected, icon) { filter = f; buildChips(); search() }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 9))
        }
        chip(tr("Toutes"), filter == base, base)
        chip(tr("Favoris"), filter.favoritesOnly, base.copy(favoritesOnly = true), Icon.Kind.STAR)
        chip(tr("30 min max"), filter.maxMinutes == 30, base.copy(maxMinutes = 30), Icon.Kind.CLOCK)
        for (cat in a.app.recipes.index.categories(base).take(30)) chip(cat, filter.category == cat, base.copy(category = cat))
    }

    private fun search() {
        val gen = ++generation
        val q = query
        val f = filter
        val index = a.app.recipes.index
        searchExec.execute {
            // Toutes les recettes, pas les 500 premières : la grille ne crée que les cases visibles.
            val hits = if (q.contains(',')) index.withIngredients(q.split(','), f) else index.search(q, f, limit = Int.MAX_VALUE)
            view.post {
                if (gen != generation) return@post
                results = hits
                adapter.notifyDataSetChanged()
                refreshEmpty()
            }
        }
    }

    private fun refreshEmpty() {
        val repo = a.app.recipes
        val none = repo.loaded && repo.count == 0
        count.text = if (results.isEmpty()) "" else if (results.size == 1) tr("1 recette") else tr("%s recettes", String.format(I18n.locale, "%,d", results.size))
        empty.visibility = if (results.isEmpty() && repo.loaded) View.VISIBLE else View.GONE
        emptyText.text = when {
            none && a.prefs.mode == Prefs.Mode.HARDWARE ->
                tr("Les recettes Lidl apparaîtront après la première synchronisation.\nRéglages › Recettes Lidl › Synchroniser.")
            none -> tr("Les recettes Lidl apparaîtront après la première synchronisation, une fois Camybrith Cooking installé sur le robot.")
            filter.favoritesOnly && query.isBlank() -> tr("Aucun favori pour l'instant.\nTouchez l'étoile d'une recette pour l'ajouter.")
            else -> tr("Aucune recette ne correspond.\nAstuce : séparez plusieurs ingrédients par des virgules.")
        }
    }

    override fun onShow() {
        buildChips()
        search()
        unsub = a.app.recipes.onChange { buildChips(); search() }
        if (focusSearch) showKeyboard()
    }

    override fun onHide() {
        unsub?.invoke()
    }

    override fun onKnob(delta: Int): Boolean {
        grid.smoothScrollBy(delta * Ui.u(240), 150)
        return true
    }

    private companion object {
        /** Un seul fil de recherche partagé par tous les écrans Recettes. */
        val searchExec = Executors.newSingleThreadExecutor()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = results.size
        override fun getItem(position: Int) = results[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val card = (convertView as? RecipeCard) ?: RecipeCard(c, 104).apply {
                layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT)
            }
            val e = results[position].entry
            card.bind(e, a.app.recipes.isFavorite(e.id), a.app.recipes.imageFile(e.image))
            return card
        }
    }
}
