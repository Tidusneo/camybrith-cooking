# Camybrith Cooking on the Monsieur Cuisine Connect (experimental)

🇫🇷 **[Version française](CONNECT.md)**

Camybrith Cooking comes from **Toque**, Wuki60's app written for the Monsieur Cuisine Connect
(SKMC 1200). The Connect motor code is still there, so Camybrith Cooking can also run on a Connect,
with its interface, sounds and boot animation.

> **Careful: experimental.** Camybrith Cooking has **not been tried on a real Connect yet**. Toque
> runs on it every day. If you just want a proven app on your Connect, use
> [Toque](https://github.com/Wuki60/MrCuisineVibeConnect). If you want to try Camybrith Cooking,
> follow the steps below carefully, and tell us how it went in the
> [Issues](https://github.com/scaule/camybrith-cooking/issues).

## 1. Root the Connect with Toque's guide

Rooting the Connect differs from the Smart. Follow Toque's guide to the end of its step 2,
including ADB over Wi-Fi:
[Toque install guide](https://github.com/Wuki60/MrCuisineVibeConnect/blob/main/docs/INSTALLATION.md).

At the end, Magisk must be on the robot and `adb connect ROBOT_ADDRESS:5555` must answer
`connected`.

If Toque is already installed on your Connect, you already have all that: move on.

## 2. Prepare the computer

Do steps **1.5** (Ubuntu on Windows, or the Terminal on Mac/Linux, and the project) and **7.2**
(ADB) of the [Smart guide](GUIDE.en.md). Then check root:

```
~/platform-tools/adb connect ROBOT_ADDRESS:5555
~/platform-tools/adb shell su -c id
```

The answer must contain `uid=0(root)`. Otherwise, in Magisk on the robot, **Superuser** tab, allow
"Shell".

## 3. Install Camybrith Cooking

```
cd ~/camybrith-cooking
bash outils/telecharger.sh
bash outils/installer.sh
```

* Camybrith Cooking **replaces Toque**: it is the same app (`app.toque`), your favorites and
  settings are kept. The motor pilot is an extra app.
* On Android 6 (the Connect's), the compile step is skipped: Android already compiles the app at
  install time.
* The boot animation installs the same way as on the Smart: `bash outils/animation.sh installer`.

## 4. First test, empty bowl

The app starts in **Demo** mode. For the real motor, **one hand on the main switch**:

1. **Settings**, **Machine**, **Real machine**, then **Continue**. The "Robot" line must say
   **Monsieur Cuisine Connect**.
2. The badge at the top right must say "Ready", without "Demo".
3. Empty bowl, lid closed: manual cooking, 10 s, speed 2. The motor turns then stops by itself.
4. Open the lid during a test: the motor stops and the app pauses.
5. 30 s at 37 °C, speed 1, with a little water: the temperature rises.
6. Scale: put an object of known weight on it.

**At the slightest odd behavior**: switch off at the main switch, go back to **Demo**, and open an
Issue with the log (**Settings**, **Tools**, **Log**).

## Recipes, updates, going back

* **Lidl recipes**: **Settings**, **Lidl recipes**, **Sync**. On the Connect you can also import
  the stock app's recipes.
* **Updates**: `bash outils/telecharger.sh` then `bash outils/installer.sh`. Or, simpler,
  **Settings › Updates** in the app.
* **Back to Toque**: uninstall Camybrith Cooking, then reinstall Toque with its own script.
  ```
  ~/platform-tools/adb -s ROBOT_ADDRESS:5555 uninstall app.toque
  ~/platform-tools/adb -s ROBOT_ADDRESS:5555 uninstall app.toque.pilote
  ```
  Your favorites and settings are lost then.
