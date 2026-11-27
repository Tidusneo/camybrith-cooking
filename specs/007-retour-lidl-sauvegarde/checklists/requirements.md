# Specification Quality Checklist: Retour au fonctionnement Lidl et sauvegarde des données

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-27
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Réécrite le 2026-11-27 : le retour arrière est le retour au fonctionnement avec la seule appli
  Lidl (root conservé). Choix par défaut dans Assumptions. Validation complète.
- Complétée le 2026-11-27 : geste de l'interrupteur (FR-016, vers l'appli Lidl seulement) et bulle
  de retour vers Camybrith (FR-017). Validation complète.
- Complétée le 2026-11-27 : mémoire pleine (FR-018 à FR-020, SC-009, restauration sans copie).
  Validation complète.

## Relecture des chemins d'écriture (T073, 2026-11-27)

- **Moteur** (`core/backup/RescueCommand.kt`) : fichiers écrits uniquement sous
  `/data/adb/camybrith/` (écriture atomique, `fsync`) ; en plus, `touch
  /data/adb/modules/<module du projet>/remove` (retrait) et suppression des sauvegardes laissées
  par l'interface dans `/data/data/app.toque/files/sauvegardes-a-ranger`. Commandes : celles de
  `contracts/cli.md`, complété pour `appops`, `set-home-activity` et les lancements. Aucune
  écriture dans `/system`, aucune partition touchée, l'appli Lidl jamais modifiée (seul l'état
  de son service moteur, cible du registre).
- **`post-fs-data.sh`** : lit le compteur, ajoute 1, l'écrit, `sync`, `exit 0` ; pas d'attente,
  pas de `pm` ni d'`am`, erreurs ignorées, compteur illisible remis à 0 : conforme à D13.
- **`service.sh`** : attend `sys.boot_completed`, lance `camybrith demarrage` et `surveiller`,
  remet le compteur à 0 après 2 minutes ; il ne bloque pas le démarrage.
- **Scripts `outils/`** : sur le robot, seulement `secours.sh installer` (module Magisk,
  `inscrire`), `installer.sh`, `barre.sh`, `animation.sh` (inscrits au registre avant d'agir),
  `origine.sh`, `retirer.sh`, `sauvegarde.sh envoyer` (`/data/local/tmp` puis
  `/data/adb/camybrith/sauvegardes`) ; tous demandent confirmation. Sur l'ordinateur : `sauvegardes/`
  (ignoré par git) et, pour `sauvegarde-mac.sh`, `~/Camybrith-sauvegarde` (lecture seule du robot).
- **Banc d'essai** : n'agit que sur l'émulateur `emulator-5580`.

