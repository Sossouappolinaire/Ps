package bj.sebpay.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Connexion Supabase (Auth) + appels à la fonction Edge "sebpay". */
class Api {

    static class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }

    private static class Res {
        int code;
        String text;
    }

    private final SharedPreferences prefs;

    Api(Context context) {
        prefs = context.getSharedPreferences("sebpay_session", Context.MODE_PRIVATE);
    }

    boolean isLoggedIn() {
        return !prefs.getString("refresh", "").isEmpty();
    }

    String getEmail() {
        return prefs.getString("email", "");
    }

    void logout() {
        prefs.edit().clear().apply();
    }

    // ---------------------------------------------------------------- HTTP

    private Res http(String method, String url, String body, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
        c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        Res r = new Res();
        r.code = c.getResponseCode();
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        r.text = readAll(in);
        c.disconnect();
        return r;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static JSONObject parse(String text) {
        try {
            return new JSONObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    // ---------------------------------------------------------------- Session

    void login(String email, String password) throws Exception {
        JSONObject b = new JSONObject().put("email", email).put("password", password);
        Res r = http("POST", Config.SUPABASE_URL + "/auth/v1/token?grant_type=password", b.toString(), null);
        JSONObject j = parse(r.text);
        if (r.code != 200) {
            String m = j.optString("error_description", j.optString("msg", "Connexion impossible."));
            if (m.toLowerCase().contains("invalid login")) m = "Email ou mot de passe incorrect.";
            throw new ApiException(m);
        }
        saveSession(j, email);
    }

    /** Inscription (nom, prénom, email, mot de passe). Retourne true si connecté directement, false si l'email doit d'abord être confirmé. */
    boolean signUp(String firstName, String lastName, String email, String password) throws Exception {
        JSONObject data = new JSONObject().put("first_name", firstName).put("last_name", lastName);
        JSONObject b = new JSONObject().put("email", email).put("password", password).put("data", data);
        Res r = http("POST", Config.SUPABASE_URL + "/auth/v1/signup", b.toString(), null);
        JSONObject j = parse(r.text);
        if (r.code >= 400) {
            String m = j.optString("msg", j.optString("error_description", j.optString("message", "Inscription impossible.")));
            String l = m.toLowerCase();
            if (l.contains("already registered") || l.contains("already been registered")) m = "Un compte existe déjà avec cet email.";
            else if (l.contains("password") && (l.contains("least") || l.contains("weak"))) m = "Mot de passe trop faible (6 caractères minimum).";
            else if (l.contains("rate limit")) m = "Trop de tentatives, réessayez dans quelques minutes.";
            throw new ApiException(m);
        }
        if (!j.optString("access_token").isEmpty()) {
            saveSession(j, email);
            return true;
        }
        return false;
    }

    private void saveSession(JSONObject j, String email) {
        long expires = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000L;
        SharedPreferences.Editor e = prefs.edit();
        e.putString("access", j.optString("access_token"));
        e.putString("refresh", j.optString("refresh_token"));
        e.putLong("expires", expires);
        if (email != null) e.putString("email", email);
        e.apply();
    }

    private String accessToken() throws Exception {
        if (System.currentTimeMillis() < prefs.getLong("expires", 0) - 60000L) {
            return prefs.getString("access", "");
        }
        String refresh = prefs.getString("refresh", "");
        if (refresh.isEmpty()) throw new ApiException("Session expirée, reconnectez-vous.");
        JSONObject b = new JSONObject().put("refresh_token", refresh);
        Res r = http("POST", Config.SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token", b.toString(), null);
        if (r.code != 200) {
            logout();
            throw new ApiException("Session expirée, reconnectez-vous.");
        }
        saveSession(parse(r.text), null);
        return prefs.getString("access", "");
    }

    // ---------------------------------------------------------------- Fonction Edge

    private JSONObject call(JSONObject payload) throws Exception {
        Res r = http("POST", Config.SUPABASE_URL + "/functions/v1/sebpay", payload.toString(), accessToken());
        JSONObject j = parse(r.text);
        if (r.code == 401) throw new ApiException("Accès refusé : session invalide.");
        if (!j.optBoolean("ok", false)) {
            throw new ApiException(j.optString("error", "Erreur serveur (" + r.code + ").").replaceAll("(?i)seb\\s?pay", "Kouamépay"));
        }
        return j;
    }

    private boolean approved = true;

    String me() throws Exception {
        JSONObject j = call(new JSONObject().put("action", "me"));
        approved = j.optBoolean("approved", true);
        return j.optString("role", "agent");
    }

    /** false : inscription pas encore validée par l'administrateur. */
    boolean isApproved() {
        return approved;
    }

    JSONArray usersList() throws Exception {
        JSONArray a = call(new JSONObject().put("action", "users_list")).optJSONArray("data");
        return a != null ? a : new JSONArray();
    }

    JSONArray usersPending() throws Exception {
        JSONArray a = call(new JSONObject().put("action", "users_pending")).optJSONArray("data");
        return a != null ? a : new JSONArray();
    }

    void userApprove(String userId) throws Exception {
        call(new JSONObject().put("action", "user_approve").put("user_id", userId));
    }

    void userReject(String userId) throws Exception {
        call(new JSONObject().put("action", "user_reject").put("user_id", userId));
    }

    JSONArray operators(String country) throws Exception {
        JSONObject j = call(new JSONObject().put("action", "operators").put("country", country));
        JSONArray a = j.optJSONArray("data");
        return a != null ? a : new JSONArray();
    }

    /** rcp : numéro qui reçoit {operator, phone, name} ; le net lui est versé automatiquement après l'encaissement. */
    JSONObject collect(String country, String operator, String phone, long amount, String otp, JSONObject rcp) throws Exception {
        JSONObject p = new JSONObject()
                .put("action", "collect").put("country", country).put("operator", operator)
                .put("phone", phone).put("amount", amount);
        if (rcp != null) {
            p.put("recipient_country", rcp.optString("country"))
                    .put("recipient_operator", rcp.optString("operator")).put("recipient_phone", rcp.optString("phone"))
                    .put("recipient_name", rcp.optString("name"));
        }
        if (otp != null && !otp.isEmpty()) p.put("otp_code", otp);
        return call(p).getJSONObject("data");
    }

    JSONObject payout(String country, String operator, String phone, String name, long amount) throws Exception {
        JSONObject p = new JSONObject()
                .put("action", "payout").put("country", country).put("operator", operator)
                .put("phone", phone).put("recipient_name", name).put("amount", amount);
        return call(p).getJSONObject("data");
    }

    JSONObject status(String reference) throws Exception {
        return call(new JSONObject().put("action", "status").put("reference", reference)).getJSONObject("data");
    }

    JSONObject quote(String type, long amount) throws Exception {
        return call(new JSONObject().put("action", "quote").put("type", type).put("amount", amount)).getJSONObject("data");
    }

    /** Aperçu d'un encaissement avec versement dans un autre pays : montant versé et taux de change. */
    JSONObject quote(String type, long amount, String country, String recipientCountry) throws Exception {
        return call(new JSONObject().put("action", "quote").put("type", type).put("amount", amount)
                .put("country", country).put("recipient_country", recipientCountry)).getJSONObject("data");
    }

    /** Vérifie avant l'encaissement que l'encaissement ET le versement sont possibles (API, serveur, base). Retourne la liste des problèmes (vide = prêt). */
    JSONArray preflight(String country, String operator, JSONObject rcp) throws Exception {
        JSONObject p = new JSONObject().put("action", "preflight").put("country", country).put("operator", operator);
        if (rcp != null) {
            p.put("recipient_country", rcp.optString("country")).put("recipient_operator", rcp.optString("operator"));
        }
        JSONArray a = call(p).getJSONObject("data").optJSONArray("problems");
        return a != null ? a : new JSONArray();
    }

    /** Vérifie avant un décaissement que SebPay répond pour ce pays et ce réseau. Retourne la liste des problèmes (vide = prêt). */
    JSONArray preflightPayout(String country, String operator) throws Exception {
        JSONObject p = new JSONObject().put("action", "preflight").put("type", "payout")
                .put("country", country).put("operator", operator);
        JSONArray a = call(p).getJSONObject("data").optJSONArray("problems");
        return a != null ? a : new JSONArray();
    }

    JSONObject refund(String reference) throws Exception {
        return call(new JSONObject().put("action", "refund").put("reference", reference)).getJSONObject("data");
    }

    JSONArray history() throws Exception {
        JSONArray a = call(new JSONObject().put("action", "history")).optJSONArray("data");
        return a != null ? a : new JSONArray();
    }

    /** Paiement BNB (CryptAPI) : crée une adresse unique et le montant exact en BNB, avec le réseau et le numéro qui recevront l'argent. */
    JSONObject cryptoCreate(double amountUsd, String country, String recipientOperator, String recipientPhone) throws Exception {
        return call(new JSONObject().put("action", "crypto_create").put("amount_usd", amountUsd).put("country", country)
                .put("recipient_operator", recipientOperator).put("recipient_phone", recipientPhone))
                .getJSONObject("data");
    }

    JSONObject cryptoStatus(String reference) throws Exception {
        return call(new JSONObject().put("action", "crypto_status").put("reference", reference)).getJSONObject("data");
    }

    // ---------------------------------------------------------------- Décaissement BNB

    /** Paiements BNB du client (tous pour l'administrateur), avec statut du décaissement. */
    JSONArray cryptoHistory() throws Exception {
        JSONArray a = call(new JSONObject().put("action", "crypto_history")).optJSONArray("data");
        return a != null ? a : new JSONArray();
    }

    /** Une preuve (JPEG en base64) ; le serveur exige 2 minutes après la création du paiement. */
    JSONObject cryptoProof(String reference, String jpegBase64) throws Exception {
        return call(new JSONObject().put("action", "crypto_proof").put("reference", reference)
                .put("images", new JSONArray().put(jpegBase64))).getJSONObject("data");
    }

    /** Administrateur : décaissements BNB avec preuves (à traiter ou signalés). */
    JSONArray cryptoList() throws Exception {
        JSONArray a = call(new JSONObject().put("action", "crypto_list")).optJSONArray("data");
        return a != null ? a : new JSONArray();
    }

    /** Administrateur : l'argent a été envoyé à la main, marquer « Payé ». */
    void cryptoPay(String reference) throws Exception {
        call(new JSONObject().put("action", "crypto_pay").put("reference", reference));
    }

    /** Client : argent non reçu. */
    void cryptoReport(String reference, String note) throws Exception {
        call(new JSONObject().put("action", "crypto_report").put("reference", reference).put("note", note));
    }

    /** Télécharge une image de preuve (lien signé temporaire). */
    byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        if (c.getResponseCode() >= 400) throw new ApiException("Image indisponible.");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- Service client

    /** Signe de vie de l'administrateur (ignoré pour les autres rôles). */
    void ping() throws Exception {
        call(new JSONObject().put("action", "ping"));
    }

    /** {admin_online, pending (admin seulement)} */
    JSONObject supportStatus() throws Exception {
        return call(new JSONObject().put("action", "support_status")).getJSONObject("data");
    }

    /** {messages, admin_online} ; userId : conversation ouverte par l'admin (null = la sienne). */
    JSONObject supportHistory(String userId) throws Exception {
        JSONObject p = new JSONObject().put("action", "support_history");
        if (userId != null) p.put("user_id", userId);
        return call(p).getJSONObject("data");
    }

    /** {messages: [message du client, réponse de l'IA], admin_online} */
    JSONObject supportSend(String text) throws Exception {
        return call(new JSONObject().put("action", "support_send").put("text", text)).getJSONObject("data");
    }

    JSONArray supportInbox() throws Exception {
        JSONArray a = call(new JSONObject().put("action", "support_inbox")).optJSONArray("data");
        return a != null ? a : new JSONArray();
    }

    JSONObject supportReply(String userId, String text) throws Exception {
        return call(new JSONObject().put("action", "support_reply").put("user_id", userId).put("text", text))
                .getJSONObject("data");
    }
}
