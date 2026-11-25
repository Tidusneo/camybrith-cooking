# Importing your own recipes

🇫🇷 **[Version française](RECETTES.md)**

Camybrith Cooking can convert for the Smart recipes **that you own**, written for a Thermomix:
speeds, temperatures, Varoma and dough mode are adapted automatically, and every changed setting
is flagged "to check". They show up in a separate tab and are never erased by the Lidl recipe sync.

The project ships no recipes and does not explain how to get them from any website: only use
recipes you are allowed to copy (your own, your family's…).

Recipes can be written directly in a browser with the [web recipe editor](#web-recipe-editor),
or prepared as a `data.json` file and sent with ADB.

## The `data.json` file format

A list of objects. Each object has an identifier (`id`) and a recipe in
[schema.org Recipe](https://schema.org/Recipe) format under `schema`. Robot settings are written in
the step text, **between `<nobr>` and `</nobr>`**, separated by slashes. The settings syntax is the
French one (`vitesse` = speed).

```json
[
  {
    "id": "soup1",
    "title": "Carrot soup",
    "author": "Grandma",
    "ingredients": ["300 g carrots", "500 g water", "1 pinch of salt"],
    "tags": ["vegetarian"],
    "schema": {
      "name": "Carrot soup",
      "recipeYield": "4 servings",
      "prepTime": "PT10M",
      "totalTime": "PT35M",
      "recipeCategory": ["Soups"],
      "recipeInstructions": [
        {"@type": "HowToStep", "text": "Put the carrots in the bowl and chop <nobr>5 sec/vitesse 5</nobr>."},
        {"@type": "HowToStep", "text": "Add the water and cook <nobr>20 min/100°C/vitesse 1</nobr>. Blend <nobr>1 min/vitesse 5-10</nobr>."}
      ]
    }
  }
]
```

Optional fields: `title` (otherwise `schema.name`), `author`, `ingredients` (otherwise
`schema.recipeIngredient`), `tags`, `rating_score`, `rating_count`. Steps can be grouped into
sections (`HowToSection` with `name` and `itemListElement`).

Recognized settings, for example: `15-30 sec/vitesse 7`, `10 min/100°C/vitesse 1`,
`20 min/Varoma/vitesse 1`, `Pétrin/2 min` (dough mode), `Turbo/0.5 sec/3-5 fois` (times). A time
range becomes the shortest duration; a setting without a duration is left "to set by hand".

### Monsieur Cuisine settings and weighing (optional)

Instead of a Thermomix setting in the text, a step can carry the Monsieur Cuisine setting itself,
in a `machine` field, or a weighing, in a `weigh` field. This is what the web editor writes:

```json
{"@type": "HowToStep", "text": "Cook the rice.", "machine": {"program": "manuel", "seconds": 1200, "speed": 1, "tempC": 100, "reverse": true}},
{"@type": "HowToStep", "text": "Add the rice.", "weigh": {"label": "rice", "grams": 300}}
```

- `program`: `manuel`, `petrir`, `vapeur`, `rissoler`, `turbo`, and on the Smart `mixer`,
  `smoothie`, `sous-vide`, `mijoter`, `fermenter`.
- `seconds`: one exact duration (no range). `tempC`: 0 (no heating) or 40 to 130 °C in steps of 5
  for manual cooking; the Smart programs have their own range (steam is always 120 °C).
- `speed`: 1 to 10 in manual cooking, 3 at most when heating or in reverse, 4 with the whisk
  (`"accessory": "whisk"`), and 10 min at most above speed 6. `pulses`: turbo pulses (1 to 20).
- `weigh.grams`: 1 to 5000 g. The app shows the scale at that step.

A setting the robot would refuse is marked "to check" at import.

## Photos (optional)

An `images/` folder next to `data.json`, with one JPEG photo per recipe named after its
identifier: `images/soup1.jpg`.

## Sending and importing

```
~/platform-tools/adb -s 192.168.1.42:5555 shell mkdir -p /sdcard/Android/data/app.toque/files/import
~/platform-tools/adb -s 192.168.1.42:5555 push data.json images /sdcard/Android/data/app.toque/files/import/
```

Then in the app: **Settings**, **Recipes from other machines**, **Import recipes** (or reopen the
app). To update, send the file again: recipes are replaced, favorites kept.

**Always read the "to check" steps before starting them**: the conversion respects the Smart's
safety limits, but a Thermomix and a Monsieur Cuisine do not behave exactly the same.

## Web recipe editor

The app can serve a recipe editor on the local network, to write recipes from a computer, tablet or
phone instead of preparing `data.json` by hand.

1. On the robot: **Settings**, **Recipe editor**, enable **Enable the editor on the local network**.
   The app shows a QR code, the address (`http://<robot IP>:8080`) and an 8‑character access key
   (for example `K7M4-Q9TX`).
2. Scan the QR code with your phone: the page opens already signed in. Otherwise, open the address
   in a browser on the same Wi-Fi network and enter the key (dashes, spaces and case don't matter).
3. The list shows the recipes already written (including those imported earlier with `data.json`).
   Create one or open one to edit it: details, ingredients (one per line), photo, and the steps,
   grouped in optional parts. For each step, choose **Without the robot**, **With the robot** (the
   Monsieur Cuisine program and its options: exact duration, temperature 40–130 °C in steps of 5,
   speed 1–10, reverse, whisk, turbo pulses; values the robot would refuse cannot be picked) or
   **Weigh** (ingredient and grams). Thermomix steps from an older `data.json` stay as **Free
   text** and are still converted by the importer.
   **Paste a recipe**: paste the text of a recipe found elsewhere (title, ingredients, steps). The
   robot splits it and spots settings written the Thermomix way ("5 sec/vit. 5",
   "20 min/100°C/sens inverse/vit. mijotage", "Varoma", "Pétrin/3 min", "Turbo/0.5 sec/3 fois";
   French wording for now). The result opens as a new recipe, to review before saving. Only paste
   recipes you have the right to use: the editor never fetches a recipe from the internet.
4. The **On the robot** panel shows, while you type and in the app's language, what the importer
   will make of each step, and which ones need checking or will be done manually.
5. **Save**: the recipe is converted by the same importer as `data.json` and immediately appears in
   **Recipes from other machines**. Deleting it in the editor also removes it from the app.

The recipes are kept in the app's private storage (`files/editeur/data.json` and
`files/editeur/images/`), in the same format as above. The editor only runs while it is enabled and
is only meant for a trusted home network: there is no HTTPS, only the access key (**New key** in the
settings changes it and disconnects the browsers; after 10 wrong keys access is blocked for a
minute; a browser stays signed in for 30 days at most).
