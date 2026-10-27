package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * 晨间页闹钟出口，两路：
 * - 「暂停一会儿」到点（v1.17.0）：晨间页退下时约好的时刻到了——先清推后状态，
 *   再核对晨间是否仍未完成（他在推后期里已经从别的入口填完，就不再打扰），
 *   仍未完成则经 AlertHelper.fireMorning 双路径全屏再次拉起。一次性：响过即止；
 *   拉起的页面展示时重新 engage 重弹循环（见 MorningGate）。
 * - 5 分钟重弹到点（v1.26.0，ACTION_NAG）：转发 MorningGate.onNagTick 复核后决定拉不拉。
 */
public class MorningReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent != null && MorningGate.ACTION_NAG.equals(intent.getAction())) {
            MorningGate.onNagTick(c);
            return;
        }
        MorningGate.clearSnooze(c);
        try {
            JSONObject root = HabitData.load(c);
            if (!HabitData.shouldShowMorning(root)) return;
        } catch (Exception e) {
            return;
        }
        if (TopPages.morningShown) return; // 晨间页已经在眼前（他提前回来了）
        TimerState.logEvent(c, "晨间页暂停到点：再次全屏拉起");
        AlertHelper.fireMorning(c);
    }
}
