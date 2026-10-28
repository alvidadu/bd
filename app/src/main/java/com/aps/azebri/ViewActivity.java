package com.aps.azebri;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.airbnb.lottie.LottieAnimationView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ViewActivity extends AppCompatActivity {

    private Toolbar _toolbar;
    private DrawerLayout _drawer;
    private ListView ListView1;
    private SharedPreferences Save, User;
    private ArrayList<HashMap<String, Object>> allListMap = new ArrayList<>();
    private ArrayList<HashMap<String, Object>> displayedList = new ArrayList<>();
    private ActionBarDrawerToggle _toggle;
    private LottieAnimationView lottie1;
    private LinearLayout layoutEmpty;

    private TextView tvTotalSms, tvServiceStatus;
    private SwitchCompat switchService;
    private EditText etSearch;

    // Header Toolbar Buttons
    private ImageView btnActionAdd, btnActionClear;

    // Floating Action Button
    private ExtendedFloatingActionButton fabAddSms;

    // Filter Chips
    private TextView chipAll, chipBkash, chipNagad, chipRocket, chipOthers;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        setContentView(R.layout.view);

        try {
            _toolbar = findViewById(R.id._toolbar);
            _drawer = findViewById(R.id._drawer);
            ListView1 = findViewById(R.id.ListView1);
            lottie1 = findViewById(R.id.lottie1);
            layoutEmpty = findViewById(R.id.layout_empty);

            tvTotalSms = findViewById(R.id.tv_total_sms);
            tvServiceStatus = findViewById(R.id.tv_service_status);
            switchService = findViewById(R.id.switch_service);
            etSearch = findViewById(R.id.et_search);

            btnActionAdd = findViewById(R.id.btn_action_add);
            btnActionClear = findViewById(R.id.btn_action_clear);
            fabAddSms = findViewById(R.id.fab_add_sms);

            chipAll = findViewById(R.id.chip_all);
            chipBkash = findViewById(R.id.chip_bkash);
            chipNagad = findViewById(R.id.chip_nagad);
            chipRocket = findViewById(R.id.chip_rocket);
            chipOthers = findViewById(R.id.chip_others);

            ListView1.setDivider(null);
            ListView1.setDividerHeight(0);

            Save = getSharedPreferences("Save", Activity.MODE_PRIVATE);
            User = getSharedPreferences("User", Activity.MODE_PRIVATE);

            setSupportActionBar(_toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }

            _toggle = new ActionBarDrawerToggle(this, _drawer, _toolbar, R.string.app_name, R.string.app_name);
            _drawer.addDrawerListener(_toggle);
            _toggle.syncState();

            setupServiceToggle();
            setupFilterChips();
            setupSearchListener();
            setupActionButtons();

            loadData();
            startSmsService();

            new Handler(Looper.getMainLooper()).postDelayed(this::setupDrawerButtons, 500);

        } catch (Exception e) {
            Toast.makeText(this, "UI Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupActionButtons() {
        if (btnActionAdd != null) {
            btnActionAdd.setOnClickListener(v -> showAddSmsDialog());
        }
        if (fabAddSms != null) {
            fabAddSms.setOnClickListener(v -> showAddSmsDialog());
        }
        if (btnActionClear != null) {
            btnActionClear.setOnClickListener(v -> showClearDialog());
        }
    }

    private void showAddSmsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_sms, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Spinner spSender = dialogView.findViewById(R.id.sp_sender_channel);
        EditText etBody = dialogView.findViewById(R.id.et_manual_body);
        CheckBox cbSync = dialogView.findViewById(R.id.cb_sync_server);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_add);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm_add);

        String[] channels = {"bKash", "Nagad", "Rocket", "Upay", "Cellfin", "SureCash"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, channels);
        spSender.setAdapter(adapter);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String sender = spSender.getSelectedItem().toString();
            String body = etBody.getText().toString().trim();

            if (body.isEmpty()) {
                etBody.setError("এসএমএস টেক্সট প্রদান করুন");
                etBody.requestFocus();
                return;
            }

            // Save Locally
            HashMap<String, Object> newMsg = new HashMap<>();
            newMsg.put("From", sender);
            newMsg.put("Body", body);
            allListMap.add(0, newMsg);
            Save.edit().putString("Json", new Gson().toJson(allListMap)).apply();

            // Sync to Server if checked
            if (cbSync.isChecked()) {
                syncManualSmsToServer(sender, body);
            }

            loadData();
            dialog.dismiss();
            Toast.makeText(this, "নতুন এসএমএস ডাটাবেজে যুক্ত করা হয়েছে!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void syncManualSmsToServer(String from, String body) {
        try {
            RequestQueue queue = Volley.newRequestQueue(this);
            String url = "https://payment.st-shop.xyz/api/add-data";

            Map<String, String> params = new HashMap<>();
            params.put("user_email", User.getString("email", ""));
            params.put("device_key", User.getString("device_key", ""));
            params.put("device_ip", User.getString("device_ip", ""));
            params.put("address", from);
            params.put("message", body.replaceAll("\r?\n", " "));

            StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                    response -> Toast.makeText(this, "ক্লাউড সার্ভারে সিঙ্ক সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show(),
                    error -> Log.e("VolleyError", error.toString())
            ) {
                @Override
                protected Map<String, String> getParams() {
                    return params;
                }
            };
            queue.add(stringRequest);
        } catch (Exception ignored) {}
    }

    private void setupServiceToggle() {
        if (switchService != null) {
            switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    startSmsService();
                    tvServiceStatus.setText("চালু আছে");
                    tvServiceStatus.setTextColor(Color.parseColor("#10B981"));
                    Toast.makeText(this, "এসএমএস রিডার সার্ভিস চালু করা হয়েছে", Toast.LENGTH_SHORT).show();
                } else {
                    stopSmsService();
                    tvServiceStatus.setText("বন্ধ আছে");
                    tvServiceStatus.setTextColor(Color.parseColor("#EF4444"));
                    Toast.makeText(this, "এসএমএস রিডার সার্ভিস বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupFilterChips() {
        View.OnClickListener chipListener = v -> {
            resetChipStyles();
            int id = v.getId();
            if (id == R.id.chip_all) {
                currentFilter = "ALL";
                chipAll.setBackgroundResource(R.drawable.filter_chip_selected);
                chipAll.setTextColor(Color.WHITE);
            } else if (id == R.id.chip_bkash) {
                currentFilter = "BKASH";
                chipBkash.setBackgroundResource(R.drawable.filter_chip_selected);
                chipBkash.setTextColor(Color.WHITE);
            } else if (id == R.id.chip_nagad) {
                currentFilter = "NAGAD";
                chipNagad.setBackgroundResource(R.drawable.filter_chip_selected);
                chipNagad.setTextColor(Color.WHITE);
            } else if (id == R.id.chip_rocket) {
                currentFilter = "ROCKET";
                chipRocket.setBackgroundResource(R.drawable.filter_chip_selected);
                chipRocket.setTextColor(Color.WHITE);
            } else if (id == R.id.chip_others) {
                currentFilter = "OTHERS";
                chipOthers.setBackgroundResource(R.drawable.filter_chip_selected);
                chipOthers.setTextColor(Color.WHITE);
            }
            filterData(etSearch != null ? etSearch.getText().toString().trim() : "");
        };

        if (chipAll != null) chipAll.setOnClickListener(chipListener);
        if (chipBkash != null) chipBkash.setOnClickListener(chipListener);
        if (chipNagad != null) chipNagad.setOnClickListener(chipListener);
        if (chipRocket != null) chipRocket.setOnClickListener(chipListener);
        if (chipOthers != null) chipOthers.setOnClickListener(chipListener);
    }

    private void resetChipStyles() {
        int unselectedText = Color.parseColor("#475569");
        if (chipAll != null) { chipAll.setBackgroundResource(R.drawable.filter_chip_unselected); chipAll.setTextColor(unselectedText); }
        if (chipBkash != null) { chipBkash.setBackgroundResource(R.drawable.filter_chip_unselected); chipBkash.setTextColor(unselectedText); }
        if (chipNagad != null) { chipNagad.setBackgroundResource(R.drawable.filter_chip_unselected); chipNagad.setTextColor(unselectedText); }
        if (chipRocket != null) { chipRocket.setBackgroundResource(R.drawable.filter_chip_unselected); chipRocket.setTextColor(unselectedText); }
        if (chipOthers != null) { chipOthers.setBackgroundResource(R.drawable.filter_chip_unselected); chipOthers.setTextColor(unselectedText); }
    }

    private void setupSearchListener() {
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterData(s.toString().trim());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void filterData(String query) {
        displayedList.clear();
        String lowerQuery = query.toLowerCase();

        for (HashMap<String, Object> map : allListMap) {
            String from = String.valueOf(map.get("From")).toLowerCase();
            String body = String.valueOf(map.get("Body")).toLowerCase();

            boolean matchesChannel = true;
            if ("BKASH".equals(currentFilter)) matchesChannel = from.contains("bkash");
            else if ("NAGAD".equals(currentFilter)) matchesChannel = from.contains("nagad");
            else if ("ROCKET".equals(currentFilter)) matchesChannel = from.contains("rocket");
            else if ("OTHERS".equals(currentFilter)) matchesChannel = !from.contains("bkash") && !from.contains("nagad") && !from.contains("rocket");

            boolean matchesQuery = query.isEmpty() || from.contains(lowerQuery) || body.contains(lowerQuery);

            if (matchesChannel && matchesQuery) {
                displayedList.add(map);
            }
        }

        updateUI();
    }

    private void updateUI() {
        if (displayedList.size() > 0) {
            ListView1.setVisibility(View.VISIBLE);
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
            ListView1.setAdapter(new CustomAdapter(displayedList));
        } else {
            ListView1.setVisibility(View.GONE);
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (lottie1 != null) {
                lottie1.setAnimation("Revenue.json");
                lottie1.playAnimation();
            }
        }
    }

    private void startSmsService() {
        try {
            Intent serviceIntent = new Intent(this, NotificationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
            else startService(serviceIntent);
        } catch (Exception ignored) {}
    }

    private void stopSmsService() {
        try {
            stopService(new Intent(this, NotificationService.class));
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (_toggle != null && _toggle.onOptionsItemSelected(item)) return true;
        return super.onOptionsItemSelected(item);
    }

    private void setupDrawerButtons() {
        try {
            TextView emailDisplay = findViewById(R.id.user_email_display);
            TextView deviceIdDisplay = findViewById(R.id.user_device_id_display);
            LinearLayout btnHome = findViewById(R.id.btn_home);
            LinearLayout btnDrawerAdd = findViewById(R.id.btn_drawer_add);
            LinearLayout btnClear = findViewById(R.id.btn_clear);
            LinearLayout btnLogout = findViewById(R.id.btn_logout);
            LinearLayout btnContact = findViewById(R.id.btn_contact);
            LinearLayout btnPrivacy = findViewById(R.id.btn_privacy);

            if (emailDisplay != null) {
                emailDisplay.setText(User.getString("email", "APS Merchant Account"));
            }

            if (deviceIdDisplay != null) {
                String devId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                deviceIdDisplay.setText("Device ID: " + (devId != null ? devId : "Unknown"));
            }

            if (btnHome != null) btnHome.setOnClickListener(v -> {
                loadData();
                _drawer.closeDrawer(GravityCompat.START);
                Toast.makeText(this, "ড্যাশবোর্ড রিফ্রেশ হয়েছে", Toast.LENGTH_SHORT).show();
            });

            if (btnDrawerAdd != null) btnDrawerAdd.setOnClickListener(v -> {
                _drawer.closeDrawer(GravityCompat.START);
                showAddSmsDialog();
            });

            if (btnContact != null) btnContact.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/8801315586282")));
            });

            if (btnPrivacy != null) btnPrivacy.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://payment.st-shop.xyz/")));
            });

            if (btnClear != null) btnClear.setOnClickListener(v -> {
                _drawer.closeDrawer(GravityCompat.START);
                showClearDialog();
            });

            if (btnLogout != null) btnLogout.setOnClickListener(v -> {
                _drawer.closeDrawer(GravityCompat.START);
                stopSmsService();
                User.edit().clear().apply();
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        } catch (Exception ignored) {}
    }

    private void showClearDialog() {
        new AlertDialog.Builder(this)
                .setTitle("হিস্ট্রি ডিলিট করবেন?")
                .setMessage("সমস্ত লোকাল এসএমএস হিস্ট্রি স্থায়ীভাবে মুছে ফেলা হবে। আপনি কি নিশ্চিত?")
                .setPositiveButton("হ্যাঁ, ডিলিট করুন", (d, w) -> {
                    Save.edit().remove("Json").apply();
                    loadData();
                    Toast.makeText(this, "হিস্ট্রি সফলভাবে ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("না", null)
                .show();
    }

    private void loadData() {
        try {
            String json = Save.getString("Json", "[]");
            allListMap = new Gson().fromJson(json, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());

            if (allListMap == null) allListMap = new ArrayList<>();

            if (tvTotalSms != null) {
                tvTotalSms.setText(String.valueOf(allListMap.size()));
            }

            filterData(etSearch != null ? etSearch.getText().toString().trim() : "");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class CustomAdapter extends BaseAdapter {
        ArrayList<HashMap<String, Object>> _data;
        public CustomAdapter(ArrayList<HashMap<String, Object>> _arr) { _data = _arr; }
        @Override public int getCount() { return _data.size(); }
        @Override public Object getItem(int i) { return _data.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View _v, ViewGroup _c) {
            View v = getLayoutInflater().inflate(R.layout.saved, null);
            TextView from = v.findViewById(R.id.From);
            TextView body = v.findViewById(R.id.Body);
            ImageView btnCopy = v.findViewById(R.id.btn_copy_sms);
            View cardView = v.findViewById(R.id.card_sms_item);

            try {
                String sender = String.valueOf(_data.get(i).get("From"));
                String msgBody = String.valueOf(_data.get(i).get("Body"));

                from.setText(sender);
                body.setText(msgBody);

                // MFS Channel Custom Styling
                String sLower = sender.toLowerCase();
                if (sLower.contains("bkash")) {
                    from.setBackgroundColor(Color.parseColor("#FDF2F8"));
                    from.setTextColor(Color.parseColor("#DB2777"));
                } else if (sLower.contains("nagad")) {
                    from.setBackgroundColor(Color.parseColor("#FFF7ED"));
                    from.setTextColor(Color.parseColor("#EA580C"));
                } else if (sLower.contains("rocket")) {
                    from.setBackgroundColor(Color.parseColor("#FAF5FF"));
                    from.setTextColor(Color.parseColor("#9333EA"));
                } else {
                    from.setBackgroundColor(Color.parseColor("#F0FDF4"));
                    from.setTextColor(Color.parseColor("#16A34A"));
                }

                if (btnCopy != null) {
                    btnCopy.setOnClickListener(b -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("SMS Content", msgBody);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(ViewActivity.this, "মেসেজ কপি হয়েছে", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                if (cardView != null) {
                    cardView.setOnClickListener(c -> showMessageDetailsDialog(sender, msgBody));
                }

            } catch (Exception ignored) {}
            return v;
        }
    }

    private void showMessageDetailsDialog(String sender, String body) {
        new AlertDialog.Builder(this)
                .setTitle("লেনদেনের বিবরণ (" + sender + ")")
                .setMessage(body)
                .setPositiveButton("মেসেজ কপি করুন", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("SMS Content", body);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(ViewActivity.this, "ক্লিপবোর্ডে কপি সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বন্ধ করুন", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (_drawer != null && _drawer.isDrawerOpen(GravityCompat.START)) {
            _drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}