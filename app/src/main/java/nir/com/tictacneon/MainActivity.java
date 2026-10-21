package nir.com.tictacneon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;

public class MainActivity extends Activity implements TttView.Listener {
    private static final String PREFS = "tictacneon", KEY = "state";
    private TttView game;
    private AdsManager ads;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        game = new TttView(this);
        game.setListener(this);
        root.addView(game, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout bannerBox = new LinearLayout(this);
        root.addView(bannerBox, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String s = sp.getString(KEY, null);
        if (s != null) game.restore(s);

        ads = new AdsManager(this);
        ads.initWithConsent(() -> ads.attachBanner(bannerBox));
    }

    @Override public void onGameComplete(int gamesDone) {
        ads.maybeShowInterstitial(gamesDone);
    }

    @Override public void onHintRequested() {
        new AlertDialog.Builder(this)
            .setTitle("Hint")
            .setMessage("Watch a short ad to reveal the best move?")
            .setPositiveButton("Watch ad", (d, w) -> ads.showRewarded(() -> game.showHint()))
            .setNegativeButton("Not now", null)
            .show();
    }

    @Override public void saveState() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, game.serialize()).apply();
    }

    @Override protected void onPause() { super.onPause(); saveState(); }
}
