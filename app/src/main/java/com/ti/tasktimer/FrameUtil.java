package com.ti.tasktimer;

import android.graphics.Bitmap;

/**
 * 帧级小工具（无障碍截图通道用）：
 * aHash —— 8×8 灰度均值感知哈希，采样格中心像素即可，不缩放整图，成本可忽略；
 *         连续两帧哈希相同视为「画面未变」，跳过分析省电（v1.21.0）。
 * isBlackFrame —— FLAG_SECURE 界面（银行/无痕）截出来是纯黑帧，只记不可见、不判定；
 *         v1.26.0 起截图只走无障碍一条通道，本工具由它独用。
 */
public final class FrameUtil {
    private FrameUtil() {}

    /** 8×8 均值哈希：每格取中心像素灰度，与全帧均值比较置位。 */
    public static long aHash(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w <= 0 || h <= 0) return 0L;
        int[] gray = new int[64];
        long sum = 0;
        for (int gy = 0; gy < 8; gy++) {
            int y = Math.min(h - 1, (gy * h + h / 2) / 8);
            for (int gx = 0; gx < 8; gx++) {
                int x = Math.min(w - 1, (gx * w + w / 2) / 8);
                int p = bmp.getPixel(x, y);
                int g = (((p >> 16) & 0xff) * 30 + ((p >> 8) & 0xff) * 59 + (p & 0xff) * 11) / 100;
                gray[gy * 8 + gx] = g;
                sum += g;
            }
        }
        long avg = sum / 64;
        long hash = 0L;
        for (int i = 0; i < 64; i++) {
            if (gray[i] >= avg) hash |= (1L << i);
        }
        return hash;
    }

    /** 近全黑判定：采样像素里 ≥99.5% 几乎全黑。 */
    public static boolean isBlackFrame(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int stepX = Math.max(1, w / 40), stepY = Math.max(1, h / 40);
        int total = 0, black = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int p = bmp.getPixel(x, y);
                int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                total++;
                if (r < 10 && g < 10 && b < 10) black++;
            }
        }
        return total > 0 && black >= total * 0.995;
    }
}
