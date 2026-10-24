package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 暂停卡（v1.11.0 暂停模式，定稿稿 mockup-pause-v5）：点暂停先过这张卡——
 * 上段 emoji 大格子选目的（3×3），中段选时长（沿用 ResumeReminder 的时长项＋自定义），
 * 下段「静音」开关（选「去上课」自动打开、可手动再关），底部预览＋「开始暂停」确认。
 * 全部暂停入口统一：Activity 上下文走 PauseFlow 的 AlertDialog、悬浮窗走本类的 overlay，
 * 两边共用同一张卡（buildCard）。确认只回传 Choice，真正的暂停由调用方走既有原语
 * TimerService.pauseWithReminder（「换个目的」则只写暂停槽＋重设叫回，不重复暂停）。
 */
public final class PauseCard {
    public static final String[] PURPOSE_EMOJIS = {
            "🚻", "💧", "🥗", "📞", "🙋", "😴", "🏃", "🏫", "✏️"};
    public static final int PURPOSE_CLASS = 7;  // 去上课：选中自动打开静音
    public static final int PURPOSE_CUSTOM = 8; // 自定义：出输入框

    public static class Choice {
        public long delayMs;
        public String reason; // 目的显示名（预设名或自定义文本）
        public String emoji;
        public boolean silent;
    }

    public interface OnPick { void onPick(Choice ch); }

    private PauseCard() {}

    // ---------- 卡面构建（两宿主共用） ----------
    public static View buildCard(final Context c, String taskTitle, String confirmLabel,
                                 final OnPick onPick, final Runnable onCancel) {
        Strings.init(c);
        final float d = c.getResources().getDisplayMetrics().density;
        final String[] names = Strings.pausePurposeNames();

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(248, 20, 23, 26));
        bg.setCornerRadius(18 * d);
        bg.setStroke((int) d, Color.argb(40, 255, 255, 255));
        card.setBackground(bg);
        int pad = (int) (17 * d);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(c);
        title.setText(Strings.pauseCardTitle(taskTitle));
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        card.addView(title);

        // —— 目的：emoji 大格子 3×3 ——
        final int[] selPurpose = {-1};
        final LinearLayout[] tiles = new LinearLayout[PURPOSE_EMOJIS.length];
        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(3);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = (int) (13 * d);
        grid.setLayoutParams(glp);
        for (int i = 0; i < PURPOSE_EMOJIS.length; i++) {
            final int idx = i;
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(0, (int) (10 * d), 0, (int) (7 * d));
            GridLayout.LayoutParams tlp = new GridLayout.LayoutParams();
            tlp.width = 0;
            tlp.columnSpec = GridLayout.spec(i % 3, 1f);
            tlp.setMargins((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (4 * d));
            tile.setLayoutParams(tlp);
            TextView emo = new TextView(c);
            emo.setText(PURPOSE_EMOJIS[i]);
            emo.setTextSize(26);
            emo.setGravity(Gravity.CENTER);
            tile.addView(emo);
            TextView cap = new TextView(c);
            cap.setText(names[i]);
            cap.setTextSize(10.5f);
            cap.setTextColor(Color.rgb(159, 176, 167));
            cap.setGravity(Gravity.CENTER);
            tile.addView(cap);
            tiles[i] = tile;
            grid.addView(tile);
        }
        card.addView(grid);

        final EditText customPurpose = new EditText(c);
        customPurpose.setHint(Strings.pausePurposeHint());
        customPurpose.setTextColor(Color.WHITE);
        customPurpose.setHintTextColor(Color.rgb(139, 150, 143));
        customPurpose.setTextSize(14);
        customPurpose.setVisibility(View.GONE);
        LinearLayout.LayoutParams cplp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cplp.topMargin = (int) (6 * d);
        customPurpose.setLayoutParams(cplp);
        card.addView(customPurpose);

        // —— 时长 ——
        TextView durLabel = new TextView(c);
        durLabel.setText(Strings.pauseDurationLabel());
        durLabel.setTextColor(Color.rgb(139, 150, 143));
        durLabel.setTextSize(12);
        LinearLayout.LayoutParams dllp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dllp.topMargin = (int) (15 * d);
        durLabel.setLayoutParams(dllp);
        card.addView(durLabel);

        final int[] selDur = {-1}; // -1 未选；0..n-1 标准项；n = 自定义
        final TextView[] chips = new TextView[ResumeReminder.LABELS.length + 1];
        // 等宽网格：每行 4 格、宽度均分（weight），不再按内容流式排——内容定宽会把
        // 「10 分钟」这类 chip 挤到字内换行、行尾 chip 竖排（v1.11.0 真机反馈，v1.12.0 修）
        LinearLayout chipsBox = new LinearLayout(c);
        chipsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout chipRow = null;
        for (int i = 0; i < chips.length; i++) {
            if (i % 4 == 0) {
                chipRow = new LinearLayout(c);
                chipRow.setOrientation(LinearLayout.HORIZONTAL);
                chipsBox.addView(chipRow);
            }
            TextView chip = new TextView(c);
            chip.setText(i < ResumeReminder.LABELS.length
                    ? ResumeReminder.LABELS[i] : Strings.pauseCustom());
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            // 单行放不下时自动缩字号（10–13sp），保证整行挤得下、绝不换行
            chip.setAutoSizeTextTypeUniformWithConfiguration(
                    10, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            chip.setPadding((int) (4 * d), (int) (6 * d), (int) (4 * d), (int) (6 * d));
            LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            chlp.setMargins((int) (3 * d), (int) (4 * d), (int) (3 * d), 0);
            chip.setLayoutParams(chlp);
            chips[i] = chip;
            chipRow.addView(chip);
        }
        card.addView(chipsBox);

        // 自定义时长（小时＋分钟）
        final LinearLayout customDur = new LinearLayout(c);
        customDur.setOrientation(LinearLayout.HORIZONTAL);
        customDur.setGravity(Gravity.CENTER);
        customDur.setVisibility(View.GONE);
        final EditText hourIn = durInput(c, Strings.en() ? "h" : "小时");
        final EditText minIn = durInput(c, Strings.en() ? "min" : "分钟");
        customDur.addView(hourIn);
        TextView colon = new TextView(c);
        colon.setText(" : ");
        colon.setTextColor(Color.WHITE);
        colon.setTextSize(17);
        customDur.addView(colon);
        customDur.addView(minIn);
        card.addView(customDur);

        // —— 静音行 ——
        LinearLayout silentRow = new LinearLayout(c);
        silentRow.setOrientation(LinearLayout.HORIZONTAL);
        silentRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Color.argb(18, 255, 255, 255));
        sbg.setCornerRadius(11 * d);
        sbg.setStroke((int) d, Color.argb(28, 255, 255, 255));
        silentRow.setBackground(sbg);
        silentRow.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = (int) (15 * d);
        silentRow.setLayoutParams(srlp);
        TextView sIc = new TextView(c);
        sIc.setText("🔕");
        sIc.setTextSize(19);
        silentRow.addView(sIc);
        LinearLayout sTx = new LinearLayout(c);
        sTx.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stlp.leftMargin = (int) (9 * d);
        sTx.setLayoutParams(stlp);
        TextView sT1 = new TextView(c);
        sT1.setText(Strings.pauseSilent());
        sT1.setTextColor(Color.WHITE);
        sT1.setTextSize(13.5f);
        sTx.addView(sT1);
        TextView sT2 = new TextView(c);
        sT2.setText(Strings.pauseSilentSub());
        sT2.setTextColor(Color.rgb(139, 150, 143));
        sT2.setTextSize(11);
        sTx.addView(sT2);
        silentRow.addView(sTx);
        final Switch silentSwitch = new Switch(c);
        silentRow.addView(silentSwitch);
        card.addView(silentRow);

        // —— 预览＋确认 ——
        final TextView preview = new TextView(c);
        preview.setTextSize(12.5f);
        preview.setTextColor(Color.rgb(159, 176, 167));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Color.argb(14, 255, 255, 255));
        pbg.setCornerRadius(9 * d);
        preview.setBackground(pbg);
        preview.setPadding((int) (11 * d), (int) (8 * d), (int) (11 * d), (int) (8 * d));
        LinearLayout.LayoutParams pvlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pvlp.topMargin = (int) (13 * d);
        preview.setLayoutParams(pvlp);
        card.addView(preview);

        final Button go = new Button(c);
        go.setText(confirmLabel);
        go.setTextSize(15);
        go.setAllCaps(false);
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(Color.rgb(226, 170, 61));
        gbg.setCornerRadius(11 * d);
        go.setBackground(gbg);
        go.setTextColor(Color.rgb(25, 18, 4));
        LinearLayout.LayoutParams golp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * d));
        golp.topMargin = (int) (12 * d);
        go.setLayoutParams(golp);
        card.addView(go);

        TextView cancel = new TextView(c);
        cancel.setText(Strings.btnCancel());
        cancel.setTextColor(Color.rgb(139, 150, 143));
        cancel.setTextSize(13);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, (int) (10 * d), 0, 0);
        cancel.setOnClickListener(v -> { if (onCancel != null) onCancel.run(); });
        card.addView(cancel);

        // —— 选择状态与预览刷新 ——
        final Runnable refresh = () -> {
            for (int i = 0; i < tiles.length; i++) styleTile(tiles[i], i == selPurpose[0], d);
            for (int i = 0; i < chips.length; i++) styleChip(chips[i], i == selDur[0], d);
            customPurpose.setVisibility(selPurpose[0] == PURPOSE_CUSTOM ? View.VISIBLE : View.GONE);
            customDur.setVisibility(selDur[0] == ResumeReminder.LABELS.length ? View.VISIBLE : View.GONE);
            long ms = chosenMs(selDur[0], hourIn, minIn);
            boolean ready = selPurpose[0] >= 0 && ms > 0;
            go.setEnabled(ready);
            go.setAlpha(ready ? 1f : 0.45f);
            if (!ready) {
                preview.setText(Strings.pausePreviewEmpty());
            } else {
                String name = purposeName(selPurpose[0], customPurpose, names);
                String backAt = new SimpleDateFormat("HH:mm", Locale.US)
                        .format(new Date(System.currentTimeMillis() + ms));
                preview.setText(Strings.pausePreview(PURPOSE_EMOJIS[selPurpose[0]], name,
                        durText(selDur[0], hourIn, minIn), backAt, silentSwitch.isChecked()));
            }
        };
        for (int i = 0; i < tiles.length; i++) {
            final int idx = i;
            tiles[i].setOnClickListener(v -> {
                selPurpose[0] = idx;
                if (idx == PURPOSE_CLASS) silentSwitch.setChecked(true); // 去上课：静音自动打开
                refresh.run();
            });
        }
        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            chips[i].setOnClickListener(v -> { selDur[0] = idx; refresh.run(); });
        }
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(Editable s) { refresh.run(); }
        };
        customPurpose.addTextChangedListener(tw);
        hourIn.addTextChangedListener(tw);
        minIn.addTextChangedListener(tw);
        silentSwitch.setOnCheckedChangeListener((b, checked) -> refresh.run());
        go.setOnClickListener(v -> {
            long ms = chosenMs(selDur[0], hourIn, minIn);
            if (selPurpose[0] < 0 || ms <= 0) return;
            Choice ch = new Choice();
            ch.delayMs = ms;
            ch.reason = purposeName(selPurpose[0], customPurpose, names);
            ch.emoji = PURPOSE_EMOJIS[selPurpose[0]];
            ch.silent = silentSwitch.isChecked();
            onPick.onPick(ch);
        });
        refresh.run();

        ScrollView scroll = new ScrollView(c);
        scroll.addView(card);
        return scroll;
    }

    private static String purposeName(int sel, EditText customPurpose, String[] names) {
        if (sel == PURPOSE_CUSTOM) {
            String t = customPurpose.getText().toString().trim();
            return t.isEmpty() ? Strings.pausePurposeCustomDefault() : t;
        }
        return names[sel];
    }

    private static long chosenMs(int selDur, EditText hourIn, EditText minIn) {
        if (selDur < 0) return 0;
        if (selDur < ResumeReminder.DELAYS_MS.length) return ResumeReminder.DELAYS_MS[selDur];
        long ms = (parse(hourIn) * 60L + parse(minIn)) * 60_000L;
        return ms <= 0 ? 0 : Math.max(60_000L, ms);
    }

    private static String durText(int selDur, EditText hourIn, EditText minIn) {
        if (selDur >= 0 && selDur < ResumeReminder.LABELS.length) return ResumeReminder.LABELS[selDur];
        long total = parse(hourIn) * 60L + parse(minIn);
        return total + (Strings.en() ? " min" : " 分钟");
    }

    private static void styleTile(LinearLayout tile, boolean on, float d) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(on ? Color.argb(41, 226, 170, 61) : Color.argb(12, 255, 255, 255));
        g.setCornerRadius(13 * d);
        g.setStroke((int) d, on ? Color.rgb(226, 170, 61) : Color.argb(30, 255, 255, 255));
        tile.setBackground(g);
    }

    private static void styleChip(TextView chip, boolean on, float d) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(on ? Color.argb(46, 226, 170, 61) : Color.argb(10, 255, 255, 255));
        g.setCornerRadius(999);
        g.setStroke((int) d, on ? Color.rgb(226, 170, 61) : Color.argb(46, 255, 255, 255));
        chip.setBackground(g);
        chip.setTextColor(on ? Color.rgb(255, 217, 122) : Color.rgb(223, 232, 226));
    }

    private static EditText durInput(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(139, 150, 143));
        e.setTextColor(Color.WHITE);
        e.setTextSize(17);
        e.setGravity(Gravity.CENTER);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        float d = c.getResources().getDisplayMetrics().density;
        e.setLayoutParams(new LinearLayout.LayoutParams((int) (84 * d),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private static long parse(EditText e) {
        try { return Long.parseLong(e.getText().toString().trim()); } catch (Exception ex) { return 0; }
    }

    // ---------- overlay 宿主（悬浮窗用，与原 PauseMenu 同窗口参数） ----------
    private static WindowManager owm;
    private static View overlayView;

    public static boolean overlayOpen() { return overlayView != null; }

    public static void showOverlay(final Context c, String taskTitle, String confirmLabel,
                                   final OnPick onPick) {
        closeOverlay();
        owm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (owm == null) return;
        float d = c.getResources().getDisplayMetrics().density;
        View card = buildCard(c, taskTitle, confirmLabel,
                ch -> { closeOverlay(); onPick.onPick(ch); },
                PauseCard::closeOverlay);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (324 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.dimAmount = 0.45f;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try {
            owm.addView(card, lp);
            overlayView = card;
        } catch (Exception e) {
            overlayView = null;
        }
    }

    public static void closeOverlay() {
        if (owm != null && overlayView != null) {
            try { owm.removeView(overlayView); } catch (Exception ignored) { }
        }
        overlayView = null;
    }
}
