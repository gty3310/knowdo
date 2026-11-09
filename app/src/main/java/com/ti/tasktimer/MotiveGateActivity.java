package com.ti.tasktimer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * 页外加时入口的全屏动机门（v1.72.0）：行页/日历任务行的「+15 分钟」先走这里——
 * 渲染与记录都用 MotiveGate 同一层；点「有」或「没有」后直接给当前计时加时
 * （EXTRA_ADD_SEC 秒）并关闭。不答就不能加时（本页没有跳过入口；按返回键只是
 * 放弃这次加时，不会绕过门把时间加上）。
 */
public class MotiveGateActivity extends Activity {
    public static final String EXTRA_ADD_SEC = "add_sec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        final int addSec = getIntent().getIntExtra(EXTRA_ADD_SEC, 0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        setContentView(root);
        MotiveGate.buildInto(this, root, () -> {
            if (addSec > 0) {
                TimerService.extendTimer(this, addSec);
                Toast.makeText(this, Strings.en()
                        ? "Extended by " + (addSec / 60) + " minutes"
                        : "已加时 " + (addSec / 60) + " 分钟", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
