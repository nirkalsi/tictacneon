package nir.com.tictacneon;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Tic Tac Neon - neon tic-tac-toe vs AI (3 levels) or a friend. */
public class TttView extends View {
    public interface Listener {
        void onGameComplete(int gamesDone, int outcome, int difficulty); // 1=X win, 2=O win, 3=draw
        void onHintRequested();
        void onLevelUp(int level, String rank);
        void saveState();
    }

    private static final int EMPTY = 0, X = 1, O = 2;
    private static final String[] DIFF = {"EASY", "MEDIUM", "HARD"};

    private final Random rng = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private Listener listener;

    private int[] board = new int[9];
    private boolean vsAI = true;
    private int difficulty = 2;           // 0 easy, 1 medium, 2 hard
    private int turn = X;
    private boolean gameOver = false;
    private int winner = EMPTY;           // EMPTY+gameOver = draw
    private int[] winLine = null;         // 3 winning cells
    private int scoreX = 0, scoreO = 0, draws = 0;
    private long gamesDone = 0;
    private long xp = 0;
    private int level = 1;
    private long levelFlashUntil = 0;
    private long aiMoveAt = 0;
    private long winAnimStart = 0;
    private int hintCell = -1;
    private long hintUntil = 0;
    private final long[] cellT = new long[9];       // placement time per cell (0 = empty)
    private long aiThinkingSince = 0;
    // win sparks
    private final float[] sx = new float[22], sy = new float[22], svx = new float[22], svy = new float[22];
    private boolean sparksOn = false;
    private long sparksAt = 0;

    public TttView(Context c) { super(c); }

    public void setListener(Listener l) { listener = l; }

    public String serialize() { return scoreX + ";" + scoreO + ";" + draws + ";" + gamesDone + ";" + difficulty + ";" + (vsAI ? 1 : 0) + ";" + xp + ";" + level; }
    public void restore(String s) {
        try {
            String[] a = s.split(";");
            scoreX = Integer.parseInt(a[0]); scoreO = Integer.parseInt(a[1]);
            draws = Integer.parseInt(a[2]); gamesDone = Long.parseLong(a[3]);
            difficulty = Math.min(2, Math.max(0, Integer.parseInt(a[4])));
            vsAI = a[5].equals("1");
            if (a.length > 6) xp = Long.parseLong(a[6]);
            if (a.length > 7) level = Math.max(1, Integer.parseInt(a[7]));
        } catch (Exception ignored) {}
    }

    private void persist() { if (listener != null) listener.saveState(); }

    private void grantXp(long gain) {
        xp += gain;
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();
            level++;
            levelFlashUntil = System.currentTimeMillis() + 2200;
            if (listener != null) listener.onLevelUp(level, rankName(level));
        }
    }

    public int getLevel() { return level; }
    public long getGamesDone() { return gamesDone; }
    private long xpNeeded() { return level * 400L; }
    public static String rankName(int level) {
        if (level >= 12) return "NEON LEGEND";
        if (level >= 8) return "NEON MASTER";
        if (level >= 5) return "NEON PRO";
        if (level >= 3) return "NEON PLAYER";
        return "ROOKIE";
    }

    public void showHint() {
        if (gameOver || (vsAI && turn != X)) return;
        hintCell = bestMove(turn);
        hintUntil = System.currentTimeMillis() + 2500;
        invalidate();
    }

    private void reset() {
        board = new int[9];
        for (int i = 0; i < 9; i++) cellT[i] = 0;
        gameOver = false; winner = EMPTY; winLine = null; hintCell = -1;
        turn = X;
        sparksOn = false;
        invalidate();
    }

    private static final int[][] WINS = {
        {0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}
    };

    private int[] checkWin(int[] b) {
        for (int[] w : WINS) if (b[w[0]] != EMPTY && b[w[0]] == b[w[1]] && b[w[1]] == b[w[2]]) return w;
        return null;
    }

    private boolean full(int[] b) { for (int v : b) if (v == EMPTY) return false; return true; }

    private int minimax(int[] b, int player, int depth) {
        int[] w = checkWin(b);
        if (w != null) return b[w[0]] == O ? 10 - depth : depth - 10;
        if (full(b)) return 0;
        int best = player == O ? -100 : 100;
        for (int i = 0; i < 9; i++) if (b[i] == EMPTY) {
            b[i] = player;
            int s = minimax(b, player == O ? X : O, depth + 1);
            b[i] = EMPTY;
            best = player == O ? Math.max(best, s) : Math.min(best, s);
        }
        return best;
    }

    private int bestMove(int player) {
        int best = -1, bestScore = player == O ? -100 : 100;
        for (int i = 0; i < 9; i++) if (board[i] == EMPTY) {
            board[i] = player;
            int s = minimax(board, player == O ? X : O, 0);
            board[i] = EMPTY;
            if (player == O ? s > bestScore : s < bestScore) { bestScore = s; best = i; }
        }
        return best;
    }

    private int aiMove() {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (board[i] == EMPTY) free.add(i);
        if (free.isEmpty()) return -1;
        if (difficulty == 0) return free.get(rng.nextInt(free.size()));
        if (difficulty == 1 && rng.nextFloat() < 0.45f) return free.get(rng.nextInt(free.size()));
        int m = bestMove(O);
        return m >= 0 ? m : free.get(rng.nextInt(free.size()));
    }

    private void place(int cell) {
        if (gameOver || board[cell] != EMPTY) return;
        board[cell] = turn;
        cellT[cell] = System.currentTimeMillis();
        GameSounds.play(GameSounds.CLICK);
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        hintCell = -1;
        int[] w = checkWin(board);
        if (w != null) {
            gameOver = true; winner = turn; winLine = w; winAnimStart = System.currentTimeMillis();
            startSparks();
            GameSounds.play(GameSounds.WIN);
            if (turn == X) scoreX++; else scoreO++;
            gamesDone++;
            long gain;
            if (!vsAI) gain = 20;
            else if (turn == X) gain = difficulty == 2 ? 100 : (difficulty == 1 ? 50 : 30);
            else gain = 5;
            grantXp(gain);
            persist();
            if (listener != null) listener.onGameComplete((int) gamesDone, turn == X ? 1 : 2, difficulty);
        } else if (full(board)) {
            gameOver = true; winner = EMPTY; draws++; gamesDone++;
            grantXp(vsAI ? 15 : 10);
            persist();
            if (listener != null) listener.onGameComplete((int) gamesDone, 3, difficulty);
        } else {
            turn = (turn == X) ? O : X;
            if (vsAI && turn == O) {
                aiMoveAt = System.currentTimeMillis() + 450;
                aiThinkingSince = System.currentTimeMillis();
                postDelayed(this::doAiMove, 450);
            }
        }
        invalidate();
    }

    private void doAiMove() {
        if (gameOver || !vsAI || turn != O) return;
        int m = aiMove();
        if (m >= 0) place(m);
    }

    private void startSparks() {
        sparksOn = true;
        sparksAt = System.currentTimeMillis();
        for (int i = 0; i < 22; i++) {
            sx[i] = 0.5f; sy[i] = 0.42f;
            svx[i] = (rng.nextFloat() - 0.5f) * 0.0025f;
            svy[i] = -(0.0007f + rng.nextFloat() * 0.0024f);
        }
    }

    private void drawSparks(Canvas cv, float w, float h, long now) {
        if (!sparksOn) return;
        float t = (now - sparksAt) / 1000f;
        if (t > 1.8f) { sparksOn = false; return; }
        int[] cols = {0xFF4DD0E1, 0xFFFF6E9C, 0xFFFFD700, 0xFF7CFC00};
        for (int i = 0; i < 22; i++) {
            float x = (sx[i] + svx[i] * t * 60) * w;
            float y = (sy[i] + svy[i] * t * 60 + 0.0011f * t * t * 3600) * h;
            if (y > h) continue;
            p.setColor(cols[i % 4]);
            p.setShadowLayer(8, 0, 0, cols[i % 4]);
            cv.drawCircle(x, y, w * 0.010f, p);
        }
        p.clearShadowLayer();
        postInvalidateDelayed(32);
    }

    @Override protected void onDraw(Canvas cv) {
        long now = System.currentTimeMillis();
        p.setShader(new LinearGradient(0, 0, 0, getHeight(), 0xFF12264A, 0xFF060D1F, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, getWidth(), getHeight(), p);
        p.setShader(null);

        float w = getWidth();
        p.setTextAlign(Paint.Align.CENTER);

        // title
        p.setTextSize(w * 0.08f);
        p.setFakeBoldText(true);
        p.setColor(0xFF4DD0E1);
        p.setShadowLayer(16, 0, 0, 0xFF4DD0E1);
        cv.drawText("TIC TAC NEON", w / 2, getHeight() * 0.08f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);

        // scoreboard
        p.setTextSize(w * 0.045f);
        p.setColor(0xFF4DD0E1);
        String left = vsAI ? "YOU " + scoreX : "X  " + scoreX;
        String right = vsAI ? "AI " + scoreO : "O  " + scoreO;
        cv.drawText(left, w * 0.2f, getHeight() * 0.13f, p);
        p.setColor(0xFF8FA3C8);
        cv.drawText("Draws " + draws, w * 0.5f, getHeight() * 0.13f, p);
        p.setColor(0xFFFF6E9C);
        cv.drawText(right, w * 0.8f, getHeight() * 0.13f, p);

        // rank + xp bar
        p.setTextSize(w * 0.034f);
        p.setColor(0xFF8FA3C8);
        cv.drawText("LV " + level + "  " + rankName(level), w * 0.5f, getHeight() * 0.163f, p);
        float barL = w * 0.24f, barR = w * 0.76f, barY = getHeight() * 0.168f, barH = w * 0.016f;
        rf.set(barL, barY, barR, barY + barH);
        p.setColor(0xFF1E3A6E); cv.drawRoundRect(rf, 8, 8, p);
        float frac = Math.min(1f, xp / (float) xpNeeded());
        rf.set(barL, barY, barL + (barR - barL) * frac, barY + barH);
        p.setColor(0xFF4DD0E1); cv.drawRoundRect(rf, 8, 8, p);
        if (now < levelFlashUntil) {
            p.setTextSize(w * 0.055f);
            p.setColor(0xFF4DD0E1);
            p.setFakeBoldText(true);
            cv.drawText("RANK UP!", w / 2, getHeight() * 0.05f + getHeight() * 0.16f, p);
            p.setFakeBoldText(false);
        }

        // board
        float boardSize = w * 0.86f;
        float bx = (w - boardSize) / 2;
        float by = getHeight() * 0.18f;
        float cell = boardSize / 3;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        p.setStrokeCap(Paint.Cap.ROUND);
        float gridPulse = 0.75f + 0.25f * (float) Math.sin(now / 900.0);
        p.setColor(0xFF3D5A99);
        p.setShadowLayer(10 + 8 * gridPulse, 0, 0, 0xFF3D5A99);
        for (int i = 1; i < 3; i++) {
            cv.drawLine(bx + i * cell, by + 10, bx + i * cell, by + boardSize - 10, p);
            cv.drawLine(bx + 10, by + i * cell, bx + boardSize - 10, by + i * cell, p);
        }
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
        postInvalidateDelayed(120);

        // marks
        float pad = cell * 0.22f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 9; i++) {
            if (board[i] == EMPTY) continue;
            float cx = bx + (i % 3) * cell, cy = by + (i / 3) * cell;
            float t = Math.min(1f, (now - cellT[i]) / 260f);
            if (t < 1f) postInvalidateDelayed(28);
            if (board[i] == X) {
                p.setColor(0xFF4DD0E1);
                p.setShadowLayer(14, 0, 0, 0xFF4DD0E1);
                float t1 = Math.min(1f, t * 2f), t2 = Math.max(0f, Math.min(1f, t * 2f - 1f));
                cv.drawLine(cx + pad, cy + pad, cx + pad + (cell - 2 * pad) * t1, cy + pad + (cell - 2 * pad) * t1, p);
                if (t2 > 0)
                    cv.drawLine(cx + cell - pad, cy + pad, cx + cell - pad - (cell - 2 * pad) * t2, cy + pad + (cell - 2 * pad) * t2, p);
            } else {
                p.setColor(0xFFFF6E9C);
                p.setShadowLayer(14, 0, 0, 0xFFFF6E9C);
                cv.drawArc(new RectF(cx + pad, cy + pad, cx + cell - pad, cy + cell - pad), -90, 360 * t, false, p);
            }
            p.clearShadowLayer();
        }

        // hint highlight
        if (hintCell >= 0 && now < hintUntil && board[hintCell] == EMPTY) {
            float cx = bx + (hintCell % 3) * cell, cy = by + (hintCell / 3) * cell;
            p.setColor(0x667CFC00);
            p.setStyle(Paint.Style.FILL);
            rf.set(cx + 6, cy + 6, cx + cell - 6, cy + cell - 6);
            cv.drawRoundRect(rf, 12, 12, p);
            p.setStyle(Paint.Style.STROKE);
            postInvalidateDelayed(200);
        }

        // win line animation
        if (winLine != null) {
            float t = Math.min(1f, (now - winAnimStart) / 500f);
            int a = winLine[0], b2 = winLine[2];
            float x1 = bx + (a % 3) * cell + cell / 2, y1 = by + (a / 3) * cell + cell / 2;
            float x2 = bx + (b2 % 3) * cell + cell / 2, y2 = by + (b2 / 3) * cell + cell / 2;
            p.setColor(0xFFFFD700);
            p.setStrokeWidth(12);
            p.setShadowLayer(18, 0, 0, 0xFFFFD700);
            cv.drawLine(x1, y1, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, p);
            p.clearShadowLayer();
            if (t < 1f) postInvalidateDelayed(30);
        }

        // status / result
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);
        float ctrlY = by + boardSize + getHeight() * 0.035f;
        p.setTextSize(w * 0.05f);
        p.setFakeBoldText(true);
        if (gameOver) {
            float wt = Math.min(1f, (now - winAnimStart) / 420f);
            float scale = wt < 0.5f ? 0.6f + 1.1f * wt : 1f;
            p.setTextSize(w * 0.055f * scale);
            int col = winner == X ? 0xFF4DD0E1 : (winner == O ? 0xFFFF6E9C : 0xFFB0BEC5);
            String msg = winner == X ? (vsAI ? "YOU WIN!" : "X WINS!") : (winner == O ? (vsAI ? "AI WINS!" : "O WINS!") : "DRAW!");
            p.setColor(col);
            p.setShadowLayer(18, 0, 0, col);
            cv.drawText(msg, w / 2, ctrlY, p);
            p.clearShadowLayer();
            if (wt < 1f) postInvalidateDelayed(30);
            p.setTextSize(w * 0.034f);
            p.setColor(0xFF8FA3C8);
            cv.drawText("Tap the board for a rematch", w / 2, ctrlY + getHeight() * 0.024f, p);
        } else {
            p.setColor(0xFF8FA3C8);
            if (vsAI && turn == O) {
                int dots = (int) ((now - aiThinkingSince) / 350 % 4);
                StringBuilder sb = new StringBuilder("AI thinking");
                for (int d = 0; d < dots; d++) sb.append('.');
                cv.drawText(sb.toString(), w / 2, ctrlY, p);
                postInvalidateDelayed(120);
            } else {
                String t = vsAI ? "Your turn (X)" : (turn == X ? "X to move" : "O to move");
                cv.drawText(t, w / 2, ctrlY, p);
            }
        }
        p.setFakeBoldText(false);
        drawSparks(cv, w, getHeight(), now);

        // buttons: row of three pills + full-width NEW GAME
        float btnY = ctrlY + getHeight() * 0.038f;
        float btnH = getHeight() * 0.052f;
        drawNeonPill(cv, w * 0.05f, btnY, w * 0.30f, btnH, vsAI ? "VS AI" : "2 PLAYERS", 0xFF26C6DA, 0xFF00838F, true, false);
        if (vsAI) {
            drawNeonPill(cv, w * 0.37f, btnY, w * 0.26f, btnH, DIFF[difficulty], 0xFFAB47BC, 0xFF6A1B9A, true, false);
            drawNeonPill(cv, w * 0.65f, btnY, w * 0.30f, btnH, "\u2728 HINT", 0xFF42A5F5, 0xFF1565C0, !gameOver && turn == X, false);
        } else {
            drawNeonPill(cv, w * 0.37f, btnY, w * 0.58f, btnH, "PASS & PLAY", 0xFFAB47BC, 0xFF6A1B9A, false, false);
        }
        float newY = btnY + btnH + getHeight() * 0.012f;
        float pulse = gameOver ? (float) (0.5 + 0.5 * Math.sin(now / 450.0)) : 0f;
        if (pulse > 0) postInvalidateDelayed(60);
        drawNeonPill(cv, w * 0.16f, newY, w * 0.68f, btnH, "NEW GAME  \u21BB", 0xFF66BB6A, 0xFF2E7D32, true, pulse > 0.6f);
    }

    private void drawNeonPill(Canvas cv, float x, float y, float w, float h, String label,
                              int cTop, int cBottom, boolean enabled, boolean glow) {
        rf.set(x, y, x + w, y + h);
        if (glow && enabled) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h * 0.07f); p.setColor(0x554DD0E1);
            RectF g = new RectF(rf.left - 5, rf.top - 5, rf.right + 5, rf.bottom + 5);
            cv.drawRoundRect(g, h * 0.5f + 5, h * 0.5f + 5, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (enabled) {
            p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, cTop, cBottom, Shader.TileMode.CLAMP));
        } else {
            p.setShader(null); p.setColor(0xFF1B3A66);
        }
        cv.drawRoundRect(rf, h * 0.5f, h * 0.5f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
        p.setColor(enabled ? 0x884DD0E1 : 0x443D5A99);
        cv.drawRoundRect(rf, h * 0.5f, h * 0.5f, p);
        p.setStyle(Paint.Style.FILL);
        if (enabled) {
            p.setColor(0x33FFFFFF);
            rf.set(x + 6, y + 3, x + w - 6, y + h * 0.42f);
            cv.drawRoundRect(rf, h * 0.2f, h * 0.2f, p);
        }
        p.setColor(enabled ? 0xFFFFFFFF : 0xFF8FA3C8);
        p.setTextSize(h * 0.36f); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
        p.setShadowLayer(4, 0, 2, 0x66000000);
        cv.drawText(label, x + w / 2f, y + h * 0.64f, p);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        float w = getWidth();
        float boardSize = w * 0.86f;
        float bx = (w - boardSize) / 2;
        float by = getHeight() * 0.18f;
        float cell = boardSize / 3;
        float ctrlY = by + boardSize + getHeight() * 0.035f;
        float btnY = ctrlY + getHeight() * 0.038f;
        float btnH = getHeight() * 0.06f;
        float x = e.getX(), y = e.getY();

        float newY = btnY + btnH + getHeight() * 0.012f;
        if (y >= newY && y <= newY + btnH && x >= w * 0.16f && x <= w * 0.84f) { reset(); return true; }
        if (y >= btnY && y <= btnY + btnH) {
            if (x >= w * 0.05f && x <= w * 0.35f) {
                vsAI = !vsAI; reset(); persist(); return true;
            }
            if (vsAI && x >= w * 0.37f && x <= w * 0.63f) {
                difficulty = (difficulty + 1) % 3; persist(); invalidate(); return true;
            }
            if (vsAI && x >= w * 0.65f && x <= w * 0.95f) {
                if (!gameOver && turn == X && listener != null) listener.onHintRequested();
                return true;
            }
            return true;
        }

        if (x >= bx && x < bx + boardSize && y >= by && y < by + boardSize) {
            if (gameOver) { reset(); return true; }
            if (vsAI && turn != X) return true;
            int c = (int) ((x - bx) / cell), r = (int) ((y - by) / cell);
            place(r * 3 + c);
        }
        return true;
    }
}
