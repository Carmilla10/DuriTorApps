package com.example.duritor;

import android.os.Bundle;

public class AboutActivity extends DrawerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDrawerShell(R.layout.activity_about, R.id.nav_about, R.string.title_about);
    }
}