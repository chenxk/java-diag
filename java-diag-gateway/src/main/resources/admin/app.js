(function () {
  const REFRESH_MS = 8000;
  const TAB_MCP = "mcp";
  const TAB_INSTANCES = "instances";
  const api = (path, options) =>
    fetch("/admin/api" + path, { credentials: "same-origin", ...options });

  const authShell = document.getElementById("auth-shell");
  const appShell = document.getElementById("app-shell");
  const loadingPanel = document.getElementById("panel-loading");
  const loginPanel = document.getElementById("panel-login");
  const consolePanel = document.getElementById("panel-console");
  const formLogin = document.getElementById("form-login");
  const loginError = document.getElementById("login-error");
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

  const TAB_META = {
    [TAB_MCP]: { title: "MCP 配置", sub: "Cursor MCP 接入片段" },
    [TAB_INSTANCES]: { title: "探针实例", sub: "Registry 注册列表" },
  };

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
  const probeAppsBlock = document.getElementById("probe-apps-block");
  const probeAppsCount = document.getElementById("probe-apps-count");
  const probeAppsTableWrap = document.getElementById("probe-apps-table-wrap");
  const probeAppsTbody = document.getElementById("probe-apps-tbody");
  const probeAppsEmpty = document.getElementById("probe-apps-empty");
  let filterDebounce = null;

  function setSidebarOpen(open) {
    if (!appShell) return;
    appShell.classList.toggle("sidebar-open", open);
    if (sidebarBackdrop) sidebarBackdrop.hidden = !open;
    if (btnSidebarToggle) {
      btnSidebarToggle.setAttribute("aria-expanded", open ? "true" : "false");
      btnSidebarToggle.setAttribute("aria-label", open ? "关闭菜单" : "打开菜单");
    }
  }

  function showConsole(show) {
    loadingPanel.hidden = true;
    loadingPanel.setAttribute("aria-busy", "false");
    loginPanel.hidden = show;
    if (authShell) authShell.hidden = show;
    if (appShell) appShell.hidden = !show;
    if (consolePanel) consolePanel.hidden = !show;
    setSidebarOpen(false);
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

  function switchTab(tabId) {
    const isMcp = tabId === TAB_MCP;
    tabMcp.setAttribute("aria-selected", isMcp ? "true" : "false");
    tabMcp.tabIndex = isMcp ? 0 : -1;
    tabInstances.setAttribute("aria-selected", isMcp ? "false" : "true");
    tabInstances.tabIndex = isMcp ? -1 : 0;
    panelMcp.hidden = !isMcp;
    panelInstances.hidden = isMcp;
    const meta = TAB_META[tabId] || TAB_META[TAB_MCP];
    if (pageTitle) pageTitle.textContent = meta.title;
    if (pageSub) pageSub.textContent = meta.sub;
    setSidebarOpen(false);
  }

  function renderMcpPanel(mcpMeta) {
    sessionMcp = mcpMeta || null;
    mcpSnippet.textContent = formatMcpJson(sessionMcp);

    if (!sessionMcp || sessionMcp.agentAuthConfigured) {
      mcpTokenHint.hidden = true;
      mcpTokenHint.textContent = "";
    } else {
      mcpTokenHint.hidden = false;
      mcpTokenHint.textContent =
        "网关未配置 Agent token（启动时 --token 或 JAVA_DIAG_AUTH_TOKEN）；上方 headers 为占位，请自行替换。";
    }
  }

  function flashCopyToast(ok) {
    if (copyToastTimer) clearTimeout(copyToastTimer);
    copyToast.textContent = ok ? "已复制到剪贴板" : "复制失败，请手动选择上方 JSON";
    copyToast.classList.toggle("fail", !ok);
    copyToast.hidden = false;
    copyToastTimer = setTimeout(() => {
      copyToast.hidden = true;
      copyToastTimer = null;
    }, ok ? 1800 : 2800);
  }

  function flashProbeCopyToast(ok) {
    if (probeCopyToastTimer) clearTimeout(probeCopyToastTimer);
    probeCopyToast.textContent = ok ? "已复制到剪贴板" : "复制失败，请手动选择上方 JSON";
    probeCopyToast.classList.toggle("fail", !ok);
    probeCopyToast.hidden = false;
    probeCopyToastTimer = setTimeout(() => {
      probeCopyToast.hidden = true;
      probeCopyToastTimer = null;
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
    probeAppsCount.textContent = apps.length + " 个进程";

    for (const app of apps) {
      const tr = document.createElement("tr");
      const name = app.name || app.displayName || app.mainClass || "—";
      const pid = app.pid != null ? String(app.pid) : "—";
      const main = app.mainClass || "—";
      tr.innerHTML = `
        <td class="mono probe-apps-name" title="${escapeHtml(name)}">${escapeHtml(name)}</td>
        <td class="mono">${escapeHtml(pid)}</td>
        <td class="mono probe-apps-main" title="${escapeHtml(main)}">${escapeHtml(main)}</td>
        <td class="mono">${escapeHtml(formatAppPorts(app.ports))}</td>
        <td class="mono">${escapeHtml(formatAppStart(app.startTimeEpochMs))}</td>
      `;
      probeAppsTbody.appendChild(tr);
    }
  }

  function openProbeModal(inst) {
    activeProbeInst = inst;
    const label = [inst.serviceName, inst.instanceId].filter(Boolean).join(" · ") || inst.instanceId || "探针";
    probeModalSub.textContent = label;
    renderProbeApps(inst);
    probeMcpSnippet.textContent = formatProbeMcpJson(inst);
    if (probeAuthToken(inst)) {
      probeMcpTokenHint.hidden = true;
      probeMcpTokenHint.textContent = "";
    } else {
      probeMcpTokenHint.hidden = false;
      probeMcpTokenHint.textContent =
        "该探针未上报 probe token（启动时 --token / JAVA_DIAG_AUTH_TOKEN）；上方 headers 为占位。";
    }
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

  function renderInstanceRow(inst) {
    const tr = document.createElement("tr");
    tr.className = "instance-row";
    tr.tabIndex = 0;
    tr.dataset.instanceId = inst.instanceId || "";
    const healthyRow = inst.status === "HEALTHY";
    tr.innerHTML = `
      <td><span class="status-pill ${healthyRow ? "healthy" : "unhealthy"}">
        <span class="dot"></span>${escapeHtml(inst.status || "?")}
      </span></td>
      <td class="mono">${escapeHtml(inst.serviceName || "—")}</td>
      <td class="mono">${escapeHtml(inst.instanceId || "—")}</td>
      <td class="mono">${escapeHtml((inst.host || "") + (inst.port ? ":" + inst.port : ""))}</td>
      <td class="mono">${escapeHtml(inst.mcpBaseUrl || "—")}</td>
      <td>${renderTags(inst.tags)}</td>
      <td class="mono">${renderAppsCount(inst.apps)}</td>
      <td class="mono">${escapeHtml(inst.version || "—")}</td>
      <td class="mono">${escapeHtml(formatHeartbeat(inst.lastHeartbeat))}</td>
      <td><button type="button" class="btn ghost btn-mcp" data-action="mcp">MCP</button></td>
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
        renderProbeApps(refreshed);
        probeMcpSnippet.textContent = formatProbeMcpJson(refreshed);
        if (probeAuthToken(refreshed)) {
          probeMcpTokenHint.hidden = true;
          probeMcpTokenHint.textContent = "";
        } else {
          probeMcpTokenHint.hidden = false;
          probeMcpTokenHint.textContent =
            "该探针未上报 probe token（启动时 --token / JAVA_DIAG_AUTH_TOKEN）；上方 headers 为占位。";
        }
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
    refreshMeta.textContent = "刷新于 " + now.toLocaleTimeString();
    footClock.textContent = now.toISOString().replace("T", " ").slice(0, 19) + "Z";
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
        .map(([k, v]) => `<span class="tag">${escapeHtml(k)}=${escapeHtml(String(v))}</span>`)
        .join("") +
      "</span>"
    );
  }

  function formatHeartbeat(iso) {
    if (!iso) return "—";
    try {
      const d = new Date(iso);
      const sec = Math.round((Date.now() - d.getTime()) / 1000);
      if (sec < 90) return sec + "s ago";
      return iso.replace("T", " ").replace(/\.\d+Z$/, "Z");
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

  formLogin.addEventListener("submit", async (e) => {
    e.preventDefault();
    loginError.hidden = true;
    const fd = new FormData(formLogin);
    const body = JSON.stringify({
      username: fd.get("username"),
      password: fd.get("password"),
    });
    const res = await api("/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
    });
    const data = await res.json();
    if (!res.ok || !data.ok) {
      loginError.textContent = data.error || "登录失败";
      loginError.hidden = false;
      return;
    }
    showConsole(true);
    switchTab(TAB_MCP);
    await loadSession();
    await refreshInstances();
    startTimer();
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
    if (e.key !== "Escape") return;
    if (!probeModal.hidden) {
      closeProbeModal();
      return;
    }
    if (appShell && appShell.classList.contains("sidebar-open")) {
      setSidebarOpen(false);
    }
  });

  function scheduleFilterRefresh() {
    if (filterDebounce) clearTimeout(filterDebounce);
    filterDebounce = setTimeout(() => {
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

  (async function init() {
    switchTab(TAB_MCP);
    if (await loadSession()) {
      showConsole(true);
      await refreshInstances();
      startTimer();
    } else {
      showConsole(false);
    }
  })();
})();
