# Contract: commande `camybrith` (module de secours, sur le robot)

Chemin : `/data/adb/modules/camybrith_secours/camybrith`, toujours en root. Sortie lisible en
français ; `--json` : une ligne JSON. Codes : `0` succès, `1` refus attendu (raison affichée),
`2` état inattendu → **arrêt, rien de plus n'est fait** (principe VII).

| Sous-commande | Effet | Refus (code 1) |
|---|---|---|
| `etat` | espace libre de `/data` (alerte sous 10 %), présence de la réserve, mode du robot (`mode.json`), versions, nombre de modifications actives par portée, secours, journal en cours, dernier bilan | — |
| `noter <type> <cible> <avant> <apres> <portee> <origine>` | inscrit une modification (data-model › Modification) et affiche son `id` | type ou format de cible invalide |
| `noter-fin <id> ok\|echec` | confirme ou retire une modification après tentative | id inconnu |
| `inscrire` | inscription rétroactive (research D8), sans rien modifier | registre déjà initialisé |
| `retour-lidl [--secours\|--geste]` | retour à l'appli Lidl (research D5, D6), bilan, rappel, redémarrage | cuisson en cours ; opération déjà en cours |
| `reprendre` | réapplique les modifications de portée `lidl` annulées par le dernier retour (dans l'ordre croissant d'`id`), efface `secours.json`, ouvre Camybrith, qui refait ses vérifications de Machine réelle | aucun retour à reprendre ; cuisson en cours avec l'appli Lidl |
| `retrait [--garder-socle]` | retour à l'appli Lidl puis annulation de la portée `retrait` ; à la fin, retire le module de secours et `/data/adb/camybrith` | cuisson en cours |
| `bilan [nom]` | affiche le dernier bilan (ou celui demandé) | — |
| `sauvegarder` | demande l'export à l'interface (broadcast) et range le fichier | interface absente |

**`post-fs-data.sh`** : uniquement le compteur du geste (lecture, +1, écriture, `sync`), sortie
toujours en succès, aucune attente ni appel à `pm`/`am` (research D13).

**Au démarrage (`service.sh`)** : compteur du geste (data-model › Geste de
l'interrupteur) ; reprise d'une opération interrompue ; mode de secours ; en mode Lidl,
démarrage de la bulle (`am startservice -n app.toque/.ui.ReturnBubble --ez modeLidl true`).

**Mémoire pleine** : une écriture qui échoue faute de place supprime la réserve et réessaie une
fois. `retour-lidl` fait `am force-stop` et `pm` avant toute écriture de fichier et aboutit même
sans journal ni bilan (bilan marqué `memoirePleine` dès qu'il peut être écrit) ; ce n'est jamais un
code 2. La restauration n'est pas faite par le module mais par l'interface (research D15).

**Cuisson en cours** : déterminée par `cuisson.json` (data-model › État de cuisson). Refus
(code 1) si cuisson en cours ; « état inconnu » → refus, sauf `--forcer` (utilisé seulement par
`outils/origine.sh` après confirmation explicite), `--secours` et `--geste`.

Seules écritures permises : `/data/adb/camybrith/`, `/data/adb/modules/` (retrait d'un module du
projet), `/data/data/app.toque*`, `pm enable|disable|uninstall` sur les cibles du registre,
`settings put|delete` sur les cibles du registre, `am force-stop`, `reboot` (`svc power reboot`).
Ajoutées à l'implémentation, limitées à Camybrith et à l'appli Lidl : `appops set app.toque
SYSTEM_ALERT_WINDOW allow` (bulle), `cmd package set-home-activity` sur l'alias d'accueil de
Camybrith (reprise), et des lancements sans écriture : `am start` (écran d'accueil, Camybrith),
`am start-foreground-service` (bulle), `am broadcast` vers Camybrith (sauvegarde), `monkey -p
<appli Lidl>` (ouvrir l'appli Lidl).

# Contract: scripts ordinateur (`outils/`)

Basés sur `commun.sh`. Chaque script annonce ce qu'il va écrire et demande confirmation.

| Script | Usage | Écrit ? |
|---|---|---|
| `bash outils/secours.sh installer` | installe le module, lance `camybrith inscrire`, propose de redémarrer | oui |
| `bash outils/secours.sh etat` | `camybrith etat` | non |
| `bash outils/origine.sh` | retour à l'appli Lidl : affiche ce qui sera annulé, confirmation, `camybrith retour-lidl`, affiche le bilan et le rappel ; marche si l'interface est cassée | oui |
| `bash outils/origine.sh reprendre` | `camybrith reprendre` | oui |
| `bash outils/origine.sh registre` | copie `registre.json` et les bilans dans `sauvegardes/` | non |
| `bash outils/retirer.sh` | propose une sauvegarde, copie le registre, `camybrith retrait` | oui |
| `bash outils/sauvegarde.sh creer\|lister\|recuperer [nom]\|envoyer FICHIER\|restaurer NOM` | sauvegardes manuelles | selon la sous-commande |
| `bash outils/banc-essai.sh [scenario]` | banc d'essai sur émulateur (research D11) | non (émulateur seulement) |

Scripts existants à brancher sur `camybrith noter` : `installer.sh` (applis), `barre.sh`
(réglage d'accessibilité), `animation.sh` (module). S'ils ne trouvent pas le module de secours,
ils proposent de l'installer et s'arrêtent.
