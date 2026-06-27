package com.example.duritor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsActivity extends DrawerActivity {

    private TextView totalFallsText;
    private TextView collectedText;
    private TextView pendingText;
    private TextView monthlyChartMonthText;
    private LinearLayout monthlyBarsContainer;
    private TextView monthlyBarsEmpty;

    private int orchardCount;
    private int regionCount;
    private int treeCount;
    private boolean orchardsLoaded;
    private boolean regionsLoaded;
    private boolean treesLoaded;
    private boolean fallsLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDrawerShell(R.layout.activity_analytics, R.id.nav_analytics, R.string.title_analytics);

        totalFallsText = findViewById(R.id.totalFallsText);
        collectedText = findViewById(R.id.collectedText);
        pendingText = findViewById(R.id.pendingText);
        monthlyChartMonthText = findViewById(R.id.monthlyChartMonthText);
        monthlyBarsContainer = findViewById(R.id.monthlyBarsContainer);
        monthlyBarsEmpty = findViewById(R.id.monthlyBarsEmpty);

        loadAnalytics();
    }

    private void loadAnalytics() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        database.getReference("orchards").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                orchardCount = (int) snapshot.getChildrenCount();
                orchardsLoaded = true;
                maybeUpdateSummary();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        database.getReference("regions").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                regionCount = (int) snapshot.getChildrenCount();
                regionsLoaded = true;
                maybeUpdateSummary();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        database.getReference("trees").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                treeCount = (int) snapshot.getChildrenCount();
                treesLoaded = true;
                maybeUpdateSummary();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        database.getReference("fallEvents").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int total = 0;
                int collected = 0;
                int pending = 0;
                Map<String, Integer> monthlyCollection = new HashMap<>();
                Date now = new Date();
                String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(now);
                String monthLabel = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now);

                for (DataSnapshot child : snapshot.getChildren()) {
                    total++;

                    Boolean isCollected = child.child("collected").getValue(Boolean.class);
                    if (isCollected != null && isCollected) {
                        collected++;

                        String date = child.child("date").getValue(String.class);
                        if (date != null && !date.isEmpty() && date.startsWith(currentMonth)) {
                            monthlyCollection.put(date, monthlyCollection.getOrDefault(date, 0) + 1);
                        }
                    } else {
                        pending++;
                    }
                }

                totalFallsText.setText(String.valueOf(total));
                collectedText.setText(String.valueOf(collected));
                pendingText.setText(String.valueOf(pending));

                monthlyChartMonthText.setText("Month: " + monthLabel);
                renderMonthlyBars(monthlyBarsContainer, monthlyBarsEmpty, monthlyCollection);

                fallsLoaded = true;
                maybeUpdateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AnalyticsActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void maybeUpdateSummary() {
        // Summary is not shown after removing the collection rate card.
    }

    private void renderMonthlyBars(LinearLayout container, TextView emptyView, Map<String, Integer> dailyCounts) {
        container.removeAllViews();

        if (dailyCounts.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        emptyView.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        Date now = new Date();
        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(now);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, Integer.parseInt(currentMonth.split("-")[0]));
        calendar.set(Calendar.MONTH, Integer.parseInt(currentMonth.split("-")[1]) - 1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        int max = 0;
        for (int count : dailyCounts.values()) {
            max = Math.max(max, count);
        }
        if (max == 0) {
            max = 1;
        }

        // Maximum height for bars (in dp)
        int maxBarHeight = 100;

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (int day = 1; day <= daysInMonth; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);
            String dateStr = dateFormat.format(calendar.getTime());
            int count = dailyCounts.getOrDefault(dateStr, 0);

            View bar = inflater.inflate(R.layout.analytics_monthly_bar_row, container, false);

            TextView countLabel = bar.findViewById(R.id.monthlyBarCount);
            TextView label = bar.findViewById(R.id.monthlyBarLabel);
            View fill = bar.findViewById(R.id.monthlyBarFill);

            label.setText(String.valueOf(day));
            countLabel.setText(String.valueOf(count));

            // Calculate height based on max count
            int finalMax = max;
            ViewGroup.LayoutParams params = fill.getLayoutParams();
            params.height = (int) (maxBarHeight * (count / (float) finalMax));
            fill.setLayoutParams(params);

            container.addView(bar);
        }
    }

}