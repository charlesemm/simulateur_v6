# Prompt 15 - Planification des analyses (brique Q3)

Dépend des prompts 13 et 14. Plan : `docs/07-qualite-donnees/04-plan.md`.

```text
# Prompt 15 - Planification des analyses

Contexte :
  Lire CLAUDE.md, docs/journal.md, puis docs/07-qualite-donnees/04-plan.md (section Q3).
  Demande du porteur du projet, mot pour mot : « dans cet onglet on doit pouvoir
  programmer des analyses, genre chaque jour à telle heure, analyse ».
  Écho tient le calendrier ; le script reste maître de son exécution et vient lire
  les échéances par GET /api/analyses/attendues (écrit au prompt 14).

Objectif :
  Programmer des analyses récurrentes et suivre, pour chaque échéance, si le script
  est venu et si son rapport est arrivé.

Emplacement :
  quality/planification/ (Planification, PlanificationService, OuvertureEcheances),
  persistence/ (+ repositories), api/AnalyseController (compléments),
  src/main/resources/db/migration/V15__planification.sql.

Contraintes :
  - Table planification_analyse : id, libelle, heure (TIME), jours_semaine (texte,
    ex. 'LUNDI,MARDI' ou 'TOUS'), cible, actif, date_creation, date_maj.
    Une seule valeur de cible pour l'instant : DERNIERE_SIMULATION_TERMINEE. La
    colonne existe quand même, pour que d'autres cibles n'imposent pas de migration.
  - Table analyse_attendue : id, planification_id (nullable : une analyse peut être
    demandée à la main), run_id (la simulation visée, nullable au moment de
    l'ouverture), date_echeance, date_retrait, date_rapport, statut, detail.
    Statuts : ATTENDUE, RETIREE, RAPPORT_RECU, SILENCE, RAPPORT_REFUSE, SANS_CIBLE.
  - Un @Scheduled à la minute (fixedDelay, pas de cron Spring sur l'heure locale)
    fait deux choses, et deux seulement :
      1. ouvrir les échéances dues depuis la dernière minute traitée ;
      2. passer en SILENCE les échéances retirées dont le délai de rapport est
         dépassé (réutiliser simulateur.qualite.delai-rapport-minutes du prompt 14).
    Il est IDEMPOTENT : deux passages dans la même minute n'ouvrent pas deux
    échéances. Contrainte d'unicité sur (planification_id, date_echeance).
  - « Dernière simulation terminée » = le dernier simulation_run au statut ARRETE.
    Aucune simulation terminée à l'heure dite : l'échéance s'ouvre quand même et
    prend le statut SANS_CIBLE avec son explication. Elle ne tombe pas en erreur et
    ne disparaît pas silencieusement.
  - Horloge : le service prend une java.time.Clock injectée, jamais Instant.now()
    en dur, pour que les tests pilotent le temps sans attendre.
  - Fuseau : l'heure saisie est l'heure du serveur ; l'écrire noir sur blanc dans
    l'aide du champ, et fixer le fuseau en configuration.
  - PAS DE QUARTZ, pas de nouvelle dépendance : @EnableScheduling suffit. Signaler
    la limite assumée : une seule instance d'Écho tourne, il n'y a pas de verrou
    distribué, et le jour où il y en aurait deux les échéances doubleraient.
  - @PreAuthorize sur toute écriture (créer, activer, supprimer une planification) ;
    audit par AuditService à la création et à la suppression.
  - GET /api/analyses/attendues rend désormais les échéances réellement ATTENDUE,
    avec la simulation visée et le lien de retrait de son export.

Critère de fin :
  - Test unitaire avec une Clock pilotée : une planification à 03h00 TOUS les jours
    ouvre exactement une échéance par jour, et aucune en avance.
  - Test : deux exécutions du @Scheduled dans la même minute n'ouvrent qu'une échéance.
  - Test : échéance ouverte sans aucune simulation ARRETE, statut SANS_CIBLE.
  - Test : échéance retirée sans rapport, délai dépassé, statut SILENCE.
  - Test : le cycle complet, échéance ouverte, lue par l'API, export retiré, rapport
    poussé, statut RAPPORT_RECU.
  - Test MockMvc : un OBSERVATEUR ne peut pas créer de planification.
  - ./mvnw clean verify est vert.

À ne pas faire :
  - Ne pas faire déclencher le script par Écho : Écho ouvre une échéance, c'est tout.
  - Ne pas générer de simulation depuis la planification (cible écartée au plan).
  - Ne pas ajouter Quartz ni de planificateur externe.
  - Ne pas écrire les écrans ici (prompt 16).

En fin de tâche :
  Proposer le message de commit, mettre à jour docs/journal.md, signaler les
  hypothèses dont le fuseau retenu et l'absence de verrou multi-instance.
```

**À vérifier toi-même**

- Crée une planification à l'heure qui vient, attends : l'échéance apparaît avec la simulation visée.
- Lance le script d'exemple : le statut passe de ATTENDUE à RAPPORT_RECU.
- Désactive la planification : plus aucune échéance ne s'ouvre.

