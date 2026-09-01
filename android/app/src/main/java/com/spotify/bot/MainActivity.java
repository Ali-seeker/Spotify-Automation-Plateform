package com.spotify.bot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());

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
        titleView.setPadding(0, 0, 0, 30);

        TextView descView = new TextView(this);
        descView.setText("This app uses accessibility services to automate Spotify actions.");
        descView.setTextSize(15);
        descView.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
        descView.setGravity(Gravity.CENTER);
        descView.setPadding(0, 0, 0, 60);

        Button settingsButton = new Button(this);
        settingsButton.setText("Enable Accessibility Service");
        settingsButton.setBackgroundColor(android.graphics.Color.parseColor("#1DB954"));
        settingsButton.setTextColor(android.graphics.Color.BLACK);
        settingsButton.setPadding(40, 20, 40, 20);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        Button launchButton = new Button(this);
        launchButton.setText("Launch Spotify");
        launchButton.setBackgroundColor(android.graphics.Color.parseColor("#212529"));
        launchButton.setTextColor(android.graphics.Color.WHITE);
        launchButton.setPadding(40, 20, 40, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 30, 0, 0);
        launchButton.setLayoutParams(params);

        launchButton.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Initiating Spotify Launch...", Toast.LENGTH_SHORT).show();
            SpotifyLauncher.launchSpotify(MainActivity.this, (success, message) -> {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                });
            });
        });

        Button clickSearchButton = new Button(this);
        clickSearchButton.setText("Click Spotify Search");
        clickSearchButton.setBackgroundColor(android.graphics.Color.parseColor("#1DB954"));
        clickSearchButton.setTextColor(android.graphics.Color.BLACK);
        clickSearchButton.setPadding(40, 20, 40, 20);

        LinearLayout.LayoutParams clickParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        clickParams.setMargins(0, 30, 0, 0);
        clickSearchButton.setLayoutParams(clickParams);

        clickSearchButton.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Opening Spotify & Clicking Search...", Toast.LENGTH_SHORT).show();
            SpotifyLauncher.launchSpotify(MainActivity.this, (launchSuccess, launchMsg) -> {
                if (launchSuccess) {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    SpotifyClicker.clickSearch((clickSuccess, clickMsg) -> {
                        mainHandler.post(() -> {
                            Toast.makeText(MainActivity.this, clickMsg, clickSuccess ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                        });
                    });
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Launch failed: " + launchMsg, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        layout.addView(titleView);
        layout.addView(descView);
        layout.addView(settingsButton);
        layout.addView(launchButton);
        layout.addView(clickSearchButton);

        setContentView(layout);
    }
}
