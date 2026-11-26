package com.ti.tasktimer;

import android.content.Context;

/**
 * 宽松四档的本地把关（v1.98.0）：云端判定给出原始 leniency（0–3），系统
 * 执行前先过这里。两道把关（用户 2026-11-26 定案，本地零 token、AI 绕不过）：
 *
 * 1. 难度档是「预先承诺」定地板：AI 只分真假、不赦免真分神——L0 的停稳
 *    时长走现行难度档全额，不在这里缩水（本类不碰时长，只决定档位本身）。
 * 2. 本地硬护栏（只封顶、不加严）：近 1 小时主线占比 <50%，或近 1 小时
 *    已判 L0 ≥2 次，则宽松封顶 L1——「偶尔岔一下」反复出现就不再算岔路。
 *
 * 原始值缺失（-1，服务端未部署新 prompt / 本地兜底判定 / 解析失败）时
 * 返回 -1，调用方按现行标准流程走（等价 L1），行为与 v1.97.0 一致。
 */
public final class LeniencyGate {
    public static final int NONE = -1;
    public static final int L0 = 0;
    public static final int L1 = 1;
    public static final int L2 = 2;
    public static final int L3 = 3;

    private LeniencyGate() {}

    /** 原始 leniency → 生效 leniency（护栏只把 L2/L3 封顶到 L1，永不加严）。 */
    public static int effective(Context c, int raw) {
        if (raw < 0 || raw > 3) return NONE;
        if (raw <= L1) return raw;
        if (ActivityStream.mainlineRatio(c, ActivityStream.HOUR_MS) < 0.5) return L1;
        if (ActivityStream.l0CountLastHour(c) >= 2) return L1;
        return raw;
    }
}
