package com.pdfreader.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ReadingSessionActivity extends AppCompatActivity {

    private TextView timerHours;
    private TextView timerMinutes;
    private TextView timerSeconds;
    private TextView currentPage;
    private TextView sessionBookTitle;
    private TextView sessionBookAuthor;

    private Handler timerHandler;
    private Runnable timerRunnable;
    private long startTime;
    private boolean isRunning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_session);

        initViews();
        setupTimer();
        setupButtons();
    }

    private void initViews() {
        timerHours = findViewById(R.id.timer_hours);
        timerMinutes = findViewById(R.id.timer_minutes);
        timerSeconds = findViewById(R.id.timer_seconds);
        currentPage = findViewById(R.id.current_page);
        sessionBookTitle = findViewById(R.id.session_book_title);
        sessionBookAuthor = findViewById(R.id.session_book_author);

        // TODO: Load book info from intent
        sessionBookTitle.setText("The Great Gatsby");
        sessionBookAuthor.setText("F. Scott Fitzgerald");
        currentPage.setText("142");
    }

    private void setupTimer() {
        startTime = System.currentTimeMillis();
        timerHandler = new Handler(Looper.getMainLooper());

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    int seconds = (int) (elapsedTime / 1000) % 60;
                    int minutes = (int) ((elapsedTime / (1000 * 60)) % 60);
                    int hours = (int) ((elapsedTime / (1000 * 60 * 60)) % 24);

                    timerHours.setText(String.format("%02d", hours));
                    timerMinutes.setText(String.format("%02d", minutes));
                    timerSeconds.setText(String.format("%02d", seconds));

                    timerHandler.postDelayed(this, 1000);
                }
            }
        };

        timerHandler.post(timerRunnable);
    }

    private void setupButtons() {
        findViewById(R.id.pause_button).setOnClickListener(v -> {
            isRunning = !isRunning;
            // TODO: Update button text and state
        });

        findViewById(R.id.end_session_button).setOnClickListener(v -> {
            // TODO: Save session data
            finish();
        });

        findViewById(R.id.close_button).setOnClickListener(v -> {
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }
}
