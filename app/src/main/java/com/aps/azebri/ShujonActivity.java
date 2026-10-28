package com.aps.azebri;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseApp;
import java.util.ArrayList;
import java.util.List;

public class ShujonActivity extends AppCompatActivity {

    private SharedPreferences User;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable navigateRunnable;

    private ImageView splashAppIcon;
    private View pulseWave1, pulseWave2, ambientGlow, progressBarFill;
    private RelativeLayout progressTrack;
    private LinearLayout brandTextLayout, footerLayout;

    private final List<Animator> activeAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        setContentView(R.layout.shujon);

        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception ignored) {}

        setupImmersiveStatusBar();

        User = getSharedPreferences("User", Activity.MODE_PRIVATE);

        splashAppIcon = findViewById(R.id.splash_app_icon);
        pulseWave1 = findViewById(R.id.pulse_wave_1);
        pulseWave2 = findViewById(R.id.pulse_wave_2);
        ambientGlow = findViewById(R.id.ambient_glow);
        progressTrack = findViewById(R.id.progress_track);
        progressBarFill = findViewById(R.id.progress_bar_fill);
        brandTextLayout = findViewById(R.id.brand_text_layout);
        footerLayout = findViewById(R.id.footer_layout);

        // Run Unique Animations
        startUltraAnimations();

        // Navigate after 2.8 seconds
        navigateRunnable = this::moveNext;
        handler.postDelayed(navigateRunnable, 2800);
    }

    private void setupImmersiveStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#091124"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getWindow().setNavigationBarColor(Color.parseColor("#050811"));
            }
        }
    }

    private void startUltraAnimations() {
        // Initial Invisible States
        splashAppIcon.setScaleX(0.2f);
        splashAppIcon.setScaleY(0.2f);
        splashAppIcon.setAlpha(0f);

        brandTextLayout.setAlpha(0f);
        brandTextLayout.setTranslationY(40f);

        footerLayout.setAlpha(0f);

        // 1. Spring Elastic Icon Entry Animation
        splashAppIcon.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(900)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        startIconLevitation();
                        startRadarPulseWaves();
                    }
                })
                .start();

        // 2. Text Slide & Glow Entry
        brandTextLayout.animate()
                .alpha(1.0f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 3. Footer Fade In
        footerLayout.animate()
                .alpha(1.0f)
                .setDuration(600)
                .setStartDelay(600)
                .start();

        // 4. Sleek Neon Progress Bar Fill Animation
        progressTrack.post(() -> {
            int fullWidth = progressTrack.getWidth();
            ValueAnimator progressAnimator = ValueAnimator.ofInt(0, fullWidth);
            progressAnimator.setDuration(2400);
            progressAnimator.setStartDelay(300);
            progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            progressAnimator.addUpdateListener(animation -> {
                int animatedValue = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams params = progressBarFill.getLayoutParams();
                params.width = animatedValue;
                progressBarFill.setLayoutParams(params);
            });
            progressAnimator.start();
            activeAnimators.add(progressAnimator);
        });

        // 5. Breathing Ambient Glow
        ObjectAnimator glowAnimX = ObjectAnimator.ofFloat(ambientGlow, "scaleX", 0.9f, 1.25f);
        ObjectAnimator glowAnimY = ObjectAnimator.ofFloat(ambientGlow, "scaleY", 0.9f, 1.25f);
        glowAnimX.setDuration(1400);
        glowAnimY.setDuration(1400);
        glowAnimX.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimY.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimX.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimY.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimX.start();
        glowAnimY.start();
        activeAnimators.add(glowAnimX);
        activeAnimators.add(glowAnimY);
    }

    private void startIconLevitation() {
        ObjectAnimator floatAnim = ObjectAnimator.ofFloat(splashAppIcon, "translationY", 0f, -14f);
        floatAnim.setDuration(1200);
        floatAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        floatAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.start();
        activeAnimators.add(floatAnim);
    }

    private void startRadarPulseWaves() {
        if (pulseWave1 == null || pulseWave2 == null) return;

        pulseWave1.setVisibility(View.VISIBLE);
        pulseWave2.setVisibility(View.VISIBLE);

        createWaveAnimation(pulseWave1, 0);
        createWaveAnimation(pulseWave2, 600);
    }

    private void createWaveAnimation(View waveView, long delay) {
        waveView.setScaleX(1.0f);
        waveView.setScaleY(1.0f);
        waveView.setAlpha(0.8f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(waveView, "scaleX", 1.0f, 1.8f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(waveView, "scaleY", 1.0f, 1.8f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(waveView, "alpha", 0.8f, 0f);

        scaleX.setDuration(1500);
        scaleY.setDuration(1500);
        alpha.setDuration(1500);

        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);

        scaleX.setStartDelay(delay);
        scaleY.setStartDelay(delay);
        alpha.setStartDelay(delay);

        scaleX.start();
        scaleY.start();
        alpha.start();

        activeAnimators.add(scaleX);
        activeAnimators.add(scaleY);
        activeAnimators.add(alpha);
    }

    private void moveNext() {
        if (isFinishing() || isDestroyed()) return;

        Intent screen = new Intent();
        if (User.contains("Logged")) {
            screen.setClass(getApplicationContext(), ViewActivity.class);
        } else {
            screen.setClass(getApplicationContext(), MainActivity.class);
        }

        startActivity(screen);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (handler != null && navigateRunnable != null) {
            handler.removeCallbacks(navigateRunnable);
        }
        for (Animator anim : activeAnimators) {
            if (anim != null) anim.cancel();
        }
        activeAnimators.clear();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Prevent back press during splash
    }
}