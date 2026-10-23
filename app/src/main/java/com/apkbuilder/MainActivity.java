package com.apkbuilder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Home screen (Apk Builder dashboard): real project cards, new/open/rename/duplicate/export/delete/import. */
public class MainActivity extends Activity {
    private static final int REQ_IMPORT_ZIP = 1001;

    private ProjectManager pm;
    private List<ProjectConfig> projects;
    private CardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pm = new ProjectManager(new File(getFilesDir(), "projects"));
        refresh();
        findViewById(R.id.fab).setOnClickListener(v -> newProject());
        findViewById(R.id.btnToolchain).setOnClickListener(v -> toolchain());
        findViewById(R.id.btnMenu).setOnClickListener(v -> showOverflowMenu());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // pick up anything changed by another screen (rename, new project, etc.)
    }

    private void refresh() {
        projects = pm.list();
        adapter = new CardAdapter();
        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> openProject(projects.get(i)));
        registerForContextMenu(list);
        findViewById(R.id.empty).setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.list).setVisibility(projects.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------- project actions

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (!(menuInfo instanceof AdapterView.AdapterContextMenuInfo)) return;
        int i = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
        addProjectMenuItems(menu, i);
    }

    /** Same action list whether reached by long-press (ContextMenu) or the card's own ⋮ button
     *  (AlertDialog) - one place defines what "manage this project" means. */
    private void addProjectMenuItems(android.view.Menu menu, int i) {
        menu.add("Open").setOnMenuItemClickListener(mi -> { openProject(projects.get(i)); return true; });
        menu.add("Rename").setOnMenuItemClickListener(mi -> rename(projects.get(i)));
        menu.add("Duplicate").setOnMenuItemClickListener(mi -> {
            try { pm.duplicate(projects.get(i)); }
            catch (Exception e) { Toast.makeText(this, "Duplicate failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
            refresh(); return true; });
        menu.add("Export").setOnMenuItemClickListener(mi -> { export(projects.get(i)); return true; });
        menu.add("Delete").setOnMenuItemClickListener(mi -> { confirmDelete(projects.get(i)); return true; });
    }

    private void showCardMenu(int position) {
        ProjectConfig cfg = projects.get(position);
        CharSequence[] labels = {"Open", "Rename", "Duplicate", "Export", "Delete"};
        new AlertDialog.Builder(this).setTitle(cfg.name).setItems(labels, (d, which) -> {
            switch (which) {
                case 0: openProject(cfg); break;
                case 1: rename(cfg); break;
                case 2:
                    try { pm.duplicate(cfg); } catch (Exception e) {
                        Toast.makeText(this, "Duplicate failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    refresh();
                    break;
                case 3: export(cfg); break;
                case 4: confirmDelete(cfg); break;
            }
        }).show();
    }

    private void confirmDelete(ProjectConfig cfg) {
        new AlertDialog.Builder(this).setTitle("Delete project?")
                .setMessage("\"" + cfg.name + "\" and everything inside it will be permanently deleted.")
                .setPositiveButton("Delete", (d, w) -> { pm.delete(cfg); refresh(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openProject(ProjectConfig cfg) {
        try {
            cfg.save();
            Intent it = new Intent(this, EditorActivity.class);
            it.putExtra("project_root", cfg.root.getAbsolutePath());
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private boolean rename(ProjectConfig cfg) {
        EditText et = new EditText(this);
        et.setText(cfg.name);
        new AlertDialog.Builder(this).setTitle("Rename Project")
                .setView(et).setPositiveButton("OK", (d, w) -> {
                    try { pm.rename(cfg, et.getText().toString()); refresh(); } catch (Exception e) { Toast.makeText(this, "Error: " + e, Toast.LENGTH_SHORT).show(); }
                }).setNegativeButton("Cancel", null).show();
        return true;
    }

    /** Real export: zips the whole project and hands it to the system share sheet. */
    private void export(ProjectConfig cfg) {
        try {
            File exportsDir = new File(getFilesDir(), "exports");
            exportsDir.mkdirs();
            File zip = new File(exportsDir, cfg.name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".zip");
            pm.export(cfg, zip);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/zip");
            share.putExtra(Intent.EXTRA_STREAM, ApkFileProvider.uriFor(zip));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Export " + cfg.name));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void newProject() {
        startActivity(new Intent(this, NewProjectActivity.class));
    }

    private void toolchain() {
        startActivity(new Intent(this, ToolchainActivity.class));
    }

    // ---------------------------------------------------------------- dashboard overflow menu

    private void showOverflowMenu() {
        CharSequence[] labels = {"Import Project", "Build Environment", "About Apk Builder"};
        new AlertDialog.Builder(this).setItems(labels, (d, which) -> {
            switch (which) {
                case 0: importProject(); break;
                case 1: toolchain(); break;
                case 2: showAbout(); break;
            }
        }).show();
    }

    private void importProject() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        try {
            startActivityForResult(intent, REQ_IMPORT_ZIP);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_ZIP || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        new Thread(() -> {
            File tmp = new File(getCacheDir(), "import_" + System.currentTimeMillis() + ".zip");
            try {
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    Util.copyStreamToFile(in, tmp);
                }
                ProjectConfig imported = pm.importZip(tmp);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Imported: " + imported.name, Toast.LENGTH_SHORT).show();
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                tmp.delete();
            }
        }).start();
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("About Apk Builder")
                .setMessage("Apk Builder - an on-device Android build environment.\n\n"
                        + "Real, on-device toolchain: ECJ (Java compiler), AAPT2, D8 (dexer), "
                        + "apksig (signing), zipalign - no PC or network required to build a debug APK.\n\n"
                        + "AppCompat/Material: real AndroidX libraries, bundled and linked at build time. "
                        + "Internet is used once to fetch a few small runtime-only dependencies "
                        + "(kotlin-stdlib etc.), then cached for offline builds.\n\n"
                        + "Not included in this build: cloud AI code assistant, Git integration, Firebase/"
                        + "AdMob/Google Maps consoles. These need network services and API keys this app "
                        + "doesn't have configured, so rather than show a button that doesn't work, they're "
                        + "left out for now.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------------------------------------------------------------- adapter

    private class CardAdapter extends BaseAdapter {
        private final SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());

        @Override public int getCount() { return projects.size(); }
        @Override public Object getItem(int i) { return projects.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView :
                    LayoutInflater.from(MainActivity.this).inflate(R.layout.item_project, parent, false);
            ProjectConfig cfg = projects.get(i);

            TextView avatar = v.findViewById(R.id.avatar);
            avatar.setText(cfg.name.isEmpty() ? "?" : cfg.name.substring(0, 1).toUpperCase(Locale.getDefault()));

            ((TextView) v.findViewById(R.id.name)).setText(cfg.name);
            ((TextView) v.findViewById(R.id.pkg)).setText(cfg.packageName);

            String modified = cfg.root != null && cfg.root.exists() ? fmt.format(new Date(cfg.root.lastModified())) : "-";
            ((TextView) v.findViewById(R.id.meta)).setText(
                    "Min " + cfg.minSdk + " \u00b7 Target " + cfg.targetSdk + " \u00b7 " + cfg.javaLevel + " \u00b7 " + modified);

            v.findViewById(R.id.open).setOnClickListener(btn -> openProject(cfg));
            v.findViewById(R.id.menu).setOnClickListener(btn -> showCardMenu(i));
            return v;
        }
    }
}
