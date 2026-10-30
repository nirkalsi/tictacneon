package nir.com.tictacneon;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Captures a scripted gameplay sequence as PNG frames for a synthetic preview video.
 *  NOT a device capture: rendered locally via Robolectric native graphics.
 *  State-aware: reads board/turn/gameOver via reflection and plays legal moves only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "xxhdpi")
public class GameplayCaptureTest {

    private static Field f(String n) throws Exception {
        Field f = TttView.class.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    private static void place(TttView v, int cell) throws Exception {
        Method m = TttView.class.getDeclaredMethod("place", int.class);
        m.setAccessible(true);
        m.invoke(v, cell);
    }

    private static void aiMove(TttView v) throws Exception {
        Method m = TttView.class.getDeclaredMethod("doAiMove");
        m.setAccessible(true);
        m.invoke(v);
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void captureTttGame() throws Exception {
        new File("/tmp/frames-tt").mkdirs();
        TttView v = new TttView(RuntimeEnvironment.getApplication());
        int W = 1080, H = 2243;
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Field boardF = f("board"), turnF = f("turn"), overF = f("gameOver");
        int[] pref = {4, 0, 8, 2, 6, 1, 3, 5, 7};
        long t0 = System.currentTimeMillis();
        long lastAction = 0;
        int frame = 0;
        while (frame < 390) {                          // ~13s at 30fps
            long el = System.currentTimeMillis() - t0;
            boolean over = overF.getBoolean(v);
            if (el > 500 && !over && el - lastAction > 1100) {
                int[] board = (int[]) boardF.get(v);
                int turn = turnF.getInt(v);
                if (turn == 1) {                     // X: first empty preferred cell
                    for (int cell : pref) if (board[cell] == 0) { place(v, cell); break; }
                    lastAction = el;
                } else {                             // O: let the thinking pause show, then AI moves
                    aiMove(v);
                    lastAction = el;
                }
            }
            v.draw(c);
            FileOutputStream fos = new FileOutputStream(String.format("/tmp/frames-tt/f%04d.png", frame++));
            bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
            fos.close();
            Thread.sleep(33);
        }
    }
}
