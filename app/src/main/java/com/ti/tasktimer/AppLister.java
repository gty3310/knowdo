package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 已安装可启动应用的清单工具：给专注规则的应用多选/单选、跳转执行用。 */
public final class AppLister {
    public static class AppInfo {
        public final String pkg;
        public final String label;
        AppInfo(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private AppLister() {}

    public static List<AppInfo> launchableApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> res = pm.queryIntentActivities(q, 0);
        List<AppInfo> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo r : res) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            CharSequence label = r.loadLabel(pm);
            out.add(new AppInfo(pkg, label == null ? pkg : label.toString()));
        }
        Collections.sort(out, (a, b) -> a.label.compareTo(b.label));
        return out;
    }

    public static String labelOf(Context c, String pkg) {
        if (pkg == null) return "";
        try {
            PackageManager pm = c.getPackageManager();
            CharSequence l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return l == null ? pkg : l.toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** 把指定应用带到前台；包名为空或应用不存在时返回 false。 */
    public static boolean launch(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String launcherPackage(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return r == null ? null : r.activityInfo.packageName;
        } catch (Exception e) {
            return null;
        }
    }
}
