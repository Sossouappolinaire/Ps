package bj.sebpay.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Petite boîte à outils de design : couleurs, cartes, boutons, champs, icônes dessinées (aucune ressource XML). */
final class Kit {

    // Palette
    static final int BG = 0xFFF2F5F3;
    static final int SURFACE = 0xFFFFFFFF;
    static final int PRIMARY = 0xFF0B6B4F;
    static final int PRIMARY_DARK = 0xFF063F2F;
    static final int PRIMARY_SOFT = 0xFFE2F2EB;
    static final int TEXT = 0xFF14201B;
    static final int MUTED = 0xFF6B7A74;
    static final int BORDER = 0xFFDCE4E0;
    static final int RED = 0xFFD64545;
    static final int RED_SOFT = 0xFFFCE8E8;
    static final int AMBER = 0xFF9A6200;
    static final int AMBER_SOFT = 0xFFFDF1D6;
    static final int BNB = 0xFFF3BA2F;
    static final int BNB_DARK = 0xFF181A20;
    static final int BNB_PANEL = 0xFF23262F;

    final Context c;
    private final float density;

    Kit(Context c) {
        this.c = c;
        this.density = c.getResources().getDisplayMetrics().density;
    }

    int dp(float v) {
        return Math.round(v * density);
    }

    // ------------------------------------------------------------ formes

    GradientDrawable box(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    GradientDrawable circle(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    GradientDrawable headerGradient(float bottomRadiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{PRIMARY, PRIMARY_DARK});
        float r = dp(bottomRadiusDp);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        return g;
    }

    // ------------------------------------------------------------ conteneurs

    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout hbox() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    LinearLayout card() {
        LinearLayout l = vbox();
        l.setBackground(box(SURFACE, 0, 18));
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setElevation(dp(2));
        return l;
    }

    /** Largeur maximale, hauteur adaptée, marge haute en dp. */
    LinearLayout.LayoutParams top(float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    LinearLayout.LayoutParams size(float wDp, float hDp) {
        return new LinearLayout.LayoutParams(dp(wDp), dp(hDp));
    }

    // ------------------------------------------------------------ texte

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView sectionLabel(String s) {
        TextView t = text(s.toUpperCase(), 11.5f, MUTED, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    TextView pill(String s, int fg, int bg) {
        TextView t = text(s, 11.5f, fg, true);
        t.setPadding(dp(9), dp(3), dp(9), dp(3));
        t.setBackground(box(bg, 0, 10));
        return t;
    }

    // ------------------------------------------------------------ boutons

    TextView button(String label, int fill, int fg) {
        TextView b = text(label, 16, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(15), dp(16), dp(15));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000), box(fill, 0, 14), box(Color.WHITE, 0, 14)));
        b.setClickable(true);
        return b;
    }

    TextView outlineButton(String label, int color) {
        TextView b = text(label, 15, color, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F000000), box(SURFACE, color, 14), box(Color.WHITE, 0, 14)));
        b.setClickable(true);
        return b;
    }

    void busy(View b, boolean busy) {
        b.setEnabled(!busy);
        b.setAlpha(busy ? 0.55f : 1f);
    }

    // ------------------------------------------------------------ champs

    EditText field(String hint, int inputType) {
        final EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0xFF9AA7A1);
        e.setTextColor(TEXT);
        e.setTextSize(16);
        e.setSingleLine(true); // avant setInputType, sinon le masquage du mot de passe saute
        e.setInputType(inputType);
        e.setPadding(dp(14), dp(14), dp(14), dp(14));
        e.setBackground(box(SURFACE, BORDER, 12));
        e.setOnFocusChangeListener((v, focused) -> v.setBackground(box(SURFACE, focused ? PRIMARY : BORDER, 12)));
        return e;
    }

    EditText password(String hint) {
        return field(hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    // ------------------------------------------------------------ puces

    TextView chip(String label, boolean selected) {
        TextView t = text(label, 14, selected ? Color.WHITE : TEXT, true);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setBackground(box(selected ? PRIMARY : SURFACE, selected ? 0 : BORDER, 22));
        return t;
    }

    void styleChip(TextView t, boolean selected) {
        t.setTextColor(selected ? Color.WHITE : TEXT);
        t.setBackground(box(selected ? PRIMARY : SURFACE, selected ? 0 : BORDER, 22));
    }

    // ------------------------------------------------------------ icônes

    IconView icon(int type, float sizeDp, int color) {
        IconView v = new IconView(c, type, color);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    /** Icônes vectorielles dessinées au trait : pas de dépendance, nettes sur tous les écrans. */
    static final class IconView extends View {
        static final int DOWN = 0, UP = 1, CLOCK = 2, CHEVRON = 3, CHECK = 4, CROSS = 5, GLOBE = 6, CHAT = 7, USERS = 8;

        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconView(Context ctx, int type, int color) {
            super(ctx);
            this.type = type;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            paint.setStrokeWidth(Math.min(w, h) * 0.09f);
            Path p = new Path();
            switch (type) {
                case DOWN:
                    p.moveTo(w * .5f, h * .12f); p.lineTo(w * .5f, h * .66f);
                    p.moveTo(w * .28f, h * .46f); p.lineTo(w * .5f, h * .68f); p.lineTo(w * .72f, h * .46f);
                    p.moveTo(w * .18f, h * .88f); p.lineTo(w * .82f, h * .88f);
                    break;
                case UP:
                    p.moveTo(w * .5f, h * .88f); p.lineTo(w * .5f, h * .34f);
                    p.moveTo(w * .28f, h * .54f); p.lineTo(w * .5f, h * .32f); p.lineTo(w * .72f, h * .54f);
                    p.moveTo(w * .18f, h * .12f); p.lineTo(w * .82f, h * .12f);
                    break;
                case CLOCK:
                    cv.drawCircle(w * .5f, h * .5f, Math.min(w, h) * .4f, paint);
                    p.moveTo(w * .5f, h * .28f); p.lineTo(w * .5f, h * .5f); p.lineTo(w * .66f, h * .6f);
                    break;
                case CHEVRON:
                    p.moveTo(w * .22f, h * .38f); p.lineTo(w * .5f, h * .66f); p.lineTo(w * .78f, h * .38f);
                    break;
                case CHECK:
                    p.moveTo(w * .2f, h * .52f); p.lineTo(w * .42f, h * .74f); p.lineTo(w * .8f, h * .28f);
                    break;
                case CROSS:
                    p.moveTo(w * .25f, h * .25f); p.lineTo(w * .75f, h * .75f);
                    p.moveTo(w * .75f, h * .25f); p.lineTo(w * .25f, h * .75f);
                    break;
                case CHAT:
                    p.addRoundRect(new RectF(w * .14f, h * .16f, w * .86f, h * .68f), w * .12f, w * .12f, Path.Direction.CW);
                    p.moveTo(w * .36f, h * .68f); p.lineTo(w * .3f, h * .88f); p.lineTo(w * .54f, h * .68f);
                    break;
                case USERS:
                    cv.drawCircle(w * .5f, h * .32f, w * .17f, paint);
                    p.moveTo(w * .2f, h * .88f);
                    p.cubicTo(w * .2f, h * .56f, w * .8f, h * .56f, w * .8f, h * .88f);
                    break;
                default: // GLOBE
                    cv.drawCircle(w * .5f, h * .5f, Math.min(w, h) * .4f, paint);
                    cv.drawOval(new RectF(w * .3f, h * .1f, w * .7f, h * .9f), paint);
                    p.moveTo(w * .1f, h * .5f); p.lineTo(w * .9f, h * .5f);
                    break;
            }
            cv.drawPath(p, paint);
        }
    }
}
