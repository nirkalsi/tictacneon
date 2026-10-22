package nir.com.tictacneon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private Button removeAdsBtn, hintsBtn;
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

        String s = sp.getString(KEY, null);
        if (s != null) game.restore(s);
        if (!sp.contains("hint_tokens")) sp.edit().putInt("hint_tokens", 3).apply();

        missions = new MissionsManager(this, null);

        ads = new AdsManager(this);
        boolean removed = sp.getBoolean("ads_removed", false);
        ads.setAdsRemoved(removed);
        if (removed) applyAdsRemoved();
        ads.initWithConsent(() -> { if (!removed) ads.attachBanner(bannerBox); });

        billing = new BillingManager(this, this);

        if (sp.getString("first_run", null) == null)
            sp.edit().putString("first_run", today()).apply();

        handler.postDelayed(this::maybeDailyBonus, 900);
        refreshHintsLabel();
    }

    private View buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(4, 6, 4, 0);
        addRowButton(row, "MISSIONS", v -> showMissions());
        hintsBtn = addRowButton(row, "HINTS", v -> useOrGetHint());
        addRowButton(row, "MORE GAMES", v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DEV_PAGE_URL))));
        removeAdsBtn = addRowButton(row, "NO ADS", v -> buyRemoveAds());
        return row;
    }

    private Button addRowButton(LinearLayout row, String label, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(0xFF1E3A6E);
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(4, 0, 4, 0);
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
        final int t = tokens, st = streak;
        new AlertDialog.Builder(this)
            .setTitle("Daily bonus - day " + st)
            .setMessage("Your day " + st + " gift: +" + t + " hint tokens. Come back tomorrow to grow your streak!")
            .setPositiveButton("Claim +" + t, (d, w) -> {
                sp.edit().putString("daily_last", today()).putInt("daily_streak", st).apply();
                missions.onDailyClaimed(st);
                addTokens(t);
                Toast.makeText(this, "+" + t + " hint tokens!", Toast.LENGTH_SHORT).show();
            })
            .setCancelable(false)
            .show();
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

    // ---------- remove ads ----------
    private void buyRemoveAds() {
        if (billing.getDetails(BillingManager.REMOVE_ADS) != null) {
            billing.buy(this, BillingManager.REMOVE_ADS);
        } else {
            Toast.makeText(this, "Not available yet - try again soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyAdsRemoved() {
        bannerBox.removeAllViews();
        if (removeAdsBtn != null) removeAdsBtn.setVisibility(View.GONE);
    }

    // ---------- TttView.Listener ----------
    @Override public void onGameComplete(int gamesDone, int outcome, int difficulty) {
        missions.onGame(outcome, difficulty);
        if (outcome == 1 && difficulty == 2) maybePromptReview();
        ads.maybeShowInterstitial(gamesDone);
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

    @Override public void onLevelUp(int level, String rank) {
        Toast.makeText(this, "Rank up: " + rank + " (level " + level + ")", Toast.LENGTH_SHORT).show();
    }

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

    @Override public void onCoinsPurchased(long coins) { }

    @Override public void onProductsReady() { }

    @Override protected void onPause() { super.onPause(); saveState(); }
}
