package com.apkbuilder;

import java.util.ArrayList;
import java.util.List;

/** A failed build stage. Carries structured errors (file / line / column / message). */
public class BuildException extends Exception {
    public final String stage;
    public final List<BuildResult.BuildError> errors = new ArrayList<>();

    public BuildException(String stage, String message) {
        super(message);
        this.stage = stage;
        errors.add(new BuildResult.BuildError(stage, "", 0, 0, message));
    }

    public BuildException(String stage, String message, List<BuildResult.BuildError> errs) {
        super(message);
        this.stage = stage;
        errors.addAll(errs);
        if (errors.isEmpty()) errors.add(new BuildResult.BuildError(stage, "", 0, 0, message));
    }
}
