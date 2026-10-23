package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 二级分析：NudeNet 看图，抓文字看不见的分神——黄色内容与“穿得少”。
 * 按用户 2026-10-23 的定义：衣服穿得很少的照片也算违规，
 * 所以 BELLY_EXPOSED（露腹）与 MALE_BREAST_EXPOSED（男性裸上身）同样计入 nsfw 类；
 * 只有“遮住”类（*_COVERED）与脸/脚/腋下不算。
 * 已知误报：游泳、健身、医学图可能中招——靠 VerdictEngine 的两次确认
 *（nsfw 可在设置里改成一张即触发）与事件记录复核兜底，不承诺零误判。
 */
public class NudeNetAnalyzer implements ContentAnalyzer {
    private static final Set<String> EXPOSED = new HashSet<>(Arrays.asList(
            "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED", "FEMALE_GENITALIA_EXPOSED",
            "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED"
    ));

    @Override
    public Result analyze(Context c, Bitmap bmp, TaskItem task, String fgPkg) {
        List<NudeNetDetector.Det> dets = NudeNetDetector.detect(c, bmp);
        NudeNetDetector.Det worst = null;
        for (NudeNetDetector.Det d : dets) {
            if (!EXPOSED.contains(d.label())) continue;
            if (worst == null || d.score > worst.score) worst = d;
        }
        if (worst == null) return Result.none();
        return new Result(CAT_NSFW, worst.score,
                "图像检测命中 " + worst.label() + "（置信 " + String.format(java.util.Locale.US, "%.2f", worst.score) + "）");
    }
}
