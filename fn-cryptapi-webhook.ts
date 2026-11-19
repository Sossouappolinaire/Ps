// Fonction Edge Supabase : "cryptapi-webhook" — Verify JWT DÉSACTIVÉ.
// CryptAPI appelle cette URL (GET) quand un paiement BNB est confirmé.
// Sécurité : l'URL de callback contient un jeton secret propre à chaque paiement (généré par la fonction "sebpay").
// Elle doit répondre "*ok*" en texte brut, sinon CryptAPI renvoie l'appel.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);
const T = "sebpay_crypto";

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

const done = () => new Response("*ok*", { headers: { "Content-Type": "text/plain" } });

Deno.serve(async (req) => {
  const q = new URL(req.url).searchParams;
  const ref = q.get("ref") ?? "";
  const token = q.get("t") ?? "";
  if (!ref || !token) return new Response("bad request", { status: 400 });

  const { data: t } = await db.from(T).select("*").eq("reference", ref).maybeSingle();
  if (!t || !safeEqual(token, String(t.token))) return new Response("invalid", { status: 401 });

  // Paiement détecté mais pas encore confirmé : on attend l'appel confirmé
  if (q.get("pending") === "1") return done();
  // Le paiement doit être arrivé sur l'adresse générée pour cette transaction
  if (q.get("address_in") && String(q.get("address_in")).toLowerCase() !== String(t.address_in).toLowerCase()) {
    return new Response("address mismatch", { status: 400 });
  }
  if (t.status === "approved") return done(); // déjà traité (idempotent)

  const received = Number(q.get("value_coin") ?? 0);
  const expected = Number(t.expected_bnb);
  // tolérance de 2 % (variation de change / arrondis) ; en dessous : paiement insuffisant, à traiter à la main
  const status = received >= expected * 0.98 ? "approved" : "underpaid";
  await db.from(T).update({
    status,
    value_coin: received,
    value_forwarded_coin: Number(q.get("value_forwarded_coin") ?? 0),
    txid_in: q.get("txid_in"),
    txid_out: q.get("txid_out"),
    cryptapi_uuid: q.get("uuid"),
    updated_at: new Date().toISOString(),
  }).eq("id", t.id);
  return done();
});
