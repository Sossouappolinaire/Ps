# Relais Kouamépay (IP fixe pour SebPay)
1. Créez un dépôt GitHub avec ces 4 fichiers (server.js, package.json, render.yaml, README.md) à la racine.
2. render.com → New → Blueprint → choisissez le dépôt → Apply. Aucune variable à régler.
3. Ouvrez https://<nom>.onrender.com : la page affiche l'IP et l'URL à ajouter chez SebPay.
4. Dans Supabase : update sebpay_keys set relay_url = 'https://<nom>.onrender.com' where id = 1;
