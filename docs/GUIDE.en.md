# Installing Camybrith Cooking on a Monsieur Cuisine Smart, step by step

🇫🇷 **[Version française](GUIDE.md)**

This guide starts from zero. It is written for someone who has never rooted an Android device:
read each step in full before starting it, and do not skip anything. Count two to three hours in
total, a good part of it waiting.

Steps 1 to 6 (the **root**) are done only once. After that, everything happens over Wi-Fi.

> **Want company?** A ready-to-use Claude assistant can guide you step by step: see "Let
> Claude guide you" in the [README](../README.en.md).

> Menu names on the robot are given as they appear on a robot set to French, with the English
> wording next to them when it helps. The install scripts print their messages in French; the
> guide says what they should end with.

## Contents

0. [Before you start](#0-before-you-start)
1. [Prepare the Windows PC](#1-prepare-the-windows-pc)
2. [Connect the robot and back everything up](#2-connect-the-robot-and-back-everything-up)
3. [Prepare the "cache" image](#3-prepare-the-cache-image)
4. [Boot TWRP and copy the files](#4-boot-twrp-and-copy-the-files)
5. [Restore the stock boot and install the apps](#5-restore-the-stock-boot-and-install-the-apps)
6. [Root with Magisk](#6-root-with-magisk)
7. [Control the robot over Wi-Fi (ADB)](#7-control-the-robot-over-wi-fi-adb)
8. [Install Camybrith Cooking](#8-install-camybrith-cooking)
9. [First test with the real motor](#9-first-test-with-the-real-motor)
10. [Everyday use](#10-everyday-use)
11. [Updating](#11-updating)
12. [Going back](#12-going-back)
13. [Common problems](#13-common-problems)

## 0. Before you start

### The golden rules

These mistakes have already cost other people their robot. Read them again before each step.

1. **The right USB port.** Under the maintenance cover there are two USB sockets. Plug in
   **only** to the one marked **"Android USB"**. The other one ("Power PCB USB") is wired to the
   motor and heating power board: it can fry your PC.
2. **In SP Flash Tool, always "Download Only".** Never "Firmware Upgrade", never "Format All +
   Download". If a window mentions **"PMT changed"** or offers to format, click **Cancel** and
   stop.
3. **In TWRP, never "Swipe to Allow Modifications".** Always choose **"Keep Read Only"**.
   Modifying the system partition stops the robot from booting.
4. **In the robot's factory menu, do not start any test and do not accept any dialog.** Above
   all not "Activer l'appareil" (activate the device: it breaks the link with the Lidl account),
   nor the endurance test (it spins the motor), nor the scale calibration (it needs 1 and 3 kg
   weights).
5. **Never accept a Lidl system update again** once the robot is rooted: it would remove the root
   and could leave the robot stuck. (Updates of the Monsieur Cuisine app are fine; it is the
   system update that is dangerous.)
6. **Never wipe the "cache" partition** (in TWRP or anywhere else) once Magisk is installed: it
   uses it.
7. **Keep your backup** (step 2) in two copies, on two different drives. It is what lets you
   repair the robot if something goes wrong.

### What you need

* A **Monsieur Cuisine Smart SKMS 1200 A1**. The guide was tested on a 2021 unit with Android 8.1
  and system version **V01.00.83** (June 2022).
* A **Windows 10 or 11 PC** with about **6 GB of free space**. SP Flash Tool, the tool that reads
  and writes the robot's memory, only exists for Windows (and Linux).
* A **USB-A male to USB-A male** cable, preferably **USB 2.0** (a few euros). Careful: many
  "charging" cables have no data wires and will not work.
* A **small Torx screwdriver** for the maintenance cover (check the screw size on your robot).
* Your home **Wi-Fi**, which the robot is already connected to.

### Can this robot be rooted?

On some Smart units secure boot is enabled and SP Flash Tool is refused as soon as it connects,
with the error `STATUS_BROM_CMD_SEND_DA_FAIL`. Nobody has managed to root those. Good news: the
very first test (step 2) **only reads** the memory. If it fails with that error, you stop there
without having damaged anything.

### A bit of vocabulary

* **Root**: full rights over the robot's Android system. Camybrith Cooking needs it to talk
  directly to the motor board.
* **Partition**: a piece of the robot's memory (boot, system, cache…).
* **TWRP**: a small recovery system, booted once to copy files.
* **Magisk**: the tool that provides root without touching the system partition.
* **ADB**: Google's tool to control an Android device from a computer.
* **Terminal**: the window where you type commands. To copy a command from this guide, select it,
  copy it, paste it in the terminal, then press Enter.

## 1. Prepare the Windows PC

### 1.1 SP Flash Tool

1. Download **SP Flash Tool v5 for Windows** from [spflashtool.com](https://spflashtool.com/).
2. Unzip it into a simple folder, for example `C:\SPFT`.
3. Create a folder for the whole project, for example `C:\MCS`, with a subfolder
   `C:\MCS\sauvegarde` (backup).

### 1.2 The MediaTek USB driver

Without it, Windows sees the robot for a second as "MT65xx Preloader" with a yellow triangle, then
loses it.

1. Go to the [Microsoft Update Catalog](https://www.catalog.update.microsoft.com/) and search for
   **MediaTek PreLoader USB VCOM**. Download the `.cab` file for Windows 10/11 64-bit.
2. Extract the `.cab` (with [7-Zip](https://www.7-zip.org/) for example), keeping the folders.
   You should find a `cdc-acm.inf` file in the `x64` folder.
3. Right-click the Start menu, **Terminal (Admin)**, go to that folder (`cd` followed by the path)
   and type:
   ```
   pnputil /add-driver cdc-acm.inf /install
   ```
4. If Windows answers "Access denied", the terminal is not running as administrator.
5. If Windows later says it cannot load `usb2ser.sys`, that is a Windows 11 protection:
   **Windows Security > Device security > Core isolation**, turn off **Memory integrity**, then
   restart the PC. You can turn it back on at the end.

### 1.3 The "scatter" file and TWRP

They were shared by ihrapsa in the community discussion. On
[this comment](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38#issuecomment-1605329093),
download the two attached files and put them in `C:\MCS`:

* `MT8167_Android_scatter_MC3_jonah1024.txt` (the Smart's memory map);
* `twrp_MC3_no_adb.zip` (unzip it: you get a TWRP `.img` image).

### 1.4 The three apps to install on the robot

Download them into a `C:\MCS\apk` folder and **rename them exactly** as shown:

| App | Where to get it | Tested version | Rename to |
|---|---|---|---|
| Magisk | [Magisk's official GitHub](https://github.com/topjohnwu/Magisk/releases) (`Magisk-v31.1.apk`) | 31.1 | `magisk.apk` |
| Navigation Bar (`nu.nav.bar`) | Play Store, or a reputable APK mirror (APKMirror, APKCombo) | 3.3.0 | `navbar.apk` |
| HTTP File Server (`slowscript.httpfileserver`) | Play Store, or a reputable APK mirror | 1.7.1 | `httpserver.apk` |

Why these apps: the robot has no Android buttons, so **Navigation Bar** adds a bar with Back, Home
and Recent apps. **HTTP File Server** lets you exchange files between the robot and the PC with a
plain web browser, because ADB over USB does not work on the Smart.

Get Magisk **only** from its official GitHub: fake versions circulate elsewhere.

### 1.5 Ubuntu inside Windows (WSL) and this project

Two steps (3 and 7) and all of the project's scripts run on Linux. Windows can run Ubuntu in a
window without changing anything on the PC.

1. In the Terminal (Admin), type `wsl --install -d Ubuntu`, then restart the PC.
2. Open **Ubuntu** from the Start menu, choose a user name and a password (nothing is shown while
   you type the password, that is normal).
3. In the Ubuntu window, install the tools and get the project:
   ```
   sudo apt update && sudo apt install -y git e2fsprogs zip unzip curl python3-pil
   git clone https://github.com/Tidusneo/camybrith-cooking ~/camybrith-cooking
   cd ~/camybrith-cooking
   ```

In Ubuntu, your Windows drive is under `/mnt/c`: `C:\MCS` is `/mnt/c/MCS`.

> **On a Mac or Linux**, you can do every step from 7 onwards from your usual computer: open the
> Terminal, install git and get the project the same way. Steps 1 to 6 still need a Windows PC
> for SP Flash Tool.

## 2. Connect the robot and back everything up

This step only **reads** the robot's memory. It writes nothing.

### 2.1 Connecting

1. Switch the robot off at the power switch.
2. Open the maintenance cover (Torx screws). Find the **"Android USB"** socket (golden rule 1).
3. Plug the cable between that socket and a USB port **at the back** of the PC (directly on the
   motherboard, no hub, no extension).

### 2.2 How SP Flash Tool works

When the robot is switched on, its chip spends a few seconds in a special mode before Android
starts. SP Flash Tool uses that moment to talk to the memory directly. So **always click in SP
Flash Tool first, then switch the robot on**.

1. Start `C:\SPFT\flash_tool.exe` with right-click, **Run as administrator**.
2. **Download** tab, next to "Scatter-loading File", click **choose** and open
   `C:\MCS\MT8167_Android_scatter_MC3_jonah1024.txt`. Touch nothing else in that tab.

### 2.3 The read test (16 MB)

1. **Readback** tab, click **Add**: a line appears. **Double-click** that line.
2. A window asks where to save: choose `C:\MCS\sauvegarde` and type the name `boot.img`.
   (If you forget the name, the file will be called `ROM_0`: rename it afterwards.)
3. The next window asks which area to read. Fill in exactly:
   * Type: **Hex**
   * Region: **EMMC_USER**
   * Start Address: **0x1D80000**
   * Length: **0x1000000**
4. Robot off, cable plugged in: click **Read Back**, then **switch the robot on**.
5. A progress bar starts, then a green "Readback OK" window appears. Switch the robot off.

Check: `boot.img` must be **exactly 16,777,216 bytes** (right-click, Properties).

* **Error `STATUS_BROM_CMD_SEND_DA_FAIL` right away**: your robot is locked, stop here. Nothing is
  damaged.
* **Nothing happens**: it is almost always the driver (step 1.2) or the cable. Open Device Manager
  and switch the robot on: "MediaTek PreLoader USB VCOM" should show up for a few seconds under
  **Ports (COM & LPT)**.

### 2.4 The full backup

In the **Readback** tab, remove the test line (right-click, Remove), then add these three lines the
same way (Type **Hex**, Region **EMMC_USER**):

| File name | Start Address | Length | Expected size (bytes) |
|---|---|---|---|
| `emmc_debut.bin` | 0x0 | 0xB4C00000 | 3,032,481,792 |
| `cache.img` | 0xA2000000 | 0x12C00000 | 314,572,800 |
| `system.img` | 0x22000000 | 0x80000000 | 2,147,483,648 |

`emmc_debut.bin` is a single copy of the whole beginning of the memory, up to and including the
cache: partition table, settings specific to your robot, system. It is what would let you repair
it. The other two files are used during the procedure.

Robot off: **Read Back**, then switch the robot on. Count about ten minutes and touch nothing until
the green window. Then check the three sizes and **copy the whole `C:\MCS\sauvegarde` folder to
another drive** (USB stick, external disk, another computer).

## 3. Prepare the "cache" image

The robot has neither ADB over USB nor a card reader: to get the apps onto it, we slip them into a
copy of its cache partition, which we will write back. Your backup is not modified.

In the **Ubuntu** window:

```
cd ~/camybrith-cooking
bash outils/preparer_cache.sh /mnt/c/MCS/sauvegarde /mnt/c/MCS/apk
```

The script checks the sizes, writes `boot.img` and the three apps into a copy named
`cache_avec_applis.img`, reads each file back to make sure it is identical, then checks the file
system. It must end with "Image prête" (image ready). If it says "ÉCHEC" (failure), do not use the
image.

## 4. Boot TWRP and copy the files

This is **the first step that writes to the robot**. Only start it with a verified backup.

### 4.1 Write TWRP and the cache

1. In SP Flash Tool, **Download** tab, scatter loaded:
   1. **untick every line**, then tick only **boot** and **cache**;
   2. double-click the **boot** location and choose the **TWRP** image (the `.img` from
      `twrp_MC3_no_adb.zip`);
   3. double-click the **cache** location and choose `C:\MCS\sauvegarde\cache_avec_applis.img`;
   4. in the drop-down above the list, choose **Download Only** (golden rule 2).
2. Robot off: click **Download** (the green arrow), then switch the robot on. Wait for the green
   window.
3. Switch the robot off and on again. The logo shows for a second, then the screen stays **black
   for about a minute**: that is normal. TWRP then starts.

### 4.2 Aiming in TWRP

On the Smart, TWRP's display is **rotated a quarter turn**, but touch is not: it reacts as if the
interface were shown **upright and stretched across the whole screen**.

The easiest way: take a photo of the screen with your phone, rotate the photo until TWRP reads the
right way up, and note where the button is, as a proportion (for example "three quarters of the
way to the right, at the very bottom"). Then touch the robot's screen **at the same place in
proportion**, looking at it normally. Go slowly: a misplaced tap does nothing serious as long as
you do not perform an unplanned "swipe".

### 4.3 Copy the files

1. At startup, TWRP asks whether it may modify the system: choose **Keep Read Only**
   (golden rule 3).
2. Go to **Advanced**, then **File Manager**, then open the **cache** folder.
3. For each of the four files (`boot.img`, `magisk.apk`, `navbar.apk`, `httpserver.apk`): tap the
   file, **Copy File**, choose the **sdcard** folder, **Select Folder**, then do the **Swipe to
   Confirm** (that one is safe). TWRP shows "Successful". Go back to the cache folder with
   **Back** for the next file.

## 5. Restore the stock boot and install the apps

### 5.1 Restore the stock boot

TWRP currently sits where the normal boot should be. We put the original back.

1. In SP Flash Tool, **Download** tab: **untick cache**, only **boot** stays ticked.
   Double-click its location and choose your **original `boot.img`** (the one from the backup,
   certainly not the TWRP image). Mode **Download Only**.
2. Click **Download**: SP Flash Tool waits for the robot.
3. In TWRP, tap the **home** icon (in the middle, at the very bottom of the screen seen normally),
   then **Reboot**, then **System**. If TWRP offers to install its app: **Do Not Install**, no
   swipe.
4. On reboot, SP Flash Tool catches the robot and rewrites the boot. Wait for the green window,
   then switch off and on: the Monsieur Cuisine app starts as before.

If SP Flash Tool misses it and the robot goes back to TWRP: wait a minute, switch off at the power
switch, click **Download** again, then switch on.

You can unplug the USB cable now: you will only need it again for step 6.

### 5.2 Open Android settings through the factory menu

1. In the Monsieur Cuisine app: menu (top right), **Réglages** (Settings), **À propos** (About),
   then **À propos de cet appareil** (About this device). Note the **last 4 characters of the
   tablet's serial number**, then **Terminé** (Done).
2. **Long-press (2 seconds)** the title "À propos de Monsieur Cuisine" (About Monsieur Cuisine).
3. The password is those 4 characters followed by `_Mc30`. For example, for a serial number
   ending in `AB12`, it is `AB12_Mc30`. Once unlocked, the factory menu stays reachable without a
   password for 15 days.
4. Go to the **Further test** screen and **long-press (2 seconds)** the version number, bottom
   right: Android settings open.

Reminder (golden rule 4): in the factory menu, start nothing and accept nothing.

### 5.3 Install the three apps

1. In Android settings: **Stockage** (Storage), then **Fichiers** (Files, or "Explorer"). At the
   root you will find your four files.
2. Install **`navbar.apk` first**. Android asks you to allow installing unknown apps for the file
   manager: accept.
3. Open **Navigation Bar** and give it what it asks for (display over other apps, accessibility).
   A bar appears with Back, Home and the **recent apps square**. If you get stuck before that,
   switch the robot off and on, and start again at 5.2.
4. Install **`magisk.apk`** then **`httpserver.apk`**.

Tip to open an app (the robot has no app drawer): tap its `.apk` file again in the file manager,
accept to reinstall it, then tap **Open**. Once it has been opened, it also shows up in the
bar's **square**.

## 6. Root with Magisk

### 6.1 Have Magisk patch the boot

1. Open **Magisk**. If it offers to update the app, decline.
2. On the Magisk line, tap **Install**.
3. Leave **Keep AVB 2.0/dm-verity** enabled (green).
4. Tap **Patch firmware / image** (the line with the small arrow).
5. The file picker hides internal storage: tap **⋮** at the top right, **Show internal storage**,
   then choose the device in the left column and pick **`boot.img`**.
6. Wait for the end of the log. You should read in particular "Stock boot image detected" (the
   starting boot is indeed the original) and "Pre-init storage partition: cache". The created
   file is called `magisk_patched-31100_xxxxx.img`, in the **Download** folder.

### 6.2 Get the file onto the PC

1. Open **HTTP File Server** and **start the server**. It shows an address like
   `http://192.168.1.42:8080`. Note the robot's IP address (here `192.168.1.42`): you will need it
   later.
2. On the PC, open that address in the browser, go to **Download** and download the
   `magisk_patched…img` file into `C:\MCS`.
3. Check its size: it must be **at most 16,777,216 bytes** (usually a bit under 16 MB). For an
   extra check, in Ubuntu:
   ```
   head -c 8 /mnt/c/MCS/magisk_patched*.img; echo
   ```
   The answer must be `ANDROID!`.

### 6.3 Write the patched boot

1. Plug the USB cable back in ("Android USB" socket), robot off.
2. SP Flash Tool, **Download** tab: **only boot ticked**, with the `magisk_patched…img` file.
   Mode **Download Only**.
3. **Download**, then switch the robot on and wait for the green window.
4. Switch off and on. The robot may restart once or twice by itself before showing the Monsieur
   Cuisine app: let it.
5. Open Magisk (bar square): the Magisk line must say **Installed: 31.1**. If it says additional
   setup is required, accept (it restarts once).

**The robot has been boot-looping for more than five minutes?** Don't panic: write your original
`boot.img` back the same way (step 6.3 with the original), and it will start as before.

The robot is rooted. The Monsieur Cuisine app works exactly as before.

## 7. Control the robot over Wi-Fi (ADB)

From here on, no more cable or SP Flash Tool. Everything is done from the **Ubuntu** window (or
the Terminal on a Mac or Linux), in the project folder.

### 7.1 Enable debugging on the robot

1. Android settings (through the factory menu, step 5.2): **Système** (System), **À propos de la
   tablette** (About tablet).
2. Tap **7 times** the **Numéro de build** (Build number) line: developer options are unlocked.
3. **Système**, **Options pour les développeurs** (Developer options): turn on **Débogage USB**
   (USB debugging).

### 7.2 Install ADB on the computer

Google's official tools, without installing anything into the system:

```
cd ~
curl -LO https://dl.google.com/android/repository/platform-tools-latest-linux.zip
unzip -q platform-tools-latest-linux.zip
~/platform-tools/adb version
```

On a Mac, replace `linux` with `darwin` in the address. The last command must print "Android Debug
Bridge".

### 7.3 Build and install the "ADB Wi-Fi" module

This Magisk module makes ADB listen on Wi-Fi at every boot. It also registers **your** computer's
key as authorized: on the Smart, the "Allow USB debugging?" dialog cannot show up on top of the
Monsieur Cuisine app.

```
cd ~/camybrith-cooking
~/platform-tools/adb start-server
bash outils/module_adb.sh
cp modules/adb-wifi-ma-cle.zip /mnt/c/MCS/
```

(The last line copies the module to `C:\MCS` so you can find it from Windows. On a Mac, it is
directly in the project's `modules` folder.)

1. On the robot, start the **HTTP File Server** server. On the PC, open its address in the browser
   and **upload** `adb-wifi-ma-cle.zip` with the page's upload button.
2. In **Magisk**: **Modules** tab, **Install from storage**, choose the ZIP, then **Reboot**.
3. Once the Monsieur Cuisine app is back, wait **about thirty seconds**.

### 7.4 Allow root for the computer

Since version 31, Magisk no longer shows a request dialog: root is granted in advance.

1. Open Magisk, **Superuser** tab ("#" icon).
2. Tap **+**, choose **Shell** (sometimes `com.android.shell`) and enable it, duration **Forever**
   if asked.
3. Leave **Restrict root capabilities** off.

### 7.5 Check

Replace `192.168.1.42` with your robot's address:

```
~/platform-tools/adb connect 192.168.1.42:5555
~/platform-tools/adb shell su -c id
```

The first must answer `connected to…`, the second `uid=0(root)…`. If so, the tricky part is over.

> Tip: in your router's settings, reserve a fixed IP address for the robot ("static DHCP lease").
> Otherwise its address may change and you will have to give it to the scripts again.

## 8. Install Camybrith Cooking

Still in the project folder:

```
cd ~/camybrith-cooking
git pull
bash outils/telecharger.sh
bash outils/installer.sh
```

* `telecharger.sh` (download) fetches the latest published version: the **motor pilot**, the
  **fast** ("rapide") and **safe** ("prudente") interface (into `apk/`), and the **boot
  animation** (into `modules/`).
* `installer.sh` asks for the robot's IP address the first time (it is kept in
  `outils/robot.conf`), installs the pilot, grants it root, installs the interface, grants it
  root, has **Android compile it** (one to two minutes, do not open the app meanwhile), then
  opens it. It ends with "C'est fini" (done).

Then the boot animation (optional but nice):

```
bash outils/animation.sh installer
~/platform-tools/adb -s 192.168.1.42:5555 reboot
```

On reboot you see the Camybrith Cooking animation, then the app opens by itself. The stock
Monsieur Cuisine app stays installed, untouched.

To switch the app to English: **Réglages**, **Langue · Language**, **English**.

If the app behaves strangely (frozen screen, crash after a few seconds): install the safe version,
slower but immune to this robot's compilation problem: `bash outils/installer.sh prudente`.

## 9. First test with the real motor

After installing, Camybrith Cooking starts in **Demo** mode: the motor is simulated, nothing turns.
Switch to the real motor carefully, **one hand on the robot's power switch**.

1. In the app: **Settings**, **Machine** section, choose **Real machine**, then **Continue**. The
   app takes control of the motor instead of the stock app. Check that the "Robot" line says
   **Monsieur Cuisine Smart**.
2. **Empty bowl, lid closed**: manual cooking, speed 2, 10 seconds. The motor turns then stops by
   itself.
3. **Empty bowl**: speed 2, 30 seconds, and **open the lid halfway through**. The motor stops
   immediately and the app pauses. Close it: the app offers to **resume**.
4. **200 ml of water**: speed 1, 37 °C, 30 seconds. The motor turns and the water warms up.
5. **The scale**: put an object on it, the weight is shown.

If any of these tests does not go as described, switch the robot off at the power switch, go back
to **Demo**, and open a ticket (the project's **Issues** tab) describing what you saw.

## 10. Everyday use

* **Lidl recipes**: **Settings**, **Lidl recipes**, **Sync**. They then update every night
  (adjustable).
* **The navigation bar** at the bottom of the screen can be hidden:
  `bash outils/barre.sh cacher` (hide) and `bash outils/barre.sh remettre` (bring back). Without
  it, a **long press on the clock** in Camybrith Cooking opens Back, Home and Recent apps.
* **Back to the stock app**: **Settings**, **Machine**, "Back to the stock app". Camybrith Cooking
  releases the motor and switches to Demo.
* **Your own recipes** written for a Thermomix: the file format and how to send them are explained
  in [RECIPES.en.md](RECIPES.en.md). Settings converted for the Smart are flagged "to check":
  read them before starting.
* **Customize sounds and logo**: see
  [personnalisation/README.en.md](../personnalisation/README.en.md).

## 11. Updating

**From the robot (easiest)**: when a new version is out, the app says so when it opens. Go to
**Settings › Updates**, then **Install**: it downloads the pilot and the interface, installs them
and restarts (about a minute, never while cooking).

**From the computer** (if the robot has no Internet, or to troubleshoot):

```
cd ~/camybrith-cooking
git pull
bash outils/telecharger.sh
bash outils/installer.sh
```

Your favorites, settings and recipes are kept.

## 12. Going back

From lightest to most complete:

1. **Just use the stock app**: Settings, Machine, "Back to the stock app".
2. **Remove the animation**: `bash outils/animation.sh retirer`, then reboot.
3. **Uninstall Camybrith Cooking** (after point 1):
   ```
   ~/platform-tools/adb -s 192.168.1.42:5555 uninstall app.toque
   ~/platform-tools/adb -s 192.168.1.42:5555 uninstall app.toque.pilote
   ```
4. **Remove root**: write your original `boot.img` back with SP Flash Tool (step 6.3 with the
   original). Magisk and its modules become inactive.
5. **If something goes badly wrong**, your step 2 backup holds everything needed to bring the
   robot back to its original state. Do not try things at random: ask for help in the project's
   Issues, with a precise description of what happened.

## 13. Common problems

**Windows shows "MT65xx Preloader" with a yellow triangle.** The driver is not installed: step 1.2.

**`pnputil` answers "Access denied".** The terminal is not running as administrator.

**SP Flash Tool does nothing when I switch the robot on.** Click Read Back or Download **before**
switching on. Try another USB port (at the back of the PC). Check that the cable carries data:
plug a phone in with it, it must show up in File Explorer.

**SP Flash Tool mentions "PMT changed" or wants to format.** Cancel. Check that you are in
**Download Only** and that only the right lines are ticked.

**Magisk's file picker is empty.** Tap ⋮ then "Show internal storage" (step 6.1). Otherwise, move
`boot.img` into the Download folder with the file manager.

**`adb connect` answers "failed to authenticate" or "unauthorized".** The module with your key is
not installed, or the robot has not finished booting: check in Magisk, Modules tab, that "ADB
Wi-Fi" is there and enabled, reboot, wait 30 seconds, then `~/platform-tools/adb disconnect` and
try again.

**`su -c id` answers "Permission denied".** Shell has no root: step 7.4.

**Another computer needs to control the robot.** On that computer, do step 7.2, then
`bash outils/module_adb.sh` and install that new module in Magisk: it adds its key without removing
the others.

**The scripts say the robot is not answering ("Le robot ne répond pas").** Check that it is on and
on the same network, then its address: if it changed, delete `outils/robot.conf` and run again.

**The app says root access is refused when switching to Real machine.** In Magisk, Superuser tab,
enable "Camybrith Cooking" and "Camybrith Cooking : pilote moteur".

**The app crashes or freezes after a few seconds.** Run `bash outils/installer.sh` again (it
recompiles), or switch to the safe version: `bash outils/installer.sh prudente`.

**Going back from the safe version to the fast one**: the safe version has a higher number.
Uninstall the interface (`adb uninstall app.toque`, your settings will be lost) then
`bash outils/installer.sh`.

## Sources and thanks

* Smart rooting method: [depau's guide](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38#issuecomment-1928542640),
  scatter and TWRP files by [ihrapsa](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38#issuecomment-1605329093),
  and the whole [issue #38](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38)
  discussion.
* [Magisk](https://github.com/topjohnwu/Magisk) by topjohnwu.
* [Toque](https://github.com/Wuki60/MrCuisineVibeConnect) by Wuki60, of which Camybrith Cooking is
  the Smart port.
