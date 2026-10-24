package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 日历页的薄壳（v1.8.0）：内容已抽成 CalendarPanel（主页右侧滑出面板与本页共用同一份实现）。
 * 本 Activity 只负责托管面板、转发截图授权结果与生命周期。
 */
public class CalendarActivity extends Activity {
    private static final int REQ_CAPTURE = 44;
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE && panel != null) panel.onCaptureResult(resultCode, data);
    }
}
