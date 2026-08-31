package com.spotify.bot;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(android.graphics.Color.parseColor("#090B0E"));
        layout.setPadding(50, 50, 50, 50);

        TextView titleView = new TextView(this);
        titleView.setText("Spotify Automation Bot");
        titleView.setTextSize(24);
        titleView.setTextColor(android.graphics.Color.parseColor("#1DB954"));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 50);

        TextView descView = new TextView(this);
        descView.setText("This app uses accessibility services to automate Spotify actions.");
        descView.setTextSize(16);
        descView.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
        descView.setGravity(Gravity.CENTER);
        descView.setPadding(0, 0, 0, 100);

        Button settingsButton = new Button(this);
        settingsButton.setText("Enable Accessibility Service");
        settingsButton.setBackgroundColor(android.graphics.Color.parseColor("#1DB954"));
        settingsButton.setTextColor(android.graphics.Color.BLACK);
        settingsButton.setPadding(40, 20, 40, 20);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        layout.addView(titleView);
        layout.addView(descView);
        layout.addView(settingsButton);

        setContentView(layout);
    }
}
