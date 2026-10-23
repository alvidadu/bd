package com.apkbuilder;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Shows real build output in real time, final success/failure, option to install APK. */
public class BuildActivity extends Activity {

    /** Stable substring DxRunner uses for this specific, well-documented DX limitation
     *  (DX cannot dex invokedynamic lambdas below minSdk 26, unlike D8 which desugars them). */
    private static final String LAMBDA_MINSDK_MARKER = "DX can only dex when minSdkVersion is 26 or higher";

    private String projectRoot;
    private String logs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_build);
        render(getIntent());
    }

    private void render(Intent in) {
        projectRoot = in.getStringExtra("project_root");
        boolean success = in.getBooleanExtra("success", false);
        String apkPath = in.getStringExtra("apk_path");
        logs = in.getStringExtra("logs");
        long time = in.getLongExtra("build_time", 0);

        TextView stage = findViewById(R.id.stage);
        stage.setText(success ? "\u2713 Build Succeeded" : "\u2717 Build Failed");
        stage.setTextColor(getResources().getColor(success ? R.color.success : R.color.error));

        TextView output = findViewById(R.id.output);
        String header = success
                ? "Build finished in " + time + "ms\n\n"
                : "Build FAILED after " + time + "ms\n\n";
        output.setText(header + (logs == null || logs.trim().isEmpty() ? "(no output was produced)" : logs));
        ((ScrollView) findViewById(R.id.output).getParent()).post(() ->
            ((ScrollView) findViewById(R.id.output).getParent()).fullScroll(ScrollView.FOCUS_DOWN));

        findViewById(R.id.copyLog).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("build_log", logs));
            Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        Button install = findViewById(R.id.install);
        if (success && apkPath != null) {
            install.setVisibility(View.VISIBLE);
            install.setOnClickListener(v -> ApkInstaller.install(this, new File(apkPath)));
        } else {
            install.setVisibility(View.GONE);
        }

        Button fix = findViewById(R.id.fixMinSdk);
        boolean offerFix = !success && projectRoot != null && logs != null && logs.contains(LAMBDA_MINSDK_MARKER);
        fix.setVisibility(offerFix ? View.VISIBLE : View.GONE);
        if (offerFix) fix.setOnClickListener(v -> raiseMinSdkAndRebuild());

        findViewById(R.id.cancel).setOnClickListener(v -> finish());
    }

    /** Actually edits the project's Min SDK to 26 and saves it - not just a suggestion -
     *  then re-runs the real build pipeline and re-renders this same screen with the
     *  new result, exactly like a normal build. */
    private void raiseMinSdkAndRebuild() {
        Button fix = findViewById(R.id.fixMinSdk);
        fix.setEnabled(false);
        fix.setText("REBUILDING...");
        TextView stage = findViewById(R.id.stage);
        stage.setText("Rebuilding with Min SDK 26...");
        stage.setTextColor(getResources().getColor(R.color.primary));

        new Thread(() -> {
            try {
                ProjectConfig cfg = ProjectConfig.load(new File(projectRoot));
                cfg.minSdk = 26;
                if (cfg.targetSdk < 26) cfg.targetSdk = 26;
                cfg.save();

                BuildManager bm = new BuildManager(this, cfg);
                BuildResult res = bm.build();

                runOnUiThread(() -> {
                    Intent it = new Intent(this, BuildActivity.class);
                    it.putExtra("project_root", cfg.root.getAbsolutePath());
                    it.putExtra("success", res.success);
                    it.putExtra("apk_path", res.alignedApkPath != null ? res.alignedApkPath.getAbsolutePath() : null);
                    it.putExtra("logs", res.logs);
                    it.putExtra("build_time", res.buildTime);
                    render(it);
                    fix.setEnabled(true);
                    fix.setText("RAISE MIN SDK TO 26 & REBUILD");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e, Toast.LENGTH_LONG).show();
                    fix.setEnabled(true);
                    fix.setText("RAISE MIN SDK TO 26 & REBUILD");
                });
            }
        }).start();
    }
}
