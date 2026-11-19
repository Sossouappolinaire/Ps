# Kouamépay Android — encaisser et décaisser

App Java Android (sans dépendance), compilée par GitHub Actions.
Tous les fichiers sont à la racine du dépôt, sauf le workflow : `build.yml` doit être dans `.github/workflows/build.yml` (exigence de GitHub).

## Architecture
App Android → fonction Edge `sebpay` (projet Supabase accords-flat) → API SebPay.
Les clés SebPay sont dans la table `sebpay_keys` (illisible depuis l'app). L'app ne contient que l'URL Supabase et la clé anon (publique).
Webhook : fonction `sebpay-webhook` (signature HMAC vérifiée).

## Déjà fait dans Supabase (accords-flat)
- Tables `sebpay_keys`, `sebpay_users`, `sebpay_settings`, `sebpay_transactions`.
- Clés SebPay enregistrées.
- Fonctions `sebpay` et `sebpay-webhook` déployées.
- `Config.java` rempli.

## Il reste
1. Créer votre utilisateur (Authentication > Users) puis le passer admin (voir `supabase.sql`).
2. Pousser les fichiers sur GitHub, ajouter `build.yml` dans `.github/workflows/`.
3. Actions > dernier run > Artifacts > `Kouamepay-APK`, installer l'APK.

## Commission
`sebpay_settings` : taux total (5 % par défaut = votre part + celle de SebPay) et mode `deduct` / `add`.
Votre part = commission totale − frais réels SebPay (visible par l'admin dans l'historique).

## Remboursements
- Décaissement refusé : SebPay recrédite le wallet, l'historique l'indique.
- Encaissement réussi : bouton « Rembourser le client » (admin), le montant payé est renvoyé au numéro du client.

## Interface (v2)
- Design : en-tête, navigation du bas, cartes, puces de réseaux, reçu de confirmation, écran de statut.
- `Countries.java` : tous les pays du monde (212) avec drapeau (émoji calculé depuis le code ISO). Fichier généré, la même table est dans `fn-sebpay.ts`.
- Un pays est « accepté par SebPay » si SebPay renvoie au moins un réseau pour lui (action `operators`) : la liste des réseaux est donc toujours celle de SebPay, sans rien coder en dur.
- Pays non accepté : l'app propose uniquement le BNB (BNB Smart Chain) via **CryptAPI**. Le client saisit un montant en USD, l'app affiche une adresse unique et le montant exact en BNB ; CryptAPI transfère le BNB reçu (frais CryptAPI déduits, environ 1 %) vers votre adresse `0x13108641DcfaBea3b2e8dEd4d35B8f49606f5A17`, puis appelle `cryptapi-webhook`, qui confirme le paiement dans l'app.
- **À faire** : (1) exécuter la partie « CryptAPI » de `supabase.sql` ; (2) déployer `fn-sebpay.ts` (fonction `sebpay`) et `fn-cryptapi-webhook.ts` (fonction `cryptapi-webhook`, **Verify JWT désactivé**) ; (3) vérifier chez CryptAPI que le domaine du callback est accepté (voir ci-dessous).

## Service client (v3)
- Onglet « Service client » (barre du bas) + carte en haut de l'accueil, pour **tous** les utilisateurs. Le pastille « En ligne / Hors ligne » indique si l'administrateur est connecté (signe de vie envoyé toutes les 30 s par l'app de l'admin, en ligne si < 90 s).
- Réponses automatiques par IA gratuite **sans clé**, appelées depuis la fonction Edge `sebpay` (4 services essayés dans l'ordre, le suivant prend le relais si l'un refuse ou ne répond pas) : Pollinations → LLM7 → Kilo AI Gateway → OVHcloud AI Endpoints (liste `PROVIDERS` dans `fn-sebpay.ts`).
- Si l'IA ne trouve pas de réponse fiable (ou si les 4 services échouent), le client reçoit : « Votre problème est pris en compte, veuillez patienter, l'administrateur va vous répondre dans un instant. » et la conversation apparaît « À traiter » chez l'administrateur.
- Administrateur : l'onglet liste les conversations (à traiter en premier) ; en l'ouvrant, il répond au client. Quand l'admin est en ligne et vient de répondre, l'IA se tait pour lui laisser la main.
- Limite : 20 messages par client toutes les 10 minutes.
- **À faire** : (1) exécuter la partie « Service client » de `supabase.sql` ; (2) redéployer `fn-sebpay.ts` (fonction `sebpay`).
- Commission : taux total 5 % (votre part + celle de SebPay) déjà réglé par défaut ; à modifier dans `sebpay_settings` si besoin.

## Décaissement BNB (v4)
- Le client qui paie en BNB indique aussi **le réseau et le numéro** (indicatif du pays choisi) qui doivent recevoir l'argent.
- **2 minutes** après la création du paiement, l'écran propose « Joindre une capture d'écran » (image depuis le téléphone, réduite en JPEG). Le délai est aussi contrôlé par le serveur. Les images sont stockées dans le bucket privé `sebpay-proofs`.
- **Administrateur** : onglet « Décaisser » → liste « Décaissements BNB à traiter » avec pays, réseau, numéro, captures et bouton **Décaisser**. Le décaissement est manuel : l'administrateur envoie l'argent lui-même, puis touche « J'ai envoyé l'argent : marquer Payé ».
- **Client** : voit « Payé » (écran de suivi et Historique) et peut **signaler** « Argent non reçu » depuis l'Historique ; le décaissement revient alors chez l'administrateur, marqué ⚠.
- Les paiements BNB apparaissent maintenant dans l'Historique (statut : En attente du paiement, Paiement reçu, Preuves envoyées, Payé, Signalé).
- Pays acceptés par SebPay : **deux numéros**. Le formulaire « Encaisser » demande le numéro qui paie (débité de son compte Mobile Money) **et** le numéro qui reçoit (réseau, nom du bénéficiaire, numéro). Les deux numéros peuvent être de **pays différents** (ex. Bénin → Côte d'Ivoire) : le réseau du numéro qui reçoit vient de SebPay pour son pays ; si les devises diffèrent, le taux (open.er-api.com, sans clé) est figé à l'encaissement et le reçu l'affiche (Bénin/Côte d'Ivoire : XOF, pas de conversion). Dès que l'encaissement réussit, le net est **versé automatiquement** au numéro qui reçoit (une seule fois, sans réessai automatique pour éviter un double versement). Le reçu, l'Historique et le suivi affichent les deux numéros et l'état du versement.
- **À faire (v5)** : exécuter aussi la partie « Encaissement Mobile Money avec 2 numéros » de `supabase.sql`, redéployer `sebpay` **et** `sebpay-webhook` (fn-sebpay-webhook.ts).
- **À faire** : (1) exécuter la partie « Décaissement BNB (v4) » de `supabase.sql` ; (2) redéployer `fn-sebpay.ts` ; (3) republier l'APK.

## Alignement sur la documentation SebPay (v17)
- Erreurs : le serveur lit `errors.code` et affiche un message clair (FEATURE_DISABLED, ACCOUNT_PAYOUT_DISABLED, INSUFFICIENT_BALANCE, PAYOUTS_LOCKED, AMOUNT_TOO_LOW…).
- Tests (bouton « Tester » et vérification avant opération) : lecture d'une référence inexistante sur `/collections/{ref}` et `/payouts/{ref}` ; 404 = la route répond, clés/IP/KYC valides. Ils ne détectent pas une fonctionnalité désactivée qui ne se voit qu'au POST.
- `provider_link` (Wave, Orange…) : l'app ouvre la page de l'opérateur après un encaissement, comme l'exige SebPay.
- Webhook : répond tout de suite (SebPay exige un 2xx en moins de 10 s) ; le versement automatique continue en arrière-plan.
- Rappel wallets : `/payouts` débite toujours le wallet de la devise principale du compte ; `/collections` crédite le wallet de la devise du payeur.
