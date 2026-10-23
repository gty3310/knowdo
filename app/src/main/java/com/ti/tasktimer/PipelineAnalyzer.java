package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;
import android.provider.Settings;

/**
 * 屏幕内容分析管线（组合入口）：
 * 0) 豁免口径与专注守卫一致（知行自己、桌面、Muse、任务主软件、白名单内应用）→ 不表态；
 * 1) 二级 NudeNet 看图：nsfw 命中优先（纯画面分神只有它看得见）；
 * 2) 前台包名类别：购物/短视频类应用本身就是强信号；
 * 3) 一级 OCR + 规则：抓网页版购物、网页版短视频、与任务无关的文字内容。
 * 三级 VlmAnalyzer 本版不实现（只留 ContentAnalyzer 接口），日后接入时插在这里第 4 步。
 */
public class PipelineAnalyzer implements ContentAnalyzer {
    private final NudeNetAnalyzer nudeNet = new NudeNetAnalyzer();
    private final OcrRuleAnalyzer ocr = new OcrRuleAnalyzer();

    @Override
    public Result analyze(Context c, Bitmap bmp, TaskItem task, String fgPkg) {
        if (fgPkg != null && isExempt(c, fgPkg, task)) return Result.none();

        Result nude = nudeNet.analyze(c, bmp, task, fgPkg);
        if (nude.category != null) return nude;

        if (PkgCategories.isShopping(fgPkg)) {
            return new Result(CAT_SHOPPING, 0.85f, "前台应用是购物类：" + AppLister.labelOf(c, fgPkg));
        }
        if (PkgCategories.isVideo(fgPkg)) {
            return new Result(CAT_SHORT_VIDEO, 0.85f, "前台应用是视频类：" + AppLister.labelOf(c, fgPkg));
        }
        return ocr.analyze(c, bmp, task, fgPkg);
    }

    private boolean isExempt(Context c, String fg, TaskItem task) {
        if (fg.equals(c.getPackageName())) return true;
        if (fg.equals("com.android.systemui") || fg.equals("android")) return true;
        String launcher = AppLister.launcherPackage(c);
        if (launcher != null && fg.equals(launcher)) return true;
        String muse = FocusGuard.musePackage(c);
        if (muse != null && fg.equals(muse)) return true;
        if (task != null) {
            if (task.primaryApp != null && fg.equals(task.primaryApp)) return true;
            if ("allow".equals(task.focusMode) && task.allowedApps.contains(fg)) return true;
        }
        try {
            String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) {
                int slash = ime.indexOf('/');
                String imePkg = slash > 0 ? ime.substring(0, slash) : ime;
                if (fg.equals(imePkg)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
