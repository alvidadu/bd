package com.apkbuilder;

public class ToolStatus {
    public final String name;
    public final boolean ok;
    public final String detail;

    public ToolStatus(String name, boolean ok, String detail) {
        this.name = name;
        this.ok = ok;
        this.detail = detail;
    }
}
