package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;
import android.provider.Settings;

/**
 * 屏幕内容分析管线（组合入口），v1.21.0 起按「先便宜后贵」排序：
 * 0) 豁免口径与专注守卫一致（知行自己、桌面、Muse、任务主软件、白名单内应用）→ 不表态；
 * 1) 无障碍节点文字预筛（零成本，只有无障碍通道有）：只记「与任务强相关」的
 *    放行标记，NudeNet 之后才生效（见下）。v1.94.0 起关键词不再直接定罪；
 * 2) NudeNet 看图：nsfw 只有它看得见。凡前面没给出结论的帧必须过它，
 *    不许因为重排序漏掉浏览器/社交里的黄图；
 * 3) 文字规则兜底：节点文字够多（紧凑 ≥30 字）就用节点文字跑规则（含「无关」判定），
 *    不跑 OCR；节点文字过少才跑 ML Kit OCR 再判。
 * 4) 语义判定层（v1.21.4，SemanticJudge）：只复核第 3 步的「拿不准」——
 *    不表态、相关放行、0.55 低置信「无关」；明确结论（NudeNet）在前面
 *    已经返回，不经过语义层。语义层无从判定时维持粗判。
 * v1.94.0：删第 1 步旧「包名类别终局」——不再凭前台应用是购物/视频类直接定罪。
 * v1.96.0：暂停空窗底线模式——哨兵任务（暂停守卫）且目的文本为空时，在豁免
 * 之后直接短路到第 2 步 NudeNet 并返回：只有黄图能出结论，相关性/语义层不判；
 * 目的填完后哨兵任务带上目的文本，走下面全套管线按目的判。
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

    /** nodeText 为无障碍节点文字（传 null 时走 OCR 路径兜底）。 */
    public Result analyze(Context c, Bitmap bmp, TaskItem task, String fgPkg, String nodeText) {
        lastText = nodeText != null ? nodeText : "";
        SemanticJudge.beginFrame();
        // 把任务说明拼进 note 再交给后续规则与 SemanticJudge，不改红线文件本身
        if (task != null) task = task.forJudging();
        if (fgPkg != null && isExempt(c, fgPkg, task)) {
            // 活动流水（v1.98.0）：任务主软件/允许名单的豁免帧是主线本身，
            // 记一帧主线段，否则主线占比会被系统性低估；其余豁免（本包/桌面/
            // 输入法等）不入流水
            if (task != null && (fgPkg.equals(task.primaryApp)
                    || ("allow".equals(task.focusMode) && task.allowedApps.contains(fgPkg)))) {
                ActivityStream.noteMainlineFrame(c, fgPkg);
            }
            return Result.none();
        }

        // 暂停空窗底线模式（v1.96.0）：目的还没填时只守黄图底线，其余不表态
        if (task != null && task.pauseGuard
                && (task.title == null || task.title.trim().isEmpty())) {
            Result nn = nudeNet.analyze(c, bmp, task, fgPkg);
            ActivityStream.noteFrame(c, task, fgPkg, lastText, nn);
            return nn;
        }

        boolean haveNodeText = nodeText != null && !nodeText.trim().isEmpty();
        boolean relevant = false;
        if (haveNodeText) {
            Result kw = ocr.judgeKeywords(nodeText);
            if (kw.category != null) {
                ActivityStream.noteFrame(c, task, fgPkg, lastText, kw);
                return kw;
            }
            relevant = ocr.relevantToTask(nodeText, task);
        }

        // nsfw 优先级不动：前面没给出结论的帧必须过 NudeNet；
        // 「与任务相关」的放行只在 NudeNet 之后生效，否则带任务词的页面里的黄图会被放行漏掉
        Result nude = nudeNet.analyze(c, bmp, task, fgPkg);
        if (nude.category != null) {
            ActivityStream.noteFrame(c, task, fgPkg, lastText, nude);
            return nude;
        }

        if (relevant) {
            // 关键词放行属于「置信不足的放行」：语义层复核，可能维持放行、也可能判不相关
            Result s = SemanticJudge.refine(c, task, fgPkg, lastText, Result.none());
            Result out = s != null ? s : Result.none();
            ActivityStream.noteFrame(c, task, fgPkg, lastText, out);
            return out;
        }
        Result coarse;
        if (haveNodeText && nodeText.replaceAll("\\s+", "").length() >= 30) {
            coarse = ocr.judgeText(nodeText, task, fgPkg);
        } else {
            coarse = ocr.analyze(c, bmp, task, fgPkg);
            lastText = ocr.lastText;
        }
        // judgeText/OCR 现在只有不表态与低置信「无关」两种出口，都升级
        // 语义层复核（语义层可能推翻「无关」为放行）；其余明确结论原样返回
        if (coarse.category == null || CAT_UNRELATED.equals(coarse.category)) {
            Result s = SemanticJudge.refine(c, task, fgPkg, lastText, coarse);
            if (s != null) {
                ActivityStream.noteFrame(c, task, fgPkg, lastText, s);
                return s;
            }
        }
        ActivityStream.noteFrame(c, task, fgPkg, lastText, coarse);
        return coarse;
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
