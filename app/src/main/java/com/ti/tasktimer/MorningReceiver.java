package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * 晨间页「暂停一会儿」到点（v1.17.0）：晨间页退下时约好的时刻到了——
 * 先清推后状态，再核对晨间是否仍未完成（他在推后期里已经从别的入口填完，
 * 就不再打扰），仍未完成则经 AlertHelper.fireMorning 双路径全屏再次拉起。
 * 一次性：响过即止，下一次推后由页面里再点「暂停一会儿」重新约。
 */
public class MorningReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
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
