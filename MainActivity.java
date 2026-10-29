package com.pplus.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final double DEF_LAT = 3.4508, DEF_LNG = 102.4196;
    private static final String DEF_PLACE = "Temerloh";

    private View drawer;
    private EditText search;
    private ListView list;
    private TextView hijriText, placeText, nextText;
    private TextView batteryText, batterySub, storageText, storageSub;
    private ProgressBar batteryBar, storageBar;
    private final TextView[] prayerName = new TextView[6];
    private final TextView[] prayerTime = new TextView[6];

    private SharedPreferences prefs;
    private GestureDetector gestures;
    private AppAdapter adapter;
    private final List<AppEntry> allApps = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean drawerShown;
    private Object backCallback; // OnBackInvokedCallback (API 33+)

    private int colorFg, colorDim, colorAccent, colorWarn;

    // ---------------------------------------------------------------- receivers

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateBattery(i); }
    };

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refreshInfo(); }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("pplus", MODE_PRIVATE);
        colorFg = getColor(R.color.fg);
        colorDim = getColor(R.color.dim);
        colorAccent = getColor(R.color.accent);
        colorWarn = getColor(R.color.warn);

        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = in.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = in.getSystemWindowInsetLeft(); t = in.getSystemWindowInsetTop();
                r = in.getSystemWindowInsetRight(); b = in.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return in;
        });

        drawer = findViewById(R.id.drawer);
        search = findViewById(R.id.search);
        list = findViewById(R.id.list);
        hijriText = findViewById(R.id.hijri);
        placeText = findViewById(R.id.prayer_place);
        nextText = findViewById(R.id.prayer_next);
        batteryText = findViewById(R.id.battery_text);
        batterySub = findViewById(R.id.battery_sub);
        batteryBar = findViewById(R.id.battery_bar);
        storageText = findViewById(R.id.storage_text);
        storageSub = findViewById(R.id.storage_sub);
        storageBar = findViewById(R.id.storage_bar);

        buildPrayerRow(findViewById(R.id.prayer_row));

        findViewById(R.id.clock).setOnClickListener(v ->
                safeStart(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        findViewById(R.id.date).setOnClickListener(v ->
                safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)));
        View prayerCard = findViewById(R.id.prayer_card);
        prayerCard.setOnClickListener(v ->
                Toast.makeText(this, R.string.hint_location, Toast.LENGTH_SHORT).show());
        prayerCard.setOnLongClickListener(v -> { showLocationDialog(); return true; });
        findViewById(R.id.battery_card).setOnClickListener(v ->
                safeStart(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));
        findViewById(R.id.storage_card).setOnClickListener(v ->
                safeStart(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        findViewById(R.id.apps_hint).setOnClickListener(v -> openDrawer());

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> launch(adapter.getItem(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> { appInfo(adapter.getItem(pos)); return true; });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { adapter.filter(s.toString()); }
        });
        search.setOnEditorActionListener((v, action, ev) -> {
            if (adapter.getCount() > 0) {
                launch(adapter.getItem(0));
            } else if (search.length() > 0) {
                Intent web = new Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra("query", search.getText().toString());
                if (!safeStart(web)) {
                    safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://www.google.com/search?q=" + Uri.encode(search.getText().toString()))));
                }
            }
            return true;
        });

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dy) < dp(60) || Math.abs(vy) < Math.abs(vx)) return false;
                if (dy < 0 && !drawerShown) { openDrawer(); return true; }
                if (dy > 0 && drawerShown && !list.canScrollVertically(-1)) { closeDrawer(true); return true; }
                return false;
            }
        });

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        register(packageReceiver, pf);

        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        register(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        register(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInfo();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(batteryReceiver); } catch (IllegalArgumentException ignored) {}
        try { unregisterReceiver(tickReceiver); } catch (IllegalArgumentException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packageReceiver); } catch (IllegalArgumentException ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Butang Home ditekan semasa launcher di depan
        closeDrawer(true);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Launcher tidak boleh "ditutup"; Back hanya menutup drawer.
        if (drawerShown) closeDrawer(true);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures != null && gestures.onTouchEvent(ev)) {
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---------------------------------------------------------------- setup

    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(r, f);
    }

    private void buildPrayerRow(LinearLayout row) {
        for (int i = 0; i < 6; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(this);
            name.setText(PrayerTimes.NAMES[i]);
            name.setTextSize(11);
            name.setTextColor(colorDim);
            name.setSingleLine(true);

            TextView time = new TextView(this);
            time.setTextSize(15);
            time.setTextColor(colorFg);
            time.setPadding(0, dp(2), 0, 0);
            time.setSingleLine(true);

            cell.addView(name);
            cell.addView(time);
            row.addView(cell);
            prayerName[i] = name;
            prayerTime[i] = time;
        }
    }

    // ---------------------------------------------------------------- info widgets

    private void refreshInfo() {
        updatePrayer();
        updateStorage();
        hijriText.setText(hijri());
    }

    private void updatePrayer() {
        double lat = pref("lat", DEF_LAT), lng = pref("lng", DEF_LNG);
        placeText.setText(prefs.getString("place", DEF_PLACE).toUpperCase(Locale.ROOT));

        Calendar now = Calendar.getInstance();
        int[] t = PrayerTimes.compute(now, lat, lng);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int next = -1;
        for (int i = 0; i < 6; i++) {
            if (i == 1) continue; // Syuruk bukan waktu solat
            if (t[i] > nowMin) { next = i; break; }
        }
        int left;
        if (next == -1) {
            Calendar tomorrow = (Calendar) now.clone();
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            int[] t2 = PrayerTimes.compute(tomorrow, lat, lng);
            next = 0;
            left = t2[0] + 1440 - nowMin;
        } else {
            left = t[next] - nowMin;
        }

        boolean h24 = DateFormat.is24HourFormat(this);
        for (int i = 0; i < 6; i++) {
            prayerTime[i].setText(fmtTime(t[i], h24));
            boolean hl = i == next;
            prayerTime[i].setTextColor(hl ? colorAccent : colorFg);
            prayerName[i].setTextColor(hl ? colorAccent : colorDim);
            prayerTime[i].setTypeface(null, hl ? Typeface.BOLD : Typeface.NORMAL);
        }
        nextText.setText(getString(R.string.next_prayer, PrayerTimes.NAMES[next], fmtDuration(left)));
    }

    private void updateBattery(Intent i) {
        if (i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level < 0 || scale <= 0 ? 0 : Math.round(level * 100f / scale);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        float temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;

        batteryText.setText(pct + "%" + (charging ? " ⚡" : ""));
        String sub = String.format(Locale.ROOT, "%.1f°C", temp);
        if (status == BatteryManager.BATTERY_STATUS_FULL) sub += " · " + getString(R.string.full);
        else if (charging) sub += " · " + getString(R.string.charging);
        batterySub.setText(sub);
        batteryBar.setProgress(pct);
        batteryBar.setProgressTintList(ColorStateList.valueOf(
                pct <= 15 && !charging ? colorWarn : colorAccent));
    }

    private void updateStorage() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            long used = total - free;
            int pct = total > 0 ? (int) (used * 100 / total) : 0;
            storageText.setText(pct + "%");
            storageSub.setText(getString(R.string.storage_sub,
                    android.text.format.Formatter.formatShortFileSize(this, free),
                    android.text.format.Formatter.formatShortFileSize(this, total)));
            storageBar.setProgress(pct);
            storageBar.setProgressTintList(ColorStateList.valueOf(pct >= 90 ? colorWarn : colorAccent));
        } catch (RuntimeException e) {
            storageSub.setText("—");
        }
    }

    private String hijri() {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("ms_MY@calendar=islamic-umalqura");
            android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("d MMMM y", loc);
            return f.format(new Date()) + "H";
        } catch (Throwable t) {
            return "";
        }
    }

    private void showLocationDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(8), p, 0);

        EditText place = field(box, getString(R.string.place), prefs.getString("place", DEF_PLACE),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        EditText lat = field(box, getString(R.string.latitude), String.valueOf(pref("lat", DEF_LAT)), num);
        EditText lng = field(box, getString(R.string.longitude), String.valueOf(pref("lng", DEF_LNG)), num);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.location_title)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    try {
                        double la = Double.parseDouble(lat.getText().toString().trim());
                        double lo = Double.parseDouble(lng.getText().toString().trim());
                        if (Math.abs(la) > 66 || Math.abs(lo) > 180) throw new NumberFormatException();
                        String pl = place.getText().toString().trim();
                        prefs.edit()
                                .putString("lat", String.valueOf(la))
                                .putString("lng", String.valueOf(lo))
                                .putString("place", pl.isEmpty() ? "?" : pl)
                                .apply();
                        updatePrayer();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.invalid_coords, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(DEF_PLACE, (d, w) -> {
                    prefs.edit().remove("lat").remove("lng").remove("place").apply();
                    updatePrayer();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText field(LinearLayout parent, String hint, String value, int type) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setTextSize(12);
        label.setPadding(0, dp(10), 0, 0);
        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(type);
        e.setSingleLine(true);
        parent.addView(label);
        parent.addView(e);
        return e;
    }

    // ---------------------------------------------------------------- drawer

    private void openDrawer() {
        if (drawerShown) return;
        drawerShown = true;
        search.setText("");
        list.setSelection(0);
        drawer.animate().cancel();
        drawer.setVisibility(View.VISIBLE);
        drawer.setAlpha(0f);
        drawer.setTranslationY(dp(40));
        drawer.animate().alpha(1f).translationY(0f).setDuration(160).start();
        search.requestFocus();
        search.postDelayed(() -> {
            if (drawerShown) imm().showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
        setBackCallback(true);
    }

    private void closeDrawer(boolean animate) {
        if (!drawerShown) return;
        drawerShown = false;
        imm().hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        drawer.animate().cancel();
        if (animate) {
            drawer.animate().alpha(0f).translationY(dp(40)).setDuration(130)
                    .withEndAction(() -> { if (!drawerShown) drawer.setVisibility(View.GONE); })
                    .start();
        } else {
            drawer.setVisibility(View.GONE);
        }
        setBackCallback(false);
    }

    /** Sokongan predictive back (Android 13+): Back menutup drawer. */
    private void setBackCallback(boolean enable) {
        if (Build.VERSION.SDK_INT < 33) return;
        android.window.OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (enable && backCallback == null) {
            android.window.OnBackInvokedCallback cb = () -> closeDrawer(true);
            d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        } else if (!enable && backCallback != null) {
            d.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
    }

    private InputMethodManager imm() {
        return (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    }

    // ---------------------------------------------------------------- apps

    static final class AppEntry {
        final String label;
        final String lower;
        final ComponentName component;
        Drawable icon;

        AppEntry(String label, ComponentName component, Drawable icon) {
            this.label = label;
            this.lower = label.toLowerCase(Locale.ROOT);
            this.component = component;
            this.icon = icon;
        }
    }

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final String self = getPackageName();
        new Thread(() -> {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
            final List<AppEntry> fresh = new ArrayList<>(ris.size());
            for (ResolveInfo ri : ris) {
                if (self.equals(ri.activityInfo.packageName)) continue;
                CharSequence l = ri.loadLabel(pm);
                fresh.add(new AppEntry(l == null ? ri.activityInfo.packageName : l.toString(),
                        new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                        ri.loadIcon(pm)));
            }
            Collections.sort(fresh, (a, b) -> a.label.compareToIgnoreCase(b.label));
            ui.post(() -> {
                allApps.clear();
                allApps.addAll(fresh);
                adapter.filter(search.getText().toString());
            });
        }, "load-apps").start();
    }

    private void launch(AppEntry a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (safeStart(i)) closeDrawer(false);
    }

    private void appInfo(AppEntry a) {
        safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", a.component.getPackageName(), null)));
    }

    private boolean safeStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<AppEntry> shown = new ArrayList<>();

        void filter(String query) {
            shown.clear();
            String s = query.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) {
                shown.addAll(allApps);
            } else {
                List<AppEntry> words = new ArrayList<>();
                List<AppEntry> contains = new ArrayList<>();
                for (AppEntry a : allApps) {
                    if (a.lower.startsWith(s)) shown.add(a);
                    else if (a.lower.contains(" " + s)) words.add(a);
                    else if (a.lower.contains(s)) contains.add(a);
                }
                shown.addAll(words);
                shown.addAll(contains);
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return shown.size(); }
        @Override public AppEntry getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_app, parent, false);
            AppEntry a = shown.get(position);
            ((TextView) v.findViewById(R.id.app_label)).setText(a.label);
            ((ImageView) v.findViewById(R.id.app_icon)).setImageDrawable(a.icon);
            return v;
        }
    }

    // ---------------------------------------------------------------- utils

    private double pref(String key, double def) {
        try {
            String v = prefs.getString(key, null);
            return v == null ? def : Double.parseDouble(v);
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static String fmtTime(int minutes, boolean h24) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        int h = minutes / 60, m = minutes % 60;
        if (h24) return String.format(Locale.ROOT, "%02d:%02d", h, m);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.ROOT, "%d:%02d", h12, m);
    }

    private static String fmtDuration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? h + "j " + m + "m" : m + "m";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
