// Fonction Edge Supabase : "sebpay-webhook" — Verify JWT DÉSACTIVÉ.
// La sécurité vient de la signature HMAC-SHA256 (X-SebPay-Signature).
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);
const T = "sebpay_transactions";

async function hmacHex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return Array.from(new Uint8Array(sig)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

function normStatus(s: unknown): "pending" | "approved" | "rejected" {
  const v = String(s ?? "pending").toLowerCase();
  if (["approved", "success", "successful", "completed"].includes(v)) return "approved";
  if (["rejected", "failed", "declined", "cancelled", "canceled", "expired"].includes(v)) return "rejected";
  return "pending";
}

const MIN_PAYOUT = 300;

// Versement automatique du net au numéro qui reçoit quand l'encaissement réussit (voir fn-sebpay.ts : même logique).
async function autoPayout(t: Record<string, any>) {
  if (t.type !== "collect" || !t.recipient_phone || t.payout_reference) return;
  const reference = `OUT-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
  const { data: claimed } = await db.from(T)
    .update({ payout_reference: reference, payout_note: "Versement en cours", updated_at: new Date().toISOString() })
    .eq("id", t.id).is("payout_reference", null).select("id").maybeSingle();
  if (!claimed) return;
  const note = (n: string) => db.from(T).update({ payout_note: n, updated_at: new Date().toISOString() }).eq("id", t.id);
  const amount = Math.round(Number(t.payout_amount ?? t.net_amount));
  const rCountry = t.recipient_country || t.country;
  const rCurrency = t.recipient_currency || t.currency;
  if (!(amount >= MIN_PAYOUT)) return void await note(`Montant trop faible pour le versement automatique (min ${MIN_PAYOUT}) : l'administrateur doit décaisser.`);
  try {
    const { data: k } = await db.from("sebpay_keys").select("*").eq("id", 1).maybeSingle();
    if (!k) throw new Error("configuration de paiement introuvable");
    const res = await fetch(`${k.base_url}/payouts`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Public-Key": k.public_key, "X-Secret-Key": k.secret_key },
      body: JSON.stringify({
        recipient_name: t.recipient_name || "Bénéficiaire", phone: t.recipient_phone, operator: t.recipient_operator,
        country: rCountry, amount, currency: rCurrency, external_reference: reference,
        callback_url: `${Deno.env.get("SUPABASE_URL")}/functions/v1/sebpay-webhook`,
      }),
    });
    const j = await res.json().catch(() => ({}));
    if (!res.ok || j.success === false) throw new Error(j.message || `HTTP ${res.status}`);
    const d = j.data !== undefined ? j.data : j;
    await db.from(T).insert({
      type: "payout", external_reference: reference, sebpay_id: d?.transaction_id ?? null,
      status: normStatus(d?.status), amount, currency: rCurrency, phone: t.recipient_phone,
      operator: t.recipient_operator, country: rCountry, recipient_name: t.recipient_name || "Bénéficiaire",
      sebpay_fee: Number(d?.fee_amount ?? 0), net_amount: amount, created_by: t.created_by,
    });
    await note(`Versé automatiquement à +${t.recipient_phone}`);
  } catch (e) {
    console.error("auto payout:", e);
    await note("Versement automatique non confirmé : vérifiez dans l'historique puis décaissez à la main si besoin.");
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("ok");
  const raw = await req.text();
  const received = (req.headers.get("X-SebPay-Signature") ?? "").toLowerCase().replace(/^sha256=/, "");

  const { data: keys } = await db.from("sebpay_keys").select("secret_key").eq("id", 1).maybeSingle();
  if (!keys) return new Response("config", { status: 500 });
  if (!safeEqual(received, await hmacHex(keys.secret_key, raw))) {
    return new Response("invalid signature", { status: 401 });
  }

  let evt: Record<string, unknown>;
  try {
    evt = JSON.parse(raw);
  } catch {
    return new Response("bad json", { status: 400 });
  }

  const status = normStatus(evt.status);
  const reference = String(evt.external_reference ?? "");
  if (status !== "pending" && reference) {
    const now = new Date().toISOString();
    const patch: Record<string, unknown> = { status, updated_at: now };
    const { data: t } = await db.from(T).select("*").eq("external_reference", reference).maybeSingle();
    if (t && t.status === "pending") {
      if (evt.fee_amount != null) {
        patch.sebpay_fee = Number(evt.fee_amount);
        if (t.commission_total != null) patch.my_commission = Number(t.commission_total) - Number(evt.fee_amount);
      }
      if (status === "rejected" && t.type === "payout" && !reference.startsWith("REF-")) {
        patch.refund_note = "Wallet recrédité automatiquement";
      }
      await db.from(T).update(patch).eq("id", t.id).eq("status", "pending"); // idempotent
      if (status === "approved" && t.type === "collect") await autoPayout({ ...t, ...patch });
      if (reference.startsWith("REF-")) {
        if (status === "approved") {
          await db.from(T).update({ status: "refunded", refunded_at: now, refund_note: "Remboursé", updated_at: now })
            .eq("refund_reference", reference);
        } else {
          await db.from(T).update({ refund_reference: null, refund_note: "Remboursement refusé : réessayez", updated_at: now })
            .eq("refund_reference", reference);
        }
      }
    }
  }
  return new Response("ok");
});
