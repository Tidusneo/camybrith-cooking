# Customizing Camybrith Cooking

🇫🇷 **[Version française](LISEZMOI.md)**

Everything can be changed without breaking anything, and everything goes back to default with one
command.

## 1. Sounds (the easiest)

Drop your files into `personnalisation/sons`, then run `bash outils/sons.sh envoyer` (send). No
rebuild, no risk. Each file must have exactly one of these names, as `.wav`, `.ogg` or `.mp3`:

| Name | When it plays |
|---|---|
| `demarrage` | when the app opens, once per robot power-on |
| `debut` | when a program starts |
| `fin` | when a program ends |
| `recette` | when a recipe is finished |
| `alerte` | on a problem that must be acknowledged |
| `pause` | when the robot pauses by itself (lid open, bowl removed) |
| `pesee` | when the requested weight is reached on the scale |
| `trop` | when the requested weight is exceeded |
| `appui` | on every button press (keep it very short) |
| `compte` | optional, no default sound: end-of-program countdown. It starts when its own length is left (a 9 s sound starts 9 s before the end) so it ends exactly with the program. Not for turbo, nor for a program shorter than the sound |

Example: `fin.mp3` replaces the end sound. Missing names keep the sound shipped with the app. Keep
files short (under 10 seconds, except `demarrage` and `compte`) and not too loud. In the app, then use
**Settings**, **Reload sounds** (or reopen it). To go back to the default sounds:
`bash outils/sons.sh retirer` (remove).

The default sounds are made by `marque/sons.py` (synthesized notes, no outside samples). You can
edit that script to make your own: `python3 personnalisation/marque/sons.py output_folder` (needs
`pip install numpy`).

## 2. The robot's boot animation (your logo)

This is what shows while the robot starts up. Three options.

**Your logo, fading in, from a simple image** (PNG, transparent background preferred):

```
python3 personnalisation/animation_depuis_image.py my_logo.png
bash personnalisation/fabriquer_module_animation.sh
bash outils/animation.sh installer modules/camybrith_animation-perso.zip
```

The background color is the second argument, in hexadecimal: `... my_logo.png ffffff` for white.
Open `personnalisation/bootanimation_apercu.png` to preview the result before sending it. Needs
Python 3 and Pillow (`pip install pillow`).

**A complete animation you already have** (an Android `bootanimation.zip`, 1280 x 800):

```
bash personnalisation/fabriquer_module_animation.sh my_animation.zip
bash outils/animation.sh installer modules/camybrith_animation-perso.zip
```

**The Camybrith Cooking animation, modified**: the drawing (pot, steam, name, colors) is in
`marque/marque.py` and the timing in `marque/bootanim.py`.
`python3 personnalisation/marque/bootanim.py personnalisation/bootanimation.zip` rebuilds it.

In every case, the animation appears at the next reboot. To get Lidl's back:
`bash outils/animation.sh retirer`, then reboot.

> The very first logo, shown for a split second at power-on, lives in a separate partition. On the
> Smart it is barely visible: the project does not touch it, and it is best left alone.

## 3. The app's name, icon and splash screen (for the curious)

These are inside the app itself, so it has to be rebuilt. No need for Android Studio, GitHub does
it for you:

1. Make your own copy of the project with the **Fork** button at the top of the GitHub page.
2. Edit in your copy, directly on the website (pencil icon):
   * the name: `android:label` in `app/src/main/AndroidManifest.xml`;
   * the icon: `app/src/main/res/drawable/ic_launcher.xml` (Android vector drawing);
   * the splash screen: `app/src/main/kotlin/app/toque/ui/SplashView.kt` (colors and texts);
   * the default sounds: the files in `app/src/main/assets/sons`.
3. Bump the version number in `app/build.gradle.kts` (for example `1.0.0` becomes `1.0.1`, and both
   `versionCode` values go up by 2).
4. In your copy's **Actions** tab, enable workflows. Every time the version changes, GitHub builds
   the APKs and publishes them in your **Releases**.
5. Download them into `apk/` and run `bash outils/installer.sh`.

Important: keep the same package name (`app.toque`) and the same signature
(`app/signature/toque.p12`), otherwise the new app cannot talk to the motor pilot.
