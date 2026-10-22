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
        gameOver = false; winner = EMPTY; winLine = null; hintCell = -1;
        turn = X;
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
        hintCell = -1;
        int[] w = checkWin(board);
        if (w != null) {
            gameOver = true; winner = turn; winLine = w; winAnimStart = System.currentTimeMillis();
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
        p.setStrokeWidth(5);
        p.setColor(0xFF3D5A99);
        p.setShadowLayer(10, 0, 0, 0xFF3D5A99);
        for (int i = 1; i < 3; i++) {
            cv.drawLine(bx + i * cell, by + 6, bx + i * cell, by + boardSize - 6, p);
            cv.drawLine(bx + 6, by + i * cell, bx + boardSize - 6, by + i * cell, p);
        }
        p.clearShadowLayer();
        p.setStyle(Paint.Style.FILL);

        // marks
        float pad = cell * 0.22f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 9; i++) {
            if (board[i] == EMPTY) continue;
            float cx = bx + (i % 3) * cell, cy = by + (i / 3) * cell;
            if (board[i] == X) {
                p.setColor(0xFF4DD0E1);
                p.setShadowLayer(14, 0, 0, 0xFF4DD0E1);
                cv.drawLine(cx + pad, cy + pad, cx + cell - pad, cy + cell - pad, p);
                cv.drawLine(cx + cell - pad, cy + pad, cx + pad, cy + cell - pad, p);
            } else {
                p.setColor(0xFFFF6E9C);
                p.setShadowLayer(14, 0, 0, 0xFFFF6E9C);
                cv.drawCircle(cx + cell / 2, cy + cell / 2, cell / 2 - pad, p);
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
        float ctrlY = by + boardSize + getHeight() * 0.035f;
        p.setTextSize(w * 0.05f);
        p.setFakeBoldText(true);
        if (gameOver) {
            if (winner == X) { p.setColor(0xFF4DD0E1); cv.drawText(vsAI ? "YOU WIN!" : "X WINS!", w / 2, ctrlY, p); }
            else if (winner == O) { p.setColor(0xFFFF6E9C); cv.drawText(vsAI ? "AI WINS!" : "O WINS!", w / 2, ctrlY, p); }
            else { p.setColor(0xFFB0BEC5); cv.drawText("DRAW!", w / 2, ctrlY, p); }
        } else {
            p.setColor(0xFF8FA3C8);
            String t = vsAI ? (turn == X ? "Your turn (X)" : "AI thinking...") : (turn == X ? "X to move" : "O to move");
            cv.drawText(t, w / 2, ctrlY, p);
        }
        p.setFakeBoldText(false);

        // buttons row
        float btnY = ctrlY + getHeight() * 0.03f;
        float btnH = getHeight() * 0.06f;
        p.setTextSize(w * 0.04f);
        // mode button
        rf.set(w * 0.05f, btnY, w * 0.36f, btnY + btnH);
        drawBtn(cv, rf, vsAI ? "VS AI" : "2 PLAYERS");
        // difficulty button (only vs AI)
        if (vsAI) {
            rf.set(w * 0.40f, btnY, w * 0.62f, btnY + btnH);
            drawBtn(cv, rf, DIFF[difficulty]);
        }
        // hint button (vs AI only)
        if (vsAI) {
            rf.set(w * 0.66f, btnY, w * 0.82f, btnY + btnH);
            drawBtn(cv, rf, "HINT");
        }
        // new game button
        rf.set(vsAI ? w * 0.86f : w * 0.40f, btnY, w * 0.97f, btnY + btnH);
        if (!vsAI) rf.set(w * 0.40f, btnY, w * 0.62f, btnY + btnH);
        drawBtn(cv, rf, "NEW");
    }

    private void drawBtn(Canvas cv, RectF r, String label) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1B3A66);
        cv.drawRoundRect(r, 12, 12, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0xFF4DD0E1);
        cv.drawRoundRect(r, 12, 12, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        cv.drawText(label, r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        float w = getWidth();
        float boardSize = w * 0.86f;
        float bx = (w - boardSize) / 2;
        float by = getHeight() * 0.18f;
        float cell = boardSize / 3;
        float ctrlY = by + boardSize + getHeight() * 0.035f;
        float btnY = ctrlY + getHeight() * 0.03f;
        float btnH = getHeight() * 0.06f;
        float x = e.getX(), y = e.getY();

        if (y >= btnY && y <= btnY + btnH) {
            if (x >= w * 0.05f && x <= w * 0.36f) {
                vsAI = !vsAI; reset(); persist(); return true;
            }
            if (vsAI && x >= w * 0.40f && x <= w * 0.62f) {
                difficulty = (difficulty + 1) % 3; persist(); invalidate(); return true;
            }
            if (vsAI && x >= w * 0.66f && x <= w * 0.82f) {
                if (!gameOver && turn == X && listener != null) listener.onHintRequested();
                return true;
            }
            float nl = vsAI ? w * 0.86f : w * 0.40f, nr = vsAI ? w * 0.97f : w * 0.62f;
            if (x >= nl && x <= nr) { reset(); return true; }
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
