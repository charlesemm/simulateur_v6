# Prompt 17 - Les anomalies de relation (brique Q5)

Indépendant des prompts 14 à 16 ; se branche sur l'écran du prompt 16 pour l'affichage.
Demande le prompt 13 pour les deux anomalies de fichier. Plan : `docs/07-qualite-donnees/04-plan.md`.

```text
# Prompt 17 - Les anomalies de relation

Contexte :
  Lire CLAUDE.md, docs/journal.md, docs/07-qualite-donnees/04-plan.md (section Q5)
  et docs/07-qualite-donnees/02-ce-qui-est-demande.md (section 3.1, les huit
  dimensions, et 7.1, ce qui est constructible sur le périmètre actuel).
  Les 11 AnomalyType existants abîment UN CHAMP D'UNE LIGNE. Quatre des huit
  dimensions du cahier portent sur des RELATIONS : aucun mutator ne sait les poser,
  donc le script n'a aujourd'hui rien à trouver sur ces dimensions.

Objectif :
  Poser les anomalies que le catalogue ne sait pas poser, et les inscrire au corrigé
  comme les autres, pour que les huit dimensions du cahier soient mesurables sur le
  périmètre prestations.

Emplacement :
  anomaly/AnomalyType.java, anomaly/mutators/ (une classe par type, comme les 11
  existantes), domain/prestations/anomalies/ (champs et cibles éligibles),
  export/ (pour les deux anomalies de fichier),
  src/main/resources/db/migration/V16__anomalies_relation.sql.

Contraintes :
  - QUATRE anomalies de relation, une classe de mutator chacune :
      . DOUBLON_APPROXIMATIF : écrire une SECONDE ligne proche de l'originale
        (variation orthographique du nom, « N'Guessan » contre « Nguessan »,
        espace ou tiret en plus, prénoms permutés), pas corrompre celle-ci.
        Réutiliser la mécanique de DoublonExactMutator, qui sait déjà émettre une
        ligne de plus et renseigner doublon_de.
      . REFERENCE_BRISEE : viser un identifiant qui n'existe dans aucun référentiel
        (professionnel de santé, centre de santé, type de centre). L'identifiant
        inventé doit être bien formé : c'est le lien qui est rompu, pas le format.
      . INCOHERENCE_TEMPORELLE : date de soin hors période de droits, date antidatée,
        date dans le futur. Se juge contre une autre date de la même simulation, donc
        le mutator a besoin du contexte du parcours, pas seulement du champ.
      . INCOHERENCE_CROISEE : droits actifs alors que la période de droits est close.
        Chaque champ est valide seul ; c'est leur conjonction qui est fausse.
  - DEUX anomalies DE FICHIER, qui ne peuvent pas exister dans une colonne typée et
    s'injectent donc à l'écriture de l'export (prompt 13) :
      . CARACTERES_CASSES : « N'Guessan » écrit N&#39;Guessan ou N?Guessan ;
      . FORMAT_DATE_FICHIER : la date écrite dans un format incohérent avec les autres
        lignes du même fichier.
    Elles sont inscrites au corrigé au moment de l'export, dans la même transaction
    que l'enregistrement de export_fichier. Elles n'altèrent PAS la base : la base
    reste propre, c'est le fichier qui est piégé. Le dire en commentaire dans le code.
  - MIGRATION OBLIGATOIRE sur deux contraintes existantes de anomaly_injected, à
    faire par un NOUVEAU script, jamais en modifiant V8 :
      . ck_anomaly_injected_type énumère les 11 types : la recréer avec les six
        nouveaux ;
      . ck_anomaly_injected_doublon impose (type = 'DOUBLON_EXACT') = (doublon_de IS
        NOT NULL) : DOUBLON_APPROXIMATIF renseigne aussi doublon_de, donc la
        contrainte devient « doublon_de non nul si et seulement si le type est un
        doublon, exact ou approximatif ».
  - uq_anomaly_injected UNIQUE NULLS NOT DISTINCT (event_id, type, champ) reste :
    vérifier que les nouveaux types la respectent, et dire lequel pose problème si
    l'un d'eux veut deux anomalies du même type sur le même champ.
  - Tout tirage passe par SeededRandom. Même graine, mêmes anomalies, mêmes
    emplacements. Jamais Math.random(), jamais new Random().
  - CategorieAnomalie (quality/) doit classer les six nouveaux types. Les anomalies de
    relation ne sont ni structurelles ni sémantiques au sens actuel : décider d'une
    troisième catégorie plutôt que de les forcer dans une case, et le signaler.
  - Chaque nouveau type est activable et dosable dans le scénario YAML, comme les 11
    existants (AnomalyProfile, clé en camelCase via AnomalyType.cle()).
  - Un scénario d'exemple dans scenarios/prestations/ qui active les six nouveaux.
  - HORS PÉRIMÈTRE, faute de tables, et à écrire dans le journal pour mémoire :
    téléphone, RCCM, compte contribuable, OGD, date de liquidation, délai de carence.
    Ils arriveront avec la phase 2 (affiliation) et n'ajouteront que des types au
    catalogue, sans toucher au canal, au rapprochement ni aux écrans.

Critère de fin :
  - Un test par mutator, à sortie EXACTE, avec un aléa scripté, comme les 11 existants.
  - Test : un run avec DOUBLON_APPROXIMATIF produit bien une ligne de plus, dont le
    corrigé renseigne doublon_de vers l'originale.
  - Test : REFERENCE_BRISEE produit un identifiant bien formé et absent des référentiels.
  - Test de déterminisme : deux runs de même graine et même profil produisent le même
    corrigé, pour les six nouveaux types.
  - Test : les deux anomalies de fichier apparaissent dans le fichier exporté ET dans
    le corrigé, et la base reste propre (la valeur en base est la valeur saine).
  - Test : la migration passe sur une base portant déjà des lignes des 11 anciens types.
  - Les six apparaissent dans l'écran de scénario et dans le corrigé après génération.
  - ./mvnw clean verify est vert.

À ne pas faire :
  - Ne pas modifier V8 ni aucun script Flyway déjà appliqué.
  - Ne pas écrire une classe fourre-tout : un type = un mutator = une classe testable
    isolément.
  - Ne pas toucher au calcul du rappel ni de la précision.
  - Ne pas anticiper les tables de la phase 2.
  - Ne pas corrompre les tables métier : seul l'événement porte la version corrompue,
    et le fichier pour les deux anomalies de fichier.

En fin de tâche :
  Proposer le message de commit, mettre à jour docs/journal.md avec la catégorie
  retenue pour les anomalies de relation, et clore le module Qualité des données.
```

**À vérifier toi-même**

- Lance une simulation avec le scénario d'exemple, ouvre le corrigé : les six nouveaux types y sont.
- Cherche le doublon approximatif : deux lignes, une variation orthographique, et le lien vers l'originale.
- Relance avec la même graine : le corrigé est identique.
- Fais tourner le script d'exemple : il doit maintenant rater des choses, et c'est le but — c'est ça qui se mesure.

