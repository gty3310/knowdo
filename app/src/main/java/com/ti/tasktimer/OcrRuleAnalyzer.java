package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 一级分析：文字 → 关键词规则。文字来源有两路（v1.21.0 起）：
 * 无障碍节点文字（零成本，管线预筛与「无关」判定优先用它）与
 * ML Kit OCR（中文识别器兼拉丁字母 + 日文识别器，节点文字太少时才兜底跑）。
 * 判定顺序：命中华购/短视频词 → 分神；与任务上下文有词面重合 → 相关；
 * 文字很多、零重合、且前台是浏览器/社交/视频类 → “无关”（低置信，靠两次确认兜底）；
 * 其余不表态。OCR 读不到的（花字、手写、纯画面）由二级 NudeNet 补位。
 */
public class OcrRuleAnalyzer implements ContentAnalyzer {
    private static final String[] SHOPPING_KW = {
            "购物车", "立即购买", "立即下单", "加入购物车", "领券", "优惠券",
            "去结算", "确认订单", "提交订单", "秒杀", "拼单", "包邮", "¥"
    };
    private static final String[] VIDEO_KW = {
            "点赞", "弹幕", "同款", "上滑看下一个", "关注作者", "评论区", "直播中", "打赏"
    };

    private TextRecognizer zhRecognizer;
    private TextRecognizer jaRecognizer;

    /** 最近一次 OCR 读到的文字（测试模式显示用；节点文字路径不写它）。 */
    public volatile String lastText = "";

    @Override
    public Result analyze(Context c, Bitmap bmp, TaskItem task, String fgPkg) {
        String text = ocr(c, bmp);
        lastText = text;
        return judgeText(text, task, fgPkg);
    }

    /** 只用关键词表扫一遍文字：命中购物/短视频词就出结论，否则不表态（管线预筛用）。 */
    public Result judgeKeywords(String text) {
        if (text == null) return Result.none();
        for (String kw : SHOPPING_KW) {
            if (text.contains(kw)) {
                return new Result(CAT_SHOPPING, 0.70f, "屏幕文字命中购物词「" + kw + "」");
            }
        }
        for (String kw : VIDEO_KW) {
            if (text.contains(kw)) {
                return new Result(CAT_SHORT_VIDEO, 0.65f, "屏幕文字命中短视频词「" + kw + "」");
            }
        }
        return Result.none();
    }

    /** 文字是否与任务上下文强相关（管线预筛的「直接放行」与测试模式显示用）。 */
    public boolean relevantToTask(String text, TaskItem task) {
        return task != null && text != null && !text.isEmpty() && isRelevant(text, task);
    }

    /** 完整文字规则：关键词 → 相关放行 → 文字多且零重合的风险场景判「无关」→ 不表态。 */
    public Result judgeText(String text, TaskItem task, String fgPkg) {
        if (text == null) text = "";
        Result kw = judgeKeywords(text);
        if (kw.category != null) return kw;
        if (task != null && isRelevant(text, task)) return Result.none();
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() >= 30 && PkgCategories.isReadingRisk(fgPkg)) {
            return new Result(CAT_UNRELATED, 0.55f,
                    "屏幕文字较多且与任务「" + (task == null ? "" : task.title) + "」无关键词重合（前台应用类别属阅读/社交/浏览器）");
        }
        return Result.none();
    }

    /** OCR：中文识别器（含拉丁）与日文识别器各跑一遍，文本合并。失败返回空串（不判分神）。 */
    private String ocr(Context c, Bitmap bmp) {
        StringBuilder sb = new StringBuilder();
        try {
            if (zhRecognizer == null) {
                zhRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            }
            String t = Tasks.await(zhRecognizer.process(InputImage.fromBitmap(bmp, 0)), 12, TimeUnit.SECONDS).getText();
            if (t != null) sb.append(t).append('\n');
        } catch (Exception ignored) { }
        try {
            if (jaRecognizer == null) {
                jaRecognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            }
            String t = Tasks.await(jaRecognizer.process(InputImage.fromBitmap(bmp, 0)), 12, TimeUnit.SECONDS).getText();
            if (t != null) sb.append(t);
        } catch (Exception ignored) { }
        return sb.toString();
    }

    /** 任务上下文词面重合：中文按双字词、英文按整词（≥3 字母）。 */
    private boolean isRelevant(String text, TaskItem task) {
        if (text.isEmpty()) return false;
        String lower = text.toLowerCase(java.util.Locale.US);
        for (String tok : contextTokens(task)) {
            if (tok.length() >= 3) {
                if (lower.contains(tok)) return true;
            } else if (text.contains(tok)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> contextTokens(TaskItem task) {
        Set<String> out = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        if (task.title != null) sb.append(task.title).append(' ');
        if (task.focusRule != null) sb.append(task.focusRule).append(' ');
        if (task.note != null) sb.append(task.note);
        String s = sb.toString();
        // 英文/数字词
        for (String w : s.toLowerCase(java.util.Locale.US).split("[^a-z0-9]+")) {
            if (w.length() >= 3) out.add(w);
        }
        // 中文双字词（相邻二字）
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char ch = i < s.length() ? s.charAt(i) : ' ';
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                run.append(ch);
            } else {
                for (int j = 0; j + 1 < run.length() && out.size() < 300; j++) {
                    out.add(run.substring(j, j + 2));
                }
                run.setLength(0);
            }
        }
        return out;
    }
}
