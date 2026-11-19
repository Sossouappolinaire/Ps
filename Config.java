package bj.sebpay.app;

/**
 * Seules infos présentes dans l'app : l'URL du projet Supabase et la clé "anon"
 * (publique par conception). Les clés SebPay, elles, ne sont JAMAIS dans l'app :
 * elles restent dans la base Supabase.
 */
final class Config {
    static final String SUPABASE_URL = "https://nswbnehddpjankmcymsg.supabase.co";
    static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5zd2JuZWhkZHBqYW5rbWN5bXNnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ5NDc3NDYsImV4cCI6MjExMDUyMzc0Nn0.ixDse9h_4RqRVz2sYWVxTe1RN-15umsqPytTWixz_9o";

    private Config() {}
}
