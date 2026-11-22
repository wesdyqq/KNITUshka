package org.knitushka.vivoprobe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.qq.knitushka.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Debug-only native View hosted by Vivo. All data arrives via standard RemoteViews.
 * No host storage, privileged calls, network or background workers.
 * Navigation and opening the app invoke app-issued PendingIntents, never host-identity intents.
 */
@RemoteViews.RemoteView
public final class ProbeView extends FrameLayout {
    private static final String TAG = "KnituVivoProbe";
    private static final Locale RU = Locale.forLanguageTag("ru-RU");
    private ViewPager2 pager;
    private TextView dateLabel, weekdayLabel, groupLabel;
    private ImageButton previous, next;
    private Dots dots;
    private JSONArray days = new JSONArray();
    private String applied = "";
    private boolean binding;
    private float downX, downY;
    private int widgetId = -1;
    private Context paletteContext;
    private int paletteNight = -1;
    private final GestureDetector taps;
    private final Runnable resetDay = () -> {
        int today = indexOf(LocalDate.now().toString());
        if (today >= 0) select(today, false);
    };

    public ProbeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paletteContext = context;
        setOnClickListener(view -> openSelectedDay());
        setContentDescription("Открыть выбранный день в КНИТУшке");
        taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent event) {
                if (!inside(previous, event) && !inside(next, event)) performClick();
                return false;
            }
        });
        setBackgroundResource(R.drawable.widget_background);
        setClipToOutline(true);
        Log.i(TAG, "CUSTOM_VIEW_CREATED process=" + (Build.VERSION.SDK_INT >= 28
            ? android.app.Application.getProcessName() : "unknown"));
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        // One surface, no nested cards. Header's existing safe inset is retained.
        body.setPadding(dp(4), dp(12), dp(4), dp(4));
        addView(body, new FrameLayout.LayoutParams(-1, -1));
        View header = LayoutInflater.from(getContext()).inflate(R.layout.widget_header, body, false);
        dateLabel = header.findViewById(R.id.widget_date);
        weekdayLabel = header.findViewById(R.id.widget_weekday);
        groupLabel = header.findViewById(R.id.widget_group);
        body.addView(header);
        pager = new ViewPager2(getContext());
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new DayAdapter());
        body.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = new LinearLayout(getContext());
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(4), 0, dp(4), 0);
        previous = arrow(false);
        next = arrow(true);
        footer.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(44)));
        FrameLayout dotArea = new FrameLayout(getContext());
        dots = new Dots(getContext());
        dotArea.addView(dots, new FrameLayout.LayoutParams(-1, dp(12), Gravity.CENTER));
        footer.addView(dotArea, new LinearLayout.LayoutParams(0, dp(44), 1));
        footer.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));
        body.addView(footer);
        previous.setOnClickListener(v -> select(pager.getCurrentItem() - 1, true));
        next.setOnClickListener(v -> select(pager.getCurrentItem() + 1, true));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                updateHeader(position);
                if (!binding) notifySelection(position);
            }
            @Override public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE && days.length() > 0) {
                    Log.i(TAG, "IDLE widget=" + widgetId + " page=" + pager.getCurrentItem()
                        + " date=" + day(pager.getCurrentItem()).optString("date"));
                }
            }
        });
        applySystemPalette(getResources().getConfiguration());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Vivo may retain the provider's old configuration while reattaching a widget.
        applySystemPalette(getResources().getConfiguration());
    }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applySystemPalette(configuration);
    }

    private void applySystemPalette(Configuration fallback) {
        // The launcher's own theme/context can be forced dark independently of the OS.
        int systemNight = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (systemNight != Configuration.UI_MODE_NIGHT_YES && systemNight != Configuration.UI_MODE_NIGHT_NO) {
            systemNight = fallback.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        }
        Configuration effective = new Configuration(fallback);
        effective.uiMode = (effective.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | systemNight;
        applyPalette(effective);
    }

    /** Package-private for palette regression tests; never changes system settings. */
    void applyPalette(Configuration configuration) {
        int night = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (night == paletteNight && paletteContext != null) return;
        paletteNight = night;
        paletteContext = getContext().createConfigurationContext(configuration);
        setBackground(paletteContext.getDrawable(R.drawable.widget_background));
        if (dateLabel == null) return;
        dateLabel.setTextColor(paletteContext.getColor(R.color.widget_text));
        weekdayLabel.setTextColor(paletteContext.getColor(R.color.widget_secondary));
        groupLabel.setTextColor(paletteContext.getColor(R.color.widget_secondary));
        previous.setBackground(paletteContext.getDrawable(R.drawable.widget_navigation_background));
        next.setBackground(paletteContext.getDrawable(R.drawable.widget_navigation_background));
        previous.setImageDrawable(paletteContext.getDrawable(R.drawable.widget_arrow_previous));
        next.setImageDrawable(paletteContext.getDrawable(R.drawable.widget_arrow_next));
        // Recolour existing rows in place: keep the current date and both scroll positions.
        recolourRows(pager);
        dots.invalidate();
        invalidate();
        Log.i(TAG, "PALETTE widget=" + widgetId + " night=" + (night == Configuration.UI_MODE_NIGHT_YES));
    }

    private void recolourRows(View view) {
        int id = view.getId();
        if (view instanceof TextView) {
            int color = id == R.id.widget_lesson_number ? R.color.widget_accent
                : id == R.id.widget_lesson_subject ? R.color.widget_text : R.color.widget_secondary;
            ((TextView)view).setTextColor(paletteContext.getColor(color));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) recolourRows(group.getChildAt(i));
        }
    }

    private ImageButton arrow(boolean forward) {
        ImageButton button = new ImageButton(getContext());
        button.setBackgroundResource(R.drawable.widget_navigation_background);
        button.setImageResource(forward ? R.drawable.widget_arrow_next : R.drawable.widget_arrow_previous);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        button.setContentDescription(forward ? "Следующий день" : "Предыдущий день");
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        return button;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        TextView payload = findViewById(R.id.vivo_payload);
        if (payload != null && pager != null) {
            String raw = payload.getText().toString();
            if (!raw.isEmpty() && !raw.equals(applied)) bind(raw);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private void bind(String raw) {
        binding = true;
        try {
            JSONObject payload = new JSONObject(raw);
            JSONArray incoming = payload.getJSONArray("days");
            if (incoming.length() == 0 || incoming.length() > 35) return;
            widgetId = payload.getInt("widgetId");
            days = incoming;
            applied = raw;
            groupLabel.setText(payload.optString("group", "КНИТУшка"));
            pager.getAdapter().notifyDataSetChanged();
            int selected = indexOf(payload.optString("selected"));
            if (selected < 0) selected = Math.max(0, indexOf(LocalDate.now().toString()));
            pager.setCurrentItem(selected, false);
            updateHeader(selected);
            Log.i(TAG, "BOUND widget=" + widgetId + " days=" + days.length() + " selected=" + selected);
        } catch (Exception error) {
            Log.e(TAG, "Invalid widget payload", error);
            weekdayLabel.setText("Не удалось прочитать расписание");
        } finally { binding = false; }
    }

    private int indexOf(String date) {
        for (int i = 0; i < days.length(); i++) if (day(i).optString("date").equals(date)) return i;
        return -1;
    }

    private JSONObject day(int index) {
        JSONObject value = days.optJSONObject(index);
        return value != null ? value : new JSONObject();
    }

    private void select(int index, boolean smooth) {
        if (index < 0 || index >= days.length()) return;
        pager.setCurrentItem(index, smooth && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()));
    }

    private void updateHeader(int index) {
        if (index < 0 || index >= days.length()) return;
        try {
            LocalDate date = LocalDate.parse(day(index).getString("date"));
            dateLabel.setText(date.format(DateTimeFormatter.ofPattern("dd/MM", RU)));
            dateLabel.setContentDescription(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", RU)));
            String weekday = date.format(DateTimeFormatter.ofPattern("EEEE", RU));
            weekdayLabel.setText(weekday.substring(0, 1).toUpperCase(RU) + weekday.substring(1));
            dots.selected = date.getDayOfWeek().getValue() - 1;
            dots.setContentDescription("День недели " + (dots.selected + 1) + " из 7");
            dots.invalidate();
            previous.setEnabled(index > 0);
            next.setEnabled(index < days.length() - 1);
            previous.setAlpha(index > 0 ? 1f : .4f);
            next.setAlpha(index < days.length() - 1 ? 1f : .4f);
        } catch (Exception error) { Log.e(TAG, "Invalid date", error); }
    }

    private void notifySelection(int index) {
        ViewGroup tokens = findViewById(R.id.vivo_tokens);
        if (tokens != null && index >= 0 && index < tokens.getChildCount()) {
            tokens.getChildAt(index).performClick();
        }
        removeCallbacks(resetDay);
        if (!day(index).optString("date").equals(LocalDate.now().toString())) postDelayed(resetDay, 600_000);
        Log.i(TAG, "NATIVE_SELECT widget=" + widgetId + " date=" + day(index).optString("date"));
    }

    private boolean inside(View view, MotionEvent event) {
        if (view == null) return false;
        Rect bounds = new Rect();
        view.getDrawingRect(bounds);
        offsetDescendantRectToMyCoords(view, bounds);
        return bounds.contains((int)event.getX(), (int)event.getY());
    }

    private void openSelectedDay() {
        if (pager == null) return;
        ViewGroup tokens = findViewById(R.id.vivo_open_tokens);
        int selected = pager.getCurrentItem();
        if (tokens != null && selected >= 0 && selected < tokens.getChildCount()) {
            tokens.getChildAt(selected).performClick();
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // GestureDetector dispatches one accessible click for a tap, including empty page
        // space. Do not call View's click detection as well (which would open twice).
        return isClickable();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        taps.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float x = Math.abs(event.getX() - downX), y = Math.abs(event.getY() - downY);
            if (x > ViewConfiguration.get(getContext()).getScaledTouchSlop() && x > y) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        boolean result = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        return result;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(resetDay);
        super.onDetachedFromWindow();
    }

    private final class DayAdapter extends RecyclerView.Adapter<DayHolder> {
        @Override public int getItemCount() { return days.length(); }
        @Override public DayHolder onCreateViewHolder(ViewGroup parent, int type) {
            ScrollView scroll = new ScrollView(getContext());
            scroll.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            scroll.setClipToPadding(true);
            scroll.setPadding(dp(12), 0, dp(12), 0);
            scroll.setOverScrollMode(OVER_SCROLL_NEVER);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout rows = new LinearLayout(getContext());
            rows.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
            return new DayHolder(scroll, rows);
        }
        @Override public void onBindViewHolder(DayHolder holder, int position) {
            holder.rows.removeAllViews();
            ((ScrollView)holder.itemView).scrollTo(0, 0);
            JSONObject day = day(position);
            JSONArray lessons = day.optJSONArray("lessons");
            if (lessons == null || lessons.length() == 0) {
                TextView empty = new TextView(paletteContext);
                empty.setText(day.optString("empty", "На этот день пар нет"));
                empty.setTextColor(paletteContext.getColor(R.color.widget_secondary));
                empty.setTextSize(11);
                empty.setPadding(0, dp(6), 0, dp(6));
                holder.rows.addView(empty);
                return;
            }
            for (int i = 0; i < lessons.length(); i++) {
                JSONObject row = lessons.optJSONObject(i);
                if (row == null) continue;
                View view = LayoutInflater.from(paletteContext).inflate(R.layout.schedule_widget_lesson, holder.rows, false);
                ((TextView)view.findViewById(R.id.widget_lesson_number)).setText(row.optString("number"));
                ((TextView)view.findViewById(R.id.widget_lesson_subject)).setText(row.optString("subject"));
                ((TextView)view.findViewById(R.id.widget_lesson_time)).setText(row.optString("time"));
                String room = row.optString("room");
                ((TextView)view.findViewById(R.id.widget_lesson_room)).setText(room.isEmpty() ? "Аудитория не указана" : room);
                holder.rows.addView(view);
            }
        }
    }

    private static final class DayHolder extends RecyclerView.ViewHolder {
        final LinearLayout rows;
        DayHolder(View view, LinearLayout rows) { super(view); this.rows = rows; }
    }

    private final class Dots extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int selected;
        Dots(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = Math.min(dp(7), getWidth() * .1f);
            float step = Math.max(0, (getWidth() - 2 * inset) / 6f);
            for (int i = 0; i < 7; i++) {
                paint.setColor(paletteContext.getColor(i == selected ? R.color.widget_accent : R.color.widget_dot_inactive));
                paint.setStyle(Paint.Style.FILL);
                float radius = Math.min(dp(i == selected ? 3.5f : 3), step * .38f);
                canvas.drawCircle(inset + i * step, getHeight() / 2f, radius, paint);
            }
        }
    }
}
