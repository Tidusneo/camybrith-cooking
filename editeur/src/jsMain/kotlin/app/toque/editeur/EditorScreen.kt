package app.toque.editeur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Datalist
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.OptGroup
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.Blob
import org.w3c.files.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private fun <T> List<T>.with(i: Int, v: T) = toMutableList().also { it[i] = v }
private fun <T> List<T>.without(i: Int) = toMutableList().also { it.removeAt(i) }
private fun <T> List<T>.inserted(i: Int, v: T) = toMutableList().also { it.add(i, v) }
private fun <T> List<T>.moved(i: Int, d: Int): List<T> {
    val j = i + d
    if (j !in indices) return this
    return toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
}

private fun lines(s: String) = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
private fun commas(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Photo réduite à 1280 px de côté au plus, en JPEG. */
private suspend fun shrink(file: File): Blob {
    val bitmap: dynamic = window.asDynamic().createImageBitmap(file).unsafeCast<kotlin.js.Promise<dynamic>>().await()
    val w = (bitmap.width as Number).toDouble()
    val h = (bitmap.height as Number).toDouble()
    val scale = minOf(1.0, 1280.0 / maxOf(w, h))
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = (w * scale).toInt().coerceAtLeast(1)
    canvas.height = (h * scale).toInt().coerceAtLeast(1)
    canvas.getContext("2d").asDynamic().drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    return suspendCancellableCoroutine { cont ->
        canvas.toBlob({ b -> cont.resume(b ?: Blob()) }, "image/jpeg", 0.85)
    }
}

@Composable
fun EditorScreen(app: AppState, id: String?) {
    var recipe by remember { mutableStateOf<EditorRecipe?>(if (id == null) EditorRecipe(sections = listOf(EditorSection(steps = listOf(EditorStep())))) else null) }
    var ingredients by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<Blob?>(null) }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var changed by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        app.dirty = false
        if (id == null) return@LaunchedEffect
        try {
            val r = Api.recipe(id)
            recipe = r
            ingredients = r.ingredients.joinToString("\n")
            tags = r.tags.joinToString(", ")
            if (r.hasImage) imageUrl = Api.imageUrl(r.id, r.updated)
        } catch (e: Throwable) {
            loadError = e.message ?: ""
        }
    }

    if (loadError.isNotEmpty()) {
        Div({ classes(S.header) }) {
            IconButton(Icon.BACK, tr("Retour"), big = true) { app.go("#/") }
            H1({ classes(S.title) }) { Text(tr("Recette introuvable")) }
        }
        P({ classes(S.error) }) { Text(loadError) }
        return
    }
    val r = recipe ?: run {
        P({ classes(S.muted) }) { Text(tr("Chargement…")) }
        return
    }

    fun full(x: EditorRecipe) = x.copy(ingredients = lines(ingredients), tags = commas(tags))
    // Toujours à partir de l'état courant : plusieurs saisies peuvent précéder la recomposition.
    fun update(f: (EditorRecipe) -> EditorRecipe) {
        recipe = f(recipe ?: return)
        changed = true
        app.dirty = true
    }

    // Aperçu de la conversion pour le robot, recalculé un peu après la dernière frappe.
    LaunchedEffect(r, ingredients, tags) {
        delay(600)
        preview = try {
            Api.preview(full(r))
        } catch (e: Throwable) {
            Preview(error = e.message ?: "")
        }
    }

    fun save() {
        if (saving) return
        if (r.title.isBlank()) {
            app.message(tr("Donnez un titre à la recette"), true)
            return
        }
        saving = true
        app.scope.launch {
            try {
                var res = Api.save(full(r))
                val image = pendingImage
                if (image != null) {
                    Api.uploadImage(res.recipe.id, image)
                    pendingImage = null
                    res = SaveResult(Api.recipe(res.recipe.id), res.preview)
                }
                app.dirty = false
                changed = false
                app.message(tr("Recette enregistrée et envoyée à l'appli"))
                if (r.id.isEmpty()) {
                    app.go("#/r/" + encodeURIComponent(res.recipe.id))
                } else {
                    recipe = res.recipe
                    preview = res.preview
                    if (res.recipe.hasImage) imageUrl = Api.imageUrl(res.recipe.id, res.recipe.updated)
                }
            } catch (e: Throwable) {
                app.message(e.message ?: tr("Échec de l'enregistrement"), true)
            }
            saving = false
        }
    }

    Div({ classes(S.header) }) {
        IconButton(Icon.BACK, tr("Retour"), big = true) { app.go("#/") }
        Div({ classes(S.grow); style { property("min-width", "0") } }) {
            Div({ classes(S.subtitle) }) { Text(if (r.id.isEmpty()) tr("Nouvelle recette") else tr("Modifier la recette")) }
            H1({ classes(S.title) }) { Text(r.title.ifBlank { tr("Sans titre") }) }
        }
        if (changed) Chip(tr("Non enregistrée"), "warn")
        if (r.id.isNotEmpty()) PillButton(tr("Supprimer"), Icon.TRASH, "danger") {
            if (window.confirm(tr("Supprimer « %s » ? Elle disparaîtra aussi de l'appli.", r.title))) {
                app.scope.launch {
                    try {
                        Api.delete(r.id)
                        app.dirty = false
                        app.message(tr("Recette supprimée"))
                        app.go("#/")
                    } catch (e: Throwable) {
                        app.message(e.message ?: "", true)
                    }
                }
            }
        }
        PillButton(if (saving) tr("Enregistrement…") else tr("Enregistrer"), Icon.CHECK, "primary", enabled = !saving) { save() }
    }

    Div({ classes(S.layout) }) {
        Div {
            InfoCard(r, ::update, tags) { tags = it; changed = true; app.dirty = true }
            Div({ classes(S.card) }) {
                H2({ classes(S.sectionTitle) }) { Text(tr("Ingrédients")) }
                TextArea(ingredients) {
                    classes(S.input, S.textarea)
                    attr("rows", "8")
                    attr("placeholder", tr("Un ingrédient par ligne, par exemple : 300 g de carottes"))
                    onInput { ingredients = it.value; changed = true; app.dirty = true }
                }
            }
            StepsCard(app, r, lines(ingredients), ::update)
        }
        Div({ classes(S.side) }) {
            PhotoCard(app, r, imageUrl, pendingImage != null) { blob, url ->
                if (r.id.isEmpty()) {
                    pendingImage = blob
                    imageUrl = url
                    changed = true
                    app.dirty = true
                } else {
                    app.scope.launch {
                        try {
                            Api.uploadImage(r.id, blob)
                            val fresh = Api.recipe(r.id)
                            imageUrl = Api.imageUrl(fresh.id, fresh.updated)
                            recipe = recipe?.copy(hasImage = true, updated = fresh.updated)
                            app.message(tr("Photo enregistrée"))
                        } catch (e: Throwable) {
                            app.message(e.message ?: "", true)
                        }
                    }
                }
            }
            PreviewCard(preview)
        }
    }
}

@Composable
private fun InfoCard(r: EditorRecipe, update: ((EditorRecipe) -> EditorRecipe) -> Unit, tags: String, onTags: (String) -> Unit) {
    Div({ classes(S.card) }) {
        H2({ classes(S.sectionTitle) }) { Text(tr("Informations")) }
        Div({ style { property("display", "flex"); property("flex-direction", "column"); property("gap", "14px") } }) {
            Field(tr("Titre")) {
                Input(InputType.Text) {
                    classes(S.input)
                    style { property("font-size", "20px"); property("font-weight", "600") }
                    attr("placeholder", tr("Soupe de potiron"))
                    value(r.title)
                    onInput { e -> update { it.copy(title = e.value) } }
                }
            }
            Div({ classes(S.grid) }) {
                Field(tr("Auteur")) {
                    Input(InputType.Text) {
                        classes(S.input); value(r.author)
                        onInput { e -> update { it.copy(author = e.value) } }
                    }
                }
                Field(tr("Catégorie")) {
                    Input(InputType.Text) {
                        classes(S.input); value(r.category)
                        attr("placeholder", tr("Soupes"))
                        onInput { e -> update { it.copy(category = e.value) } }
                    }
                }
                Field(tr("Portions")) {
                    NumberField(r.servings.takeIf { it > 0 }?.toDouble(), wide = true) { v -> update { it.copy(servings = v?.toInt() ?: 0) } }
                }
                Field(tr("Préparation (min)")) {
                    NumberField(r.prepMinutes.takeIf { it > 0 }?.toDouble(), wide = true) { v -> update { it.copy(prepMinutes = v?.toInt() ?: 0) } }
                }
                Field(tr("Temps total (min)")) {
                    NumberField(r.totalMinutes.takeIf { it > 0 }?.toDouble(), wide = true) { v -> update { it.copy(totalMinutes = v?.toInt() ?: 0) } }
                }
            }
            TagsField(tags, onTags)
        }
    }
}

@Composable
private fun TagsField(tags: String, onChange: (String) -> Unit) {
    Field(tr("Mots-clés (séparés par des virgules)")) {
        Input(InputType.Text) {
            classes(S.input); value(tags)
            attr("placeholder", tr("végétarien, hiver"))
            onInput { onChange(it.value) }
        }
    }
}

@Composable
private fun StepsCard(app: AppState, r: EditorRecipe, ingredients: List<String>, update: ((EditorRecipe) -> EditorRecipe) -> Unit) {
    fun sections(f: (List<EditorSection>) -> List<EditorSection>) = update { it.copy(sections = f(it.sections)) }
    fun section(si: Int, f: (EditorSection) -> EditorSection) = sections { all -> if (si in all.indices) all.with(si, f(all[si])) else all }
    fun steps(si: Int, f: (List<EditorStep>) -> List<EditorStep>) = section(si) { it.copy(steps = f(it.steps)) }
    var number = 0
    Div({ classes(S.card) }) {
        H2({ classes(S.sectionTitle) }) { Text(tr("Étapes")) }
        P({ classes(S.muted) }) {
            Text(tr("Écrivez ce qu'il faut faire, puis choisissez le réglage du Monsieur Cuisine ou la pesée : l'appli les proposera à cette étape."))
        }
        r.sections.forEachIndexed { si, section ->
            val many = r.sections.size > 1
            Div({ if (many || section.name.isNotEmpty()) classes(S.section) }) {
                if (many || section.name.isNotEmpty()) {
                    Div({ classes(S.row) }) {
                        Input(InputType.Text) {
                            classes(S.input, S.grow)
                            style { property("font-weight", "700") }
                            attr("placeholder", tr("Nom de la partie (facultatif)"))
                            value(section.name)
                            onInput { e -> section(si) { it.copy(name = e.value) } }
                        }
                        IconButton(Icon.UP, tr("Monter"), si > 0) { sections { it.moved(si, -1) } }
                        IconButton(Icon.DOWN, tr("Descendre"), si < r.sections.size - 1) { sections { it.moved(si, 1) } }
                        IconButton(Icon.TRASH, tr("Supprimer la partie"), many) {
                            if (section.steps.isEmpty() || window.confirm(tr("Supprimer cette partie et ses étapes ?"))) {
                                sections { it.without(si) }
                            }
                        }
                    }
                }
                section.steps.forEachIndexed { i, step ->
                    number++
                    key(si, i) {
                        StepCard(app, number, step, ingredients, i > 0, i < section.steps.size - 1,
                            onChange = { f -> steps(si) { if (i in it.indices) it.with(i, f(it[i])) else it } },
                            onMove = { d -> steps(si) { it.moved(i, d) } },
                            onCopy = { steps(si) { if (i in it.indices) it.inserted(i + 1, it[i]) else it } },
                            onDelete = { steps(si) { if (i in it.indices) it.without(i) else it } },
                        )
                    }
                }
                Div({ classes(S.row); style { property("margin-top", "8px") } }) {
                    PillButton(tr("Ajouter une étape"), Icon.PLUS, "ghost", small = true) {
                        val last = section.steps.lastOrNull()
                        // Une nouvelle étape reprend le mode de la précédente (robot ou non), pas son texte.
                        val fresh = when (last?.kind) {
                            StepKind.MACHINE -> EditorStep(machine = last.machine)
                            StepKind.WEIGH -> EditorStep(weigh = Weighing())
                            else -> EditorStep()
                        }
                        steps(si) { it + fresh }
                    }
                }
            }
        }
        Div({ classes(S.row); style { property("margin-top", "10px") } }) {
            PillButton(tr("Ajouter une partie"), Icon.PLUS, "ghost", small = true) {
                sections { it + EditorSection(name = tr("Nouvelle partie"), steps = listOf(EditorStep())) }
            }
        }
        P({ classes(S.hint) }) {
            Text(tr("Les parties (« Pâte », « Garniture »…) sont facultatives."))
        }
    }
}

/** Ingrédient pesable d'une ligne « 300 g de riz » : nom et grammes (0 si pas en grammes). */
private fun weighable(line: String): Pair<String, Int> {
    val m = Regex("^(\\d+(?:[.,]\\d+)?)\\s*(kg|g)\\s+(?:de |d'|d’)?(.+)$", RegexOption.IGNORE_CASE).find(line.trim())
        ?: return line.trim() to 0
    val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
    val g = if (m.groupValues[2].equals("kg", true)) n * 1000 else n
    return m.groupValues[3].trim() to g.toInt()
}

private fun programLabel(p: Program): String = when (p) {
    Program.MANUAL -> tr("Cuisson manuelle")
    Program.KNEAD -> tr("Pétrir")
    Program.STEAM -> tr("Vapeur")
    Program.TOAST -> tr("Rissoler")
    Program.TURBO -> tr("Turbo")
    Program.PUREE -> tr("Mixer")
    Program.SMOOTHIE -> tr("Smoothie")
    Program.SOUS_VIDE -> tr("Sous-vide")
    Program.SLOW_COOK -> tr("Mijoter")
    Program.FERMENT -> tr("Fermenter")
}

private fun programHint(p: Program): String = when (p) {
    Program.MANUAL -> tr("Durée, température et vitesse au choix. En chauffe ou en sens inverse, vitesse 3 au plus ; avec le fouet, vitesse 4 au plus.")
    Program.KNEAD -> tr("Pâtes à pain et à pizza : alterne les sens à vitesse 4, sans chauffe. 2 min au plus.")
    Program.STEAM -> tr("Cuisson à la vapeur à 120 °C dans le panier ou le plateau, sans tourner la lame.")
    Program.TOAST -> tr("Faire revenir : remue un peu en sens inverse toutes les 30 s. 14 min au plus.")
    Program.TURBO -> tr("Impulsions à pleine vitesse, sans chauffe ni fouet, contenu à moins de 60 °C.")
    Program.PUREE -> tr("Purées, soupes mixées, compotes. Vitesse 8, sans chauffe.")
    Program.SMOOTHIE -> tr("Fruits et glace pilée. Vitesse 10, sans chauffe.")
    Program.SOUS_VIDE -> tr("Bain d'eau à température précise. Le minuteur part une fois la température atteinte.")
    Program.SLOW_COOK -> tr("Cuisson lente. Remue 30 s toutes les 2 min 30 pendant 10 min, puis sans remuer.")
    Program.FERMENT -> tr("Yaourts, pâtes levées. Remue 5 min au début, puis maintient la température.")
}

/** Résumé du réglage, comme l'appli l'affiche. */
private fun summary(m: Machine): String {
    if (m.program == Program.TURBO) return tr("Turbo") + " × " + m.pulses
    val parts = ArrayList<String>()
    if (m.program != Program.MANUAL) parts += programLabel(m.program)
    parts += Rules.duration(m.seconds)
    if (m.tempC > 0) parts += "${m.tempC} °C"
    if (m.program == Program.MANUAL) {
        parts += tr("vitesse %d", m.speed)
        if (m.reverse) parts += tr("inverse")
        if (m.whisk) parts += tr("fouet")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun StepCard(
    app: AppState,
    number: Int,
    step: EditorStep,
    ingredients: List<String>,
    canUp: Boolean,
    canDown: Boolean,
    onChange: ((EditorStep) -> EditorStep) -> Unit,
    onMove: (Int) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val kind = step.kind
    Div({ classes(S.step) }) {
        Div({ classes(S.row) }) {
            Span({ classes(S.stepNumber) }) { Text(number.toString()) }
            val kinds = listOf(StepKind.TEXT to tr("Sans le robot"), StepKind.MACHINE to tr("Avec le robot"), StepKind.WEIGH to tr("Peser"))
            Segmented(if (kind == StepKind.RAW) kinds + (StepKind.RAW to tr("Texte libre")) else kinds, kind) { k ->
                if (k == kind) return@Segmented
                // Un texte libre garde ses mots ; ses réglages Thermomix sont remplacés par le formulaire.
                val text = if (kind == StepKind.RAW) Cleanup.withoutSettings(step.text) else step.text
                if (kind == StepKind.RAW && !window.confirm(tr("Les réglages Thermomix de ce texte seront retirés : choisissez ensuite ceux du Monsieur Cuisine. Continuer ?"))) return@Segmented
                onChange {
                    when (k) {
                        StepKind.TEXT -> EditorStep(text)
                        StepKind.MACHINE -> EditorStep(text, machine = Rules.defaults(Program.MANUAL))
                        StepKind.WEIGH -> EditorStep(text, weigh = Weighing())
                        StepKind.RAW -> it
                    }
                }
            }
            Div({ classes(S.grow) })
            IconButton(Icon.UP, tr("Monter"), canUp) { onMove(-1) }
            IconButton(Icon.DOWN, tr("Descendre"), canDown) { onMove(1) }
            IconButton(Icon.COPY, tr("Dupliquer")) { onCopy() }
            IconButton(Icon.TRASH, tr("Supprimer l'étape")) { onDelete() }
        }
        TextArea(step.text) {
            classes(S.input, S.textarea)
            if (kind == StepKind.RAW) classes(S.mono)
            attr("rows", if (kind == StepKind.WEIGH) "2" else "3")
            attr(
                "placeholder",
                when (kind) {
                    StepKind.WEIGH -> tr("Ajouter le riz dans le bol (facultatif)")
                    else -> tr("Mettre les carottes dans le bol et mixer.")
                },
            )
            onInput { e -> onChange { it.copy(text = e.value) } }
        }
        if (kind == StepKind.RAW) {
            P({ classes(S.hint) }) {
                Text(tr("Étape d'un fichier Thermomix : l'appli convertit ses réglages entre <nobr> et </nobr>. Choisissez « Avec le robot » pour les remplacer par ceux du Monsieur Cuisine."))
            }
        }
        step.machine?.takeIf { kind == StepKind.MACHINE }?.let { m ->
            MachineEditor(m) { f -> onChange { st -> st.copy(machine = st.machine?.let { Rules.normalize(f(it)) }) } }
        }
        step.weigh?.takeIf { kind == StepKind.WEIGH }?.let { w ->
            WeighEditor(w, ingredients) { f -> onChange { st -> st.copy(weigh = st.weigh?.let(f)) } }
        }
    }
}

/** Retire les réglages Thermomix d'un texte libre. */
private object Cleanup {
    fun withoutSettings(text: String): String =
        EditorStep.TAG.replace(text, "").replace(Regex("\\s+([.,;:!?])"), "$1").replace(Regex("\\s+"), " ").trim()
}

@Composable
private fun Choice(value: String, onPick: (String) -> Unit, content: @Composable () -> Unit) {
    Select({
        classes(S.input)
        style { property("width", "auto") }
        onChange { e -> e.value?.let(onPick) }
    }) { content() }
}

@Composable
private fun MachineEditor(m: Machine, edit: ((Machine) -> Machine) -> Unit) {
    val spec = Rules.spec(m.program)
    Div({ classes(S.setting) }) {
        Div({ classes(S.row) }) {
            Span({ classes(S.label) }) { Text(tr("Programme")) }
            Choice(m.program.key, { key ->
                val p = Program.of(key) ?: return@Choice
                if (p != m.program) edit { Rules.defaults(p) }
            }) {
                OptGroup(tr("Programmes")) {
                    listOf(Program.MANUAL, Program.KNEAD, Program.STEAM, Program.TOAST, Program.TURBO).forEach { p ->
                        Option(p.key, { if (p == m.program) selected() }) { Text(programLabel(p)) }
                    }
                }
                OptGroup(tr("Programmes du Smart")) {
                    listOf(Program.PUREE, Program.SMOOTHIE, Program.SOUS_VIDE, Program.SLOW_COOK, Program.FERMENT).forEach { p ->
                        Option(p.key, { if (p == m.program) selected() }) { Text(programLabel(p)) }
                    }
                }
            }
        }
        P({ classes(S.hint) }) { Text(programHint(m.program)) }

        if (m.program == Program.TURBO) {
            Div({ classes(S.row) }) {
                Span({ classes(S.label) }) { Text(tr("Impulsions")) }
                Choice(m.pulses.toString(), { v -> edit { it.copy(pulses = v.toIntOrNull() ?: 1) } }) {
                    (1..Rules.MAX_PULSES).forEach { n -> Option(n.toString(), { if (n == m.pulses) selected() }) { Text(n.toString()) } }
                }
            }
        } else {
            DurationField(m, spec) { s -> edit { it.copy(seconds = s) } }
        }

        when {
            m.program == Program.MANUAL -> Div({ classes(S.row) }) {
                Span({ classes(S.label) }) { Text(tr("Température")) }
                Choice(m.tempC.toString(), { v -> edit { it.copy(tempC = v.toIntOrNull() ?: 0) } }) {
                    Option("0", { if (m.tempC == 0) selected() }) { Text(tr("Sans chauffe")) }
                    Rules.TEMPERATURES.forEach { t -> Option(t.toString(), { if (m.tempC == t) selected() }) { Text("$t °C") } }
                }
            }
            Rules.fixedTemperature(m.program) > 0 -> Div({ classes(S.row) }) {
                Span({ classes(S.label) }) { Text(tr("Température")) }
                Chip("${Rules.fixedTemperature(m.program)} °C")
            }
            spec.temperatures.isNotEmpty() -> Div({ classes(S.row) }) {
                Span({ classes(S.label) }) { Text(tr("Température")) }
                Choice(m.tempC.toString(), { v -> edit { it.copy(tempC = v.toIntOrNull() ?: spec.defaultC) } }) {
                    spec.temperatures.forEach { t -> Option(t.toString(), { if (m.tempC == t) selected() }) { Text("$t °C") } }
                }
            }
        }

        if (m.program == Program.MANUAL) {
            val max = Rules.maxSpeed(m)
            Div({ classes(S.row) }) {
                Span({ classes(S.label) }) { Text(tr("Vitesse")) }
                Choice(m.speed.toString(), { v -> edit { it.copy(speed = v.toIntOrNull() ?: 1) } }) {
                    (1..Rules.MAX_SPEED).forEach { v ->
                        Option(v.toString(), {
                            if (v == m.speed) selected()
                            if (v > max) disabled()
                        }) { Text(v.toString()) }
                    }
                }
                Toggle(tr("Sens inverse"), m.reverse) { on -> edit { it.copy(reverse = on) } }
                Toggle(tr("Fouet"), m.whisk) { on -> edit { it.copy(whisk = on) } }
            }
            val why = listOfNotNull(
                if (m.tempC > 0) tr("en chauffe") else null,
                if (m.reverse) tr("en sens inverse") else null,
                if (m.whisk) tr("avec le fouet") else null,
            )
            if (max < Rules.MAX_SPEED) P({ classes(S.hint) }) { Text(tr("Vitesse %d au plus (%s).", max, why.joinToString(", "))) }
        }

        Div({ classes(S.row) }) {
            Chip(summary(m), "cool")
            if (!Rules.durationOk(m)) Chip(durationRule(m), "warn", Icon.WARN)
        }
    }
}

private fun durationRule(m: Machine): String {
    val spec = Rules.spec(m.program)
    val range = tr("Durée entre %s et %s", Rules.duration(Rules.minSeconds(m)), Rules.duration(Rules.maxSeconds(m)))
    return if (spec.stepS > 1 && spec.stepS < 60) range + ", " + tr("par pas de %s", Rules.duration(spec.stepS)) else range
}

/** Durée en minutes et secondes (minutes seules pour les programmes longs). */
@Composable
private fun DurationField(m: Machine, spec: Rules.Spec, onChange: (Int) -> Unit) {
    val withSeconds = spec.stepS < 60
    val min = m.seconds / 60
    val sec = m.seconds % 60
    Div({ classes(S.row) }) {
        Span({ classes(S.label) }) { Text(tr("Durée")) }
        NumberField(min.toDouble(), "0") { v -> onChange((v?.toInt() ?: 0) * 60 + if (withSeconds) sec else 0) }
        Span({ classes(S.muted) }) { Text("min") }
        if (withSeconds) {
            NumberField(sec.toDouble(), "0") { v -> onChange(min * 60 + (v?.toInt() ?: 0).coerceIn(0, 59)) }
            Span({ classes(S.muted) }) { Text("s") }
        }
        Span({ classes(S.hint) }) { Text(durationRule(m)) }
    }
}

@Composable
private fun WeighEditor(w: Weighing, ingredients: List<String>, edit: ((Weighing) -> Weighing) -> Unit) {
    val known = ingredients.map { weighable(it) }
    Div({ classes(S.setting) }) {
        Div({ classes(S.row) }) {
            Span({ classes(S.label) }) { Text(tr("Ingrédient")) }
            Input(InputType.Text) {
                classes(S.input, S.grow)
                attr("list", "weigh-ingredients")
                attr("placeholder", tr("riz"))
                value(w.label)
                onInput { e ->
                    val label = e.value
                    // Ingrédient de la liste en grammes : son poids est repris.
                    val grams = known.firstOrNull { it.first.equals(label.trim(), true) }?.second ?: 0
                    edit { if (grams > 0 && it.grams == 0) it.copy(label = label, grams = grams) else it.copy(label = label) }
                }
            }
            Datalist({ id("weigh-ingredients") }) {
                known.map { it.first }.distinct().forEach { Option(it) }
            }
        }
        Div({ classes(S.row) }) {
            Span({ classes(S.label) }) { Text(tr("Poids")) }
            NumberField(w.grams.takeIf { it > 0 }?.toDouble(), "0") { v -> edit { it.copy(grams = v?.toInt() ?: 0) } }
            Span({ classes(S.muted) }) { Text("g") }
        }
        Div({ classes(S.row) }) {
            if (w.grams in 1..Rules.MAX_GRAMS) Chip(tr("Peser %d g", w.grams) + if (w.label.isBlank()) "" else " · " + w.label.trim(), "cool")
            else Chip(tr("Poids entre 1 et %d g", Rules.MAX_GRAMS), "warn", Icon.WARN)
        }
        P({ classes(S.hint) }) { Text(tr("L'appli affiche la balance à cette étape et indique quand le poids est atteint.")) }
    }
}

@Composable
private fun PhotoCard(app: AppState, r: EditorRecipe, url: String?, pending: Boolean, onPicked: (Blob, String) -> Unit) {
    Div({ classes(S.card) }) {
        H2({ classes(S.sectionTitle) }) { Text(tr("Photo")) }
        Div({
            classes(S.photo)
            style {
                if (url != null) property("background-image", "url(\"$url\")")
                else property("background-image", placeholderStyle(r.title, app.dark))
            }
        }) { if (url == null) Text(initials(r.title.ifBlank { "?" })) }
        if (pending) P({ classes(S.hint) }) { Text(tr("La photo sera envoyée à l'enregistrement.")) }
        Input(InputType.File) {
            attr("accept", "image/*")
            attr("id", "photo-input")
            style { property("display", "none") }
            onChange { e ->
                val input = e.target as HTMLInputElement
                val file = input.files?.item(0) ?: return@onChange
                input.value = ""
                app.scope.launch {
                    try {
                        val blob = shrink(file)
                        if (blob.size.toDouble() > 8 * 1024 * 1024) {
                            app.message(tr("Photo JPEG de 8 Mo au plus"), true)
                            return@launch
                        }
                        onPicked(blob, js("URL").createObjectURL(blob) as String)
                    } catch (_: Throwable) {
                        app.message(tr("Image illisible"), true)
                    }
                }
            }
        }
        PillButton(if (url == null) tr("Ajouter une photo") else tr("Changer la photo"), Icon.CAMERA, "ghost", small = true) {
            (document.getElementById("photo-input") as? HTMLInputElement)?.click()
        }
    }
}

@Composable
private fun PreviewCard(preview: Preview?) {
    Div({ classes(S.card) }) {
        H2({ classes(S.sectionTitle) }) { Text(tr("Sur le robot")) }
        if (preview == null) {
            P({ classes(S.muted) }) { Text(tr("Chargement…")) }
            return@Div
        }
        if (preview.error.isNotEmpty()) {
            P({ classes(S.error) }) { Text(preview.error) }
            return@Div
        }
        if (preview.steps.isEmpty()) {
            P({ classes(S.muted) }) { Text(tr("Ajoutez des étapes pour voir ce que fera le robot.")) }
            return@Div
        }
        val review = preview.steps.count { it.review || it.manual || it.problems.isNotEmpty() }
        P({ classes(S.muted) }) {
            Text(
                when (review) {
                    0 -> tr("Tous les réglages sont valides sur le Monsieur Cuisine.")
                    1 -> tr("1 étape à vérifier : son réglage n'est pas accepté ou se fera à la main.")
                    else -> tr("%d étapes à vérifier : leur réglage n'est pas accepté ou se fera à la main.", review)
                },
            )
        }
        preview.steps.forEachIndexed { i, s ->
            Div({ classes(S.previewStep) }) {
                Span({ classes(S.previewNumber) }) { Text((i + 1).toString()) }
                Div({ style { property("display", "flex"); property("flex-direction", "column"); property("gap", "6px"); property("min-width", "0") } }) {
                    Span({ style { property("font-size", "14px") } }) { Text(s.text) }
                    Div({ classes(S.row) }) {
                        if (s.machine.isNotEmpty()) Chip(s.machine, "cool")
                        if (s.weigh.isNotEmpty()) Chip(s.weigh, "cool")
                        if (s.manual) Chip(tr("À régler à la main"), "warn", Icon.HAND)
                        else if (s.review && s.problems.isEmpty()) Chip(tr("Réglage à vérifier"), "warn", Icon.WARN)
                    }
                    s.problems.forEach { P({ classes(S.error) }) { Text(it) } }
                }
            }
        }
        if (preview.notes.isNotEmpty()) P({ classes(S.hint) }) { Text(preview.notes) }
    }
}
