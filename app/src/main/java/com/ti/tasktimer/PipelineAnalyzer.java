package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;
import android.provider.Settings;

/**
 * 屏幕内容分析管线（组合入口），v1.21.0 起按「先便宜后贵」排序：
 * 0) 豁免口径与专注守卫一致（知行自己、桌面、Muse、任务主软件、白名单内应用）→ 不表态；
 * 1) 前台包名类别（零成本）：购物/短视频类应用本身就是强信号；
 * 2) 无障碍节点文字预筛（零成本，只有无障碍通道有）：命中购物/短视频关键词直接出结论；
 *    「与任务强相关」的放行标记先记下、NudeNet 之后才生效（见下）；
 * 3) NudeNet 看图：nsfw 只有它看得见。凡包名与关键词都没给出结论的帧必须过它，
 *    不许因为重排序漏掉浏览器/社交里的黄图；
 * 4) 文字规则兜底：节点文字够多（紧凑 ≥30 字）就用节点文字跑完整规则（含「无关」判定），
 *    不跑 OCR；节点文字过少才跑 ML Kit OCR 再判。
 * 三级 VlmAnalyzer 本版不实现（只留 ContentAnalyzer 接口），日后接入时插在这里第 5 步。
 */
public class PipelineAnalyzer implements ContentAnalyzer {
    private final NudeNetAnalyzer nudeNet = new NudeNetAnalyzer();
    private final OcrRuleAnalyzer ocr = new OcrRuleAnalyzer();

    /** 本帧实际依据的文字（节点文字优先，否则 OCR 文字），测试模式显示用。 */
    public volatile String lastText = "";

    @Override
    public Result analyze(Context c, Bitmap bmp, TaskItem task, String fgPkg) {
        return analyze(c, bmp, task, fgPkg, null);
    }

    /** nodeText 为无障碍节点文字（录屏回退通道传 null，走原 OCR 路径）。 */
    public Result analyze(Context c, Bitmap bmp, TaskItem task, String fgPkg, String nodeText) {
        lastText = nodeText != null ? nodeText : "";
        if (fgPkg != null && isExempt(c, fgPkg, task)) return Result.none();

        if (PkgCategories.isShopping(fgPkg)) {
            return new Result(CAT_SHOPPING, 0.85f, "前台应用是购物类：" + AppLister.labelOf(c, fgPkg));
        }
        if (PkgCategories.isVideo(fgPkg)) {
            return new Result(CAT_SHORT_VIDEO, 0.85f, "前台应用是视频类：" + AppLister.labelOf(c, fgPkg));
        }

        boolean haveNodeText = nodeText != null && !nodeText.trim().isEmpty();
        boolean relevant = false;
        if (haveNodeText) {
            Result kw = ocr.judgeKeywords(nodeText);
            if (kw.category != null) return kw;
            relevant = ocr.relevantToTask(nodeText, task);
        }

        // nsfw 优先级不动：包名与关键词都没给出结论的帧必须过 NudeNet；
        // 「与任务相关」的放行只在 NudeNet 之后生效，否则带任务词的页面里的黄图会被放行漏掉
        Result nude = nudeNet.analyze(c, bmp, task, fgPkg);
        if (nude.category != null) return nude;

        if (relevant) return Result.none();
        if (haveNodeText && nodeText.replaceAll("\\s+", "").length() >= 30) {
            return ocr.judgeText(nodeText, task, fgPkg);
        }
        Result r = ocr.analyze(c, bmp, task, fgPkg);
        lastText = ocr.lastText;
        return r;
    }

    /** 文字与当前任务是否强相关（测试模式在结论为「不判分神」时区分显示用）。 */
    public boolean relevantToTask(String text, TaskItem task) {
        return ocr.relevantToTask(text, task);
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
