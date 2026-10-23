package com.ti.tasktimer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 前台应用包名的粗分类（屏幕内容守卫用）：
 * 购物/短视频类应用本身就是强信号；浏览器/社交/聊天类是“无关内容”判定的风险场景。
 * 这只是启发式名单，判错靠两次确认与“误判放行”兜底。
 */
public final class PkgCategories {
    private PkgCategories() {}

    private static final Set<String> SHOPPING = new HashSet<>(Arrays.asList(
            "com.taobao.taobao",        // 淘宝
            "com.tmall.wireless",       // 天猫
            "com.jingdong.app.mall",    // 京东
            "com.xunmeng.pinduoduo",    // 拼多多
            "com.taobao.idlefish",      // 闲鱼
            "com.shizhuang.duapp",      // 得物
            "com.achievo.vipshop",      // 唯品会
            "com.amazon.mShop.android.shopping",
            "com.ebay.mobile"
    ));

    private static final Set<String> VIDEO = new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.aweme",       // 抖音
            "com.ss.android.ugc.aweme.lite",  // 抖音极速版
            "com.smile.gifmaker",             // 快手
            "com.kuaishou.nebula",            // 快手极速版
            "tv.danmaku.bili",                // B站
            "com.tencent.qqlive",             // 腾讯视频
            "com.qiyi.video",                 // 爱奇艺
            "com.youku.phone",                // 优酷
            "com.google.android.youtube",
            "com.netflix.mediaclient"
    ));

    /** 文字很多却与任务零重合时，才在这类应用里判“无关”（阅读/社交/浏览器场景）。 */
    private static final Set<String> READING_RISK = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.tencent.mtt",          // QQ浏览器
            "com.tencent.mm",           // 微信
            "com.tencent.mobileqq",
            "com.xingin.xhs",           // 小红书
            "com.sina.weibo",
            "com.zhihu.android"
    ));

    public static boolean isShopping(String pkg) { return pkg != null && SHOPPING.contains(pkg); }
    public static boolean isVideo(String pkg) { return pkg != null && VIDEO.contains(pkg); }
    public static boolean isReadingRisk(String pkg) {
        return pkg != null && (READING_RISK.contains(pkg) || VIDEO.contains(pkg) || SHOPPING.contains(pkg));
    }
}
