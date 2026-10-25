package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * NudeNet 320n（YOLOv8n，18 类身体部位检测）的 ONNX 推理封装。
 * 模型文件 assets/nudenet320n.onnx（官方 nudenet 3.4.2 包内原文件，约 12MB）。
 * 预处理与官方 Python 版一致：右/下补黑边成正方形 → 缩放 320×320 → /255 → CHW。
 * 输出 [1,22,2100]：每锚点 4 个框值 + 18 类分数；本类做阈值过滤 + 按类 NMS。
 * 只回“类别+分数+框”，判定口径在 NudeNetAnalyzer。
 */
public final class NudeNetDetector {
    /** 与官方 nudenet.py 的 __labels 顺序严格一致（下标即类别 id）。 */
    public static final String[] LABELS = {
            "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED",
            "FEMALE_BREAST_EXPOSED", "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED",
            "ANUS_EXPOSED", "FEET_EXPOSED", "BELLY_COVERED", "FEET_COVERED",
            "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE", "BELLY_EXPOSED",
            "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED",
            "BUTTOCKS_COVERED"
    };
    private static final int INPUT = 320;
    private static final float SCORE_THRESH = 0.35f;
    private static final float NMS_IOU = 0.45f;

    public static final class Det {
        public final int cls;
        public final float score;
        Det(int cls, float score) { this.cls = cls; this.score = score; }
        public String label() { return LABELS[cls]; }
    }

    private static OrtSession session;
    private static String inputName;
    private static boolean failed;
    private static boolean notDownloadedLogged;

    private NudeNetDetector() {}

    private static synchronized boolean ensureSession(Context c) {
        if (session != null) return true;
        if (failed) return false;
        // v1.21.4：模型不再随包，从 ModelManager 的下载缓存加载；没下载时
        // 明确在日志里给出去向（只记一次），不静默失效、也不崩溃
        ModelManager.Model m = ModelManager.find(c, ModelManager.ID_NUDENET);
        if (m == null || !ModelManager.isReady(c, m)) {
            if (!notDownloadedLogged) {
                notDownloadedLogged = true;
                TimerState.logEvent(c, "屏幕守卫：黄色识别模型还没下载，去 设置 → 模型下载 里下载后这一项才会工作");
            }
            return false;
        }
        try {
            java.io.File f = ModelManager.mainFile(c, m);
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(2);
            session = env.createSession(f.getAbsolutePath(), opts);
            inputName = session.getInputNames().iterator().next();
            return true;
        } catch (Exception e) {
            failed = true;
            TimerState.logEvent(c, "屏幕守卫：NudeNet 模型加载失败（" + e.getClass().getSimpleName() + "），二级分类停用");
            return false;
        }
    }

    /** 检测一张图；模型不可用或无命中返回空表。调用方负责 recycle 入参位图之外的副本。 */
    public static List<Det> detect(Context c, Bitmap src) {
        if (src == null || !ensureSession(c)) return Collections.emptyList();
        Bitmap sq = null, small = null;
        try {
            int w = src.getWidth(), h = src.getHeight();
            int side = Math.max(w, h);
            sq = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(sq);
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(src, 0, 0, null);
            small = Bitmap.createScaledBitmap(sq, INPUT, INPUT, true);

            int[] px = new int[INPUT * INPUT];
            small.getPixels(px, 0, INPUT, 0, 0, INPUT, INPUT);
            FloatBuffer fb = FloatBuffer.allocate(3 * INPUT * INPUT);
            for (int i = 0; i < px.length; i++) fb.put(i, ((px[i] >> 16) & 0xff) / 255f);
            for (int i = 0; i < px.length; i++) fb.put(INPUT * INPUT + i, ((px[i] >> 8) & 0xff) / 255f);
            for (int i = 0; i < px.length; i++) fb.put(2 * INPUT * INPUT + i, (px[i] & 0xff) / 255f);

            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OnnxTensor tensor = OnnxTensor.createTensor(env, fb, new long[]{1, 3, INPUT, INPUT});
            float[][][] out;
            try (OrtSession.Result res = session.run(Collections.singletonMap(inputName, tensor))) {
                out = (float[][][]) res.get(0).getValue();
            } finally {
                tensor.close();
            }
            return postprocess(out[0]); // [22][2100]
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {
            if (small != null && small != src) small.recycle();
            if (sq != null && sq != src) sq.recycle();
        }
    }

    private static List<Det> postprocess(float[][] o) {
        int anchors = o[0].length;
        // 候选：[cx, cy, w, h, score, cls]
        List<float[]> cand = new ArrayList<>();
        for (int a = 0; a < anchors; a++) {
            int best = -1;
            float bestScore = 0f;
            for (int k = 0; k < 18; k++) {
                float s = o[4 + k][a];
                if (s > bestScore) { bestScore = s; best = k; }
            }
            if (best < 0 || bestScore < SCORE_THRESH) continue;
            float cx = o[0][a], cy = o[1][a], bw = o[2][a], bh = o[3][a];
            cand.add(new float[]{cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, bestScore, best});
        }
        cand.sort((x, y) -> Float.compare(y[4], x[4]));
        List<Det> kept = new ArrayList<>();
        List<float[]> keptBoxes = new ArrayList<>();
        for (float[] b : cand) {
            boolean suppressed = false;
            for (float[] k : keptBoxes) {
                if ((int) k[5] == (int) b[5] && iou(k, b) > NMS_IOU) { suppressed = true; break; }
            }
            if (!suppressed) {
                keptBoxes.add(b);
                kept.add(new Det((int) b[5], b[4]));
            }
        }
        return kept;
    }

    private static float iou(float[] a, float[] b) {
        float x1 = Math.max(a[0], b[0]), y1 = Math.max(a[1], b[1]);
        float x2 = Math.min(a[2], b[2]), y2 = Math.min(a[3], b[3]);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float areaA = Math.max(0, a[2] - a[0]) * Math.max(0, a[3] - a[1]);
        float areaB = Math.max(0, b[2] - b[0]) * Math.max(0, b[3] - b[1]);
        float union = areaA + areaB - inter;
        return union <= 0 ? 0 : inter / union;
    }
}
