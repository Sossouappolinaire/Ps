package bj.sebpay.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends Activity {

    private static final String PREFS_UI = "sebpay_ui";

    /** Devises à grosses coupures : on propose des montants rapides adaptés. */
    private static final Set<String> BIG_CURRENCIES = new HashSet<>(Arrays.asList(
            "XOF", "XAF", "GNF", "CDF", "BIF", "KMF", "DJF", "RWF", "UGX", "TZS", "MGA", "IDR", "VND",
            "KHR", "LAK", "PYG", "MMK", "IRR", "LBP", "SYP", "YER", "UZS", "KRW", "COP", "NGN", "SLE", "MWK"));

    private Api api;
    private Kit k;
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, JSONArray> opsCache = new HashMap<>();

    private LinearLayout root;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView headTitle, headSub;
    private final List<String> navKeys = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private final List<Kit.IconView> navIcons = new ArrayList<>();

    private String role = "agent";
    private String tab = "collect";
    private Runnable poller;
    private int pollCount;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        api = new Api(this);
        k = new Kit(this);
        getWindow().setStatusBarColor(Kit.PRIMARY_DARK);
        root = k.vbox();
        root.setBackgroundColor(Kit.BG);
        setContentView(root);
        if (api.isLoggedIn()) loadProfile();
        else showLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        appVisible = true;
        if (api.isLoggedIn() && "admin".equals(role) && content != null) startBeat();
    }

    @Override
    protected void onPause() {
        appVisible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopBeat();
        stopPolling();
        io.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ utilitaires

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private String errMsg(Exception e) {
        if (e instanceof Api.ApiException) return e.getMessage();
        return "Erreur réseau" + (e.getMessage() == null ? "." : " : " + e.getMessage());
    }

    private void handleError(Exception e, StatusBox target) {
        if (!api.isLoggedIn()) {
            showLogin();
            toast(errMsg(e));
            return;
        }
        if (target != null) target.err(errMsg(e));
        else toast(errMsg(e));
    }

    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    private static String group(String digits, String sep) {
        StringBuilder sb = new StringBuilder();
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) sb.append(sep);
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private static String fmt(long v) {
        return (v < 0 ? "-" : "") + group(String.valueOf(Math.abs(v)), " ");
    }

    private String money(JSONObject t) {
        return fmt(t.optLong("amount")) + " " + str(t, "currency");
    }

    /** Temps restant avant l'expiration (24 h) d'un paiement BNB non reçu. */
    private String leftText(String createdIso) {
        long left = parseMs(createdIso) + 24L * 3600_000L - System.currentTimeMillis();
        if (left <= 0) return "expiré";
        long h = left / 3600_000L, m = (left % 3600_000L) / 60_000L;
        return h > 0 ? h + " h " + m + " min" : m + " min";
    }

    private String when(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso.substring(0, 19));
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    private void copy(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private LinearLayout.LayoutParams withTop(LinearLayout.LayoutParams p, float topDp) {
        p.topMargin = k.dp(topDp);
        return p;
    }

    /** Feuille qui monte du bas de l'écran (sélecteur, confirmation). */
    private Dialog sheet(View contentView, int heightPx) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        float r = k.dp(24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Kit.SURFACE);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        contentView.setBackground(bg);
        d.setContentView(contentView);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    heightPx > 0 ? heightPx : ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    /** Boîte de confirmation simple (remboursement, etc.). */
    private void confirmSheet(String title, String message, String confirmLabel, int color, final Runnable onConfirm) {
        LinearLayout box = k.vbox();
        box.setPadding(k.dp(20), k.dp(20), k.dp(20), k.dp(20));
        box.addView(k.text(title, 20, Kit.TEXT, true));
        box.addView(k.text(message, 15, Kit.MUTED, false), k.top(8));
        LinearLayout btns = k.hbox();
        TextView no = k.outlineButton("Annuler", Kit.MUTED);
        TextView yes = k.button(confirmLabel, color, Color.WHITE);
        btns.addView(no, withTop(k.weight(1), 0));
        LinearLayout.LayoutParams yp = k.weight(1);
        yp.leftMargin = k.dp(10);
        btns.addView(yes, yp);
        box.addView(btns, k.top(20));
        final Dialog d = sheet(box, 0);
        no.setOnClickListener(v -> d.dismiss());
        yes.setOnClickListener(v -> {
            d.dismiss();
            onConfirm.run();
        });
        d.show();
    }

    // ------------------------------------------------------------------ zone d'état (chargement / succès / erreur)

    private final class StatusBox {
        final LinearLayout box = k.hbox();
        final ProgressBar pb = new ProgressBar(MainActivity.this);
        final TextView tv = k.text("", 14, Kit.TEXT, true);

        StatusBox() {
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12));
            box.setVisibility(View.GONE);
            box.addView(pb, k.size(20, 20));
            LinearLayout.LayoutParams tp = k.weight(1);
            tp.leftMargin = k.dp(12);
            box.addView(tv, tp);
        }

        private void show(String m, int bg, int fg, boolean spin) {
            box.setBackground(k.box(bg, 0, 14));
            tv.setTextColor(fg);
            tv.setText(m);
            pb.setIndeterminateTintList(ColorStateList.valueOf(fg));
            pb.setVisibility(spin ? View.VISIBLE : View.GONE);
            box.setVisibility(View.VISIBLE);
        }

        void loading(String m) { show(m, Kit.PRIMARY_SOFT, Kit.PRIMARY, true); }

        void ok(String m) { show("✓  " + m, Kit.PRIMARY_SOFT, Kit.PRIMARY, false); }

        void err(String m) { show(m, Kit.RED_SOFT, Kit.RED, false); }

        void warn(String m) { show(m, Kit.AMBER_SOFT, Kit.AMBER, false); }

        void hide() { box.setVisibility(View.GONE); }
    }

    // ------------------------------------------------------------------ connexion

    private void showLogin() {
        stopPolling();
        root.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = k.vbox();

        LinearLayout hero = k.vbox();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(k.headerGradient(34));
        hero.setPadding(k.dp(24), k.dp(56), k.dp(24), k.dp(84));
        TextView logo = k.text("S", 32, Kit.PRIMARY, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(k.box(Color.WHITE, 0, 22));
        hero.addView(logo, k.size(70, 70));
        TextView name = k.text("Kouamépay", 30, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        hero.addView(name, k.top(14));
        TextView tag = k.text("Encaissez et décaissez en Mobile Money, partout dans le monde.", 14, 0xCCFFFFFF, false);
        tag.setGravity(Gravity.CENTER);
        hero.addView(tag, k.top(6));
        col.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = k.card();
        card.setPadding(k.dp(20), k.dp(22), k.dp(20), k.dp(22));
        LinearLayout.LayoutParams cp = k.top(-48);
        cp.leftMargin = k.dp(16);
        cp.rightMargin = k.dp(16);
        col.addView(card, cp);

        card.addView(k.text("Connexion", 22, Kit.TEXT, true));
        card.addView(k.text("Accédez à votre espace sécurisé.", 14, Kit.MUTED, false), k.top(2));
        final EditText email = k.field("Adresse email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText pass = k.password("Mot de passe");
        final StatusBox msg = new StatusBox();
        final TextView go = k.button("Se connecter", Kit.PRIMARY, Color.WHITE);
        card.addView(email, k.top(20));
        card.addView(pass, k.top(10));
        card.addView(go, k.top(18));
        card.addView(msg.box, k.top(12));
        TextView toSignup = k.text("Pas de compte ?  S'inscrire", 15, Kit.PRIMARY, true);
        toSignup.setGravity(Gravity.CENTER);
        toSignup.setPadding(k.dp(8), k.dp(14), k.dp(8), k.dp(6));
        toSignup.setOnClickListener(v -> showSignup());
        card.addView(toSignup, k.top(8));

        TextView foot = k.text("Connexion chiffrée · Mobile Money · BNB", 12, Kit.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        col.addView(foot, k.top(20));

        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        go.setOnClickListener(v -> {
            final String em = email.getText().toString().trim();
            final String pw = pass.getText().toString();
            if (em.isEmpty() || pw.isEmpty()) {
                msg.warn("Renseignez l'email et le mot de passe.");
                return;
            }
            k.busy(go, true);
            msg.loading("Connexion en cours…");
            io.execute(() -> {
                try {
                    api.login(em, pw);
                    role = api.me();
                    main.post(this::showShell);
                } catch (Exception e) {
                    if (e instanceof Api.ApiException) api.logout();
                    main.post(() -> {
                        msg.err(errMsg(e));
                        k.busy(go, false);
                    });
                }
            });
        });
    }

    // ------------------------------------------------------------------ inscription

    private void showSignup() {
        stopPolling();
        root.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = k.vbox();

        LinearLayout hero = k.vbox();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(k.headerGradient(34));
        hero.setPadding(k.dp(24), k.dp(40), k.dp(24), k.dp(84));
        TextView back = k.text("←  Retour à l'accueil", 14, Color.WHITE, true);
        back.setPadding(k.dp(14), k.dp(8), k.dp(14), k.dp(8));
        back.setBackground(k.box(0x33FFFFFF, 0, 16));
        back.setOnClickListener(v -> showLogin());
        hero.addView(back, k.wrap());
        TextView name = k.text("Créer un compte", 28, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        hero.addView(name, k.top(22));
        TextView tag = k.text("Rejoignez Kouamépay en quelques secondes.", 14, 0xCCFFFFFF, false);
        tag.setGravity(Gravity.CENTER);
        hero.addView(tag, k.top(6));
        col.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = k.card();
        card.setPadding(k.dp(20), k.dp(22), k.dp(20), k.dp(22));
        LinearLayout.LayoutParams cp = k.top(-48);
        cp.leftMargin = k.dp(16);
        cp.rightMargin = k.dp(16);
        col.addView(card, cp);

        card.addView(k.text("Inscription", 22, Kit.TEXT, true));
        final EditText last = k.field("Nom", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        final EditText first = k.field("Prénom", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        final EditText email = k.field("Adresse email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText pass = k.password("Mot de passe (6 caractères minimum)");
        final StatusBox msg = new StatusBox();
        final TextView go = k.button("Créer mon compte", Kit.PRIMARY, Color.WHITE);
        card.addView(last, k.top(18));
        card.addView(first, k.top(10));
        card.addView(email, k.top(10));
        card.addView(pass, k.top(10));
        card.addView(go, k.top(18));
        card.addView(msg.box, k.top(12));

        TextView toLogin = k.text("Déjà un compte ?  Connexion", 15, Kit.PRIMARY, true);
        toLogin.setGravity(Gravity.CENTER);
        toLogin.setPadding(k.dp(8), k.dp(14), k.dp(8), k.dp(6));
        toLogin.setOnClickListener(v -> showLogin());
        card.addView(toLogin, k.top(8));

        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        go.setOnClickListener(v -> {
            final String ln = last.getText().toString().trim();
            final String fn = first.getText().toString().trim();
            final String em = email.getText().toString().trim();
            final String pw = pass.getText().toString();
            if (ln.isEmpty() || fn.isEmpty() || em.isEmpty() || pw.isEmpty()) {
                msg.warn("Renseignez le nom, le prénom, l'email et le mot de passe.");
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(em).matches()) {
                msg.warn("Adresse email invalide.");
                return;
            }
            if (pw.length() < 6) {
                msg.warn("Le mot de passe doit contenir au moins 6 caractères.");
                return;
            }
            k.busy(go, true);
            msg.loading("Création du compte…");
            io.execute(() -> {
                try {
                    boolean connected = api.signUp(fn, ln, em, pw);
                    if (connected) {
                        role = api.me();
                        main.post(this::showShell);
                    } else {
                        main.post(() -> {
                            toast("Compte créé. L'administrateur doit valider votre inscription, puis connectez-vous.");
                            showLogin();
                        });
                    }
                } catch (Exception e) {
                    if (e instanceof Api.ApiException && api.isLoggedIn()) api.logout();
                    main.post(() -> {
                        msg.err(errMsg(e));
                        k.busy(go, false);
                    });
                }
            });
        });
    }

    private void loadProfile() {
        root.removeAllViews();
        LinearLayout wait = k.vbox();
        wait.setGravity(Gravity.CENTER);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Kit.PRIMARY));
        wait.addView(pb, k.size(36, 36));
        wait.addView(k.text("Chargement…", 15, Kit.MUTED, false), k.top(12));
        root.addView(wait, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        io.execute(() -> {
            try {
                role = api.me();
                main.post(this::showShell);
            } catch (Exception e) {
                if (e instanceof Api.ApiException) api.logout();
                main.post(() -> {
                    showLogin();
                    toast(errMsg(e));
                });
            }
        });
    }

    // ------------------------------------------------------------------ structure principale

    /** Inscription pas encore validée : l'administrateur doit la confirmer. */
    private void showPending() {
        stopPolling();
        stopBeat();
        root.removeAllViews();
        LinearLayout col = k.vbox();
        col.setGravity(Gravity.CENTER);
        col.setPadding(k.dp(28), k.dp(28), k.dp(28), k.dp(28));
        TextView logo = k.text("S", 32, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(k.box(Kit.PRIMARY, 0, 22));
        col.addView(logo, k.size(70, 70));
        TextView t = k.text("Inscription en attente", 22, Kit.TEXT, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t, k.top(18));
        TextView s = k.text("Votre compte " + api.getEmail() + " doit être confirmé par l'administrateur. "
                + "Vous pourrez ensuite utiliser Kouamépay.", 14.5f, Kit.MUTED, false);
        s.setGravity(Gravity.CENTER);
        col.addView(s, k.top(8));
        TextView refresh = k.button("Actualiser", Kit.PRIMARY, Color.WHITE);
        refresh.setOnClickListener(v -> loadProfile());
        col.addView(refresh, k.top(22));
        TextView out = k.outlineButton("Quitter", Kit.MUTED);
        out.setOnClickListener(v -> {
            api.logout();
            showLogin();
        });
        col.addView(out, k.top(10));
        root.addView(col, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showShell() {
        if (!api.isApproved()) {
            showPending();
            return;
        }
        stopPolling();
        root.removeAllViews();
        final boolean admin = "admin".equals(role);

        // en-tête
        LinearLayout head = k.vbox();
        head.setBackground(k.headerGradient(28));
        head.setPadding(k.dp(20), k.dp(18), k.dp(20), k.dp(24));
        LinearLayout brand = k.hbox();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mini = k.text("S", 18, Kit.PRIMARY, true);
        mini.setGravity(Gravity.CENTER);
        mini.setBackground(k.box(Color.WHITE, 0, 12));
        brand.addView(mini, k.size(36, 36));
        LinearLayout.LayoutParams bn = k.weight(1);
        bn.leftMargin = k.dp(10);
        brand.addView(k.text("Kouamépay", 19, Color.WHITE, true), bn);
        TextView out = k.text("Quitter", 13, Color.WHITE, true);
        out.setPadding(k.dp(14), k.dp(8), k.dp(14), k.dp(8));
        out.setBackground(k.box(0x33FFFFFF, 0, 16));
        out.setOnClickListener(v -> {
            stopPolling();
            stopBeat();
            supportTarget = null;
            opsCache.clear();
            api.logout();
            showLogin();
        });
        brand.addView(out, k.wrap());
        head.addView(brand, k.top(0));
        headTitle = k.text("", 26, Color.WHITE, true);
        head.addView(headTitle, k.top(18));
        headSub = k.text("", 13.5f, 0xCCFFFFFF, false);
        head.addView(headSub, k.top(2));
        TextView who = k.pill(api.getEmail() + "  ·  " + (admin ? "Administrateur" : "Agent"), Color.WHITE, 0x33FFFFFF);
        LinearLayout.LayoutParams wp = k.wrap();
        wp.topMargin = k.dp(12);
        head.addView(who, wp);
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // contenu
        ScrollView sv = new ScrollView(this);
        scroll = sv;
        sv.setFillViewport(true);
        content = k.vbox();
        content.setPadding(k.dp(16), k.dp(18), k.dp(16), k.dp(28));
        content.setClipToPadding(false);
        sv.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // barre de navigation du bas
        View line = new View(this);
        line.setBackgroundColor(Kit.BORDER);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, k.dp(1))));
        LinearLayout bar = k.hbox();
        bar.setBackgroundColor(Kit.SURFACE);
        bar.setPadding(k.dp(8), k.dp(6), k.dp(8), k.dp(6));
        navKeys.clear();
        navLabels.clear();
        navIcons.clear();
        addNav(bar, "collect", admin ? "Encaisser" : "Encaisser\n+ Décaisser", Kit.IconView.DOWN);
        if (admin) addNav(bar, "payout", "Décaisser", Kit.IconView.UP);
        addNav(bar, "history", "Historique", Kit.IconView.CLOCK);
        addNav(bar, "support", "Service client", Kit.IconView.CHAT);
        if (admin) addNav(bar, "users", "Utilisateurs", Kit.IconView.USERS);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (("payout".equals(tab) || "users".equals(tab)) && !admin) tab = "collect";
        startBeat();
        showTab(tab);
        refreshUsersBadge();
    }

    private void addNav(LinearLayout bar, final String key, String label, int iconType) {
        LinearLayout item = k.vbox();
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(k.dp(4), k.dp(8), k.dp(4), k.dp(8));
        Kit.IconView ic = k.icon(iconType, 24, Kit.MUTED);
        item.addView(ic, k.size(24, 24));
        TextView tv = k.text(label, 11.5f, Kit.MUTED, true);
        tv.setGravity(Gravity.CENTER);
        item.addView(tv, k.top(3));
        bar.addView(item, k.weight(1));
        item.setOnClickListener(v -> showTab(key));
        navKeys.add(key);
        navLabels.add(tv);
        navIcons.add(ic);
    }

    private void showTab(String name) {
        tab = name;
        stopPolling();
        for (int i = 0; i < navKeys.size(); i++) {
            boolean on = navKeys.get(i).equals(name);
            navLabels.get(i).setTextColor(on ? Kit.PRIMARY : Kit.MUTED);
            navIcons.get(i).setColor(on ? Kit.PRIMARY : Kit.MUTED);
        }
        if ("collect".equals(name)) {
            headTitle.setText("Encaisser + Décaisser");
            headSub.setText("Le client paie, le montant est versé automatiquement");
        } else if ("payout".equals(name)) {
            headTitle.setText("Décaisser");
            headSub.setText("Payer les clients crypto et envoyer depuis le wallet");
        } else if ("users".equals(name)) {
            headTitle.setText("Mes utilisateurs");
            headSub.setText("Valider les inscriptions de vos clients");
        } else if ("support".equals(name)) {
            final boolean admin = "admin".equals(role);
            headTitle.setText("Service client");
            headSub.setText(!admin ? "Posez votre question, réponse immédiate"
                    : supportTarget != null ? supportLabel : "Les conversations de vos clients");
        } else {
            headTitle.setText("Historique");
            headSub.setText("Vos dernières transactions");
        }
        content.removeAllViews();
        if (!"support".equals(name)) supportTarget = null;
        if ("history".equals(name)) {
            showHistory();
        } else if ("support".equals(name)) {
            showSupport();
        } else if ("users".equals(name)) {
            showUsers();
        } else {
            content.addView(supportCard(), k.top(0));
            final boolean adminUser = "admin".equals(role);
            if ("payout".equals(name) && !adminUser) { showTab("collect"); return; }
            if ("payout".equals(name)) content.addView(bnbQueue(), k.top(14));
            Form f = new Form("payout".equals(name));
            content.addView(f.build(), k.top(14));
            f.selectInitial();
        }
    }

    // ------------------------------------------------------------------ formulaire encaisser / décaisser

    private final class Form {
        final boolean payout;
        final LinearLayout box = k.vbox();
        final LinearLayout body = k.vbox();
        final List<JSONObject> ops = new ArrayList<>();
        Countries.Country country;
        int opIndex = 0;
        int token = 0;
        TextView flagView, nameView, subView;

        Form(boolean payout) {
            this.payout = payout;
        }

        View build() {
            box.addView(k.sectionLabel("Pays"));
            LinearLayout sel = k.hbox();
            sel.setGravity(Gravity.CENTER_VERTICAL);
            sel.setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12));
            sel.setBackground(k.box(Kit.SURFACE, Kit.BORDER, 14));
            flagView = k.text("", 28, Kit.TEXT, false);
            sel.addView(flagView, new LinearLayout.LayoutParams(k.dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout mid = k.vbox();
            nameView = k.text("", 16, Kit.TEXT, true);
            subView = k.text("", 13, Kit.MUTED, false);
            mid.addView(nameView);
            mid.addView(subView);
            sel.addView(mid, k.weight(1));
            sel.addView(k.icon(Kit.IconView.CHEVRON, 20, Kit.MUTED), k.size(20, 20));
            sel.setOnClickListener(v -> pickCountry(country, picked -> selectCountry(picked)));
            box.addView(sel, k.top(8));
            box.addView(body, k.top(0));
            return box;
        }

        void selectInitial() {
            String iso = getSharedPreferences(PREFS_UI, MODE_PRIVATE).getString("country", "BJ");
            Countries.Country c = Countries.byIso(iso);
            selectCountry(c != null ? c : Countries.FEATURED.get(0));
        }

        void selectCountry(final Countries.Country c) {
            country = c;
            getSharedPreferences(PREFS_UI, MODE_PRIVATE).edit().putString("country", c.iso).apply();
            flagView.setText(c.flag());
            nameView.setText(c.name);
            subView.setText("+" + c.dial + "  ·  " + c.currency);
            final int my = ++token;
            JSONArray cached = opsCache.get(c.iso);
            if (cached != null) {
                applyOps(cached);
                return;
            }
            renderLoading();
            io.execute(() -> {
                try {
                    final JSONArray arr = api.operators(c.iso);
                    main.post(() -> {
                        if (my != token) return;
                        opsCache.put(c.iso, arr);
                        applyOps(arr);
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        if (my == token) renderError(e);
                    });
                }
            });
        }

        /** Pays accepté par SebPay = SebPay renvoie au moins un réseau. Sinon : BNB uniquement. */
        void applyOps(JSONArray arr) {
            ops.clear();
            if (!payout) arr = withWave(country.iso, arr); // Wave : lien de paiement, même si SebPay ne le connaît pas
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !str(o, "slug").isEmpty()) ops.add(o);
            }
            if (ops.isEmpty()) renderBnb();
            else renderMobile();
        }

        void renderLoading() {
            body.removeAllViews();
            LinearLayout row = k.hbox();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(k.dp(4), k.dp(26), k.dp(4), k.dp(8));
            ProgressBar pb = new ProgressBar(MainActivity.this);
            pb.setIndeterminateTintList(ColorStateList.valueOf(Kit.PRIMARY));
            row.addView(pb, k.size(22, 22));
            LinearLayout.LayoutParams tp = k.wrap();
            tp.leftMargin = k.dp(12);
            row.addView(k.text("Recherche des réseaux disponibles…", 14.5f, Kit.MUTED, false), tp);
            body.addView(row, k.top(0));
        }

        void renderError(Exception e) {
            if (!api.isLoggedIn()) {
                showLogin();
                toast(errMsg(e));
                return;
            }
            body.removeAllViews();
            LinearLayout err = k.vbox();
            err.setPadding(k.dp(16), k.dp(16), k.dp(16), k.dp(16));
            err.setBackground(k.box(Kit.RED_SOFT, 0, 14));
            err.addView(k.text(errMsg(e), 14, Kit.RED, true));
            TextView retry = k.outlineButton("Réessayer", Kit.RED);
            retry.setOnClickListener(v -> selectCountry(country));
            err.addView(retry, k.top(12));
            body.addView(err, k.top(18));
        }

        // ---------------------------------------------------------- BNB (pays non pris en charge par SebPay)

        void renderBnb() {
            body.removeAllViews();
            final Countries.Country c = country;
            LinearLayout card = k.vbox();
            card.setBackground(k.box(Kit.BNB_DARK, 0, 22));
            card.setPadding(k.dp(20), k.dp(20), k.dp(20), k.dp(20));
            card.setElevation(k.dp(3));

            LinearLayout head = k.hbox();
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView logo = k.text("\u25C6", 22, Kit.BNB_DARK, true);
            logo.setGravity(Gravity.CENTER);
            logo.setBackground(k.circle(Kit.BNB));
            head.addView(logo, k.size(46, 46));
            LinearLayout t = k.vbox();
            t.addView(k.text("Paiement en BNB uniquement", 17, Color.WHITE, true));
            t.addView(k.text("BNB Smart Chain  \u00B7  BEP-20", 12.5f, Kit.BNB, true));
            LinearLayout.LayoutParams tp = k.weight(1);
            tp.leftMargin = k.dp(12);
            head.addView(t, tp);
            card.addView(head);

            card.addView(k.text(c.flag() + "  " + c.name + " n'est pas pris en charge en Mobile Money. "
                    + (payout
                    ? "Le décaissement Mobile Money est donc impossible pour ce pays."
                    : "Vous payez en crypto-monnaie BNB ; choisissez ci-dessous le pays et le réseau Mobile Money qui recevront l'argent."), 14, 0xFFC9CED6, false), k.top(14));

            if (!payout) {
                LinearLayout slot = k.vbox();
                card.addView(slot, k.top(0));
                cryptoForm(slot, c);
            }

            card.addView(k.text("Envoyez uniquement du BNB sur le réseau BNB Smart Chain (BEP-20). "
                    + "Tout autre actif ou réseau serait définitivement perdu.", 12.5f, 0xFF9AA1AD, false), k.top(16));
            body.addView(card, k.top(18));
        }

        // ---------------------------------------------------------- Mobile Money

        void renderMobile() {
            body.removeAllViews();
            opIndex = 0;
            final Countries.Country c = country;

            body.addView(k.sectionLabel(payout ? "Réseau Mobile Money" : "Réseau du numéro qui paie"), k.top(20));
            HorizontalScrollView hs = new HorizontalScrollView(MainActivity.this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chipRow = k.hbox();
            hs.addView(chipRow);
            final TextView[] chips = new TextView[ops.size()];
            for (int i = 0; i < ops.size(); i++) {
                JSONObject o = ops.get(i);
                chips[i] = k.chip(str(o, "name").isEmpty() ? str(o, "slug") : str(o, "name"), i == 0);
                LinearLayout.LayoutParams cp = k.wrap();
                cp.rightMargin = k.dp(8);
                chipRow.addView(chips[i], cp);
            }
            body.addView(hs, k.top(8));

            final TextView otpHint = k.text("", 13, Kit.AMBER, true);
            otpHint.setPadding(k.dp(14), k.dp(11), k.dp(14), k.dp(11));
            otpHint.setBackground(k.box(Kit.AMBER_SOFT, 0, 12));
            otpHint.setVisibility(View.GONE);
            body.addView(otpHint, k.top(10));

            final EditText who = k.field("Nom complet du bénéficiaire",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            if (payout) {
                body.addView(k.sectionLabel("Bénéficiaire"), k.top(18));
                body.addView(who, k.top(8));
            }

            body.addView(k.sectionLabel(payout ? "Numéro de téléphone" : "Numéro qui paie (débité de son compte Mobile Money)"), k.top(18));
            LinearLayout phoneRow = k.hbox();
            TextView prefix = k.text(c.flag() + "  +" + c.dial, 16, Kit.TEXT, true);
            prefix.setGravity(Gravity.CENTER);
            prefix.setPadding(k.dp(14), 0, k.dp(14), 0);
            prefix.setBackground(k.box(Kit.SURFACE, Kit.BORDER, 12));
            phoneRow.addView(prefix, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            final EditText phone = k.field("Numéro", InputType.TYPE_CLASS_PHONE);
            LinearLayout.LayoutParams pp = k.weight(1);
            pp.leftMargin = k.dp(8);
            phoneRow.addView(phone, pp);
            body.addView(phoneRow, k.top(8));

            // Encaissement : second numéro = celui qui reçoit l'argent directement (versé automatiquement après le paiement).
            // Son pays peut être différent de celui du numéro qui paie (ex. Bénin -> Côte d'Ivoire).
            final int[] rIdx = {0};
            final Countries.Country[] rCty = {c};
            final List<JSONObject> rOps = new ArrayList<>();
            final EditText rWho = k.field("Nom complet du bénéficiaire",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            final EditText rPhone = k.field("Numéro", InputType.TYPE_CLASS_PHONE);
            if (!payout) {
                body.addView(k.sectionLabel("Numéro qui reçoit l'argent"), k.top(22));
                body.addView(k.text("Il reçoit directement le montant dès que le paiement réussit. "
                        + "Son pays peut être différent.", 12.5f, Kit.MUTED, false), k.top(2));
                LinearLayout rSel = k.hbox();
                rSel.setGravity(Gravity.CENTER_VERTICAL);
                rSel.setPadding(k.dp(14), k.dp(10), k.dp(14), k.dp(10));
                rSel.setBackground(k.box(Kit.SURFACE, Kit.BORDER, 14));
                final TextView rFlag = k.text(c.flag(), 24, Kit.TEXT, false);
                rSel.addView(rFlag, new LinearLayout.LayoutParams(k.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
                final TextView rName = k.text(c.name, 15, Kit.TEXT, true);
                rSel.addView(rName, k.weight(1));
                rSel.addView(k.icon(Kit.IconView.CHEVRON, 20, Kit.MUTED), k.size(20, 20));
                body.addView(rSel, k.top(8));
                final LinearLayout rBox = k.vbox();
                body.addView(rBox, k.top(0));

                final Runnable[] reload = new Runnable[1];
                final Consumer<JSONArray> renderR = arr -> {
                    rOps.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null && !str(o, "slug").isEmpty()) rOps.add(o);
                    }
                    rIdx[0] = 0;
                    rBox.removeAllViews();
                    if (rOps.isEmpty()) {
                        rBox.addView(k.text(rCty[0].name + " n'est pas pris en charge en Mobile Money : "
                                + "choisissez un autre pays pour le numéro qui reçoit.", 13, Kit.AMBER, true), k.top(10));
                        return;
                    }
                    HorizontalScrollView rhs = new HorizontalScrollView(MainActivity.this);
                    rhs.setHorizontalScrollBarEnabled(false);
                    LinearLayout rRow = k.hbox();
                    rhs.addView(rRow);
                    final TextView[] rChips = new TextView[rOps.size()];
                    for (int i = 0; i < rOps.size(); i++) {
                        JSONObject o = rOps.get(i);
                        rChips[i] = k.chip(str(o, "name").isEmpty() ? str(o, "slug") : str(o, "name"), i == 0);
                        LinearLayout.LayoutParams cp = k.wrap();
                        cp.rightMargin = k.dp(8);
                        rRow.addView(rChips[i], cp);
                        final int idx = i;
                        rChips[i].setOnClickListener(v -> {
                            rIdx[0] = idx;
                            for (int j = 0; j < rChips.length; j++) k.styleChip(rChips[j], j == idx);
                        });
                    }
                    rBox.addView(rhs, k.top(10));
                    if (rWho.getParent() != null) ((ViewGroup) rWho.getParent()).removeView(rWho);
                    rBox.addView(rWho, k.top(10));
                    LinearLayout rPhoneRow = k.hbox();
                    TextView rPrefix = k.text(rCty[0].flag() + "  +" + rCty[0].dial, 16, Kit.TEXT, true);
                    rPrefix.setGravity(Gravity.CENTER);
                    rPrefix.setPadding(k.dp(14), 0, k.dp(14), 0);
                    rPrefix.setBackground(k.box(Kit.SURFACE, Kit.BORDER, 12));
                    rPhoneRow.addView(rPrefix, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    if (rPhone.getParent() != null) ((ViewGroup) rPhone.getParent()).removeView(rPhone);
                    LinearLayout.LayoutParams rpp = k.weight(1);
                    rpp.leftMargin = k.dp(8);
                    rPhoneRow.addView(rPhone, rpp);
                    rBox.addView(rPhoneRow, k.top(10));
                };
                reload[0] = () -> {
                    final Countries.Country rc0 = rCty[0];
                    rFlag.setText(rc0.flag());
                    rName.setText(rc0.name + "  ·  +" + rc0.dial + "  ·  " + rc0.currency);
                    JSONArray cached = opsCache.get(rc0.iso);
                    if (cached != null) {
                        renderR.accept(cached);
                        return;
                    }
                    rBox.removeAllViews();
                    rBox.addView(k.text("Recherche des réseaux disponibles…", 13.5f, Kit.MUTED, false), k.top(12));
                    io.execute(() -> {
                        try {
                            final JSONArray arr = api.operators(rc0.iso);
                            main.post(() -> {
                                if (rCty[0] != rc0) return;
                                opsCache.put(rc0.iso, arr);
                                renderR.accept(arr);
                            });
                        } catch (Exception e) {
                            main.post(() -> {
                                if (rCty[0] != rc0) return;
                                rBox.removeAllViews();
                                rBox.addView(k.text(errMsg(e), 13.5f, Kit.RED, true), k.top(12));
                            });
                        }
                    });
                };
                rSel.setOnClickListener(v -> pickCountry(rCty[0], picked -> {
                    rCty[0] = picked;
                    reload[0].run();
                }));
                reload[0].run();
            }

            body.addView(k.sectionLabel("Montant"), k.top(18));
            LinearLayout amountCard = k.vbox();
            amountCard.setPadding(k.dp(16), k.dp(12), k.dp(16), k.dp(14));
            amountCard.setBackground(k.box(Kit.SURFACE, Kit.BORDER, 14));
            LinearLayout amountRow = k.hbox();
            amountRow.setGravity(Gravity.CENTER_VERTICAL);
            final EditText amount = new EditText(MainActivity.this);
            amount.setBackground(null);
            amount.setHint("0");
            amount.setHintTextColor(0xFFB5C0BB);
            amount.setTextColor(Kit.TEXT);
            amount.setTextSize(30);
            amount.setTypeface(Typeface.DEFAULT_BOLD);
            amount.setSingleLine(true);
            amount.setInputType(InputType.TYPE_CLASS_NUMBER);
            amount.setKeyListener(DigitsKeyListener.getInstance("0123456789 "));
            amount.setRawInputType(InputType.TYPE_CLASS_NUMBER); // clavier numérique malgré l'espace autorisé
            amount.setPadding(0, 0, 0, 0);
            amount.addTextChangedListener(new TextWatcher() {
                private boolean editing;

                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int n) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int n) {
                }

                @Override
                public void afterTextChanged(Editable e) {
                    if (editing) return;
                    editing = true;
                    String digits = e.toString().replaceAll("\\D", "");
                    if (digits.length() > 12) digits = digits.substring(0, 12);
                    String f = group(digits, " ");
                    if (!f.equals(e.toString())) e.replace(0, e.length(), f);
                    editing = false;
                }
            });
            amountRow.addView(amount, k.weight(1));
            amountRow.addView(k.text(c.currency, 16, Kit.MUTED, true), k.wrap());
            amountCard.addView(amountRow);

            HorizontalScrollView qs = new HorizontalScrollView(MainActivity.this);
            qs.setHorizontalScrollBarEnabled(false);
            LinearLayout qRow = k.hbox();
            qs.addView(qRow);
            int[] presets = BIG_CURRENCIES.contains(c.currency)
                    ? new int[]{1000, 2000, 5000, 10000, 25000, 50000}
                    : new int[]{10, 20, 50, 100, 200, 500};
            for (final int p : presets) {
                TextView q = k.text(group(String.valueOf(p), " "), 13, Kit.PRIMARY, true);
                q.setPadding(k.dp(12), k.dp(7), k.dp(12), k.dp(7));
                q.setBackground(k.box(Kit.PRIMARY_SOFT, 0, 16));
                LinearLayout.LayoutParams qp = k.wrap();
                qp.rightMargin = k.dp(8);
                qRow.addView(q, qp);
                q.setOnClickListener(v -> {
                    amount.setText(group(String.valueOf(p), " "));
                    amount.setSelection(amount.getText().length());
                });
            }
            amountCard.addView(qs, k.top(10));
            body.addView(amountCard, k.top(8));

            final EditText otp = k.field("Code OTP de l'opérateur", InputType.TYPE_CLASS_NUMBER);
            otp.setVisibility(View.GONE);
            if (!payout) body.addView(otp, k.top(10));

            final TextView test = k.outlineButton(payout ? "Tester le décaissement" : "Tester l'encaissement et le versement", Kit.PRIMARY);
            body.addView(test, k.top(22));
            final TextView go = k.button(payout ? "Décaisser" : "Encaisser", payout ? Kit.RED : Kit.PRIMARY, Color.WHITE);
            body.addView(go, k.top(10));
            final StatusBox status = new StatusBox();
            body.addView(status.box, k.top(12));

            // Bouton « Tester » : vérifie l'encaissement ET le versement (ou le décaissement) sans rien envoyer.
            test.setOnClickListener(v -> {
                final JSONObject top = ops.get(opIndex);
                final String tslug = str(top, "slug");
                if (!payout && top.optBoolean("wave_link", false)) {
                    status.warn("Wave fonctionne par lien de paiement : il n'y a pas de test d'API.");
                    return;
                }
                JSONObject trc = null;
                if (!payout) {
                    if (rOps.isEmpty()) {
                        status.warn("Choisissez un pays pris en charge pour le numéro qui reçoit.");
                        return;
                    }
                    try {
                        JSONObject ro = rOps.get(rIdx[0]);
                        trc = new JSONObject().put("country", rCty[0].iso).put("operator", str(ro, "slug"));
                    } catch (Exception ignored) {
                        status.warn("Numéro qui reçoit invalide.");
                        return;
                    }
                }
                final JSONObject trcp = trc;
                k.busy(test, true);
                status.loading(payout ? "Test du décaissement…" : "Test de l'encaissement et du versement…");
                io.execute(() -> {
                    try {
                        JSONArray problems = payout ? api.preflightPayout(c.iso, tslug) : api.preflight(c.iso, tslug, trcp);
                        if (problems.length() == 0) {
                            main.post(() -> {
                                status.ok(payout ? "Test réussi : le décaissement est disponible. Vous pouvez décaisser."
                                        : "Test réussi : l'encaissement et le versement sont disponibles. Vous pouvez encaisser.");
                                k.busy(test, false);
                            });
                        } else {
                            StringBuilder sbt = new StringBuilder(payout ? "Test échoué (décaissement) :" : "Test échoué :");
                            for (int i = 0; i < problems.length(); i++) sbt.append("\n• ").append(problems.optString(i));
                            final String msgt = sbt.toString();
                            main.post(() -> {
                                status.err(msgt);
                                k.busy(test, false);
                            });
                        }
                    } catch (Exception e) {
                        main.post(() -> {
                            handleError(e, status);
                            k.busy(test, false);
                        });
                    }
                });
            });
            final LinearLayout waveSlot = k.vbox();
            body.addView(waveSlot, k.top(12));

            // sélection du réseau
            final Runnable refreshOp = () -> {
                for (int i = 0; i < chips.length; i++) k.styleChip(chips[i], i == opIndex);
                JSONObject o = ops.get(opIndex);
                boolean need = !payout && o.optBoolean("otp_required", false);
                String ussd = str(o, "ussd_code");
                otp.setVisibility(need ? View.VISIBLE : View.GONE);
                otpHint.setVisibility(need ? View.VISIBLE : View.GONE);
                otpHint.setText("Cet opérateur exige un code OTP"
                        + (ussd.isEmpty() ? "." : " : le client compose " + ussd + " pour le recevoir."));
                waveSlot.removeAllViews();
                go.setText(!payout && o.optBoolean("wave_link", false) ? "Générer le lien Wave" : (payout ? "Décaisser" : "Encaisser"));
            };
            for (int i = 0; i < chips.length; i++) {
                final int idx = i;
                chips[i].setOnClickListener(v -> {
                    opIndex = idx;
                    refreshOp.run();
                });
            }
            refreshOp.run();

            go.setOnClickListener(v -> {
                final JSONObject op = ops.get(opIndex);
                final String slug = str(op, "slug");
                final String ph = phone.getText().toString().trim();
                final String name = who.getText().toString().trim();
                final String code = otp.getText().toString().trim();
                String digits = amount.getText().toString().replaceAll("\\D", "");
                long amt;
                try {
                    amt = digits.isEmpty() ? 0 : Long.parseLong(digits);
                } catch (NumberFormatException e) {
                    amt = 0;
                }
                final long value = amt;
                if (!payout && op.optBoolean("wave_link", false)) {
                    if (value <= 0) {
                        status.warn("Saisissez un montant valide.");
                        return;
                    }
                    showWaveLink(waveSlot, status, c, value);
                    return;
                }
                if (payout && name.isEmpty()) {
                    status.warn("Saisissez le nom du bénéficiaire.");
                    return;
                }
                if (ph.isEmpty()) {
                    status.warn(payout ? "Saisissez le numéro de téléphone." : "Saisissez le numéro qui paie.");
                    return;
                }
                JSONObject rc = null;
                if (!payout) {
                    String rn = rWho.getText().toString().trim();
                    String rp = rPhone.getText().toString().trim();
                    if (rn.isEmpty()) {
                        status.warn("Saisissez le nom du bénéficiaire (numéro qui reçoit).");
                        return;
                    }
                    if (rp.replaceAll("\\D", "").length() < 6) {
                        status.warn("Saisissez le numéro qui reçoit l'argent.");
                        return;
                    }
                    if (rOps.isEmpty()) {
                        status.warn("Choisissez un pays pris en charge pour le numéro qui reçoit.");
                        return;
                    }
                    try {
                        JSONObject ro = rOps.get(rIdx[0]);
                        rc = new JSONObject().put("country", rCty[0].iso).put("operator", str(ro, "slug"))
                                .put("operator_name", str(ro, "name").isEmpty() ? str(ro, "slug") : str(ro, "name"))
                                .put("phone", rp).put("name", rn);
                    } catch (Exception ignored) {
                        status.warn("Numéro qui reçoit invalide.");
                        return;
                    }
                }
                final JSONObject rcp = rc;
                if (value <= 0) {
                    status.warn("Saisissez un montant valide.");
                    return;
                }
                if (!payout && op.optBoolean("otp_required", false) && code.isEmpty()) {
                    status.warn("Saisissez le code OTP.");
                    return;
                }

                k.busy(go, true);
                status.loading(payout ? "Vérification du décaissement…" : "Vérification de l'encaissement et du versement…");
                io.execute(() -> {
                    try {
                        JSONArray problems = payout ? api.preflightPayout(c.iso, slug) : api.preflight(c.iso, slug, rcp);
                        if (problems.length() > 0) {
                            StringBuilder sbp = new StringBuilder(payout ? "Impossible de décaisser pour le moment :" : "Impossible d'encaisser pour le moment :");
                            for (int i = 0; i < problems.length(); i++) sbp.append("\n• ").append(problems.optString(i));
                            final String msgp = sbp.toString();
                            main.post(() -> {
                                status.err(msgp);
                                k.busy(go, false);
                            });
                            return;
                        }
                        final JSONObject qt = (!payout && rcp != null)
                                ? api.quote("collect", value, c.iso, rcp.optString("country"))
                                : api.quote(payout ? "payout" : "collect", value);
                        main.post(() -> {
                            k.busy(go, false);
                            status.hide();
                            confirmAndSend(payout, qt, c, op, slug, ph, name, code, value, go, status, rcp);
                        });
                    } catch (Exception e) {
                        main.post(() -> {
                            handleError(e, status);
                            k.busy(go, false);
                        });
                    }
                });
            });
        }
    }

    // ------------------------------------------------------------------ Wave (réseau absent de SebPay : lien de paiement)

    /** Ajoute « Wave » à la liste des réseaux d'un pays où Wave existe, si SebPay ne le renvoie pas déjà. */
    private static JSONArray withWave(String iso, JSONArray arr) {
        if (!Config.WAVE_COUNTRIES.contains(iso)) return arr;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = (str(o, "slug") + " " + str(o, "name")).toLowerCase(Locale.ROOT);
            if (id.contains("wave")) return arr;
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) out.put(arr.opt(i));
        try {
            out.put(new JSONObject().put("slug", "wave").put("name", "Wave").put("wave_link", true));
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Affiche le lien Wave du pays avec le montant : ouvrir, copier ou partager. */
    private void showWaveLink(LinearLayout slot, StatusBox status, Countries.Country c, long amount) {
        slot.removeAllViews();
        final String url = Config.waveUrl(c.iso, amount);
        if (url == null) {
            status.warn("Aucun lien Wave configuré pour " + c.name + " (Config.WAVE_LINKS).");
            return;
        }
        status.hide();
        LinearLayout card = k.vbox();
        card.setPadding(k.dp(16), k.dp(16), k.dp(16), k.dp(16));
        card.setBackground(k.box(Kit.PRIMARY_SOFT, 0, 14));
        card.addView(k.text("Paiement Wave · " + group(String.valueOf(amount), " ") + " " + c.currency, 16, Kit.TEXT, true));
        card.addView(k.text("Le client ouvre ce lien dans Wave, vérifie le montant et confirme. "
                + "Vérifiez la réception dans votre compte Wave Business : le versement au bénéficiaire se fait ensuite à la main.",
                13, Kit.MUTED, false), k.top(6));
        TextView link = k.text(url, 12.5f, Kit.PRIMARY, false);
        link.setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10));
        link.setBackground(k.box(Kit.SURFACE, Kit.BORDER, 12));
        card.addView(link, k.top(10));
        TextView open = k.button("Ouvrir dans Wave", Kit.PRIMARY, Color.WHITE);
        TextView copyB = k.outlineButton("Copier le lien", Kit.PRIMARY);
        TextView share = k.outlineButton("Envoyer au client", Kit.PRIMARY);
        card.addView(open, k.top(12));
        card.addView(copyB, k.top(8));
        card.addView(share, k.top(8));
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                copy("Lien Wave", url);
                toast("Aucune application pour ouvrir le lien : il a été copié.");
            }
        });
        copyB.setOnClickListener(v -> {
            copy("Lien Wave", url);
            toast("Lien copié.");
        });
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, "Payez " + group(String.valueOf(amount), " ") + " " + c.currency + " avec Wave : " + url);
            startActivity(Intent.createChooser(i, "Envoyer le lien Wave"));
        });
        slot.addView(card, k.top(0));
    }

    // ------------------------------------------------------------------ sélecteur de pays (tous les pays du monde)

    private void pickCountry(final Countries.Country current, final Consumer<Countries.Country> onPick) {
        LinearLayout sheetBox = k.vbox();
        sheetBox.setPadding(k.dp(16), k.dp(10), k.dp(16), k.dp(8));
        View handle = new View(this);
        handle.setBackground(k.box(Kit.BORDER, 0, 3));
        LinearLayout.LayoutParams hp = k.size(40, 5);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheetBox.addView(handle, hp);
        sheetBox.addView(k.text("Choisir un pays", 20, Kit.TEXT, true), k.top(14));
        sheetBox.addView(k.text(Countries.ALL.size() + " pays et territoires", 13, Kit.MUTED, false), k.top(2));
        final EditText search = k.field("Rechercher un pays ou un indicatif", InputType.TYPE_CLASS_TEXT);
        sheetBox.addView(search, k.top(12));

        final ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setVerticalScrollBarEnabled(false);
        lv.setSelector(new ColorDrawable(0x14000000));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = k.dp(8);
        sheetBox.addView(lv, lp);

        final List<Object> shown = new ArrayList<>();
        final int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
        final Dialog dlg = sheet(sheetBox, h);

        final BaseAdapter ad = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int i) {
                return shown.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public boolean isEnabled(int i) {
                return shown.get(i) instanceof Countries.Country;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                Object o = shown.get(i);
                if (o instanceof String) {
                    TextView hd = k.sectionLabel((String) o);
                    hd.setPadding(k.dp(4), k.dp(14), k.dp(4), k.dp(4));
                    return hd;
                }
                Countries.Country c = (Countries.Country) o;
                boolean sel = current != null && c.iso.equals(current.iso);
                LinearLayout r = k.hbox();
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(k.dp(4), k.dp(11), k.dp(4), k.dp(11));
                r.addView(k.text(c.flag(), 24, Kit.TEXT, false),
                        new LinearLayout.LayoutParams(k.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
                r.addView(k.text(c.name, 16, sel ? Kit.PRIMARY : Kit.TEXT, sel), k.weight(1));
                r.addView(k.text("+" + c.dial, 14, Kit.MUTED, false), k.wrap());
                if (sel) {
                    LinearLayout.LayoutParams cp = k.size(20, 20);
                    cp.leftMargin = k.dp(10);
                    r.addView(k.icon(Kit.IconView.CHECK, 20, Kit.PRIMARY), cp);
                }
                return r;
            }
        };

        final Runnable fill = () -> {
            shown.clear();
            String q = Countries.norm(search.getText().toString().trim());
            if (q.isEmpty()) {
                shown.add("Pays fréquents");
                shown.addAll(Countries.FEATURED);
                shown.add("Tous les pays");
                for (Countries.Country c : Countries.ALL) {
                    if (!Countries.FEATURED.contains(c)) shown.add(c);
                }
            } else {
                for (Countries.Country c : Countries.ALL) {
                    if (c.key.contains(q)) shown.add(c);
                }
            }
            ad.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                fill.run();
            }
        });
        lv.setAdapter(ad);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = shown.get(pos);
            if (o instanceof Countries.Country) {
                dlg.dismiss();
                onPick.accept((Countries.Country) o);
            }
        });
        fill.run();
        dlg.show();
    }

    // ------------------------------------------------------------------ paiement BNB via CryptAPI

    private EditText darkField(String hint, int type) {
        final EditText e = k.field(hint, type);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF7C8492);
        e.setBackground(k.box(Kit.BNB_PANEL, 0, 12));
        e.setOnFocusChangeListener((v, f) -> v.setBackground(k.box(Kit.BNB_PANEL, f ? Kit.BNB : 0, 12)));
        return e;
    }

    private TextView darkLabel(String s) {
        TextView l = k.text(s, 11.5f, 0xFF9AA1AD, true);
        l.setLetterSpacing(0.08f);
        return l;
    }

    /** Étape 1 : montant en USD, pays + réseau + numéro qui recevront l'argent, puis adresse unique chez CryptAPI. */
    private void cryptoForm(final LinearLayout slot, final Countries.Country payerCountry) {
        slot.removeAllViews();
        // Le pays qui reçoit l'argent est choisi ici (indépendant du pays du payeur) : il doit avoir du Mobile Money.
        Countries.Country saved = Countries.byIso(getSharedPreferences(PREFS_UI, MODE_PRIVATE).getString("crypto_rcountry", "BJ"));
        final Countries.Country[] rc = {saved != null ? saved : Countries.FEATURED.get(0)};
        final String[] chosenNet = {""};
        final List<JSONObject> rOps = new ArrayList<>();

        slot.addView(darkLabel("MONTANT À PAYER (USD)"), k.top(20));
        final EditText amt = darkField("Ex. 25", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        slot.addView(amt, k.top(8));

        // ---- pays qui reçoit l'argent
        slot.addView(darkLabel("PAYS QUI REÇOIT L'ARGENT"), k.top(18));
        LinearLayout sel = k.hbox();
        sel.setGravity(Gravity.CENTER_VERTICAL);
        sel.setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12));
        sel.setBackground(k.box(Kit.BNB_PANEL, 0, 12));
        final TextView rFlag = k.text("", 24, Color.WHITE, false);
        sel.addView(rFlag, new LinearLayout.LayoutParams(k.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout mid = k.vbox();
        final TextView rName = k.text("", 15.5f, Color.WHITE, true);
        final TextView rSub = k.text("", 12.5f, 0xFF9AA1AD, false);
        mid.addView(rName);
        mid.addView(rSub);
        sel.addView(mid, k.weight(1));
        sel.addView(k.icon(Kit.IconView.CHEVRON, 20, 0xFF9AA1AD), k.size(20, 20));
        slot.addView(sel, k.top(8));

        // ---- réseau qui reçoit l'argent (liste du pays choisi)
        slot.addView(darkLabel("RÉSEAU QUI REÇOIT L'ARGENT"), k.top(18));
        final LinearLayout netBox = k.vbox();
        slot.addView(netBox, k.top(8));

        // ---- numéro qui reçoit l'argent
        final TextView phoneLabel = darkLabel("");
        slot.addView(phoneLabel, k.top(18));
        LinearLayout phoneRow = k.hbox();
        final TextView prefix = k.text("", 16, Color.WHITE, true);
        prefix.setGravity(Gravity.CENTER);
        prefix.setPadding(k.dp(14), 0, k.dp(14), 0);
        prefix.setBackground(k.box(Kit.BNB_PANEL, 0, 12));
        phoneRow.addView(prefix, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final EditText phone = darkField("Numéro", InputType.TYPE_CLASS_PHONE);
        LinearLayout.LayoutParams pp = k.weight(1);
        pp.leftMargin = k.dp(8);
        phoneRow.addView(phone, pp);
        slot.addView(phoneRow, k.top(8));

        final TextView go = k.button("Générer l'adresse de paiement", Kit.BNB, Kit.BNB_DARK);
        slot.addView(go, k.top(18));
        final StatusBox st = new StatusBox();
        slot.addView(st.box, k.top(12));

        // Affiche la liste des réseaux (boutons) ; zone de saisie libre si le pays n'a aucun réseau listé.
        final java.util.function.Consumer<JSONArray> renderNets = arr0 -> {
            JSONArray arr = arr0;
            netBox.removeAllViews();
            rOps.clear();
            chosenNet[0] = "";
            arr = withWave(rc[0].iso, arr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !str(o, "slug").isEmpty()) rOps.add(o);
            }
            if (rOps.isEmpty()) {
                final EditText net = darkField("Nom du réseau (ex. MTN, Orange Money, Moov)",
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                net.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
                    @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
                    @Override public void afterTextChanged(Editable e) { chosenNet[0] = e.toString().trim(); }
                });
                netBox.addView(net);
                return;
            }
            HorizontalScrollView hs = new HorizontalScrollView(MainActivity.this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = k.hbox();
            hs.addView(row);
            final TextView[] chips = new TextView[rOps.size()];
            for (int i = 0; i < rOps.size(); i++) {
                final JSONObject o = rOps.get(i);
                final String label = str(o, "name").isEmpty() ? str(o, "slug") : str(o, "name");
                chips[i] = k.text(label, 14, Kit.BNB_DARK, true);
                chips[i].setPadding(k.dp(16), k.dp(10), k.dp(16), k.dp(10));
                LinearLayout.LayoutParams cp = k.wrap();
                cp.rightMargin = k.dp(8);
                row.addView(chips[i], cp);
            }
            final Runnable paint = () -> {
                for (int i = 0; i < chips.length; i++) {
                    boolean on = chips[i].getText().toString().equals(chosenNet[0]);
                    chips[i].setTextColor(on ? Kit.BNB_DARK : Color.WHITE);
                    chips[i].setBackground(k.box(on ? Kit.BNB : Kit.BNB_PANEL, 0, 22));
                }
            };
            for (final TextView ch : chips) {
                ch.setOnClickListener(v -> {
                    chosenNet[0] = ch.getText().toString();
                    paint.run();
                });
            }
            chosenNet[0] = chips[0].getText().toString();
            paint.run();
            netBox.addView(hs);
        };

        final Runnable[] reload = new Runnable[1];
        reload[0] = () -> {
            final Countries.Country r0 = rc[0];
            getSharedPreferences(PREFS_UI, MODE_PRIVATE).edit().putString("crypto_rcountry", r0.iso).apply();
            rFlag.setText(r0.flag());
            rName.setText(r0.name);
            rSub.setText("+" + r0.dial + "  ·  " + r0.currency);
            prefix.setText("+" + r0.dial);
            phoneLabel.setText("NUMÉRO QUI REÇOIT L'ARGENT  ·  " + r0.flag() + " " + r0.name);
            JSONArray cached = opsCache.get(r0.iso);
            if (cached != null) {
                renderNets.accept(cached);
                return;
            }
            netBox.removeAllViews();
            netBox.addView(k.text("Recherche des réseaux disponibles…", 13.5f, 0xFF9AA1AD, false));
            io.execute(() -> {
                try {
                    final JSONArray arr = api.operators(r0.iso);
                    main.post(() -> {
                        if (rc[0] != r0) return;
                        opsCache.put(r0.iso, arr);
                        renderNets.accept(arr);
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        if (rc[0] != r0) return;
                        renderNets.accept(new JSONArray()); // saisie libre en secours
                    });
                }
            });
        };
        sel.setOnClickListener(v -> pickCountry(rc[0], picked -> {
            rc[0] = picked;
            reload[0].run();
        }));
        reload[0].run();

        go.setOnClickListener(v -> {
            double usd;
            try {
                usd = Double.parseDouble(amt.getText().toString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                usd = 0;
            }
            if (!(usd >= 1)) {
                st.warn("Saisissez un montant d'au moins 1 USD.");
                return;
            }
            final String network = chosenNet[0].trim();
            if (network.isEmpty()) {
                st.warn("Choisissez le réseau qui recevra l'argent.");
                return;
            }
            final String number = phone.getText().toString().trim();
            if (number.replaceAll("\\D", "").length() < 6) {
                st.warn("Saisissez le numéro qui recevra l'argent.");
                return;
            }
            final double value = usd;
            final Countries.Country dest = rc[0];
            k.busy(go, true);
            st.loading("Création de l'adresse de paiement…");
            io.execute(() -> {
                try {
                    // « country » = pays du bénéficiaire : sert à valider le numéro côté serveur.
                    final JSONObject d = api.cryptoCreate(value, dest.iso, network, number);
                    main.post(() -> cryptoResult(slot, payerCountry, d));
                } catch (Exception e) {
                    main.post(() -> {
                        handleError(e, st);
                        k.busy(go, false);
                    });
                }
            });
        });
    }

    /** Étape 2 : adresse unique + montant exact en BNB, puis suivi jusqu'à la confirmation. */
    private void cryptoResult(final LinearLayout slot, final Countries.Country c, JSONObject d) {
        slot.removeAllViews();
        final String addr = str(d, "address_in");
        final String bnb = new BigDecimal(d.optString("expected_bnb", "0")).stripTrailingZeros().toPlainString();
        final String ref = str(d, "reference");

        slot.addView(darkLabel("ENVOYEZ EXACTEMENT"), k.top(20));
        slot.addView(k.text(bnb + " BNB", 28, Color.WHITE, true), k.top(4));
        slot.addView(k.text("\u2248 " + d.optString("amount_usd", "") + " USD", 13, 0xFF9AA1AD, false), k.top(0));

        slot.addView(darkLabel("ADRESSE DE PAIEMENT (UNIQUE POUR CETTE TRANSACTION)"), k.top(18));
        TextView addrView = k.text(addr, 14, Color.WHITE, true);
        addrView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        addrView.setPadding(k.dp(14), k.dp(14), k.dp(14), k.dp(14));
        addrView.setBackground(k.box(Kit.BNB_PANEL, 0, 12));
        addrView.setTextIsSelectable(true);
        slot.addView(addrView, k.top(6));

        TextView copyAddr = k.button("Copier l'adresse", Kit.BNB, Kit.BNB_DARK);
        TextView copyAmt = k.outlineButton("Copier le montant", Kit.BNB);
        copyAmt.setBackground(k.box(Color.TRANSPARENT, Kit.BNB, 14));
        TextView wallet = k.outlineButton("Ouvrir mon portefeuille", Kit.BNB);
        wallet.setBackground(k.box(Color.TRANSPARENT, Kit.BNB, 14));
        slot.addView(copyAddr, k.top(14));
        slot.addView(copyAmt, k.top(10));
        slot.addView(wallet, k.top(10));
        copyAddr.setOnClickListener(v -> {
            copy("Adresse BNB", addr);
            toast("Adresse copiée.");
        });
        copyAmt.setOnClickListener(v -> {
            copy("Montant BNB", bnb);
            toast("Montant copié.");
        });
        wallet.setOnClickListener(v -> openWallet(addr, bnb));

        final String phoneLabel = "+" + str(d, "recipient_phone");
        slot.addView(darkLabel("L'ARGENT SERA ENVOYÉ À"), k.top(18));
        slot.addView(k.text(str(d, "recipient_operator") + "  ·  " + phoneLabel, 15, Color.WHITE, true), k.top(6));

        final StatusBox st = new StatusBox();
        st.loading("En attente de paiement (24 h maximum)… la confirmation est automatique dès que le BNB arrive.");
        slot.addView(st.box, k.top(16));
        slot.addView(proofSection(ref, System.currentTimeMillis(), 0, true, null), k.top(18));
        TextView again = k.text("Nouveau paiement", 14, Kit.BNB, true);
        again.setGravity(Gravity.CENTER);
        again.setPadding(0, k.dp(12), 0, k.dp(4));
        again.setOnClickListener(v -> {
            stopPolling();
            cryptoForm(slot, c);
        });
        slot.addView(again, k.top(6));
        startCryptoPolling(ref, st, phoneLabel);
    }

    private void startCryptoPolling(final String ref, final StatusBox st, final String phoneLabel) {
        if (poller != null) main.removeCallbacks(poller); // on garde le compte à rebours des preuves
        pollCount = 0;
        poller = new Runnable() {
            @Override
            public void run() {
                final Runnable self = this;
                io.execute(() -> {
                    String status = "pending";
                    JSONObject row = null;
                    try {
                        row = api.cryptoStatus(ref);
                        status = row.optString("status", "pending");
                    } catch (Exception ignored) {
                        // erreur passagère : on réessaie au prochain tour
                    }
                    final String fs = status;
                    final JSONObject r = row;
                    main.post(() -> {
                        if (poller != self) return;
                        final String po = r != null ? r.optString("payout_status", "awaiting") : "awaiting";
                        if (r != null && r.optBoolean("expired", false)) {
                            poller = null;
                            st.warn("Paiement expiré : le BNB n'est pas arrivé en 24 h. Faites un nouveau paiement.");
                        } else if ("paid".equals(po)) {
                            poller = null;
                            st.ok("Payé : l'argent a été envoyé à " + phoneLabel
                                    + ". S'il n'est pas arrivé, signalez-le depuis l'Historique.");
                        } else if ("reported".equals(po)) {
                            poller = null;
                            st.warn("Votre signalement a été transmis à l'administrateur.");
                        } else if ("underpaid".equals(fs)) {
                            poller = null;
                            st.warn("Montant reçu inférieur au montant demandé. Contactez l'administrateur.");
                        } else if (++pollCount > 720) { // 1 heure
                            poller = null;
                            st.warn("Toujours en attente. Suivez-le dans l'onglet Historique.");
                        } else {
                            if ("proof_sent".equals(po)) {
                                st.loading("Preuves reçues. L'administrateur va envoyer l'argent à " + phoneLabel + "…");
                            } else if ("approved".equals(fs)) {
                                st.ok("Paiement BNB reçu et confirmé"
                                        + (r != null ? " : " + r.optString("value_coin", "") + " BNB" : "")
                                        + ". Envoyez vos preuves ci-dessous.");
                            }
                            main.postDelayed(self, 5000);
                        }
                    });
                });
            }
        };
        main.postDelayed(poller, 5000);
    }

    // ------------------------------------------------------------------ preuves de paiement (images)

    private static final int REQ_IMAGE = 4711;
    private static final long PROOF_DELAY_MS = 120_000L; // 2 minutes avant de proposer l'envoi des preuves
    private Consumer<Uri> onImagePicked;
    private final Map<String, Bitmap> imgCache = new HashMap<>();

    private long parseMs(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            return in.parse(iso.substring(0, 19)).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private void pickImage(Consumer<Uri> cb) {
        onImagePicked = cb;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choisir la capture d'écran"), REQ_IMAGE);
        } catch (ActivityNotFoundException e) {
            toast("Aucune application pour choisir une image.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMAGE && res == RESULT_OK && data != null && data.getData() != null && onImagePicked != null) {
            Consumer<Uri> cb = onImagePicked;
            onImagePicked = null;
            cb.accept(data.getData());
        }
    }

    /** Image du téléphone -> JPEG réduit (2000 px max, < 900 Ko) en base64. */
    private String encodeImage(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int big = Math.max(o.outWidth, o.outHeight);
        if (big <= 0) throw new Api.ApiException("Image illisible.");
        int sample = 1;
        while (big / sample > 2000) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bmp == null) throw new Api.ApiException("Image illisible.");
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rot = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (Exception ignored) {
            // pas d'EXIF : image telle quelle
        }
        float scale = Math.min(1f, 1600f / Math.max(bmp.getWidth(), bmp.getHeight()));
        Matrix m = new Matrix();
        if (scale < 1f) m.postScale(scale, scale);
        if (rot != 0) m.postRotate(rot);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int q = 75;
        out.compress(Bitmap.CompressFormat.JPEG, q, bo);
        while (bo.size() > 900_000 && q > 35) {
            bo.reset();
            q -= 10;
            out.compress(Bitmap.CompressFormat.JPEG, q, bo);
        }
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    /**
     * Bloc « preuves » : compte à rebours de 2 minutes (le serveur l'exige aussi), puis bouton pour joindre
     * une capture d'écran depuis le téléphone. Les images partent à l'administrateur.
     */
    private View proofSection(final String ref, final long startMs, int alreadySent, boolean dark, final Runnable onSent) {
        final int fg = dark ? Color.WHITE : Kit.TEXT;
        final int muted = dark ? 0xFF9AA1AD : Kit.MUTED;
        final LinearLayout box = k.vbox();
        TextView title = k.text("PREUVES DE PAIEMENT", 11.5f, muted, true);
        title.setLetterSpacing(0.08f);
        box.addView(title);
        final TextView info = k.text("", 13.5f, fg, false);
        box.addView(info, k.top(6));
        final TextView pick = dark ? k.button("Joindre une capture d'écran", Kit.BNB, Kit.BNB_DARK)
                : k.button("Joindre une capture d'écran", Kit.PRIMARY, Color.WHITE);
        box.addView(pick, k.top(10));
        final StatusBox st = new StatusBox();
        box.addView(st.box, k.top(10));
        final int[] sent = {alreadySent};

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                long left = PROOF_DELAY_MS - (System.currentTimeMillis() - startMs);
                if (left > 0) {
                    long s = (left + 999) / 1000;
                    info.setText("Après votre paiement, vous pourrez envoyer vos preuves dans "
                            + (s / 60) + ":" + String.format(Locale.US, "%02d", s % 60) + ".");
                    pick.setVisibility(View.GONE);
                    tickers.remove(this);
                    tickers.add(this);
                    main.postDelayed(this, 1000);
                } else {
                    tickers.remove(this);
                    info.setText("Joignez la capture d'écran de votre paiement BNB avec la référence de la transaction "
                            + "(image depuis votre téléphone). Elle sera envoyée à l'administrateur."
                            + (sent[0] > 0 ? "\n" + sent[0] + " preuve(s) déjà envoyée(s)." : ""));
                    pick.setVisibility(View.VISIBLE);
                }
            }
        };
        refresh.run();

        pick.setOnClickListener(v -> pickImage(uri -> {
            k.busy(pick, true);
            st.loading("Envoi de la preuve…");
            io.execute(() -> {
                try {
                    final String b64 = encodeImage(uri);
                    api.cryptoProof(ref, b64);
                    main.post(() -> {
                        sent[0]++;
                        k.busy(pick, false);
                        st.ok("Preuve envoyée à l'administrateur.");
                        refresh.run();
                        if (onSent != null) onSent.run();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        k.busy(pick, false);
                        handleError(e, st);
                    });
                }
            });
        }));
        return box;
    }

    // ------------------------------------------------------------------ portefeuille BNB

    private void openWallet(String addr, String amountStr) {
        String uri = "ethereum:" + addr + "@56"; // EIP-681 : chaîne 56 = BNB Smart Chain
        try {
            if (!amountStr.isEmpty()) {
                String wei = new BigDecimal(amountStr.replace(',', '.')).movePointRight(18).toBigInteger().toString();
                if (!"0".equals(wei)) uri += "?value=" + wei;
            }
        } catch (NumberFormatException e) {
            toast("Montant BNB invalide.");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (ActivityNotFoundException e) {
            copy("Adresse BNB", addr);
            toast("Aucun portefeuille compatible : l'adresse a été copiée.");
        }
    }

    // ------------------------------------------------------------------ confirmation (reçu)

    private LinearLayout receiptRow(String label, String value, boolean strong) {
        LinearLayout r = k.hbox();
        r.setPadding(0, k.dp(7), 0, k.dp(7));
        r.addView(k.text(label, 14, Kit.MUTED, false), k.weight(1));
        TextView v = k.text(value, 14.5f, Kit.TEXT, true);
        v.setGravity(Gravity.END);
        if (strong) v.setTextColor(Kit.PRIMARY);
        r.addView(v, k.wrap());
        return r;
    }

    private void confirmAndSend(final boolean payout, JSONObject qt, final Countries.Country c, JSONObject op,
                                final String slug, final String ph, final String who, final String code,
                                final long value, final TextView go, final StatusBox status, final JSONObject rcp) {
        LinearLayout box = k.vbox();
        box.setPadding(k.dp(20), k.dp(20), k.dp(20), k.dp(20));
        box.addView(k.text(payout ? "Confirmer le décaissement" : "Confirmer l'encaissement", 20, Kit.TEXT, true));
        box.addView(k.text(payout ? "Montant envoyé" : "Le client paiera", 13, Kit.MUTED, false), k.top(14));
        TextView big = k.text(fmt(qt.optLong("charge")) + " " + c.currency, 32, payout ? Kit.RED : Kit.PRIMARY, true);
        box.addView(big, k.top(2));

        LinearLayout rows = k.vbox();
        rows.setPadding(k.dp(14), k.dp(6), k.dp(14), k.dp(6));
        rows.setBackground(k.box(Kit.BG, 0, 14));
        rows.addView(receiptRow("Pays", c.flag() + " " + c.name, false));
        rows.addView(receiptRow("Réseau", str(op, "name").isEmpty() ? slug : str(op, "name"), false));
        rows.addView(receiptRow(payout ? "Numéro qui reçoit" : "Numéro qui paie", "+" + c.dial + " " + ph, false));
        if (payout) rows.addView(receiptRow("Bénéficiaire", who, false));
        if (!payout && rcp != null) {
            Countries.Country rcc = Countries.byIso(rcp.optString("country"));
            rows.addView(receiptRow("Pays qui reçoit", rcc != null ? rcc.flag() + " " + rcc.name : rcp.optString("country"), false));
            rows.addView(receiptRow("Numéro qui reçoit", "+" + (rcc != null ? rcc.dial : c.dial) + " " + rcp.optString("phone"), true));
            rows.addView(receiptRow("Réseau qui reçoit", rcp.optString("operator_name"), false));
            rows.addView(receiptRow("Bénéficiaire", rcp.optString("name"), false));
        }
        rows.addView(receiptRow("Commission (" + qt.optString("percent", "5") + " %)", fmt(qt.optLong("commission")) + " " + c.currency, false));
        if (!payout) rows.addView(receiptRow("Net après commission", fmt(qt.optLong("net")) + " " + c.currency, qt.isNull("payout_amount")));
        if (!payout && !qt.isNull("payout_amount")) {
            String pc = qt.optString("payout_currency");
            if (!pc.equals(c.currency)) {
                rows.addView(receiptRow("Taux de change", "1 " + c.currency + " = "
                        + new BigDecimal(qt.optDouble("fx_rate")).round(new java.math.MathContext(6)).stripTrailingZeros().toPlainString()
                        + " " + pc, false));
            }
            rows.addView(receiptRow("Le bénéficiaire reçoit", fmt(qt.optLong("payout_amount")) + " " + pc, true));
        }
        box.addView(rows, k.top(16));

        LinearLayout btns = k.hbox();
        TextView no = k.outlineButton("Annuler", Kit.MUTED);
        TextView yes = k.button("Confirmer", payout ? Kit.RED : Kit.PRIMARY, Color.WHITE);
        btns.addView(no, k.weight(1));
        LinearLayout.LayoutParams yp = k.weight(1);
        yp.leftMargin = k.dp(10);
        btns.addView(yes, yp);
        box.addView(btns, k.top(18));

        final Dialog d = sheet(box, 0);
        no.setOnClickListener(v -> d.dismiss());
        yes.setOnClickListener(v -> {
            d.dismiss();
            k.busy(go, true);
            status.loading("Envoi en cours…");
            io.execute(() -> {
                try {
                    JSONObject t = payout
                            ? api.payout(c.iso, slug, ph, who, value)
                            : api.collect(c.iso, slug, ph, value, code, rcp);
                    final String ref = t.getString("external_reference");
                    final String link = t.isNull("provider_link") ? "" : t.optString("provider_link", "");
                    main.post(() -> {
                        status.loading(payout
                                ? "Décaissement lancé, en attente de confirmation…"
                                : (link.isEmpty() ? "Demande envoyée : le client doit valider sur son téléphone…"
                                : "Finalisez le paiement dans la page de l'opérateur qui s'ouvre (Wave, Orange…)…"));
                        if (!payout && !link.isEmpty()) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                            } catch (ActivityNotFoundException ex) {
                                copy("Lien de paiement", link);
                                toast("Aucune application pour ouvrir le lien : il a été copié.");
                            }
                        }
                        startPolling(ref, status, go, payout);
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        handleError(e, status);
                        k.busy(go, false);
                    });
                }
            });
        });
        d.show();
    }

    // ------------------------------------------------------------------ suivi du statut

    private final List<Runnable> tickers = new ArrayList<>();

    private void stopPolling() {
        if (poller != null) main.removeCallbacks(poller);
        poller = null;
        for (Runnable r : tickers) main.removeCallbacks(r);
        tickers.clear();
    }

    private void startPolling(final String ref, final StatusBox status, final View go, final boolean payout) {
        stopPolling();
        pollCount = 0;
        poller = new Runnable() {
            @Override
            public void run() {
                final Runnable self = this;
                io.execute(() -> {
                    String st = "pending";
                    JSONObject t = null;
                    try {
                        t = api.status(ref);
                        st = t.optString("status", "pending");
                    } catch (Exception ignored) {
                        // erreur passagère : on réessaie au prochain tour
                    }
                    final String finalStatus = st;
                    final JSONObject row = t;
                    main.post(() -> {
                        if (poller != self) return; // annulé (changement d'onglet, etc.)
                        if ("approved".equals(finalStatus)) {
                            poller = null;
                            status.ok((payout ? "Décaissement réussi" : "Encaissement réussi")
                                    + (row != null ? " : " + money(row) : "")
                                    + (!payout && row != null && !str(row, "recipient_phone").isEmpty()
                                    ? ". Versement au +" + str(row, "recipient_phone") + " : " + str(row, "payout_note") : ""));
                            k.busy(go, false);
                        } else if ("rejected".equals(finalStatus)) {
                            poller = null;
                            status.err(payout
                                    ? "Décaissement refusé (le wallet est remboursé automatiquement)."
                                    : "Paiement refusé ou expiré. Aucun montant débité.");
                            k.busy(go, false);
                        } else if (++pollCount > 60) {
                            poller = null;
                            status.warn("Toujours en attente. Suivez-le dans l'onglet Historique.");
                            k.busy(go, false);
                        } else {
                            main.postDelayed(self, 3000);
                        }
                    });
                });
            }
        };
        main.postDelayed(poller, 3000);
    }

    // ------------------------------------------------------------------ historique

    private void showHistory() {
        final LinearLayout list = k.vbox();
        final StatusBox info = new StatusBox();
        info.loading("Chargement de l'historique…");
        content.addView(info.box, k.top(0));
        content.addView(list, k.top(4));

        io.execute(() -> {
            try {
                final JSONArray arr = mergeHistory(api.history(), loadCryptoHistory());
                main.post(() -> {
                    if (!"history".equals(tab)) return;
                    if (arr.length() == 0) {
                        info.warn("Aucune transaction pour le moment.");
                    } else {
                        info.hide();
                    }
                    renderHistory(list, arr);
                });
            } catch (Exception e) {
                main.post(() -> handleError(e, info));
            }
        });
    }

    private void renderHistory(LinearLayout list, JSONArray arr) {
        list.removeAllViews();
        final boolean admin = "admin".equals(role);
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject t = arr.optJSONObject(i);
            if (t == null) continue;
            if ("crypto".equals(str(t, "type"))) {
                list.addView(cryptoHistoryCard(t), k.top(i == 0 ? 0 : 10));
                continue;
            }
            final boolean collect = "collect".equals(str(t, "type"));
            final String st = str(t, "status");
            final String ref = str(t, "external_reference");
            final String refundRef = str(t, "refund_reference");
            final boolean refundRunning = collect && "approved".equals(st) && !refundRef.isEmpty();

            String label;
            int fg, bg;
            if ("approved".equals(st) && !refundRunning) {
                label = "Réussi";
                fg = Kit.PRIMARY;
                bg = Kit.PRIMARY_SOFT;
            } else if ("rejected".equals(st)) {
                label = "Échoué";
                fg = Kit.RED;
                bg = Kit.RED_SOFT;
            } else if ("refunded".equals(st)) {
                label = "Remboursé";
                fg = Kit.MUTED;
                bg = Kit.BG;
            } else {
                label = refundRunning ? "Remboursement…" : "En attente";
                fg = Kit.AMBER;
                bg = Kit.AMBER_SOFT;
            }

            LinearLayout card = k.card();
            card.setPadding(k.dp(14), k.dp(14), k.dp(14), k.dp(14));

            LinearLayout top = k.hbox();
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout ic = k.hbox();
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(k.circle(collect ? Kit.PRIMARY_SOFT : Kit.RED_SOFT));
            ic.addView(k.icon(collect ? Kit.IconView.DOWN : Kit.IconView.UP, 20, collect ? Kit.PRIMARY : Kit.RED));
            top.addView(ic, k.size(42, 42));

            Countries.Country cc = Countries.byIso(str(t, "country"));
            LinearLayout mid = k.vbox();
            mid.addView(k.text(collect ? "Encaissement" : "Décaissement", 15, Kit.TEXT, true));
            mid.addView(k.text((cc != null ? cc.flag() + " " + cc.name : str(t, "country")) + "  ·  " + str(t, "operator"),
                    12.5f, Kit.MUTED, false));
            LinearLayout.LayoutParams mp = k.weight(1);
            mp.leftMargin = k.dp(12);
            top.addView(mid, mp);

            LinearLayout right = k.vbox();
            right.setGravity(Gravity.END);
            long amt = t.optLong("amount");
            right.addView(k.text((collect ? "+" : "−") + fmt(amt) + " " + str(t, "currency"), 15,
                    collect ? Kit.PRIMARY : Kit.TEXT, true));
            LinearLayout.LayoutParams bp = k.wrap();
            bp.topMargin = k.dp(4);
            bp.gravity = Gravity.END;
            right.addView(k.pill(label, fg, bg), bp);
            top.addView(right, k.wrap());
            card.addView(top);

            StringBuilder sb = new StringBuilder();
            if (!collect && !str(t, "recipient_name").isEmpty()) {
                sb.append("Bénéficiaire : ").append(str(t, "recipient_name")).append("\n");
            }
            sb.append(collect ? "Numéro qui paie : +" : "Numéro qui reçoit : +").append(str(t, "phone"));
            if (collect && !str(t, "recipient_phone").isEmpty()) {
                sb.append("\nNuméro qui reçoit : +").append(str(t, "recipient_phone"));
                if (!str(t, "recipient_operator").isEmpty()) sb.append(" · ").append(str(t, "recipient_operator"));
                if (!str(t, "recipient_name").isEmpty()) sb.append(" · ").append(str(t, "recipient_name"));
                Countries.Country rcc = Countries.byIso(str(t, "recipient_country"));
                if (rcc != null && !rcc.iso.equals(str(t, "country"))) sb.append("\n").append(rcc.flag()).append(" ").append(rcc.name);
                if (!t.isNull("payout_amount")) {
                    sb.append("\nVersé : ").append(fmt(t.optLong("payout_amount"))).append(" ").append(str(t, "recipient_currency"));
                }
                if (!str(t, "payout_note").isEmpty()) sb.append("\n").append(str(t, "payout_note"));
            }
            if (!t.isNull("commission_total") && t.optLong("commission_total") > 0) {
                sb.append("\nCommission ").append(fmt(t.optLong("commission_total")))
                        .append("  ·  net ").append(fmt(t.optLong("net_amount")));
                if (admin && !t.isNull("my_commission")) {
                    sb.append("\nDont frais réseau ").append(fmt(t.optLong("sebpay_fee")))
                            .append("  ·  vous ").append(fmt(t.optLong("my_commission")));
                }
            }
            if (!str(t, "refund_note").isEmpty()) sb.append("\n").append(str(t, "refund_note").replaceAll("(?i)seb\\s?pay", "Kouamépay"));
            sb.append("\n").append(when(str(t, "created_at")));
            final String toRefresh = "pending".equals(st) ? ref : (refundRunning ? refundRef : "");
            if (!toRefresh.isEmpty()) sb.append("  ·  touchez pour actualiser");
            card.addView(k.text(sb.toString(), 12.5f, Kit.MUTED, false), k.top(10));

            // statut à actualiser : la transaction elle-même, ou son remboursement en cours
            if (!toRefresh.isEmpty()) {
                card.setOnClickListener(v -> io.execute(() -> {
                    try {
                        api.status(toRefresh);
                    } catch (Exception ignored) {
                    }
                    main.post(() -> {
                        if ("history".equals(tab)) showTab("history");
                    });
                }));
            }

            if (admin && collect && "approved".equals(st) && refundRef.isEmpty()) {
                final TextView rb = k.outlineButton("Rembourser le client", Kit.RED);
                rb.setOnClickListener(v -> confirmSheet("Rembourser ce client ?",
                        money(t) + " seront renvoyés à +" + str(t, "phone") + ".", "Rembourser", Kit.RED, () -> {
                            k.busy(rb, true);
                            io.execute(() -> {
                                try {
                                    api.refund(ref);
                                    main.post(() -> {
                                        toast("Remboursement lancé.");
                                        if ("history".equals(tab)) showTab("history");
                                    });
                                } catch (Exception e) {
                                    main.post(() -> {
                                        k.busy(rb, false);
                                        handleError(e, null);
                                    });
                                }
                            });
                        }));
                card.addView(rb, k.top(12));
            }
            list.addView(card, k.top(i == 0 ? 0 : 10));
        }
    }

    // ------------------------------------------------------------------ paiements BNB : historique client

    /** Paiements BNB ; tableau vide si la migration SQL n'est pas encore appliquée (l'historique SebPay reste visible). */
    private JSONArray loadCryptoHistory() {
        try {
            return api.cryptoHistory();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONArray mergeHistory(JSONArray a, JSONArray b) {
        List<JSONObject> all = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) all.add(a.optJSONObject(i));
        for (int i = 0; i < b.length(); i++) if (b.optJSONObject(i) != null) all.add(b.optJSONObject(i));
        all.sort((x, y) -> str(y, "created_at").compareTo(str(x, "created_at")));
        JSONArray out = new JSONArray();
        for (JSONObject o : all) out.put(o);
        return out;
    }

    private View cryptoHistoryCard(final JSONObject t) {
        final boolean admin = "admin".equals(role);
        final String pay = str(t, "status");
        final String po = str(t, "payout_status");
        final String ref = str(t, "reference");
        String label;
        int fg, bg;
        if ("paid".equals(po)) {
            label = "Payé";
            fg = Kit.PRIMARY;
            bg = Kit.PRIMARY_SOFT;
        } else if ("reported".equals(po)) {
            label = "Signalé";
            fg = Kit.RED;
            bg = Kit.RED_SOFT;
        } else if ("proof_sent".equals(po)) {
            label = "Preuves envoyées";
            fg = Kit.AMBER;
            bg = Kit.AMBER_SOFT;
        } else if ("underpaid".equals(pay)) {
            label = "Montant insuffisant";
            fg = Kit.RED;
            bg = Kit.RED_SOFT;
        } else if ("approved".equals(pay)) {
            label = "Paiement reçu";
            fg = Kit.PRIMARY;
            bg = Kit.PRIMARY_SOFT;
        } else if (t.optBoolean("expired", false)) {
            label = "Expiré";
            fg = Kit.MUTED;
            bg = Kit.BG;
        } else {
            label = "En attente de paiement";
            fg = Kit.AMBER;
            bg = Kit.AMBER_SOFT;
        }

        LinearLayout card = k.card();
        card.setPadding(k.dp(14), k.dp(14), k.dp(14), k.dp(14));
        LinearLayout top = k.hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = k.text("\u25C6", 18, Kit.BNB_DARK, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(k.circle(Kit.BNB));
        top.addView(logo, k.size(42, 42));
        Countries.Country cc = Countries.byIso(str(t, "country"));
        LinearLayout mid = k.vbox();
        mid.addView(k.text("Paiement BNB", 15, Kit.TEXT, true));
        mid.addView(k.text((cc != null ? cc.flag() + " " + cc.name : str(t, "country")) + "  ·  " + str(t, "recipient_operator"),
                12.5f, Kit.MUTED, false));
        LinearLayout.LayoutParams mp = k.weight(1);
        mp.leftMargin = k.dp(12);
        top.addView(mid, mp);
        LinearLayout right = k.vbox();
        right.setGravity(Gravity.END);
        right.addView(k.text(str(t, "amount_usd") + " USD", 15, Kit.TEXT, true));
        LinearLayout.LayoutParams bp = k.wrap();
        bp.topMargin = k.dp(4);
        bp.gravity = Gravity.END;
        right.addView(k.pill(label, fg, bg), bp);
        top.addView(right, k.wrap());
        card.addView(top);

        StringBuilder sb = new StringBuilder();
        sb.append("Numéro qui reçoit : +").append(str(t, "recipient_phone"));
        if (!str(t, "expected_bnb").isEmpty()) {
            sb.append("\n").append(new BigDecimal(str(t, "expected_bnb")).stripTrailingZeros().toPlainString()).append(" BNB");
        }
        if ("reported".equals(po)) sb.append("\nSignalé : ").append(str(t, "report_note"));
        if ("pending".equals(pay) && !t.optBoolean("expired", false)) {
            sb.append("\nExpire dans ").append(leftText(str(t, "created_at"))).append(" (24 h maximum)");
        }
        sb.append("\n").append(when(str(t, "created_at")));
        card.addView(k.text(sb.toString(), 12.5f, Kit.MUTED, false), k.top(10));

        if (!admin && ("awaiting".equals(po) || "reported".equals(po)) && !"underpaid".equals(pay) && !t.optBoolean("expired", false)) {
            card.addView(proofSection(ref, parseMs(str(t, "created_at")), t.optInt("proof_count", 0), false, null), k.top(12));
        }
        if (!admin && "paid".equals(po)) {
            final TextView rb = k.outlineButton("Argent non reçu ? Signaler", Kit.RED);
            rb.setOnClickListener(v -> confirmSheet("Signaler un problème ?",
                    "L'administrateur sera prévenu que l'argent n'est pas arrivé au +" + str(t, "recipient_phone") + ".",
                    "Signaler", Kit.RED, () -> {
                        k.busy(rb, true);
                        io.execute(() -> {
                            try {
                                api.cryptoReport(ref, "Argent non reçu");
                                main.post(() -> {
                                    toast("Signalement envoyé à l'administrateur.");
                                    if ("history".equals(tab)) showTab("history");
                                });
                            } catch (Exception e) {
                                main.post(() -> {
                                    k.busy(rb, false);
                                    handleError(e, null);
                                });
                            }
                        });
                    }));
            card.addView(rb, k.top(12));
        }
        return card;
    }

    // ------------------------------------------------------------------ paiements BNB : décaissements à traiter (administrateur)

    private void loadImage(final ImageView iv, final String url, final Runnable afterLoad) {
        Bitmap cached = imgCache.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        io.execute(() -> {
            try {
                byte[] b = api.download(url);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(b, 0, b.length, o);
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / sample > 1600) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                final Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length, o2);
                if (bm == null) return;
                main.post(() -> {
                    imgCache.put(url, bm);
                    iv.setImageBitmap(bm);
                    if (afterLoad != null) afterLoad.run();
                });
            } catch (Exception ignored) {
                // miniature indisponible : le lien signé a peut-être expiré, un rafraîchissement le renouvelle
            }
        });
    }

    private void showFullImage(String url) {
        Bitmap bm = imgCache.get(url);
        if (bm == null) return;
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setBackgroundColor(Color.BLACK);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(bm);
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        d.show();
    }

    /** Pastille « Utilisateurs (n) » : nombre d'inscriptions à valider. */
    private void refreshUsersBadge() {
        if (!"admin".equals(role)) return;
        io.execute(() -> {
            try {
                final int n = api.usersPending().length();
                main.post(() -> {
                    int i = navKeys.indexOf("users");
                    if (i >= 0) navLabels.get(i).setText(n > 0 ? "Utilisateurs (" + n + ")" : "Utilisateurs");
                });
            } catch (Exception ignored) {
                // serveur pas encore à jour ou réseau coupé : pas de pastille
            }
        });
    }

    /** « Mes utilisateurs » : inscriptions à valider, puis tous les comptes. */
    private void showUsers() {
        if (!"admin".equals(role)) {
            showTab("collect");
            return;
        }
        content.addView(k.text("Chargement…", 14, Kit.MUTED, false));
        io.execute(() -> {
            try {
                final JSONArray arr = api.usersList();
                main.post(() -> {
                    if (!"users".equals(tab)) return;
                    content.removeAllViews();
                    List<JSONObject> pending = new ArrayList<>();
                    List<JSONObject> all = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject u = arr.optJSONObject(i);
                        if (u == null) continue;
                        if (u.optBoolean("approved", true)) all.add(u);
                        else pending.add(u);
                    }
                    int pi = navKeys.indexOf("users");
                    if (pi >= 0) navLabels.get(pi).setText(pending.isEmpty() ? "Utilisateurs" : "Utilisateurs (" + pending.size() + ")");
                    content.addView(k.sectionLabel("Inscriptions à valider (" + pending.size() + ")"));
                    if (pending.isEmpty()) {
                        content.addView(k.text("Aucune inscription en attente.", 13.5f, Kit.MUTED, false), k.top(6));
                    }
                    for (JSONObject u : pending) content.addView(pendingUserCard(u), k.top(8));
                    content.addView(k.sectionLabel("Mes utilisateurs (" + all.size() + ")"), k.top(22));
                    for (JSONObject u : all) content.addView(userCard(u), k.top(8));
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!"users".equals(tab)) return;
                    content.removeAllViews();
                    content.addView(k.text(errMsg(e), 14, Kit.RED, false));
                });
            }
        });
    }

    private View userCard(final JSONObject u) {
        final String name = (str(u, "first_name") + " " + str(u, "last_name")).trim();
        final boolean admin = "admin".equals(str(u, "role"));
        LinearLayout card = k.card();
        card.setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12));
        LinearLayout top = k.hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(k.text(name.isEmpty() ? str(u, "email") : name, 15, Kit.TEXT, true), k.weight(1));
        top.addView(admin ? k.pill("Administrateur", Kit.PRIMARY, Kit.PRIMARY_SOFT) : k.pill("Agent", Kit.MUTED, Kit.BG), k.wrap());
        card.addView(top);
        card.addView(k.text(str(u, "email") + "\nInscrit le " + when(str(u, "created_at")), 12.5f, Kit.MUTED, false), k.top(4));
        return card;
    }

    private View pendingUserCard(final JSONObject u) {
        final String id = str(u, "user_id");
        final String name = (str(u, "first_name") + " " + str(u, "last_name")).trim();
        LinearLayout card = k.card();
        card.setPadding(k.dp(14), k.dp(14), k.dp(14), k.dp(14));
        card.addView(k.text(name.isEmpty() ? str(u, "email") : name, 15, Kit.TEXT, true));
        card.addView(k.text(str(u, "email") + "\nInscrit le " + when(str(u, "created_at")), 12.5f, Kit.MUTED, false), k.top(4));
        LinearLayout row = k.hbox();
        final TextView no = k.outlineButton("Refuser", Kit.RED);
        final TextView yes = k.button("Valider", Kit.PRIMARY, Color.WHITE);
        LinearLayout.LayoutParams np = k.weight(1);
        np.rightMargin = k.dp(8);
        row.addView(no, np);
        row.addView(yes, k.weight(1));
        card.addView(row, k.top(12));
        yes.setOnClickListener(v -> {
            k.busy(yes, true);
            io.execute(() -> {
                try {
                    api.userApprove(id);
                    main.post(() -> {
                        toast("Inscription validée.");
                        if ("users".equals(tab)) showTab("users");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        k.busy(yes, false);
                        handleError(e, null);
                    });
                }
            });
        });
        no.setOnClickListener(v -> confirmSheet("Refuser cette inscription ?",
                "Le compte " + str(u, "email") + " n'aura pas accès à Kouamépay.", "Refuser", Kit.RED, () -> {
                    io.execute(() -> {
                        try {
                            api.userReject(id);
                            main.post(() -> {
                                toast("Inscription refusée.");
                                if ("users".equals(tab)) showTab("users");
                            });
                        } catch (Exception e) {
                            main.post(() -> handleError(e, null));
                        }
                    });
                }));
        return card;
    }

    /** Liste des décaissements BNB à traiter : pays, réseau, numéro, preuves, bouton « Décaisser ». */
    private View bnbQueue() {
        final LinearLayout wrap = k.vbox();
        if (!"admin".equals(role)) return wrap;
        io.execute(() -> {
            try {
                final JSONArray arr = api.cryptoList();
                main.post(() -> {
                    if (!"payout".equals(tab)) return;
                    if (arr.length() == 0) {
                        wrap.addView(k.text("Aucun paiement crypto en attente.", 13.5f, Kit.MUTED, false));
                        return;
                    }
                    wrap.addView(k.sectionLabel("Paiements crypto à traiter (" + arr.length() + ")"));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject t = arr.optJSONObject(i);
                        if (t != null) wrap.addView(bnbQueueCard(t), k.top(8));
                    }
                });
            } catch (Exception ignored) {
                // migration SQL pas encore appliquée ou réseau coupé : le décaissement Mobile Money reste utilisable
            }
        });
        return wrap;
    }

    private View bnbQueueCard(final JSONObject t) {
        final String pay = str(t, "status");
        final String phone = str(t, "recipient_phone");
        Countries.Country cc = Countries.byIso(str(t, "country"));
        final String where = cc != null ? cc.flag() + " " + cc.name : str(t, "country");

        LinearLayout card = k.card();
        card.setPadding(k.dp(14), k.dp(14), k.dp(14), k.dp(14));
        LinearLayout top = k.hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(k.text(where, 16, Kit.TEXT, true), k.weight(1));
        top.addView(k.text(str(t, "amount_usd") + " USD", 16, Kit.TEXT, true), k.wrap());
        card.addView(top);

        StringBuilder sb = new StringBuilder();
        sb.append("Réseau : ").append(str(t, "recipient_operator"));
        sb.append("\nNuméro : +").append(phone);
        sb.append("\nPaiement BNB : ").append("approved".equals(pay) ? "reçu et confirmé"
                : "underpaid".equals(pay) ? "montant insuffisant"
                : "en attente de paiement (expire dans " + leftText(str(t, "created_at")) + ")");
        if (str(t, "proof_sent_at").isEmpty()) sb.append("\nPreuves : pas encore envoyées");
        else sb.append("\nPreuves reçues le ").append(when(str(t, "proof_sent_at")));
        card.addView(k.text(sb.toString(), 13.5f, Kit.TEXT, false), k.top(10));

        if ("reported".equals(str(t, "payout_status"))) {
            TextView rep = k.text("⚠ Le client signale : " + (str(t, "report_note").isEmpty() ? "argent non reçu" : str(t, "report_note")),
                    13.5f, Kit.RED, true);
            rep.setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10));
            rep.setBackground(k.box(Kit.RED_SOFT, 0, 12));
            card.addView(rep, k.top(10));
        }

        JSONArray proofs = t.optJSONArray("proofs");
        if (proofs != null && proofs.length() > 0) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = k.hbox();
            hs.addView(row);
            for (int i = 0; i < proofs.length(); i++) {
                final String url = proofs.optString(i);
                final ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackground(k.box(Kit.BG, 0, 12));
                iv.setClipToOutline(true);
                loadImage(iv, url, null);
                iv.setOnClickListener(v -> showFullImage(url));
                LinearLayout.LayoutParams ip = k.size(96, 96);
                ip.rightMargin = k.dp(8);
                row.addView(iv, ip);
            }
            card.addView(hs, k.top(12));
            card.addView(k.text("Touchez une image pour l'agrandir.", 12, Kit.MUTED, false), k.top(6));
        }

        final String po = str(t, "payout_status");
        if ("proof_sent".equals(po) || "reported".equals(po)) {
            TextView go = k.button("Décaisser", Kit.RED, Color.WHITE);
            go.setOnClickListener(v -> payoutSheet(t, where));
            card.addView(go, k.top(12));
        } else {
            TextView wait = k.text("En attente du client : paiement BNB puis preuves. Le bouton Décaisser apparaîtra à la réception des preuves.",
                    12.5f, Kit.MUTED, false);
            card.addView(wait, k.top(12));
        }
        return card;
    }

    /** Décaissement manuel : l'administrateur voit pays, réseau et numéro, envoie l'argent, puis marque « Payé ». */
    private void payoutSheet(final JSONObject t, String where) {
        final String phone = str(t, "recipient_phone");
        final String ref = str(t, "reference");
        LinearLayout box = k.vbox();
        box.setPadding(k.dp(20), k.dp(20), k.dp(20), k.dp(20));
        box.addView(k.text("Décaisser manuellement", 20, Kit.TEXT, true));
        box.addView(k.text("Envoyez vous-même l'argent au numéro ci-dessous, puis confirmez. "
                + "Le client verra « Payé » et pourra signaler si l'argent n'arrive pas.", 14, Kit.MUTED, false), k.top(8));
        LinearLayout rows = k.vbox();
        rows.setPadding(k.dp(14), k.dp(6), k.dp(14), k.dp(6));
        rows.setBackground(k.box(Kit.BG, 0, 14));
        rows.addView(receiptRow("Pays", where, false));
        rows.addView(receiptRow("Réseau", str(t, "recipient_operator"), false));
        rows.addView(receiptRow("Numéro", "+" + phone, true));
        rows.addView(receiptRow("Montant payé", str(t, "amount_usd") + " USD", false));
        box.addView(rows, k.top(16));

        TextView copyNum = k.outlineButton("Copier le numéro", Kit.PRIMARY);
        TextView yes = k.button("J'ai envoyé l'argent : marquer Payé", Kit.PRIMARY, Color.WHITE);
        TextView no = k.outlineButton("Annuler", Kit.MUTED);
        box.addView(copyNum, k.top(16));
        box.addView(yes, k.top(10));
        box.addView(no, k.top(10));
        final Dialog d = sheet(box, 0);
        copyNum.setOnClickListener(v -> {
            copy("Numéro", "+" + phone);
            toast("Numéro copié.");
        });
        no.setOnClickListener(v -> d.dismiss());
        yes.setOnClickListener(v -> {
            k.busy(yes, true);
            io.execute(() -> {
                try {
                    api.cryptoPay(ref);
                    main.post(() -> {
                        d.dismiss();
                        toast("Marqué comme payé.");
                        if ("payout".equals(tab)) showTab("payout");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        k.busy(yes, false);
                        handleError(e, null);
                    });
                }
            });
        });
        d.show();
    }

    // ------------------------------------------------------------------ service client

    private static final String SUPPORT_GREETING =
            "Bonjour ! Je suis l'assistant Kouamépay. Décrivez votre problème, je vous réponds tout de suite. "
                    + "Si je ne trouve pas la solution, l'administrateur prendra le relais.";

    private String supportTarget = null;   // admin : client dont la conversation est ouverte
    private String supportLabel = "";
    private Runnable beat;
    private boolean appVisible = true;

    /** L'administrateur donne un signe de vie toutes les 30 s tant que l'appli est ouverte : c'est ce qui affiche « En ligne ». */
    private void startBeat() {
        stopBeat();
        if (!"admin".equals(role)) return;
        beat = new Runnable() {
            @Override
            public void run() {
                if (appVisible && api.isLoggedIn()) {
                    io.execute(() -> {
                        try {
                            api.ping();
                        } catch (Exception ignored) {
                            // réseau coupé : on réessaie au prochain battement
                        }
                    });
                }
                main.postDelayed(this, 30000);
            }
        };
        main.post(beat);
    }

    private void stopBeat() {
        if (beat != null) main.removeCallbacks(beat);
        beat = null;
    }

    private void setOnlinePill(TextView pill, boolean online) {
        pill.setText(online ? "● En ligne" : "○ Hors ligne");
        pill.setTextColor(online ? Kit.PRIMARY : Kit.MUTED);
        pill.setBackground(k.box(online ? Kit.PRIMARY_SOFT : 0xFFE9EEEB, 0, 10));
    }

    /** Carte du service client en haut de l'accueil (visible par tous les utilisateurs). */
    private View supportCard() {
        final boolean admin = "admin".equals(role);
        LinearLayout row = k.hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12));
        row.setBackground(k.box(Kit.SURFACE, Kit.BORDER, 14));
        row.addView(k.icon(Kit.IconView.CHAT, 26, Kit.PRIMARY), k.size(26, 26));
        LinearLayout mid = k.vbox();
        mid.addView(k.text("Service client", 15, Kit.TEXT, true));
        final TextView sub = k.text(admin ? "Messages de vos clients" : "Une question ? Réponse immédiate.", 12.5f, Kit.MUTED, false);
        mid.addView(sub);
        LinearLayout.LayoutParams mp = k.weight(1);
        mp.leftMargin = k.dp(12);
        row.addView(mid, mp);
        final TextView pill = k.pill("…", Kit.MUTED, 0xFFE9EEEB);
        row.addView(pill, k.wrap());
        row.setClickable(true);
        row.setOnClickListener(v -> showTab("support"));
        io.execute(() -> {
            try {
                final JSONObject s = api.supportStatus();
                main.post(() -> {
                    setOnlinePill(pill, s.optBoolean("admin_online"));
                    int n = s.optInt("pending", 0);
                    if (admin && n > 0) sub.setText(n + " conversation(s) à traiter");
                });
            } catch (Exception ignored) {
                // la carte reste neutre si le statut est indisponible
            }
        });
        return row;
    }

    private void showSupport() {
        if ("admin".equals(role) && supportTarget == null) showSupportInbox();
        else showChat("admin".equals(role) ? supportTarget : null);
    }

    private void scrollToEnd() {
        if (scroll == null || content == null) return;
        scroll.post(() -> scroll.scrollTo(0, content.getHeight()));
    }

    private View addBubble(LinearLayout list, String sender, String body, String iso, boolean adminView) {
        final boolean mine = adminView ? "admin".equals(sender) : "user".equals(sender);
        int fill, fg, labelColor, stroke;
        String label;
        if (mine) {
            fill = Kit.PRIMARY; fg = Color.WHITE; labelColor = 0xCCFFFFFF; stroke = 0; label = "Vous";
        } else if ("admin".equals(sender)) {
            fill = Kit.PRIMARY_SOFT; fg = Kit.TEXT; labelColor = Kit.PRIMARY; stroke = 0; label = "Administrateur";
        } else if ("ai".equals(sender)) {
            fill = Kit.SURFACE; fg = Kit.TEXT; labelColor = Kit.MUTED; stroke = Kit.BORDER; label = "Assistant";
        } else {
            fill = Kit.SURFACE; fg = Kit.TEXT; labelColor = Kit.MUTED; stroke = Kit.BORDER; label = "Client";
        }
        LinearLayout b = k.vbox();
        b.setPadding(k.dp(14), k.dp(10), k.dp(14), k.dp(10));
        b.setBackground(k.box(fill, stroke, 16));
        b.addView(k.text(label, 11.5f, labelColor, true));
        b.addView(k.text(body, 15, fg, false), k.top(2));
        if (iso != null && !iso.isEmpty()) {
            b.addView(k.text(when(iso), 11, mine ? 0xB3FFFFFF : Kit.MUTED, false), k.top(4));
        }
        LinearLayout.LayoutParams lp = k.wrap();
        lp.topMargin = k.dp(8);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        if (mine) lp.leftMargin = k.dp(44);
        else lp.rightMargin = k.dp(44);
        list.addView(b, lp);
        return b;
    }

    private boolean appendMessages(LinearLayout list, JSONArray arr, long[] lastId, boolean adminView) {
        boolean added = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            long id = m.optLong("id");
            if (id <= lastId[0]) continue;
            addBubble(list, str(m, "sender"), str(m, "body"), str(m, "created_at"), adminView);
            lastId[0] = id;
            added = true;
        }
        return added;
    }

    /** Admin : liste des clients qui ont écrit, ceux à traiter en premier. */
    private void showSupportInbox() {
        final LinearLayout list = k.vbox();
        final StatusBox info = new StatusBox();
        info.loading("Chargement des conversations…");
        content.addView(info.box, k.top(0));
        content.addView(list, k.top(4));
        poller = new Runnable() {
            @Override
            public void run() {
                final Runnable self = this;
                io.execute(() -> {
                    try {
                        final JSONArray arr = api.supportInbox();
                        main.post(() -> {
                            if (!"support".equals(tab) || supportTarget != null || poller != self) return;
                            if (arr.length() == 0) info.warn("Aucun message client pour le moment.");
                            else info.hide();
                            renderInbox(list, arr);
                            main.postDelayed(self, 8000);
                        });
                    } catch (Exception e) {
                        main.post(() -> {
                            if (poller != self) return;
                            handleError(e, info);
                            if (api.isLoggedIn()) main.postDelayed(self, 10000);
                        });
                    }
                });
            }
        };
        main.post(poller);
    }

    private void renderInbox(LinearLayout list, JSONArray arr) {
        list.removeAllViews();
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            final String uid = str(c, "user_id");
            String email = str(c, "email");
            final String label = email.isEmpty() ? "Client" : email;
            final boolean pending = c.optBoolean("pending");
            LinearLayout card = k.card();
            card.setClickable(true);
            LinearLayout top = k.hbox();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(k.text(label, 15, Kit.TEXT, true), k.weight(1));
            if (pending) top.addView(k.pill("À traiter", Kit.AMBER, Kit.AMBER_SOFT), k.wrap());
            card.addView(top);
            String last = str(c, "last_body");
            if (last.length() > 90) last = last.substring(0, 90) + "…";
            card.addView(k.text(last, 13.5f, Kit.MUTED, false), k.top(4));
            card.addView(k.text(when(str(c, "last_at")), 11.5f, Kit.MUTED, false), k.top(4));
            card.setOnClickListener(v -> {
                supportTarget = uid;
                supportLabel = label;
                showTab("support");
            });
            list.addView(card, k.top(i == 0 ? 0 : 10));
        }
    }

    /** Conversation : le client parle à l'assistant IA ; l'admin répond à un client. */
    private void showChat(final String target) {
        final boolean adminView = target != null;
        final LinearLayout list = k.vbox();
        final long[] lastId = {0};
        final boolean[] sending = {false};
        final StatusBox info = new StatusBox();

        if (adminView) {
            TextView back = k.outlineButton("← Toutes les conversations", Kit.PRIMARY);
            back.setOnClickListener(v -> {
                supportTarget = null;
                showTab("support");
            });
            content.addView(back, k.top(0));
        }
        final TextView pill = k.pill("…", Kit.MUTED, 0xFFE9EEEB);
        LinearLayout.LayoutParams pp = k.wrap();
        pp.topMargin = k.dp(adminView ? 12 : 0);
        content.addView(pill, pp);
        content.addView(info.box, k.top(10));
        content.addView(list, k.top(4));

        LinearLayout inputRow = k.hbox();
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText input = k.field(adminView ? "Votre réponse…" : "Écrivez votre message…",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        final TextView send = k.button("Envoyer", Kit.PRIMARY, Color.WHITE);
        inputRow.addView(input, k.weight(1));
        LinearLayout.LayoutParams sp = k.wrap();
        sp.leftMargin = k.dp(8);
        inputRow.addView(send, sp);
        content.addView(inputRow, k.top(14));

        final Runnable doSend = () -> {
            final String text = input.getText().toString().trim();
            if (text.isEmpty() || sending[0]) return;
            sending[0] = true;
            k.busy(send, true);
            input.setText("");
            final List<View> temp = new ArrayList<>();
            temp.add(addBubble(list, adminView ? "admin" : "user", text, null, adminView));
            if (!adminView) temp.add(addBubble(list, "ai", "L'assistant écrit…", null, false));
            scrollToEnd();
            io.execute(() -> {
                try {
                    final JSONArray got;
                    final boolean online;
                    if (adminView) {
                        JSONObject r = api.supportReply(target, text);
                        got = new JSONArray().put(r.optJSONObject("message"));
                        online = true;
                    } else {
                        JSONObject r = api.supportSend(text);
                        JSONArray a = r.optJSONArray("messages");
                        got = a != null ? a : new JSONArray();
                        online = r.optBoolean("admin_online");
                    }
                    main.post(() -> {
                        for (View v : temp) list.removeView(v);
                        sending[0] = false;
                        k.busy(send, false);
                        if (!adminView) setOnlinePill(pill, online);
                        appendMessages(list, got, lastId, adminView);
                        info.hide();
                        scrollToEnd();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        for (View v : temp) list.removeView(v);
                        sending[0] = false;
                        k.busy(send, false);
                        input.setText(text);
                        handleError(e, info);
                    });
                }
            });
        };
        send.setOnClickListener(v -> doSend.run());
        input.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                doSend.run();
                return true;
            }
            return false;
        });

        info.loading("Chargement…");
        poller = new Runnable() {
            boolean first = true;

            @Override
            public void run() {
                final Runnable self = this;
                if (sending[0]) {
                    main.postDelayed(self, 4000);
                    return;
                }
                io.execute(() -> {
                    try {
                        final JSONObject d = api.supportHistory(target);
                        main.post(() -> {
                            if (!"support".equals(tab) || poller != self) return;
                            setOnlinePill(pill, d.optBoolean("admin_online"));
                            if (!sending[0]) info.hide();
                            JSONArray arr = d.optJSONArray("messages");
                            if (arr == null) arr = new JSONArray();
                            if (first) {
                                first = false;
                                if (arr.length() == 0 && !adminView) addBubble(list, "ai", SUPPORT_GREETING, null, false);
                                if (arr.length() > 0) scrollToEnd();
                            }
                            if (!sending[0] && appendMessages(list, arr, lastId, adminView)) scrollToEnd();
                            main.postDelayed(self, 5000);
                        });
                    } catch (Exception e) {
                        main.post(() -> {
                            if (poller != self) return;
                            handleError(e, info);
                            if (api.isLoggedIn()) main.postDelayed(self, 8000);
                        });
                    }
                });
            }
        };
        main.post(poller);
    }
}
