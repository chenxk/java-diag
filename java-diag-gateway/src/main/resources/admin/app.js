(function () {
  const REFRESH_MS = 8000;
  const api = (path, options) =>
    fetch("/admin/api" + path, { credentials: "same-origin", ...options });

  const loginPanel = document.getElementById("panel-login");
  const consolePanel = document.getElementById("panel-console");
  const formLogin = document.getElementById("form-login");
  const loginError = document.getElementById("login-error");
  const btnLogout = document.getElementById("btn-logout");
  const rows = document.getElementById("instance-rows");
  const emptyState = document.getElementById("empty-state");
  const refreshMeta = document.getElementById("refresh-meta");
  const footClock = document.getElementById("foot-clock");

  let timer = null;

  function showConsole(show) {
    loginPanel.hidden = show;
    consolePanel.hidden = !show;
    btnLogout.hidden = !show;
  }

  async function checkSession() {
    const res = await api("/session");
    const data = await res.json();
    return data.authenticated === true;
  }

  async function refreshInstances() {
    const res = await api("/instances");
    if (res.status === 401) {
      stopTimer();
      showConsole(false);
      return;
    }
    const data = await res.json();
    if (!data.ok) return;

    const list = data.instances || [];
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
        const tr = document.createElement("tr");
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
          <td class="mono">${escapeHtml(inst.version || "—")}</td>
          <td class="mono">${escapeHtml(formatHeartbeat(inst.lastHeartbeat))}</td>
        `;
        rows.appendChild(tr);
      }
    }
    const now = new Date();
    refreshMeta.textContent = "刷新于 " + now.toLocaleTimeString();
    footClock.textContent = now.toISOString().replace("T", " ").slice(0, 19) + "Z";
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
    await refreshInstances();
    startTimer();
  });

  btnLogout.addEventListener("click", async () => {
    await api("/logout", { method: "POST" });
    stopTimer();
    showConsole(false);
  });

  (async function init() {
    if (await checkSession()) {
      showConsole(true);
      await refreshInstances();
      startTimer();
    } else {
      showConsole(false);
    }
  })();
})();
