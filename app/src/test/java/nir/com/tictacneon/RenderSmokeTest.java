package nir.com.tictacneon;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

/** Pixel-render smoke: game view + full screen + reward/daily/missions/shop overlays. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class RenderSmokeTest {

    private void renderView(View v, int w, int h, String path) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        FileOutputStream fos = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        assertTrue(path + " too small", new File(path).length() > 5000);
    }

    private MainActivity activity() {
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().get();
    }

    private void callPrivate(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    private static void resetAnimationState(android.view.View v) {
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) resetAnimationState(g.getChildAt(i));
        }
    }

    private void renderLatestDialog(String tag) throws Exception {
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("no dialog for " + tag, d);
        android.view.View content = d.getWindow().getDecorView();
        resetAnimationState(content); // dialogs enter with alpha/scale animation; render post-animation state
        content.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(1700, View.MeasureSpec.AT_MOST));
        renderView(content, Math.max(content.getMeasuredWidth(), 400),
                   Math.max(content.getMeasuredHeight(), 200) + 80, "/tmp/renders/" + tag + ".png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView1080() throws Exception {
        renderView(new TttView(RuntimeEnvironment.getApplication()), 1080, 2243, "/tmp/renders/tictacneon-game-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameView720() throws Exception {
        renderView(new TttView(RuntimeEnvironment.getApplication()), 720, 1480, "/tmp/renders/tictacneon-game-720x1480.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderFullScreen1080() throws Exception {
        MainActivity a = activity();
        renderView(a.findViewById(android.R.id.content), 1080, 2243, "/tmp/renders/tictacneon-full-1080x2243.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderDailyBonus() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "maybeDailyBonus");
        renderLatestDialog("tictacneon-daily");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderMissions() throws Exception {
        MainActivity a = activity();
        callPrivate(a, "showMissions");
        renderLatestDialog("tictacneon-missions");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderHintRewardPrompt() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("tictacneon", 0)
            .edit().putInt("hint_tokens", 0).apply();
        MainActivity a = activity();
        callPrivate(a, "useOrGetHint");
        renderLatestDialog("tictacneon-hintprompt");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void rankUpOverlay() throws Exception {
        TttView v = new TttView(RuntimeEnvironment.getApplication());
        java.lang.reflect.Field fp = TttView.class.getDeclaredField("levelUpPending"); fp.setAccessible(true); fp.setInt(v, 5);
        java.lang.reflect.Field fs = TttView.class.getDeclaredField("levelUpShowing"); fs.setAccessible(true); fs.setBoolean(v, true);
        java.lang.reflect.Field fa = TttView.class.getDeclaredField("levelUpAt"); fa.setAccessible(true); fa.setLong(v, System.currentTimeMillis() - 1000);
        renderView(v, 1080, 2243, "/tmp/renders/tictacneon-rankup.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void winState() throws Exception {
        TttView v = new TttView(RuntimeEnvironment.getApplication());
        java.lang.reflect.Field fb = TttView.class.getDeclaredField("board"); fb.setAccessible(true);
        int[] b = (int[]) fb.get(v); b[0] = 1; b[1] = 1; b[2] = 1; b[4] = 2; b[5] = 2;
        java.lang.reflect.Field fg = TttView.class.getDeclaredField("gameOver"); fg.setAccessible(true); fg.setBoolean(v, true);
        java.lang.reflect.Field fw = TttView.class.getDeclaredField("winner"); fw.setAccessible(true); fw.setInt(v, 1);
        java.lang.reflect.Field fl = TttView.class.getDeclaredField("winLine"); fl.setAccessible(true); fl.set(v, new int[]{0, 1, 2});
        long t0 = System.currentTimeMillis() - 2000;
        java.lang.reflect.Field fa = TttView.class.getDeclaredField("winAnimStart"); fa.setAccessible(true); fa.setLong(v, t0);
        java.lang.reflect.Field fo = TttView.class.getDeclaredField("gameOverAt"); fo.setAccessible(true); fo.setLong(v, t0);
        renderView(v, 1080, 2243, "/tmp/renders/tictacneon-win.png");
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void renderGameViewSmall() throws Exception {
        renderView(new TttView(RuntimeEnvironment.getApplication()), 960, 1600, "/tmp/renders/tictacneon-game-960x1600.png");
    }

}
