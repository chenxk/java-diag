(function () {
  const REFRESH_MS = 8000;
  const TAB_MCP = "mcp";
  const TAB_INSTANCES = "instances";
  const LANG_KEY = "java-diag-admin-lang";
  const api = (path, options) =>
    fetch("/admin/api" + path, { credentials: "same-origin", ...options });

  const I18N = {
    zh: {
      "doc.title": "Jave Diag · Registry Console",
      "a11y.skip": "跳到主内容",
      "lang.group": "语言",
      "loading.label": "校验会话…",
      "loading.hint": "正在连接 Registry Console…",
      "login.title": "控制台登录",
      "login.hint": "与 Agent Bearer / Registry token 独立；默认账号见文档。",
      "login.username": "用户名",
      "login.password": "密码",
      "login.submit": "进入控制台",
      "login.err.credentials": "用户名或密码不正确。请核对后重试，或查看文档中的默认账号。",
      "login.err.credentials.raw": "{raw}。请核对后重试，或查看文档中的默认账号。",
      "login.err.server": "服务暂时不可用。请稍后重试。",
      "login.err.server.raw": "{raw}。请稍后重试。",
      "login.err.generic": "登录失败。请检查网络后重试。",
      "login.err.generic.raw": "{raw}。请检查网络后重试。",
      "login.err.network": "无法连接网关。请确认服务已启动且地址正确后重试。",
      "nav.sidebar": "主导航",
      "nav.sections": "控制台分区",
      "nav.mcp": "MCP 配置",
      "nav.mcp.hint": "Agent 接入",
      "nav.instances": "探针实例",
      "nav.logout": "退出登录",
      "nav.menuOpen": "打开菜单",
      "nav.menuClose": "关闭菜单",
      "tab.mcp.title": "MCP 配置",
      "tab.mcp.sub": "Agent MCP 接入片段",
      "tab.instances.title": "探针实例",
      "tab.instances.sub": "Registry 注册列表",
      "mcp.lead.before": "下方为可粘贴的",
      "mcp.lead.mid": "片段（含",
      "mcp.lead.after": "；登录后可见 Agent token）。",
      "mcp.copyUrl": "复制 URL",
      "mcp.copyJson": "复制完整 JSON",
      "mcp.tokenHint": "网关未配置 Agent token（启动时 --auth-token 或 JAVA_DIAG_AUTH_TOKEN）；上方 headers 为占位，请自行替换。",
      "mcp.probeTokenHint": "该探针未上报 probe token（启动时 --auth-token / JAVA_DIAG_AUTH_TOKEN）；上方 headers 为占位。",
      "copy.ok": "已复制到剪贴板",
      "copy.fail": "复制失败，请手动选择上方 JSON",
      "filter.service": "Service 过滤",
      "filter.service.ph": "probe serviceName…",
      "filter.app": "App / JVM 过滤",
      "filter.app.ph": "业务名或 mainClass…",
      "stats.label": "实例统计",
      "stats.total": "注册总数",
      "stats.healthy": "健康",
      "stats.unhealthy": "过期 / 不健康",
      "th.status": "状态",
      "th.heartbeat": "最后心跳",
      "th.actions": "操作",
      "instances.empty": "暂无注册探针",
      "instances.empty.hint.before": "探针配置",
      "instances.empty.hint.after": "后会出现在此列表（约 5–10s 刷新）。",
      "instances.viewMcp": "查看 MCP",
      "instances.refreshAt": "刷新于 {time}",
      "foot.note": "只读视图 · 数据来自 InMemoryRegistry",
      "modal.title": "探针详情 · MCP",
      "modal.close": "关闭",
      "modal.apps.title": "本机 JVM 目录",
      "modal.apps.hint.before": "来自心跳",
      "modal.apps.hint.mid": "；网关侧可用",
      "modal.apps.hint.after": "按 name 解析 pid。",
      "modal.apps.empty": "暂无上报 JVM（等待下一次心跳，或探针未启用进程扫描）",
      "modal.apps.count": "{n} 个进程",
      "modal.probeFallback": "探针",
    },
    en: {
      "doc.title": "Jave Diag · Registry Console",
      "a11y.skip": "Skip to main content",
      "lang.group": "Language",
      "loading.label": "Checking session…",
      "loading.hint": "Connecting to Registry Console…",
      "login.title": "Console sign-in",
      "login.hint": "Separate from Agent Bearer / Registry token; see docs for the default account.",
      "login.username": "Username",
      "login.password": "Password",
      "login.submit": "Enter console",
      "login.err.credentials": "Incorrect username or password. Check and retry, or see the default account in the docs.",
      "login.err.credentials.raw": "{raw}. Check and retry, or see the default account in the docs.",
      "login.err.server": "Service temporarily unavailable. Please try again later.",
      "login.err.server.raw": "{raw}. Please try again later.",
      "login.err.generic": "Sign-in failed. Check your network and try again.",
      "login.err.generic.raw": "{raw}. Check your network and try again.",
      "login.err.network": "Cannot reach the gateway. Confirm the service is running and the URL is correct.",
      "nav.sidebar": "Main navigation",
      "nav.sections": "Console sections",
      "nav.mcp": "MCP config",
      "nav.mcp.hint": "Agent access",
      "nav.instances": "Probe instances",
      "nav.logout": "Sign out",
      "nav.menuOpen": "Open menu",
      "nav.menuClose": "Close menu",
      "tab.mcp.title": "MCP config",
      "tab.mcp.sub": "Agent MCP connection snippet",
      "tab.instances.title": "Probe instances",
      "tab.instances.sub": "Registry instance list",
      "mcp.lead.before": "Pasteable",
      "mcp.lead.mid": "snippet (with",
      "mcp.lead.after": "; Agent token visible after sign-in).",
      "mcp.copyUrl": "Copy URL",
      "mcp.copyJson": "Copy full JSON",
      "mcp.tokenHint": "Gateway Agent token is not configured (--auth-token or JAVA_DIAG_AUTH_TOKEN at startup); headers above are placeholders—replace them yourself.",
      "mcp.probeTokenHint": "This probe did not report a probe token (--auth-token / JAVA_DIAG_AUTH_TOKEN at startup); headers above are placeholders.",
      "copy.ok": "Copied to clipboard",
      "copy.fail": "Copy failed; select the JSON above manually",
      "filter.service": "Service filter",
      "filter.service.ph": "probe serviceName…",
      "filter.app": "App / JVM filter",
      "filter.app.ph": "app name or mainClass…",
      "stats.label": "Instance stats",
      "stats.total": "Registered",
      "stats.healthy": "Healthy",
      "stats.unhealthy": "Stale / unhealthy",
      "th.status": "Status",
      "th.heartbeat": "Last heartbeat",
      "th.actions": "Actions",
      "instances.empty": "No registered probes",
      "instances.empty.hint.before": "Configure the probe with",
      "instances.empty.hint.after": "and it will appear here (refresh ~5–10s).",
      "instances.viewMcp": "View MCP",
      "instances.refreshAt": "Refreshed {time}",
      "foot.note": "Read-only · data from InMemoryRegistry",
      "modal.title": "Probe detail · MCP",
      "modal.close": "Close",
      "modal.apps.title": "Local JVM catalog",
      "modal.apps.hint.before": "From heartbeat",
      "modal.apps.hint.mid": "; on the gateway use",
      "modal.apps.hint.after": "to resolve pid by name.",
      "modal.apps.empty": "No JVM reported yet (wait for the next heartbeat, or process scan is disabled on the probe)",
      "modal.apps.count": "{n} process(es)",
      "modal.probeFallback": "probe",
    },
  };

  let currentLang = "zh";

  function detectDefaultLang() {
    try {
      const stored = localStorage.getItem(LANG_KEY);
      if (stored === "zh" || stored === "en") return stored;
    } catch {
      /* ignore */
    }
    const nav = String(
      (typeof navigator !== "undefined" && (navigator.language || navigator.userLanguage)) || "en"
    ).toLowerCase();
    return nav.startsWith("zh") ? "zh" : "en";
  }

  function t(key, vars) {
    const dict = I18N[currentLang] || I18N.en;
    let s = dict[key];
    if (s == null) s = I18N.en[key];
    if (s == null) s = key;
    if (vars) {
      Object.keys(vars).forEach((k) => {
        s = s.split("{" + k + "}").join(String(vars[k]));
      });
    }
    return s;
  }

  function localeTag() {
    return currentLang === "zh" ? "zh-CN" : "en";
  }

  const authShell = document.getElementById("auth-shell");
  const appShell = document.getElementById("app-shell");
  const loadingPanel = document.getElementById("panel-loading");
  const loginPanel = document.getElementById("panel-login");
  const consolePanel = document.getElementById("panel-console");
  const formLogin = document.getElementById("form-login");
  const loginError = document.getElementById("login-error");
  const btnLogin = document.getElementById("btn-login");
  const btnLogout = document.getElementById("btn-logout");
  const rows = document.getElementById("instance-rows");
  const emptyState = document.getElementById("empty-state");
  const refreshMeta = document.getElementById("refresh-meta");
  const footClock = document.getElementById("foot-clock");
  const mcpSnippet = document.getElementById("mcp-snippet");
  const mcpTokenHint = document.getElementById("mcp-token-hint");
  const btnCopyUrl = document.getElementById("btn-copy-url");
  const btnCopyJson = document.getElementById("btn-copy-json");
  const copyToast = document.getElementById("copy-toast");
  const tabMcp = document.getElementById("tab-mcp");
  const tabInstances = document.getElementById("tab-instances");
  const panelMcp = document.getElementById("tab-panel-mcp");
  const panelInstances = document.getElementById("tab-panel-instances");
  const pageTitle = document.getElementById("page-title");
  const pageSub = document.getElementById("page-sub");
  const btnSidebarToggle = document.getElementById("btn-sidebar-toggle");
  const sidebarBackdrop = document.getElementById("sidebar-backdrop");
  const tabButtons = [tabMcp, tabInstances];

  const probeModal = document.getElementById("probe-mcp-modal");
  const probeModalSub = document.getElementById("probe-mcp-modal-sub");
  const probeMcpSnippet = document.getElementById("probe-mcp-snippet");
  const probeMcpTokenHint = document.getElementById("probe-mcp-token-hint");
  const probeCopyUrl = document.getElementById("probe-mcp-copy-url");
  const probeCopyJson = document.getElementById("probe-mcp-copy-json");
  const probeCopyToast = document.getElementById("probe-mcp-copy-toast");

  let timer = null;
  let sessionMcp = null;
  let copyToastTimer = null;
  let probeCopyToastTimer = null;
  let activeProbeInst = null;
  let lastInstanceList = [];
  const filterService = document.getElementById("filter-service");
  const filterApp = document.getElementById("filter-app");
  const probeAppsTbody = document.getElementById("probe-apps-tbody");
  const probeAppsCount = document.getElementById("probe-apps-count");
  const probeAppsTableWrap = document.getElementById("probe-apps-table-wrap");
  const probeAppsEmpty = document.getElementById("probe-apps-empty");
  let filterDebounce = null;
  let syncingUrl = false;
  let relativeTimeFmt = null;
  let dateTimeFmt = null;
  let timeFmt = null;
  let activeTab = TAB_MCP;
  let lastRefreshAt = null;
  let lastCopyToastOk = null;
  let lastProbeCopyToastOk = null;

  function rebuildFormatters() {
    try {
      const loc = localeTag();
      relativeTimeFmt = new Intl.RelativeTimeFormat(loc, { numeric: "auto" });
      dateTimeFmt = new Intl.DateTimeFormat(loc, {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      });
      timeFmt = new Intl.DateTimeFormat(loc, {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      });
    } catch {
      relativeTimeFmt = null;
      dateTimeFmt = null;
      timeFmt = null;
    }
  }

  function tabMeta(tabId) {
    if (tabId === TAB_INSTANCES) {
      return { title: t("tab.instances.title"), sub: t("tab.instances.sub") };
    }
    return { title: t("tab.mcp.title"), sub: t("tab.mcp.sub") };
  }

  function applyStaticI18n() {
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.getAttribute("data-i18n");
      if (key) el.textContent = t(key);
    });
    document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
      const key = el.getAttribute("data-i18n-placeholder");
      if (key) el.setAttribute("placeholder", t(key));
    });
    document.querySelectorAll("[data-i18n-aria]").forEach((el) => {
      const key = el.getAttribute("data-i18n-aria");
      if (key) el.setAttribute("aria-label", t(key));
    });
    document.title = t("doc.title");
  }

  function updateLangSwitchUI() {
    document.querySelectorAll(".lang-btn").forEach((btn) => {
      const lang = btn.getAttribute("data-lang");
      const on = lang === currentLang;
      btn.setAttribute("aria-pressed", on ? "true" : "false");
      btn.classList.toggle("is-active", on);
    });
  }

  function applyDynamicI18n() {
    const meta = tabMeta(activeTab);
    if (pageTitle) pageTitle.textContent = meta.title;
    if (pageSub) pageSub.textContent = meta.sub;

    if (btnSidebarToggle && appShell) {
      const open = appShell.classList.contains("sidebar-open");
      btnSidebarToggle.setAttribute("aria-label", open ? t("nav.menuClose") : t("nav.menuOpen"));
    }

    if (sessionMcp) {
      renderMcpPanel(sessionMcp);
    }

    if (lastRefreshAt) {
      refreshMeta.textContent = t("instances.refreshAt", {
        time: timeFmt ? timeFmt.format(lastRefreshAt) : lastRefreshAt.toLocaleTimeString(),
      });
      if (footClock) {
        footClock.textContent = dateTimeFmt
          ? dateTimeFmt.format(lastRefreshAt)
          : lastRefreshAt.toISOString().replace("T", " ").slice(0, 19) + "Z";
      }
    }

    if (lastInstanceList.length) {
      rows.replaceChildren();
      for (const inst of lastInstanceList) {
        rows.appendChild(renderInstanceRow(inst));
      }
    }

    if (activeProbeInst && !probeModal.hidden) {
      refreshOpenProbeModal(activeProbeInst);
    }

    if (copyToast && !copyToast.hidden && lastCopyToastOk != null) {
      copyToast.textContent = lastCopyToastOk ? t("copy.ok") : t("copy.fail");
    }
    if (probeCopyToast && !probeCopyToast.hidden && lastProbeCopyToastOk != null) {
      probeCopyToast.textContent = lastProbeCopyToastOk ? t("copy.ok") : t("copy.fail");
    }
  }

  function setLang(lang) {
    if (lang !== "zh" && lang !== "en") return;
    if (lang === currentLang) {
      updateLangSwitchUI();
      return;
    }
    currentLang = lang;
    try {
      localStorage.setItem(LANG_KEY, lang);
    } catch {
      /* ignore */
    }
    document.documentElement.lang = localeTag();
    rebuildFormatters();
    applyStaticI18n();
    updateLangSwitchUI();
    applyDynamicI18n();
  }

  function bindLangSwitchers() {
    document.querySelectorAll(".lang-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        const lang = btn.getAttribute("data-lang");
        if (lang) setLang(lang);
      });
    });
  }

  function setSidebarOpen(open) {
    if (!appShell) return;
    appShell.classList.toggle("sidebar-open", open);
    if (sidebarBackdrop) sidebarBackdrop.hidden = !open;
    if (btnSidebarToggle) {
      btnSidebarToggle.setAttribute("aria-expanded", open ? "true" : "false");
      btnSidebarToggle.setAttribute("aria-label", open ? t("nav.menuClose") : t("nav.menuOpen"));
    }
  }

  function showConsole(show) {
    loadingPanel.hidden = true;
    loadingPanel.setAttribute("aria-busy", "false");
    loginPanel.hidden = show;
    if (authShell) authShell.hidden = show;
    if (appShell) appShell.hidden = !show;
    if (consolePanel) consolePanel.hidden = !show;
    document.body.classList.toggle("is-authed", show);
    setSidebarOpen(false);
  }

  function setLoginBusy(busy) {
    if (!btnLogin) return;
    btnLogin.disabled = busy;
    btnLogin.setAttribute("aria-busy", busy ? "true" : "false");
    const spinner = btnLogin.querySelector(".btn-spinner");
    if (spinner) spinner.hidden = !busy;
  }

  function mcpUrl() {
    return location.origin.replace(/\/$/, "") + "/mcp";
  }

  function probeMcpUrl(inst) {
    if (!inst || !inst.mcpBaseUrl) return "";
    const base = String(inst.mcpBaseUrl).replace(/\/$/, "");
    const path = (inst.mcpPath || "/mcp").replace(/^\/?/, "/");
    if (base.endsWith(path)) return base;
    return base + path;
  }

  function probeServerName(inst) {
    const svc = inst && inst.serviceName && String(inst.serviceName).trim();
    if (svc) return "java-diag-" + sanitizeServerName(svc);
    const host = inst && inst.host ? String(inst.host) : "probe";
    return "java-diag-" + sanitizeServerName(host);
  }

  function sanitizeServerName(s) {
    return s.replace(/[^a-zA-Z0-9._-]+/g, "-").replace(/^-+|-+$/g, "") || "probe";
  }

  function buildMcpServersObject(mcpMeta) {
    const name = (mcpMeta && mcpMeta.serverName) || "java-diag-gateway";
    const entry = { url: mcpUrl() };
    if (mcpMeta && mcpMeta.agentAuthConfigured && mcpMeta.agentAuthToken) {
      entry.headers = { Authorization: "Bearer " + mcpMeta.agentAuthToken };
    } else {
      entry.headers = { Authorization: "Bearer <token>" };
    }
    return { [name]: entry };
  }

  function probeAuthToken(inst) {
    if (!inst) return null;
    const token = inst.authToken || inst.probeToken;
    return token && String(token).trim() ? String(token).trim() : null;
  }

  function buildProbeMcpServersObject(inst) {
    const name = probeServerName(inst);
    const entry = { url: probeMcpUrl(inst) };
    const token = probeAuthToken(inst);
    if (token) {
      entry.headers = { Authorization: "Bearer " + token };
    } else {
      entry.headers = { Authorization: "Bearer <token>" };
    }
    return { [name]: entry };
  }

  function buildMcpJsonDocument(mcpMeta) {
    return { mcpServers: buildMcpServersObject(mcpMeta) };
  }

  function buildProbeMcpJsonDocument(inst) {
    return { mcpServers: buildProbeMcpServersObject(inst) };
  }

  function formatMcpJson(mcpMeta) {
    return JSON.stringify(buildMcpJsonDocument(mcpMeta), null, 2);
  }

  function formatProbeMcpJson(inst) {
    return JSON.stringify(buildProbeMcpJsonDocument(inst), null, 2);
  }

  function readUrlState() {
    const params = new URLSearchParams(location.search);
    const hash = (location.hash || "").replace(/^#/, "");
    const hashParams = new URLSearchParams(hash.includes("=") ? hash : "");
    const tabRaw = params.get("tab") || hashParams.get("tab") || (hash === TAB_INSTANCES || hash === TAB_MCP ? hash : "");
    const tab = tabRaw === TAB_INSTANCES ? TAB_INSTANCES : TAB_MCP;
    return {
      tab,
      service: params.get("service") || hashParams.get("service") || "",
      app: params.get("app") || hashParams.get("app") || "",
    };
  }

  function writeUrlState(opts) {
    if (syncingUrl) return;
    const state = readUrlState();
    const next = {
      tab: opts.tab != null ? opts.tab : state.tab,
      service: opts.service != null ? opts.service : (filterService ? filterService.value.trim() : state.service),
      app: opts.app != null ? opts.app : (filterApp ? filterApp.value.trim() : state.app),
    };
    const params = new URLSearchParams();
    if (next.tab && next.tab !== TAB_MCP) params.set("tab", next.tab);
    if (next.service) params.set("service", next.service);
    if (next.app) params.set("app", next.app);
    const qs = params.toString();
    const clean = location.pathname + (qs ? "?" + qs : "");
    if (clean !== location.pathname + location.search) {
      history.replaceState(null, "", clean);
    }
  }

  function switchTab(tabId, opts) {
    const options = opts || {};
    const isMcp = tabId === TAB_MCP;
    activeTab = isMcp ? TAB_MCP : TAB_INSTANCES;
    tabMcp.setAttribute("aria-selected", isMcp ? "true" : "false");
    tabMcp.tabIndex = isMcp ? 0 : -1;
    tabInstances.setAttribute("aria-selected", isMcp ? "false" : "true");
    tabInstances.tabIndex = isMcp ? -1 : 0;
    panelMcp.hidden = !isMcp;
    panelInstances.hidden = isMcp;
    const meta = tabMeta(activeTab);
    if (pageTitle) pageTitle.textContent = meta.title;
    if (pageSub) pageSub.textContent = meta.sub;
    setSidebarOpen(false);
    if (!options.skipUrl) writeUrlState({ tab: activeTab });
    if (options.focusTab) {
      (isMcp ? tabMcp : tabInstances).focus();
    }
  }

  function renderMcpPanel(mcpMeta) {
    sessionMcp = mcpMeta || null;
    mcpSnippet.textContent = formatMcpJson(sessionMcp);

    if (!sessionMcp || sessionMcp.agentAuthConfigured) {
      mcpTokenHint.hidden = true;
      mcpTokenHint.textContent = "";
    } else {
      mcpTokenHint.hidden = false;
      mcpTokenHint.textContent = t("mcp.tokenHint");
    }
  }

  function flashCopyToast(ok) {
    if (copyToastTimer) clearTimeout(copyToastTimer);
    lastCopyToastOk = ok;
    copyToast.textContent = ok ? t("copy.ok") : t("copy.fail");
    copyToast.classList.toggle("fail", !ok);
    copyToast.hidden = false;
    copyToastTimer = setTimeout(() => {
      copyToast.hidden = true;
      copyToastTimer = null;
      lastCopyToastOk = null;
    }, ok ? 1800 : 2800);
  }

  function flashProbeCopyToast(ok) {
    if (probeCopyToastTimer) clearTimeout(probeCopyToastTimer);
    lastProbeCopyToastOk = ok;
    probeCopyToast.textContent = ok ? t("copy.ok") : t("copy.fail");
    probeCopyToast.classList.toggle("fail", !ok);
    probeCopyToast.hidden = false;
    probeCopyToastTimer = setTimeout(() => {
      probeCopyToast.hidden = true;
      probeCopyToastTimer = null;
      lastProbeCopyToastOk = null;
    }, ok ? 1800 : 2800);
  }

  async function copyText(text, toastFn) {
    let ok = false;

    if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
      try {
        await navigator.clipboard.writeText(text);
        ok = true;
      } catch {
        ok = false;
      }
    }

    if (!ok) {
      try {
        const ta = document.createElement("textarea");
        ta.value = text;
        ta.setAttribute("readonly", "");
        ta.style.position = "fixed";
        ta.style.left = "-9999px";
        ta.style.top = "0";
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        ta.setSelectionRange(0, text.length);
        ok = document.execCommand("copy");
        document.body.removeChild(ta);
      } catch {
        ok = false;
      }
    }

    if (toastFn) {
      toastFn(ok);
    } else {
      flashCopyToast(ok);
    }
    return ok;
  }

  function formatAppStart(epochMs) {
    if (epochMs == null || epochMs === "") return "—";
    try {
      const d = new Date(Number(epochMs));
      if (Number.isNaN(d.getTime())) return "—";
      if (dateTimeFmt) return dateTimeFmt.format(d);
      return d.toISOString().replace("T", " ").slice(0, 19) + "Z";
    } catch {
      return "—";
    }
  }

  function formatAppPorts(ports) {
    if (!ports || !Array.isArray(ports) || ports.length === 0) return "—";
    return ports.join(", ");
  }

  function renderProbeApps(inst) {
    const apps = inst && Array.isArray(inst.apps) ? inst.apps : [];
    probeAppsTbody.replaceChildren();

    if (apps.length === 0) {
      probeAppsCount.hidden = true;
      probeAppsTableWrap.hidden = true;
      probeAppsEmpty.hidden = false;
      return;
    }

    probeAppsEmpty.hidden = true;
    probeAppsTableWrap.hidden = false;
    probeAppsCount.hidden = false;
    probeAppsCount.textContent = t("modal.apps.count", { n: apps.length });

    for (const app of apps) {
      const tr = document.createElement("tr");
      const name = app.name || app.displayName || app.mainClass || "—";
      const pid = app.pid != null ? String(app.pid) : "—";
      const main = app.mainClass || "—";
      tr.innerHTML = `
        <td class="mono probe-apps-name" title="${escapeHtml(name)}">${escapeHtml(name)}</td>
        <td class="mono cell-num" title="${escapeHtml(pid)}">${escapeHtml(pid)}</td>
        <td class="mono probe-apps-main" title="${escapeHtml(main)}">${escapeHtml(main)}</td>
        <td class="mono cell-num">${escapeHtml(formatAppPorts(app.ports))}</td>
        <td class="mono cell-num">${escapeHtml(formatAppStart(app.startTimeEpochMs))}</td>
      `;
      probeAppsTbody.appendChild(tr);
    }
  }

  function refreshOpenProbeModal(inst) {
    const label =
      [inst.serviceName, inst.instanceId].filter(Boolean).join(" · ") ||
      inst.instanceId ||
      t("modal.probeFallback");
    probeModalSub.textContent = label;
    renderProbeApps(inst);
    probeMcpSnippet.textContent = formatProbeMcpJson(inst);
    if (probeAuthToken(inst)) {
      probeMcpTokenHint.hidden = true;
      probeMcpTokenHint.textContent = "";
    } else {
      probeMcpTokenHint.hidden = false;
      probeMcpTokenHint.textContent = t("mcp.probeTokenHint");
    }
  }

  function openProbeModal(inst) {
    activeProbeInst = inst;
    refreshOpenProbeModal(inst);
    probeModal.hidden = false;
    document.body.classList.add("modal-open");
    probeCopyJson.focus();
  }

  function closeProbeModal() {
    probeModal.hidden = true;
    document.body.classList.remove("modal-open");
    activeProbeInst = null;
  }

  function findInstanceById(id) {
    return lastInstanceList.find((i) => i.instanceId === id) || null;
  }

  async function loadSession() {
    const res = await api("/session");
    const data = await res.json();
    if (data.authenticated === true && data.mcp) {
      renderMcpPanel(data.mcp);
    }
    return data.authenticated === true;
  }

  function cellTruncate(text, className) {
    const value = text || "—";
    const cls = className ? ' class="' + className + '"' : "";
    return `<td${cls} title="${escapeHtml(value)}"><span class="truncate mono">${escapeHtml(value)}</span></td>`;
  }

  function renderInstanceRow(inst) {
    const tr = document.createElement("tr");
    tr.className = "instance-row";
    tr.tabIndex = 0;
    tr.dataset.instanceId = inst.instanceId || "";
    const healthyRow = inst.status === "HEALTHY";
    const host = (inst.host || "") + (inst.port ? ":" + inst.port : "");
    const statusLabel = inst.status || "?";
    tr.innerHTML = `
      <td><span class="status-ind ${healthyRow ? "healthy" : "unhealthy"}">
        <span class="dot" aria-hidden="true"></span>${escapeHtml(statusLabel)}
      </span></td>
      ${cellTruncate(inst.serviceName || "—", "mono")}
      ${cellTruncate(inst.instanceId || "—", "mono")}
      ${cellTruncate(host || "—", "mono")}
      ${cellTruncate(inst.mcpBaseUrl || "—", "mono")}
      <td>${renderTags(inst.tags)}</td>
      <td class="mono cell-num">${escapeHtml(renderAppsCount(inst.apps))}</td>
      <td class="mono truncate" title="${escapeHtml(inst.version || "—")}">${escapeHtml(inst.version || "—")}</td>
      <td class="mono cell-num" title="${escapeHtml(formatHeartbeatAbsolute(inst.lastHeartbeat))}">${escapeHtml(formatHeartbeat(inst.lastHeartbeat))}</td>
      <td><button type="button" class="btn ghost btn-mcp" data-action="mcp">${escapeHtml(t("instances.viewMcp"))}</button></td>
    `;
    tr.addEventListener("click", (e) => {
      if (e.target.closest("[data-action='mcp']")) return;
      openProbeModal(inst);
    });
    tr.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        openProbeModal(inst);
      }
    });
    const btn = tr.querySelector("[data-action='mcp']");
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      openProbeModal(inst);
    });
    return tr;
  }

  function instanceListQuery() {
    const params = new URLSearchParams();
    const svc = filterService && filterService.value.trim();
    const app = filterApp && filterApp.value.trim();
    if (svc) params.set("serviceName", svc);
    if (app) params.set("app", app);
    const q = params.toString();
    return q ? "/instances?" + q : "/instances";
  }

  async function refreshInstances() {
    const res = await api(instanceListQuery());
    if (res.status === 401) {
      stopTimer();
      showConsole(false);
      return;
    }
    const data = await res.json();
    if (!data.ok) return;

    const list = data.instances || [];
    lastInstanceList = list;
    const openId = activeProbeInst && activeProbeInst.instanceId;
    if (openId) {
      const refreshed = findInstanceById(openId);
      if (refreshed) {
        activeProbeInst = refreshed;
        refreshOpenProbeModal(refreshed);
      }
    }

    document.getElementById("stat-count").textContent = String(data.count ?? list.length);
    const healthy = list.filter((i) => i.status === "HEALTHY").length;
    document.getElementById("stat-healthy").textContent = String(healthy);
    document.getElementById("stat-unhealthy").textContent = String(list.length - healthy);

    rows.replaceChildren();
    if (list.length === 0) {
      emptyState.hidden = false;
    } else {
      emptyState.hidden = true;
      for (const inst of list) {
        rows.appendChild(renderInstanceRow(inst));
      }
    }
    const now = new Date();
    lastRefreshAt = now;
    refreshMeta.textContent = t("instances.refreshAt", {
      time: timeFmt ? timeFmt.format(now) : now.toLocaleTimeString(),
    });
    footClock.textContent = dateTimeFmt ? dateTimeFmt.format(now) : now.toISOString().replace("T", " ").slice(0, 19) + "Z";
  }

  function renderAppsCount(apps) {
    if (!apps || !Array.isArray(apps) || apps.length === 0) return "—";
    return String(apps.length);
  }

  function renderTags(tags) {
    if (!tags || typeof tags !== "object") return "—";
    const entries = Object.entries(tags);
    if (entries.length === 0) return "—";
    return (
      '<span class="tags">' +
      entries
        .map(([k, v]) => {
          const label = k + "=" + String(v);
          return `<span class="tag" title="${escapeHtml(label)}" translate="no">${escapeHtml(label)}</span>`;
        })
        .join("") +
      "</span>"
    );
  }

  function formatHeartbeatAbsolute(iso) {
    if (!iso) return "—";
    try {
      const d = new Date(iso);
      if (Number.isNaN(d.getTime())) return iso;
      return dateTimeFmt ? dateTimeFmt.format(d) : iso;
    } catch {
      return iso;
    }
  }

  function formatHeartbeat(iso) {
    if (!iso) return "—";
    try {
      const d = new Date(iso);
      if (Number.isNaN(d.getTime())) return iso;
      const diffSec = Math.round((d.getTime() - Date.now()) / 1000);
      const abs = Math.abs(diffSec);
      if (relativeTimeFmt && abs < 90) {
        return relativeTimeFmt.format(diffSec, "second");
      }
      if (relativeTimeFmt && abs < 3600 * 36) {
        return relativeTimeFmt.format(Math.round(diffSec / 60), "minute");
      }
      return dateTimeFmt ? dateTimeFmt.format(d) : iso.replace("T", " ").replace(/\.\d+Z$/, "Z");
    } catch {
      return iso;
    }
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function startTimer() {
    stopTimer();
    timer = setInterval(refreshInstances, REFRESH_MS);
  }

  function stopTimer() {
    if (timer) clearInterval(timer);
    timer = null;
  }

  function focusFirstLoginError() {
    const user = formLogin.querySelector('[name="username"]');
    const pass = formLogin.querySelector('[name="password"]');
    if (user && !user.value.trim()) {
      user.focus();
      return;
    }
    if (pass) pass.focus();
  }

  function loginErrorMessage(data, res) {
    const raw = (data && data.error) || "";
    if (res.status === 401 || /invalid|unauthorized|密码|账号|credential/i.test(raw)) {
      return raw ? t("login.err.credentials.raw", { raw }) : t("login.err.credentials");
    }
    if (res.status >= 500) {
      return raw ? t("login.err.server.raw", { raw }) : t("login.err.server");
    }
    return raw ? t("login.err.generic.raw", { raw }) : t("login.err.generic");
  }

  formLogin.addEventListener("submit", async (e) => {
    e.preventDefault();
    loginError.hidden = true;
    loginError.textContent = "";
    setLoginBusy(true);
    const fd = new FormData(formLogin);
    const body = JSON.stringify({
      username: fd.get("username"),
      password: fd.get("password"),
    });
    try {
      const res = await api("/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.ok) {
        loginError.textContent = loginErrorMessage(data, res);
        loginError.hidden = false;
        focusFirstLoginError();
        return;
      }
      showConsole(true);
      const state = readUrlState();
      switchTab(state.tab || TAB_MCP);
      await loadSession();
      await refreshInstances();
      startTimer();
    } catch {
      loginError.textContent = t("login.err.network");
      loginError.hidden = false;
      focusFirstLoginError();
    } finally {
      setLoginBusy(false);
    }
  });

  btnCopyUrl.addEventListener("click", () => copyText(mcpUrl()));
  btnCopyJson.addEventListener("click", () => copyText(formatMcpJson(sessionMcp)));

  probeCopyUrl.addEventListener("click", () => {
    if (activeProbeInst) copyText(probeMcpUrl(activeProbeInst), flashProbeCopyToast);
  });
  probeCopyJson.addEventListener("click", () => {
    if (activeProbeInst) copyText(formatProbeMcpJson(activeProbeInst), flashProbeCopyToast);
  });

  probeModal.querySelectorAll("[data-modal-close]").forEach((el) => {
    el.addEventListener("click", closeProbeModal);
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (!probeModal.hidden) {
        closeProbeModal();
        return;
      }
      if (appShell && appShell.classList.contains("sidebar-open")) {
        setSidebarOpen(false);
      }
      return;
    }

    const inTablist = e.target && e.target.getAttribute && e.target.getAttribute("role") === "tab";
    if (!inTablist) return;
    if (e.key !== "ArrowDown" && e.key !== "ArrowUp" && e.key !== "ArrowLeft" && e.key !== "ArrowRight" && e.key !== "Home" && e.key !== "End") {
      return;
    }
    e.preventDefault();
    const idx = tabButtons.indexOf(e.target);
    if (idx < 0) return;
    let next = idx;
    if (e.key === "ArrowDown" || e.key === "ArrowRight") next = (idx + 1) % tabButtons.length;
    if (e.key === "ArrowUp" || e.key === "ArrowLeft") next = (idx - 1 + tabButtons.length) % tabButtons.length;
    if (e.key === "Home") next = 0;
    if (e.key === "End") next = tabButtons.length - 1;
    const tabId = tabButtons[next].dataset.tab;
    switchTab(tabId, { focusTab: true });
  });

  function scheduleFilterRefresh() {
    if (filterDebounce) clearTimeout(filterDebounce);
    filterDebounce = setTimeout(() => {
      writeUrlState({});
      if (!consolePanel.hidden) refreshInstances();
    }, 350);
  }

  if (filterService) filterService.addEventListener("input", scheduleFilterRefresh);
  if (filterApp) filterApp.addEventListener("input", scheduleFilterRefresh);

  tabMcp.addEventListener("click", () => switchTab(TAB_MCP));
  tabInstances.addEventListener("click", () => switchTab(TAB_INSTANCES));

  if (btnSidebarToggle) {
    btnSidebarToggle.addEventListener("click", () => {
      const open = !appShell.classList.contains("sidebar-open");
      setSidebarOpen(open);
    });
  }
  if (sidebarBackdrop) {
    sidebarBackdrop.addEventListener("click", () => setSidebarOpen(false));
  }

  btnLogout.addEventListener("click", async () => {
    await api("/logout", { method: "POST" });
    stopTimer();
    closeProbeModal();
    showConsole(false);
  });

  window.addEventListener("popstate", () => {
    syncingUrl = true;
    const state = readUrlState();
    if (filterService) filterService.value = state.service;
    if (filterApp) filterApp.value = state.app;
    switchTab(state.tab, { skipUrl: true });
    syncingUrl = false;
    if (!consolePanel.hidden) refreshInstances();
  });

  (async function init() {
    currentLang = detectDefaultLang();
    document.documentElement.lang = localeTag();
    rebuildFormatters();
    applyStaticI18n();
    updateLangSwitchUI();
    bindLangSwitchers();

    const state = readUrlState();
    syncingUrl = true;
    if (filterService) filterService.value = state.service;
    if (filterApp) filterApp.value = state.app;
    switchTab(state.tab || TAB_MCP, { skipUrl: true });
    syncingUrl = false;
    writeUrlState({ tab: state.tab || TAB_MCP, service: state.service, app: state.app });
    if (await loadSession()) {
      showConsole(true);
      await refreshInstances();
      startTimer();
    } else {
      showConsole(false);
    }
  })();
})();
