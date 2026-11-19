package bj.sebpay.app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Seules infos présentes dans l'app : l'URL du projet Supabase et la clé "anon"
 * (publique par conception). Les clés SebPay, elles, ne sont JAMAIS dans l'app :
 * elles restent dans la base Supabase.
 */
final class Config {
    static final String SUPABASE_URL = "https://nswbnehddpjankmcymsg.supabase.co";
    static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5zd2JuZWhkZHBqYW5rbWN5bXNnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ5NDc3NDYsImV4cCI6MjExMDUyMzc0Nn0.ixDse9h_4RqRVz2sYWVxTe1RN-15umsqPytTWixz_9o";

    /** Pays où Wave est disponible : le réseau « Wave » est toujours proposé, même si SebPay ne le liste pas. */
    static final Set<String> WAVE_COUNTRIES = new HashSet<>(Arrays.asList("SN", "CI", "ML", "BF", "GM", "UG"));

    /**
     * Lien marchand Wave Business par pays (ISO -> lien). Collez ici VOTRE lien, tel que Wave vous le donne,
     * sans le montant : l'app ajoute « ?amount=... » toute seule.
     * Exemple : WAVE_LINKS.put("SN", "https://pay.wave.com/m/M_sn_XXXXXXXX/c/sn/");
     */
    static final Map<String, String> WAVE_LINKS = new HashMap<>();
    static {
        // WAVE_LINKS.put("SN", "https://pay.wave.com/m/M_sn_XXXXXXXX/c/sn/");
        // WAVE_LINKS.put("CI", "https://pay.wave.com/m/M_ci_XXXXXXXX/c/ci/");
    }

    /** Lien Wave du pays avec le montant, ou null si aucun lien n'est configuré. */
    static String waveUrl(String iso, long amount) {
        String base = WAVE_LINKS.get(iso);
        if (base == null || base.trim().isEmpty()) return null;
        base = base.trim();
        return base + (base.contains("?") ? "&" : "?") + "amount=" + amount;
    }

    private Config() {}
}
