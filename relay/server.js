// Kouamépay — relais à IP fixe pour SebPay (Render, Node 18+, aucune dépendance, AUCUNE variable obligatoire).
// Les fonctions Edge Supabase sortent avec des IP qui changent ; SebPay n'accepte que des IP autorisées.
// Ce serveur transmet les appels à SebPay depuis l'IP fixe de Render.
// Il ne stocke aucune clé : les clés SebPay arrivent dans chaque appel (depuis Supabase) et c'est SebPay qui les valide.
// Variables facultatives : SEBPAY_BASE_URL (défaut ci-dessous), RELAY_TOKEN (si défini, exigé dans Authorization: Bearer).
const http = require("http");
const crypto = require("crypto");

const BASE = (process.env.SEBPAY_BASE_URL || "https://newapi.sebpay.bj/api/v1").replace(/\/+$/, "");
const TOKEN = process.env.RELAY_TOKEN || "";
const PORT = process.env.PORT || 10000;

const same = (a, b) => { const x = Buffer.from(a), y = Buffer.from(b); return x.length === y.length && crypto.timingSafeEqual(x, y); };
const json = (res, code, obj) => { res.writeHead(code, { "Content-Type": "application/json" }); res.end(JSON.stringify(obj)); };

async function outIp() {
  for (const u of ["https://api.ipify.org", "https://ifconfig.me/ip", "https://icanhazip.com"]) {
    try {
      const t = (await (await fetch(u, { signal: AbortSignal.timeout(5000) })).text()).trim();
      if (/^[0-9a-fA-F:.]{7,45}$/.test(t)) return t;
    } catch (_) {}
  }
  return "";
}

http.createServer(async (req, res) => {
  try {
    const host = req.headers["x-forwarded-host"] || req.headers.host || "";
    const url = "https://" + host;
    if (req.method === "GET" && req.url === "/ip") return json(res, 200, { ip: await outIp(), url });
    if (req.method === "GET" && (req.url === "/" || req.url === "/health")) {
      const ip = await outIp();
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      return res.end(`<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>Relais Kouamépay</title>
<body style="font-family:sans-serif;padding:20px;max-width:520px;margin:auto">
<h2>Relais Kouamépay ✔</h2>
<p>À ajouter dans SebPay → Adresses IP autorisées :</p>
<p><b>Adresse IP</b><br><code style="font-size:20px">${ip || "indisponible, rechargez"}</code></p>
<p><b>Site web</b><br><code style="font-size:16px">${url}</code></p>
<p style="color:#666;font-size:13px">Render peut utiliser plusieurs IP de sortie dans la même région : ajoutez plutôt la plage ${ip ? ip.replace(/\.\d+$/, ".0/24") : ""}.</p></body>`);
    }
    if (req.method !== "POST" || req.url !== "/relay") return json(res, 404, { error: "not found" });
    if (TOKEN && !same((req.headers["authorization"] || "").replace(/^Bearer\s+/i, ""), TOKEN)) return json(res, 401, { error: "non autorisé" });

    let raw = "";
    for await (const c of req) { raw += c; if (raw.length > 100000) return json(res, 413, { error: "trop gros" }); }
    const { method, path, body } = JSON.parse(raw || "{}");
    if (!/^\/(operators|collections|payouts)(\/|\?|$)/.test(String(path))) return json(res, 400, { error: "chemin refusé" });
    if (!req.headers["x-public-key"] || !req.headers["x-secret-key"]) return json(res, 401, { error: "clés manquantes" });
    const m = method === "POST" ? "POST" : "GET";

    const up = await fetch(BASE + path, {
      method: m,
      headers: { "Content-Type": "application/json", "X-Public-Key": String(req.headers["x-public-key"]), "X-Secret-Key": String(req.headers["x-secret-key"]) },
      body: m === "POST" && body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(45000),
    });
    res.writeHead(up.status, { "Content-Type": "application/json" });
    res.end(await up.text());
  } catch (e) {
    json(res, 502, { error: "relais : " + (e && e.message ? e.message : e) });
  }
}).listen(PORT, () => console.log("relais prêt sur " + PORT));
