-- V20 : provenance des tables de valeurs, inscrite dans la base.
-- Trois des quatorze listes de V18 ont ete deduites et non sourcees. Tant qu'une direction metier
-- ne les a pas validees, il faut que cela se voie depuis un client SQL, et pas seulement dans un
-- journal de projet : un commentaire de table est lu par celui qui ouvre la table, pas par celui
-- qui lit le depot. Aucune donnee n'est modifiee ici.

COMMENT ON TABLE "TB_TV_LOCALISATION_PAYS" IS
    'SOURCE : codes ISO 3166-1 alpha-3, CEDEAO et pays voisins.';
COMMENT ON TABLE "TB_TV_LOCALISATION_REGIONS" IS
    'SOURCE : decoupage administratif ivoirien, 31 regions et 2 districts autonomes.';
COMMENT ON TABLE "TB_TV_LOCALISATION_DEPARTEMENTS" IS
    'SOURCE : decoupage administratif ivoirien, 111 departements. Croise par district.';
COMMENT ON TABLE "TB_TV_LOCALISATION_LOCALITES" IS
    'SOURCE : les 13 communes du district autonome d''Abidjan et les chefs-lieux retenus.';
COMMENT ON TABLE "TB_TV_REGIMES" IS
    'SOURCE : les deux regimes de la CMU. RGB contributif, prise en charge 70 %, ticket moderateur '
    '30 % a la charge de l''assure. RAM non contributif, prise en charge 100 %, aucun reste a charge. '
    'Le taux est applique par la tarification comme un PLANCHER : un type de prestation fixe au-dessus '
    '(hospitalisation a 90 %) garde son taux.';
COMMENT ON TABLE "TB_TV_TYPES_FACTURES" IS
    'SOURCE : les quatre bons de prise en charge, ER-01-GPR ambulatoire et medicaments, ER-02-GPR '
    'hospitalisation, ER-03-GPR biologie et imagerie, et le dentaire.';
COMMENT ON TABLE "TB_REF_SPECIALITES_MEDICALES" IS
    'SOURCE : specialites du panier de soins. Cardiologie, diabetologie-endocrinologie et '
    'anesthesie-reanimation ouvrent au 2023-03-01, d''ou une date de debut distincte.';
COMMENT ON TABLE "TB_TV_CIVILITES" IS 'SOURCE : usage administratif courant.';
COMMENT ON TABLE "TB_TV_GENRES" IS 'SOURCE : usage administratif courant. Le code I n''est pas produit par le generateur.';
COMMENT ON TABLE "TB_TV_ORGANISMES" IS
    'SOURCE : organismes de la CMU, CNAM pilote et regulateur, organismes de gestion deleguee.';
COMMENT ON TABLE "TB_TV_TYPES_ETABLISSEMENTS_SANITAIRES" IS
    'SOURCE : pyramide sanitaire ivoirienne, du centre de sante rural au CHU, plus le secteur prive.';

-- Les trois listes deduites. Elles fonctionnent et sont coherentes, mais personne ne les a validees.
COMMENT ON TABLE "TB_TV_PRODUITS" IS
    'A VALIDER PAR LES DIRECTIONS METIER. Liste DEDUITE, non sourcee. La recherche documentaire '
    'retrouve bien le contenu du panier de soins de base (consultations, medicaments, chirurgie, '
    'biologie, imagerie, soins bucco-dentaires, hospitalisation, et l''ajout de l''hypertension et du '
    'diabete simples) mais aucune nomenclature de PRODUITS. Les trois codes ici sont une hypothese '
    'de travail du simulateur.';
COMMENT ON TABLE "TB_TV_MOTIFS_REJETS" IS
    'A VALIDER PAR LES DIRECTIONS METIER. Liste DEDUITE, non sourcee : aucune nomenclature publique '
    'de motifs de rejet de la CNAM n''a ete retrouvee. Les 22 motifs sont construits par analogie avec '
    'les controles de liquidation usuels. A remplacer par la liste reelle des que disponible.';
COMMENT ON TABLE "TB_TV_STATUTS_FACTURES" IS
    'Deux familles. Famille PARCOURS : SOURCEE, elle reprend la machine a etats du simulateur '
    '(FactureStatut) et ses transitions autorisees. Famille LIQUIDATION : DEDUITE, non sourcee, '
    'A VALIDER PAR LES DIRECTIONS METIER. La liquidation des factures releve des organismes de '
    'gestion deleguee ; la suite reelle des statuts et leurs transitions restent a confirmer.';
