-- Renommage des tables de donnees simulees : prefixe TB_ECHO et nom en majuscules.
-- Les noms sont entre guillemets : sans cela PostgreSQL replierait l'identifiant en minuscules.
-- Les tables internes a l'application (app_user, api_key, audit_log, scenario, info_application)
-- ne sont pas concernees.
ALTER TABLE acte                  RENAME TO "TB_ECHO_ACTE";
ALTER TABLE analyse_attendue      RENAME TO "TB_ECHO_ANALYSE_ATTENDUE";
ALTER TABLE anomaly_injected      RENAME TO "TB_ECHO_ANOMALY_INJECTED";
ALTER TABLE assure                RENAME TO "TB_ECHO_ASSURE";
ALTER TABLE ayant_droit           RENAME TO "TB_ECHO_AYANT_DROIT";
ALTER TABLE centre_sante          RENAME TO "TB_ECHO_CENTRE_SANTE";
ALTER TABLE chaos_action          RENAME TO "TB_ECHO_CHAOS_ACTION";
ALTER TABLE checkpoint_evenement  RENAME TO "TB_ECHO_CHECKPOINT_EVENEMENT";
ALTER TABLE echange               RENAME TO "TB_ECHO_ECHANGE";
ALTER TABLE evenement             RENAME TO "TB_ECHO_EVENEMENT";
ALTER TABLE examen                RENAME TO "TB_ECHO_EXAMEN";
ALTER TABLE export_fichier        RENAME TO "TB_ECHO_EXPORT_FICHIER";
ALTER TABLE facture               RENAME TO "TB_ECHO_FACTURE";
ALTER TABLE facture_historique    RENAME TO "TB_ECHO_FACTURE_HISTORIQUE";
ALTER TABLE facture_ligne         RENAME TO "TB_ECHO_FACTURE_LIGNE";
ALTER TABLE medecin               RENAME TO "TB_ECHO_MEDECIN";
ALTER TABLE medicament            RENAME TO "TB_ECHO_MEDICAMENT";
ALTER TABLE pharmacie             RENAME TO "TB_ECHO_PHARMACIE";
ALTER TABLE planification_analyse RENAME TO "TB_ECHO_PLANIFICATION_ANALYSE";
ALTER TABLE quality_detection     RENAME TO "TB_ECHO_QUALITY_DETECTION";
ALTER TABLE quality_report        RENAME TO "TB_ECHO_QUALITY_REPORT";
ALTER TABLE simulation_run        RENAME TO "TB_ECHO_SIMULATION_RUN";
ALTER TABLE taux                  RENAME TO "TB_ECHO_TAUX";
