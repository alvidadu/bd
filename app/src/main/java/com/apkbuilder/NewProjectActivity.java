package com.apkbuilder;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

/** New project wizard: name, package, SDK, optional dependencies (Java 17 + D8 fixed). */
public class NewProjectActivity extends Activity {
    private EditText appName, pkgName, versionName, versionCode, minSdk, targetSdk, maxSdk;
    private CheckBox appCompat, material, stringFog, r8, proguard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_project);
        appName = findViewById(R.id.appName);
        pkgName = findViewById(R.id.pkgName);
        versionName = findViewById(R.id.versionName);
        versionCode = findViewById(R.id.versionCode);
        minSdk = findViewById(R.id.minSdk);
        targetSdk = findViewById(R.id.targetSdk);
        maxSdk = findViewById(R.id.maxSdk);
        appCompat = findViewById(R.id.appCompat);
        material = findViewById(R.id.material);
        stringFog = findViewById(R.id.stringFog);
        r8 = findViewById(R.id.r8);
        proguard = findViewById(R.id.proguard);

        findViewById(R.id.create).setOnClickListener(v -> create());
    }

    private void create() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.name = appName.getText().toString();
        cfg.packageName = pkgName.getText().toString();
        cfg.versionName = versionName.getText().toString();
        cfg.versionCode = Integer.parseInt(versionCode.getText().toString());
        cfg.minSdk = Integer.parseInt(minSdk.getText().toString());
        cfg.targetSdk = Integer.parseInt(targetSdk.getText().toString());
        cfg.maxSdk = Integer.parseInt(maxSdk.getText().toString());
        cfg.javaLevel = ProjectConfig.JAVA_LEVEL;
        cfg.dexer = "D8";
        cfg.appCompat = appCompat.isChecked();
        cfg.material = material.isChecked();
        cfg.stringFog = stringFog.isChecked();
        cfg.r8 = r8.isChecked();
        cfg.proguard = proguard.isChecked();

        try {
            ProjectManager pm = new ProjectManager(new File(getFilesDir(), "projects"));
            pm.create(cfg, null);
            Toast.makeText(this, "Project created", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }
}
