-- Déjà appliquée sur le projet accords-flat (migration « sebpay_montants_reels_versement »).
-- Montants réels renvoyés par SebPay pour les versements / décaissements / remboursements.
alter table public.sebpay_transactions
  add column if not exists recipient_amount numeric,  -- montant reçu par le bénéficiaire (taux SebPay)
  add column if not exists total_deducted numeric;    -- total débité du wallet par SebPay (montant + frais)
