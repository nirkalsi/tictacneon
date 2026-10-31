package nir.com.tictacneon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements TttView.Listener, BillingManager.Listener {
    private static final String PREFS = "tictacneon", KEY = "state";

    private TttView game;
    private AdsManager ads;
    private BillingManager billing;
    private MissionsManager missions;
    private SharedPreferences sp;
    private LinearLayout bannerBox;
    private Button hintsBtn;
    private final Handler handler = new Handler();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF060D1F);
        root.addView(buildButtonRow());
        game = new TttView(this);
        game.setListener(this);
        root.addView(game, new LinearLayout.LayoutParams(-1, 0, 1f));
        bannerBox = new LinearLayout(this);
        root.addView(bannerBox, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        applySystemBarInsets(root);

        String s = sp.getString(KEY, null);
        if (s != null) game.restore(s);
        if (!sp.contains("hint_tokens")) sp.edit().putInt("hint_tokens", 3).apply();

        missions = new MissionsManager(this, null);

        ads = new AdsManager(this);
        boolean removed = sp.getBoolean("ads_removed", false);
        ads.setAdsRemoved(removed);
        if (removed) applyAdsRemoved();
        ads.initWithConsent(() -> {
            if (!removed && sp.getInt("games_done_flag", 0) > 0) ads.attachBanner(bannerBox);
        });

        billing = new BillingManager(this, this);

        if (sp.getString("first_run", null) == null)
            sp.edit().putString("first_run", today()).apply();

        if (sp.getInt("games_done_flag", 0) > 0) handler.postDelayed(this::maybeDailyBonus, 900);
        refreshHintsLabel();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // Edge-to-edge is enforced on API 35+ (targetSdk 36): pad the content below the status bar and above the nav bar.
    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    private GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0x33FFFFFF);
        return g;
    }

    private View buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(6), dp(6), dp(6), dp(2));
        addRowButton(row, "\uD83C\uDFAF MISSIONS", v -> showMissions(), 0xFF1565C0);
        hintsBtn = addRowButton(row, "\u2728 HINTS", v -> useOrGetHint(), 0xFF6A1B9A);
        return row;
    }

    private Button addRowButton(LinearLayout row, String label, View.OnClickListener l, int color) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(12f);
        btn.setSingleLine(true);
        btn.setPadding(0, 0, 0, 0);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(pill(color, 22));
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(btn, lp);
        btn.setOnClickListener(l);
        return btn;
    }

    private void refreshHintsLabel() {
        if (hintsBtn != null)
            hintsBtn.setText("HINTS " + sp.getInt("hint_tokens", 0));
    }

    private void addTokens(int n) {
        sp.edit().putInt("hint_tokens", sp.getInt("hint_tokens", 0) + n).apply();
        refreshHintsLabel();
    }

    // ---------- daily bonus ----------
    private String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private String yesterday() {
        Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private void maybeDailyBonus() {
        String last = sp.getString("daily_last", "");
        if (today().equals(last)) return;
        int streak = yesterday().equals(last) ? sp.getInt("daily_streak", 0) + 1 : 1;
        int tokens = Math.min(1 + streak, 5);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(12), dp(16), dp(12), dp(4));

        TextView gift = new TextView(this);
        gift.setText("\u2728");
        gift.setTextSize(44f);
        gift.setGravity(Gravity.CENTER);
        box.addView(gift);

        TextView title = new TextView(this);
        title.setText("Daily Bonus - Day " + streak);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(6), 0, dp(10));
        box.addView(title);

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER);
        for (int d = 1; d <= 7; d++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            boolean isToday = d == Math.min(streak, 7);
            boolean claimed = d < streak;
            boolean isDay7 = d == 7;
            cell.setBackground(pill(isToday || isDay7 ? 0xFFFFD54F : (claimed ? 0xFFC8E6C9 : 0xFFECEFF1), 10));
            cell.setPadding(dp(2), dp(6), dp(2), dp(6));
            TextView dl = new TextView(this);
            dl.setText("Day " + d);
            dl.setTextSize(9f);
            dl.setTextColor(0xFF37474F);
            dl.setGravity(Gravity.CENTER);
            cell.addView(dl);
            TextView dv = new TextView(this);
            dv.setText(claimed ? "\u2714" : "+" + Math.min(1 + d, 5));
            dv.setTextSize(isDay7 ? 13f : 11f);
            dv.setTypeface(Typeface.DEFAULT_BOLD);
            dv.setTextColor(isToday ? 0xFF6D4C00 : 0xFF37474F);
            dv.setGravity(Gravity.CENTER);
            cell.addView(dv);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.setMargins(dp(2), 0, dp(2), 0);
            track.addView(cell, clp);
        }
        box.addView(track, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Today's gift: +" + tokens + " hint tokens. Keep the streak alive!");
        sub.setTextSize(13f);
        sub.setTextColor(0xFFB0BEC5);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(10), 0, dp(4));
        box.addView(sub);

        final int t = tokens, st = streak;
        AlertDialog dlg = new AlertDialog.Builder(this)
            .setView(box)
            .setCancelable(false)
            .create();
        TextView claim = new TextView(this);
        claim.setText("CLAIM +" + t + " \u2728");
        claim.setTextSize(16f);
        claim.setTypeface(Typeface.DEFAULT_BOLD);
        claim.setTextColor(0xFFFFFFFF);
        claim.setGravity(Gravity.CENTER);
        claim.setBackground(pill(0xFF2E7D32, 22));
        claim.setPadding(dp(10), dp(12), dp(10), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.setMargins(dp(8), dp(10), dp(8), dp(6));
        box.addView(claim, clp);
        claim.setOnClickListener(v -> {
            gift.animate().rotationBy(360f).setDuration(420).withEndAction(() -> {
            dlg.dismiss();
            sp.edit().putString("daily_last", today()).putInt("daily_streak", st).apply();
            missions.onDailyClaimed(st);
            addTokens(t);
            Toast.makeText(this, "+" + t + " hint tokens!", Toast.LENGTH_SHORT).show();
            }).start();
        });
        dlg.show();
        animateDialogIn(box);
    }


    /** Pop-in entrance animation for modal dialogs (daily bonus etc.). */
    private void animateDialogIn(android.view.View content) {
        content.setAlpha(0f);
        content.setScaleX(0.8f);
        content.setScaleY(0.8f);
        content.post(() -> content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(280)
            .setInterpolator(new android.view.animation.OvershootInterpolator())
            .start());
    }

    // ---------- hints ----------
    private void useOrGetHint() {
        int tokens = sp.getInt("hint_tokens", 0);
        if (tokens > 0) {
            sp.edit().putInt("hint_tokens", tokens - 1).apply();
            refreshHintsLabel();
            game.showHint();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("No hint tokens")
                .setMessage("Watch a short ad for +2 hint tokens?")
                .setPositiveButton("Watch ad", (d, w) -> ads.showRewarded(() -> {
                    addTokens(2);
                    Toast.makeText(this, "+2 hint tokens!", Toast.LENGTH_SHORT).show();
                }))
                .setNegativeButton("Not now", null)
                .show();
        }
    }

    // ---------- missions ----------
    private void showMissions() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        addSection(box, "Daily missions", missions.dailyMissions());
        addSection(box, "Achievements", missions.achievements(game.getLevel()));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this).setTitle("Missions").setView(sv)
            .setNegativeButton("Close", null).show();
    }

    private void addSection(LinearLayout box, String title, List<MissionsManager.Item> items) {
        TextView h = new TextView(this);
        h.setText(title);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextSize(16f);
        h.setPadding(0, 12, 0, 6);
        box.addView(h);
        for (MissionsManager.Item i : items) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            String status = i.claimed ? " (claimed)" : " - " + i.progress + "/" + i.goal;
            t.setText(i.title + status + "  [+" + i.reward + " hint]");
            line.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            if (!i.claimed && i.done()) {
                Button c = new Button(this);
                c.setText("Claim");
                c.setOnClickListener(v -> {
                    long reward = missions.claim(i);
                    if (reward > 0) {
                        addTokens((int) reward);
                        Toast.makeText(this, "+" + reward + " hint tokens!", Toast.LENGTH_SHORT).show();
                    }
                    c.setEnabled(false); c.setText("Done");
                    t.setText(i.title + " (claimed)  [+" + i.reward + " hint]");
                });
                line.addView(c);
            }
            box.addView(line);
        }
    }

    private void applyAdsRemoved() {
        bannerBox.removeAllViews();
    }

    // ---------- TttView.Listener ----------
    @Override public void onGameComplete(int gamesDone, int outcome, int difficulty) {
        missions.onGame(outcome, difficulty);
        if (outcome == 1 && difficulty == 2) maybePromptReview();
        if (sp.getInt("games_done_flag", 0) == 0) {
            sp.edit().putInt("games_done_flag", 1).apply();
            if (!sp.getBoolean("ads_removed", false)) ads.attachBanner(bannerBox);
            handler.postDelayed(this::maybeDailyBonus, 1200);
        }
    }

    private void maybePromptReview() {
        if (sp.getBoolean("review_prompted", false)) return;
        sp.edit().putBoolean("review_prompted", true).apply();
        handler.postDelayed(() -> {
            ReviewManager manager = ReviewManagerFactory.create(this);
            manager.requestReviewFlow().addOnCompleteListener(task -> {
                if (task.isSuccessful())
                    manager.launchReviewFlow(this, task.getResult());
            });
        }, 1800);
    }

    @Override public void onHintRequested() { useOrGetHint(); }

    @Override public void onLevelUp(int level, String rank) { /* in-game celebration overlay handles this */ }

    @Override public void saveState() {
        sp.edit().putString(KEY, game.serialize()).apply();
    }

    // ---------- BillingManager.Listener ----------
    @Override public void onAdsRemovedOwned() {
        if (sp.getBoolean("ads_removed", false)) return;
        sp.edit().putBoolean("ads_removed", true).apply();
        runOnUiThread(() -> {
            ads.setAdsRemoved(true);
            applyAdsRemoved();
            Toast.makeText(this, "Ads removed - thank you!", Toast.LENGTH_LONG).show();
        });
    }



    @Override protected void onPause() { GameSounds.stopMusic(); GameSounds.onAppPause(); super.onPause(); saveState(); }

    @Override protected void onResume() {
        super.onResume();
        GameSounds.onAppResume();
        GameSounds.startMusic(this);
    }

    @Override protected void onDestroy() {
        GameSounds.release();
        super.onDestroy();
    }
}
