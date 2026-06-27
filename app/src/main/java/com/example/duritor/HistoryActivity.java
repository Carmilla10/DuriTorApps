package com.example.duritor;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

public class HistoryActivity extends DrawerActivity {

    private ListView historyListView;
    private DatabaseReference historyRef;
    private final List<FallEvent> events = new ArrayList<>();
    private final List<FallEvent> filteredEvents = new ArrayList<>();
    private HistoryAdapter adapter;
    private final Set<String> uploadingEventIds = Collections.synchronizedSet(new HashSet<>());

    // UI Components
    private LinearLayout calendarGridContainer;
    private TextView monthYearLabel;
    private TextView dateHeader;
    private Spinner filterSpinner;
    private String[] filterOptions = {"All", "Pending", "Collected"};
    private String currentFilter = "All";
    private String selectedDateFilter = null; // null = show all dates

    // Calendar Settings
    private int currentYear;
    private int currentMonth;
    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private enum DayStatus {
        ADJACENT, NO_ACTIVITY, FUTURE, PENDING, PARTIAL, COLLECTED, SELECTED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupDrawerShell(
                R.layout.activity_history,
                R.id.nav_history,
                R.string.title_history
        );

        historyListView = findViewById(R.id.historyListView);
        calendarGridContainer = findViewById(R.id.calendarGridContainer);
        monthYearLabel = findViewById(R.id.monthYearLabel);
        dateHeader = findViewById(R.id.dateHeader);
        filterSpinner = findViewById(R.id.filterSpinner);

        Calendar cal = Calendar.getInstance();
        currentYear = cal.get(Calendar.YEAR);
        currentMonth = cal.get(Calendar.MONTH);

        findViewById(R.id.monthYearButton).setOnClickListener(v -> showMonthYearPicker());
        updateMonthYearLabel();
        updateDateHeader();
        setupCalendar();

        adapter = new HistoryAdapter();
        historyListView.setAdapter(adapter);

        // Setup Filter Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filterOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(spinnerAdapter);

        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilter = filterOptions[position];
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                currentFilter = "All";
                applyFilters();
            }
        });

        historyRef = FirebaseDatabase.getInstance().getReference("fallEvents");
        loadHistory();
    }

    private void applyFilters() {
        filteredEvents.clear();
        for (FallEvent event : events) {
            // 1. Status Filter
            boolean matchesStatus = false;
            if (currentFilter.equals("All")) {
                matchesStatus = true;
            } else if (currentFilter.equals("Pending") && !event.collected) {
                matchesStatus = true;
            } else if (currentFilter.equals("Collected") && event.collected) {
                matchesStatus = true;
            }

            if (!matchesStatus) continue;

            // 2. Calendar Date Filter
            boolean matchesDate = true;
            if (selectedDateFilter != null) {
                matchesDate = event.date.equals(selectedDateFilter);
            }

            if (matchesStatus && matchesDate) {
                filteredEvents.add(event);
            }
        }
        adapter.notifyDataSetChanged();
        updateDateHeader();
        updateListViewHeight();
    }

    private void updateListViewHeight() {
        if (historyListView == null) return;
        ListAdapter listAdapter = historyListView.getAdapter();
        if (listAdapter == null) return;

        int totalHeight = 0;
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                historyListView.getWidth() > 0
                        ? historyListView.getWidth()
                        : getResources().getDisplayMetrics().widthPixels - (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics()),
                View.MeasureSpec.EXACTLY);

        for (int i = 0; i < listAdapter.getCount(); i++) {
            View item = listAdapter.getView(i, null, historyListView);
            item.measure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            totalHeight += item.getMeasuredHeight();
        }

        if (listAdapter.getCount() > 1) {
            totalHeight += historyListView.getDividerHeight() * (listAdapter.getCount() - 1);
        }
        totalHeight += historyListView.getPaddingTop() + historyListView.getPaddingBottom();

        ViewGroup.LayoutParams params = historyListView.getLayoutParams();
        params.height = totalHeight;
        historyListView.setLayoutParams(params);
        historyListView.requestLayout();
    }

    private void loadHistory() {
        historyRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                events.clear();

                if (isFallEventSnapshot(snapshot)) {
                    events.add(buildEventFromSnapshot(snapshot, snapshot.getKey() != null ? snapshot.getKey() : "fallEvents"));
                } else {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (!isFallEventSnapshot(child)) continue;
                        String eventId = child.getKey();
                        if (eventId == null) continue;
                        events.add(buildEventFromSnapshot(child, eventId));
                    }
                }

                // Sort by date/time (newest first)
                events.sort((e1, e2) -> {
                    String d1 = e1.date + " " + e1.time;
                    String d2 = e2.date + " " + e2.time;
                    return d2.compareTo(d1);
                });

                setupCalendar();
                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- CALENDAR METHODS ---
    private void updateMonthYearLabel() {
        Calendar cal = Calendar.getInstance();
        cal.set(currentYear, currentMonth, 1);
        monthYearLabel.setText(monthYearFormat.format(cal.getTime()));
    }

    private void updateDateHeader() {
        if (selectedDateFilter == null) {
            dateHeader.setText(R.string.calendar_all_dates);
            return;
        }
        dateHeader.setText(formatDate(selectedDateFilter));
    }

    private void showMonthYearPicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_month_year_picker, null);
        Spinner yearSpinner = dialogView.findViewById(R.id.yearSpinner);
        Spinner monthSpinner = dialogView.findViewById(R.id.monthSpinner);

        Calendar now = Calendar.getInstance();
        int startYear = now.get(Calendar.YEAR) - 5;
        int endYear = now.get(Calendar.YEAR) + 1;
        List<String> years = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            years.add(String.valueOf(y));
        }

        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, years);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);
        yearSpinner.setSelection(currentYear - startYear);

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MONTH_NAMES);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthSpinner.setAdapter(monthAdapter);
        monthSpinner.setSelection(currentMonth);

        new AlertDialog.Builder(this)
                .setTitle(R.string.calendar_select_month_year)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    currentYear = Integer.parseInt(years.get(yearSpinner.getSelectedItemPosition()));
                    currentMonth = monthSpinner.getSelectedItemPosition();
                    selectedDateFilter = null;
                    updateMonthYearLabel();
                    updateDateHeader();
                    setupCalendar();
                    applyFilters();
                })
                .show();
    }

    private void setupCalendar() {
        calendarGridContainer.removeAllViews();

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(currentYear, currentMonth, 1);
        int firstDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar prevMonth = Calendar.getInstance();
        prevMonth.set(currentYear, currentMonth, 1);
        prevMonth.add(Calendar.DAY_OF_MONTH, -1);
        int daysInPrevMonth = prevMonth.get(Calendar.DAY_OF_MONTH);

        Calendar today = Calendar.getInstance();
        int day = 1;
        int nextMonthDay = 1;

        for (int row = 0; row < 6; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44)));

            for (int col = 0; col < 7; col++) {
                int cellIndex = row * 7 + col;

                if (cellIndex < firstDayOfWeek && day == 1) {
                    int prevDay = daysInPrevMonth - (firstDayOfWeek - cellIndex - 1);
                    rowLayout.addView(createDayCell(String.valueOf(prevDay), DayStatus.ADJACENT, null));
                } else if (day <= daysInMonth) {
                    String dateKey = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, day);
                    boolean isFuture = isFutureDate(currentYear, currentMonth, day, today);
                    DayStatus status = resolveDayStatus(dateKey, isFuture);
                    rowLayout.addView(createDayCell(String.valueOf(day), status, dateKey));
                    day++;
                } else if (nextMonthDay <= 14) {
                    rowLayout.addView(createDayCell(String.valueOf(nextMonthDay), DayStatus.ADJACENT, null));
                    nextMonthDay++;
                } else {
                    rowLayout.addView(createEmptyCell());
                }
            }
            calendarGridContainer.addView(rowLayout);
        }
    }

    private DayStatus resolveDayStatus(String dateKey, boolean isFuture) {
        if (dateKey.equals(selectedDateFilter)) {
            return DayStatus.SELECTED;
        }

        int total = 0;
        int collected = 0;
        for (FallEvent event : events) {
            if (dateKey.equals(event.date)) {
                total++;
                if (event.collected) {
                    collected++;
                }
            }
        }

        if (total == 0) {
            if (isFuture) {
                return DayStatus.FUTURE;
            }
            if (isTodayDateKey(dateKey)) {
                return DayStatus.PARTIAL;
            }
            return DayStatus.NO_ACTIVITY;
        }
        if (collected == total) {
            return DayStatus.COLLECTED;
        }
        if (collected == 0) {
            return DayStatus.PENDING;
        }
        return DayStatus.PARTIAL;
    }

    private View createDayCell(String dayText, DayStatus status, String dateKey) {
        FrameLayout cell = new FrameLayout(this);
        int size = dpToPx(34);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(44), 1f);
        cell.setLayoutParams(params);

        View circleBg = new View(this);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(size, size);
        circleParams.gravity = android.view.Gravity.CENTER;
        circleBg.setLayoutParams(circleParams);

        TextView textView = new TextView(this);
        textView.setText(dayText);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setGravity(android.view.Gravity.CENTER);
        textView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                android.view.Gravity.CENTER));

        switch (status) {
            case ADJACENT:
                circleBg.setBackgroundColor(0x00000000);
                textView.setTextColor(getColor(R.color.text_hint));
                textView.setAlpha(0.45f);
                break;
            case SELECTED:
                circleBg.setBackgroundResource(R.drawable.bg_calendar_selected);
                textView.setTextColor(getColor(R.color.white));
                break;
            case COLLECTED:
                circleBg.setBackgroundResource(R.drawable.bg_calendar_collected);
                textView.setTextColor(getColor(R.color.white));
                break;
            case PARTIAL:
                circleBg.setBackgroundResource(R.drawable.bg_calendar_partial);
                textView.setTextColor(getColor(R.color.white));
                break;
            case PENDING:
                circleBg.setBackgroundResource(R.drawable.bg_calendar_pending);
                textView.setTextColor(getColor(R.color.white));
                break;
            case FUTURE:
            case NO_ACTIVITY:
            default:
                circleBg.setBackgroundResource(R.drawable.bg_calendar_no_activity);
                textView.setTextColor(getColor(R.color.text_primary));
                break;
        }

        cell.addView(circleBg);
        cell.addView(textView);

        if (dateKey != null) {
            cell.setClickable(true);
            cell.setFocusable(true);
            TypedValue selectableBg = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableBg, true);
            cell.setForeground(ContextCompat.getDrawable(this, selectableBg.resourceId));
            cell.setOnClickListener(v -> {
                if (dateKey.equals(selectedDateFilter)) {
                    selectedDateFilter = null;
                } else {
                    selectedDateFilter = dateKey;
                }
                applyFilters();
                setupCalendar();
            });
        }

        return cell;
    }
    private View createEmptyCell() {
        FrameLayout cell = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(44), 1f);
        cell.setLayoutParams(params);
        return cell;
    }

    private boolean isTodayDateKey(String dateKey) {
        return dateKey.equals(dateKeyFormat.format(new Date()));
    }

    private boolean isFutureDate(int year, int month, int day, Calendar today) {
        Calendar target = Calendar.getInstance();
        target.set(year, month, day, 0, 0, 0);
        target.set(Calendar.MILLISECOND, 0);
        Calendar todayStart = (Calendar) today.clone();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        return target.after(todayStart);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // --- HELPER DATA METHODS ---
    private boolean isFallEventSnapshot(DataSnapshot snapshot) {
        if (snapshot == null) return false;
        return snapshot.hasChild("alert") || snapshot.hasChild("date") || snapshot.hasChild("time") || snapshot.hasChild("photoUrl") || snapshot.hasChild("imageUrl") || snapshot.hasChild("url");
    }

    private FallEvent buildEventFromSnapshot(DataSnapshot snapshot, String eventId) {
        FallEvent event = new FallEvent();
        event.id = eventId;
        event.alert = valueOrDefault(snapshot.child("alert").getValue(String.class), "Durian Fall Detected!");
        event.date = valueOrDefault(snapshot.child("date").getValue(String.class), "-");
        event.time = valueOrDefault(snapshot.child("time").getValue(String.class), "-");
        event.orchard = valueOrDefault(snapshot.child("orchardName").getValue(String.class), "Unknown Orchard");
        event.region = valueOrDefault(snapshot.child("regionName").getValue(String.class), "Unknown Region");
        event.tree = valueOrDefault(snapshot.child("treeName").getValue(String.class),
                valueOrDefault(snapshot.child("treeId").getValue(String.class), "Unknown Tree"));
        event.photoUrl = readImageValue(snapshot);
        Boolean collected = snapshot.child("collected").getValue(Boolean.class);
        event.collected = collected != null && collected;
        return event;
    }

    private String valueOrDefault(String value, String fallback) {
        return (value != null && !value.isEmpty()) ? value : fallback;
    }

    private String formatDate(String date) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());
            Date parsedDate = inputFormat.parse(date);
            if (parsedDate != null) {
                return outputFormat.format(parsedDate);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return date;
    }

    private String readImageValue(DataSnapshot snapshot) {
        String[] possibleKeys = {
                "url", "storagePath", "photoPath", "photoUrl", "photoURL",
                "imageUrl", "imageURL", "imgUrl", "imgURL", "image", "downloadUrl"
        };

        for (String key : possibleKeys) {
            String value = snapshot.child(key).getValue(String.class);
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    // --- ACTION BUTTON METHODS ---
    private void collectEvent(FallEvent event) {
        if (event == null || event.id == null || event.id.isEmpty()) {
            Toast.makeText(this, "Unable to collect this event", Toast.LENGTH_SHORT).show();
            return;
        }

        if (event.collected) {
            Toast.makeText(this, "Already collected", Toast.LENGTH_SHORT).show();
            return;
        }

        event.collected = true;

        String collectedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        Map<String, Object> updates = new HashMap<>();
        updates.put("collected", true);
        updates.put("collectedAt", collectedAt);

        historyRef.child(event.id).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    if (!isDestroyed() && !isFinishing()) {
                        Toast.makeText(HistoryActivity.this, "✅ Marked as collected!", Toast.LENGTH_SHORT).show();
                        runOnUiThread(() -> applyFilters());
                    }
                })
                .addOnFailureListener(e -> {
                    event.collected = false;
                    if (!isDestroyed() && !isFinishing()) {
                        Toast.makeText(HistoryActivity.this, "Failed to collect: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        runOnUiThread(() -> applyFilters());
                    }
                });
    }

    private void confirmDeleteEvent(FallEvent event) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Event")
                .setMessage("Are you sure you want to delete this event?")
                .setPositiveButton("Delete", (dialog, which) -> deleteEvent(event))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteEvent(FallEvent event) {
        if (event == null || event.id == null || event.id.isEmpty()) {
            Toast.makeText(this, "Unable to delete this event", Toast.LENGTH_SHORT).show();
            return;
        }

        historyRef.child(event.id).removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(HistoryActivity.this, "🗑️ Event deleted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(HistoryActivity.this, "Failed to delete event", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- IMAGE LOADING HELPERS ---
    private String sanitizeImageUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        return trimmed.replace(" ", "%20");
    }

    private void downloadToLocalCache(String eventId, String imageUrl, ImageView target) {
        if (eventId == null || eventId.isEmpty() || imageUrl == null || imageUrl.isEmpty()) return;
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) return;

        if (!uploadingEventIds.add("dl:" + eventId)) {
            Log.d("HistoryActivity", "Download already in progress for event " + eventId);
            return;
        }

        new Thread(() -> {
            File cacheDir = new File(getCacheDir(), "fall_images");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File outFile = new File(cacheDir, eventId + ".jpg");

            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 400) {
                    Log.e("HistoryActivity", "HTTP response code " + responseCode + " for " + imageUrl);
                    return;
                }

                try (InputStream is = connection.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                    }
                    bos.flush();
                }

                if (outFile.exists() && outFile.length() > 0) {
                    runOnUiThread(() -> loadImageUrl(outFile.toURI().toString(), target));
                } else {
                    Log.e("HistoryActivity", "Downloaded file empty for " + imageUrl);
                }
            } catch (IOException e) {
                Log.e("HistoryActivity", "Failed to download image to local cache: " + imageUrl, e);
            } finally {
                uploadingEventIds.remove("dl:" + eventId);
            }
        }).start();
    }

    private void loadImageUrl(String url, ImageView target) {
        String sanitizedUrl = sanitizeImageUrl(url);
        Log.d("HistoryActivity", "Loading image URL: " + sanitizedUrl);
        Glide.with(this)
                .load(Uri.parse(sanitizedUrl))
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .timeout(15000)
                        .skipMemoryCache(false)
                        .centerCrop())
                .placeholder(android.R.drawable.ic_menu_camera)
                .error(android.R.drawable.ic_menu_camera)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target1, boolean isFirstResource) {
                        Log.e("HistoryActivity", "Glide load failed for URL: " + url, e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target1, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        Log.d("HistoryActivity", "Glide image loaded: " + url);
                        return false;
                    }
                })
                .into(target);
    }

    // --- VIEW HOLDER & ADAPTER ---
    private static class ViewHolder {
        final TextView historyAlert;
        final TextView historyTime;
        final TextView historyRegion;
        final TextView historyOrchard;
        final TextView collectButton;
        final TextView deleteButton;
        final ImageView historyImage;
        final TextView viewImageText; // ADDED

        ViewHolder(View view) {
            historyAlert = view.findViewById(R.id.historyAlert);
            historyTime = view.findViewById(R.id.historyTime);
            historyRegion = view.findViewById(R.id.historyRegion);
            historyOrchard = view.findViewById(R.id.historyOrchard);
            collectButton = view.findViewById(R.id.collectButton);
            deleteButton = view.findViewById(R.id.deleteButton);
            historyImage = view.findViewById(R.id.historyImage);
            viewImageText = view.findViewById(R.id.viewImageText); // ADDED
        }
    }

    private class HistoryAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return filteredEvents.isEmpty() ? 1 : filteredEvents.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredEvents.isEmpty() ? null : filteredEvents.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (filteredEvents.isEmpty()) {
                TextView emptyView = new TextView(HistoryActivity.this);
                emptyView.setText("📭 No events yet");
                emptyView.setTextColor(ContextCompat.getColor(HistoryActivity.this, R.color.text_secondary));
                emptyView.setPadding(16, 40, 16, 40);
                emptyView.setTextSize(16f);
                emptyView.setGravity(android.view.Gravity.CENTER);
                return emptyView;
            }

            ViewHolder holder;
            if (convertView == null || convertView.getTag() == null) {
                convertView = LayoutInflater.from(HistoryActivity.this)
                        .inflate(R.layout.history_item, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            FallEvent event = filteredEvents.get(position);

            holder.historyAlert.setText(event.alert);
            holder.historyTime.setText("🕒 " + event.time);
            holder.historyRegion.setText("📍 " + event.region);
            holder.historyOrchard.setText("🌳 " + event.orchard);

            // --- BUTTON STATE LOGIC ---
            if (event.collected) {
                holder.collectButton.setText("Collected");
                holder.collectButton.setTextColor(ContextCompat.getColor(HistoryActivity.this, R.color.text_hint));
                holder.collectButton.setEnabled(false);
            } else {
                holder.collectButton.setText("Collect");
                holder.collectButton.setTextColor(ContextCompat.getColor(HistoryActivity.this, R.color.primary));
                holder.collectButton.setEnabled(true);
            }

            holder.collectButton.setOnClickListener(v -> collectEvent(event));
            holder.deleteButton.setOnClickListener(v -> confirmDeleteEvent(event));

            // Image Loading & Click Listeners
            if (event.photoUrl != null && !event.photoUrl.isEmpty() && !event.photoUrl.equals("null")) {
                holder.historyImage.setVisibility(View.VISIBLE);
                holder.viewImageText.setVisibility(View.VISIBLE); // Show text

                Glide.with(HistoryActivity.this)
                        .load(event.photoUrl)
                        .placeholder(android.R.drawable.ic_menu_camera)
                        .error(android.R.drawable.ic_menu_camera)
                        .centerCrop()
                        .into(holder.historyImage);

                // Create one listener for both the image and the text
                View.OnClickListener imageClickListener = v ->
                        ImageViewer.show(HistoryActivity.this, event.photoUrl);

                holder.historyImage.setOnClickListener(imageClickListener);
                holder.viewImageText.setOnClickListener(imageClickListener); // ADDED

            } else {
                holder.historyImage.setVisibility(View.GONE);
                holder.viewImageText.setVisibility(View.GONE);
                holder.historyImage.setOnClickListener(null);
                holder.viewImageText.setOnClickListener(null);
            }

            return convertView;
        }
    }

    private static class FallEvent {
        String id;
        String alert;
        String date;
        String time;
        String orchard;
        String region;
        String tree;
        String photoUrl;
        boolean collected;
    }
}