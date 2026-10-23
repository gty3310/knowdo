package com.ti.tasktimer;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

/** 轻量版检测实现：UsageStats 最近事件里的最后一个前台应用。 */
public class UsageStatsDetector implements ForegroundDetector {
    @Override
    public String foregroundPackage(Context c) {
        if (!isReady(c)) return null;
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 60_000L, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            long lastTime = 0;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                        && e.getTimeStamp() >= lastTime) {
                    lastTime = e.getTimeStamp();
                    last = e.getPackageName();
                }
            }
            return last;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public boolean isReady(Context c) {
        try {
            AppOpsManager aom = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return false;
            int mode = Build.VERSION.SDK_INT >= 29
                    ? aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }
}
