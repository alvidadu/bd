package com.apkbuilder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Code editor: real nested project file tree (src/main/java/... on disk), syntax highlighting, save, build. */
public class EditorActivity extends Activity {

    /** One real node of the on-disk project tree, resolved fresh from disk every time. */
    private static class Node {
        File file;
        boolean isDir;
        int depth;
        boolean expanded;
        Node(File f, boolean dir, int depth) { this.file = f; this.isDir = dir; this.depth = depth; }
    }

    private static final List<String> ROOT_SKIP = Arrays.asList("build", "output", "settings");

    private ProjectConfig cfg;
    private EditText editor;
    private TextView filePath, projectName;
    private File currentFile;
    private SyntaxHighlighter highlighter;

    /** Which folders (by absolute path) are expanded. The tree itself is never cached -
     *  it is re-walked from disk every time this changes, so rename/create/delete can
     *  never leave a stale node behind. */
    private final Set<String> expandedPaths = new HashSet<>();
    private final List<Node> visible = new ArrayList<>();
    private TreeAdapter adapter;
    private View filePanel;
    private boolean dirty;
    private boolean loadingFile;

    private static final int REQ_IMPORT_FILE = 2001;
    private File importTarget; // folder that "Import file" was started from

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        String projectRoot = getIntent().getStringExtra("project_root");
        try {
            cfg = ProjectConfig.load(new File(projectRoot));
            // Projects created or last saved before Gradle-file generation existed won't have
            // settings.gradle/build.gradle on disk yet - opening the project is a safe, natural
            // point to backfill them, rather than making the user visit Settings first.
            try { GradleFilesGenerator.generate(cfg); } catch (Exception ignored) { }
            // Only fall back to the hand-written attrs.xml shim when the real AppCompat/
            // Material pack isn't available - see BuildManager for why generating both
            // would conflict.
            if ((cfg.appCompat || cfg.material) && RealAppCompatMaterial.load(this).isEmpty()) {
                MaterialCompatShim.ensureAttrs(cfg);
            }
            projectName = findViewById(R.id.projectName);
            projectName.setText(cfg.name);
            editor = findViewById(R.id.editor);
            filePath = findViewById(R.id.filePath);
            filePanel = findViewById(R.id.filePanel);

            ListView fileList = findViewById(R.id.fileList);
            adapter = new TreeAdapter();
            fileList.setAdapter(adapter);
            fileList.setOnItemClickListener((p, v, i, id) -> {
                Node n = visible.get(i);
                if (n.isDir) {
                    toggle(n.file);
                } else {
                    if (openFile(n.file)) filePanel.setVisibility(View.GONE);
                }
            });
            registerForContextMenu(fileList);

            findViewById(R.id.btnSave).setOnClickListener(v -> save());
            findViewById(R.id.btnSettings).setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class).putExtra("project_root", projectRoot)));
            findViewById(R.id.btnBuild).setOnClickListener(v -> build());
            findViewById(R.id.btnFiles).setOnClickListener(v -> toggleFilePanel());

            editor.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable e) {
                    if (loadingFile) return;
                    setDirty(true);
                }
            });

            expandedPaths.add(cfg.root.getAbsolutePath());
            autoExpandToFirstJava();
            rebuildVisible();
            openFile(findFirstJavaFile());
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ---------------------------------------------------------------- tree

    private boolean isSourceFile(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".java") || n.endsWith(".xml") || n.endsWith(".pro") || n.endsWith(".json")
            || n.endsWith(".gradle") || n.endsWith(".properties");
    }

    private File[] sortedChildren(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return new File[0];
        Arrays.sort(kids, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return kids;
    }

    /** Expands every folder on the way down to the first .java file, so a freshly
     *  opened project lands on real code instead of a collapsed empty tree. */
    private void autoExpandToFirstJava() {
        File first = findFirstJavaFile();
        if (first == null) return;
        File dir = first.getParentFile();
        while (dir != null) {
            expandedPaths.add(dir.getAbsolutePath());
            if (dir.equals(cfg.root)) break;
            dir = dir.getParentFile();
        }
    }

    private File findFirstJavaFile() {
        List<File> all = Util.find(cfg.javaDir(), ".java");
        return all.isEmpty() ? null : all.get(0);
    }

    /** Re-walks the real directory tree from disk and rebuilds the flattened visible
     *  list according to expandedPaths. Always reflects current disk state - there is
     *  no cached node graph that a rename/create/delete could leave stale. */
    private void rebuildVisible() {
        visible.clear();
        walk(cfg.root, 0, true);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void walk(File dir, int depth, boolean isRoot) {
        for (File k : sortedChildren(dir)) {
            if (isRoot && ROOT_SKIP.contains(k.getName())) continue;
            boolean isDir = k.isDirectory();
            Node n = new Node(k, isDir, depth + 1);
            n.expanded = isDir && expandedPaths.contains(k.getAbsolutePath());
            visible.add(n);
            if (n.expanded) walk(k, depth + 1, false);
        }
    }

    private void toggle(File folder) {
        String p = folder.getAbsolutePath();
        if (!expandedPaths.remove(p)) expandedPaths.add(p);
        rebuildVisible();
    }

    // ---------------------------------------------------------------- folder actions

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (!(menuInfo instanceof AdapterView.AdapterContextMenuInfo)) return;
        int i = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
        if (i < 0 || i >= visible.size()) return;
        Node n = visible.get(i);
        File f = n.file;
        menu.setHeaderTitle(f.getName());
        if (n.isDir) {
            // long-press on a folder
            menu.add("Folder name").setOnMenuItemClickListener(mi -> { showName(f, true); return true; });
            menu.add("Rename").setOnMenuItemClickListener(mi -> { renameEntry(f, true); return true; });
            menu.add("Create folder").setOnMenuItemClickListener(mi -> { createInside(f, true); return true; });
            menu.add("Create file").setOnMenuItemClickListener(mi -> { createInside(f, false); return true; });
            menu.add("Import file").setOnMenuItemClickListener(mi -> { importInto(f); return true; });
            menu.add("Delete").setOnMenuItemClickListener(mi -> { deleteFolder(f); return true; });
        } else {
            // long-press on a file
            menu.add("File name").setOnMenuItemClickListener(mi -> { showName(f, false); return true; });
            menu.add("Rename").setOnMenuItemClickListener(mi -> { renameEntry(f, false); return true; });
            menu.add("Delete").setOnMenuItemClickListener(mi -> { deleteFile(f); return true; });
        }
    }

    /** "Folder name" / "File name": shows the name and where it lives, with a Copy button. */
    private void showName(File f, boolean isDir) {
        String rel = relativePath(f);
        String info = (isDir ? "Folder name: " : "File name: ") + f.getName() + "\n\nPath: " + rel
                + (isDir ? "" : "\nSize: " + humanSize(f.length()));
        new AlertDialog.Builder(this).setTitle(isDir ? "Folder name" : "File name")
                .setMessage(info)
                .setPositiveButton("Copy name", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("name", f.getName()));
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
    }

    /** A new name must be a single path segment: no '/', not "." or "..". */
    private static boolean validName(String name) {
        return !name.isEmpty() && !name.equals(".") && !name.equals("..")
                && !name.contains("/") && !name.contains("\\") && name.indexOf('\0') < 0;
    }

    private void renameEntry(File f, boolean isDir) {
        EditText et = new EditText(this);
        et.setText(f.getName());
        et.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(isDir ? "Rename folder" : "Rename file")
                .setView(et)
                .setPositiveButton("OK", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.equals(f.getName())) return;
                    if (!validName(name)) {
                        Toast.makeText(this, "Invalid name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File dest = new File(f.getParentFile(), name);
                    if (dest.exists()) {
                        Toast.makeText(this, "\"" + name + "\" already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Keep unsaved edits: the open file may be this one or live inside this folder.
                    boolean isCurrent = currentFile != null && currentFile.equals(f);
                    boolean insideRenamed = isDir && currentFile != null
                            && currentFile.getAbsolutePath().startsWith(f.getAbsolutePath() + File.separator);
                    if ((isCurrent || insideRenamed) && dirty) save();
                    String relInside = insideRenamed
                            ? currentFile.getAbsolutePath().substring(f.getAbsolutePath().length()) : null;
                    if (!f.renameTo(dest)) {
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isDir) {
                        // re-key every expanded path that lived under the old folder path
                        String oldP = f.getAbsolutePath();
                        Set<String> moved = new HashSet<>();
                        for (String ep : expandedPaths) {
                            if (ep.equals(oldP) || ep.startsWith(oldP + File.separator)) moved.add(ep);
                        }
                        for (String ep : moved) {
                            expandedPaths.remove(ep);
                            expandedPaths.add(dest.getAbsolutePath() + ep.substring(oldP.length()));
                        }
                    }
                    if (isCurrent) currentFile = dest;
                    if (insideRenamed) currentFile = new File(dest.getAbsolutePath() + relInside);
                    if (currentFile != null && (isCurrent || insideRenamed)) setDirty(false);
                    rebuildVisible();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- import file

    private void importInto(File folder) {
        importTarget = folder;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "Import file into " + folder.getName()), REQ_IMPORT_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_FILE || resultCode != RESULT_OK || data == null || importTarget == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        File target = importTarget;
        int ok = 0;
        String lastErr = null;
        File lastDest = null;
        for (Uri u : uris) {
            try {
                lastDest = copyIn(u, target);
                ok++;
            } catch (Exception e) {
                lastErr = e.getMessage();
            }
        }
        expandedPaths.add(target.getAbsolutePath());
        rebuildVisible();
        if (ok > 0 && lastErr == null) {
            Toast.makeText(this, ok == 1 && lastDest != null ? "Imported: " + lastDest.getName() : "Imported " + ok + " files",
                    Toast.LENGTH_SHORT).show();
        } else if (ok > 0) {
            Toast.makeText(this, "Imported " + ok + ", failed: " + lastErr, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Import failed: " + lastErr, Toast.LENGTH_LONG).show();
        }
    }

    /** Copies the picked file into the folder; never overwrites (adds _1, _2... if the name is taken). */
    private File copyIn(Uri uri, File folder) throws Exception {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "imported_file";
        }
        name = name.replace('/', '_').replace('\\', '_').trim();
        if (!validName(name)) name = "imported_file";
        folder.mkdirs();
        File dest = new File(folder, name);
        if (dest.exists()) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            int n = 1;
            while (dest.exists()) dest = new File(folder, base + "_" + (n++) + ext);
        }
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("Cannot read the selected file");
            Util.copyStreamToFile(in, dest);
        } catch (Exception e) {
            dest.delete();
            throw e;
        }
        return dest;
    }

    private void deleteFile(File f) {
        new AlertDialog.Builder(this).setTitle("Delete file?")
                .setMessage("This will permanently delete \"" + f.getName() + "\".")
                .setPositiveButton("Delete", (d, w) -> {
                    boolean losingCurrent = currentFile != null && currentFile.equals(f);
                    if (!f.delete()) {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (losingCurrent) {
                        currentFile = null;
                        dirty = false;
                        File next = findFirstJavaFile();
                        if (next != null) openFile(next);
                        else { loadingFile = true; editor.setText(""); loadingFile = false; filePath.setText(""); }
                    }
                    rebuildVisible();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createInside(File folder, boolean asFolder) {
        EditText et = new EditText(this);
        new AlertDialog.Builder(this).setTitle(asFolder ? "New folder" : "New file")
                .setView(et)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File dest = new File(folder, name);
                    try {
                        for (String seg : name.split("[/\\\\]")) {
                            if (seg.equals("..")) throw new java.io.IOException("Invalid name");
                        }
                        if (dest.exists()) throw new java.io.IOException("Already exists");
                        if (asFolder) {
                            if (!dest.mkdirs()) throw new java.io.IOException("Could not create folder");
                        } else {
                            dest.getParentFile().mkdirs();
                            if (!dest.createNewFile()) throw new java.io.IOException("Could not create file");
                        }
                        expandedPaths.add(folder.getAbsolutePath());
                        rebuildVisible();
                        if (!asFolder) openFile(dest);
                    } catch (Exception e) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFolder(File folder) {
        new AlertDialog.Builder(this).setTitle("Delete folder?")
                .setMessage("This will permanently delete \"" + folder.getName() + "\" and everything inside it.")
                .setPositiveButton("Delete", (d, w) -> {
                    boolean losingCurrent = currentFile != null
                            && currentFile.getAbsolutePath().startsWith(folder.getAbsolutePath() + File.separator);
                    Util.deleteRec(folder);
                    expandedPaths.remove(folder.getAbsolutePath());
                    if (losingCurrent) {
                        currentFile = null;
                        dirty = false;
                        File next = findFirstJavaFile();
                        if (next != null) openFile(next);
                        else { editor.setText(""); filePath.setText(""); }
                    }
                    rebuildVisible();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- editor

    private void toggleFilePanel() {
        filePanel.setVisibility(filePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    /** Opens a text file in the editor. Binary files (images, fonts, ...) and huge files can't be edited. */
    private boolean openFile(File f) {
        if (f == null) return false;
        if (f.length() > 2L * 1024 * 1024 || looksBinary(f)) {
            Toast.makeText(this, f.getName() + " (" + humanSize(f.length()) + ") is not a text file - cannot be edited here",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (currentFile != null && dirty) save();
        currentFile = f;
        loadingFile = true;
        try {
            editor.setText(Util.readText(f));
            highlighter = new SyntaxHighlighter(editor, f.getName());
            highlighter.attach();
            if (adapter != null) adapter.notifyDataSetChanged();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e, Toast.LENGTH_SHORT).show();
        } finally {
            loadingFile = false;
        }
        setDirty(false);
        return true;
    }

    /** True if the first 4 KB contain a NUL byte (png, ttf, zip, ...). */
    private static boolean looksBinary(File f) {
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[4096];
            int n = in.read(b);
            for (int k = 0; k < n; k++) if (b[k] == 0) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private String relativePath(File f) {
        String base = cfg.root.getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.startsWith(base) ? p.substring(base.length() + 1) : p;
    }

    private void setDirty(boolean d) {
        dirty = d;
        if (currentFile != null) filePath.setText((d ? "\u25cf " : "") + relativePath(currentFile));
    }

    private void save() {
        if (currentFile == null) return;
        try {
            Util.writeAtomic(currentFile, editor.getText().toString());
            setDirty(false);
            Toast.makeText(this, "Saved: " + currentFile.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e, Toast.LENGTH_SHORT).show();
        }
    }

    private void build() {
        save();
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Building...");
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.setCancelable(true);
        pd.show();

        new Thread(() -> {
            try {
                BuildManager bm = new BuildManager(this, cfg);
                BuildResult res = bm.build();
                pd.dismiss();
                Intent it = new Intent(this, BuildActivity.class);
                it.putExtra("project_root", cfg.root.getAbsolutePath());
                it.putExtra("success", res.success);
                it.putExtra("apk_path", res.alignedApkPath != null ? res.alignedApkPath.getAbsolutePath() : null);
                it.putExtra("logs", res.logs);
                it.putExtra("build_time", res.buildTime);
                startActivity(it);
            } catch (Exception e) {
                pd.dismiss();
                Toast.makeText(EditorActivity.this, "Build error: " + e, Toast.LENGTH_LONG).show();
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (filePanel.getVisibility() == View.VISIBLE) {
            filePanel.setVisibility(View.GONE);
            return;
        }
        if (dirty) save();
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- adapter

    private class TreeAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int i) { return visible.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView :
                    LayoutInflater.from(EditorActivity.this).inflate(R.layout.item_file, parent, false);
            Node n = visible.get(i);
            TextView icon = v.findViewById(R.id.icon);
            TextView fname = v.findViewById(R.id.fname);
            TextView fdir = v.findViewById(R.id.fdir);

            String name = n.file.getName();
            if (n.isDir) {
                icon.setText(n.expanded ? "\u25be" : "\u25b8");
                fname.setText(name + "/");
                fname.setTextColor(getResources().getColor(R.color.text_secondary));
                fdir.setText("");
            } else {
                String lower = name.toLowerCase(Locale.ROOT);
                icon.setText(lower.endsWith(".java") ? "J" : lower.endsWith(".xml") ? "X"
                        : (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                           || lower.endsWith(".webp") || lower.endsWith(".gif")) ? "I" : "\u2022");
                fname.setText(name);
                boolean active = n.file.equals(currentFile);
                fname.setTextColor(getResources().getColor(active ? R.color.primary : R.color.text_primary));
                fdir.setText(relativePath(n.file));
            }
            v.setPaddingRelative(14 + n.depth * 16, v.getPaddingTop(), v.getPaddingEnd(), v.getPaddingBottom());
            boolean activeRow = !n.isDir && n.file.equals(currentFile);
            v.setBackgroundResource(activeRow ? R.drawable.bg_row_active : 0);
            return v;
        }
    }
}
