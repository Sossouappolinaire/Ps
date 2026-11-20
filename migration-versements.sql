-- Déjà appliquée sur le projet accords-flat. À garder pour mémoire / autre projet.
alter table sebpay_transactions
  add column if not exists manual_payout_reference text,
  add column if not exists manual_payout_at timestamptz,
  add column if not exists manual_payout_by text;
