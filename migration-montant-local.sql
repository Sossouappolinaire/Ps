-- À exécuter une fois dans le SQL Editor de Supabase (montant en monnaie locale des paiements BNB)
alter table sebpay_crypto
  add column if not exists recipient_currency text,   -- monnaie du pays qui reçoit (ex. XOF)
  add column if not exists payout_amount numeric,     -- montant à envoyer au bénéficiaire, en monnaie locale
  add column if not exists fx_rate numeric;           -- taux USD -> monnaie locale figé à la création
