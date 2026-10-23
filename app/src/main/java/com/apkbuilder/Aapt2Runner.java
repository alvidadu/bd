package com.apkbuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real AAPT2 compile + link. Runs the actual aapt2 binary (imported toolchain, or the one
 * bundled in jniLibs/<abi>/libaapt2.so, exposed at runtime as a plain executable file).
 */
public class Aapt2Runner {
    private final File aapt2;

    public Aapt2Runner(File aapt2) { this.aapt2 = aapt2; }

    public String unavailableReason() {
        if (aapt2 == null || !aapt2.isFile()) return "AAPT2 not found";
        if (!aapt2.canExecute() && !aapt2.setExecutable(true)) return "AAPT2 is not executable on this device";
        return null;
    }

    /** Compiles every res/ subfolder into <compiledDir>/*.flat files. */
    public void compile(File resDir, File compiledDir, BuildLogger log, AtomicReference<Process> holder) throws BuildException {
        String bad = unavailableReason();
        if (bad != null) throw new BuildException("Resources", bad);
        compiledDir.mkdirs();
        List<String> cmd = new ArrayList<>();
        cmd.add(aapt2.getAbsolutePath());
        cmd.add("compile");
        cmd.add("--dir");
        cmd.add(resDir.getAbsolutePath());
        cmd.add("-o");
        cmd.add(compiledDir.getAbsolutePath());
        ToolRunner.Result r = ToolRunner.run(cmd, null, log, "AAPT2", holder);
        if (r.code != 0) {
            throw new BuildException("Resources", "AAPT2 compile failed (exit " + r.code + ")",
                    BuildErrorParser.parseAapt2(r.lines, "Resources"));
        }
    }

    /** Links compiled resources + manifest into resources.apk (contains resources.arsc, R data), and writes R.java. */
    public void link(File compiledDir, File manifest, File androidJar, File outApk, File rJavaOutDir,
                     int minSdk, int targetSdk, String versionName, int versionCode,
                     BuildLogger log, AtomicReference<Process> holder) throws BuildException {
        link(compiledDir, java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                manifest, androidJar, outApk, rJavaOutDir, minSdk, targetSdk, versionName, versionCode, log, holder);
    }

    /**
     * Same as above, but also links in one or more library resource sets (each its own
     * compiled-.flat directory, e.g. from AppCompat/Material) via "--extra-packages", exactly
     * how a real Android build merges library resources into the final app: AAPT2 assigns every
     * resource - the app's own and every library's - a single, stable ID in one pass, and
     * writes a separate R.java per package (one for the app, one per extra package) into
     * rJavaOutDir. See RealAppCompatMaterial for why this works cleanly: modern AndroidX AARs
     * never bundle a compiled R.class of their own, so there's nothing stale to conflict with
     * these freshly generated ones.
     */
    public void link(File compiledDir, List<File> extraCompiledDirs, List<String> extraPackages,
                     File manifest, File androidJar, File outApk, File rJavaOutDir,
                     int minSdk, int targetSdk, String versionName, int versionCode,
                     BuildLogger log, AtomicReference<Process> holder) throws BuildException {
        String bad = unavailableReason();
        if (bad != null) throw new BuildException("Resources", bad);
        rJavaOutDir.mkdirs();
        List<String> cmd = new ArrayList<>();
        cmd.add(aapt2.getAbsolutePath());
        cmd.add("link");
        cmd.add("-I");
        cmd.add(androidJar.getAbsolutePath());
        cmd.add("--manifest");
        cmd.add(manifest.getAbsolutePath());
        cmd.add("--min-sdk-version");
        cmd.add(String.valueOf(minSdk));
        cmd.add("--target-sdk-version");
        cmd.add(String.valueOf(targetSdk));
        cmd.add("--version-name");
        cmd.add(versionName);
        cmd.add("--version-code");
        cmd.add(String.valueOf(versionCode));
        cmd.add("--auto-add-overlay");
        if (!extraPackages.isEmpty()) {
            cmd.add("--extra-packages");
            cmd.add(String.join(":", extraPackages));
        }
        cmd.add("--java");
        cmd.add(rJavaOutDir.getAbsolutePath());
        cmd.add("-o");
        cmd.add(outApk.getAbsolutePath());
        // Library resource sets go in as OVERLAYS (-R), exactly like Gradle does. As plain
        // positional inputs, AAPT2 treats two definitions of the same resource as a hard
        // conflict - and AppCompat and Material both declare <declare-styleable name="SearchView">
        // (and other shared names), which produced "styleable/SearchView has a conflicting
        // value for configuration ()". In overlay mode AAPT2 merges same-named styleables and
        // lets the later overlay win for everything else.
        // Priority is "later wins": RealAppCompatMaterial lists the highest-level libraries
        // first (appcompat, material, ... core, ... tracing), so add them in reverse so the
        // low-level ones are the base, then the project's own resources last so they always
        // win over any library resource.
        for (int i = extraCompiledDirs.size() - 1; i >= 0; i--) addFlats(cmd, extraCompiledDirs.get(i), true);
        addFlats(cmd, compiledDir, !extraCompiledDirs.isEmpty());
        ToolRunner.Result r = ToolRunner.run(cmd, null, log, "AAPT2", holder);
        if (r.code != 0 || !outApk.exists()) {
            List<BuildResult.BuildError> errors = BuildErrorParser.parseAapt2(r.lines, "Resources");
            String libHint = libraryAttrHint(errors);
            String msg = "AAPT2 link failed (exit " + r.code + ")" + (libHint != null ? "\n\n" + libHint : "");
            throw new BuildException("Resources", msg, errors);
        }
    }

    private void addFlats(List<String> cmd, File compiledDir, boolean overlay) {
        File[] flats = compiledDir.listFiles((d, n) -> n.endsWith(".flat"));
        if (flats == null) return;
        java.util.Arrays.sort(flats); // deterministic order
        for (File f : flats) {
            if (overlay) cmd.add("-R");
            cmd.add(f.getAbsolutePath());
        }
    }

    /** "attribute X not found" for an attribute that only exists via AppCompat/Material's own
     *  attrs.xml is the single most common way people discover this builder can't resolve
     *  those libraries' resources (see GradleFilesGenerator) - worth naming directly instead
     *  of leaving someone to guess why a perfectly-spelled attribute "doesn't exist". */
    private static final java.util.Set<String> LIBRARY_ONLY_ATTRS = new java.util.HashSet<>(java.util.Arrays.asList(
            "cardBackgroundColor", "cardCornerRadius", "cardElevation", "cardUseCompatPadding", "cardPreventCornerOverlap",
            "boxBackgroundMode", "boxStrokeColor", "boxStrokeWidth", "boxCornerRadiusTopStart", "boxCornerRadiusTopEnd",
            "boxCornerRadiusBottomStart", "boxCornerRadiusBottomEnd", "hintTextColor", "helperText", "errorEnabled",
            "colorPrimary", "colorPrimaryDark", "colorAccent", "colorOnPrimary", "colorOnSurface",
            "cornerRadius", "strokeColor", "strokeWidth", "rippleColor", "fabSize", "fabCustomSize",
            "tabIndicatorColor", "tabSelectedTextColor", "tabTextColor", "navigationIcon", "popupTheme",
            "materialButtonStyle", "chipStyle", "textInputStyle", "layout_behavior", "behavior_hideable",
            "elevation", "actionBarSize", "appBarLayoutStyle"
    ));

    private String libraryAttrHint(List<BuildResult.BuildError> errors) {
        java.util.Set<String> hit = new java.util.LinkedHashSet<>();
        for (BuildResult.BuildError e : errors) {
            for (String attr : LIBRARY_ONLY_ATTRS) {
                if (e.message != null && e.message.contains("attribute " + attr + " ")) hit.add(attr);
            }
        }
        if (hit.isEmpty()) return null;
        return "These attribute(s) - " + String.join(", ", hit) + " - belong to the AppCompat/Material "
             + "libraries, not plain Android. This on-device builder can't resolve them (see the "
             + "AppCompat/Material note earlier in this log, or Settings). The layout that uses them "
             + "needs either: plain Android views instead (e.g. a LinearLayout + background drawable "
             + "instead of MaterialCardView, a plain EditText instead of TextInputLayout), or building "
             + "this project with a real Gradle tool (its build.gradle/settings.gradle are ready for "
             + "that - see GradleFilesGenerator).";
    }
}
