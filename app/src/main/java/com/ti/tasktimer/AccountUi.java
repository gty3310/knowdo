package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 账号与同步弹窗（Supabase 登录/注册/退出），v1.8.0 从 MainActivity 抽出，
 * 供主页抽屉与设置页共用同一套逻辑与文案。onChanged 在登录态/同步结果变化后回调，
 * 由调用方自己重读数据、刷新界面。
 */
public final class AccountUi {
    private AccountUi() { }

    private static Handler handler(Activity a) {
        return new Handler(Looper.getMainLooper());
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void toast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_LONG).show();
    }

    /** 未登录显示邮箱+密码+登录/注册；已登录显示邮箱、同步状态、立即同步与退出登录。
     *  未登录不硬挡：App 本地与 Drive 桥照常可用。 */
    public static void show(final Activity act, final TaskStore store, final Runnable onChanged) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 20), dp(act, 12), dp(act, 20), 0);
        if (Supa.isLoggedIn(act)) {
            TextView who = new TextView(act);
            who.setText("已登录：" + Supa.userEmail(act));
            who.setTextSize(16);
            who.setTextColor(Color.BLACK);
            box.addView(who);
            TextView st = new TextView(act);
            st.setText(SbSync.statusText(act, store)
                    + "\n\n任务与计时状态都存在云端（Supabase），手机与电脑共用同一份；"
                    + "另一台设备开始计时，这台会立刻镜像同一个倒计时。");
            st.setTextSize(14);
            st.setTextColor(Color.DKGRAY);
            box.addView(st);
            new AlertDialog.Builder(act)
                    .setTitle("账号与同步")
                    .setView(box)
                    .setPositiveButton("立即同步", (d, w) -> runSync(act, store, onChanged, true))
                    .setNeutralButton("退出登录", (d, w) -> doLogout(act, onChanged))
                    .setNegativeButton("关闭", null)
                    .show();
            return;
        }
        final EditText email = new EditText(act);
        email.setHint("邮箱");
        email.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(email);
        final EditText password = new EditText(act);
        password.setHint("密码（至少 6 位）");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(password);
        // 确认密码（v1.20.0）：只在点「注册」时校验，两次不一致就拦下防输错；登录不用它
        final EditText password2 = new EditText(act);
        password2.setHint("确认密码（注册时再输一遍）");
        password2.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(password2);
        TextView tip = new TextView(act);
        tip.setText("登录后进入云端同步：本机现有任务会全量成为你的云端数据，不会丢失。"
                + "没登录也能照常用本机与 Drive 桥。");
        tip.setTextSize(13);
        tip.setTextColor(Color.DKGRAY);
        box.addView(tip);
        // 验证邮件说明（v1.20.0）：邮件是数据库服务 Supabase 发的，发件人不眼熟，必须说清
        TextView verifyTip = new TextView(act);
        verifyTip.setText("注册提醒：提交注册后，数据库服务 Supabase 会给你发一封验证邮件，"
                + "发件人可能不眼熟。请留意邮箱的收件箱与垃圾邮件文件夹，找到这封邮件、"
                + "点里面的链接完成验证，再回来登录。");
        verifyTip.setTextSize(13);
        verifyTip.setTextColor(Color.DKGRAY);
        verifyTip.setPadding(0, dp(act, 8), 0, 0);
        box.addView(verifyTip);
        AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle("账号与同步（Supabase）")
                .setView(box)
                .setPositiveButton("登录", (d, w) -> doAuth(act, true,
                        email.getText().toString().trim(), password.getText().toString(), store, onChanged))
                .setNeutralButton("注册", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        // 注册按钮接管点击：先校验两次密码，一致才关窗提交（默认行为会直接关窗，来不及拦）
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            String em = email.getText().toString().trim();
            String pw = password.getText().toString();
            String pw2 = password2.getText().toString();
            if (em.isEmpty() || pw.isEmpty()) {
                toast(act, "邮箱和密码都要填");
                return;
            }
            if (!pw.equals(pw2)) {
                password2.setError("两次输入的密码不一致，请再输一遍");
                password2.requestFocus();
                return;
            }
            dlg.dismiss();
            doAuth(act, false, em, pw, store, onChanged);
        });
    }

    private static void doAuth(final Activity act, final boolean signIn, final String email,
                               final String password, final TaskStore store, final Runnable onChanged) {
        if (email.isEmpty() || password.isEmpty()) {
            toast(act, "邮箱和密码都要填");
            return;
        }
        toast(act, signIn ? "正在登录…" : "正在注册…");
        new Thread(() -> {
            try {
                String pendingMsg = null;
                if (signIn) {
                    Supa.signIn(act, email, password);
                } else {
                    pendingMsg = Supa.signUp(act, email, password);
                }
                if (pendingMsg != null) {
                    final String m = pendingMsg;
                    handler(act).post(() -> toast(act, m));
                    return;
                }
                handler(act).post(() -> {
                    SyncManager.setSupabaseMode(act, true);
                    if (SyncManager.realtimeEnabled(act)) SyncService.start(act);
                    toast(act, "登录成功，正在同步");
                    runSync(act, store, onChanged, true);
                });
            } catch (Supa.Err e) {
                final String m = e.network ? "网络连不上，稍后再试"
                        : (signIn ? "登录失败：" : "注册失败：") + e.getMessage();
                handler(act).post(() -> toast(act, m));
            } catch (Exception e) {
                final String m = (signIn ? "登录失败：" : "注册失败：") + e.getMessage();
                handler(act).post(() -> toast(act, m));
            }
        }).start();
    }

    private static void doLogout(final Activity act, final Runnable onChanged) {
        new Thread(() -> {
            Supa.signOut(act);
            handler(act).post(() -> {
                SyncManager.setSupabaseMode(act, false);
                SupaRealtime.shutdown();
                SyncService.stop(act);
                if (onChanged != null) onChanged.run();
                toast(act, "已退出登录：回到 Drive 桥模式（本机数据都在）");
            });
        }).start();
    }

    /** 与 MainActivity 原 runSync 同义：后台线程同步一次，回来回调刷新。 */
    public static void runSync(final Activity act, final TaskStore store,
                               final Runnable onChanged, final boolean verbose) {
        new Thread(() -> {
            String result = SyncManager.syncNowSmart(act, store);
            SyncManager.setLastResult(act, result);
            handler(act).post(() -> {
                if (onChanged != null) onChanged.run();
                if (verbose) toast(act, result);
            });
        }).start();
    }
}
