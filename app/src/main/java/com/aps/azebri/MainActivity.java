package com.aps.azebri;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.FirebaseApp;
import java.util.HashMap;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private HashMap<String, Object> Map = new HashMap<>();
    private String Id = "";
    private String Status = "";
    private String Message = "";

    private LinearLayout linear1, linear2, linear3;
    private FrameLayout Done;
    private TextView Tv3, btnSupport;
    private CheckBox checkbox1;
    private LottieAnimationView lottie1;
    private EditText Email, Password;
    private ImageView btnTogglePassword;
    private ProgressBar Progress;

    private SharedPreferences User;
    private RequestNetwork WiFi;
    private RequestNetwork.RequestListener _WiFi_request_listener;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        setContentView(R.layout.main);
        FirebaseApp.initializeApp(this);
        initialize();
        initializeLogic();
    }

    private void initialize() {
        linear1 = findViewById(R.id.linear1);
        linear2 = findViewById(R.id.linear2);
        linear3 = findViewById(R.id.linear3);
        Done = findViewById(R.id.Done);
        checkbox1 = findViewById(R.id.checkbox1);
        lottie1 = findViewById(R.id.lottie1);
        Email = findViewById(R.id.Email);
        Password = findViewById(R.id.Password);
        btnTogglePassword = findViewById(R.id.btn_toggle_password);
        Tv3 = findViewById(R.id.Tv3);
        Progress = findViewById(R.id.Progress);
        btnSupport = findViewById(R.id.btn_support);

        User = getSharedPreferences("User", Activity.MODE_PRIVATE);
        WiFi = new RequestNetwork(this);

        Progress.setVisibility(View.GONE);

        _WiFi_request_listener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String _tag, String _response, HashMap<String, Object> _h) {
                setLoadingState(false);
                try {
                    JSONObject jo = new JSONObject(_response);
                    Status = jo.getString("status");
                    Message = jo.getString("message");

                    if ("1".equals(Status)) {
                        User.edit().putString("Logged", "True")
                                .putString("email", Email.getText().toString().trim())
                                .putString("device_key", Password.getText().toString().trim())
                                .putString("uid", Message)
                                .putString("device_ip", Id).apply();

                        Intent i = new Intent(MainActivity.this, ViewActivity.class);
                        startActivity(i);
                        finish();
                    } else {
                        SketchwareUtil.showMessage(getApplicationContext(), Message);
                    }
                } catch (Exception e) {
                    SketchwareUtil.showMessage(getApplicationContext(), "সার্ভার রেসপন্স ত্রুটিপূর্ণ!");
                }
            }

            @Override
            public void onErrorResponse(String _t, String _m) {
                setLoadingState(false);
                SketchwareUtil.showMessage(getApplicationContext(), "সার্ভারের সাথে সংযোগ স্থাপন সম্ভব হয়নি!");
            }
        };

        View.OnClickListener loginClick = v -> {
            String emailText = Email.getText().toString().trim();
            String passText = Password.getText().toString().trim();

            if (emailText.isEmpty()) {
                Email.setError("ইমেইল দেওয়া আবশ্যক");
                Email.requestFocus();
                return;
            }

            if (passText.isEmpty()) {
                Password.setError("ডিভাইস কি দিন");
                Password.requestFocus();
                return;
            }

            if (!checkbox1.isChecked()) {
                SketchwareUtil.showMessage(getApplicationContext(), "শর্তাবলী ও Privacy Policy-তে সম্মতি দিন");
                return;
            }

            setLoadingState(true);

            Map = new HashMap<>();
            Map.put("user_email", emailText);
            Map.put("device_key", passText);
            Map.put("device_ip", Id);
            WiFi.setParams(Map, RequestNetworkController.REQUEST_PARAM);
            WiFi.startRequestNetwork(RequestNetworkController.POST, "https://payment.st-shop.xyz/api/device-connect", "", _WiFi_request_listener);
        };

        Done.setOnClickListener(loginClick);
        if (linear3 != null) linear3.setOnClickListener(loginClick);

        if (btnTogglePassword != null) {
            btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        }

        if (btnSupport != null) {
            btnSupport.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/8801315586282")));
                } catch (Exception e) {
                    SketchwareUtil.showMessage(getApplicationContext(), "সাপোর্ট পেজ খোলা সম্ভব হয়নি");
                }
            });
        }
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            Password.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            btnTogglePassword.setColorFilter(Color.parseColor("#64748B"));
            isPasswordVisible = false;
        } else {
            Password.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnTogglePassword.setImageResource(R.drawable.ic_visibility_on);
            btnTogglePassword.setColorFilter(Color.parseColor("#0B63E5"));
            isPasswordVisible = true;
        }
        Password.setSelection(Password.getText().length());
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            Progress.setVisibility(View.VISIBLE);
            Tv3.setVisibility(View.GONE);
            Done.setEnabled(false);
            linear3.setEnabled(false);
        } else {
            Progress.setVisibility(View.GONE);
            Tv3.setVisibility(View.VISIBLE);
            Done.setEnabled(true);
            linear3.setEnabled(true);
        }
    }

    private void initializeLogic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        }

        if (User.contains("Logged")) {
            startActivity(new Intent(this, ViewActivity.class));
            finish();
            return;
        }

        Password.setTransformationMethod(PasswordTransformationMethod.getInstance());
        _ProgressColor(Progress, "#FFFFFF");

        try {
            Id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            Id = "device_" + System.currentTimeMillis();
        }

        _Permission();
        applyModernInputStyles();
        applyUltraButtonGradient();
        setupPrivacySpan();

        try {
            lottie1.setAnimation("Login.json");
            lottie1.playAnimation();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyModernInputStyles() {
        GradientDrawable normalState1 = new GradientDrawable();
        normalState1.setCornerRadius(16);
        normalState1.setColor(Color.parseColor("#F8FAFC"));
        normalState1.setStroke(2, Color.parseColor("#CBD5E1"));

        GradientDrawable normalState2 = new GradientDrawable();
        normalState2.setCornerRadius(16);
        normalState2.setColor(Color.parseColor("#F8FAFC"));
        normalState2.setStroke(2, Color.parseColor("#CBD5E1"));

        linear1.setBackground(normalState1);
        linear2.setBackground(normalState2);

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            GradientDrawable activeState = new GradientDrawable();
            activeState.setCornerRadius(16);
            if (hasFocus) {
                activeState.setColor(Color.parseColor("#FFFFFF"));
                activeState.setStroke(3, Color.parseColor("#0B63E5"));
            } else {
                activeState.setColor(Color.parseColor("#F8FAFC"));
                activeState.setStroke(2, Color.parseColor("#CBD5E1"));
            }
            if (v == Email) linear1.setBackground(activeState);
            else if (v == Password) linear2.setBackground(activeState);
        };

        Email.setOnFocusChangeListener(focusListener);
        Password.setOnFocusChangeListener(focusListener);
    }

    private void applyUltraButtonGradient() {
        // Fintech Brand Gradient (APS Royal Blue to Cyan Glow)
        GradientDrawable btnShape = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#0B63E5"), Color.parseColor("#0284C7")}
        );
        btnShape.setCornerRadius(16);

        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                btnShape,
                null
        );

        linear3.setBackground(ripple);
        linear3.setElevation(8);
    }

    private void setupPrivacySpan() {
        if (checkbox1.getButtonDrawable() != null) {
            checkbox1.getButtonDrawable().setColorFilter(Color.parseColor("#0B63E5"), PorterDuff.Mode.SRC_IN);
        }

        SpannableString ss = new SpannableString("আমি Privacy Policy পড়েছি এবং সম্মতি দিচ্ছি");

        ClickableSpan cs = new ClickableSpan() {
            @Override
            public void onClick(View w) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://payment.st-shop.xyz")));
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#0B63E5"));
                ds.setUnderlineText(true);
                ds.setFakeBoldText(true);
            }
        };

        ss.setSpan(cs, 5, 19, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        checkbox1.setText(ss);
        checkbox1.setMovementMethod(LinkMovementMethod.getInstance());
        checkbox1.setTextSize(12);
        checkbox1.setPadding(12, 0, 0, 0);
    }

    public void _ProgressColor(ProgressBar p, String c) {
        if (p != null && p.getIndeterminateDrawable() != null) {
            p.getIndeterminateDrawable().setColorFilter(Color.parseColor(c), PorterDuff.Mode.SRC_IN);
        }
    }

    public void _Permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.POST_NOTIFICATIONS
            }, 1001);
        } else {
            requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS}, 1001);
        }
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}