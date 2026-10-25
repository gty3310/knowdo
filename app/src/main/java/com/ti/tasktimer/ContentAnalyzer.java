package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * 屏幕内容分析层（与 ForegroundDetector 同样的留口方式，三个实现可替换）：
 * 一级 OcrRuleAnalyzer（ML Kit OCR + 关键词规则 + 前台包名类别，便宜）；
 * 二级 NudeNetAnalyzer（ONNX 小模型看图，抓黄图/穿得少与纯画面分神）；
 * 三级 VlmAnalyzer（端侧多模态大模型）只留接口、本版不实现——耗电大且
 * “AI 聊天是否跑题”这类语义判定要靠它，日后接入时实现本接口即可，
 * PipelineAnalyzer 与 VerdictEngine 都不用改。
 * 组合入口是 PipelineAnalyzer：每张截图先过二级（nsfw 优先级最高），
 * 再看前台包名类别，最后过一级 OCR 规则。
 */
public interface ContentAnalyzer {
    String CAT_SHOPPING = "shopping";
    String CAT_SHORT_VIDEO = "short_video";
    String CAT_OFFTOPIC_CHAT = "offtopic_chat";
    String CAT_NSFW = "nsfw";
    String CAT_UNRELATED = "unrelated";

    /** 分析一张截图。返回 category=null 表示“不判分神”（相关或判不了）。 */
    Result analyze(Context c, Bitmap bmp, TaskItem task, String fgPkg);

    final class Result {
        public final String category;   // null = 不判分神
        public final float confidence;  // 0~1
        public final String detail;     // 给人看的证据（命中词/检测标签/包名）
        /** 语义层来源（v1.21.4，SemanticJudge 填写）：local=本地预筛 / cloud=云端终判 / degraded=本地降级；null=未过语义层。 */
        public String semanticSource;
        /** 语义层本地预筛的余弦分数（未跑预筛时 NaN），供测试窗读数与记录对账。 */
        public float semanticScore = Float.NaN;

        public Result(String category, float confidence, String detail) {
            this.category = category;
            this.confidence = confidence;
            this.detail = detail == null ? "" : detail;
        }

        public static Result none() { return new Result(null, 0f, ""); }
    }

    static String label(String category) {
        if (category == null) return "无";
        switch (category) {
            case CAT_SHOPPING: return "购物";
            case CAT_SHORT_VIDEO: return "短视频";
            case CAT_OFFTOPIC_CHAT: return "无关闲聊";
            case CAT_NSFW: return "黄色/暴露内容";
            case CAT_UNRELATED: return "与任务无关的内容";
            default: return category;
        }
    }
}
