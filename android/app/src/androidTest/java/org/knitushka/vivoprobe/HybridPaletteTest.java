package org.knitushka.vivoprobe;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager2.widget.ViewPager2;
import com.qq.knitushka.R;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class HybridPaletteTest {
    @Test public void recoloursInBothDirectionsWithoutChangingSelectedDayOrHeaderGeometry() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.runOnMainSync(() -> {
            try {
                ProbeView root = (ProbeView)LayoutInflater.from(context).inflate(R.layout.vivo_hybrid_widget, null);
                JSONArray days = new JSONArray();
                for (int index = 0; index < 2; index++) {
                    JSONObject lesson = new JSONObject().put("number", 2).put("subject", "Основы российской государственности")
                        .put("time", "11:20 – 12:50").put("room", "Д-236");
                    days.put(new JSONObject().put("date", index == 0 ? "2026-11-24" : "2026-11-25")
                        .put("lessons", new JSONArray().put(lesson)));
                }
                JSONObject payload = new JSONObject().put("widgetId", -1).put("selected", "2026-11-25")
                    .put("group", "861-72").put("days", days);
                ((TextView)root.findViewById(R.id.vivo_payload)).setText(payload.toString());
                int width = Math.round(180 * context.getResources().getDisplayMetrics().density);
                int height = Math.round(300 * context.getResources().getDisplayMetrics().density);
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, width, height);
                ViewPager2 pager = findPager(root);
                assertNotNull(pager);
                assertEquals(1, pager.getCurrentItem());
                TextView date = root.findViewById(R.id.widget_date);
                int top = date.getTop(), left = date.getLeft();
                for (boolean dark : new boolean[]{true, false, true}) {
                    Configuration configuration = new Configuration(context.getResources().getConfiguration());
                    configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                    root.applyPalette(configuration);
                    assertEquals(dark ? Color.rgb(241, 233, 225) : Color.rgb(41, 37, 34), date.getCurrentTextColor());
                    TextView subject = root.findViewById(R.id.widget_lesson_subject);
                    assertNotNull(subject);
                    assertEquals(date.getCurrentTextColor(), subject.getCurrentTextColor());
                    assertEquals(1, pager.getCurrentItem());
                    assertEquals("25/11", date.getText().toString());
                    assertEquals(top, date.getTop());
                    assertEquals(left, date.getLeft());
                    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    root.draw(new Canvas(image));
                    assertEquals(dark ? Color.rgb(36, 33, 32) : Color.rgb(250, 247, 242), image.getPixel(width / 2, height / 2));
                    File file = new File(context.getCacheDir(), "vivo-hybrid-" + (dark ? "dark" : "light") + ".png");
                    try (FileOutputStream output = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, output); }
                    image.recycle();
                }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    private ViewPager2 findPager(View view) {
        if (view instanceof ViewPager2) return (ViewPager2)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ViewPager2 found = findPager(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
