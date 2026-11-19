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
const MIN_PAYOUT = 300; // SebPay refuse les décaissements < 300

const reply = (body: unknown) =>
  new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
const ok = (data: unknown) => reply({ ok: true, data });
const fail = (error: string) => reply({ ok: false, error });

class SebpayError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

type Keys = { base_url: string; public_key: string; secret_key: string };

async function loadKeys(): Promise<Keys> {
  const { data, error } = await db.from("sebpay_keys").select("*").eq("id", 1).maybeSingle();
  if (error || !data) throw new Error("Clés SebPay introuvables (table sebpay_keys).");
  return data as Keys;
}

async function sebpay(keys: Keys, method: string, path: string, body?: unknown) {
  const res = await fetch(`${keys.base_url}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-Public-Key": keys.public_key,
      "X-Secret-Key": keys.secret_key,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok || j.success === false) throw new SebpayError(j.message || `Erreur SebPay (HTTP ${res.status})`, res.status);
  return j.data !== undefined ? j.data : j;
}

// ---- CryptAPI : paiements BNB pour les pays que SebPay n'accepte pas.
// Chaque paiement obtient une adresse unique ; CryptAPI transfère automatiquement le BNB reçu vers BNB_ADDRESS
// (frais CryptAPI déduits, environ 1 %) et appelle la fonction "cryptapi-webhook".
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

// ---- Commission : un taux total (la vôtre + celle de SebPay), réglé dans sebpay_settings
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

// Versement automatique : quand un encaissement réussit, le net est envoyé au numéro qui reçoit.
// La réservation (payout_reference) garantit un seul versement, même si le webhook et le suivi arrivent en même temps.
// En cas de doute (erreur réseau après l'envoi), on ne réessaie JAMAIS tout seul : pas de double versement possible.
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
    const d = await sebpay(await loadKeys(), "POST", "/payouts", {
      recipient_name: t.recipient_name || "Bénéficiaire", phone: t.recipient_phone, operator: t.recipient_operator,
      country: rCountry, amount, currency: rCurrency, external_reference: reference,
      callback_url: `${SUPABASE_URL}/functions/v1/sebpay-webhook`,
    });
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


// ---- Service client : réponses automatiques par IA gratuite (sans clé), avec bascule entre 4 services
const SUPPORT_TABLE = "sebpay_support";
const ONLINE_WINDOW_MS = 90_000; // l'admin est « en ligne » s'il a donné signe de vie il y a moins de 90 s
const ESCALATE_REPLY =
  "Votre problème est pris en compte, veuillez patienter, l'administrateur va vous répondre dans un instant.";

const SUPPORT_PROMPT = `Tu es le service client de l'application SebPay. Réponds toujours dans la langue du client (français par défaut), en 2 à 5 phrases courtes, de façon claire et polie.
Ce que tu sais de SebPay :
- Onglet « Encaisser » : on choisit le pays, le réseau Mobile Money (liste fournie par SebPay), le numéro qui paie (débité de son compte Mobile Money), le numéro qui reçoit l'argent (avec son réseau et le nom du bénéficiaire) et le montant ; une fois le paiement réussi, le net est versé automatiquement au numéro qui reçoit. Le client confirme le paiement sur son téléphone (parfois avec un code OTP). Le statut passe de « En attente » à « Réussi » ou « Refusé ».
- Onglet « Décaisser » (administrateur seulement) : envoi d'argent depuis le wallet SebPay vers un numéro Mobile Money. Si le décaissement est refusé, SebPay recrédite le wallet automatiquement.
- Pays non accepté par SebPay : l'application propose le paiement en BNB (BNB Smart Chain) via CryptAPI : on saisit un montant en USD, l'application affiche une adresse unique et le montant exact en BNB à envoyer.
- Commission : 5 % au total, indiquée sur le reçu avant confirmation. Le client BNB indique aussi le réseau et le numéro qui recevront l'argent ; 2 minutes après, il envoie ses preuves (images) à l'administrateur, qui décaisse à la main puis marque « Payé » ; le client peut signaler « argent non reçu » depuis l'Historique.
- Onglet « Historique » : les dernières transactions et leur statut. Un encaissement réussi peut être remboursé par l'administrateur.
- Un paiement « En attente » peut durer quelques minutes : conseille de patienter et de vérifier le statut dans l'Historique.
Règles strictes :
- Ne demande JAMAIS de mot de passe, de code secret ni de clé.
- N'invente rien : si tu n'es pas sûr, si la demande concerne une transaction précise, un remboursement, de l'argent débité sans résultat, un compte ou une action de l'administrateur, réponds UNIQUEMENT par le mot : ESCALADE
- Ne promets jamais un remboursement ou un délai.`;

type Provider = { name: string; url: string; model?: string; pick?: (ids: string[]) => string | undefined };

const PROVIDERS: Provider[] = [
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
    headers: { "Content-Type": "application/json", Authorization: "Bearer unused" },
    body: JSON.stringify({ model, messages, temperature: 0.3, max_tokens: 400, stream: false }),
    signal: AbortSignal.timeout(14000),
  });
  if (!res.ok) return null; // refus (quota, modèle indisponible…) : on passera au service suivant
  const j = await res.json().catch(() => null);
  let t = String(j?.choices?.[0]?.message?.content ?? "").replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  if (!t) return null;
  return t.slice(0, 1500);
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
  const { data: prof } = await db.from("sebpay_users").select("role").eq("user_id", data.user.id).maybeSingle();
  if (!prof) return null;
  return { id: data.user.id as string, role: prof.role as string, email: (data.user.email ?? "") as string };
}

Deno.serve(async (req) => {
  try {
    const user = await getUser(req);
    if (!user) return fail("Compte non autorisé pour cette application.");
    const p = await req.json();
    const callback = `${SUPABASE_URL}/functions/v1/sebpay-webhook`;
    const isAdmin = user.role === "admin";

    switch (p.action) {
      case "me":
        return reply({ ok: true, role: user.role });

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

      case "crypto_create": {
        const usd = Number(p.amount_usd);
        if (!(usd >= 1)) return fail("Montant minimum : 1 USD.");
        if (usd > 100000) return fail("Montant trop élevé.");
        if (!COUNTRIES[p.country]) return fail("Pays non supporté.");
        const recipientOperator = String(p.recipient_operator ?? "").trim().slice(0, 60);
        if (!recipientOperator) return fail("Indiquez le réseau qui recevra l'argent.");
        const recipientPhone = normalizePhone(p.recipient_phone, p.country);
        if (!recipientPhone) return fail("Numéro de téléphone invalide pour ce pays.");
        const ticker = await bnbTicker();
        const expected = await usdToBnb(usd, ticker);
        const reference = `CRY-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        const token = crypto.randomUUID().replaceAll("-", "");
        const cb = `${SUPABASE_URL}/functions/v1/cryptapi-webhook?ref=${reference}&t=${token}`;
        const d = await cryptapi(
          `/${ticker}/create/?callback=${encodeURIComponent(cb)}&address=${BNB_ADDRESS}&pending=0&confirmations=1`,
        );
        if (!d.address_in) throw new Error("CryptAPI n'a pas renvoyé d'adresse.");
        if (Number(d.minimum_transaction_coin ?? 0) > expected) {
          return fail(`Montant trop faible : minimum ${d.minimum_transaction_coin} BNB.`);
        }
        const row = {
          reference, token, ticker, status: "pending", country: String(p.country ?? ""),
          amount_usd: usd, expected_bnb: expected, address_in: d.address_in, address_out: BNB_ADDRESS,
          recipient_operator: recipientOperator, recipient_phone: recipientPhone,
          created_by: user.id,
        };
        const { error } = await db.from(TC).insert(row);
        if (error) return fail("Enregistrement du paiement impossible (table sebpay_crypto créée ?).");
        return ok({
          reference, address_in: d.address_in, expected_bnb: expected, amount_usd: usd, status: "pending",
          recipient_operator: recipientOperator, recipient_phone: recipientPhone,
        });
      }

      case "crypto_status": {
        const { data: t } = await db.from(TC).select("*").eq("reference", String(p.reference ?? "")).maybeSingle();
        if (!t || (!isAdmin && t.created_by !== user.id)) return fail("Paiement introuvable.");
        const { token: _t, ...rest } = t; // le jeton du callback ne sort jamais du serveur
        return ok(rest);
      }

      // ---------------- Décaissement BNB : preuves du client, décaissement manuel par l'administrateur
      case "crypto_history": {
        let q = db.from(TC).select("*").order("created_at", { ascending: false }).limit(50);
        if (!isAdmin) q = q.eq("created_by", user.id);
        const { data, error } = await q;
        if (error) return fail("Lecture de l'historique impossible.");
        return ok((data ?? []).map(({ token: _t, proof_paths, ...r }) => ({
          ...r, type: "crypto", proof_count: (proof_paths ?? []).length,
        })));
      }

      case "crypto_proof": {
        const { data: t } = await db.from(TC).select("*").eq("reference", String(p.reference ?? "")).maybeSingle();
        if (!t || t.created_by !== user.id) return fail("Paiement introuvable.");
        if (t.payout_status === "paid") return fail("Ce paiement est déjà marqué comme payé.");
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
        if (!isAdmin) return fail("Réservé à l'administrateur.");
        const { data } = await db.from(TC).select("*").in("payout_status", ["proof_sent", "reported"])
          .order("proof_sent_at", { ascending: true }).limit(100);
        const out = [];
        for (const r of data ?? []) {
          const { token: _t, proof_paths, ...rest } = r;
          let proofs: string[] = [];
          if ((proof_paths ?? []).length) {
            const { data: signed } = await db.storage.from(PROOF_BUCKET).createSignedUrls(proof_paths, 3600);
            proofs = (signed ?? []).map((x) => x.signedUrl).filter(Boolean) as string[];
          }
          out.push({ ...rest, type: "crypto", proofs });
        }
        return ok(out);
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
        if (payoutAmount < MIN_PAYOUT) return fail(`Montant trop faible : le bénéficiaire doit recevoir au moins ${MIN_PAYOUT} ${rc.currency} (après commission).`);
        // La migration SQL v5 doit être appliquée, sinon le versement automatique serait perdu après l'encaissement
        const chk = await db.from(T).select("recipient_phone,payout_reference").limit(1);
        if (chk.error) return fail("Mise à jour de la base requise : exécutez la partie « v5 » de supabase.sql.");
        const reference = `COL-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        const payload: Record<string, unknown> = {
          amount: pl.charge, currency: c.currency, phone, operator: p.operator, country: p.country,
          external_reference: reference, callback_url: callback,
        };
        if (p.otp_code) payload.otp_code = String(p.otp_code);
        const d = await sebpay(await loadKeys(), "POST", "/collections", payload);
        const fee = Number(d?.fee_amount ?? 0);
        const row = {
          type: "collect", external_reference: reference, sebpay_id: d?.transaction_id ?? null,
          status: normStatus(d?.status), amount: pl.charge, currency: c.currency, phone,
          operator: p.operator, country: p.country, commission_percent: s.percent,
          commission_total: pl.commission, sebpay_fee: fee, my_commission: pl.commission - fee,
          net_amount: pl.net, created_by: user.id,
          recipient_operator: rOp, recipient_phone: rPhone, recipient_name: rName,
          recipient_country: rCountry, recipient_currency: rc.currency, payout_amount: payoutAmount, fx_rate: fx,
        };
        const { data: ins, error } = await db.from(T).insert(row).select("id").single();
        if (error) console.error("insert transaction:", error.message);
        if (row.status === "approved" && ins) await autoPayout({ ...row, id: ins.id });
        return ok(row);
      }

      case "payout": {
        if (!isAdmin) return fail("Seul un administrateur peut décaisser.");
        const c = COUNTRIES[p.country];
        if (!c) return fail("Pays non supporté.");
        const amount = Math.round(Number(p.amount));
        if (!(amount > 0)) return fail("Montant invalide.");
        const s = await loadSettings();
        const pl = plan("payout", amount, s.percent, s.mode);
        if (pl.charge < MIN_PAYOUT) return fail(`Montant minimum de décaissement : ${MIN_PAYOUT} (après commission).`);
        const recipient = String(p.recipient_name ?? "").trim().slice(0, 180);
        if (!recipient) return fail("Nom du bénéficiaire manquant.");
        if (!p.operator) return fail("Opérateur manquant.");
        const phone = normalizePhone(p.phone, p.country);
        if (!phone) return fail("Numéro de téléphone invalide pour ce pays.");
        const reference = `PAY-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        const d = await sebpay(await loadKeys(), "POST", "/payouts", {
          recipient_name: recipient, phone, operator: p.operator, country: p.country,
          amount: pl.charge, currency: c.currency, external_reference: reference, callback_url: callback,
        });
        const fee = Number(d?.fee_amount ?? 0);
        const row = {
          type: "payout", external_reference: reference, sebpay_id: d?.transaction_id ?? null,
          status: normStatus(d?.status), amount: pl.charge, currency: c.currency, phone,
          operator: p.operator, country: p.country, recipient_name: recipient,
          commission_percent: s.percent, commission_total: pl.commission, sebpay_fee: fee,
          my_commission: pl.commission - fee, net_amount: pl.net, created_by: user.id,
        };
        const { error } = await db.from(T).insert(row);
        if (error) console.error("insert transaction:", error.message);
        return ok(row);
      }

      case "refund": {
        // Rembourse un encaissement réussi : renvoie le montant payé au numéro du client
        if (!isAdmin) return fail("Seul un administrateur peut rembourser.");
        const { data: t } = await db.from(T).select("*").eq("external_reference", String(p.reference ?? "")).maybeSingle();
        if (!t || t.type !== "collect" || t.status !== "approved") {
          return fail("Seuls les encaissements réussis peuvent être remboursés.");
        }
        if (!COUNTRIES[t.country]) return fail("Pays non supporté.");
        const reference = `REF-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
        // On réserve d'abord, pour qu'un double clic ne rembourse jamais deux fois
        const { data: claimed } = await db.from(T)
          .update({ refund_reference: reference, refund_note: "Remboursement en cours", updated_at: new Date().toISOString() })
          .eq("id", t.id).is("refund_reference", null).select("id").maybeSingle();
        if (!claimed) return fail("Un remboursement est déjà en cours ou effectué.");
        try {
          const d = await sebpay(await loadKeys(), "POST", "/payouts", {
            recipient_name: "Remboursement client", phone: t.phone, operator: t.operator, country: t.country,
            amount: t.amount, currency: t.currency, external_reference: reference, callback_url: callback,
          });
          const row = {
            type: "payout", external_reference: reference, sebpay_id: d?.transaction_id ?? null,
            status: normStatus(d?.status), amount: t.amount, currency: t.currency, phone: t.phone,
            operator: t.operator, country: t.country, recipient_name: `Remboursement de ${t.external_reference}`,
            sebpay_fee: Number(d?.fee_amount ?? 0), net_amount: t.amount, created_by: user.id,
          };
          const { error } = await db.from(T).insert(row);
          if (error) console.error("insert refund:", error.message);
          await afterStatusChange(reference, row.status);
          return ok(row);
        } catch (e) {
          await db.from(T).update({ refund_reference: null, refund_note: null }).eq("id", t.id);
          throw e;
        }
      }

      case "status": {
        const { data: t } = await db.from(T).select("*").eq("external_reference", String(p.reference ?? "")).maybeSingle();
        if (!t || (!isAdmin && t.created_by !== user.id)) return fail("Transaction introuvable.");
        if (t.status !== "pending") return ok(t);
        const path = `/${t.type === "collect" ? "collections" : "payouts"}/${encodeURIComponent(t.external_reference)}`;
        const d = await sebpay(await loadKeys(), "GET", path);
        const status = normStatus(d?.status);
        if (status === "pending") return ok(t);
        const patch: Record<string, unknown> = { status, updated_at: new Date().toISOString() };
        if (d?.fee_amount != null) {
          patch.sebpay_fee = Number(d.fee_amount);
          if (t.commission_total != null) patch.my_commission = Number(t.commission_total) - Number(d.fee_amount);
        }
        if (status === "rejected" && t.type === "payout" && !t.external_reference.startsWith("REF-")) {
          patch.refund_note = "Wallet recrédité automatiquement par SebPay";
        }
        const { data: upd } = await db.from(T).update(patch).eq("id", t.id).select().single();
        await afterStatusChange(t.external_reference, status);
        if (status === "approved" && t.type === "collect") await autoPayout({ ...t, ...patch });
        const { data: fresh } = await db.from(T).select("*").eq("id", t.id).maybeSingle();
        return ok(fresh ?? upd ?? { ...t, ...patch });
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
          const { sebpay_fee: _f, my_commission: _m, ...rest } = r; // la part de l'admin reste privée
          return rest;
        });
        return ok(rows);
      }


      // ---------------- Service client
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

        const answer = await askAI(history);
        const escalate = answer === null || /\bESCALADE\b/i.test(answer);
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

      default:
        return fail("Action inconnue.");
    }
  } catch (e) {
    console.error(e);
    return fail(e instanceof Error ? e.message : "Erreur serveur.");
  }
});
