-- Référence : ce schéma est DÉJÀ appliqué dans le projet accords-flat (migrations sebpay_schema et sebpay_commission_mode).
-- Les clés SebPay y sont déjà enregistrées dans sebpay_keys.

-- Réglages utiles (SQL Editor) :
-- Changer la commission totale (la vôtre + celle de SebPay) :
--   update sebpay_settings set commission_total_percent = 5 where id = 1;
-- Mode "deduct" (par défaut) : la commission est déduite du montant.
-- Mode "add" : la commission s'ajoute (le client paie en plus à l'encaissement) :
--   update sebpay_settings set commission_mode = 'add' where id = 1;

-- Rendre un utilisateur (créé dans Authentication > Users) administrateur de l'app :
-- insert into sebpay_users (user_id, role)
-- select id, 'admin' from auth.users where email = 'votre@email.com';

-- Ajouter un agent (encaisse seulement) :
-- insert into sebpay_users (user_id, role)
-- select id, 'agent' from auth.users where email = 'agent@email.com';

-- ============ CryptAPI (paiements BNB pour les pays non acceptés par SebPay) ============
-- À exécuter une fois dans le SQL Editor de Supabase.
create table if not exists sebpay_crypto (
  id uuid primary key default gen_random_uuid(),
  reference text unique not null,
  token text not null,                 -- secret du callback, jamais renvoyé à l'app
  ticker text,
  status text not null default 'pending',   -- pending | approved | underpaid
  country text,
  amount_usd numeric,
  expected_bnb numeric,
  address_in text,                     -- adresse unique générée par CryptAPI pour ce paiement
  address_out text,                    -- votre adresse BNB (réception)
  value_coin numeric,                  -- reçu avant frais CryptAPI
  value_forwarded_coin numeric,        -- transféré chez vous après frais
  txid_in text,
  txid_out text,
  cryptapi_uuid text,
  created_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table sebpay_crypto enable row level security; -- aucune policy : accessible uniquement via les fonctions Edge

-- Variables facultatives des fonctions Edge (Project Settings > Edge Functions > Secrets) :
--   BNB_ADDRESS       (par défaut : 0x13108641DcfaBea3b2e8dEd4d35B8f49606f5A17)
--   CRYPTAPI_TICKER   (seulement si la détection automatique du BNB échoue)

-- ============ Service client (chat IA + administrateur) ============
-- À exécuter une fois dans le SQL Editor de Supabase, puis redéployer la fonction `sebpay` (fn-sebpay.ts).
create table if not exists sebpay_support (
  id bigint generated always as identity primary key,
  user_id uuid not null,               -- le client (une conversation par utilisateur)
  user_email text,
  sender text not null check (sender in ('user', 'ai', 'admin')),
  body text not null,
  needs_admin boolean not null default false,  -- l'IA n'a pas su répondre : l'administrateur doit répondre
  created_at timestamptz not null default now()
);
create index if not exists sebpay_support_user_idx on sebpay_support (user_id, created_at);
alter table sebpay_support enable row level security; -- aucune policy : accessible uniquement via la fonction Edge

-- Présence de l'administrateur : mise à jour toutes les 30 s par l'app tant qu'elle est ouverte
create table if not exists sebpay_presence (
  id int primary key default 1,
  admin_last_seen timestamptz
);
alter table sebpay_presence enable row level security;
insert into sebpay_presence (id) values (1) on conflict do nothing;

-- ============ Décaissement BNB (v4) ============
-- À exécuter une fois dans le SQL Editor de Supabase, puis redéployer la fonction `sebpay` (fn-sebpay.ts).
-- Le client qui paie en BNB indique le réseau et le numéro qui doivent recevoir l'argent ; 2 minutes après,
-- il peut envoyer ses preuves (images). L'administrateur les reçoit, décaisse à la main, puis marque « Payé ».
alter table sebpay_crypto
  add column if not exists recipient_operator text,                      -- réseau qui reçoit l'argent
  add column if not exists recipient_phone text,                         -- numéro qui reçoit (indicatif + numéro)
  add column if not exists payout_status text not null default 'awaiting', -- awaiting | proof_sent | paid | reported
  add column if not exists proof_paths text[] not null default '{}',    -- images de preuve (bucket sebpay-proofs)
  add column if not exists proof_sent_at timestamptz,
  add column if not exists paid_at timestamptz,
  add column if not exists paid_by uuid,
  add column if not exists reported_at timestamptz,                      -- le client signale : argent non reçu
  add column if not exists report_note text;

-- Bucket privé pour les preuves (accès uniquement via la fonction Edge, par liens signés temporaires)
insert into storage.buckets (id, name, public) values ('sebpay-proofs', 'sebpay-proofs', false)
on conflict (id) do nothing;

-- ============ Encaissement Mobile Money avec 2 numéros (v5) ============
-- Pays acceptés par SebPay : le numéro qui paie (débité) ET le numéro qui reçoit l'argent directement.
-- Quand l'encaissement réussit, la fonction Edge verse automatiquement le montant net au numéro qui reçoit.
alter table sebpay_transactions
  add column if not exists recipient_operator text,   -- réseau du numéro qui reçoit (encaissement)
  add column if not exists recipient_phone text,      -- numéro qui reçoit (indicatif + numéro)
  add column if not exists payout_reference text,     -- référence du versement automatique (OUT-...)
  add column if not exists payout_note text;          -- état du versement automatique
-- (recipient_name existe déjà : nom du bénéficiaire)

-- Pays différents autorisés (ex. Bénin -> Côte d'Ivoire) : pays/devise du numéro qui reçoit, montant versé et taux fixés à l'encaissement
alter table sebpay_transactions
  add column if not exists recipient_country text,
  add column if not exists recipient_currency text,
  add column if not exists payout_amount numeric,
  add column if not exists fx_rate numeric;


-- ===== Relais à IP fixe (v11) : adresse du serveur relais (ex. https://kouamepay-relay.onrender.com), vide = appel direct
alter table sebpay_keys add column if not exists relay_url text;
-- update sebpay_keys set relay_url = 'https://VOTRE-RELAIS.onrender.com' where id = 1;

-- ===== Inscription dans l'app (v12) : nom, prénom, et création automatique du profil "agent" =====
alter table sebpay_users add column if not exists first_name text;
alter table sebpay_users add column if not exists last_name text;
alter table sebpay_users add column if not exists email text;

create or replace function public.sebpay_handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.sebpay_users (user_id, role, first_name, last_name, email)
  values (new.id, 'agent', new.raw_user_meta_data->>'first_name', new.raw_user_meta_data->>'last_name', new.email)
  on conflict (user_id) do nothing;
  return new;
end $$;

drop trigger if exists sebpay_on_signup on auth.users;
create trigger sebpay_on_signup after insert on auth.users
  for each row execute function public.sebpay_handle_new_user();

-- ===== Relais Render : IP 74.220.48.0/24 autorisée chez SebPay, site https://ma-boutique-ngu0.onrender.com =====
update sebpay_keys set relay_url = 'https://ma-boutique-ngu0.onrender.com' where id = 1;
