# Data Model: Retour au fonctionnement Lidl et sauvegarde des données

**Feature**: 007-retour-lidl-sauvegarde | **Date**: 2026-11-27

Racine sur le robot : `/data/adb/camybrith/` (root, mode 700). Voir [research.md](research.md).

## Modification (élément du registre)

| Champ | Type | Règle |
|---|---|---|
| `id` | entier | croissant, unique dans le registre |
| `type` | énuméré | `composant`, `reglage`, `demarrage`, `module`, `appli` (liste fermée, D2) |
| `cible` | texte | `paquet/composant` (composant, demarrage), `namespace/clé` (reglage), id du module, nom du paquet (appli) |
| `avant` | texte ou null | état ou valeur d'avant : `enabled`/`disabled`, valeur du réglage, `absent` |
| `apres` | texte | état ou valeur posée |
| `portee` | énuméré | `lidl`, `retrait`, `socle` (D2) |
| `origine` | texte | `interface`, `installer.sh`, `barre.sh`, `animation.sh`, `module`, `guide` |
| `date` | ISO 8601 | heure locale avec décalage |
| `deduit` | booléen | `true` si inscrite rétroactivement (D8) |
| `etat` | énuméré | `active` → `annulee` ou `echec` (raison dans `erreur`) ; `annulee` → `active` lors d'une reprise |
| `erreur` | texte ou null | message lisible si `etat` = `echec` |

Règles :
- Inscrite **avant** la modification ; si la modification échoue ensuite, la modification est
  retirée du registre (ou marquée `annulee`) par la même commande.
- `cible` doit correspondre au format de son `type` ; toute autre valeur est refusée (pas de
  commande libre).
- Annulation selon `avant` : `enabled`/`disabled` → remettre cet état ; valeur de réglage →
  la remettre ; `absent` → désactiver le composant (`demarrage`, `composant`), supprimer le
  réglage (`reglage`), retirer le module (`module`) ou désinstaller (`appli`).
- Deux modifications actives de même `type` et `cible` : la plus ancienne garde `avant`, la
  nouvelle est fusionnée (on revient à l'état d'origine, pas à un état intermédiaire).

## Registre

Fichier `registre.json` : `{ "format": 1, "robot": <empreinte>, "modifications": [ … ] }`.
Annulation : modifications `active` de la portée demandée, **ordre décroissant d'`id`**.
Une copie est récupérable sur l'ordinateur (`outils/origine.sh registre`).

## Journal d'opération

Fichier `op.json`, présent seulement pendant une opération.

| Champ | Valeurs |
|---|---|
| `type` | `retour-lidl`, `reprise`, `retrait` |
| `origine` | `interface`, `ordinateur`, `secours`, `geste` |
| `etape` | `debut` → `arret` → `annulation` → `verification` → `termine` (retour-lidl, retrait) ; `debut` → `reapplication` → `verification` → `termine` (reprise) |

Au démarrage : journal non `termine` → l'opération est **terminée** (pas annulée) pour
`retour-lidl` et `retrait` (D5) ; une `reprise` interrompue est **abandonnée vers l'appli Lidl**
(un `retour-lidl` est lancé, le sens sûr). Si le journal ne peut pas être écrit (mémoire pleine
malgré la réserve), le retour se fait sans journal (research D15).

## Bilan de retour

Fichier `bilans/AAAAMMJJ-HHMMSS.json` : type d'opération, date, origine (`interface`,
`ordinateur`, `secours`, `geste`), liste des
modifications annulées, liste des échecs avec raison, résultat de chaque contrôle de la
vérification finale (D6), `rappel` : texte « Ne jamais accepter de mise à jour système Lidl tant
que le robot est rooté » (FR-009). Les 10 derniers bilans sont gardés.

## État de cuisson

Fichier `cuisson.json` écrit par l'interface (research D12).

| Champ | Type | Règle |
|---|---|---|
| `occupe` | booléen | vrai d'un démarrage de programme à sa fin (pause comprise) |
| `programme` | texte | nom lisible, vide si `occupe` est faux |
| `maj` | ISO 8601 | rafraîchi au moins toutes les 30 s quand `occupe` est vrai |

Règle : cuisson en cours si `occupe` est vrai et `maj` a moins de 60 s ; fichier périmé avec
processus du pilote actif → « état inconnu » (refus côté robot, confirmation demandée côté Mac,
poursuite en mode de secours et pour le geste). Pilote arrêté → pas de cuisson, quel que soit le
fichier.

## Geste de l'interrupteur

Fichier `demarrages-courts` : un entier.

| Événement | Effet |
|---|---|
| démarrage (`post-fs-data.sh`) | +1, puis `sync` |
| robot allumé depuis 2 min après `sys.boot_completed` (`service.sh`) | remis à 0 |
| valeur ≥ 3 au démarrage, robot pas en mode Lidl | `retour-lidl --geste`, puis remis à 0 |
| valeur ≥ 3 au démarrage, robot déjà en mode Lidl | remis à 0, rien d'autre |

« Mode Lidl » : `mode.json` vaut `lidl` (voir Mode du robot).

## Mode du robot

Fichier `mode.json`, source unique pour savoir dans quel mode est le robot.

| Champ | Type | Règle |
|---|---|---|
| `mode` | énuméré | `camybrith` ou `lidl` ; absent = `camybrith` |
| `depuis` | ISO 8601 | date du dernier changement |
| `origine` | énuméré | `interface`, `ordinateur`, `secours`, `geste` (mêmes valeurs que Journal.origine) |

Écrit (écriture atomique) par `retour-lidl` à l'étape `termine` (`lidl`) et par `reprendre` à
l'étape `termine` (`camybrith`). Lu par le geste, la bulle et le bandeau de Camybrith.

## Bulle de retour

Pas de fichier propre : la bulle est démarrée à chaque démarrage en mode Lidl ; sa position et
son état réduit sont gardés dans les préférences existantes de `ReturnBubble`.

## Espace de stockage

| Élément | Règle |
|---|---|
| `reserve` | fichier de 2 Mo dans `/data/adb/camybrith/` ; supprimé pour libérer de la place si une écriture du module échoue ; recréé par `service.sh` quand l'espace le permet |
| Espace libre | mesuré sur `/data` ; alerte si < 10 % |
| Seuil de sauvegarde | espace libre ≥ taille estimée de la sauvegarde + 50 Mo |
| Bilan | champ `memoirePleine` (booléen) si une écriture a échoué pendant l'opération |

## Restauration en cours

Fichier `restauration-en-cours` dans les données de l'interface (pas dans `/data/adb`) : nom de la
sauvegarde à appliquer et nom de la sauvegarde automatique de l'état d'avant. Présent du début à
la fin d'une restauration ; à l'ouverture de l'appli, s'il existe, la restauration est rejouée
(idempotente) puis le fichier supprimé.

## Identité du robot pour le module

Fichier `robot.json` écrit par l'interface : `vendorPackage`, `hardwareServiceSuffix`,
`camybrithBootReceiver`, `camybrithHomeAlias` (D6).

## État de secours

Fichier `secours.json` : date, nombre de plantages comptés, version de Camybrith en cause.
Règle de décision (fonction pure dans `core`) : au moins **3 plantages de `app.toque` dans les
120 s** suivant `sys.boot_completed` ; jamais pendant le traitement d'un journal d'opération.

## Sauvegarde (manuelle, exportable)

Fichier `sauvegardes/camybrith-AAAAMMJJ-HHMMSS.camybrith` (zip).

| Champ du manifeste | Type | Règle |
|---|---|---|
| `format` | entier | commence à 1 |
| `createdAt` | ISO 8601 | |
| `app.versionName` / `app.versionCode` | texte / entier | interface installée |
| `pilot.versionCode` | entier | |
| `robot` | texte | empreinte anonymisée, jamais le numéro de série |
| `sections` | liste | `favorites`, `recipes` (sources IMPORTED et USER seulement), `settings` (sans secrets), `pilot` ; plus tard `wishlist`, `planning`, `home`, `notifications`, `health` |
| `summary` | objet | compteurs affichés à l'utilisateur |
| `files` | liste `{path, size, sha256}` | tous les fichiers du zip sauf le manifeste |
| `secretsIncluded` | booléen | toujours `false` |

Clés secrètes exclues : liste fermée dans `core` (`mqttPassword` aujourd'hui) ; toute clé non
déclarée sûre est exclue. Validation : format connu, empreintes exactes, aucun fichier en trop,
`app.versionCode` ≤ version installée, autre robot → avertissement. Conversions de format en
mémoire uniquement. Au plus 5 sauvegardes.
