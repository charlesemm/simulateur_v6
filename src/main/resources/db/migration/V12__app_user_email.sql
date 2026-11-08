-- Adresse de messagerie du compte, facultative.
-- Elle ne sert qu'a preparer le message de remise des identifiants (lien mailto: cote navigateur) :
-- le simulateur n'envoie aucun courriel lui-meme et ne stocke jamais de mot de passe.
alter table app_user
    add column email varchar(255);

comment on column app_user.email is 'Adresse ou envoyer les identifiants (facultative, aucun envoi automatique)';
