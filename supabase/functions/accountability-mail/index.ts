// 知行 KnowDo · 黄图问责邮件（v1.28.0 feature 7，2026-10-28）
// 这是屏幕内容守卫「截图从不保存、从不上传」红线的唯一例外，且只限打码图：
// App 在守卫终判为黄色内容、且用户本人在设置里打开了问责开关后，先把截图在
// 手机内存里重度模糊＋降分辨率打码（原图从不落盘、从不上传），把打码图传到
// 私有桶 guard-reports 的自己名下文件夹，再调用本函数。本函数只见得到打码图。
//
// 契约：
// - 入参 JSON: { path: "<uid>/<ts>.jpg", task_title: string, occurred_at: epoch_ms }
//   path 必须以调用者自己的 uid 开头，否则 400（防借道读别人的图）。
// - 配置以服务端为准：开关、收件人、标题/正文模板都读 user_state.accountability
//   （App 每次改设置同步上来的那一份），请求里传的收件人不算数。
// - 防滥发在服务端执行（accountability_log 表，只有 service_role 能读写）：
//   同类事件距上一封已发送不足 10 分钟 → 合并（记 merged、不发）；
//   当天（Asia/Shanghai）已发送满 5 封 → 记 capped、不发。超限静默但留痕。
// - 发送：Resend API，发件 知行 KnowDo <noreply@tiguo.net>，打码图作附件；
//   RESEND_API_KEY 只存在 Supabase secrets 里，永不进仓库、永不进 APK。
//   未配置 Key 时返回 500 { sent:false, reason:"proxy_not_configured" }。
// - 桶里的图只为发这一封存在：任何分支处理完都删掉，事件全留在 log 表里。
// - 成功: 200 { sent: true, id: <resend message id> }

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY") ?? "";
const FROM = "知行 KnowDo <noreply@tiguo.net>";
const BUCKET = "guard-reports";
const DAILY_CAP = 5;
const MERGE_WINDOW_MS = 10 * 60 * 1000;

const DEFAULT_SUBJECT = "知行守卫提醒：检测到黄色内容";
const DEFAULT_BODY =
  "知行屏幕内容守卫在 {时间} 检测到黄色/暴露内容（当时任务：{任务}）。\n" +
  "打码后的截图见附件——原图从未保存或上传，附件是当场在手机上模糊处理的版本。\n" +
  "这封邮件由知行 KnowDo 的黄图问责功能自动发送。";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** 调用者 uid：网关已开 verify_jwt，令牌真伪由平台把关，这里只解 payload 取 sub。 */
function jwtSub(req: Request): string | null {
  const auth = req.headers.get("Authorization") ?? "";
  const m = auth.match(/^Bearer\s+(.+)$/i);
  if (!m) return null;
  const parts = m[1].split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const obj = JSON.parse(atob(payload));
    return typeof obj.sub === "string" && obj.sub ? obj.sub : null;
  } catch {
    return null;
  }
}

function svcHeaders(extra: Record<string, string> = {}): Record<string, string> {
  return {
    "apikey": SERVICE_KEY,
    "Authorization": `Bearer ${SERVICE_KEY}`,
    ...extra,
  };
}

async function restGet(path: string): Promise<unknown> {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { headers: svcHeaders() });
  if (!r.ok) throw new Error(`rest_get_${r.status}`);
  return await r.json();
}

async function logEvent(userId: string, day: string, status: string, detail: string): Promise<void> {
  await fetch(`${SUPABASE_URL}/rest/v1/accountability_log`, {
    method: "POST",
    headers: svcHeaders({ "Content-Type": "application/json", "Prefer": "return=minimal" }),
    body: JSON.stringify({ user_id: userId, day, status, detail }),
  }).catch(() => {});
}

async function storageDelete(path: string): Promise<void> {
  await fetch(`${SUPABASE_URL}/storage/v1/object/${BUCKET}/${path}`, {
    method: "DELETE",
    headers: svcHeaders(),
  }).catch(() => {});
}

async function storageDownload(path: string): Promise<ArrayBuffer | null> {
  const r = await fetch(`${SUPABASE_URL}/storage/v1/object/${BUCKET}/${path}`, {
    headers: svcHeaders(),
  });
  if (!r.ok) return null;
  return await r.arrayBuffer();
}

function toBase64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  let bin = "";
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    bin += String.fromCharCode(...bytes.subarray(i, i + CHUNK));
  }
  return btoa(bin);
}

function dayInShanghai(d = new Date()): string {
  // en-CA 给出 YYYY-MM-DD 形状
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
  }).format(d);
}

function timeInShanghai(ts: number): string {
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false,
  }).formatToParts(new Date(ts));
  const p: Record<string, string> = {};
  for (const it of parts) p[it.type] = it.value;
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`;
}

function render(tpl: string, whenText: string, taskTitle: string): string {
  // 中英模板占位符都认：{时间}/{time}、{任务}/{task}
  return tpl
    .split("{时间}").join(whenText).split("{time}").join(whenText)
    .split("{任务}").join(taskTitle || "（未命名任务）")
    .split("{task}").join(taskTitle || "(untitled task)");
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ sent: false, error: "method_not_allowed" }, 405);
  const uid = jwtSub(req);
  if (!uid) return json({ sent: false, error: "unauthorized" }, 401);

  let body: Record<string, unknown> = {};
  try { body = await req.json(); } catch { /* 空体走下面的参数校验 */ }
  const path = typeof body.path === "string" ? body.path : "";
  const taskTitle = typeof body.task_title === "string" ? body.task_title : "";
  const occurredAt = typeof body.occurred_at === "number" ? body.occurred_at : Date.now();
  if (!path || !path.startsWith(uid + "/")) {
    return json({ sent: false, error: "bad_path" }, 400);
  }

  // 配置以服务端为准：读用户自己的 user_state.accountability
  let cfg: Record<string, unknown> | null = null;
  try {
    const rows = await restGet(
      `user_state?select=accountability&user_id=eq.${encodeURIComponent(uid)}`) as Array<Record<string, unknown>>;
    cfg = (rows[0]?.accountability as Record<string, unknown>) ?? null;
  } catch {
    return json({ sent: false, error: "config_unreadable" }, 502);
  }
  const recipients = Array.isArray(cfg?.recipients)
    ? (cfg!.recipients as unknown[]).map((x) => String(x).trim()).filter((x) => x.includes("@"))
    : [];
  if (!cfg || cfg.enabled !== true || recipients.length === 0) {
    await storageDelete(path);
    return json({ sent: false, reason: "disabled" });
  }

  const day = dayInShanghai();
  // 防滥发：先看当天已发送数与上一封时间（只数 status=sent 的行）
  let sentToday: Array<{ created_at: string }> = [];
  try {
    sentToday = await restGet(
      `accountability_log?select=created_at&user_id=eq.${encodeURIComponent(uid)}` +
      `&day=eq.${day}&status=eq.sent&order=created_at.desc`) as Array<{ created_at: string }>;
  } catch {
    return json({ sent: false, error: "log_unreadable" }, 502);
  }
  if (sentToday.length >= DAILY_CAP) {
    await logEvent(uid, day, "capped", `daily cap ${DAILY_CAP} reached`);
    await storageDelete(path);
    return json({ sent: false, reason: "daily_cap" });
  }
  const lastSentAt = sentToday.length ? Date.parse(sentToday[0].created_at) : 0;
  if (lastSentAt && Date.now() - lastSentAt < MERGE_WINDOW_MS) {
    await logEvent(uid, day, "merged", "within 10 minutes of previous sent mail");
    await storageDelete(path);
    return json({ sent: false, reason: "merged_window" });
  }

  if (!RESEND_API_KEY) {
    await logEvent(uid, day, "error", "proxy_not_configured");
    await storageDelete(path);
    return json({ sent: false, reason: "proxy_not_configured" }, 500);
  }

  const img = await storageDownload(path);
  if (!img) {
    await logEvent(uid, day, "error", "image_missing");
    return json({ sent: false, reason: "image_missing" }, 502);
  }

  const whenText = timeInShanghai(occurredAt);
  const subject = render(
    typeof cfg.subject === "string" && cfg.subject ? cfg.subject : DEFAULT_SUBJECT,
    whenText, taskTitle);
  const text = render(
    typeof cfg.body === "string" && cfg.body ? cfg.body : DEFAULT_BODY,
    whenText, taskTitle);

  let resendStatus = 0;
  let resendId = "";
  try {
    const r = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${RESEND_API_KEY}`,
      },
      body: JSON.stringify({
        from: FROM,
        to: recipients,
        subject,
        text,
        attachments: [{ filename: "guard-report.jpg", content: toBase64(img) }],
      }),
    });
    resendStatus = r.status;
    if (r.ok) {
      const data = await r.json().catch(() => ({}));
      resendId = typeof data?.id === "string" ? data.id : "";
    }
  } catch {
    resendStatus = -1;
  }
  await storageDelete(path);
  if (resendStatus < 200 || resendStatus >= 300) {
    await logEvent(uid, day, "error", `resend_http_${resendStatus}`);
    return json({ sent: false, reason: "send_failed", status: resendStatus }, 502);
  }
  await logEvent(uid, day, "sent", `resend_id=${resendId}`);
  return json({ sent: true, id: resendId });
});
