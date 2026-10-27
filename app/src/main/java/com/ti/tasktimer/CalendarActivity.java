package com.ti.tasktimer;

import android.app.Activity;
import android.os.Bundle;

/**
 * 日历页的薄壳（v1.8.0）：内容已抽成 CalendarPanel（主页右侧滑出面板与本页共用同一份实现）。
 * 本 Activity 只负责托管面板与生命周期。（v1.26.0：截图授权结果转发随录屏通道移除一并删去。）
 */
public class CalendarActivity extends Activity {
    private CalendarPanel panel;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        panel = new CalendarPanel(this);
        setContentView(panel);
    }

    @Override protected void onResume() {
        super.onResume();
        if (panel != null) panel.panelStart();
    }

    @Override protected void onPause() {
        if (panel != null) panel.panelStop();
        super.onPause();
    }
}
