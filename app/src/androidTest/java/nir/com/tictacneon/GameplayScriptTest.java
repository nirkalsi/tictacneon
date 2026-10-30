package nir.com.tictacneon;

import android.graphics.Rect;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Scripted real gameplay for device acceptance video: taps derived from the game view's own layout math. */
@RunWith(AndroidJUnit4.class)
public class GameplayScriptTest {
    private UiDevice d;
    private Rect v;

    private void tap(float fx, float fy) {
        d.click((int) (v.left + v.width() * fx), (int) (v.top + v.height() * fy));
    }

    private void awaitView(String pkg) {
        android.content.Context c = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
        UiObject2 first = d.wait(Until.findObject(By.pkg(pkg).clazz("android.widget.Button")), 30000);
        if (first == null) throw new AssertionError("top buttons not found for " + pkg);
        int dh = d.getDisplayHeight(), dw = d.getDisplayWidth(), top = 0;
        for (UiObject2 b : d.findObjects(By.pkg(pkg).clazz("android.widget.Button"))) {
            Rect r = b.getVisibleBounds();
            if (r.centerY() < dh / 4) top = Math.max(top, r.bottom);
        }
        int navId = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = navId > 0 ? c.getResources().getDimensionPixelSize(navId) : 0;
        v = new Rect(0, top, dw, dh - nav);
    }

    @Test public void playTicTac() throws Exception {
        d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        awaitView("nir.com.tictacneon");
        Thread.sleep(1500);
        float w = v.width(), h = v.height();
        float cell = w * 0.86f / 3f, bx = (w - w * 0.86f) / 2f, by = h * 0.18f;
        tap((bx + 1.5f * cell) / w, (by + 1.5f * cell) / h); Thread.sleep(2000); // center
        tap((bx + 0.5f * cell) / w, (by + 0.5f * cell) / h); Thread.sleep(2000); // top-left
        tap((bx + 2.5f * cell) / w, (by + 2.5f * cell) / h); Thread.sleep(2000); // bottom-right
        tap((bx + 2.5f * cell) / w, (by + 0.5f * cell) / h); Thread.sleep(2500); // top-right
        tap(0.5f, (h * 0.325f + w * 0.86f + h * 0.03f) / h); Thread.sleep(1500); // NEW GAME area
    }
}
