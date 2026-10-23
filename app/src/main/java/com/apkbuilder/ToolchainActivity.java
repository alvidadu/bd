package com.apkbuilder;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** "Build Environment" screen: shows toolchain status (green/red for each tool), import button. */
public class ToolchainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toolchain);
        ToolchainChecker tc = new ToolchainChecker(this);
        tc.ensureBundledToolchain();
        List<ToolStatus> tools = tc.check();

        ArrayAdapter<ToolStatus> adapter = new ArrayAdapter<ToolStatus>(this, R.layout.item_tool, tools) {
            @Override
            public android.view.View getView(int i, android.view.View cv, android.view.ViewGroup p) {
                if (cv == null) cv = android.view.LayoutInflater.from(getContext()).inflate(R.layout.item_tool, p, false);
                ToolStatus t = getItem(i);
                ((android.widget.TextView) cv.findViewById(R.id.name)).setText(t.name);
                ((android.widget.TextView) cv.findViewById(R.id.detail)).setText(t.detail);
                ((android.widget.TextView) cv.findViewById(R.id.status)).setText(t.ok ? "✓" : "✗");
                return cv;
            }
        };
        ((ListView) findViewById(R.id.list)).setAdapter(adapter);
        findViewById(R.id.importBtn).setOnClickListener(v -> Toast.makeText(this, "Toolchain import not yet implemented", Toast.LENGTH_SHORT).show());
    }
}
