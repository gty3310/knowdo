package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模型下载页（v1.21.4）：守卫用的模型不再随安装包，誰开哪个功能就
 * 在这里把对应模型下到手机上。逐个模型列名称、用途、大小、状态与
 * 下载/删除按钮；下载在后台线程跑（ModelManager），带进度，完成后
 * 自动全量校验，校验不过会报错并清掉坏文件。
 * 下载源未配置（构建没注入读令牌）时整页给出明确说明，不崩溃。
 * 版式照全 App 极简宁静风，文案不用句号收尾。
 */
public class ModelActivity extends Activity {
    private LinearLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean downloading;
    private List<ModelManager.Model> models;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        buildUi(null);
        // 进页先联网刷新一次清单（版本升级靠它发现），拉不到就用缓存清单渲染
        exec.execute(() -> {
            List<ModelManager.Model> fresh = ModelManager.manifest(this, true);
            ui.post(() -> buildUi(fresh));
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean en() { return Strings.en(); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void buildUi(List<ModelManager.Model> list) {
        if (list != null) models = list;
        if (models == null) models = ModelManager.manifest(this, false);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(en() ? "Model downloads" : "模型下载");
        title.setTextSize(21);
        title.setTextColor(Ui.text(this));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText(en()
                ? "Models are no longer inside the install package. Whatever feature you turn on, "
                + "download its model here — it is verified after downloading and stored only on this phone"
                : "模型不再装进安装包。你开哪个功能，就在这里把它的模型下到手机上，"
                + "下完自动校验，只存在这台手机里");
        intro.setTextSize(12.5f);
        intro.setTextColor(Ui.sub(this));
        intro.setPadding(0, dp(8), 0, dp(4));
        root.addView(intro);

        if (!ModelManager.sourceConfigured()) {
            TextView warn = new TextView(this);
            warn.setText(en()
                    ? "The download source is not configured in this build, so models cannot be "
                    + "downloaded yet. Features that need a model will say so instead of failing quietly"
                    : "这个安装包还没有配置下载源，暂时下不了模型。需要模型的功能会明确提示你，不会悄悄失效");
            warn.setTextSize(13);
            warn.setTextColor(Ui.danger(this));
            warn.setPadding(0, dp(10), 0, dp(4));
            root.addView(warn);
        }

        if (models.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(en() ? "The model list could not be loaded. Check the network and reopen this page"
                    : "模型清单没加载出来。看看网络，再重新打开这一页");
            empty.setTextSize(13);
            empty.setTextColor(Ui.sub(this));
            empty.setPadding(0, dp(14), 0, 0);
            root.addView(empty);
            return;
        }
        for (ModelManager.Model m : models) addModelRow(m);
    }

    private String displayName(ModelManager.Model m) {
        if (ModelManager.ID_NUDENET.equals(m.id)) return en() ? "Image model" : "黄色识别模型";
        if (ModelManager.ID_EMBED.equals(m.id)) return en() ? "Semantic model" : "语义小模型";
        return m.id;
    }

    private String purposeText(ModelManager.Model m) {
        if (en()) {
            if (ModelManager.ID_NUDENET.equals(m.id)) return "Spots explicit or barely-dressed images for the screen-content guard";
            if (ModelManager.ID_EMBED.equals(m.id)) return "Compares screen text with your task on-device, and settles verdicts when the cloud is unreachable";
        }
        return m.purpose;
    }

    private void addModelRow(final ModelManager.Model m) {
        ModelManager.Status st = ModelManager.status(this, m);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(14), 0, dp(6));

        TextView name = new TextView(this);
        name.setText(displayName(m));
        name.setTextSize(16);
        name.setTextColor(Ui.text(this));
        box.addView(name);

        TextView sub = new TextView(this);
        sub.setText(purposeText(m) + "\n" + ModelManager.sizeText(m.totalBytes())
                + (en() ? " · status: " : " · 状态：") + statusText(st));
        sub.setTextSize(12.5f);
        sub.setTextColor(Ui.sub(this));
        box.addView(sub);

        final TextView progress = new TextView(this);
        progress.setTextSize(12.5f);
        progress.setTextColor(Ui.accent(this));
        progress.setPadding(0, dp(4), 0, 0);
        progress.setVisibility(View.GONE);
        box.addView(progress);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);
        if (st == ModelManager.Status.READY) {
            Button del = new Button(this);
            del.setText(en() ? "Delete" : "删除");
            del.setOnClickListener(v -> {
                ModelManager.delete(this, m.id);
                toast(en() ? "Deleted. The feature that needs it will ask before using it again"
                        : "已删除。需要它的功能下次用之前会再提醒你下载");
                buildUi(null);
            });
            btnRow.addView(del);
        } else if (st != ModelManager.Status.NOT_CONFIGURED) {
            final Button dl = new Button(this);
            dl.setText(st == ModelManager.Status.VERIFY_FAILED
                    ? (en() ? "Download again" : "重新下载")
                    : (en() ? "Download" : "下载"));
            dl.setOnClickListener(v -> startDownload(m, dl, progress));
            btnRow.addView(dl);
        }
        box.addView(btnRow);
        root.addView(box);

        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private String statusText(ModelManager.Status st) {
        switch (st) {
            case READY: return en() ? "ready" : "已就绪";
            case DOWNLOADING: return en() ? "downloading" : "下载中";
            case VERIFY_FAILED: return en() ? "check failed or update pending" : "校验失败或待更新";
            case NOT_CONFIGURED: return en() ? "source not configured" : "下载源未配置";
            default: return en() ? "not downloaded" : "未下载";
        }
    }

    private void startDownload(final ModelManager.Model m, final Button btn, final TextView progress) {
        if (downloading) {
            toast(en() ? "Another model is downloading — one at a time" : "正在下载另一个模型，一个一个来");
            return;
        }
        downloading = true;
        btn.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setText(en() ? "Downloading…" : "下载中…");
        exec.execute(() -> {
            String[] err = new String[1];
            boolean ok = ModelManager.download(this, m,
                    (id, done, total) -> ui.post(() -> progress.setText(
                            (en() ? "Downloading " : "下载中 ") + (total > 0 ? (done * 100 / total) : 0) + "%")),
                    err);
            ui.post(() -> {
                downloading = false;
                if (ok) {
                    toast(en() ? "Downloaded and verified" : "下载完成，校验通过");
                    if (ModelManager.ID_NUDENET.equals(m.id)
                            && GuardEnableGate.completePendingEnable(this)) {
                        toast(en() ? "Screen-content guard is on" : "屏幕内容守卫已开启");
                    }
                } else {
                    toast((en() ? "Download failed: " : "下载没成功：")
                            + (err[0] == null ? "" : err[0]));
                }
                buildUi(null);
            });
        });
    }
}
