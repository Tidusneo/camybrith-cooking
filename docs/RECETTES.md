# Importer tes propres recettes

🇬🇧 **[English version](RECIPES.en.md)**

Camybrith Cooking sait convertir pour le Smart des recettes **que tu possèdes**, écrites pour un
Thermomix : les vitesses, températures, Varoma et pétrin sont adaptés automatiquement, et chaque
réglage modifié est marqué « à vérifier ». Elles apparaissent dans un onglet à part et ne sont
jamais effacées par la synchronisation des recettes Lidl.

Le projet ne fournit aucune recette et n'explique pas comment en récupérer sur un site : utilise
uniquement des recettes que tu as le droit de copier (les tiennes, celles de tes proches…).

Les recettes peuvent s'écrire directement dans un navigateur avec l'[éditeur de recettes
web](#éditeur-de-recettes-web), ou se préparer dans un fichier `data.json` envoyé par ADB.

## Le format du fichier `data.json`

Une liste d'objets. Chaque objet a un identifiant (`id`) et une recette au format
[schema.org Recipe](https://schema.org/Recipe) dans `schema`. Les réglages du robot sont écrits
dans le texte des étapes, **entre `<nobr>` et `</nobr>`**, séparés par des barres obliques.

```json
[
  {
    "id": "soupe1",
    "title": "Soupe de carottes",
    "author": "Mamie",
    "ingredients": ["300 g de carottes", "500 g d'eau", "1 pincée de sel"],
    "tags": ["végétarien"],
    "schema": {
      "name": "Soupe de carottes",
      "recipeYield": "4 portions",
      "prepTime": "PT10M",
      "totalTime": "PT35M",
      "recipeCategory": ["Soupes"],
      "recipeInstructions": [
        {"@type": "HowToStep", "text": "Mettre les carottes dans le bol et hacher <nobr>5 sec/vitesse 5</nobr>."},
        {"@type": "HowToStep", "text": "Ajouter l'eau et cuire <nobr>20 min/100°C/vitesse 1</nobr>. Mixer <nobr>1 min/vitesse 5-10</nobr>."}
      ]
    }
  }
]
```

Champs facultatifs : `title` (sinon `schema.name`), `author`, `ingredients` (sinon
`schema.recipeIngredient`), `tags`, `rating_score`, `rating_count`. Les étapes peuvent être
regroupées en sections (`HowToSection` avec `name` et `itemListElement`).

Réglages reconnus, par exemple : `15-30 sec/vitesse 7`, `10 min/100°C/vitesse 1`,
`20 min/Varoma/vitesse 1`, `Pétrin/2 min`, `Turbo/0.5 sec/3-5 fois`. Une fourchette de temps
devient la durée la plus courte ; un réglage sans durée est laissé « à régler à la main ».

### Réglages du Monsieur Cuisine et pesées (facultatif)

Au lieu d'un réglage Thermomix dans le texte, une étape peut porter le réglage du Monsieur Cuisine
lui-même, dans un champ `machine`, ou une pesée, dans un champ `weigh`. C'est ce qu'écrit
l'éditeur web :

```json
{"@type": "HowToStep", "text": "Cuire le riz.", "machine": {"program": "manuel", "seconds": 1200, "speed": 1, "tempC": 100, "reverse": true}},
{"@type": "HowToStep", "text": "Ajouter le riz.", "weigh": {"label": "riz", "grams": 300}}
```

- `program` : `manuel`, `petrir`, `vapeur`, `rissoler`, `turbo`, et sur le Smart `mixer`,
  `smoothie`, `sous-vide`, `mijoter`, `fermenter`.
- `seconds` : une durée précise (pas de fourchette). `tempC` : 0 (sans chauffe) ou de 40 à 130 °C
  de 5 en 5 en cuisson manuelle ; les programmes du Smart ont leur propre plage (la vapeur est
  toujours à 120 °C).
- `speed` : de 1 à 10 en cuisson manuelle, 3 au plus en chauffe ou en sens inverse, 4 avec le
  fouet (`"accessory": "whisk"`), et 10 min au plus au-delà de la vitesse 6. `pulses` : impulsions
  turbo (1 à 20).
- `weigh.grams` : de 1 à 5000 g. L'appli affiche la balance à cette étape.

Un réglage que le robot refuserait est marqué « à vérifier » à l'import.

## Les photos (facultatif)

Un dossier `images/` à côté de `data.json`, avec une photo JPEG par recette nommée d'après son
identifiant : `images/soupe1.jpg`.

## Envoyer et importer

```
~/platform-tools/adb -s 192.168.1.42:5555 shell mkdir -p /sdcard/Android/data/app.toque/files/import
~/platform-tools/adb -s 192.168.1.42:5555 push data.json images /sdcard/Android/data/app.toque/files/import/
```

Puis dans l'appli : **Réglages**, **Recettes d'autres robots**, **Importer les recettes** (ou
rouvre l'appli). Pour mettre à jour, renvoie le fichier : les recettes sont remplacées, les
favoris gardés.

**Relis toujours les étapes « à vérifier » avant de les lancer** : la conversion respecte les
limites de sécurité du Smart, mais un Thermomix et un Monsieur Cuisine ne se comportent pas
exactement pareil.

## Éditeur de recettes web

L'appli peut servir un éditeur de recettes sur le réseau local, pour écrire tes recettes depuis un
ordinateur, une tablette ou un téléphone au lieu de préparer `data.json` à la main.

1. Sur le robot : **Réglages**, **Éditeur de recettes**, active **Activer l'éditeur sur le réseau
   local**. L'appli affiche l'adresse (`http://<IP du robot>:8080`) et un code d'accès à 6 chiffres.
2. Dans un navigateur sur le même réseau Wi-Fi, ouvre cette adresse et saisis le code.
3. La liste montre les recettes déjà écrites (y compris celles importées avant avec `data.json`).
   Crées-en une ou ouvre-la pour la modifier : informations, ingrédients (un par ligne), photo, et
   les étapes, groupées en parties facultatives. Pour chaque étape, choisis **Sans le robot**,
   **Avec le robot** (le programme du Monsieur Cuisine et ses options : durée précise, température
   de 40 à 130 °C de 5 en 5, vitesse de 1 à 10, sens inverse, fouet, impulsions turbo ; les valeurs
   que le robot refuserait ne sont pas proposées) ou **Peser** (ingrédient et grammes). Les étapes
   Thermomix d'un ancien `data.json` restent en **Texte libre** et sont toujours converties.
4. Le panneau **Sur le robot** montre, pendant la saisie et dans la langue de l'appli, ce que
   l'importeur fera de chaque étape, et lesquelles sont à vérifier ou se feront à la main.
5. **Enregistrer** : la recette est convertie par le même importeur que `data.json` et apparaît
   aussitôt dans **Recettes d'autres robots**. La supprimer dans l'éditeur la retire aussi de l'appli.

Les recettes sont gardées dans le stockage privé de l'appli (`files/editeur/data.json` et
`files/editeur/images/`), au même format que ci-dessus. L'éditeur ne tourne que s'il est activé et
n'est fait que pour un réseau domestique de confiance : pas de HTTPS, seulement le code d'accès
(**Nouveau code** dans les réglages le change et déconnecte les navigateurs).
