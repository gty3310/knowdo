package com.ti.tasktimer.dynui;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * v2.3.0 活卡片地基逻辑测试（纯 JVM，不依赖 Android）：
 * 覆盖用户口径的四件事在协议/数据层成立——
 * 两卡成组、updateDataModel 原地刷新且只通知绑定子树、组件同 id 替换不新增、
 * surface 状态独立于 View（销毁重绑拿回同一组与最新值）、坏消息不拖垮整串。
 * 注：本沙箱里 Gradle 自带 testReleaseUnitTest 死于测试执行器内部 NPE
 * （resultProcessor is null，与被测代码无关），故用 javac + JUnitCore 直跑取证。
 */
public class DynUiLogicTest {

    private static final String TWO_CARDS = "["
            + "{\"createSurface\":{\"surfaceId\":\"chat-1\"}},"
            + "{\"updateDataModel\":{\"surfaceId\":\"chat-1\",\"path\":\"/\",\"value\":{"
            + "  \"tasks\":["
            + "    {\"title\":\"回复 LinkedIn 消息\",\"durationMinutes\":25,\"priority\":\"important\"},"
            + "    {\"title\":\"站桩\",\"durationMinutes\":60,\"priority\":\"secondary\"}"
            + "  ]}}},"
            + "{\"updateComponents\":{\"surfaceId\":\"chat-1\",\"components\":["
            + "  {\"id\":\"card-a\",\"component\":\"TaskCard\",\"props\":{\"title\":{\"path\":\"/tasks/0/title\"},\"durationMinutes\":{\"path\":\"/tasks/0/durationMinutes\"}}},"
            + "  {\"id\":\"card-b\",\"component\":\"TaskCard\",\"props\":{\"title\":{\"path\":\"/tasks/1/title\"},\"durationMinutes\":{\"path\":\"/tasks/1/durationMinutes\"}}}"
            + "]}}"
            + "]";

    @Test
    public void twoCardsFormOneSurfaceGroup() {
        DynUiProcessor p = new DynUiProcessor();
        p.process(TWO_CARDS);
        DynUiProcessor.Surface s = p.surface("chat-1");
        assertNotNull(s);
        assertEquals(2, s.components.size());
        assertEquals("TaskCard", s.component("card-a").type);
        assertEquals("回复 LinkedIn 消息",
                s.model.getString(DynUiProcessor.bindingPath(s.component("card-a"), "title"), ""));
        assertEquals("60",
                s.model.getString(DynUiProcessor.bindingPath(s.component("card-b"), "durationMinutes"), ""));
    }

    @Test
    public void updateDataModelRefreshesInPlaceAndNotifiesOnlyBoundSubtree() {
        DynUiProcessor p = new DynUiProcessor();
        p.process(TWO_CARDS);
        DynUiProcessor.Surface s = p.surface("chat-1");
        DynUiProcessor.ComponentSpec cardA = s.component("card-a");

        AtomicInteger aDurationHits = new AtomicInteger();
        AtomicInteger bDurationHits = new AtomicInteger();
        AtomicInteger aTitleHits = new AtomicInteger();
        s.model.addListener("/tasks/0/durationMinutes", c -> aDurationHits.incrementAndGet());
        s.model.addListener("/tasks/1/durationMinutes", c -> bDurationHits.incrementAndGet());
        s.model.addListener("/tasks/0/title", c -> aTitleHits.incrementAndGet());

        p.process("[{\"updateDataModel\":{\"surfaceId\":\"chat-1\",\"path\":\"/tasks/0/durationMinutes\",\"value\":45}}]");

        assertEquals("45", s.model.getString("/tasks/0/durationMinutes", ""));
        assertEquals(1, aDurationHits.get());
        assertEquals(0, bDurationHits.get());
        assertEquals(0, aTitleHits.get());
        // 组件对象没换：刷新走数据层，组件不重建
        assertSame(cardA, s.component("card-a"));
    }

    @Test
    public void sameIdComponentIsReplacedNotDuplicated() {
        DynUiProcessor p = new DynUiProcessor();
        p.process(TWO_CARDS);
        List<String> changed = new ArrayList<>();
        p.addSurfaceListener(new DynUiProcessor.SurfaceListener() {
            @Override public void onComponentsChanged(String sid, List<String> ids) { changed.addAll(ids); }
            @Override public void onSurfaceDeleted(String sid) { }
        });
        // 「改」展开表单就是同 id 换类型：TaskCard → TaskEditForm
        p.process("[{\"updateComponents\":{\"surfaceId\":\"chat-1\",\"components\":["
                + "{\"id\":\"card-a\",\"component\":\"TaskEditForm\",\"props\":{}}]}}]");
        DynUiProcessor.Surface s = p.surface("chat-1");
        assertEquals(2, s.components.size());
        assertEquals("TaskEditForm", s.component("card-a").type);
        assertEquals(java.util.Collections.singletonList("card-a"), changed);
    }

    @Test
    public void surfaceStateSurvivesViewRebindWithLatestValues() {
        DynUiProcessor p = new DynUiProcessor();
        p.process(TWO_CARDS);
        // 外部（任务列表）改了值 → updateDataModel
        p.process("[{\"updateDataModel\":{\"surfaceId\":\"chat-1\",\"path\":\"/tasks/1/durationMinutes\",\"value\":30}}]");
        // 「View 销毁」在协议层无对应物：处理器里的 surface 就是唯一状态；
        // 新 View 按 surfaceId 重新绑定，读到的必须是同一组组件＋最新值
        DynUiProcessor.Surface rebound = p.surface("chat-1");
        assertNotNull(rebound);
        assertEquals(2, rebound.components.size());
        assertEquals("30", rebound.model.getString("/tasks/1/durationMinutes", ""));
        assertEquals("回复 LinkedIn 消息", rebound.model.getString("/tasks/0/title", ""));
    }

    @Test
    public void badMessageDoesNotBreakTheBatch() throws Exception {
        DynUiProcessor p = new DynUiProcessor();
        p.process("[{\"nonsense\":{}},"
                + "{\"createSurface\":{\"surfaceId\":\"chat-2\"}},"
                + "{\"updateComponents\":{\"surfaceId\":\"missing\",\"components\":[]}}]");
        assertNotNull(p.surface("chat-2"));
        assertNull(p.surface("missing"));
        assertEquals(1, p.surfaceCount());
        // 整串非法 JSON：静默无操作
        p.process("not json at all");
        assertEquals(1, p.surfaceCount());
    }

    @Test
    public void deleteSurfaceRemovesState() {
        DynUiProcessor p = new DynUiProcessor();
        p.process(TWO_CARDS);
        AtomicInteger deleted = new AtomicInteger();
        p.addSurfaceListener(new DynUiProcessor.SurfaceListener() {
            @Override public void onComponentsChanged(String sid, List<String> ids) { }
            @Override public void onSurfaceDeleted(String sid) { deleted.incrementAndGet(); }
        });
        p.process("[{\"deleteSurface\":{\"surfaceId\":\"chat-1\"}}]");
        assertNull(p.surface("chat-1"));
        assertEquals(1, deleted.get());
        assertTrue(p.surfaceCount() == 0);
    }
}
