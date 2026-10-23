package com.apkbuilder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;

/** Project build settings: SDK, Java, dexer, optimization, signing. */
public class SettingsActivity extends Activity {
    private ProjectConfig cfg;
    private EditText minSdk, maxSdk, versionName, versionCode, assetsPath, nativeLibsPath, localLibsPath;
    private EditText ksPath, ksAlias, ksPass, keyPass;
    private CheckBox stringFog, r8, proguard, customKeystore, libAppCompat, libMaterial;

    private static final int REQ_PICK_ICON = 1001;
    private ImageView iconPreview;
    private TextView iconStatus;
    private Bitmap pendingIcon;        // picked from gallery, applied when SAVE SETTINGS is tapped
    private boolean pendingReset;      // "Use default icon" tapped, applied on SAVE SETTINGS

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        String root = getIntent().getStringExtra("project_root");
        try {
            cfg = ProjectConfig.load(new File(root));
            minSdk = findViewById(R.id.minSdk);
            maxSdk = findViewById(R.id.maxSdk);
            versionName = findViewById(R.id.versionName);
            versionCode = findViewById(R.id.versionCode);
            assetsPath = findViewById(R.id.assetsPath);
            nativeLibsPath = findViewById(R.id.nativeLibsPath);
            localLibsPath = findViewById(R.id.localLibsPath);
            stringFog = findViewById(R.id.stringFog);
            r8 = findViewById(R.id.r8);
            proguard = findViewById(R.id.proguard);
            customKeystore = findViewById(R.id.customKeystore);
            ksPath = findViewById(R.id.ksPath);
            ksAlias = findViewById(R.id.ksAlias);
            ksPass = findViewById(R.id.ksPass);
            keyPass = findViewById(R.id.keyPass);

            minSdk.setText(String.valueOf(cfg.minSdk));
            maxSdk.setText(String.valueOf(cfg.maxSdk));
            versionName.setText(cfg.versionName);
            versionCode.setText(String.valueOf(cfg.versionCode));
            assetsPath.setText(cfg.assetsPath);
            nativeLibsPath.setText(cfg.nativeLibsPath);
            localLibsPath.setText(cfg.localLibsPath);
            stringFog.setChecked(cfg.stringFog);
            r8.setChecked(cfg.r8);
            proguard.setChecked(cfg.proguard);
            customKeystore.setChecked(cfg.customKeystore);
            ksPath.setText(cfg.ksPath);
            ksAlias.setText(cfg.ksAlias);
            ksPass.setText(cfg.ksPass);
            keyPass.setText(cfg.keyPass);

            libAppCompat = findViewById(R.id.libAppCompat);
            libMaterial = findViewById(R.id.libMaterial);
            libAppCompat.setChecked(cfg.appCompat || cfg.material);
            libMaterial.setChecked(cfg.material);
            // Material is built on top of AppCompat, so turning Material on also ticks AppCompat.
            libMaterial.setOnCheckedChangeListener((b, on) -> { if (on) libAppCompat.setChecked(true); });
            libAppCompat.setOnCheckedChangeListener((b, on) -> { if (!on) libMaterial.setChecked(false); });

            iconPreview = findViewById(R.id.iconPreview);
            iconStatus = findViewById(R.id.iconStatus);
            showCurrentIcon();
            findViewById(R.id.btnSelectIcon).setOnClickListener(v -> pickIcon());
            findViewById(R.id.btnResetIcon).setOnClickListener(v -> {
                pendingIcon = null;
                pendingReset = true;
                iconPreview.setImageResource(R.drawable.ic_launcher);
                iconStatus.setText("Default icon will be used. Tap SAVE SETTINGS to apply.");
            });

            findViewById(R.id.save).setOnClickListener(v -> save());
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showCurrentIcon() {
        java.io.File f = AppIconManager.previewFile(cfg);
        Bitmap b = f == null ? null : BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b != null) {
            iconPreview.setImageBitmap(b);
            iconStatus.setText("Custom icon is set. If you don't change it, this icon is used in the build.");
        } else {
            iconPreview.setImageResource(R.drawable.ic_launcher);
            iconStatus.setText("No icon selected: the default Android app icon is used in the build.");
        }
    }

    private void pickIcon() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Select your app icon"), REQ_PICK_ICON);
        } catch (Exception e) {
            Toast.makeText(this, "No gallery / file picker found", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_ICON || resultCode != RESULT_OK || data == null) return; // cancelled = nothing changes
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            Bitmap b = AppIconManager.decode(this, uri);
            pendingIcon = b;
            pendingReset = false;
            iconPreview.setImageBitmap(b);
            iconStatus.setText("Icon selected. Tap SAVE SETTINGS to apply it.");
        } catch (Exception e) {
            Toast.makeText(this, "Could not use this image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void save() {
        try {
            cfg.minSdk = Integer.parseInt(minSdk.getText().toString());
            cfg.maxSdk = Integer.parseInt(maxSdk.getText().toString());
            cfg.versionName = versionName.getText().toString();
            cfg.versionCode = Integer.parseInt(versionCode.getText().toString());
            cfg.assetsPath = assetsPath.getText().toString();
            cfg.nativeLibsPath = nativeLibsPath.getText().toString();
            cfg.localLibsPath = localLibsPath.getText().toString();
            cfg.javaLevel = ProjectConfig.JAVA_LEVEL;
            cfg.dexer = "D8";
            cfg.appCompat = libAppCompat.isChecked();
            cfg.material = libMaterial.isChecked();
            cfg.stringFog = stringFog.isChecked();
            cfg.r8 = r8.isChecked();
            cfg.proguard = proguard.isChecked();
            cfg.customKeystore = customKeystore.isChecked();
            cfg.ksPath = ksPath.getText().toString();
            cfg.ksAlias = ksAlias.getText().toString();
            cfg.ksPass = ksPass.getText().toString();
            cfg.keyPass = keyPass.getText().toString();
            cfg.save();
            if (pendingIcon != null) {
                AppIconManager.apply(cfg, pendingIcon);
            } else if (pendingReset) {
                AppIconManager.reset(cfg);
            }
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }
}
