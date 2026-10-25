package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.HorizontalScrollView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知行 KnowDo 主界面：Excel 式任务表。
 * 每行：▶ 开始计时 | 任务（点按改名）| 情景（点按多选）| 截止（点按选日期/每日重复）| 时长（点按改分钟）
 * 长按一行：标记完成 / 删除。与 Drive 的 tasks.json 通过「同步」双向合并。
 */
public class MainActivity extends Activity {
    private static final int REQ_PICK_BRIDGE = 42;
    private static final int REQ_CAPTURE = 43;
    private TaskItem pendingGuardTask; // 等待截图授权结果的待启动任务

    private TaskStore store;
    private TaskAdapter adapter;
    // ---- v1.8.0 新界面状态 ----
    private boolean appliedDark;
    private boolean appliedEn;
    private TextView nowTitleView;
    private TextView nowRemainView;
    private TextView accountView;   // 今日时间账（v1.23.0，任务页最上方）
    private TextView overflowView;  // 时间账下方的点名行（排不下的必干），无溢出时藏
    private String accountShown = ""; // 时间账已显示文本，没变就不重设（每秒刷新防闪）
    private TextView liveLeftView; // 正在计时行的「剩余」格，状态条每秒顺手刷新它
    private LinearLayout headerInner;
    private LinearLayout bodyInner;
    private HorizontalScrollView headerScroll;
    private HorizontalScrollView bodyHScroll;
    private boolean syncingScroll;
    private FrameLayout drawerRoot;
    private TextView drawerAccountTitle;
    private TextView drawerAccountSub;
    private TextView segLangZh, segLangEn, segThemeLight, segThemeDark;
    private FrameLayout calContainer;
    private CalendarPanel calendarPanel;
    private boolean calOpen;
    // ---- v1.18.0 日历改版：日历进日程页内容区（与任务表左右分屏）、行拖拽 ----
    private FrameLayout rootFrame;
    private ScrollView bodyScrollV;
    private TextView fabView;
    private boolean listDragging; // 主页行正被拖向日历时，整表重画让路（拖完再画）
    private final java.util.Map<String, Integer> splitScheduled = new java.util.HashMap<>(); // 分屏时今日已排：taskId → 最早开始分钟
    // ---- v1.16.0 底部三 Tab：日程（原主页内容）／习惯／知行 ----
    public static final String EXTRA_OPEN_TAB = "open_tab";
    public static final int TAB_SCHEDULE = 0;
    public static final int TAB_HABITS = 1;
    public static final int TAB_ZHIXING = 2;
    private int currentTab = TAB_SCHEDULE;
    private FrameLayout scheduleWrap;
    private FrameLayout habitContainer;
    private FrameLayout zhixingContainer;
    private final LinearLayout[] tabCells = new LinearLayout[3];
    private final TextView[] tabLabels = new TextView[3];
    private final View[] tabMarks = new View[3];
    private HabitPage habitPage;
    private ZhixingPage zhixingPage;
    private String expandedTaskId; // 手风琴：当前展开的任务 id（一次一个）
    private final java.util.Set<String> expandedDays = new java.util.HashSet<>();
    private static final int REQ_CAL_CAPTURE = 44;
    private android.content.BroadcastReceiver syncReceiver;
    // 数据变更监听（v1.6.2）：悬浮窗/同步在别处改了数据，本页前台时立刻跟上
    private final TaskStore.ChangeListener storeListener = this::onStoreChanged;
    // 正在打开的内容编辑弹窗（笔记/专注规则）状态：外部改动来了要刷新它，但不覆盖手头输入
    private AlertDialog openDialog;
    private EditText openDialogInput;
    private String openDialogTaskId;
    private String openDialogKind; // "note" / "rule"
    private String openDialogText; // 打开弹窗时输入框的文字；与现行文字一致＝用户还没改动
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusLoop = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        appliedDark = Ui.dark(this);
        appliedEn = Strings.en();
        store = new TaskStore(this);
        AlertHelper.createChannels(this);
        buildUi();
        // 常驻悬浮窗：打开过知行就一直在（开关在权限页；没给悬浮权限时服务空转等待授权）
        if (FloatingWindow.enabled(this)) FloatService.start(this);
        if (getIntent() != null && getIntent().getBooleanExtra("pick_bridge", false)) {
            launchBridgePicker();
        } else if (SyncManager.isSupabaseMode(this)) {
            runSync(false); // Supabase 模式：打开即对账一次（含漏掉的 autoStart/计时状态）
            if (SyncManager.realtimeEnabled(this)) SyncService.start(this);
        } else if (SyncManager.getUri(this) != null) {
            runSync(false); // 打开即自动同步一次
            if (SyncManager.realtimeEnabled(this)) SyncService.start(this);
        }
        // 建议设置（v1.19.0）：新装/升级后首次打开呈现一次（不阻断功能）；
        // 本轮呈现了清单页，就先不叠更新日志弹框（更新日志的已弹记录未写，下次打开再弹）
        // 更新日志（v1.13.0 起的长期规矩）：升级后首次打开弹一次，全新安装不弹
        if (!SuggestedSettingsActivity.maybeShow(this)) {
            WhatsNew.maybeShow(this);
        }
        // 通知点开可直达某个 Tab（习惯提醒 → 习惯页）
        if (getIntent() != null && getIntent().hasExtra(EXTRA_OPEN_TAB)) {
            switchTab(getIntent().getIntExtra(EXTRA_OPEN_TAB, TAB_SCHEDULE));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra(EXTRA_OPEN_TAB)) {
            switchTab(intent.getIntExtra(EXTRA_OPEN_TAB, TAB_SCHEDULE));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.dark(this) != appliedDark || Ui.en(this) != appliedEn) { recreate(); return; }
        store.load();
        // 昨日未完成滚入今日必干（v1.23.0）：排在晨间门之前——跨过 04:00 日界后的
        // 第一次回前台就滚，晨间页里看到的也是滚完的数。滚完走同一条云端同步路推上去，
        // 并顺手触发本地自动排程把滚入的必干填进今日空档。
        try {
            if (Rollover.maybeRoll(this, store)) {
                runSync(false);
                LocalAutoScheduler.runAsync(this, store, this::refreshTimeAccount);
            }
        } catch (Exception ignored) { }
        // 晨间首填（v1.16.0；v1.17.0 起另有晨间门首触全屏拉起，本入口保留）：
        // 开关开着且当前 session 未完成 → 先进晨间页，填完才回来
        // （排在衔接守卫之前：晨间页回来后 onResume 会再走一遍正常流程）
        // 例外：刚在晨间页点了「暂停一会儿」退下时，本页可能就在晨间页下层，
        // 这次 onResume 不要立刻把页拉回来（推后期间他主动打开知行仍照常进，
        // 那时标记早已被消费掉）。
        try {
            org.json.JSONObject hroot = HabitData.load(this);
            if (!MorningGate.consumeSuppressResumePull() && HabitData.shouldShowMorning(hroot)) {
                startActivity(new Intent(this, MorningActivity.class));
                return;
            }
        } catch (Exception ignored) { }
        adapter.notifyDataSetChanged();
        StartReminder.reschedule(this, store);
        AlarmScheduler.rescheduleAll(this, store);
        handler.removeCallbacks(statusLoop);
        handler.post(statusLoop);
        if (syncReceiver == null) {
            syncReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, Intent i) {
                    store.load();
                    adapter.notifyDataSetChanged();
                    refreshStatus();
                }
            };
        }
        IntentFilter f = new IntentFilter(SyncService.ACTION_SYNC_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(syncReceiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(syncReceiver, f);
        TaskStore.addChangeListener(storeListener);
        // 回前台发现今日有未排的必做 → 本地自动排程填空档（v1.23.0；内部先本地短路，
        // 没有未排必做时不发任何网络请求；落盘后回调刷新时间账的点名行）
        LocalAutoScheduler.runAsync(this, store, this::refreshTimeAccount);
        // 任务衔接守卫（v1.10.0）：冷启动/回到主页时若处于空档，全屏问「下一个任务」
        IdleGuard.checkAndFire(this);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(statusLoop);
        if (calOpen) closeCalendar();
        TaskStore.removeChangeListener(storeListener);
        if (syncReceiver != null) {
            try { unregisterReceiver(syncReceiver); } catch (Exception ignored) { }
        }
        super.onPause();
    }

    /** 任意来源改完数据（悬浮窗编辑、后台同步合并、检查页回写）都会走到这里：
     *  列表整表重读刷新；正开着的笔记/专注弹窗按「没动过才刷新」的规则跟上最新值。 */
    private void onStoreChanged() {
        store.load();
        if (adapter != null) adapter.notifyDataSetChanged();
        refreshStatus();
        refreshOpenDialog();
        // 计时/暂停中任务的子任务被改 → 对账接管（v1.13.0；无变化时内部立刻返回）
        SubtaskResync.check(this);
        // 新建/改期落到今天的必做 → 本地自动排程填空档（v1.23.0；内部短路＋并发合并，
        // 同步合并带进来的变更也会走到这里，云端新值先回读再合并，不互相覆盖）
        LocalAutoScheduler.runAsync(this, store, null);
    }

    /** 打开中的内容弹窗跟上数据源：输入框文字与打开时一致（用户没改过）才换成最新值；
     *  用户已经动手改了就保持他的输入（弹窗关闭重开时自然读到最新，保存时也会按 id 重取对象）。 */
    private void refreshOpenDialog() {
        if (openDialog == null || !openDialog.isShowing() || openDialogInput == null
                || openDialogTaskId == null) return;
        TaskItem fresh = store.byId(openDialogTaskId);
        if (fresh == null) return;
        String freshText = "rule".equals(openDialogKind)
                ? (fresh.focusRule == null ? "" : fresh.focusRule)
                : (fresh.note == null ? "" : fresh.note);
        if (openDialogInput.getText().toString().equals(openDialogText)) {
            openDialogText = freshText;
            openDialogInput.setText(freshText);
            openDialogInput.setSelection(openDialogInput.getText().length());
        }
    }

    /** 登记一个正打开的内容编辑弹窗，关闭时自动注销（供 refreshOpenDialog 跟踪）。 */
    private AlertDialog trackOpenDialog(AlertDialog d, EditText input, TaskItem t, String kind) {
        openDialog = d;
        openDialogInput = input;
        openDialogTaskId = t.id;
        openDialogKind = kind;
        openDialogText = input.getText().toString();
        d.setOnDismissListener(x -> {
            if (openDialog == d) {
                openDialog = null;
                openDialogInput = null;
                openDialogTaskId = null;
                openDialogKind = null;
                openDialogText = null;
            }
        });
        return d;
    }

    /** 保存前按 id 重取最新对象：弹窗持有的是打开那一刻的副本，期间数据可能已被别处改过，
     *  直接拿旧副本改完存盘会把别人的新改动整表盖掉（v1.6.2 同步 bug 的一半根因）。 */
    private TaskItem freshCopyOf(TaskItem t) {
        TaskItem cur = store.byId(t.id);
        return cur != null ? cur : t;
    }

    // ---------------- UI 骨架 ----------------
    // ---------------- UI 骨架（v1.8.0 改版） ----------------
    // 顶栏：左 ☰ 菜单＋字标，右「日历」按钮；顶栏下细条＝当前任务＋剩余时间。
    // 表格：表头与主体两个横向滚动区镜像同步；日历面板从右侧滑出（左缘拉手拖动定宽）；
    // 抽屉从左侧滑出（账号/语言/外观/帮助/设置）。旧顶栏按钮的去向：账号→抽屉与设置页，
    // 同步/实时→设置页账号组，帮助→抽屉与设置页，记录→设置页通用组，新增→右下 FAB。
    private void buildUi() {
        adapter = new TaskAdapter();
        FrameLayout root = new FrameLayout(this);
        rootFrame = root;
        root.setBackgroundColor(Ui.bg(this));
        setContentView(root);

        // v1.16.0：根改为「页面区（权重 1）＋底部三 Tab」纵列；日程页＝原主页内容整套，
        // 习惯/知行是同级的两个页面容器（懒构建、切过去时刷新），日历与抽屉仍盖在最上层。
        LinearLayout mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout pages = new FrameLayout(this);
        mainCol.addView(pages, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        scheduleWrap = new FrameLayout(this);
        pages.addView(scheduleWrap, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        habitContainer = new FrameLayout(this);
        habitContainer.setVisibility(View.GONE);
        pages.addView(habitContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        zhixingContainer = new FrameLayout(this);
        zhixingContainer.setVisibility(View.GONE);
        pages.addView(zhixingContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        buildTabBar(mainCol);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scheduleWrap.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶栏
        LinearLayout topbar = new LinearLayout(this);
        topbar.setOrientation(LinearLayout.HORIZONTAL);
        topbar.setGravity(Gravity.CENTER_VERTICAL);
        topbar.setPadding(dp(6), dp(10), dp(12), dp(4));
        TextView burger = new TextView(this);
        burger.setText("☰");
        burger.setTextSize(21);
        burger.setTextColor(Ui.text(this));
        burger.setGravity(Gravity.CENTER);
        burger.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(40)));
        burger.setOnClickListener(v -> openDrawer());
        topbar.addView(burger);
        TextView wordmark = new TextView(this);
        wordmark.setText(Strings.appWordmark());
        wordmark.setTextSize(19);
        wordmark.setTextColor(Ui.text(this));
        wordmark.setTypeface(wordmark.getTypeface(), android.graphics.Typeface.BOLD);
        topbar.addView(wordmark);
        View tsp = new View(this);
        tsp.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topbar.addView(tsp);
        TextView calBtn = new TextView(this);
        calBtn.setText(Strings.calendar());
        calBtn.setTextSize(14);
        calBtn.setTextColor(Ui.accent(this));
        calBtn.setGravity(Gravity.CENTER);
        calBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
        android.graphics.drawable.GradientDrawable calBg = new android.graphics.drawable.GradientDrawable();
        calBg.setCornerRadius(dp(9));
        calBg.setColor(Ui.muted(this));
        calBtn.setBackground(calBg);
        calBtn.setOnClickListener(v -> toggleCalendar());
        topbar.addView(calBtn);
        content.addView(topbar);

        // 细条：当前任务＋剩余时间
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(16), dp(1), dp(16), dp(8));
        nowTitleView = new TextView(this);
        nowTitleView.setTextSize(14);
        nowTitleView.setTextColor(Ui.text(this));
        nowTitleView.setSingleLine(true);
        nowTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowTitleView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        strip.addView(nowTitleView);
        nowRemainView = new TextView(this);
        nowRemainView.setTextSize(14);
        nowRemainView.setTextColor(Ui.accent(this));
        nowRemainView.setTypeface(nowRemainView.getTypeface(), android.graphics.Typeface.BOLD);
        nowRemainView.setPadding(dp(10), 0, 0, 0);
        strip.addView(nowRemainView);
        content.addView(strip);
        View stripLine = new View(this);
        stripLine.setBackgroundColor(Ui.line(this));
        content.addView(stripLine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // 今日时间账（v1.23.0，任务页最上方，学 Sunsama）：三个数——今日还剩、必干占、空余。
        // 极简宁静口径：不套卡片、不加边框，一行大字直接落在页面上；refreshStatus 每秒顺手刷新。
        LinearLayout accountBox = new LinearLayout(this);
        accountBox.setOrientation(LinearLayout.VERTICAL);
        accountBox.setPadding(dp(16), dp(10), dp(16), dp(8));
        accountView = new TextView(this);
        accountView.setTextSize(21); // 基准字号：数字按 1.0 显示，标签按 0.62 缩小
        accountView.setTextColor(Ui.text(this));
        accountBox.addView(accountView);
        overflowView = new TextView(this);
        overflowView.setTextSize(13);
        overflowView.setTextColor(Ui.accent(this));
        overflowView.setPadding(0, dp(4), 0, 0);
        overflowView.setVisibility(View.GONE);
        // 点名行点一下展开日历看究竟（日历已开着时不动）
        overflowView.setOnClickListener(v -> { if (!calOpen) toggleCalendar(); });
        accountBox.addView(overflowView);
        content.addView(accountBox);

        // 内容区分屏行（v1.18.0）：左＝表格区（权重 1），右＝日历容器（默认藏）。
        // 日历不再盖在 root 上：它只占日程页内容区、永远在底部三 Tab 之上；
        // 展开时左边就是这张任务表本体，任务行可以直接拖进右侧日历。
        LinearLayout splitRow = new LinearLayout(this);
        splitRow.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(splitRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        // 表格区：表头滚动 + 主体（纵向滚动套横向滚动），两者横向镜像同步
        FrameLayout tableArea = new FrameLayout(this);
        splitRow.addView(tableArea, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        // 日历容器（宽度由 toggleCalendar/拉手决定，展开/收起只动这一列）
        calContainer = new FrameLayout(this);
        calContainer.setBackgroundColor(Ui.surface(this));
        calContainer.setVisibility(View.GONE);
        splitRow.addView(calContainer, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout tableCol = new LinearLayout(this);
        tableCol.setOrientation(LinearLayout.VERTICAL);
        tableArea.addView(tableCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        headerScroll = new HorizontalScrollView(this);
        headerScroll.setHorizontalScrollBarEnabled(false);
        headerInner = new LinearLayout(this);
        headerInner.setOrientation(LinearLayout.HORIZONTAL);
        headerInner.setBackgroundColor(Ui.surface(this));
        headerScroll.addView(headerInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        tableCol.addView(headerScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)));
        View headLine = new View(this);
        headLine.setBackgroundColor(Ui.line(this));
        tableCol.addView(headLine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        ScrollView bodyScroll = new ScrollView(this);
        bodyScrollV = bodyScroll;
        bodyScroll.setFillViewport(true);
        bodyHScroll = new HorizontalScrollView(this);
        bodyHScroll.setHorizontalScrollBarEnabled(false);
        bodyInner = new LinearLayout(this);
        bodyInner.setOrientation(LinearLayout.VERTICAL);
        bodyHScroll.addView(bodyInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bodyScroll.addView(bodyHScroll, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tableCol.addView(bodyScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        headerScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (!syncingScroll && bodyHScroll.getScrollX() != x) {
                syncingScroll = true;
                bodyHScroll.scrollTo(x, 0);
                syncingScroll = false;
            }
        });
        bodyHScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (!syncingScroll && headerScroll.getScrollX() != x) {
                syncingScroll = true;
                headerScroll.scrollTo(x, 0);
                syncingScroll = false;
            }
        });
        // 齿轮：sticky 在表格区右上角（盖在表头滚动区之上，不随横向滚动跑）
        Button gear = new Button(this);
        gear.setBackground(new Ui.GearDrawable(Ui.sub(this), Ui.surface(this)));
        gear.setOnClickListener(v -> dialogColumns());
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(34), dp(38));
        glp.gravity = Gravity.TOP | Gravity.END;
        tableArea.addView(gear, glp);

        // 新增任务 FAB（原顶栏「＋新增」的去向）
        TextView fab = new TextView(this);
        fab.setText("+");
        fab.setTextSize(26);
        fab.setTextColor(Ui.onAccent(this));
        fab.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable fabBg = new android.graphics.drawable.GradientDrawable();
        fabBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        fabBg.setColor(Ui.accent(this));
        fab.setBackground(fabBg);
        fab.setElevation(dp(4));
        fab.setOnClickListener(v -> TaskEditSheet.showCreate(this, store, null, false,
                new TaskEditSheet.Host() {
                    @Override public void onSaved(TaskItem t, boolean created) {
                        adapter.notifyDataSetChanged();
                        refreshStatus();
                    }
                    @Override public void onFocusRule(TaskItem t) { }
                    @Override public void onResetTimer(TaskItem t) { }
                }));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.setMargins(0, 0, dp(18), dp(18));
        scheduleWrap.addView(fab, flp); // FAB 只属于日程页（v1.16.0 起随页面切换显隐）
        fabView = fab;

        buildDrawer(root);
        renderTable();
        refreshStatus();
    }

    // ---------------- 底部三 Tab（v1.16.0） ----------------
    // 日程＝原主页内容整套（顶栏/细条/任务表/FAB 都在 scheduleWrap 里），习惯与知行是
    // 懒构建的同级页面；切换只改可见性，不销毁任何页面，状态不丢。抽屉仍是 root 层
    // 覆盖物；日历自 v1.18.0 起在日程页内容区里与任务表分屏，不再盖住 Tab 栏，
    // 切走再回来保持展开状态（切走时只停掉面板的周期刷新，回来再续上）。

    private void buildTabBar(LinearLayout mainCol) {
        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        mainCol.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Ui.surface(this));
        bar.setPadding(dp(6), dp(4), dp(6), dp(6));
        mainCol.addView(bar);
        String[] icons = {"🗓️", "🌱", "◎"};
        String[] labels = {Strings.tabSchedule(), Strings.tabHabits(), Strings.tabZhixing()};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(2), 0, 0);
            TextView ic = new TextView(this);
            ic.setText(icons[i]);
            ic.setTextSize(19);
            ic.setGravity(Gravity.CENTER);
            cell.addView(ic);
            TextView lab = new TextView(this);
            lab.setText(labels[i]);
            lab.setTextSize(11.5f);
            lab.setGravity(Gravity.CENTER);
            cell.addView(lab);
            View mark = new View(this);
            android.graphics.drawable.GradientDrawable mg = new android.graphics.drawable.GradientDrawable();
            mg.setColor(Ui.accent(this));
            mg.setCornerRadius(dp(2));
            mark.setBackground(mg);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(18), dp(3));
            mlp.topMargin = dp(3);
            cell.addView(mark, mlp);
            cell.setOnClickListener(v -> switchTab(idx));
            bar.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tabCells[i] = cell;
            tabLabels[i] = lab;
            tabMarks[i] = mark;
        }
        paintTabs();
    }

    private void paintTabs() {
        for (int i = 0; i < 3; i++) {
            if (tabLabels[i] == null) continue;
            boolean on = i == currentTab;
            tabLabels[i].setTextColor(on ? Ui.text(this) : Ui.sub(this));
            tabLabels[i].setTypeface(tabLabels[i].getTypeface(),
                    on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tabMarks[i].setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void switchTab(int tab) {
        if (tab < TAB_SCHEDULE || tab > TAB_ZHIXING) tab = TAB_SCHEDULE;
        currentTab = tab;
        if (scheduleWrap != null) scheduleWrap.setVisibility(tab == TAB_SCHEDULE ? View.VISIBLE : View.GONE);
        if (habitContainer != null) habitContainer.setVisibility(tab == TAB_HABITS ? View.VISIBLE : View.GONE);
        if (zhixingContainer != null) zhixingContainer.setVisibility(tab == TAB_ZHIXING ? View.VISIBLE : View.GONE);
        paintTabs();
        // 日历保持离开前的展开状态（v1.18.0）：切走只停面板周期刷新，回来再续上
        if (calendarPanel != null && calOpen) {
            if (tab != TAB_SCHEDULE) calendarPanel.panelStop();
            else calendarPanel.panelStart();
        }
        if (tab == TAB_HABITS) {
            if (habitPage == null) habitPage = new HabitPage(this, habitContainer);
            habitPage.refresh();
        } else if (tab == TAB_ZHIXING) {
            if (zhixingPage == null) zhixingPage = new ZhixingPage(this, zhixingContainer);
            zhixingPage.refresh(store);
        }
    }

    // ---------------- 抽屉 ----------------

    private void buildDrawer(FrameLayout root) {
        drawerRoot = new FrameLayout(this);
        drawerRoot.setVisibility(View.GONE);
        root.addView(drawerRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackgroundColor(android.graphics.Color.argb(90, 0, 0, 0));
        scrim.setOnClickListener(v -> closeDrawer());
        drawerRoot.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Ui.surface(this));
        panel.setPadding(dp(20), dp(22), dp(18), dp(18));
        panel.setOnClickListener(v -> { });
        drawerRoot.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView wm = new TextView(this);
        wm.setText(Strings.appFull());
        wm.setTextSize(17);
        wm.setTextColor(Ui.text(this));
        wm.setTypeface(wm.getTypeface(), android.graphics.Typeface.BOLD);
        panel.addView(wm);

        // 账号区：点整块打开账号弹窗（复用 AccountUi，与设置页同一套）
        LinearLayout acct = new LinearLayout(this);
        acct.setOrientation(LinearLayout.VERTICAL);
        acct.setPadding(0, dp(16), 0, dp(14));
        drawerAccountTitle = new TextView(this);
        drawerAccountTitle.setTextSize(16);
        drawerAccountTitle.setTextColor(Ui.text(this));
        drawerAccountTitle.setTypeface(drawerAccountTitle.getTypeface(), android.graphics.Typeface.BOLD);
        acct.addView(drawerAccountTitle);
        drawerAccountSub = new TextView(this);
        drawerAccountSub.setTextSize(12);
        drawerAccountSub.setTextColor(Ui.sub(this));
        drawerAccountSub.setPadding(0, dp(3), 0, 0);
        acct.addView(drawerAccountSub);
        acct.setOnClickListener(v -> { closeDrawer(); dialogAccount(); });
        panel.addView(acct);
        panel.addView(drawerLine());

        panel.addView(drawerLabel(Strings.language()));
        LinearLayout langSeg = segRow();
        segLangZh = segItem("中文");
        segLangEn = segItem("EN");
        segLangZh.setOnClickListener(v -> { Ui.setLangPref(this, "zh"); recreate(); });
        segLangEn.setOnClickListener(v -> { Ui.setLangPref(this, "en"); recreate(); });
        langSeg.addView(segLangZh);
        langSeg.addView(segLangEn);
        panel.addView(langSeg);

        panel.addView(drawerLabel(Strings.appearance()));
        LinearLayout themeSeg = segRow();
        segThemeLight = segItem(Strings.light());
        segThemeDark = segItem(Strings.dark());
        segThemeLight.setOnClickListener(v -> { Ui.setThemePref(this, "light"); recreate(); });
        segThemeDark.setOnClickListener(v -> { Ui.setThemePref(this, "dark"); recreate(); });
        themeSeg.addView(segThemeLight);
        themeSeg.addView(segThemeDark);
        panel.addView(themeSeg);
        panel.addView(drawerLine());

        panel.addView(drawerRow(Strings.en() ? "Chat with AI" : "和 AI 聊聊", v -> {
            closeDrawer();
            ChatActivity.openGeneral(this);
        }));
        panel.addView(drawerRow(Strings.help(), v -> {
            closeDrawer();
            startActivity(new Intent(this, HelpActivity.class));
        }));
        panel.addView(drawerRow(Strings.settings(), v -> {
            closeDrawer();
            startActivity(new Intent(this, SettingsActivity.class));
        }));
        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView ver = new TextView(this);
        ver.setText(Strings.versionLine());
        ver.setTextSize(11);
        ver.setTextColor(Ui.sub(this));
        panel.addView(ver);
        updateDrawerAccount();
        refreshSegStates();
    }

    private View drawerLine() {
        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, dp(10), 0, dp(2));
        line.setLayoutParams(lp);
        return line;
    }

    private TextView drawerLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(Ui.sub(this));
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    private TextView drawerRow(String s, View.OnClickListener cl) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(Ui.text(this));
        t.setPadding(0, dp(11), 0, dp(11));
        t.setOnClickListener(cl);
        return t;
    }

    /** 分段控件外框（语言/外观共用）：圆角描边，选中项填强调色。 */
    private LinearLayout segRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, Ui.line(this));
        bg.setColor(Ui.surface(this));
        r.setBackground(bg);
        r.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r.setLayoutParams(lp);
        return r;
    }

    private TextView segItem(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(5), dp(16), dp(5));
        return t;
    }

    private void segPaint(TextView t, boolean selected) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(selected ? Ui.accent(this) : android.graphics.Color.TRANSPARENT);
        t.setBackground(bg);
        t.setTextColor(selected ? Ui.onAccent(this) : Ui.sub(this));
    }

    private void refreshSegStates() {
        if (segLangZh == null) return;
        boolean en = Ui.en(this);
        segPaint(segLangZh, !en);
        segPaint(segLangEn, en);
        boolean dark = Ui.dark(this);
        segPaint(segThemeLight, !dark);
        segPaint(segThemeDark, dark);
    }

    private void updateDrawerAccount() {
        if (drawerAccountTitle == null) return;
        if (Supa.isLoggedIn(this)) {
            drawerAccountTitle.setText(Supa.userEmail(this));
            drawerAccountSub.setText(Strings.en()
                    ? (SbSync.isSyncing() ? "Syncing…" : SbSync.pendingCount(this, store) > 0
                        ? SbSync.pendingCount(this, store) + " pending" : "Synced")
                    : SbSync.statusText(this, store));
        } else {
            drawerAccountTitle.setText(Strings.notLoggedIn());
            drawerAccountSub.setText(Strings.loginHint());
        }
    }

    private void openDrawer() {
        if (drawerRoot == null) return;
        View panel = ((ViewGroup) drawerRoot).getChildAt(1);
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.68f);
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        if (lp.width != w) { lp.width = w; panel.setLayoutParams(lp); }
        updateDrawerAccount();
        refreshSegStates();
        drawerRoot.setVisibility(View.VISIBLE);
    }

    private void closeDrawer() {
        if (drawerRoot != null) drawerRoot.setVisibility(View.GONE);
    }

    // ---------------- 日历面板 ----------------

    private void toggleCalendar() {
        if (calOpen) { closeCalendar(); return; }
        if (calendarPanel == null) {
            calendarPanel = new CalendarPanel(this);
            calendarPanel.setBlockFocusHost(taskId -> expandTaskFromCalendar(taskId));
            calContainer.addView(calendarPanel, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            calContainer.addView(buildCalGrip(), new FrameLayout.LayoutParams(
                    dp(16), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START));
        }
        int rootW = ((View) calContainer.getParent()).getWidth();
        if (rootW <= 0) rootW = getResources().getDisplayMetrics().widthPixels;
        int w = Math.round(rootW * Ui.calPanelPct(this) / 100f);
        w = Math.max(dp(160), Math.min(w, Math.round(rootW * 0.94f)));
        ViewGroup.LayoutParams lp = calContainer.getLayoutParams();
        lp.width = w;
        calContainer.setLayoutParams(lp);
        calContainer.setVisibility(View.VISIBLE);
        calOpen = true;
        if (fabView != null) fabView.setVisibility(View.GONE); // FAB 不压日历（v1.18.0）
        renderTable(); // 行按分屏形态重画：play 按钮＋已排标记＋可拖
        calendarPanel.panelStart();
        calContainer.post(() -> { if (calendarPanel != null) calendarPanel.refreshNow(); });
    }

    private void closeCalendar() {
        calOpen = false;
        if (calendarPanel != null) calendarPanel.panelStop();
        if (calContainer != null) calContainer.setVisibility(View.GONE);
        if (fabView != null) fabView.setVisibility(View.VISIBLE);
        renderTable(); // 行恢复普通形态（去掉分屏专属的 play 按钮/已排标记/拖拽）
    }

    /** 点日历里的计划块 → 在主页任务列表里定位并展开该任务（v1.18.0，宿主回调）。 */
    public void expandTaskFromCalendar(final String taskId) {
        if (taskId == null) return;
        expandedTaskId = taskId;
        renderTable();
        if (bodyScrollV != null) {
            bodyScrollV.post(() -> {
                for (int i = 0; i < bodyInner.getChildCount(); i++) {
                    View ch = bodyInner.getChildAt(i);
                    if (taskId.equals(ch.getTag())) {
                        bodyScrollV.smoothScrollTo(0, Math.max(0, ch.getTop() - dp(4)));
                        break;
                    }
                }
            });
        }
    }

    // ---------------- 分屏行拖拽（v1.18.0）：任务列表本体 → 右侧日历 ----------------
    // 跨容器实现：行上的触摸由观察式监听旁观判定（不抢点击/勾选/长按），向右横拖过
    // 阈值才「拾起」；拾起后影子块挂在 root 上跟随手指，落点预览与几何换算交给
    // CalendarPanel.listDragOver（计划栏琥珀色预览块，5 分钟吸附），松手经
    // CalendarPanel.listDrop → SyncManager.addScheduleBlock 落盘，拖回/拖出＝取消零写入。

    private TextView dragGhost;

    private void attachDragObserver(View v, View.OnTouchListener l, View exclude) {
        if (v == null) return;
        if (v != exclude) v.setOnTouchListener(l);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) attachDragObserver(g.getChildAt(i), l, exclude);
        }
    }

    private void showDragGhost(TaskItem t, float rawX, float rawY) {
        hideDragGhost();
        dragGhost = new TextView(this);
        dragGhost.setText(t.title + " · " + Strings.fmtDur(t.durationMinutes));
        dragGhost.setTextSize(13);
        dragGhost.setSingleLine(true);
        dragGhost.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dragGhost.setTextColor(Ui.text(this));
        dragGhost.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(Ui.surface(this));
        bg.setStroke(dp(1), Ui.accent(this));
        dragGhost.setBackground(bg);
        dragGhost.setElevation(dp(6));
        rootFrame.addView(dragGhost, new FrameLayout.LayoutParams(dp(150), dp(38)));
        moveDragGhost(rawX, rawY);
    }

    private void moveDragGhost(float rawX, float rawY) {
        if (dragGhost == null || rootFrame == null) return;
        int[] rl = new int[2];
        rootFrame.getLocationOnScreen(rl);
        dragGhost.setX(rawX - rl[0] - dragGhost.getWidth() / 4f);
        dragGhost.setY(rawY - rl[1] - dragGhost.getHeight() / 2f);
    }

    private void hideDragGhost() {
        if (dragGhost != null && rootFrame != null) rootFrame.removeView(dragGhost);
        dragGhost = null;
    }

    /**
     * 行拖拽观察者：DOWN/MOVE 全程 return false，行内原有点击、勾选、长按、展开照常；
     * 竖拖（滚动）与向左横拖（表格横滚）先于阈值被判死并放行拦截，滚动容器随后接管；
     * 只有向右横拖过 dp(12) 才拾起（朝日历方向，与设计稿一致），拾起后 return true 独占手势。
     */
    private class RowDragObserver implements View.OnTouchListener {
        private final TaskItem task;
        private final View rowRoot;
        private float downX, downY;
        private boolean picked, dead;
        private int dropMin = -1;

        RowDragObserver(TaskItem task, View rowRoot) {
            this.task = task;
            this.rowRoot = rowRoot;
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (calendarPanel == null || !calOpen) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    picked = false;
                    dead = false;
                    dropMin = -1;
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                    if (!picked) {
                        if (dead) return false;
                        if (Math.abs(dy) > dp(10) && Math.abs(dy) > Math.abs(dx)) {
                            dead = true; // 竖拖＝列表滚动
                            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                            return false;
                        }
                        if (dx > dp(12) && dx >= Math.abs(dy)) {
                            picked = true;
                            listDragging = true;
                            try {
                                android.os.Vibrator vb = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                                if (vb != null) vb.vibrate(30);
                            } catch (Exception ignored) { }
                            rowRoot.setAlpha(0.35f);
                            showDragGhost(task, ev.getRawX(), ev.getRawY());
                            dropMin = calendarPanel.listDragOver(task, ev.getRawX(), ev.getRawY());
                            return true;
                        }
                        if (Math.abs(dx) > dp(12)) {
                            dead = true; // 向左横拖＝表格横滚
                            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        return false;
                    }
                    moveDragGhost(ev.getRawX(), ev.getRawY());
                    dropMin = calendarPanel.listDragOver(task, ev.getRawX(), ev.getRawY());
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!picked) return false;
                    endDrag(dropMin >= 0);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (!picked) return false;
                    endDrag(false);
                    return true;
                default:
                    return picked;
            }
        }

        private void endDrag(boolean commit) {
            picked = false;
            hideDragGhost();
            rowRoot.setAlpha(1f);
            if (commit) {
                final int dm = dropMin;
                calendarPanel.listDrop(task, dm, () -> { listDragging = false; renderTable(); });
            } else {
                calendarPanel.listDragCancel();
                listDragging = false;
                renderTable();
            }
        }
    }

    /** 面板左缘拉手：拖它决定展开幅度；拖到 <16% 松手自动关，上限 94%。 */
    private View buildCalGrip() {
        FrameLayout grip = new FrameLayout(this);
        grip.setBackgroundColor(Ui.muted(this));
        View line = new View(this);
        line.setBackgroundColor(Ui.sub(this));
        grip.addView(line, new FrameLayout.LayoutParams(dp(4), dp(48), Gravity.CENTER));
        grip.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private int startW;
            @Override public boolean onTouch(View v, MotionEvent e) {
                int rootW = ((View) calContainer.getParent()).getWidth();
                if (rootW <= 0) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getRawX();
                        startW = calContainer.getLayoutParams().width;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int nw = startW + Math.round(startX - e.getRawX());
                        nw = Math.max(dp(60), Math.min(nw, Math.round(rootW * 0.94f)));
                        ViewGroup.LayoutParams lp = calContainer.getLayoutParams();
                        lp.width = nw;
                        calContainer.setLayoutParams(lp);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        int w = calContainer.getLayoutParams().width;
                        if (w < rootW * 0.16f) { closeCalendar(); return true; }
                        Ui.setCalPanelPct(MainActivity.this, Math.round(w * 100f / rootW));
                        if (calendarPanel != null) calendarPanel.refreshNow();
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
        return grip;
    }

    // ---------------- 列显隐（与设置页共用 Ui 同一份本地存储） ----------------

    private void dialogColumns() {
        final String[] ids = {"start", "used", "left", "dur", "dl", "pri", "sc"};
        String[] labels = new String[ids.length];
        boolean[] checked = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) {
            labels[i] = SettingsActivity.colLabel(ids[i]);
            checked[i] = Ui.colVisible(this, ids[i], SettingsActivity.colDefVisible(ids[i]));
        }
        new AlertDialog.Builder(this)
                .setTitle(Strings.columnsTitle())
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    Ui.setColVisible(this, ids[which], isChecked);
                    renderTable();
                })
                .setPositiveButton(Strings.en() ? "Done" : "完成", null)
                .show();
    }

    private void refreshStatus() {
        if (nowTitleView == null) return;
        if (TimerState.isRunning(this)) {
            TaskItem cur = store.byId(TimerState.taskId(this));
            nowTitleView.setText(cur != null ? cur.title : TimerState.taskTitle(this));
            nowRemainView.setText(Strings.remain() + " "
                    + mmss(Math.max(0, TimerState.endAt(this) - System.currentTimeMillis())));
        } else if (TimerState.pausedTaskId(this) != null) {
            TaskItem cur = store.byId(TimerState.pausedTaskId(this));
            nowTitleView.setText((cur != null ? cur.title : TimerState.pausedTitle(this))
                    + Strings.pausedTag());
            nowRemainView.setText(Strings.remain() + " " + mmss(TimerState.pausedRemainMs(this)));
        } else {
            TaskItem first = null;
            for (TaskItem t : store.visibleSorted()) {
                if (!t.isDoneToday()) { first = t; break; }
            }
            if (first != null) {
                nowTitleView.setText(first.title);
                nowRemainView.setText(Strings.plannedMin(first.durationMinutes));
            } else {
                nowTitleView.setText(Strings.appFull());
                nowRemainView.setText("");
            }
        }
        if (liveLeftView != null && TimerState.isRunning(this)) {
            liveLeftView.setText(mmss(Math.max(0, TimerState.endAt(this) - System.currentTimeMillis())));
        }
        updateDrawerAccount();
        refreshTimeAccount();
    }

    /**
     * 今日时间账刷新（v1.23.0）：三个数拼成一行——标签小字、数字大字（宁静口径：不堆边框按钮）。
     * 超了（空余为负）时空余数字走点缀色并加「超了」。文本没变就不重设，避免每秒闪烁；
     * 溢出点名行每轮都重算（本地缓存口径，排程落盘后回调也会再触发一次本方法）。
     */
    private void refreshTimeAccount() {
        if (accountView == null) return;
        DayAccount.Result a = DayAccount.compute(store);
        boolean en = Strings.en();
        String remain = DayAccount.fmtDur(a.remainMin);
        String must = DayAccount.fmtDur(a.mustBufferedMin);
        String freeAbs = DayAccount.fmtDur(Math.abs(a.freeMin));
        String sig = a.remainMin + "|" + a.mustBufferedMin + "|" + a.freeMin + "|" + en;
        if (!sig.equals(accountShown)) {
            accountShown = sig;
            String l1 = en ? "Left today " : "今日还剩 ";
            String l2 = en ? "　Must-do " : "　必干占 ";
            String l3 = en ? "　Free " : "　空余 ";
            SpannableStringBuilder sb = new SpannableStringBuilder();
            appendAccountPart(sb, l1, remain);
            appendAccountPart(sb, l2, must);
            String freeText = (a.over() ? "−" : "") + freeAbs + (a.over() ? (en ? " over" : " 超了") : "");
            int freeNumStart = appendAccountPart(sb, l3, freeText);
            if (a.over()) {
                sb.setSpan(new ForegroundColorSpan(Ui.accent(this)), freeNumStart, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            accountView.setText(sb);
        }
        if (overflowView != null) {
            LocalAutoScheduler.Plan p = LocalAutoScheduler.computeLocal(this, store);
            if (!p.overflow.isEmpty()) {
                overflowView.setText(en
                        ? p.overflow.size() + " must-do task(s) don't fit today — tap to see the calendar"
                        : "还有 " + p.overflow.size() + " 件必干排不进今天，点开日历看看");
                overflowView.setVisibility(View.VISIBLE);
            } else {
                overflowView.setVisibility(View.GONE);
            }
        }
    }

    /** 时间账一行的一段：标签小字次级色 ＋ 数字大字粗体。返回数字段的起始下标。 */
    private int appendAccountPart(SpannableStringBuilder sb, String label, String num) {
        int s0 = sb.length();
        sb.append(label);
        sb.setSpan(new ForegroundColorSpan(Ui.sub(this)), s0, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(0.62f), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int s1 = sb.length();
        sb.append(num);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s1, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s1;
    }

    // ---------------- 任务行 ----------------
    /** 列表行：0 今日任务 / 1 历史日期头（可展开）/ 2 历史任务（某日完成的） */
    private static class Row {
        int kind;
        TaskItem t;
        String date;
        int count;
        static Row task(TaskItem t) { Row r = new Row(); r.kind = 0; r.t = t; return r; }
        static Row header(String date, int count) { Row r = new Row(); r.kind = 1; r.date = date; r.count = count; return r; }
        static Row history(TaskItem t) { Row r = new Row(); r.kind = 2; r.t = t; return r; }
    }

    // ---------------- 任务表渲染（v1.8.0） ----------------
    // 列模型：task 常显；start/used/left/dur/dl/pri/sc 可显隐、宽度可拖（Ui 本地偏好）。
    // 渲染方式与旧 ListView 一致地整表重建（notifyDataSetChanged 仍是唯一刷新入口），
    // 行内所有操作都接回原有真实入口（onPlay / 各 dialog* / markDone 语义），无平行逻辑。
    private static class ColDef {
        final String id;
        final int defW;
        ColDef(String id, int defW) { this.id = id; this.defW = defW; }
    }

    private static final ColDef[] ALL_COLS = {
            new ColDef("task", 200), new ColDef("start", 80), new ColDef("used", 56),
            new ColDef("left", 56), new ColDef("dur", 56), new ColDef("dl", 92),
            new ColDef("pri", 52), new ColDef("sc", 76),
    };

    private final java.util.Map<String, java.util.List<View>> colViews = new java.util.HashMap<>();
    private final java.util.List<View> fullViews = new ArrayList<>();
    private int curTableW;
    private float dragStartX;
    private int dragStartW;
    private int dragCurW;

    private java.util.List<ColDef> visibleCols() {
        java.util.List<ColDef> out = new ArrayList<>();
        for (ColDef c : ALL_COLS) {
            if ("task".equals(c.id) || Ui.colVisible(this, c.id, SettingsActivity.colDefVisible(c.id))) {
                out.add(c);
            }
        }
        return out;
    }

    private int colPx(ColDef c) { return dp(Ui.colWidthDp(this, c.id, c.defW)); }

    private int tableWidthPx() {
        int w = dp(30) + (calOpen ? dp(38) : 0); // 分屏时行首多一个 play 按钮列
        for (ColDef c : visibleCols()) w += colPx(c);
        return w + dp(36);
    }

    /** 分屏形态下今日已排映射：taskId → 当天最早一块的开始分钟（与日历 planBlocks 同一数据源）。 */
    private void rebuildSplitScheduled() {
        splitScheduled.clear();
        if (!calOpen) return;
        try {
            org.json.JSONObject sch = SyncManager.latestSchedule(this);
            if (sch == null || !TaskItem.today().equals(sch.optString("date", ""))) return;
            org.json.JSONArray arr = sch.optJSONArray("blocks");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String tid = o.optString("taskId", "");
                int s = parseHhmm(o.optString("start", ""));
                if (tid.isEmpty() || s < 0) continue;
                Integer cur = splitScheduled.get(tid);
                if (cur == null || s < cur) splitScheduled.put(tid, s);
            }
        } catch (Exception ignored) { }
    }

    private static int parseHhmm(String hhmm) {
        try {
            String[] p = hhmm.trim().split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            if (h < 0 || h > 24 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String hhmmOfMin(int min) {
        return String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60);
    }

    private String colTitle(String id) {
        switch (id) {
            case "task": return Strings.colTask();
            case "start": return Strings.colStart();
            case "used": return Strings.colUsed();
            case "left": return Strings.colLeft();
            case "dur": return Strings.colDur();
            case "dl": return Strings.colDeadline();
            case "pri": return Strings.colPriority();
            case "sc": return Strings.colScenarios();
            default: return id;
        }
    }

    private void regCol(String id, View v) {
        colViews.computeIfAbsent(id, k -> new ArrayList<>()).add(v);
    }

    private void setW(View v, int px) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.width != px) { lp.width = px; v.setLayoutParams(lp); }
    }

    /** 拖列宽时实时调宽：只改已注册视图的 LayoutParams，不重建（手势不中断）；松手才落盘。 */
    private void applyColWidth(String id, int newPx) {
        int delta = newPx - dragCurW;
        if (delta == 0) return;
        dragCurW = newPx;
        java.util.List<View> vs = colViews.get(id);
        if (vs != null) for (View v : vs) setW(v, newPx);
        curTableW += delta;
        setW(headerInner, curTableW);
        setW(bodyInner, curTableW);
        for (View v : fullViews) setW(v, curTableW);
    }

    private void renderTable() {
        if (bodyInner == null) return;
        if (listDragging) return; // 行正被拖向日历：整表重建会把手里的行销毁，拖完再画
        rebuildSplitScheduled();
        colViews.clear();
        fullViews.clear();
        liveLeftView = null;
        curTableW = tableWidthPx();
        buildHeader();
        bodyInner.removeAllViews();
        for (Row r : buildRows()) {
            if (r.kind == 1) bodyInner.addView(dateHeaderView(r));
            else if (r.kind == 2) bodyInner.addView(historyRowView(r));
            else bodyInner.addView(taskRowView(r.t));
        }
        setW(headerInner, curTableW);
        setW(bodyInner, curTableW);
    }

    /** 行数据（与旧 TaskAdapter.reload 完全同口径）：可见任务在前，其后按完成日期倒序的历史分组。 */
    private java.util.List<Row> buildRows() {
        java.util.List<Row> rows = new ArrayList<>();
        for (TaskItem t : store.visibleSorted()) rows.add(Row.task(t));
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        java.util.Map<String, java.util.List<TaskItem>> byDate = new java.util.TreeMap<>(
                java.util.Collections.reverseOrder());
        for (TaskItem t : store.tasks) {
            if (t.deleted || t.doneDates == null) continue;
            for (String d : t.doneDates) {
                if (d != null && d.compareTo(today) < 0) {
                    byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(t);
                }
            }
        }
        for (java.util.Map.Entry<String, java.util.List<TaskItem>> e : byDate.entrySet()) {
            rows.add(Row.header(e.getKey(), e.getValue().size()));
            if (expandedDays.contains(e.getKey())) {
                for (TaskItem t : e.getValue()) rows.add(Row.history(t));
            }
        }
        return rows;
    }

    private void buildHeader() {
        headerInner.removeAllViews();
        if (calOpen) { // 与行首 play 按钮列对齐的空位
            View pb = new View(this);
            pb.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
            headerInner.addView(pb);
        }
        View cb = new View(this);
        cb.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(38)));
        headerInner.addView(cb);
        for (ColDef c : visibleCols()) headerInner.addView(headerCellView(c));
        View gearSpacer = new View(this);
        gearSpacer.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(38)));
        headerInner.addView(gearSpacer);
    }

    private View headerCellView(final ColDef c) {
        FrameLayout cell = new FrameLayout(this);
        cell.setLayoutParams(new LinearLayout.LayoutParams(colPx(c), dp(38)));
        TextView lab = new TextView(this);
        lab.setText(colTitle(c.id));
        lab.setTextSize(12);
        lab.setTextColor(Ui.sub(this));
        lab.setGravity(Gravity.CENTER_VERTICAL);
        lab.setPadding(dp(6), 0, dp(12), 0);
        lab.setSingleLine(true);
        cell.addView(lab, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        cell.addView(line, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM));
        // 右缘拖拽区：按住横拖改本列宽度
        View handle = new View(this);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(dp(14),
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
        cell.addView(handle, hlp);
        handle.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartX = e.getRawX();
                    dragStartW = colPx(c);
                    dragCurW = dragStartW;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int nw = dragStartW + Math.round(e.getRawX() - dragStartX);
                    nw = Math.max(dp(44), Math.min(nw, dp(340)));
                    applyColWidth(c.id, nw);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Ui.setColWidthDp(MainActivity.this, c.id,
                            Math.round(dragCurW / getResources().getDisplayMetrics().density));
                    return true;
                default:
                    return false;
            }
        });
        regCol(c.id, cell);
        return cell;
    }

    private View dateHeaderView(final Row r) {
        TextView h = new TextView(this);
        boolean open = expandedDays.contains(r.date);
        h.setText((open ? "▼ " : "▶ ") + r.date + (Strings.en()
                ? " · " + r.count + " done (tap to open)"
                : " · 完成 " + r.count + " 个（点开看那天做了什么）"));
        h.setTextSize(14);
        h.setTextColor(Ui.sub(this));
        h.setBackgroundColor(Ui.muted(this));
        h.setPadding(dp(12), dp(10), dp(12), dp(10));
        h.setLayoutParams(new LinearLayout.LayoutParams(curTableW, LinearLayout.LayoutParams.WRAP_CONTENT));
        h.setOnClickListener(v -> {
            if (expandedDays.contains(r.date)) expandedDays.remove(r.date);
            else expandedDays.add(r.date);
            renderTable();
        });
        fullViews.add(h);
        return h;
    }

    private View historyRowView(final Row r) {
        TextView h = new TextView(this);
        h.setText("✓ " + r.t.title + (Strings.en()
                ? " (" + r.t.durationMinutes + " min)"
                : "（" + r.t.durationMinutes + " 分）"));
        h.setTextSize(14);
        h.setTextColor(Ui.sub(this));
        h.setPadding(dp(28), dp(8), dp(12), dp(8));
        h.setLayoutParams(new LinearLayout.LayoutParams(curTableW, LinearLayout.LayoutParams.WRAP_CONTENT));
        h.setOnClickListener(v -> dialogNote(r.t));
        fullViews.add(h);
        return h;
    }

    private String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private String hmOf(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(java.util.Locale.US, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private String startCellText(TaskItem t) {
        long ms = t.nextStartMs();
        if (ms <= 0) return "—";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        String day = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return day.equals(TaskItem.today()) ? hmOf(ms) : day.substring(5) + " " + hmOf(ms);
    }

    /** Deadline 格（v1.8.0）：只放日期/每日＋截止时刻；开始时间独立成列，不再拼 ▶ 尾巴。 */
    private String deadlineOnlyText(TaskItem t) {
        if ("daily".equals(t.repeat)) return Strings.dateDaily();
        if (t.deadline == null) return Strings.dateEmpty();
        return t.deadline.substring(5) + (t.deadlineTime == null ? "" : "\n" + t.deadlineTime);
    }

    private View taskRowView(final TaskItem t) {
        final boolean done = t.isDoneToday();
        final boolean isRunningThis = TimerState.isRunning(this) && t.id.equals(TimerState.taskId(this));
        final boolean isPausedThis = !TimerState.isRunning(this) && t.id.equals(TimerState.pausedTaskId(this));
        final boolean expanded = t.id.equals(expandedTaskId);

        LinearLayout rowRoot = new LinearLayout(this);
        rowRoot.setOrientation(LinearLayout.HORIZONTAL);
        rowRoot.setLayoutParams(new LinearLayout.LayoutParams(curTableW, LinearLayout.LayoutParams.WRAP_CONTENT));
        rowRoot.setBackgroundColor(done ? Ui.doneBg(this) : isRunningThis ? Ui.runningBg(this) : Ui.bg(this));
        rowRoot.setTag(t.id); // 日历点块时按 id 定位滚动（expandTaskFromCalendar）
        fullViews.add(rowRoot);
        View bar = new View(this);
        bar.setBackgroundColor(isRunningThis ? Ui.accent(this) : android.graphics.Color.TRANSPARENT);
        rowRoot.addView(bar, new LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout colV = new LinearLayout(this);
        colV.setOrientation(LinearLayout.VERTICAL);
        rowRoot.addView(colV, new LinearLayout.LayoutParams(curTableW - dp(3),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setOnLongClickListener(v -> { dialogRowMenu(t); return true; });
        colV.addView(row);

        // 分屏形态专属（v1.18.0）：行首 play 按钮——点一下直接开始/暂停这个任务，
        // 不必展开、不必回主页；走的还是 onPlay（与展开区主 chip 同一路径、同一切换语义）。
        TextView playBtn = null;
        if (calOpen) {
            playBtn = new TextView(this);
            playBtn.setText(isRunningThis ? "⏸" : "▶");
            playBtn.setTextSize(17);
            playBtn.setGravity(Gravity.CENTER);
            playBtn.setTextColor(isRunningThis || isPausedThis ? Ui.accent(this) : Ui.sub(this));
            playBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(44)));
            final TextView pb = playBtn;
            pb.setOnClickListener(v -> onPlay(t));
            row.addView(playBtn);
        }

        CheckBox doneBox = new CheckBox(this);
        doneBox.setChecked(done);
        doneBox.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(44)));
        doneBox.setOnClickListener(v -> {
            boolean nowDone = doneBox.isChecked();
            if (nowDone) { t.markDone(); StartBaseline.markCompleted(this, t.id); }
            else { t.markUndone(); StartBaseline.clearCompleted(this, t.id); }
            store.save();
            SbSync.pushDoneAsync(this, t.id); // 完成状态立刻定向写上云并回读确认（v1.14.0 防弹回）
            adapter.notifyDataSetChanged();
            refreshStatus();
            if (nowDone) fireGuardAfterManualDone();
        });
        row.addView(doneBox);

        for (ColDef c : visibleCols()) row.addView(cellView(c, t, done, isRunningThis, isPausedThis, expanded));

        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        colV.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        if (expanded) colV.addView(expansionView(t, done, isRunningThis, isPausedThis));
        // 分屏拖拽（v1.18.0）：未完成、且未排进今日计划的行可横拖进右侧日历。
        // 观察式监听只旁观手势、不接管行内点击/勾选/长按（见 RowDragObserver）。
        if (calOpen && calendarPanel != null && !done && !splitScheduled.containsKey(t.id)) {
            RowDragObserver obs = new RowDragObserver(t, rowRoot);
            attachDragObserver(row, obs, playBtn);
        }
        return rowRoot;
    }

    private View cellView(final ColDef c, final TaskItem t, boolean done,
                          boolean isRunningThis, boolean isPausedThis, boolean expanded) {
        TextView v = new TextView(this);
        v.setTextSize(13);
        v.setTextColor(Ui.text(this));
        v.setPadding(dp(6), 0, dp(2), 0);
        v.setLayoutParams(new LinearLayout.LayoutParams(colPx(c), LinearLayout.LayoutParams.WRAP_CONTENT));
        switch (c.id) {
            case "task": {
                // 任务格：名字（点它展开/收起）＋右侧小箭头；外层 FrameLayout 承接列宽注册
                FrameLayout cell = new FrameLayout(this);
                cell.setLayoutParams(new LinearLayout.LayoutParams(colPx(c),
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                v.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
                String titleText = ("must".equals(t.priority) ? "★ " : "") + t.title
                        + (isPausedThis ? Strings.pausedTag() : "");
                Integer plannedMin = (calOpen && !done) ? splitScheduled.get(t.id) : null;
                boolean rolledMark = Rollover.isRolled(this, t.id);
                if (plannedMin != null || rolledMark) {
                    SpannableStringBuilder sb = new SpannableStringBuilder(titleText);
                    if (plannedMin != null) {
                        // 分屏形态「已排 HH:mm」小标记（v1.18.0）：点缀色、小一号，接在标题后
                        int s0 = sb.length();
                        sb.append("  ·  ").append(Strings.calPlannedChip(hhmmOfMin(plannedMin)));
                        sb.setSpan(new ForegroundColorSpan(Ui.accent(this)), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new RelativeSizeSpan(0.82f), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (rolledMark) {
                        // 昨日滚入标记（v1.23.0）：小小的「昨」字，次级色、低调，当天有效
                        int s0 = sb.length();
                        sb.append("  ").append(Strings.en() ? "yday" : "昨");
                        sb.setSpan(new ForegroundColorSpan(Ui.sub(this)), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.setSpan(new RelativeSizeSpan(0.78f), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    v.setText(sb);
                } else {
                    v.setText(done ? titleText + " ✓" : titleText);
                }
                v.setTextSize(15);
                // 任务名行内至多两行（v1.22.0）：太长先换行到第二行，两行还放不下才省略号；
                // 展开这一行时完整显示、不截断——认任务靠第一眼，展开就是要看全。
                if (expanded) {
                    v.setMaxLines(Integer.MAX_VALUE);
                    v.setEllipsize(null);
                } else {
                    v.setMaxLines(2);
                    v.setEllipsize(android.text.TextUtils.TruncateAt.END);
                }
                v.setPadding(dp(6), 0, dp(16), 0);
                v.setTextColor(done ? Ui.sub(this) : Ui.text(this));
                if (isRunningThis) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
                if (done) v.setPaintFlags(v.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                v.setOnClickListener(x -> {
                    expandedTaskId = expanded ? null : t.id;
                    renderTable();
                });
                cell.addView(v);
                TextView chev = new TextView(this);
                chev.setText("›");
                chev.setTextSize(15);
                chev.setTextColor(Ui.sub(this));
                chev.setGravity(Gravity.CENTER);
                if (expanded) chev.setRotation(90);
                FrameLayout.LayoutParams chlp = new FrameLayout.LayoutParams(dp(16), dp(24),
                        Gravity.END | Gravity.CENTER_VERTICAL);
                cell.addView(chev, chlp);
                regCol(c.id, cell);
                return cell;
            }
            case "start":
                v.setText(startCellText(t));
                v.setTextColor(t.nextStartMs() > 0 ? Ui.link(this) : Ui.sub(this));
                v.setOnClickListener(x -> dialogDeadline(t));
                break;
            case "used": {
                int usedMin = (int) (SessionLog.totalMsForTask(this, t.id) / 60000);
                v.setText(Strings.fmtMin(usedMin));
                v.setOnClickListener(x -> dialogDuration(t));
                break;
            }
            case "left":
                if (isRunningThis) {
                    v.setText(mmss(Math.max(0, TimerState.endAt(this) - System.currentTimeMillis())));
                    v.setTextColor(Ui.accent(this));
                    v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
                    liveLeftView = v;
                } else if (isPausedThis) {
                    v.setText(mmss(TimerState.pausedRemainMs(this)));
                    v.setTextColor(Ui.accent(this));
                } else if (done) {
                    v.setText("—");
                    v.setTextColor(Ui.sub(this));
                } else {
                    int usedToday = (int) (SessionLog.todayMsForTask(this, t.id) / 60000);
                    v.setText(Strings.fmtMin(Math.max(0, t.durationMinutes - usedToday)));
                }
                break;
            case "dur":
                v.setText(Strings.fmtDur(t.durationMinutes)
                        + (t.actualMinutes > 0 ? "\n" + (Strings.en() ? "act " : "实") + t.actualMinutes : ""));
                v.setTextColor(Ui.link(this));
                v.setOnClickListener(x -> dialogDuration(t));
                break;
            case "dl":
                v.setText(deadlineOnlyText(t));
                v.setTextColor(t.deadline == null && !"daily".equals(t.repeat) ? Ui.sub(this) : Ui.link(this));
                v.setOnClickListener(x -> dialogDeadline(t));
                break;
            case "pri":
                if ("must".equals(t.priority)) {
                    v.setText(Strings.priMust());
                    v.setTextColor(Ui.danger(this));
                } else if ("secondary".equals(t.priority)) {
                    v.setText(Strings.priSecondary());
                    v.setTextColor(Ui.sub(this));
                } else {
                    v.setText("—");
                    v.setTextColor(Ui.sub(this));
                }
                v.setOnClickListener(x -> dialogTitle(t));
                break;
            case "sc":
                v.setText(t.scenarios.isEmpty() ? Strings.scenariosEmpty() : String.join("、", t.scenarios));
                v.setTextColor(t.scenarios.isEmpty() ? Ui.sub(this) : Ui.link(this));
                v.setOnClickListener(x -> dialogScenarios(t));
                break;
            default:
                break;
        }
        regCol(c.id, v);
        return v;
    }

    /** 展开面板（手风琴）：笔记＋元信息＋操作 chips，全部接现有真实入口。 */
    private View expansionView(final TaskItem t, boolean done,
                               final boolean isRunningThis, boolean isPausedThis) {
        LinearLayout exp = new LinearLayout(this);
        exp.setOrientation(LinearLayout.VERTICAL);
        exp.setBackgroundColor(Ui.muted(this));
        exp.setPadding(dp(42), dp(10), dp(14), dp(12));
        boolean hasNote = t.note != null && !t.note.trim().isEmpty();
        TextView note = new TextView(this);
        note.setText(hasNote ? t.note : Strings.noNote());
        note.setTextSize(13);
        note.setTextColor(hasNote ? Ui.text(this) : Ui.sub(this));
        note.setOnClickListener(v -> dialogNote(t));
        exp.addView(note);

        int usedMin = (int) (SessionLog.totalMsForTask(this, t.id) / 60000);
        int segs = SessionLog.countForTask(this, t.id)
                + (t.id.equals(SessionLog.openTaskId(this)) ? 1 : 0);
        String startPart = t.nextStartMs() > 0
                ? Strings.metaStart(startCellText(t)) : Strings.metaNotStarted();
        String alarmPart = "";
        if (t.alarmMs() > 0) alarmPart += " · ⏰ " + t.alarmAt.substring(5).replace('T', ' ');
        if (t.alarmOnTimerEnd) {
            alarmPart += " · " + (Strings.en() ? "Rings at end" : "结束响铃");
        }
        TextView meta = new TextView(this);
        meta.setText(startPart + " · " + Strings.metaUsed(usedMin, segs)
                + " · " + Strings.metaFocus(focusCellText(t).toString()) + alarmPart);
        meta.setTextSize(12);
        meta.setTextColor(Ui.sub(this));
        meta.setPadding(0, dp(6), 0, 0);
        exp.addView(meta);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(10), 0, 0);
        String primary = isRunningThis ? Strings.chipPause()
                : isPausedThis ? Strings.chipResume() : Strings.chipStart();
        chips.addView(chip(primary, true, v -> onPlay(t)));
        chips.addView(chip(Strings.chipEdit(), false, v -> TaskEditSheet.showEdit(this, store, t,
                new TaskEditSheet.Host() {
                    @Override public void onSaved(TaskItem saved, boolean created) {
                        adapter.notifyDataSetChanged();
                        refreshStatus();
                    }
                    @Override public void onFocusRule(TaskItem task) { dialogFocus(task); }
                    @Override public void onResetTimer(TaskItem task) { resetTask(task); }
                })));
        chips.addView(chip(Strings.chipAlarm(), false, v -> dialogAlarm(t)));
        if (isRunningThis) {
            chips.addView(chip(Strings.chipPlus15(), false, v -> {
                TimerService.extendTimer(MainActivity.this, 15 * 60);
                toast(Strings.en() ? "Extended by 15 minutes" : "已加时 15 分钟");
                handler.postDelayed(() -> { renderTable(); refreshStatus(); }, 650);
            }));
        }
        chips.addView(chip(done ? Strings.chipUndone() : Strings.chipDone(), false, v -> {
            if (done) { t.markUndone(); StartBaseline.clearCompleted(MainActivity.this, t.id); }
            else { t.markDone(); StartBaseline.markCompleted(MainActivity.this, t.id); }
            store.save();
            SbSync.pushDoneAsync(this, t.id);
            expandedTaskId = null;
            adapter.notifyDataSetChanged();
            refreshStatus();
            if (!done) fireGuardAfterManualDone();
        }));
        exp.addView(chips);

        // 子任务区（v1.11.0）：与大号悬浮窗共用同一面板组件
        View line2 = new View(this);
        line2.setBackgroundColor(Ui.line(this));
        LinearLayout.LayoutParams l2lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        l2lp.setMargins(0, dp(12), 0, dp(8));
        line2.setLayoutParams(l2lp);
        exp.addView(line2);
        SubtaskPanel panel = new SubtaskPanel(this, false, false);
        panel.bind(t, subtaskHost);
        exp.addView(panel);
        return exp;
    }

    /** 子任务面板宿主（主页）：落盘走 store，编辑走对话框。 */
    private final SubtaskPanel.Host subtaskHost = new SubtaskPanel.Host() {
        @Override public void onSubtasksEdited(TaskItem t) {
            store.save();
            adapter.notifyDataSetChanged();
            refreshStatus();
        }
        @Override public void requestEditSubtask(TaskItem t, int index) {
            dialogSubtask(t, index);
        }
        @Override public int runningIndex(TaskItem t) {
            if (TimerState.isRunning(MainActivity.this)
                    && t.id.equals(TimerState.taskId(MainActivity.this))
                    && TimerState.subActive(MainActivity.this)) {
                return TimerState.subIndex(MainActivity.this);
            }
            return -1;
        }
        @Override public String runningRemainText(TaskItem t) {
            return mmss(Math.max(0, TimerState.subEndAt(MainActivity.this) - System.currentTimeMillis()));
        }
        @Override public String headerRightText(TaskItem t) {
            int ri = runningIndex(t);
            if (ri >= 0) {
                return Strings.subProgress(ri + 1, TimerState.subCount(MainActivity.this),
                        TimerState.subTitle(MainActivity.this));
            }
            if (t.hasSubtasks()) return Strings.subTotalNote(t.subtasksTotalMinutes());
            return null;
        }
    };

    /** 子任务编辑（新增 index=-1 / 改名改分钟 / 删除带二次确认）。 */
    private void dialogSubtask(final TaskItem t, final int index) {
        final Subtask cur = index >= 0 && index < t.subtasks.size() ? t.subtasks.get(index) : null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final EditText nameIn = new EditText(this);
        nameIn.setHint(Strings.subNameHint());
        if (cur != null) nameIn.setText(cur.title);
        box.addView(nameIn);
        final EditText minIn = new EditText(this);
        minIn.setHint(Strings.subMinutesHint());
        minIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minIn.setText(cur != null ? String.valueOf(cur.minutes) : "5");
        box.addView(minIn);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(cur == null ? Strings.subEditNew() : Strings.subEditTitle())
                .setView(box)
                .setPositiveButton(Strings.subSave(), (d, w) -> {
                    TaskItem task = freshCopyOf(t);
                    String name = nameIn.getText().toString().trim();
                    if (name.isEmpty()) name = Strings.subtasksTitle() + (task.subtasks.size() + 1);
                    int mins = 5;
                    try {
                        mins = Integer.parseInt(minIn.getText().toString().trim());
                    } catch (NumberFormatException ignored) { }
                    if (mins <= 0) mins = 5;
                    if (cur == null) {
                        task.subtasks.add(Subtask.create(name, mins));
                    } else {
                        Subtask s = null;
                        for (Subtask x : task.subtasks) if (x.id.equals(cur.id)) s = x;
                        if (s == null) return;
                        s.title = name;
                        s.minutes = mins;
                    }
                    task.syncDurationFromSubtasks();
                    task.touch();
                    store.save();
                    adapter.notifyDataSetChanged();
                    refreshStatus();
                })
                .setNegativeButton(Strings.btnCancel(), null);
        if (cur != null) {
            b.setNeutralButton(Strings.subDelete(), (d, w) -> {
                new AlertDialog.Builder(this)
                        .setMessage(Strings.subDeleteConfirm())
                        .setPositiveButton(Strings.subDelete(), (d2, w2) -> {
                            TaskItem task = freshCopyOf(t);
                            for (int i = 0; i < task.subtasks.size(); i++) {
                                if (task.subtasks.get(i).id.equals(cur.id)) {
                                    task.subtasks.remove(i);
                                    break;
                                }
                            }
                            task.syncDurationFromSubtasks();
                            task.touch();
                            store.save();
                            adapter.notifyDataSetChanged();
                            refreshStatus();
                        })
                        .setNegativeButton(Strings.btnCancel(), null)
                        .show();
            });
        }
        b.show();
    }

    private TextView chip(String label, boolean filled, View.OnClickListener cl) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(13);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(14), dp(7), dp(14), dp(7));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(17));
        if (filled) {
            g.setColor(Ui.accent(this));
            c.setTextColor(Ui.onAccent(this));
        } else {
            g.setColor(Ui.surface(this));
            g.setStroke(1, Ui.line(this));
            c.setTextColor(Ui.text(this));
        }
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        c.setLayoutParams(lp);
        c.setOnClickListener(cl);
        return c;
    }

    /** 闹钟设置（v1.9.0）：闹钟时间（到点响铃、与计时无关）＋计时结束时响铃开关。 */
    private void dialogAlarm(final TaskItem t) {
        boolean en = Strings.en();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        // 闹钟时间（可选）：打开选择器时默认带入该任务的开始时间 nextStartAt
        final String[] alarmHolder = {t.alarmAt};
        final Button alarmBtn = new Button(this);
        final Runnable refreshAlarmBtn = () -> alarmBtn.setText(
                alarmHolder[0] == null
                        ? Strings.alarmTimeLabel() + "：" + Strings.alarmNotSet()
                        : Strings.alarmTimeLabel() + "：" + alarmHolder[0].replace('T', ' '));
        refreshAlarmBtn.run();
        alarmBtn.setOnClickListener(v -> {
            final String init = alarmHolder[0] != null ? alarmHolder[0] : t.nextStartAt;
            Calendar ac = Calendar.getInstance();
            if (init != null && init.length() >= 10) {
                try {
                    String[] dp2 = init.substring(0, 10).split("-");
                    ac.set(Integer.parseInt(dp2[0]), Integer.parseInt(dp2[1]) - 1,
                            Integer.parseInt(dp2[2]));
                } catch (Exception ignored) { }
            }
            new android.app.DatePickerDialog(this, (dv, y, mo, da) -> {
                int ah = 7, am2 = 0;
                if (init != null && init.length() >= 16) {
                    try {
                        String[] tp2 = init.substring(11, 16).split(":");
                        ah = Integer.parseInt(tp2[0]);
                        am2 = Integer.parseInt(tp2[1]);
                    } catch (Exception ignored) { }
                }
                android.app.TimePickerDialog atp = new android.app.TimePickerDialog(this,
                        (tv, h3, m3) -> {
                            alarmHolder[0] = String.format("%04d-%02d-%02dT%02d:%02d",
                                    y, mo + 1, da, h3, m3);
                            refreshAlarmBtn.run();
                        }, ah, am2, true);
                atp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL,
                        en ? "Clear" : "清除时间",
                        (d3, w3) -> { alarmHolder[0] = null; refreshAlarmBtn.run(); });
                atp.show();
            }, ac.get(Calendar.YEAR), ac.get(Calendar.MONTH),
                    ac.get(Calendar.DAY_OF_MONTH)).show();
        });
        box.addView(alarmBtn);
        final CheckBox endBox = new CheckBox(this);
        endBox.setText(Strings.alarmRingOnEnd());
        endBox.setChecked(t.alarmOnTimerEnd);
        box.addView(endBox);
        new AlertDialog.Builder(this)
                .setTitle(Strings.alarmTitle())
                .setView(box)
                .setPositiveButton(en ? "Save" : "保存", (d, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    cur.alarmAt = alarmHolder[0];
                    cur.alarmOnTimerEnd = endBox.isChecked();
                    cur.touch(); store.save(); adapter.notifyDataSetChanged();
                    AlarmScheduler.rescheduleAll(this, store);
                })
                .setNegativeButton(en ? "Cancel" : "取消", null)
                .show();
    }

    /** 兼容旧调用点：所有 adapter.notifyDataSetChanged() 仍走整表重画（语义不变）。 */
    private class TaskAdapter {
        void notifyDataSetChanged() { renderTable(); }
    }

    private String focusCellText(TaskItem t) {
        if ("block".equals(t.focusMode)) {
            return t.blockedApps.isEmpty() ? "禁（未选）"
                    : "禁:" + AppLister.labelOf(this, t.blockedApps.get(0)) + (t.blockedApps.size() > 1 ? "+" + (t.blockedApps.size() - 1) : "");
        }
        if ("allow".equals(t.focusMode)) {
            return t.allowedApps.isEmpty() ? "仅（未选）"
                    : "仅:" + AppLister.labelOf(this, t.allowedApps.get(0)) + (t.allowedApps.size() > 1 ? "+" + (t.allowedApps.size() - 1) : "");
        }
        return (t.focusRule == null || t.focusRule.isEmpty()) ? "＋专注" : "规则";
    }

    /** 衔接守卫（v1.10.0）：手动把任务标记完成之后，约 1.5 秒后若仍空档则全屏问「下一个任务」。 */
    private void fireGuardAfterManualDone() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> IdleGuard.fireNow(this), 1500);
    }

    private void onPlay(final TaskItem t) {
        if (TimerState.isRunning(this)) {
            String runningId = TimerState.taskId(this);
            if (t.id.equals(runningId)) {
                // 暂停必须带时长＋目的——先过暂停卡，确认才真正暂停
                PauseFlow.ask(this, t.title, ch -> {
                    TimerService.pauseWithReminder(this, ch.delayMs, ch.reason, ch.emoji, ch.silent);
                    toast("已暂停：「" + t.title + "」，到点会提醒你回来");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        adapter.notifyDataSetChanged();
                        refreshStatus();
                    }, 350);
                });
                return;
            }
            // 直接切换（v1.16.1 用户定案）：切换≠暂停。点另一个任务的开始就立即切过去——
            // 原任务在 TimerService.ACTION_START 里直接停表（SessionLog.open 先把它的分段
            // 以当前时刻关段落盘、已用不丢），不进暂停态：无 p_reason、无暂停倒计时、
            // 无叫回；之后随时点它的开始接着计。中间不许有任何对话框/暂停卡。
            // 暂停槽里若还有别的任务在暂停，也原样不动（旧实现会把它覆盖掉）。
            startTaskTimer(t);
            return;
        }
        if (t.id.equals(TimerState.pausedTaskId(this))) {
            int total = TimerState.pausedTotalSec(this);
            if (total <= 0) total = Math.max(1, t.durationMinutes) * 60;
            int remain = (int) Math.max(1, TimerState.pausedRemainMs(this) / 1000);
            TimerService.resumeTimer(this, total, remain, t.id, t.title);
            toast("继续计时：" + t.title + "（剩余约 " + (remain / 60) + " 分 " + (remain % 60) + " 秒）");
            adapter.notifyDataSetChanged();
            refreshStatus();
            return;
        }
        startTaskTimer(t);
    }

    /** 重置：把这个任务的计时清零，从完整时长重新开始（长按 ▶ 弹出）。
     *  若别的任务正在计时，这就是一次直接切换＋重置（v1.16.1 定案，同 onPlay）：
     *  原任务直接停表落盘，不经暂停卡。 */
    private void resetTask(final TaskItem t) {
        StartBaseline.markReset(this, t.id); // 重置＝抵扣基数清零（v1.21.1），必须在起表前打戳
        startTaskTimer(t);
        toast("已重置，从 0 重新开始：" + t.title);
    }

    private void startTaskTimer(TaskItem t) {
        // 屏幕内容守卫开着且还没有可用授权时，先请求一次截图授权（安卓 14+ 每个计时会话一次），
        // 拿到结果（允许或拒绝）后都照常开始计时：拒绝就本次不采样，不拦着做事。
        if (ScreenGuardPrefs.enabled(this) && !ScreenCaptureService.isAuthorized(this)) {
            android.media.projection.MediaProjectionManager mpm =
                    (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mpm != null) {
                pendingGuardTask = t;
                toast("屏幕内容守卫已开：请在系统弹窗里允许这一次截图授权（每个任务一次）");
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
                return;
            }
        }
        doStartTaskTimer(t);
    }

    private void doStartTaskTimer(TaskItem t) {
        // 今天已计过时按剩余接着倒数（v1.21.1，抵扣收口在 TimerService.startTimerSmart，
        // 它自己弹「继续计时」提示）；没抵扣才走全新开始
        if (!TimerService.startTimerSmart(this, t)) {
            TimerService.startTimer(this, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
            toast("开始计时：" + t.title + "（" + t.durationMinutes + " 分钟）");
        }
        adapter.notifyDataSetChanged();
    }

    // ---------------- 复制任务信息（v1.6.1） ----------------
    /** 把任务全字段文案复制到剪贴板（与 AI 按钮同一份 buildInfo）。noteOverride/ruleOverride
     *  非空时用编辑框里的现行文字替换对应字段拼文案，拼完即恢复，不落盘、不改任务本身。 */
    private void copyTaskInfo(final TaskItem t, String noteOverride, String ruleOverride) {
        String oldNote = t.note, oldRule = t.focusRule;
        if (noteOverride != null) t.note = noteOverride;
        if (ruleOverride != null) t.focusRule = ruleOverride;
        String info = AiLaunchActivity.buildInfo(this, t);
        t.note = oldNote;
        t.focusRule = oldRule;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("知行任务", info));
        toast("已复制任务信息");
    }

    /** 给整段内容编辑框套一层 FrameLayout：右上方常驻一个「复制」按钮（v1.6.1），
     *  叠在编辑区右上角、不随文字滚动消失；编辑框右侧留位，长文字换行不钻到按钮底下。
     *  kind="note" 时复制文案用编辑框现行文字作笔记；kind="rule" 时作专注规则。 */
    private FrameLayout wrapWithCopyButton(final TaskItem t, final EditText input, final String kind) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        input.setPadding(input.getPaddingLeft(), input.getPaddingTop(), dp(56), input.getPaddingBottom());
        TextView btn = new TextView(this);
        btn.setText("复制");
        btn.setTextSize(12);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.argb(210, 97, 97, 97));
        btn.setPadding(dp(10), dp(4), dp(10), dp(4));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        blp.setMargins(0, dp(4), dp(6), 0);
        btn.setLayoutParams(blp);
        btn.setOnClickListener(v -> {
            if ("note".equals(kind)) copyTaskInfo(t, input.getText().toString(), null);
            else if ("rule".equals(kind)) copyTaskInfo(t, null, input.getText().toString());
            else copyTaskInfo(t, null, null);
        });
        wrap.addView(btn);
        return wrap;
    }

    // ---------------- 编辑对话框 ----------------
    private void dialogTitle(final TaskItem t) {
        final EditText input = new EditText(this);
        input.setText(t.title);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("任务名称")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        TaskItem cur = freshCopyOf(t);
                        cur.title = name; cur.touch(); store.save(); adapter.notifyDataSetChanged();
                    }
                })
                .setNeutralButton("设为必要/取消必要", (d, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    cur.priority = "must".equals(cur.priority) ? null : "must";
                    cur.touch(); store.save(); adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogScenarios(final TaskItem t) {
        LinkedHashSet<String> knownSet = new LinkedHashSet<>();
        knownSet.add("有网");
        knownSet.add("无网");
        knownSet.add("跟 AI 聊天");
        knownSet.addAll(store.allScenarios());
        knownSet.addAll(t.scenarios);
        final List<String> known = new ArrayList<>(knownSet);
        final Set<String> sel = new LinkedHashSet<>(t.scenarios);
        boolean[] checked = new boolean[known.size()];
        for (int i = 0; i < known.size(); i++) checked[i] = sel.contains(known.get(i));
        new AlertDialog.Builder(this)
                .setTitle("情景（可多选）")
                .setMultiChoiceItems(known.toArray(new String[0]), checked,
                        (d, which, isChecked) -> {
                            if (isChecked) sel.add(known.get(which));
                            else sel.remove(known.get(which));
                        })
                .setPositiveButton("保存", (d, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    cur.scenarios = new ArrayList<>(sel);
                    cur.touch(); store.save(); adapter.notifyDataSetChanged();
                })
                .setNeutralButton("＋新增情景", (d, w) -> {
                    final EditText input = new EditText(this);
                    input.setHint("如：在书院、早上、跟 AI 聊天");
                    new AlertDialog.Builder(this)
                            .setTitle("新增情景")
                            .setView(input)
                            .setPositiveButton("添加", (d2, w2) -> {
                                String s = input.getText().toString().trim();
                                TaskItem cur = freshCopyOf(t);
                                if (!s.isEmpty() && !cur.scenarios.contains(s)) {
                                    cur.scenarios.add(s);
                                    cur.touch(); store.save(); adapter.notifyDataSetChanged();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 截止格文字：日期/每日 + 完成时间；设了下次开始时间时再加一行小字 ▶时间（不是今天时带 MM-DD）。 */
    private CharSequence deadlineCellText(TaskItem t) {
        String base = "daily".equals(t.repeat) ? "每日"
                : (t.deadline == null ? "＋日期"
                : t.deadline.substring(5) + (t.deadlineTime == null ? "" : "\n" + t.deadlineTime));
        long ms = t.nextStartMs();
        if (ms <= 0) return base;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        String hm = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        String day = String.format("%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        String tail = "\n▶" + (day.equals(TaskItem.today()) ? hm : day.substring(5) + " " + hm);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(base);
        int start = sb.length();
        sb.append(tail);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.78f),
                start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(46, 125, 50)),
                start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private void dialogDeadline(final TaskItem t) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final CheckBox repeatBox = new CheckBox(this);
        repeatBox.setText("每日重复任务");
        repeatBox.setChecked("daily".equals(t.repeat));
        box.addView(repeatBox);
        final DatePicker picker = new DatePicker(this);
        Calendar cal = Calendar.getInstance();
        if (t.deadline != null) {
            try {
                String[] p = t.deadline.split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) { }
        }
        picker.init(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), null);
        box.addView(picker);
        repeatBox.setOnCheckedChangeListener((b, isChecked) -> picker.setEnabled(!isChecked));
        picker.setEnabled(!repeatBox.isChecked());
        // 完成时间（可选）：当天几点前做完
        final String[] timeHolder = {t.deadlineTime};
        final Button timeBtn = new Button(this);
        final Runnable refreshTimeBtn = () -> timeBtn.setText(
                timeHolder[0] == null ? "完成时间（可选）：未设置" : "完成时间：" + timeHolder[0] + " 前");
        refreshTimeBtn.run();
        timeBtn.setOnClickListener(v -> {
            int hh = 18, mm = 0;
            if (timeHolder[0] != null) {
                try {
                    String[] p = timeHolder[0].split(":");
                    hh = Integer.parseInt(p[0]);
                    mm = Integer.parseInt(p[1]);
                } catch (Exception ignored) { }
            }
            android.app.TimePickerDialog tp = new android.app.TimePickerDialog(this,
                    (view, h2, m2) -> {
                        timeHolder[0] = String.format("%02d:%02d", h2, m2);
                        refreshTimeBtn.run();
                    }, hh, mm, true);
            tp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "清除时间",
                    (d2, w2) -> { timeHolder[0] = null; refreshTimeBtn.run(); });
            tp.show();
        });
        box.addView(timeBtn);
        // 下次开始时间（可选）：到点全屏提醒开始，与截止日期相互独立（每日重复任务也能设）
        final String[] startHolder = {t.nextStartAt};
        final Button startBtn = new Button(this);
        final Runnable refreshStartBtn = () -> startBtn.setText(
                startHolder[0] == null ? "下次开始时间（可选）：未设置"
                        : "下次开始时间：" + startHolder[0].replace('T', ' '));
        refreshStartBtn.run();
        startBtn.setOnClickListener(v -> {
            Calendar sc = Calendar.getInstance();
            if (startHolder[0] != null && startHolder[0].length() >= 10) {
                try {
                    String[] dp2 = startHolder[0].substring(0, 10).split("-");
                    sc.set(Integer.parseInt(dp2[0]), Integer.parseInt(dp2[1]) - 1, Integer.parseInt(dp2[2]));
                } catch (Exception ignored) { }
            }
            new android.app.DatePickerDialog(this, (dv, y, mo, da) -> {
                int sh = 9, sm = 0;
                if (startHolder[0] != null && startHolder[0].length() >= 16) {
                    try {
                        String[] tp2 = startHolder[0].substring(11, 16).split(":");
                        sh = Integer.parseInt(tp2[0]);
                        sm = Integer.parseInt(tp2[1]);
                    } catch (Exception ignored) { }
                }
                android.app.TimePickerDialog stp = new android.app.TimePickerDialog(this,
                        (tv, h3, m3) -> {
                            startHolder[0] = String.format("%04d-%02d-%02dT%02d:%02d", y, mo + 1, da, h3, m3);
                            refreshStartBtn.run();
                        }, sh, sm, true);
                stp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "清除时间",
                        (d3, w3) -> { startHolder[0] = null; refreshStartBtn.run(); });
                stp.show();
            }, sc.get(Calendar.YEAR), sc.get(Calendar.MONTH), sc.get(Calendar.DAY_OF_MONTH)).show();
        });
        box.addView(startBtn);
        new AlertDialog.Builder(this)
                .setTitle("截止日期")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    if (repeatBox.isChecked()) {
                        cur.repeat = "daily";
                        cur.deadline = null;
                        cur.deadlineTime = null;
                    } else {
                        cur.repeat = "none";
                        cur.deadline = String.format("%04d-%02d-%02d",
                                picker.getYear(), picker.getMonth() + 1, picker.getDayOfMonth());
                        cur.deadlineTime = timeHolder[0];
                    }
                    cur.nextStartAt = startHolder[0];
                    cur.touch(); store.save(); adapter.notifyDataSetChanged();
                    StartReminder.reschedule(this, store);
                })
                .setNeutralButton("清除日期", (d, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    cur.repeat = "none";
                    cur.deadline = null;
                    cur.deadlineTime = null;
                    cur.nextStartAt = null;
                    cur.touch(); store.save(); adapter.notifyDataSetChanged();
                    StartReminder.reschedule(this, store);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogDuration(final TaskItem t) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        TextView planLabel = new TextView(this);
        planLabel.setText("计划时长（分钟）");
        box.addView(planLabel);
        final EditText planInput = new EditText(this);
        planInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        planInput.setText(String.valueOf(t.durationMinutes));
        planInput.setSelectAllOnFocus(true);
        box.addView(planInput);
        if (t.hasSubtasks()) {
            // 子任务口径（v1.11.0）：总时长恒等于子任务之和，这里改不了，去改子任务分钟
            planInput.setEnabled(false);
            TextView lockNote = new TextView(this);
            lockNote.setText(Strings.subDurationLocked());
            lockNote.setTextSize(12);
            box.addView(lockNote);
        }
        TextView actualLabel = new TextView(this);
        actualLabel.setText("实际用时（分钟，可空）");
        box.addView(actualLabel);
        final EditText actualInput = new EditText(this);
        actualInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (t.actualMinutes > 0) actualInput.setText(String.valueOf(t.actualMinutes));
        actualInput.setHint("做完后填实际花了多少分钟");
        box.addView(actualInput);
        new AlertDialog.Builder(this)
                .setTitle("时长")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    try {
                        int m = Integer.parseInt(planInput.getText().toString().trim());
                        if (m > 0) cur.durationMinutes = m;
                    } catch (NumberFormatException ignored) { }
                    String a = actualInput.getText().toString().trim();
                    if (a.isEmpty()) {
                        cur.actualMinutes = 0;
                    } else {
                        try {
                            int am = Integer.parseInt(a);
                            if (am >= 0) cur.actualMinutes = am;
                        } catch (NumberFormatException ignored) { }
                    }
                    cur.touch(); store.save(); adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogFocus(final TaskItem t) {
        final List<AppLister.AppInfo> apps = AppLister.launchableApps(this);
        final String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
        final String[] mode = {t.focusMode == null ? "off" : t.focusMode};
        final List<String> blocked = new ArrayList<>(t.blockedApps);
        final List<String> allowed = new ArrayList<>(t.allowedApps);
        final String[] primary = {t.primaryApp};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        final EditText ruleInput = new EditText(this);
        ruleInput.setHint("描述什么情况下你可能会分神，如：读经典时想刷微信、查资料容易逛到购物网站（Muse 会据此翻译成要拦/只许用的软件名单）");
        ruleInput.setText(t.focusRule == null ? "" : t.focusRule);
        ruleInput.setMinLines(2);
        ruleInput.setTextSize(15);
        box.addView(wrapWithCopyButton(t, ruleInput, "rule"));

        final TextView summary = new TextView(this);
        summary.setTextSize(13);
        summary.setTextColor(Color.DKGRAY);
        summary.setPadding(0, dp(8), 0, dp(4));

        final Button[] modeBtns = new Button[3];
        final String[] modeVals = {"off", "block", "allow"};
        final String[] modeNames = {"不监测", "黑名单", "白名单"};
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            for (int i = 0; i < 3; i++) {
                modeBtns[i].setBackgroundColor(modeVals[i].equals(mode[0])
                        ? Color.rgb(46, 125, 50) : Color.rgb(189, 189, 189));
                modeBtns[i].setTextColor(Color.WHITE);
            }
            summary.setText("拦截 " + blocked.size() + " 个 · 允许 " + allowed.size() + " 个 · 主软件："
                    + (primary[0] == null ? "无（跳回当前任务页）" : AppLister.labelOf(this, primary[0])));
        };
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            modeBtns[i] = new Button(this);
            modeBtns[i].setText(modeNames[i]);
            modeBtns[i].setTextSize(14);
            modeBtns[i].setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            modeBtns[i].setOnClickListener(v -> { mode[0] = modeVals[idx]; refresh[0].run(); });
            modeRow.addView(modeBtns[i]);
        }
        box.addView(modeRow);
        box.addView(summary);

        Button pickBlocked = new Button(this);
        pickBlocked.setText("选择要拦截的软件（黑名单）");
        pickBlocked.setOnClickListener(v -> pickAppsMulti("拦截这些软件", apps, labels, blocked, refresh[0]));
        box.addView(pickBlocked);
        Button pickAllowed = new Button(this);
        pickAllowed.setText("选择只允许用的软件（白名单）");
        pickAllowed.setOnClickListener(v -> pickAppsMulti("只允许这些软件", apps, labels, allowed, refresh[0]));
        box.addView(pickAllowed);
        Button pickPrimary = new Button(this);
        pickPrimary.setText("选择主软件（被拦下时跳回它）");
        pickPrimary.setOnClickListener(v -> {
            String[] items = new String[labels.length + 1];
            items[0] = "无（跳回知行的当前任务页）";
            System.arraycopy(labels, 0, items, 1, labels.length);
            new AlertDialog.Builder(this)
                    .setTitle("主软件")
                    .setItems(items, (d, which) -> {
                        primary[0] = which == 0 ? null : apps.get(which - 1).pkg;
                        refresh[0].run();
                    })
                    .show();
        });
        box.addView(pickPrimary);
        refresh[0].run();

        AlertDialog focusDialog = new AlertDialog.Builder(this)
                .setTitle("专注规则")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    cur.focusRule = ruleInput.getText().toString().trim();
                    cur.focusMode = mode[0];
                    cur.blockedApps = new ArrayList<>(blocked);
                    cur.allowedApps = new ArrayList<>(allowed);
                    cur.primaryApp = primary[0];
                    cur.touch();
                    store.save();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
        trackOpenDialog(focusDialog, ruleInput, t, "rule");
    }

    private void pickAppsMulti(String title, List<AppLister.AppInfo> apps, String[] labels,
                               List<String> target, Runnable onDone) {
        final Set<String> sel = new LinkedHashSet<>(target);
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) checked[i] = sel.contains(apps.get(i).pkg);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    if (isChecked) sel.add(apps.get(which).pkg);
                    else sel.remove(apps.get(which).pkg);
                })
                .setPositiveButton("确定", (d, w) -> {
                    target.clear();
                    target.addAll(sel);
                    onDone.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dialogNote(final TaskItem t) {
        EditText input = new EditText(this);
        input.setText(t.note == null ? "" : t.note);
        input.setSelection(input.getText().length());
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("笔记 · " + t.title)
                .setView(wrapWithCopyButton(t, input, "note"))
                .setPositiveButton("保存", (dg, w) -> {
                    TaskItem cur = freshCopyOf(t);
                    cur.note = input.getText().toString().trim();
                    cur.touch();
                    store.save();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
        trackOpenDialog(d, input, t, "note");
    }

    private void dialogRowMenu(final TaskItem t) {
        String[] items = {t.isDoneToday() ? "取消完成标记" : "标记完成", "删除任务"};
        new AlertDialog.Builder(this)
                .setTitle(t.title)
                .setItems(items, (d, which) -> {
                    TaskItem cur = freshCopyOf(t);
                    if (which == 0) {
                        boolean toDone = !cur.isDoneToday();
                        if (toDone) { cur.markDone(); StartBaseline.markCompleted(this, cur.id); }
                        else { cur.markUndone(); StartBaseline.clearCompleted(this, cur.id); }
                        store.save();
                        SbSync.pushDoneAsync(this, cur.id);
                        adapter.notifyDataSetChanged();
                        if (toDone) fireGuardAfterManualDone();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("删除任务")
                                .setMessage("确定删除「" + cur.title + "」吗？")
                                .setPositiveButton("删除", (d2, w2) -> {
                                    store.remove(freshCopyOf(t));
                                    adapter.notifyDataSetChanged();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    // ---------------- 账号与同步（Supabase；v1.8.0 弹窗逻辑抽到 AccountUi，抽屉与设置页共用） ----------------
    private void dialogAccount() {
        AccountUi.show(this, store, () -> {
            store.load();
            if (adapter != null) adapter.notifyDataSetChanged();
            refreshStatus();
        });
    }

    // ---------------- Drive 同步 ----------------
    private void launchBridgePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK_BRIDGE);
        toast("请在 Google Drive 里打开 _电子大脑/task list/ 并选中 tasks.json");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAL_CAPTURE) {
            if (calendarPanel != null) calendarPanel.onCaptureResult(resultCode, data);
            return;
        }
        if (requestCode == REQ_CAPTURE) {
            TaskItem t = pendingGuardTask;
            pendingGuardTask = null;
            if (resultCode == RESULT_OK && data != null) {
                ProjectionAuth.store(resultCode, data);
            } else {
                toast("未获得截图授权：本次计时不做屏幕内容采样，其余功能照常");
            }
            if (t != null) doStartTaskTimer(t);
            return;
        }
        if (requestCode == REQ_PICK_BRIDGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) { }
            SyncManager.setUri(this, uri);
            runSync(true);
            if (SyncManager.realtimeEnabled(this)) SyncService.start(this);
        }
    }

    private void runSync(final boolean verbose) {
        new Thread(() -> {
            String result = SyncManager.syncNowSmart(this, store);
            SyncManager.setLastResult(this, result);
            handler.post(() -> {
                store.load();
                adapter.notifyDataSetChanged();
                refreshStatus();
                if (verbose) toast(result);
            });
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
