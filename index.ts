// Fonction Edge Supabase : "sebpay"  (vérification JWT ACTIVÉE)
// Lit les clés dans sebpay_keys (service role) et appelle l'API SebPay.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });
const T = "sebpay_transactions";

// Tous les pays du monde : ISO|indicatif|devise (aligné avec Countries.java).
// Que SebPay accepte un pays est décidé par SebPay lui-même (action "operators"), pas par cette table.
const RAW_COUNTRIES = `AF|93|AFN
ZA|27|ZAR
AL|355|ALL
DZ|213|DZD
DE|49|EUR
AD|376|EUR
AO|244|AOA
AG|1|XCD
SA|966|SAR
AR|54|ARS
AM|374|AMD
AU|61|AUD
AT|43|EUR
AZ|994|AZN
BS|1|BSD
BH|973|BHD
BD|880|BDT
BB|1|BBD
BE|32|EUR
BZ|501|BZD
BJ|229|XOF
BT|975|BTN
BY|375|BYN
MM|95|MMK
BO|591|BOB
BA|387|BAM
BW|267|BWP
BR|55|BRL
BN|673|BND
BG|359|BGN
BF|226|XOF
BI|257|BIF
KH|855|KHR
CM|237|XAF
CA|1|CAD
CV|238|CVE
CL|56|CLP
CN|86|CNY
CY|357|EUR
CO|57|COP
KM|269|KMF
CG|242|XAF
CD|243|CDF
KP|850|KPW
KR|82|KRW
CR|506|CRC
CI|225|XOF
HR|385|EUR
CU|53|CUP
DK|45|DKK
DJ|253|DJF
DM|1|XCD
EG|20|EGP
AE|971|AED
EC|593|USD
ER|291|ERN
ES|34|EUR
EE|372|EUR
SZ|268|SZL
US|1|USD
ET|251|ETB
FJ|679|FJD
FI|358|EUR
FR|33|EUR
GA|241|XAF
GM|220|GMD
GE|995|GEL
GH|233|GHS
GR|30|EUR
GD|1|XCD
GT|502|GTQ
GN|224|GNF
GW|245|XOF
GQ|240|XAF
GY|592|GYD
HT|509|HTG
HN|504|HNL
HU|36|HUF
MH|692|USD
SB|677|SBD
IN|91|INR
ID|62|IDR
IQ|964|IQD
IR|98|IRR
IE|353|EUR
IS|354|ISK
IL|972|ILS
IT|39|EUR
JM|1|JMD
JP|81|JPY
JO|962|JOD
KZ|7|KZT
KE|254|KES
KG|996|KGS
KI|686|AUD
KW|965|KWD
LA|856|LAK
LS|266|LSL
LV|371|EUR
LB|961|LBP
LR|231|LRD
LY|218|LYD
LI|423|CHF
LT|370|EUR
LU|352|EUR
MK|389|MKD
MG|261|MGA
MY|60|MYR
MW|265|MWK
MV|960|MVR
ML|223|XOF
MT|356|EUR
MA|212|MAD
MU|230|MUR
MR|222|MRU
MX|52|MXN
FM|691|USD
MD|373|MDL
MC|377|EUR
MN|976|MNT
ME|382|EUR
MZ|258|MZN
NA|264|NAD
NR|674|AUD
NP|977|NPR
NI|505|NIO
NE|227|XOF
NG|234|NGN
NO|47|NOK
NZ|64|NZD
OM|968|OMR
UG|256|UGX
UZ|998|UZS
PK|92|PKR
PW|680|USD
PS|970|ILS
PA|507|PAB
PG|675|PGK
PY|595|PYG
NL|31|EUR
PE|51|PEN
PH|63|PHP
PL|48|PLN
PT|351|EUR
QA|974|QAR
CF|236|XAF
DO|1|DOP
CZ|420|CZK
RO|40|RON
GB|44|GBP
RU|7|RUB
RW|250|RWF
KN|1|XCD
LC|1|XCD
SM|378|EUR
VC|1|XCD
SV|503|USD
WS|685|WST
ST|239|STN
SN|221|XOF
RS|381|RSD
SC|248|SCR
SL|232|SLE
SG|65|SGD
SK|421|EUR
SI|386|EUR
SO|252|SOS
SD|249|SDG
SS|211|SSP
LK|94|LKR
SE|46|SEK
CH|41|CHF
SR|597|SRD
SY|963|SYP
TJ|992|TJS
TZ|255|TZS
TD|235|XAF
TH|66|THB
TL|670|USD
TG|228|XOF
TO|676|TOP
TT|1|TTD
TN|216|TND
TM|993|TMT
TR|90|TRY
TV|688|AUD
UA|380|UAH
UY|598|UYU
VU|678|VUV
VA|379|EUR
VE|58|VES
VN|84|VND
YE|967|YER
ZM|260|ZMW
ZW|263|USD
TW|886|TWD
XK|383|EUR
HK|852|HKD
MO|853|MOP
PR|1|USD
GL|299|DKK
NC|687|XPF
PF|689|XPF
RE|262|EUR
YT|262|EUR
GP|590|EUR
MQ|596|EUR
GF|594|EUR
BM|1|BMD
KY|1|KYD
GI|350|GIP
FO|298|DKK`;
const COUNTRIES: Record<string, { dial: string; currency: string }> = Object.fromEntries(
  RAW_COUNTRIES.split("\n").map((l) => {
    const [iso, dial, currency] = l.split("|");
    return [iso, { dial, currency }];
  }),
);
// Minimum local : seulement là où il a été constaté (XOF / XAF : SebPay refuse < 300). Ailleurs, c'est SebPay qui décide
// (erreur AMOUNT_TOO_LOW avec errors.min_amount, déjà traduite en message clair) : on ne devine pas un minimum.
const minPayout = (currency: string) => (currency === "XOF" || currency === "XAF" ? 300 : 1);

const reply = (body: unknown) =>
  new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
const ok = (data: unknown) => reply({ ok: true, data });
const fail = (error: string) => reply({ ok: false, error });

class SebpayError extends Error {
  constructor(message: string, public status: number, public code?: string) {
    super(message);
  }
}

// Codes d'erreur documentés par SebPay (errors.code) -> message clair en français.
const BLOCKING_CODES = new Set([
  "FEATURE_DISABLED", "COUNTRY_UNAVAILABLE", "OPERATOR_MAINTENANCE", "AGGREGATOR_MAINTENANCE",
  "ACCOUNT_PAYIN_DISABLED", "ACCOUNT_PAYOUT_DISABLED", "ACCOUNT_SUSPENDED", "KYC_REQUIRED", "WRONG_ENVIRONMENT",
  "IP_NOT_ALLOWED", "IP_PENDING_APPROVAL", "IP_REJECTED", "IP_DISABLED", "PAYOUTS_LOCKED", "PAYOUTS_FROZEN",
]);
function friendlyError(code: string | undefined, errors: Record<string, unknown> | undefined): string | null {
  switch (code) {
    case "FEATURE_DISABLED": case "COUNTRY_UNAVAILABLE": case "OPERATOR_MAINTENANCE": case "AGGREGATOR_MAINTENANCE":
      return "Service, pays ou réseau temporairement indisponible chez le fournisseur : réessayez plus tard ou choisissez un autre réseau.";
    case "ACCOUNT_PAYOUT_DISABLED": return "Les décaissements ne sont pas autorisés sur ce compte pour ce pays : contactez le support SebPay.";
    case "ACCOUNT_PAYIN_DISABLED": return "Les encaissements ne sont pas autorisés sur ce compte pour ce pays : contactez le support SebPay.";
    case "INSUFFICIENT_BALANCE": return "Solde insuffisant sur le wallet principal pour ce décaissement.";
    case "PAYOUTS_LOCKED": return `Décaissements bloqués après 5 refus d'affilée${errors?.blocked_until ? ` jusqu'à ${errors.blocked_until}` : " jusqu'à la fin de la journée"}.`;
    case "PAYOUTS_FROZEN": return "Décaissements suspendus le temps d'une vérification : contactez le support SebPay.";
    case "ACCOUNT_SUSPENDED": return "Compte SebPay suspendu : contactez le support SebPay.";
    case "KYC_REQUIRED": return "Le KYC du compte SebPay n'est pas validé (clés live).";
    case "WRONG_ENVIRONMENT": return "Les clés enregistrées sont de l'autre environnement (test / live).";
    case "MONTHLY_LIMIT_REACHED": return "Limite mensuelle de réception du compte atteinte : contactez le support SebPay.";
    case "AMOUNT_TOO_LOW": return `Montant trop faible${errors?.min_amount ? ` : minimum ${errors.min_amount} ${errors.currency ?? ""}` : ""}.`;
    case "RATE_LIMITED": return "Trop de requêtes : réessayez dans quelques secondes.";
    case "OTP_REQUIRED": return "Ce réseau exige un code OTP.";
    case "PAYMENT_DECLINED": return "Paiement refusé par le réseau : vérifiez le numéro et le réseau.";
    case "PAYOUT_DECLINED": return "Décaissement refusé par le réseau : vérifiez le numéro et le réseau.";
    case "REQUEST_IN_PROGRESS": return "Cette demande est déjà en cours de traitement chez le fournisseur : ne la renvoyez pas, vérifiez l'Historique dans quelques minutes.";
    case "EXTERNAL_REFERENCE_CONFLICT": return "Cette référence existe déjà chez le fournisseur avec d'autres paramètres : ne renvoyez rien et vérifiez l'Historique.";
    case "INTERNAL_ERROR": return "Erreur interne chez le fournisseur : le résultat est incertain, vérifiez l'Historique avant de réessayer.";
    default: return null;
  }
}

type Keys = { base_url: string; public_key: string; secret_key: string; relay_url?: string | null };

async function loadKeys(): Promise<Keys> {
  const { data, error } = await db.from("sebpay_keys").select("*").eq("id", 1).maybeSingle();
  if (error || !data) throw new Error("Configuration de paiement introuvable.");
  return data as Keys;
}

// Les fonctions Edge n'ont PAS d'adresse IP fixe : SebPay (qui n'accepte que des IP autorisées) les refuse.
// Solution : passer par un petit relais à IP fixe (voir relay/server.js, hébergé sur Render, dont la plage
// 74.220.x.x est déjà autorisée chez SebPay). L'adresse du relais est dans sebpay_keys.relay_url (ou le secret SEBPAY_RELAY_URL).
const RELAY_TOKEN = Deno.env.get("SEBPAY_RELAY_TOKEN") ?? ""; // facultatif

// deno-lint-ignore no-explicit-any
async function sebpay(keys: Keys, method: string, path: string, body?: unknown, attempt = 0): Promise<any> {
  const RELAY_URL = String(keys.relay_url || Deno.env.get("SEBPAY_RELAY_URL") || "").trim().replace(/\/+$/, "");
  const authHeaders = { "X-Public-Key": keys.public_key, "X-Secret-Key": keys.secret_key };
  const res = RELAY_URL
    ? await fetch(`${RELAY_URL}/relay`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${RELAY_TOKEN}`, ...authHeaders },
      body: JSON.stringify({ method, path, body: body ?? null }),
      signal: AbortSignal.timeout(55_000), // Render gratuit : le premier appel peut réveiller le service (~30 s)
    })
    : await fetch(`${keys.base_url}${path}`, {
      method,
      headers: { "Content-Type": "application/json", ...authHeaders },
      body: body ? JSON.stringify(body) : undefined,
    });
  const j = await res.json().catch(() => ({}));
  if (!res.ok || j.success === false) {
    const code: string | undefined = typeof j?.errors?.code === "string" ? j.errors.code : undefined;
    // Trop de requêtes : la demande n'a PAS été traitée, on peut la rejouer (même référence) après le délai demandé (Retry-After).
    if (code === "RATE_LIMITED" && attempt < 1) {
      const ra = Number(res.headers.get("Retry-After") ?? j?.errors?.retry_after ?? 2);
      const secs = Math.min(Math.max(Number.isFinite(ra) ? ra : 2, 1), 10);
      await new Promise((r) => setTimeout(r, secs * 1000));
      return sebpay(keys, method, path, body, attempt + 1);
    }
    let msg = friendlyError(code, j?.errors) ?? String(j.message || j.error || `Erreur de paiement (HTTP ${res.status})`).replace(/seb\s?pay/gi, "Kouamépay");
    if (code?.startsWith("IP_") || (/adresse\s+ip|ip address|not authorized|non autoris/i.test(msg) && /ip/i.test(msg))) {
      if (RELAY_URL) {
        msg += " — l'IP du relais n'est pas autorisée : ajoutez sa plage (voir /ip du relais) dans « Adresses IP autorisées ».";
      } else {
        // Sans relais : on indique l'adresse vue par SebPay (elle change d'un appel à l'autre, d'où le relais).
        const ip = await serverIp();
        if (ip) msg += ` — IP de ce serveur : ${ip} (non fixe : renseignez sebpay_keys.relay_url)`;
      }
    }
    throw new SebpayError(msg, res.status, code);
  }
  return j.data !== undefined ? j.data : j;
}

/** Adresse IP publique avec laquelle cette fonction sort sur internet (celle que SebPay voit). */
async function serverIp(): Promise<string> {
  for (const url of ["https://api.ipify.org", "https://ifconfig.me/ip", "https://icanhazip.com"]) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(5000) });
      const t = (await r.text()).trim();
      if (/^[0-9a-fA-F:.]{7,45}$/.test(t)) return t;
    } catch (_) { /* on essaie le service suivant */ }
  }
  return "";
}

// ---- Paiements BNB pour les pays que SebPay n'accepte pas.
// MODE MANUEL : le client envoie le BNB à VOTRE adresse (BNB_ADDRESS), joint une capture d'écran 2 minutes après,
// puis l'administrateur vérifie dans son wallet, envoie l'argent à la main et marque « Payé ».
// (Les anciennes fonctions CryptAPI ci-dessous ne servent plus à créer les paiements.)
const CRYPTAPI = "https://api.cryptapi.io";
const BNB_ADDRESS = Deno.env.get("BNB_ADDRESS") ?? "0x13108641DcfaBea3b2e8dEd4d35B8f49606f5A17";
const TC = "sebpay_crypto";
const PROOF_BUCKET = "sebpay-proofs";
const PROOF_DELAY_MS = 120_000; // les preuves peuvent être envoyées 2 minutes après la création du paiement

async function cryptapi(path: string) {
  const res = await fetch(`${CRYPTAPI}${path}`);
  const j = await res.json().catch(() => ({}));
  if (!res.ok || j.status === "error") {
    throw new Error(j.error || j.message || `Erreur CryptAPI (HTTP ${res.status})`);
  }
  return j;
}

// Le nom exact du BNB chez CryptAPI est lu dans /info/ (ex. "bep20/bnb"), pour ne rien deviner.
async function bnbTicker(): Promise<string> {
  const forced = Deno.env.get("CRYPTAPI_TICKER");
  if (forced) return forced;
  const info = await cryptapi("/info/");
  for (const chain of ["bsc", "bep20", "bnb-smart-chain"]) {
    if (info?.[chain]?.bnb) return `${chain}/bnb`;
  }
  if (info?.bnb) return "bnb";
  throw new Error("Le BNB n'est pas disponible chez CryptAPI pour le moment.");
}

async function usdToBnb(usd: number, ticker: string): Promise<number> {
  let v: unknown;
  try {
    v = (await cryptapi(`/convert/?value=${usd}&from=usd&to=bnb`)).value_coin;
  } catch {
    v = (await cryptapi(`/${ticker}/convert/?value=${usd}&from=usd`)).value_coin;
  }
  const n = Number(v);
  if (!(n > 0)) throw new Error("Conversion USD → BNB impossible.");
  return Math.ceil(n * 1e6) / 1e6; // arrondi au-dessus, 6 décimales
}

// Prix du BNB en USD : sources publiques sans clé, la suivante prend le relais si l'une échoue.
async function bnbPriceUsd(): Promise<number> {
  const get = async (url: string) => (await fetch(url, { signal: AbortSignal.timeout(6000) })).json();
  const sources: Array<() => Promise<number>> = [
    async () => Number((await get("https://api.binance.com/api/v3/ticker/price?symbol=BNBUSDT"))?.price),
    async () => Number((await get("https://data-api.binance.vision/api/v3/ticker/price?symbol=BNBUSDT"))?.price),
    async () => Number((await get("https://api.coingecko.com/api/v3/simple/price?ids=binancecoin&vs_currencies=usd"))?.binancecoin?.usd),
  ];
  for (const src of sources) {
    try {
      const n = await src();
      if (n > 0) return n;
    } catch (_) { /* source suivante */ }
  }
  throw new Error("Prix du BNB indisponible pour le moment, réessayez dans un instant.");
}

async function usdToBnbManual(usd: number): Promise<number> {
  const n = usd / (await bnbPriceUsd());
  if (!(n > 0)) throw new Error("Conversion USD → BNB impossible.");
  return Math.ceil(n * 1e6) / 1e6; // arrondi au-dessus, 6 décimales
}

// ---- Commission : un taux total (la vôtre + celle de SebPay), réglé dans sebpay_settings
// Un paiement BNB non reçu reste « en attente de paiement » 24 h au maximum.
const CRYPTO_TTL_MS = 24 * 60 * 60 * 1000;
function withExpiry<T extends Record<string, unknown>>(r: T) {
  const created = new Date(String(r.created_at)).getTime();
  return {
    ...r,
    expires_at: new Date(created + CRYPTO_TTL_MS).toISOString(),
    expired: r.status === "pending" && Date.now() > created + CRYPTO_TTL_MS,
  };
}

async function loadSettings() {
  const { data } = await db.from("sebpay_settings").select("*").eq("id", 1).maybeSingle();
  return {
    percent: Number(data?.commission_total_percent ?? 5),
    mode: (data?.commission_mode ?? "deduct") as string,
  };
}

// charge = montant réellement envoyé à SebPay ; net = montant qui vous revient / revient au bénéficiaire
function plan(type: "collect" | "payout", amount: number, percent: number, mode: string) {
  const commission = Math.round((amount * percent) / 100);
  if (mode === "add") {
    return { charge: type === "collect" ? amount + commission : amount, commission, net: amount };
  }
  return { charge: type === "payout" ? amount - commission : amount, commission, net: amount - commission };
}

// Taux de change (service public sans clé) : seulement si la devise du bénéficiaire diffère de celle du paiement.
// Bénin -> Côte d'Ivoire : même devise (XOF), taux 1. Le taux est figé à l'encaissement et enregistré avec la transaction.
async function fxRate(from: string, to: string): Promise<number> {
  if (from === to) return 1;
  const res = await fetch(`https://open.er-api.com/v6/latest/${encodeURIComponent(from)}`, { signal: AbortSignal.timeout(8000) });
  const j = await res.json().catch(() => ({}));
  const r = Number(j?.rates?.[to]);
  if (!res.ok || !(r > 0)) throw new Error(`Taux de change ${from} → ${to} indisponible, réessayez.`);
  return r;
}

// Refus net = SebPay n'a rien créé (la référence reste libre). Erreur 5xx (hors 503), requête déjà en cours ou conflit de référence
// = résultat INCERTAIN : on garde la référence et on ne renvoie jamais avec une nouvelle (doc SebPay : « ne le renvoyez jamais avec une nouvelle référence »).
const isClearRefusal = (e: unknown) =>
  e instanceof SebpayError && e.code !== "REQUEST_IN_PROGRESS" && e.code !== "EXTERNAL_REFERENCE_CONFLICT" && (e.status < 500 || e.status === 503);

/** Montants réels renvoyés par SebPay (taux de change et frais de SebPay, pas ceux d'un service tiers) : recipient_amount, total_deducted. */
// deno-lint-ignore no-explicit-any
async function saveAmounts(reference: string, d: any) {
  const patch: Record<string, unknown> = {};
  if (d?.recipient_amount != null && Number.isFinite(Number(d.recipient_amount))) patch.recipient_amount = Number(d.recipient_amount);
  if (d?.total_deducted != null && Number.isFinite(Number(d.total_deducted))) patch.total_deducted = Number(d.total_deducted);
  if (!Object.keys(patch).length) return;
  const { error } = await db.from(T).update(patch).eq("external_reference", reference);
  if (error) console.error("saveAmounts:", error.message);
}

/** Frais réels de l'encaissement (GET /c/calculate-fee, prix exact du compte) : la réponse d'une collecte ne contient pas fee_amount. */
async function feePercent(keys: Keys, kind: "collection" | "payout", iso: string): Promise<number | null> {
  try {
    const q = kind === "collection" ? "source_country" : "destination_country";
    const d = await sebpay(keys, "GET", `/c/calculate-fee?amount=10000&${q}=${encodeURIComponent(iso.toLowerCase())}&transaction_type=${kind}`);
    if (d?.fee_percentage == null) return null; // part fixe : pas de pourcentage exploitable
    const pct = Number(d.fee_percentage);
    return Number.isFinite(pct) ? pct : null;
  } catch (_) {
    return null;
  }
}

// Versement automatique : quand un encaissement réussit, le net est envoyé au numéro qui reçoit.
// La réservation (payout_reference) garantit un seul versement, même si le webhook et le suivi arrivent en même temps.
// La ligne du versement est enregistrée AVANT l'appel : si la réponse se perd, la référence reste connue
// (webhook et suivi la retrouvent) et une relance réutilise la MÊME référence. On ne réessaie jamais tout seul.
// deno-lint-ignore no-explicit-any
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
  const floor = minPayout(rCurrency);
  if (!(amount >= floor)) return void await note(`Montant trop faible pour le versement automatique (min ${floor} ${rCurrency}) : l'administrateur doit décaisser.`);
  const row = {
    type: "payout", external_reference: reference, sebpay_id: null, status: "pending", amount, currency: rCurrency,
    phone: t.recipient_phone, operator: t.recipient_operator, country: rCountry,
    recipient_name: t.recipient_name || "Bénéficiaire", sebpay_fee: 0, net_amount: amount, created_by: t.created_by,
  };
  const ins = await db.from(T).insert(row);
  if (ins.error) {
    console.error("auto payout insert:", ins.error.message);
    return void await note("Versement refusé : enregistrement impossible, rien n'a été envoyé.");
  }
  try {
    const d = await sebpay(await loadKeys(), "POST", "/payouts", {
      recipient_name: row.recipient_name, phone: t.recipient_phone, operator: t.recipient_operator,
      country: rCountry, amount, currency: rCurrency, external_reference: reference,
      callback_url: `${SUPABASE_URL}/functions/v1/sebpay-webhook`,
    });
    await db.from(T).update({
      sebpay_id: d?.transaction_id ?? null, sebpay_fee: Number(d?.fee_amount ?? 0), updated_at: new Date().toISOString(),
    }).eq("external_reference", reference);
    await saveAmounts(reference, d);
    const st = normStatus(d?.status);
    if (st !== "pending") await db.from(T).update({ status: st, updated_at: new Date().toISOString() }).eq("external_reference", reference).eq("status", "pending");
    await note(`Versé automatiquement à +${t.recipient_phone}`);
  } catch (e) {
    console.error("auto payout:", e);
    const why = String((e as Error)?.message ?? e).slice(0, 220);
    if (isClearRefusal(e)) {
      await db.from(T).delete().eq("external_reference", reference); // rien créé chez SebPay
      await note(`Versement refusé : ${why}`);
    } else {
      await note(`Versement automatique non confirmé (${why}) : vérifiez dans l'historique puis décaissez à la main si besoin.`);
    }
  }
}

// Remet une transaction « pending » d'équerre avec SebPay (GET /collections|/payouts : SebPay revérifie auprès de l'opérateur).
// Sert au suivi de l'appli ET au webhook (confirmation par l'API avant de livrer / verser, comme la doc le recommande).
// deno-lint-ignore no-explicit-any
async function syncStatus(t: Record<string, any>): Promise<Record<string, any>> {
  if (t.status !== "pending") return t;
  const path = `/${t.type === "collect" ? "collections" : "payouts"}/${encodeURIComponent(t.external_reference)}`;
  let d;
  try {
    d = await sebpay(await loadKeys(), "GET", path);
  } catch (e) {
    // 404 sur une transaction jamais confirmée, vieille de plus de 3 min : la demande n'est jamais arrivée chez SebPay.
    if (e instanceof SebpayError && e.status === 404 && !t.sebpay_id && Date.now() - new Date(t.created_at).getTime() > 180_000) {
      await db.from(T).update({ status: "rejected", updated_at: new Date().toISOString() }).eq("id", t.id).eq("status", "pending");
      await afterStatusChange(t.external_reference, "rejected");
      const { data: gone } = await db.from(T).select("*").eq("id", t.id).maybeSingle();
      return gone ?? t;
    }
    throw e;
  }
  const status = normStatus(d?.status);
  if (status === "pending") return t;
  const patch: Record<string, unknown> = { status, updated_at: new Date().toISOString() };
  if (d?.transaction_id && !t.sebpay_id) patch.sebpay_id = d.transaction_id;
  if (d?.fee_amount != null) {
    patch.sebpay_fee = Number(d.fee_amount);
    // Pour un encaissement dont les frais sont déjà estimés (calculate-fee), la marge tient déjà compte des frais du versement : on n'y touche pas.
    if (t.commission_total != null && !(t.type === "collect" && Number(t.sebpay_fee) > 0)) patch.my_commission = Number(t.commission_total) - Number(d.fee_amount);
  }
  if (status === "rejected" && t.type === "payout" && !t.external_reference.startsWith("REF-")) {
    patch.refund_note = "Wallet recrédité automatiquement";
  }
  await db.from(T).update(patch).eq("id", t.id).eq("status", "pending"); // idempotent
  await saveAmounts(t.external_reference, d);
  await afterStatusChange(t.external_reference, status);
  const { data: fresh } = await db.from(T).select("*").eq("id", t.id).maybeSingle();
  const out = fresh ?? { ...t, ...patch };
  if (status === "approved" && t.type === "collect") await autoPayout(out);
  const { data: last } = await db.from(T).select("*").eq("id", t.id).maybeSingle();
  return last ?? out;
}

// Suivi en direct : pour un encaissement réussi, joint l'état RÉEL du versement (ligne OUT-…), revérifié auprès de SebPay s'il est encore « pending ».
// deno-lint-ignore no-explicit-any
async function withPayoutLeg(t: Record<string, any>): Promise<Record<string, any>> {
  if (t.type !== "collect" || t.status !== "approved" || !t.payout_reference) return t;
  try {
    const { data: leg } = await db.from(T).select("*").eq("external_reference", t.payout_reference).maybeSingle();
    if (!leg) return t;
    // deno-lint-ignore no-explicit-any
    const fresh: Record<string, any> = await syncStatus(leg).catch(() => leg);
    return { ...t, payout_leg: { status: fresh.status, created_at: fresh.created_at, updated_at: fresh.updated_at } };
  } catch (_e) {
    return t;
  }
}

// Un versement est « à reprendre » quand l'encaissement a réussi mais que l'argent n'est pas arrivé chez le bénéficiaire.
// pay = ligne du versement automatique, man = ligne du décaissement fait à la main par l'administrateur.
// check = résultat incertain (réseau coupé après l'envoi) : l'administrateur doit vérifier l'historique avant de décaisser.
// deno-lint-ignore no-explicit-any
function classifyPayout(t: Record<string, any>, pay: Record<string, any> | null, man: Record<string, any> | null): { reason: string; check: boolean } | null {
  if (t.type !== "collect" || t.status !== "approved" || !t.recipient_phone || !t.payout_reference || t.refund_reference) return null;
  if (t.manual_payout_at) {
    return man && man.status === "rejected"
      ? { reason: "Le décaissement manuel a été refusé par le réseau (le wallet a été recrédité).", check: false }
      : null;
  }
  if (pay && pay.status === "approved") return null; // le versement automatique a bien abouti
  const note = String(t.payout_note ?? "");
  const age = Date.now() - new Date(String(t.updated_at ?? t.created_at)).getTime();
  if (note === "Décaissement manuel en cours") {
    // en cours : on ne propose rien (évite un double décaissement), sauf s'il est resté sans réponse
    return age > 10 * 60_000 ? { reason: "Décaissement manuel resté sans confirmation.", check: true } : null;
  }
  if (note.startsWith("Décaissement non confirmé")) return { reason: note, check: true };
  if (note.startsWith("Versement refusé") || note.startsWith("Montant trop faible")) return { reason: note, check: false };
  if (note.startsWith("Versement automatique non confirmé")) return { reason: note, check: true };
  if (pay && pay.status === "rejected") {
    return { reason: "Versement refusé par le réseau du bénéficiaire (le wallet a été recrédité).", check: false };
  }
  if (note === "Versement en cours" && !pay && age > 10 * 60_000) return { reason: "Versement resté sans confirmation.", check: true };
  return null;
}

// Lignes de versement liées à un encaissement (automatique et manuel), pour juger de leur statut réel
// deno-lint-ignore no-explicit-any
async function payRows(t: Record<string, any>) {
  const refs = [t.payout_reference, t.manual_payout_reference].filter(Boolean);
  // deno-lint-ignore no-explicit-any
  const map = new Map<string, Record<string, any>>();
  if (refs.length) {
    const { data } = await db.from(T).select("external_reference,status").in("external_reference", refs);
    for (const r of data ?? []) map.set(String(r.external_reference), r);
  }
  return { pay: map.get(String(t.payout_reference)) ?? null, man: map.get(String(t.manual_payout_reference)) ?? null };
}

/** Teste que SebPay répond pour une fonctionnalité (encaissement ou décaissement), en lecture seule :
 *  on lit une référence inexistante (GET /collections/{ref} ou /payouts/{ref}) : 404 = la route répond, les clés, l'IP et le KYC sont bons. */
async function featureProblem(keys: Keys, path: string, label: string): Promise<string | null> {
  try {
    await sebpay(keys, "GET", path);
    return null;
  } catch (e) {
    const msg = String((e as Error).message ?? "").slice(0, 160);
    if (e instanceof SebpayError) {
      if (e.status === 404) return null; // attendu : référence de test inconnue
      if ((e.code && BLOCKING_CODES.has(e.code)) || e.status === 401 || e.status === 403 || e.status >= 500) return `${label} indisponible : ${msg}`;
      return null; // 400/422/429 : pas une panne de la fonctionnalité
    }
    return `${label} indisponible : ${msg}`; // réseau / relais injoignable
  }
}

function normStatus(s: unknown): "pending" | "approved" | "rejected" {
  const v = String(s ?? "pending").toLowerCase();
  if (["approved", "success", "successful", "completed"].includes(v)) return "approved";
  if (["rejected", "failed", "declined", "cancelled", "canceled", "expired"].includes(v)) return "rejected";
  return "pending";
}

function normalizePhone(raw: unknown, country: string): string | null {
  const c = COUNTRIES[country];
  if (!c) return null;
  const input = String(raw ?? "").trim();
  const intl = input.startsWith("+") || input.startsWith("00");
  let d = input.replace(/\D/g, "");
  if (d.startsWith("00")) d = d.slice(2);
  // On retire l'indicatif s'il est annoncé (+ / 00), ou si le reste est assez long pour être un numéro complet.
  if (d.startsWith(c.dial) && (intl || d.length - c.dial.length >= 8)) d = d.slice(c.dial.length);
  if (country === "BJ") {
    if (d.length === 8) d = "01" + d; // Bénin : préfixe 01 + 8 chiffres
    if (!(d.length === 10 && d.startsWith("01"))) return null;
  } else if (d.length < 6 || d.length > 12) {
    return null;
  }
  return c.dial + d;
}

// Quand un remboursement (REF-...) se termine, on met à jour l'encaissement d'origine
async function afterStatusChange(reference: string, status: string) {
  if (!reference.startsWith("REF-")) return;
  const now = new Date().toISOString();
  if (status === "approved") {
    await db.from(T).update({ status: "refunded", refunded_at: now, refund_note: "Remboursé", updated_at: now })
      .eq("refund_reference", reference);
  } else if (status === "rejected") {
    await db.from(T).update({ refund_reference: null, refund_note: "Remboursement refusé : réessayez", updated_at: now })
      .eq("refund_reference", reference);
  }
}


// ---- Service client : FAQ écrite à l'avance, puis IA gratuite, puis administrateur
const SUPPORT_TABLE = "sebpay_support";
const ONLINE_WINDOW_MS = 90_000; // l'admin est « en ligne » s'il a donné signe de vie il y a moins de 90 s
const ESCALATE_REPLY =
  "Votre problème est pris en compte, veuillez patienter, l'administrateur va vous répondre dans un instant.";

const SUPPORT_PROMPT = `Tu es le service client de l'application Kouamépay. Ne mentionne jamais le nom d'un prestataire de paiement ou d'un fournisseur tiers : parle uniquement de Kouamépay. Réponds toujours dans la langue du client (français par défaut), en 2 à 5 phrases courtes, de façon claire et polie.
Ce que tu sais de Kouamépay :
- Onglet « Encaisser » : on choisit le pays, le réseau Mobile Money (liste fournie par l'application), le numéro qui paie (débité de son compte Mobile Money), le numéro qui reçoit l'argent (avec son réseau et le nom du bénéficiaire) et le montant ; une fois le paiement réussi, le net est versé automatiquement au numéro qui reçoit. Le client confirme le paiement sur son téléphone (parfois avec un code OTP). Le statut passe de « En attente » à « Réussi » ou « Refusé ».
- Onglet « Décaisser » (administrateur seulement) : envoi d'argent depuis le wallet Kouamépay vers un numéro Mobile Money. Si le décaissement est refusé, le wallet est recrédité automatiquement.
- Pays non pris en charge en Mobile Money : l'application propose le paiement en BNB (BNB Smart Chain) : on saisit un montant en USD, l'application affiche le montant équivalent dans la monnaie du pays qui reçoit, puis l'adresse de paiement et le montant exact en BNB à envoyer.
- Commission : 5 % au total, indiquée sur le reçu avant confirmation. Le client BNB indique aussi le réseau et le numéro qui recevront l'argent ; 2 minutes après, il envoie ses preuves (images) à l'administrateur, qui décaisse à la main puis marque « Payé » ; le client peut signaler « argent non reçu » depuis l'Historique.
- Onglet « Historique » : les dernières transactions et leur statut. Un encaissement réussi peut être remboursé par l'administrateur.
- Un paiement « En attente » peut durer quelques minutes : conseille de patienter et de vérifier le statut dans l'Historique.
Règles strictes :
- Ne demande JAMAIS de mot de passe, de code secret ni de clé.
- Les questions générales (comment envoyer ou recevoir de l'argent, d'un pays à un autre, quels pays, la commission, comment suivre un paiement) reçoivent TOUJOURS une explication claire : ne réponds jamais ESCALADE pour elles.
- N'invente rien : si tu n'es pas sûr, ou si la demande concerne une transaction DÉJÀ effectuée et bloquée, un remboursement, de l'argent débité sans résultat, un compte ou une action de l'administrateur, réponds UNIQUEMENT par le mot : ESCALADE
- Ne promets jamais un remboursement ou un délai.`;

type Provider = { name: string; url: string; model?: string; key?: string; pick?: (ids: string[]) => string | undefined };

// Service IA gratuit AVEC clé (plus fiable que les services anonymes). Secrets Supabase à définir :
//   AI_API_KEY (obligatoire pour l'activer), AI_API_URL et AI_MODEL (facultatifs : Groq par défaut).
const AI_API_KEY = Deno.env.get("AI_API_KEY") ?? "";
const KEYED: Provider[] = AI_API_KEY
  ? [{
    name: "ia-cle",
    url: Deno.env.get("AI_API_URL") ?? "https://api.groq.com/openai/v1/chat/completions",
    model: Deno.env.get("AI_MODEL") ?? "llama-3.1-8b-instant",
    key: AI_API_KEY,
  }]
  : [];

const PROVIDERS: Provider[] = [
  ...KEYED,
  { name: "pollinations", url: "https://text.pollinations.ai/openai", model: "openai" },
  { name: "llm7", url: "https://api.llm7.io/v1/chat/completions", model: "default" },
  {
    name: "kilo",
    url: "https://api.kilo.ai/api/gateway/chat/completions",
    model: "kilo-auto/free",
    pick: (ids) => ids.find((i) => /free/i.test(i)),
  },
  {
    name: "ovh",
    url: "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions",
    pick: (ids) => ids.find((i) => /llama.*instruct/i.test(i)) ?? ids.find((i) => /qwen.*instruct/i.test(i)) ?? ids[0],
  },
];

async function listModels(chatUrl: string): Promise<string[]> {
  try {
    const res = await fetch(chatUrl.replace(/\/chat\/completions$/, "/models"), { signal: AbortSignal.timeout(8000) });
    if (!res.ok) return [];
    const j = await res.json();
    return (j?.data ?? []).map((m: { id?: string }) => String(m?.id ?? "")).filter(Boolean);
  } catch {
    return [];
  }
}

async function askProvider(p: Provider, model: string, messages: unknown[]): Promise<string | null> {
  const res = await fetch(p.url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${p.key ?? "unused"}` },
    body: JSON.stringify({ model, messages, temperature: 0.3, max_tokens: 400, stream: false }),
    signal: AbortSignal.timeout(14000),
  });
  if (!res.ok) {
    console.error("support IA refus", p.name, res.status); // quota, modèle indisponible… : on passera au service suivant
    return null;
  }
  const j = await res.json().catch(() => null);
  let t = String(j?.choices?.[0]?.message?.content ?? "").replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  if (!t) return null;
  return t.replace(/seb\s?pay/gi, "Kouamépay").slice(0, 1500);
}

/** Réponses écrites à l'avance : gratuites, instantanées, indépendantes de tout service externe. */
function faqAnswer(text: string): string | null {
  const t = text.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  const has = (...w: string[]) => w.some((x) => t.includes(x));
  // Problème sur une transaction déjà faite : on laisse l'administrateur traiter
  if (has("rembours", "pas recu", "pas arrive", "debite", "bloque", "arnaque", "perdu", "probleme")) return null;
  if (has("commission", "frais", "combien ca coute", "tarif"))
    return "La commission Kouamépay est de 5 % au total. Elle est affichée sur le reçu avant que vous confirmiez le paiement.";
  if (has("envoy", "transfer", "transfert", "envoi", "expedi", "encaisser", "payer", "paiement"))
    return "Pour envoyer de l'argent, ouvrez l'onglet « Encaisser » : choisissez le pays et le réseau Mobile Money, saisissez le numéro qui paie, puis le numéro qui reçoit (avec son réseau et le nom du bénéficiaire) et le montant. Confirmez le paiement sur votre téléphone : une fois réussi, l'argent est versé automatiquement au bénéficiaire. Si votre pays n'est pas disponible en Mobile Money, l'application propose le paiement en BNB.";
  if (has("bnb", "crypto"))
    return "Si votre pays n'est pas pris en charge en Mobile Money, l'application propose le paiement en BNB : saisissez un montant en USD, l'application affiche le montant équivalent dans la monnaie du pays qui reçoit, puis l'adresse de paiement et le montant exact en BNB à envoyer. Ensuite, envoyez vos preuves (images) à l'administrateur, qui verse l'argent au bénéficiaire.";
  if (has("statut", "en attente", "historique", "suivre"))
    return "Un paiement « En attente » peut durer quelques minutes. Patientez, puis vérifiez son statut dans l'onglet « Historique » : il passera à « Réussi » ou « Refusé ».";
  return null;
}

/** Essaie les services dans l'ordre : si l'un refuse ou ne répond pas, le suivant prend le relais. */
async function askAI(history: { role: string; content: string }[]): Promise<string | null> {
  const messages = [{ role: "system", content: SUPPORT_PROMPT }, ...history];
  for (const p of PROVIDERS) {
    try {
      let model = p.model;
      let out = model ? await askProvider(p, model, messages) : null;
      if (out === null && p.pick) {
        const picked = p.pick(await listModels(p.url));
        if (picked && picked !== model) out = await askProvider(p, picked, messages);
      }
      if (out !== null) return out;
    } catch (e) {
      console.error("support IA", p.name, e instanceof Error ? e.message : e);
    }
  }
  return null;
}

async function adminOnline(): Promise<boolean> {
  const { data } = await db.from("sebpay_presence").select("admin_last_seen").eq("id", 1).maybeSingle();
  if (!data?.admin_last_seen) return false;
  return Date.now() - new Date(data.admin_last_seen).getTime() < ONLINE_WINDOW_MS;
}

// Conversations à traiter : le dernier message « à traiter » n'a pas encore reçu de réponse de l'admin
async function buildInbox() {
  const { data } = await db.from(SUPPORT_TABLE).select("*").order("created_at", { ascending: false }).limit(600);
  const byUser = new Map<string, { user_id: string; email: string; last_body: string; last_at: string; last_sender: string; pending: boolean; settled: boolean }>();
  for (const m of data ?? []) { // du plus récent au plus ancien
    let c = byUser.get(m.user_id);
    if (!c) {
      c = { user_id: m.user_id, email: m.user_email ?? "", last_body: m.body, last_at: m.created_at, last_sender: m.sender, pending: false, settled: false };
      byUser.set(m.user_id, c);
    }
    if (!c.email && m.user_email) c.email = m.user_email;
    if (c.settled) continue;
    if (m.sender === "admin") c.settled = true; // l'admin a répondu après : rien à traiter plus haut
    else if (m.needs_admin) { c.pending = true; c.settled = true; }
  }
  return [...byUser.values()]
    .map(({ settled: _s, ...c }) => c)
    .sort((a, b) => Number(b.pending) - Number(a.pending) || b.last_at.localeCompare(a.last_at));
}

async function getUser(req: Request) {
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await db.auth.getUser(token);
  if (error || !data.user) return null;
  // deno-lint-ignore no-explicit-any
  let prof: any = null;
  const r1 = await db.from("sebpay_users").select("role,approved").eq("user_id", data.user.id).maybeSingle();
  if (!r1.error) {
    prof = r1.data;
  } else {
    // colonne « approved » absente (partie v20 de supabase.sql pas encore exécutée) : pas de validation exigée
    const r2 = await db.from("sebpay_users").select("role").eq("user_id", data.user.id).maybeSingle();
    prof = r2.data ? { role: r2.data.role, approved: true } : null;
  }
  if (!prof) return null;
  return {
    id: data.user.id as string, role: prof.role as string, email: (data.user.email ?? "") as string,
    approved: prof.approved === true, // toute inscription doit être validée par l'administrateur
  };
}

Deno.serve(async (req) => {
  try {
    // Appel interne du webhook (clé de service) : confirme la transaction auprès de SebPay, puis verse si besoin.
    const bearer = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
    if (bearer && SERVICE_KEY && bearer === SERVICE_KEY) {
      const q = await req.json();
      if (q.action !== "reconcile") return fail("Action interne inconnue.");
      const { data: t0 } = await db.from(T).select("*").eq("external_reference", String(q.reference ?? "")).maybeSingle();
      if (!t0) return ok({ found: false });
      const r = await syncStatus(t0);
      if (r.status === "approved" && r.type === "collect") await autoPayout(r); // idempotent (réservation payout_reference)
      return ok({ found: true, status: r.status });
    }
    const user = await getUser(req);
    if (!user) return fail("Compte non autorisé pour cette application.");
    const p = await req.json();
    if (!user.approved) {
      if (p.action === "me") return reply({ ok: true, role: user.role, approved: false });
      return fail("Inscription en attente de validation par l'administrateur.");
    }
    const callback = `${SUPABASE_URL}/functions/v1/sebpay-webhook`;
    const isAdmin = user.role === "admin";

    switch (p.action) {
      case "me":
        return reply({ ok: true, role: user.role, approved: true });

      case "operators": {
        if (!COUNTRIES[p.country]) return fail("Pays non supporté.");
        try {
          const d = await sebpay(await loadKeys(), "GET", `/operators?country=${p.country}`);
          const list = Array.isArray(d) ? d : (d?.operators ?? d?.data ?? []);
          return ok(list);
        } catch (e) {
          // Refus 4xx de SebPay : pays non accepté => liste vide (l'app affiche alors BNB uniquement).
          // Panne réseau / erreur 5xx : on laisse remonter l'erreur, sans faire croire que le pays est refusé.
          if (e instanceof SebpayError && e.status >= 400 && e.status < 500 && e.status !== 401 && e.status !== 403 && e.status !== 429) {
            return ok([]);
          }
          throw e;
        }
      }

      case "crypto_quote": {
        // Montant équivalent dans la monnaie du pays qui reçoit l'argent (affiché dans le formulaire pendant la saisie)
        const usdQ = Number(p.amount_usd);
        const rcQ = COUNTRIES[String(p.country ?? "")];
        if (!(usdQ > 0) || !rcQ) return fail("Montant ou pays invalide.");
        const rateQ = await fxRate("USD", rcQ.currency);
        return ok({ currency: rcQ.currency, rate: rateQ, local_amount: Math.floor(usdQ * rateQ) });
      }

      case "crypto_create": {
        const usd = Number(p.amount_usd);
        if (!(usd >= 1)) return fail("Montant minimum : 1 USD.");
        if (usd > 100000) return fail("Montant trop élevé.");
        if (!COUNTRIES[p.country]) return fail("Pays non supporté.");
        const recipientOperator = String(p.recipient_operator ?? "").trim().slice(0, 60);
        if (!recipientOperator) return fail("Indiquez le réseau qui recevra l'argent.");
        const recipientPhone = normalizePhone(p.recipient_phone, p.country);
        if (!recipientPhone) return fail("Numéro de téléphone invalide pour ce pays.");
        const expected = await usdToBnbManual(usd);
        const rCurrency = COUNTRIES[p.country].currency;
        const fx = await fxRate("USD", rCurrency);
        const payoutAmount = Math.floor(usd * fx); // montant que le bénéficiaire doit recevoir (monnaie de son pays)
        const reference = `CRY-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        const token = crypto.randomUUID().replaceAll("-", ""); // colonne obligatoire, inutilisée sans CryptAPI
        const ticker = "manual";
        const d = { address_in: BNB_ADDRESS }; // adresse fixe : la vôtre
        const row = {
          reference, token, ticker, status: "pending", country: String(p.country ?? ""),
          amount_usd: usd, expected_bnb: expected, address_in: d.address_in, address_out: BNB_ADDRESS,
          recipient_operator: recipientOperator, recipient_phone: recipientPhone,
          created_by: user.id,
        };
        let { error } = await db.from(TC).insert({ ...row, recipient_currency: rCurrency, payout_amount: payoutAmount, fx_rate: fx });
        if (error) {
          // colonnes du montant local absentes (migration non appliquée) : on enregistre quand même, sans elles
          ({ error } = await db.from(TC).insert(row));
        }
        if (error) return fail("Enregistrement du paiement impossible (table sebpay_crypto créée ?).");
        return ok({
          reference, address_in: d.address_in, expected_bnb: expected, amount_usd: usd, status: "pending",
          recipient_operator: recipientOperator, recipient_phone: recipientPhone,
          recipient_currency: rCurrency, payout_amount: payoutAmount, fx_rate: fx,
        });
      }

      case "crypto_status": {
        const { data: t } = await db.from(TC).select("*").eq("reference", String(p.reference ?? "")).maybeSingle();
        if (!t || (!isAdmin && t.created_by !== user.id)) return fail("Paiement introuvable.");
        const { token: _t, ...rest } = t; // le jeton du callback ne sort jamais du serveur
        return ok(withExpiry(rest));
      }

      // ---------------- Décaissement BNB : preuves du client, décaissement manuel par l'administrateur
      case "crypto_history": {
        let q = db.from(TC).select("*").order("created_at", { ascending: false }).limit(50);
        if (!isAdmin) q = q.eq("created_by", user.id);
        const { data, error } = await q;
        if (error) return fail("Lecture de l'historique impossible.");
        return ok((data ?? []).map(({ token: _t, proof_paths, ...r }) => withExpiry({
          ...r, type: "crypto", proof_count: (proof_paths ?? []).length,
        })));
      }

      case "crypto_proof": {
        const { data: t } = await db.from(TC).select("*").eq("reference", String(p.reference ?? "")).maybeSingle();
        if (!t || t.created_by !== user.id) return fail("Paiement introuvable.");
        if (t.payout_status === "paid") return fail("Ce paiement est déjà marqué comme payé.");
        if (withExpiry(t).expired) return fail("Ce paiement a expiré (24 h sans réception du BNB). Faites un nouveau paiement.");
        const wait = PROOF_DELAY_MS - (Date.now() - new Date(t.created_at).getTime());
        if (wait > 0) return fail(`Les preuves peuvent être envoyées dans ${Math.ceil(wait / 1000)} s.`);
        const images: unknown[] = Array.isArray(p.images) ? p.images : [];
        if (images.length < 1 || images.length > 3) return fail("Joignez de 1 à 3 images.");
        if ((t.proof_paths ?? []).length + images.length > 6) return fail("Maximum 6 preuves par paiement.");
        const added: string[] = [];
        for (const raw of images) {
          let bytes: Uint8Array;
          try {
            bytes = Uint8Array.from(atob(String(raw)), (ch) => ch.charCodeAt(0));
          } catch {
            return fail("Image illisible.");
          }
          if (bytes.length < 1000 || bytes.length > 3_000_000) return fail("Image trop lourde ou invalide (max 3 Mo).");
          if (!(bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff)) return fail("Format d'image non accepté (JPEG).");
          const path = `${t.reference}/${Date.now()}-${crypto.randomUUID().slice(0, 6)}.jpg`;
          const up = await db.storage.from(PROOF_BUCKET).upload(path, bytes, { contentType: "image/jpeg" });
          if (up.error) return fail("Envoi de l'image impossible (bucket sebpay-proofs créé ?).");
          added.push(path);
        }
        const now = new Date().toISOString();
        await db.from(TC).update({
          proof_paths: [...(t.proof_paths ?? []), ...added], payout_status: "proof_sent",
          proof_sent_at: now, report_note: null, updated_at: now,
        }).eq("id", t.id);
        return ok({ payout_status: "proof_sent", proof_count: (t.proof_paths ?? []).length + added.length });
      }

      case "crypto_list": {
        // Tous les paiements crypto à traiter, avec le numéro qui reçoit, dès la création (même avant les preuves).
        // Les paiements jamais reçus au bout de 24 h disparaissent de la liste.
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const { data } = await db.from(TC).select("*").in("payout_status", ["awaiting", "proof_sent", "reported"])
          .order("created_at", { ascending: false }).limit(200);
        const rank: Record<string, number> = { reported: 0, proof_sent: 1, awaiting: 2 };
        const out = [];
        for (const r of data ?? []) {
          const { token: _t, proof_paths, ...rest } = r;
          const w = withExpiry({ ...rest, type: "crypto" });
          if (w.expired) continue;
          let proofs: string[] = [];
          if ((proof_paths ?? []).length) {
            const { data: signed } = await db.storage.from(PROOF_BUCKET).createSignedUrls(proof_paths, 3600);
            proofs = (signed ?? []).map((x) => x.signedUrl).filter(Boolean) as string[];
          }
          out.push({ ...w, proofs });
        }
        out.sort((x, y) => (rank[String(x.payout_status)] ?? 3) - (rank[String(y.payout_status)] ?? 3));
        return ok(out.slice(0, 100));
      }

      case "crypto_pay": {
        // L'administrateur a envoyé l'argent à la main : il marque le décaissement comme payé
        if (!isAdmin) return fail("Seul un administrateur peut décaisser.");
        const { data: t } = await db.from(TC).select("id,payout_status").eq("reference", String(p.reference ?? "")).maybeSingle();
        if (!t) return fail("Paiement introuvable.");
        if (!["proof_sent", "reported"].includes(t.payout_status)) return fail("Aucune preuve à traiter pour ce paiement.");
        const now = new Date().toISOString();
        await db.from(TC).update({
          payout_status: "paid", paid_at: now, paid_by: user.id, reported_at: null, report_note: null, updated_at: now,
        }).eq("id", t.id);
        return ok({ payout_status: "paid" });
      }

      case "crypto_report": {
        // Le client signale que l'argent n'est pas arrivé, même après « Payé »
        const { data: t } = await db.from(TC).select("id,created_by,payout_status").eq("reference", String(p.reference ?? "")).maybeSingle();
        if (!t || t.created_by !== user.id) return fail("Paiement introuvable.");
        if (t.payout_status !== "paid") return fail("Ce paiement n'est pas encore marqué comme payé.");
        const now = new Date().toISOString();
        await db.from(TC).update({
          payout_status: "reported", reported_at: now,
          report_note: String(p.note ?? "").trim().slice(0, 300) || "Argent non reçu", updated_at: now,
        }).eq("id", t.id);
        return ok({ payout_status: "reported" });
      }

      case "preflight": {
        // Vérification AVANT toute opération : base de données, clés, API SebPay (encaissement ET décaissement), change.
        // type "payout" : décaissement seul. Sinon : encaissement + versement automatique au bénéficiaire.
        const isPayoutCheck = p.type === "payout";
        const problems: string[] = [];
        const c = COUNTRIES[String(p.country ?? "")];
        const rc = COUNTRIES[String(p.recipient_country ?? p.country ?? "")];
        if (!c) problems.push(isPayoutCheck ? "Pays non supporté." : "Pays du numéro qui paie non supporté.");
        if (!isPayoutCheck && !rc) problems.push("Pays du numéro qui reçoit non supporté.");
        const chk = await db.from(T).select("recipient_phone,payout_reference,recipient_country,payout_amount").limit(1);
        if (chk.error) problems.push("Base de données non à jour : exécutez la partie « v5 » de supabase.sql.");
        if (c && (isPayoutCheck || rc)) {
          let keys: Keys | null = null;
          try { keys = await loadKeys(); } catch (_) { problems.push("Clés de paiement introuvables côté serveur."); }
          if (keys) {
            const ops = async (iso: string) => {
              const d = await sebpay(keys!, "GET", `/operators?country=${iso}`);
              return (Array.isArray(d) ? d : (d?.operators ?? d?.data ?? [])) as Array<Record<string, unknown>>;
            };
            const has = (list: Array<Record<string, unknown>>, slug: unknown) => list.some((o) => String(o.slug) === String(slug));
            if (isPayoutCheck) {
              // Les deux appels à SebPay partent en même temps (plus rapide, et moins de risque de dépasser le délai du relais)
              const [opsRes, pp] = await Promise.all([
                ops(String(p.country)).then((l) => ({ l, e: null as unknown }), (e) => ({ l: [] as Array<Record<string, unknown>>, e })),
                featureProblem(keys, "/payouts/KOUAMEPAY-PREFLIGHT", "Le décaissement"),
              ]);
              if (opsRes.e) problems.push(`Service de paiement injoignable (décaissement) : ${String((opsRes.e as Error).message).slice(0, 160)}`);
              else if (!has(opsRes.l, p.operator)) problems.push("Ce réseau n'est pas accepté pour le décaissement dans ce pays.");
              if (pp) problems.push(pp);

              // Contrôles sur les données saisies (envoyées par l'appli quand les champs sont remplis)
              if (p.amount != null && String(p.amount) !== "") {
                const amt = Math.round(Number(p.amount));
                if (!(amt > 0)) problems.push("Montant invalide.");
                else {
                  const st = await loadSettings();
                  const pl = plan("payout", amt, st.percent, st.mode);
                  if (pl.charge < minPayout(c.currency)) problems.push(`Montant trop faible : minimum ${minPayout(c.currency)} ${c.currency} envoyés (après commission).`);
                }
              }
              if (p.phone != null && String(p.phone).trim() !== "" && !normalizePhone(p.phone, String(p.country))) {
                problems.push("Numéro de téléphone invalide pour ce pays.");
              }
              if (p.recipient_name != null && String(p.recipient_name).trim() === "") problems.push("Nom du bénéficiaire manquant.");

              // SebPay bloque les décaissements après 5 refus d'affilée : on le détecte dans l'historique du jour
              const dayStart = new Date();
              dayStart.setUTCHours(0, 0, 0, 0);
              const { data: lastOut } = await db.from(T).select("status").eq("type", "payout")
                .gte("created_at", dayStart.toISOString()).order("created_at", { ascending: false }).limit(5);
              if ((lastOut ?? []).length === 5 && (lastOut ?? []).every((r) => r.status === "rejected")) {
                problems.push("5 décaissements refusés d'affilée aujourd'hui : SebPay bloque probablement les décaissements jusqu'à la fin de la journée.");
              }
            } else {
              try {
                const l = await ops(String(p.country));
                if (!has(l, p.operator)) problems.push("Le réseau du numéro qui paie n'est pas accepté pour l'encaissement.");
              } catch (e) { problems.push(`Service de paiement injoignable (encaissement) : ${String((e as Error).message).slice(0, 160)}`); }
              try {
                const l = await ops(String(p.recipient_country ?? p.country));
                if (!has(l, p.recipient_operator)) problems.push("Le réseau du numéro qui reçoit n'est pas accepté pour le versement.");
              } catch (e) { problems.push(`Service de paiement injoignable (versement) : ${String((e as Error).message).slice(0, 160)}`); }
              const cp = await featureProblem(keys, "/collections/KOUAMEPAY-PREFLIGHT", "L'encaissement");
              if (cp) problems.push(cp);
              const pp = await featureProblem(keys, "/payouts/KOUAMEPAY-PREFLIGHT", "Le versement au bénéficiaire");
              if (pp) problems.push(pp);
            }
          }
          if (!isPayoutCheck && rc && c.currency !== rc.currency) {
            try { await fxRate(c.currency, rc.currency); } catch (e) { problems.push(String((e as Error).message)); }
          }
        }
        return ok({ ready: problems.length === 0, problems });
      }

      case "quote": {
        const type = p.type === "payout" ? "payout" : "collect";
        const amount = Math.round(Number(p.amount));
        if (!(amount > 0)) return fail("Montant invalide.");
        const s = await loadSettings();
        const out: Record<string, unknown> = { ...plan(type, amount, s.percent, s.mode), percent: s.percent, mode: s.mode };
        const from = COUNTRIES[String(p.country ?? "")], to = COUNTRIES[String(p.recipient_country ?? "")];
        if (type === "collect" && from && to) {
          const fx = await fxRate(from.currency, to.currency);
          out.payout_amount = Math.floor(Number(out.net) * fx);
          out.payout_currency = to.currency;
          out.fx_rate = fx;
        }
        return ok(out);
      }

      case "collect": {
        const c = COUNTRIES[p.country];
        if (!c) return fail("Pays non supporté.");
        const amount = Math.round(Number(p.amount));
        if (!(amount > 0)) return fail("Montant invalide.");
        if (!p.operator) return fail("Opérateur manquant.");
        const phone = normalizePhone(p.phone, p.country);
        if (!phone) return fail("Numéro de téléphone invalide pour ce pays.");
        // Pays acceptés par SebPay : le numéro qui reçoit l'argent est obligatoire (en plus du numéro qui paie)
        const rOp = String(p.recipient_operator ?? "").trim().slice(0, 60);
        if (!rOp) return fail("Choisissez le réseau du numéro qui reçoit l'argent.");
        const rName = String(p.recipient_name ?? "").trim().slice(0, 180);
        if (!rName) return fail("Nom du bénéficiaire manquant.");
        const rCountry = String(p.recipient_country ?? p.country);
        const rc = COUNTRIES[rCountry];
        if (!rc) return fail("Pays du numéro qui reçoit non supporté.");
        const rPhone = normalizePhone(p.recipient_phone, rCountry);
        if (!rPhone) return fail("Numéro qui reçoit : numéro invalide pour ce pays.");
        const s = await loadSettings();
        const pl = plan("collect", amount, s.percent, s.mode);
        const fx = await fxRate(c.currency, rc.currency);
        const payoutAmount = Math.floor(pl.net * fx);
        if (payoutAmount < minPayout(rc.currency)) return fail(`Montant trop faible : le bénéficiaire doit recevoir au moins ${minPayout(rc.currency)} ${rc.currency} (après commission).`);
        // La migration SQL v5 doit être appliquée, sinon le versement automatique serait perdu après l'encaissement
        const chk = await db.from(T).select("recipient_phone,payout_reference").limit(1);
        if (chk.error) return fail("Mise à jour de la base requise : exécutez la partie « v5 » de supabase.sql.");
        const reference = `COL-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        const payload: Record<string, unknown> = {
          amount: pl.charge, currency: c.currency, phone, operator: p.operator, country: p.country,
          external_reference: reference, callback_url: callback,
        };
        if (p.otp_code) payload.otp_code = String(p.otp_code);
        const keys = await loadKeys();
        const row = {
          type: "collect", external_reference: reference, sebpay_id: null, status: "pending", amount: pl.charge,
          currency: c.currency, phone, operator: p.operator, country: p.country, commission_percent: s.percent,
          commission_total: pl.commission, sebpay_fee: 0, my_commission: pl.commission,
          net_amount: pl.net, created_by: user.id,
          recipient_operator: rOp, recipient_phone: rPhone, recipient_name: rName,
          recipient_country: rCountry, recipient_currency: rc.currency, payout_amount: payoutAmount, fx_rate: fx,
        };
        // La ligne est enregistrée AVANT l'appel : si la réponse se perd, la référence reste connue et le webhook retrouve la transaction.
        const ins0 = await db.from(T).insert(row).select("id").single();
        if (ins0.error || !ins0.data) {
          console.error("insert transaction:", ins0.error?.message);
          return fail("Enregistrement impossible : rien n'a été envoyé, réessayez.");
        }
        const rowId = ins0.data.id;
        let d;
        try {
          d = await sebpay(keys, "POST", "/collections", payload);
        } catch (e) {
          if (isClearRefusal(e)) {
            await db.from(T).delete().eq("id", rowId); // SebPay n'a rien créé : la référence reste libre
            throw e;
          }
          console.error("collect incertain:", e);
          return fail("Réponse non reçue du fournisseur : la demande a peut-être été envoyée. Ne la renvoyez pas, vérifiez l'Historique dans quelques minutes.");
        }
        // La réponse d'une collecte ne contient pas fee_amount : on prend les frais réels du compte (calculate-fee).
        // La marge tient aussi compte des frais estimés du versement automatique (prélevés sur votre wallet, en monnaie du bénéficiaire).
        let fee = Number(d?.fee_amount ?? 0);
        const [pctC, pctP] = await Promise.all([
          fee > 0 ? Promise.resolve(null) : feePercent(keys, "collection", p.country),
          feePercent(keys, "payout", rCountry),
        ]);
        if (!(fee > 0) && pctC != null) fee = Math.round((pl.charge * pctC) / 100);
        const payoutFeeEst = pctP != null && fx > 0 ? Math.round((payoutAmount * pctP / 100) / fx) : 0;
        await db.from(T).update({
          sebpay_id: d?.transaction_id ?? null, sebpay_fee: fee, my_commission: pl.commission - fee - payoutFeeEst, updated_at: new Date().toISOString(),
        }).eq("id", rowId);
        const st = normStatus(d?.status);
        if (st !== "pending") await db.from(T).update({ status: st, updated_at: new Date().toISOString() }).eq("id", rowId).eq("status", "pending");
        const { data: fresh } = await db.from(T).select("*").eq("id", rowId).maybeSingle();
        const out = fresh ?? { ...row, id: rowId, status: st };
        if (out.status === "approved") await autoPayout(out);
        // provider_link : page de l'opérateur (Wave, Orange…) où le client DOIT valider le paiement (null si validation sur le téléphone).
        return ok({ ...out, provider_link: d?.provider_link ?? null });
      }

      case "payout": {
        if (!isAdmin) return fail("Seul un administrateur peut décaisser.");
        const c = COUNTRIES[p.country];
        if (!c) return fail("Pays non supporté.");
        const amount = Math.round(Number(p.amount));
        if (!(amount > 0)) return fail("Montant invalide.");
        const s = await loadSettings();
        const pl = plan("payout", amount, s.percent, s.mode);
        if (pl.charge < minPayout(c.currency)) return fail(`Montant minimum de décaissement : ${minPayout(c.currency)} ${c.currency} (après commission).`);
        const recipient = String(p.recipient_name ?? "").trim().slice(0, 180);
        if (!recipient) return fail("Nom du bénéficiaire manquant.");
        if (!p.operator) return fail("Opérateur manquant.");
        const phone = normalizePhone(p.phone, p.country);
        if (!phone) return fail("Numéro de téléphone invalide pour ce pays.");
        const reference = `PAY-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        const row = {
          type: "payout", external_reference: reference, sebpay_id: null, status: "pending", amount: pl.charge,
          currency: c.currency, phone, operator: p.operator, country: p.country, recipient_name: recipient,
          commission_percent: s.percent, commission_total: pl.commission, sebpay_fee: 0,
          my_commission: pl.commission, net_amount: pl.net, created_by: user.id,
        };
        // Ligne enregistrée AVANT l'appel (référence toujours connue, même si la réponse se perd)
        const ins0 = await db.from(T).insert(row).select("id").single();
        if (ins0.error || !ins0.data) {
          console.error("insert transaction:", ins0.error?.message);
          return fail("Enregistrement impossible : rien n'a été envoyé, réessayez.");
        }
        const rowId = ins0.data.id;
        let d;
        try {
          d = await sebpay(await loadKeys(), "POST", "/payouts", {
            recipient_name: recipient, phone, operator: p.operator, country: p.country,
            amount: pl.charge, currency: c.currency, external_reference: reference, callback_url: callback,
          });
        } catch (e) {
          if (isClearRefusal(e)) {
            await db.from(T).delete().eq("id", rowId);
            throw e;
          }
          console.error("payout incertain:", e);
          return fail("Réponse non reçue du fournisseur : le décaissement a peut-être été envoyé. Ne le renvoyez pas, vérifiez l'Historique dans quelques minutes.");
        }
        const fee = Number(d?.fee_amount ?? 0);
        await db.from(T).update({
          sebpay_id: d?.transaction_id ?? null, sebpay_fee: fee, my_commission: pl.commission - fee, updated_at: new Date().toISOString(),
        }).eq("id", rowId);
        await saveAmounts(reference, d);
        const st = normStatus(d?.status);
        if (st !== "pending") await db.from(T).update({ status: st, updated_at: new Date().toISOString() }).eq("id", rowId).eq("status", "pending");
        const { data: fresh } = await db.from(T).select("*").eq("id", rowId).maybeSingle();
        return ok(fresh ?? { ...row, id: rowId, status: st });
      }

      case "refund": {
        // Rembourse un encaissement réussi : renvoie le montant payé au numéro du client
        if (!isAdmin) return fail("Seul un administrateur peut rembourser.");
        const { data: t } = await db.from(T).select("*").eq("external_reference", String(p.reference ?? "")).maybeSingle();
        if (!t || t.type !== "collect" || t.status !== "approved") {
          return fail("Seuls les encaissements réussis peuvent être remboursés.");
        }
        if (!COUNTRIES[t.country]) return fail("Pays non supporté.");
        // Remboursement NON CONFIRMÉ (réponse perdue) : on rejoue la MÊME référence, SebPay renvoie l'objet déjà créé
        // sans payer une seconde fois (doc : ne jamais renvoyer avec une nouvelle référence).
        let reference: string;
        let replay = false;
        if (t.refund_reference) {
          const oldNote = String(t.refund_note ?? "");
          if (!oldNote.startsWith("Remboursement non confirmé")) return fail("Un remboursement est déjà en cours ou effectué.");
          const { data: rr } = await db.from(T).select("status").eq("external_reference", t.refund_reference).maybeSingle();
          if (rr && rr.status !== "pending") return fail("Ce remboursement a déjà été traité : actualisez l'Historique.");
          reference = String(t.refund_reference);
          replay = true;
          const { data: again } = await db.from(T)
            .update({ refund_note: "Remboursement en cours", updated_at: new Date().toISOString() })
            .eq("id", t.id).eq("refund_reference", reference).eq("refund_note", oldNote).select("id").maybeSingle();
          if (!again) return fail("Un remboursement est déjà en cours ou effectué.");
        } else {
          reference = `REF-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
          // On réserve d'abord, pour qu'un double clic ne rembourse jamais deux fois
          const { data: claimed } = await db.from(T)
            .update({ refund_reference: reference, refund_note: "Remboursement en cours", updated_at: new Date().toISOString() })
            .eq("id", t.id).is("refund_reference", null).select("id").maybeSingle();
          if (!claimed) return fail("Un remboursement est déjà en cours ou effectué.");
        }
        const release = () => db.from(T).update({ refund_reference: null, refund_note: null }).eq("id", t.id);
        const row = {
          type: "payout", external_reference: reference, sebpay_id: null, status: "pending", amount: t.amount, currency: t.currency,
          phone: t.phone, operator: t.operator, country: t.country, recipient_name: `Remboursement de ${t.external_reference}`,
          sebpay_fee: 0, net_amount: t.amount, created_by: user.id,
        };
        const existing = await db.from(T).select("id").eq("external_reference", reference).maybeSingle();
        if (!existing.data) {
          const ins = await db.from(T).insert(row);
          if (ins.error) {
            console.error("insert refund:", ins.error.message);
            await release(); // rien n'a été envoyé : on peut réessayer
            return fail("Enregistrement impossible : rien n'a été envoyé, réessayez.");
          }
        }
        let d;
        try {
          d = await sebpay(await loadKeys(), "POST", "/payouts", {
            recipient_name: "Remboursement client", phone: t.phone, operator: t.operator, country: t.country,
            amount: t.amount, currency: t.currency, external_reference: reference, callback_url: callback,
          });
        } catch (e) {
          if (isClearRefusal(e)) {
            await db.from(T).delete().eq("external_reference", reference);
            await release(); // refus net : SebPay n'a rien créé, on peut réessayer
            throw e;
          }
          // Résultat INCERTAIN : on garde la réservation (pas de second remboursement avec une nouvelle référence).
          // Le webhook ou le suivi du statut tranchera ; un nouvel essai de l'administrateur rejoue la MÊME référence.
          console.error("refund incertain:", e);
          await db.from(T).update({ refund_note: "Remboursement non confirmé : vérifiez l'Historique avant de réessayer", updated_at: new Date().toISOString() }).eq("id", t.id);
          return fail("Remboursement non confirmé : il a peut-être été envoyé. Vérifiez l'Historique ; un nouvel essai réutilisera la même référence (aucun double paiement).");
        }
        const fee = Number(d?.fee_amount ?? 0);
        await db.from(T).update({ sebpay_id: d?.transaction_id ?? null, sebpay_fee: fee, updated_at: new Date().toISOString() }).eq("external_reference", reference);
        await saveAmounts(reference, d);
        const st = normStatus(d?.status);
        if (st !== "pending") await db.from(T).update({ status: st, updated_at: new Date().toISOString() }).eq("external_reference", reference).eq("status", "pending");
        await afterStatusChange(reference, st);
        if (replay && st === "pending") await db.from(T).update({ refund_note: "Remboursement en cours", updated_at: new Date().toISOString() }).eq("id", t.id).eq("refund_reference", reference);
        const { data: fresh } = await db.from(T).select("*").eq("external_reference", reference).maybeSingle();
        return ok(fresh ?? { ...row, status: st });
      }

      case "status": {
        const { data: t } = await db.from(T).select("*").eq("external_reference", String(p.reference ?? "")).maybeSingle();
        if (!t || (!isAdmin && t.created_by !== user.id)) return fail("Transaction introuvable.");
        return ok(await withPayoutLeg(await syncStatus(t)));
      }

      case "history": {
        // Filet de sécurité (administrateur) : un encaissement réussi dont le versement n'a JAMAIS été tenté
        // (payout_reference vide) est repris ici. Sans risque de double versement : la réservation est atomique.
        if (isAdmin) {
          const { data: orphans } = await db.from(T).select("*").eq("type", "collect").eq("status", "approved")
            .not("recipient_phone", "is", null).is("payout_reference", null).limit(5);
          for (const o of orphans ?? []) await autoPayout(o);
        }
        let q = db.from(T).select("*").order("created_at", { ascending: false }).limit(50);
        if (!isAdmin) q = q.eq("created_by", user.id);
        const { data, error } = await q;
        if (error) return fail("Lecture de l'historique impossible.");
        const rows = (data ?? []).map((r) => {
          if (isAdmin) return r;
          const { sebpay_fee: _f, my_commission: _m, recipient_amount: _ra, total_deducted: _td, ...rest } = r; // la part de l'admin reste privée
          return rest;
        });
        return ok(rows);
      }

      // ---------------- Versements échoués à reprendre (l'encaissement a réussi, le versement au bénéficiaire non)
      case "payout_failed_list": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const { data: cands, error } = await db.from(T).select("*").eq("type", "collect").eq("status", "approved")
          .not("recipient_phone", "is", null).not("payout_reference", "is", null).is("refund_reference", null)
          .order("created_at", { ascending: false }).limit(300);
        if (error) return fail("Base de données non à jour : colonnes manual_payout_* manquantes.");
        const refs = (cands ?? []).flatMap((c) => [c.payout_reference, c.manual_payout_reference]).filter(Boolean);
        // deno-lint-ignore no-explicit-any
        const rows = new Map<string, Record<string, any>>();
        if (refs.length) {
          const { data } = await db.from(T).select("external_reference,status").in("external_reference", refs);
          for (const r of data ?? []) rows.set(String(r.external_reference), r);
        }
        const out = [];
        for (const c of cands ?? []) {
          const info = classifyPayout(c, rows.get(String(c.payout_reference)) ?? null, rows.get(String(c.manual_payout_reference)) ?? null);
          if (info) out.push({ ...c, fail_reason: info.reason, fail_check: info.check });
        }
        return ok(out.slice(0, 50));
      }

      case "payout_failed_send": {
        // L'administrateur envoie l'argent au bénéficiaire depuis le wallet (le net a déjà été calculé : aucune commission en plus)
        if (!isAdmin) return fail("Seul un administrateur peut décaisser.");
        const { data: t } = await db.from(T).select("*").eq("external_reference", String(p.reference ?? "")).maybeSingle();
        if (!t) return fail("Transaction introuvable.");
        const lnk = await payRows(t);
        const info = classifyPayout(t, lnk.pay, lnk.man);
        if (!info) return fail("Ce versement n'est plus à reprendre : il a déjà été effectué ou traité.");
        if (info.check && p.confirm_checked !== true) {
          return fail("Le résultat du premier versement est incertain : vérifiez l'Historique, puis confirmez.");
        }
        const amount = Math.round(Number(t.payout_amount ?? t.net_amount));
        const rCountry = t.recipient_country || t.country;
        const rCurrency = t.recipient_currency || t.currency;
        if (!COUNTRIES[rCountry]) return fail("Pays du bénéficiaire inconnu.");
        const floor = minPayout(rCurrency);
        if (!(amount >= floor)) {
          return fail(`Montant trop faible pour un décaissement (minimum ${floor} ${rCurrency}) : envoyez-le autrement, puis touchez « Déjà réglé ».`);
        }
        // Résultat INCERTAIN : on rejoue la MÊME référence avec les mêmes paramètres. SebPay renvoie alors l'objet déjà créé
        // (idempotent_replay) sans payer une seconde fois. Une nouvelle référence n'est utilisée qu'après un refus net.
        const noteNow = String(t.payout_note ?? "");
        let reuseRef: string | null = null;
        if (info.check) {
          if (t.manual_payout_reference && lnk.man?.status !== "rejected") reuseRef = t.manual_payout_reference;
          else if (!t.manual_payout_reference && noteNow.startsWith("Versement automatique non confirmé") && t.payout_reference && lnk.pay?.status !== "rejected") {
            reuseRef = t.payout_reference;
          }
        }
        const newRef: string = reuseRef ?? `MAN-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        const startIso = new Date().toISOString();
        // réservation atomique : un double clic ne décaisse jamais deux fois
        const base = db.from(T)
          .update({ manual_payout_reference: newRef, manual_payout_at: null, payout_note: "Décaissement manuel en cours", updated_at: startIso })
          .eq("id", t.id);
        const { data: claimed } = await (t.manual_payout_reference
          ? base.eq("manual_payout_reference", t.manual_payout_reference)
          : base.is("manual_payout_reference", null)).select("id").maybeSingle();
        if (!claimed) return fail("Un décaissement est déjà en cours pour ce versement.");
        const release = (note: string) => db.from(T).update({
          manual_payout_reference: t.manual_payout_reference ?? null, manual_payout_at: t.manual_payout_at ?? null,
          payout_note: note, updated_at: new Date().toISOString(),
        }).eq("id", t.id);
        const row = {
          type: "payout", external_reference: newRef, sebpay_id: null, status: "pending", amount, currency: rCurrency,
          phone: t.recipient_phone, operator: t.recipient_operator, country: rCountry,
          recipient_name: t.recipient_name || "Bénéficiaire", sebpay_fee: 0, net_amount: amount, created_by: user.id,
        };
        // ligne enregistrée AVANT l'appel (sauf si on rejoue une référence dont la ligne existe déjà)
        const existing = await db.from(T).select("id").eq("external_reference", newRef).maybeSingle();
        if (!existing.data) {
          const ins = await db.from(T).insert(row);
          if (ins.error) {
            console.error("insert manual payout:", ins.error.message);
            await release("Versement refusé : enregistrement impossible, rien n'a été envoyé.");
            return fail("Enregistrement impossible : rien n'a été envoyé, réessayez.");
          }
        }
        try {
          const d = await sebpay(await loadKeys(), "POST", "/payouts", {
            recipient_name: row.recipient_name, phone: t.recipient_phone, operator: t.recipient_operator,
            country: rCountry, amount, currency: rCurrency, external_reference: newRef, callback_url: callback,
          });
          const st = normStatus(d?.status);
          await db.from(T).update({
            sebpay_id: d?.transaction_id ?? null, sebpay_fee: Number(d?.fee_amount ?? 0), status: st, updated_at: new Date().toISOString(),
          }).eq("external_reference", newRef);
          await saveAmounts(newRef, d);
          const doneIso = new Date().toISOString();
          await db.from(T).update({
            manual_payout_at: doneIso, manual_payout_by: user.id,
            payout_note: `Versé par l'administrateur à +${t.recipient_phone}`, updated_at: doneIso,
          }).eq("id", t.id);
          return ok({ ...row, sebpay_id: d?.transaction_id ?? null, status: st, replay: d?.idempotent_replay === true });
        } catch (e) {
          console.error("manual payout:", e);
          const why = String((e as Error)?.message ?? e).slice(0, 220);
          if (isClearRefusal(e)) {
            // refus net : on libère la réservation, l'administrateur pourra réessayer avec une nouvelle référence
            if (!reuseRef) await db.from(T).delete().eq("external_reference", newRef);
            await release(`Versement refusé : ${why}`);
            throw e;
          }
          await db.from(T).update({
            payout_note: `Décaissement non confirmé (${why}) : vérifiez dans l'historique puis réessayez si besoin.`, updated_at: new Date().toISOString(),
          }).eq("id", t.id);
          return fail(`Décaissement non confirmé (${why}). Vérifiez dans l'Historique avant de réessayer : la relance réutilisera la même référence.`);
        }
      }

      case "payout_failed_close": {
        // Le versement a été réglé en dehors de l'appli : on le retire de la liste
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const { data: t } = await db.from(T).select("*").eq("external_reference", String(p.reference ?? "")).maybeSingle();
        if (!t) return fail("Transaction introuvable.");
        const lnk = await payRows(t);
        if (!classifyPayout(t, lnk.pay, lnk.man)) return fail("Ce versement n'est plus à reprendre.");
        const closeIso = new Date().toISOString();
        await db.from(T).update({
          manual_payout_at: closeIso, manual_payout_by: user.id, manual_payout_reference: null,
          payout_note: "Réglé hors de l'application (clôturé par l'administrateur)", updated_at: closeIso,
        }).eq("id", t.id);
        return ok({});
      }


      // ---------------- Service client
      case "myip": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const seen = new Set<string>();
        for (let i = 0; i < 3; i++) { const ip = await serverIp(); if (ip) seen.add(ip); }
        return ok({ ips: [...seen] });
      }

      case "ping": {
        if (isAdmin) await db.from("sebpay_presence").upsert({ id: 1, admin_last_seen: new Date().toISOString() });
        return ok({});
      }

      case "support_status": {
        const out: Record<string, unknown> = { admin_online: await adminOnline() };
        if (isAdmin) out.pending = (await buildInbox()).filter((c) => c.pending).length;
        return ok(out);
      }

      case "support_history": {
        const target = isAdmin && p.user_id ? String(p.user_id) : user.id;
        const { data } = await db.from(SUPPORT_TABLE).select("id,sender,body,created_at")
          .eq("user_id", target).order("created_at", { ascending: false }).limit(60);
        return ok({ messages: (data ?? []).reverse(), admin_online: await adminOnline() });
      }

      case "support_send": {
        const text = String(p.text ?? "").trim().slice(0, 1000);
        if (!text) return fail("Écrivez votre message.");
        const since = new Date(Date.now() - 10 * 60_000).toISOString();
        const { count } = await db.from(SUPPORT_TABLE).select("id", { count: "exact", head: true })
          .eq("user_id", user.id).eq("sender", "user").gte("created_at", since);
        if ((count ?? 0) >= 20) return fail("Trop de messages en peu de temps. Patientez quelques minutes.");

        const { data: recent } = await db.from(SUPPORT_TABLE).select("sender,body,created_at")
          .eq("user_id", user.id).order("created_at", { ascending: false }).limit(10);
        const { data: um, error: ue } = await db.from(SUPPORT_TABLE)
          .insert({ user_id: user.id, user_email: user.email, sender: "user", body: text })
          .select("id,sender,body,created_at").single();
        if (ue || !um) return fail("Envoi impossible, réessayez.");

        const online = await adminOnline();
        const last = (recent ?? [])[0];
        // Si l'admin est en ligne et vient de répondre à ce client, on le laisse poursuivre sans l'IA
        if (online && last?.sender === "admin" && Date.now() - new Date(last.created_at).getTime() < 10 * 60_000) {
          return ok({ messages: [um], admin_online: online });
        }

        const history = [...(recent ?? [])].reverse()
          .map((m) => ({ role: m.sender === "user" ? "user" : "assistant", content: String(m.body) }));
        history.push({ role: "user", content: text });

        const answer = faqAnswer(text) ?? await askAI(history);
        const escalate = answer === null || /\bESCALADE\b/i.test(answer);
        if (escalate) console.error("support escalade", answer === null ? "aucune IA disponible" : "IA a répondu ESCALADE");
        const { data: am } = await db.from(SUPPORT_TABLE)
          .insert({
            user_id: user.id, user_email: user.email, sender: "ai",
            body: escalate ? ESCALATE_REPLY : answer, needs_admin: escalate,
          })
          .select("id,sender,body,created_at").single();
        return ok({ messages: am ? [um, am] : [um], admin_online: online });
      }

      case "support_inbox": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        return ok(await buildInbox());
      }

      case "support_reply": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const text = String(p.text ?? "").trim().slice(0, 2000);
        const target = String(p.user_id ?? "");
        if (!text || !target) return fail("Message ou client manquant.");
        const { data: m, error } = await db.from(SUPPORT_TABLE)
          .insert({ user_id: target, sender: "admin", body: text })
          .select("id,sender,body,created_at").single();
        if (error || !m) return fail("Envoi impossible, réessayez.");
        await db.from("sebpay_presence").upsert({ id: 1, admin_last_seen: new Date().toISOString() });
        return ok({ message: m });
      }

      case "users_list": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const { data } = await db.from("sebpay_users").select("user_id,email,first_name,last_name,role,approved,created_at")
          .order("approved", { ascending: true }).order("created_at", { ascending: false }).limit(300);
        return ok(data ?? []);
      }

      case "users_pending": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const { data } = await db.from("sebpay_users").select("user_id,email,first_name,last_name,created_at")
          .eq("approved", false).order("created_at", { ascending: true }).limit(100);
        return ok(data ?? []);
      }

      case "user_approve": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const target = String(p.user_id ?? "");
        if (!target) return fail("Compte manquant.");
        const { data: u } = await db.from("sebpay_users").update({ approved: true, approved_at: new Date().toISOString() })
          .eq("user_id", target).eq("approved", false).select("user_id").maybeSingle();
        if (!u) return fail("Inscription introuvable ou déjà validée.");
        // l'administrateur remplace la confirmation par email : le compte peut se connecter tout de suite
        await db.auth.admin.updateUserById(target, { email_confirm: true });
        return ok({});
      }

      case "user_reject": {
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const target = String(p.user_id ?? "");
        if (!target || target === user.id) return fail("Compte invalide.");
        // on retire seulement l'accès à Kouamépay : le compte d'authentification est partagé avec d'autres applications
        const { data: u } = await db.from("sebpay_users").delete().eq("user_id", target).eq("approved", false)
          .select("user_id").maybeSingle();
        if (!u) return fail("Inscription introuvable ou déjà validée.");
        return ok({});
      }

      default:
        return fail("Action inconnue.");
    }
  } catch (e) {
    console.error(e);
    return fail(e instanceof Error ? e.message : "Erreur serveur.");
  }
});
