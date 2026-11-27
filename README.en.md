# Camybrith Cooking

🇫🇷 **[Version française](README.md)** · 🇪🇸 **[Versión en español](README.es.md)**

**A new, fast and modern interface for the Lidl Monsieur Cuisine Smart kitchen robot (SKMS 1200 A1).**
Free, open source (MIT license), no account, no subscription, and it works without Internet.

Camybrith Cooking replaces the stock app on the robot's tablet. It drives the motor, heating and
scale itself, and keeps the official Monsieur Cuisine recipes.

> **Independent project**, not affiliated with Lidl, SilverCrest or Hoyer. The robot has to be
> "rooted", which probably voids the warranty and carries a small risk of making it unusable if a
> step goes wrong. The guide is written to avoid that, with a full backup before touching anything,
> but you do everything **at your own risk**. Tested on a Smart SKMS 1200 A1 (Android 8.1, system
> version V01.00.83 from June 2022) in November 2026.

<p align="center"><img src="docs/images/animation_demarrage.gif" width="560" alt="Camybrith Cooking boot animation"></p>

<p align="center"><img src="docs/images/pesee-195g.gif" width="560" alt="Weighing: the bowl fills up on screen"><br><sub>Weighing for real: the bowl fills up on screen while you pour.</sub></p>

| | |
|---|---|
| ![Home](docs/images/accueil.png) | ![Recipes](docs/images/recettes.png) |
| **Home**: the recipe in progress, manual cooking, scale, top rated | **Recipes**: about 1,700 Monsieur Cuisine recipes, filters and favorites |
| ![Recipe](docs/images/recette.png) | ![Guided weighing](docs/images/pesee-guidee.png) |
| **A recipe**: ingredients, steps, reviews | **Guided cooking**: each ingredient is weighed in the bowl, step by step |
| ![Manual cooking](docs/images/cuisson-manuelle.png) | ![Scale](docs/images/balance.png) |
| **Manual cooking**: time, temperature and speed with the knob | **Scale**: the bowl fills up on screen until the target weight |
| ![Search](docs/images/recherche.png) | ![Settings](docs/images/reglages.png) |
| **Instant search**, even with typos | **Settings**: light or dark theme, language, sounds |

<sub>Screenshots taken on the robot. Recipe photos belong to Monsieur Cuisine (Lidl). The screenshots show the app in French; it is also available in English and Spanish.</sub>

## Install

**Start here: [docs/GUIDE.en.md](docs/GUIDE.en.md)**, the step by step guide, written for someone
who has never done this kind of thing.

You need:
* a **Windows PC** (for the tool that writes to the robot's memory);
* a **USB-A male to USB-A male** cable (USB 2.0) and a small Torx screwdriver;
* your home Wi-Fi;
* two to three hours, without rushing.

Once the robot is rooted, everything else (install, updates, sounds, logo) is done **over Wi-Fi**,
from Windows (with Ubuntu, see the guide), a Mac or Linux.

> The app itself is available in **French, English and Spanish** (*Settings › Langue · Language*). The
> install scripts print their messages in French; the guide tells you what to expect at each step.

## Got a Monsieur Cuisine Connect?

Camybrith Cooking can also run on the **Connect** (SKMC 1200), as an **experimental** version: not
tried on a real Connect yet. See [docs/CONNECT.en.md](docs/CONNECT.en.md).

## Let Claude guide you

Prefer some company? The project ships a **ready-to-use assistant** for Claude: it knows the whole
guide, moves forward one step at a time, checks file sizes and screen photos with you before each
delicate step, and stops if anything looks wrong.

1. Download `camybrith-assistant-claude.zip` from the
   [Releases](https://github.com/scaule/camybrith-cooking/releases/latest) page.
2. On claude.ai or in the Claude app: make sure code execution and file creation are enabled in
   **Settings > Capabilities**, then go to **Customize > Skills**, click **+**, **Create skill**,
   **Upload a skill**, and pick the ZIP.
3. Start a new conversation and write for example: "I want to install Camybrith Cooking on my
   Monsieur Cuisine Smart, guide me."

With **Claude Code** it is even simpler: open it in the project folder, it finds its instructions
(`CLAUDE.md`) by itself and can run the scripts for you, asking before every action on the robot.

Without a Claude account, you can also give the link to [docs/GUIDE.en.md](docs/GUIDE.en.md) to
another assistant and ask it to guide you step by step.

## What you get

* **An interface designed for the robot's screen and knob**, smooth on its small tablet.
* **The Monsieur Cuisine recipes for the Smart**, downloaded from Lidl's public website, nightly
  or on demand, with their settings, photos, ratings and reviews.
* **Search that works**: ignores accents, plurals and typos. Search by ingredients, filters,
  favorites.
* **Guided cooking**: each step starts with one tap, the next one shows up by itself, weighing is
  built in (knob or +10 g and +50 g buttons to reach the target weight).
* **Your own recipes** written for another robot (JSON file, see
  [docs/RECIPES.en.md](docs/RECIPES.en.md)): speeds, temperatures, Varoma and dough mode are
  converted for the Smart, in a separate tab.
* **Manual cooking**, steam, sauté, knead, Turbo, scale.
* **Lid open = pause**, resumed with one tap once it is closed again.
* **Offline**: nothing depends on a server.
* **Everything can be customized**: sounds, the boot animation with your own logo, even the name
  and icon (see [personnalisation/README.en.md](personnalisation/README.en.md)).

## Home Assistant

Camybrith can send the robot's state to Home Assistant over MQTT (status, program, temperature,
weight, time remaining, lid, alert), with automatic sensor discovery. Read only: nothing can start
the robot remotely. See [docs/HOME-ASSISTANT.en.md](docs/HOME-ASSISTANT.en.md).

## Cooking safety

All the stock app's limits are kept (speed 3 max when heating and in reverse, maximum durations,
Turbo conditions). The lid, errors and freshness of the base's data are checked **before** every
start; a base that stops answering stops everything; an alert stays on screen until it is read;
nothing restarts on its own after a pause.

Motor control runs in **a separate app** (the "motor pilot"): if the interface crashes, cooking is
not cut off. On top of that, the robot's board stops the motor by itself about 4 seconds after the
last command it received.

## Repository layout

```
docs/GUIDE.en.md     step by step install guide (start here)
docs/RECIPES.en.md   importing your own recipes
docs/SMART.md        technical notes on the Smart (protocol, programs, tests; French)
outils/              scripts run from your computer: install, sounds, animation, nav bar…
modules/             Magisk modules: ADB over Wi-Fi, boot animation, rescue (back to the Lidl app)
personnalisation/    your sounds, your logo, and the tools to make them
core/                pure Kotlin logic, tested without Android (protocol, safety, recipes)
commun/              code shared by the interface and the pilot
pilote/              "motor pilot" app (app.toque.pilote)
app/                 interface app (app.toque)
moteur-secours/      engine of the rescue module's "camybrith" command (code from core/backup)
banc/fausse-lidl/    fake Lidl app for the emulator test bench (never published)
specs/               feature specifications (Spec Kit) and project constitution (.specify/)
```

## Building the APKs yourself

The APKs are built and published automatically by GitHub (**Actions** tab, **Releases** page). To
build them on your computer: JDK 17, Android SDK, then

```
./gradlew :core:test :pilote:assembleRelease :app:assembleRelease
./gradlew -Pprudent=true :app:assembleRelease     # "prudente" (safe) version
```

The **fast** version ("rapide") is compiled ahead of time by Android before its first launch
(that is what `outils/installer.sh` does): on this robot, Android's just-in-time compiler makes the
interface crash. The **safe** version ("prudente") is never compiled: slower, but immune to that
problem.

Gradle must see JDK 17: on a Mac with Homebrew (`brew install openjdk@17`),
`export JAVA_HOME=/opt/homebrew/opt/openjdk@17`. The rescue module is built with
`bash outils/fabriquer_secours.sh` (output: `modules/camybrith_secours.zip`).

**Test bench without the robot** (back to the Lidl app, power cuts, restarts): you also need the
emulator and the Android 8.1 SDK image (`sdkmanager "emulator"
"system-images;android-27;default;arm64-v8a"`, or `x86_64` on an Intel PC; the script downloads it
if missing), then `bash outils/banc-essai.sh`. It never touches the robot.

## Adding a language

The app is written in French, with English and Spanish as well. Each language is one file in
[`app/src/main/assets/lang/`](app/src/main/assets/lang): copy `en.json` to `de.json` for example,
change `"_name"` and translate the values (keys are the French texts; keep `%s` and `%d`).

## Credits and license

Camybrith Cooking is a Smart port of **Toque**
([Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)), written for the
Monsieur Cuisine Connect. Thanks to Wuki60 and Toque's contributors.

The rooting method comes from the community of
[EliasKotlyar/Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack)
(issue #38, especially depau and ihrapsa). Root is provided by
[Magisk](https://github.com/topjohnwu/Magisk).

MIT license: see [LICENSE](LICENSE). No Lidl code, recipe or file is included in this repository;
the only Lidl images are the recipe photos visible in the screenshots.
