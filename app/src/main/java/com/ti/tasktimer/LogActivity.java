package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 记录页：计时与检查的完整流水（每次开始/过半/结束/加时都带时间戳），外加同步状态。 */
public class LogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("使用记录");
        title.setTextSize(22);
        root.addView(title);

        TextView sync = new TextView(this);
        String last = SyncManager.lastResult(this);
        sync.setText("同步：" + (last.isEmpty() ? "尚未同步" : last));
        sync.setTextSize(14);
        sync.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(sync);

        Button repick = new Button(this);
        repick.setText("重新选择 Drive 桥文件（tasks.json）");
        repick.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("pick_bridge", true);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
        root.addView(repick);

        TextView log = new TextView(this);
        String s = TimerState.log(this);
        log.setText(s.isEmpty() ? "（暂无记录）" : s);
        log.setTextSize(13);
        log.setPadding(0, pad / 2, 0, 0);
        root.addView(log);
    }
}
