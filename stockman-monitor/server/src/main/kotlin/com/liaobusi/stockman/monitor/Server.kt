package com.liaobusi.stockman.monitor

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        monitorModule()
    }.start(wait = true)
}

fun Application.monitorModule() {
    val engine = MarketEngine()

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CORS) {
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHost("localhost:8081")
        allowHost("127.0.0.1:8081")
    }
    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respond(
                mapOf(
                    "name" to "StockMan Compose Monitor",
                    "status" to "running",
                    "webSocket" to "ws://localhost:8080/ws",
                    "database" to engine.database.path(),
                    "dbViewer" to "http://localhost:8080/db"
                )
            )
        }
        get("/db") {
            call.respondText(dbViewerHtml(), ContentType.Text.Html)
        }
        get("/api/snapshot") {
            call.respond(engine.snapshot())
        }
        get("/api/sync/status") {
            call.respond(engine.syncStatus())
        }
        get("/api/sync/history/status") {
            call.respond(engine.historySyncStatus())
        }
        post("/api/sync/stocks") {
            runCatching {
                engine.syncStocksNow()
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (it.message ?: "sync failed")))
            }
        }
        post("/api/sync/history") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 1
            val stockLimit = call.request.queryParameters["stockLimit"]?.toIntOrNull()?.takeIf { it > 0 }
            val codes = call.request.queryParameters.getAll("code").orEmpty() +
                call.request.queryParameters["codes"].orEmpty().split(',').filter { it.isNotBlank() }
            runCatching {
                engine.syncHistoryNow(limit = limit, stockLimit = stockLimit, codes = codes.map { it.trim() }.distinct())
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (it.message ?: "history sync failed")))
            }
        }
        get("/api/db/tables") {
            call.respond(DbTables(database = engine.database.path(), tables = engine.database.tableNames()))
        }
        get("/api/db/table/{name}") {
            val name = call.parameters["name"].orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            runCatching {
                engine.database.queryTable(name, limit, offset)
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "bad request")))
            }
        }
        get("/api/history/{code}") {
            val code = call.parameters["code"].orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 120
            runCatching {
                engine.database.dailyLines(code, limit)
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "bad request")))
            }
        }
        post("/api/tick") {
            val request = call.receive<ManualTickRequest>()
            val tick = engine.manualTick(request)
            if (tick == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown code or empty tick"))
            } else {
                call.respond(tick)
            }
        }
        webSocket("/ws") {
            engine.connect(this)
        }
    }
}

private fun dbViewerHtml(): String = """
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>StockMan DB Viewer</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      color: #20242a;
      background: #f4f6f8;
    }
    main { max-width: 1180px; margin: 0 auto; padding: 24px; }
    header {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      align-items: center;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }
    h1 { margin: 0; font-size: 28px; }
    p { margin: 6px 0 0; color: #66707c; }
    .tabs { display: flex; gap: 8px; flex-wrap: wrap; margin: 16px 0; }
    .chart {
      background: #fff;
      border: 1px solid #dfe5ec;
      border-radius: 8px;
      margin-bottom: 16px;
      overflow: hidden;
    }
    .chartBody { padding: 0 16px 16px; }
    .chart svg { width: 100%; height: 260px; display: block; }
    .chartTip {
      min-height: 24px;
      padding: 0 16px 12px;
      color: #334155;
      font-size: 14px;
      font-weight: 650;
    }
    .hoverDot { opacity: 0; pointer-events: none; }
    .hit { fill: transparent; cursor: crosshair; }
    .hit:hover + .hoverDot { opacity: 1; }
    .toolbar {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
      padding: 12px 16px;
      border-bottom: 1px solid #edf0f4;
      background: #fff;
    }
    button {
      border: 0;
      border-radius: 8px;
      padding: 10px 13px;
      font-weight: 700;
      cursor: pointer;
      color: #20242a;
      background: #e7ebf0;
    }
    button:disabled { cursor: not-allowed; opacity: .45; }
    button.active { color: #fff; background: #20242a; }
    select, input {
      height: 36px;
      border: 1px solid #d5dce5;
      border-radius: 8px;
      padding: 0 10px;
      background: #fff;
      color: #20242a;
    }
    input { width: 88px; }
    .panel {
      background: #fff;
      border: 1px solid #dfe5ec;
      border-radius: 8px;
      overflow: auto;
    }
    .meta {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      padding: 14px 16px;
      border-bottom: 1px solid #edf0f4;
      color: #66707c;
      flex-wrap: wrap;
    }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th, td {
      padding: 11px 10px;
      border-bottom: 1px solid #edf0f4;
      text-align: left;
      white-space: nowrap;
    }
    th { background: #f7f9fb; color: #66707c; }
    .error { color: #b42318; padding: 16px; }
    code { color: #334155; }
  </style>
</head>
<body>
<main>
  <header>
    <div>
      <h1>StockMan DB Viewer</h1>
      <p id="dbPath">正在连接数据库...</p>
      <p id="syncStatus">正在读取同步状态...</p>
    </div>
    <div>
      <button id="syncButton">同步大A股票</button>
      <a href="http://localhost:8081/">返回前端</a>
    </div>
  </header>
  <div class="tabs" id="tabs"></div>
  <section class="chart" id="chartSection" hidden>
    <div class="toolbar">
      <strong>日线查询</strong>
      <label>代码 <input id="chartCode" value="688233" maxlength="6"></label>
      <label>条数 <input id="chartLimit" type="number" min="5" max="500" value="120"></label>
      <button id="loadChart">查询</button>
      <span id="chartTitle"></span>
    </div>
    <div class="chartTip" id="chartTip"></div>
    <div class="chartBody" id="chartBody"></div>
  </section>
  <section class="panel">
    <div class="meta">
      <strong id="tableName">-</strong>
      <span id="tableTotal">-</span>
    </div>
    <div class="toolbar">
      <button id="prevPage">上一页</button>
      <span id="pageInfo">第 - 页</span>
      <button id="nextPage">下一页</button>
      <label>每页
        <select id="pageSize">
          <option value="50">50</option>
          <option value="100">100</option>
          <option value="200" selected>200</option>
          <option value="500">500</option>
        </select>
      </label>
      <label>跳转
        <input id="pageInput" type="number" min="1" value="1">
      </label>
      <button id="jumpPage">Go</button>
    </div>
    <div id="content"></div>
  </section>
</main>
<script>
let activeTable = "";
let currentPage = 1;
let currentTotal = 0;

async function loadTables() {
  const rsp = await fetch("/api/db/tables");
  const data = await rsp.json();
  document.getElementById("dbPath").innerHTML = "SQLite: <code>" + data.database + "</code>";
  await loadSyncStatus();
  const tabs = document.getElementById("tabs");
  tabs.innerHTML = "";
  data.tables.forEach((name, index) => {
    const button = document.createElement("button");
    button.textContent = name;
    button.onclick = () => loadTable(name, 1);
    tabs.appendChild(button);
    if (index === 0) loadTable(name, 1);
  });
}

function pageSize() {
  return Number(document.getElementById("pageSize").value || 200);
}

function maxPage() {
  return Math.max(1, Math.ceil(currentTotal / pageSize()));
}

function updatePager() {
  const pages = maxPage();
  document.getElementById("pageInfo").textContent = "第 " + currentPage + " / " + pages + " 页";
  document.getElementById("pageInput").value = currentPage;
  document.getElementById("prevPage").disabled = currentPage <= 1;
  document.getElementById("nextPage").disabled = currentPage >= pages;
}

async function loadTable(name, page = currentPage) {
  activeTable = name;
  currentPage = Math.max(1, page);
  document.getElementById("chartSection").hidden = name !== "historystock";
  document.querySelectorAll("button").forEach(btn => {
    btn.classList.toggle("active", btn.textContent === name);
  });
  document.getElementById("tableName").textContent = name;
  document.getElementById("tableTotal").textContent = "加载中";
  const content = document.getElementById("content");
  content.innerHTML = "";
  try {
    const limit = pageSize();
    const offset = (currentPage - 1) * limit;
    const rsp = await fetch("/api/db/table/" + encodeURIComponent(name) + "?limit=" + limit + "&offset=" + offset);
    const data = await rsp.json();
    currentTotal = data.total;
    const from = data.total === 0 ? 0 : offset + 1;
    const to = Math.min(offset + data.rows.length, data.total);
    document.getElementById("tableTotal").textContent = "共 " + data.total + " 行，当前 " + from + "-" + to;
    updatePager();
    const table = document.createElement("table");
    const thead = document.createElement("thead");
    const headerRow = document.createElement("tr");
    data.columns.forEach(column => {
      const th = document.createElement("th");
      th.textContent = column;
      headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);
    const tbody = document.createElement("tbody");
    data.rows.forEach(row => {
      const tr = document.createElement("tr");
      data.columns.forEach(column => {
        const td = document.createElement("td");
        td.textContent = row[column] ?? "";
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    content.appendChild(table);
    if (name === "historystock") loadDailyChart().catch(() => {});
  } catch (error) {
    content.innerHTML = '<div class="error">' + error + '</div>';
    updatePager();
  }
}

async function loadSyncStatus() {
  const rsp = await fetch("/api/sync/status");
  const data = await rsp.json();
  const date = data.lastStockSyncDate || "-";
  const count = data.lastStockSyncCount || "-";
  const source = data.lastStockSyncSource || "-";
  document.getElementById("syncStatus").textContent =
    "stock " + data.stockCount + " 行，historystock " + data.historyCount + " 行，上次同步 " + date + " / " + count + " 只 / " + source;
}

function renderDailyChart(data) {
  const body = document.getElementById("chartBody");
  const lines = data.lines || [];
  document.getElementById("chartTitle").textContent = data.code + " " + (data.name || "") + "，" + lines.length + " 条";
  document.getElementById("chartTip").textContent = "";
  if (lines.length === 0) {
    body.innerHTML = '<div class="error">没有日线数据</div>';
    return;
  }
  const width = 1000;
  const height = 260;
  const pad = 34;
  const values = lines.map(item => item.closePrice);
  const min = Math.min.apply(null, values);
  const max = Math.max.apply(null, values);
  const span = Math.max(0.01, max - min);
  const x = index => pad + index * ((width - pad * 2) / Math.max(1, lines.length - 1));
  const y = value => height - pad - ((value - min) / span) * (height - pad * 2);
  const points = lines.map((item, index) => x(index).toFixed(1) + "," + y(item.closePrice).toFixed(1)).join(" ");
  const last = lines[lines.length - 1];
  const first = lines[0];
  const hitWidth = Math.max(6, (width - pad * 2) / Math.max(1, lines.length - 1));
  const hovers = lines.map((item, index) => {
    const cx = x(index);
    const cy = y(item.closePrice);
    const label = item.date + ' 开 ' + item.openPrice.toFixed(2) + ' / 收 ' + item.closePrice.toFixed(2) + ' / ' + item.chg.toFixed(2) + '%';
    return '<rect class="hit" x="' + (cx - hitWidth / 2).toFixed(1) + '" y="' + pad + '" width="' + hitWidth.toFixed(1) + '" height="' + (height - pad * 2) + '" data-label="' + label + '"></rect>' +
      '<circle class="hoverDot" cx="' + cx.toFixed(1) + '" cy="' + cy.toFixed(1) + '" r="5" fill="#ef4444"></circle>';
  }).join("");
  body.innerHTML =
    '<svg id="dailySvg" viewBox="0 0 ' + width + ' ' + height + '" role="img">' +
    '<line x1="' + pad + '" y1="' + pad + '" x2="' + pad + '" y2="' + (height - pad) + '" stroke="#d5dce5"/>' +
    '<line x1="' + pad + '" y1="' + (height - pad) + '" x2="' + (width - pad) + '" y2="' + (height - pad) + '" stroke="#d5dce5"/>' +
    '<text x="' + pad + '" y="22" fill="#66707c" font-size="13">高 ' + max.toFixed(2) + '</text>' +
    '<text x="' + pad + '" y="' + (height - 10) + '" fill="#66707c" font-size="13">低 ' + min.toFixed(2) + '</text>' +
    '<text x="' + (width - 180) + '" y="22" fill="#20242a" font-size="14">收 ' + last.closePrice.toFixed(2) + ' / ' + last.chg.toFixed(2) + '%</text>' +
    '<polyline points="' + points + '" fill="none" stroke="#2563eb" stroke-width="2.5"/>' +
    '<circle cx="' + x(lines.length - 1).toFixed(1) + '" cy="' + y(last.closePrice).toFixed(1) + '" r="4" fill="#2563eb"/>' +
    '<text x="' + pad + '" y="' + (height - 5) + '" fill="#66707c" font-size="12">' + first.date + '</text>' +
    '<text x="' + (width - 90) + '" y="' + (height - 5) + '" fill="#66707c" font-size="12">' + last.date + '</text>' +
    hovers +
    '</svg>';
  const tip = document.getElementById("chartTip");
  document.querySelectorAll("#dailySvg .hit").forEach(node => {
    node.addEventListener("mouseenter", () => { tip.textContent = node.dataset.label || ""; });
    node.addEventListener("mousemove", () => { tip.textContent = node.dataset.label || ""; });
    node.addEventListener("mouseleave", () => { tip.textContent = ""; });
  });
}

async function loadDailyChart() {
  const code = document.getElementById("chartCode").value.trim();
  const limit = Number(document.getElementById("chartLimit").value || 120);
  if (!code) return;
  const rsp = await fetch("/api/history/" + encodeURIComponent(code) + "?limit=" + limit);
  if (!rsp.ok) throw new Error(await rsp.text());
  renderDailyChart(await rsp.json());
}

document.getElementById("syncButton").onclick = async () => {
  const button = document.getElementById("syncButton");
  button.disabled = true;
  button.textContent = "同步中...";
  try {
    const rsp = await fetch("/api/sync/stocks", { method: "POST" });
    if (!rsp.ok) throw new Error(await rsp.text());
    await loadTables();
    if (activeTable) await loadTable(activeTable);
  } catch (error) {
    alert(error);
  } finally {
    button.disabled = false;
    button.textContent = "同步大A股票";
  }
};

document.getElementById("loadChart").onclick = () => {
  loadDailyChart().catch(error => {
    document.getElementById("chartBody").innerHTML = '<div class="error">' + error + '</div>';
  });
};

document.getElementById("prevPage").onclick = () => {
  if (activeTable && currentPage > 1) loadTable(activeTable, currentPage - 1);
};

document.getElementById("nextPage").onclick = () => {
  if (activeTable && currentPage < maxPage()) loadTable(activeTable, currentPage + 1);
};

document.getElementById("jumpPage").onclick = () => {
  if (!activeTable) return;
  const target = Number(document.getElementById("pageInput").value || 1);
  loadTable(activeTable, Math.min(Math.max(1, target), maxPage()));
};

document.getElementById("pageSize").onchange = () => {
  if (activeTable) loadTable(activeTable, 1);
};

loadTables();
</script>
</body>
</html>
""".trimIndent()
