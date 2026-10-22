package nir.com.tictacneon;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.*;

/** Daily missions + lifetime achievements. Rewards are hint tokens. */
public class MissionsManager {
    public static class Item {
        public String id, title;
        public long goal, reward, progress;
        public boolean claimed;
        public boolean done() { return progress >= goal; }
    }

    private final SharedPreferences sp;
    private final Runnable onChanged;

    public MissionsManager(Context c, Runnable onChanged) {
        sp = c.getSharedPreferences("tictacneon", Context.MODE_PRIVATE);
        this.onChanged = onChanged;
        rollDailyIfNeeded();
    }

    private String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private void rollDailyIfNeeded() {
        if (!today().equals(sp.getString("missions_day", ""))) {
            sp.edit()
                .putString("missions_day", today())
                .putLong("dm1", 0).putLong("dm2", 0).putLong("dm3", 0)
                .putBoolean("dm1c", false).putBoolean("dm2c", false).putBoolean("dm3c", false)
                .apply();
        }
    }

    /** outcome: 1=player win, 2=AI/other win, 3=draw. difficulty 0-2. */
    public void onGame(int outcome, int difficulty) {
        rollDailyIfNeeded();
        SharedPreferences.Editor e = sp.edit();
        e.putLong("dm3", sp.getLong("dm3", 0) + 1);
        if (outcome == 1) {
            e.putLong("dm1", sp.getLong("dm1", 0) + 1);
            e.putLong("wins_total", sp.getLong("wins_total", 0) + 1);
            int streak = sp.getInt("win_streak", 0) + 1;
            e.putInt("win_streak", streak);
            if (streak >= 3) e.putBoolean("ach4_done", true);
            if (difficulty == 2) {
                e.putLong("dm2", sp.getLong("dm2", 0) + 1);
                long hw = sp.getLong("hard_wins", 0) + 1;
                e.putLong("hard_wins", hw);
            }
        } else if (outcome == 2) {
            e.putInt("win_streak", 0);
        }
        e.apply();
        if (onChanged != null) onChanged.run();
    }

    public void onDailyClaimed(int streak) {
        if (streak >= 3) sp.edit().putBoolean("ach6_done", true).apply();
    }

    public List<Item> dailyMissions() {
        rollDailyIfNeeded();
        List<Item> out = new ArrayList<>();
        out.add(mk("dm1", "Win 3 games today", 3, 2, sp.getLong("dm1", 0), sp.getBoolean("dm1c", false)));
        out.add(mk("dm2", "Beat HARD AI today", 1, 3, sp.getLong("dm2", 0), sp.getBoolean("dm2c", false)));
        out.add(mk("dm3", "Play 10 games today", 10, 1, sp.getLong("dm3", 0), sp.getBoolean("dm3c", false)));
        return out;
    }

    public List<Item> achievements(int level) {
        long wins = sp.getLong("wins_total", 0);
        List<Item> out = new ArrayList<>();
        out.add(mk("ach1", "First win", 1, 1, wins, sp.getBoolean("ach1c", false)));
        out.add(mk("ach2", "10 wins", 10, 2, wins, sp.getBoolean("ach2c", false)));
        out.add(mk("ach3", "50 wins", 50, 5, wins, sp.getBoolean("ach3c", false)));
        out.add(mk("ach4", "Win streak: 3 in a row", 1, 3,
            sp.getBoolean("ach4_done", false) ? 1 : 0, sp.getBoolean("ach4c", false)));
        out.add(mk("ach5", "Reach level 5", 5, 3, level, sp.getBoolean("ach5c", false)));
        out.add(mk("ach6", "3-day daily streak", 1, 2,
            sp.getBoolean("ach6_done", false) ? 1 : 0, sp.getBoolean("ach6c", false)));
        return out;
    }

    private Item mk(String id, String title, long goal, long reward, long progress, boolean claimed) {
        Item i = new Item(); i.id = id; i.title = title; i.goal = goal; i.reward = reward;
        i.progress = Math.min(progress, goal); i.claimed = claimed; return i;
    }

    public long claim(Item i) {
        if (i.claimed || !i.done()) return 0;
        sp.edit().putBoolean(i.id + "c", true).apply();
        if (onChanged != null) onChanged.run();
        return i.reward;
    }
}
