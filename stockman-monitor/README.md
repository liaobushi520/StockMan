# StockMan Compose Monitor

从原 Android 项目的异动监控逻辑拆出来的本地 Web 测试项目。

- `server`: Ktor + SQLite 服务，负责同步行情、落库、监控异动、WebSocket 推送。
- `web`: Kotlin Compose for Web 前端，负责行情表、异动列表和浏览器通知。
- 数据库: `server/data/stockman-monitor.db`

## 环境要求

- JDK 17
- IntelliJ IDEA 2024+ 或命令行终端
- 网络可访问行情源

本项目使用 Gradle Wrapper。因为 wrapper 在父 Android 项目根目录，所以命令都从 `stockman-monitor` 目录里用 `./gradlew` 执行。

## 数据同步策略

实时股票同步入口是 `RealtimeStockSync`，行情源策略位于 `server/src/main/kotlin/.../sync/strategy`：

- `RealtimeStockStrategy`: 单个实时行情源策略接口。
- `PagedRequestConfig`: 配置页码类接口的 `baseUrl`、`path`、`headers`、固定查询参数、`page`、`pageSize` 参数名。
- `RangeRequestConfig`: 配置范围类接口的 `baseUrl`、`path`、`headers`、固定查询参数、`begin`、`end` 参数名。

当前实时刷新可配置数据源：

- `Sina`: 默认实时刷新源；更新行情字段，保留 `stock.bk`，适合高频刷新和监控。
- `EastMoney`: 可手动选择；会完整刷新 `stock` 并更新 `bk` 字段，调用频率要低，避免风控。

任一实时源必须返回超过 5000 只才算完整；如果返回数据不完整，本次 `stock` 同步会失败并保留旧数据。

网络层使用 OkHttp + Retrofit。Retrofit API 使用 `@Url`、`@HeaderMap`、`@QueryMap` 组合请求，不在策略里手写整段 URL；响应解析使用 Gson 的 `JsonObject` / `JsonArray`，便于兼容不同数据源返回结构。

手动刷新实时股票：

```bash
curl -X POST "http://localhost:8080/api/sync/stocks?source=sina"
curl -X POST "http://localhost:8080/api/sync/stocks?source=eastmoney"
```

手动同步当天历史日 K：

```bash
curl -X POST "http://localhost:8080/api/sync/history"
```

手动回补历史日 K：

```bash
curl -X POST "http://localhost:8080/api/sync/history"
```

单只或多只重试：

```bash
curl -X POST "http://localhost:8080/api/sync/history?code=302132"
curl -X POST "http://localhost:8080/api/sync/history?codes=302132,920156"
```

同步状态：

```bash
curl http://localhost:8080/api/sync/status
curl http://localhost:8080/api/sync/history/status
```

## 定时任务

服务端定时任务集中在 `MarketEngine`、`RealtimeStockSync` 和 `HistoryStockSync`：

| 任务 | 触发频率/时间 | 数据源 | 行为 |
| --- | --- | --- | --- |
| 初始化 stock | 服务启动后，如果 `stock` 少于 1000 行 | 新浪 | 拉取全量 A 股实时行情，保留已有 `bk`。 |
| 实时行情计划刷新 | 交易日 `09:25`、`12:10`、`15:10`，由每 60 秒调度循环检查 | 新浪 | 更新 `stock` 行情字段，保留已有 `bk`。 |
| 东方财富低频刷新 | 交易日 `09:25-09:40`、`14:55-15:10` 窗口各一次，仍由每 60 秒调度循环检查 | 东方财富 | 低频更新实时行情；自动任务保留已有 `bk`，降低风控风险。 |
| 收盘后实时转历史 | 交易日 15:00 后，每 60 秒检查，当天只成功执行一次 | 当前 `stock` 快照 | 如果 `stock` 日期等于当前历史目标交易日且数据完整，写入 `historystock`，按 `(code, date)` upsert。 |
| 模拟种子行情 | 每 1 秒 | 本地种子数据 | 仅在真实 `stock` 数据不足时用于演示异动监控。 |
| 历史 K 线后台同步 | 用户点击 historystock 的增量/全量按钮后启动 | 搜狐优先，东方财富辅助 | 串行同步，每只股票请求后延迟约 100ms，成功/失败写入 `history_sync_result`。 |

前端定时任务：

| 任务 | 频率/条件 | 行为 |
| --- | --- | --- |
| 监控交易时间检查 | 每 30 秒 | 非交易时间自动停止监控按钮状态。 |
| 监控来源刷新 | 每 60 秒 | 重新拉取已选热榜/龙虎榜来源并去重。 |
| 开盘啦异动直播 | 每 10 秒，15:00 后停止 | 前端直接请求开盘啦接口并渲染。 |
| DB Viewer stock 自动刷新 | 用户在 `/db` 的 stock 表头配置，支持 1-3600 秒 | 按所选新浪/东方财富调用 `/api/sync/stocks`。 |

## IntelliJ 调试

1. 用 IntelliJ 打开 `stockman-monitor` 目录。
2. 等 Gradle Sync 完成。
3. 打开 Gradle 工具窗口。
4. 运行 `server > Tasks > application > run` 启动服务端。
5. 运行 `web > Tasks > kotlin browser > jsBrowserDevelopmentRun` 启动前端。

服务端默认地址：

```text
http://localhost:8080
```

数据库可视化：

```text
http://localhost:8080/db
```

前端页面：

```text
http://localhost:8081
```

调试服务端代码时，也可以在 IntelliJ 新建 Gradle Run Configuration：

```text
Gradle project: stockman-monitor
Tasks: :server:run
```

调试前端时使用：

```text
Gradle project: stockman-monitor
Tasks: :web:jsBrowserDevelopmentRun
```

## 命令行编译

从 `stockman-monitor` 目录执行：

```bash
./gradlew :server:build
./gradlew :web:jsBrowserDevelopmentWebpack
```

一次性构建服务端和前端：

```bash
./gradlew :server:build :web:jsBrowserDevelopmentWebpack
```

## 本地运行

### 一键重启服务端和前端

从 `stockman-monitor` 目录执行：

```bash
./scripts/restart-local.sh
```

脚本会先关闭旧的 `screen` 会话和已经占用 `8080`、`8081` 的旧进程，然后后台启动服务端和前端开发服务器。

等价命令如下，便于在没有脚本权限时复制执行：

```bash
screen -S stockman-server -X quit >/dev/null 2>&1 || true; \
screen -S stockman-web -X quit >/dev/null 2>&1 || true; \
pids=$({ lsof -ti tcp:8080; lsof -ti tcp:8081; } 2>/dev/null | sort -u); \
if [ -n "$pids" ]; then kill $pids; sleep 2; fi; \
mkdir -p run-logs; \
./gradlew --no-daemon :server:installDist > run-logs/server-build.log 2>&1; \
screen -dmS stockman-server bash -lc 'cd /Users/haoshuaihui/AndroidProject/MyLearn/StockMan/stockman-monitor && server/build/install/server/bin/server > run-logs/server.log 2>&1'; \
screen -dmS stockman-web bash -lc 'cd /Users/haoshuaihui/AndroidProject/MyLearn/StockMan/stockman-monitor && ./gradlew --no-daemon -Dkotlin.daemon.jvm.options=-Xmx2g :web:jsBrowserDevelopmentRun > run-logs/web.log 2>&1'; \
echo "server: http://localhost:8080"; \
echo "db:     http://localhost:8080/db"; \
echo "web:    http://localhost:8081"; \
echo "logs:   run-logs/server-build.log run-logs/server.log run-logs/web.log"
```

查看启动日志：

```bash
tail -f run-logs/server.log
tail -f run-logs/web.log
```

如果端口进程没有正常退出，可以手动强杀：

```bash
pids=$({ lsof -ti tcp:8080; lsof -ti tcp:8081; } 2>/dev/null | sort -u); \
if [ -n "$pids" ]; then kill -9 $pids; fi
```

### 分别启动

终端 1，启动服务端：

```bash
./gradlew :server:run
```

终端 2，启动前端开发服务器：

```bash
./gradlew :web:jsBrowserDevelopmentRun
```

浏览器打开：

```text
http://localhost:8081
```

## 生产打包

服务端分发包：

```bash
./gradlew :server:installDist
```

生成目录：

```text
server/build/install/server
```

启动服务端：

```bash
server/build/install/server/bin/server
```

前端静态资源：

```bash
./gradlew :web:jsBrowserProductionWebpack
```

生成目录：

```text
web/build/dist/js/productionExecutable
```

前端生产部署时可以把该目录交给 Nginx、Caddy 或任意静态文件服务。当前前端默认连接 `localhost:8080/ws`，如果服务端域名变了，需要同步调整前端 WebSocket/API 地址。

## 常用接口

```text
GET  http://localhost:8080/api/snapshot
GET  http://localhost:8080/api/sync/status
GET  http://localhost:8080/api/sync/history/status
POST http://localhost:8080/api/sync/stocks?source=sina
POST http://localhost:8080/api/sync/stocks?source=eastmoney
POST http://localhost:8080/api/sync/history
GET  http://localhost:8080/api/history/688233?limit=210
GET  http://localhost:8080/api/db/tables
GET  http://localhost:8080/api/db/table/stock?limit=100
GET  http://localhost:8080/api/db/table/historystock?limit=100
POST http://localhost:8080/api/tick
```

手动制造异动：

```bash
curl -X POST http://localhost:8080/api/tick \
  -H 'Content-Type: application/json' \
  -d '{"code":"600519","price":1740.0,"chg":3.4}'
```

## 监控规则

规则来自原工程 `Injector.Tracker`：

- 上一帧等于涨停价，本帧跌破涨停价: 炸板
- 上一帧等于跌停价，本帧高于跌停价: 翘板
- 约 15 秒内涨幅增量大于等于 1%
- 约 15 到 60 秒内涨幅增量大于等于 2%
- 约 60 到 90 秒内涨幅增量大于等于 3%
- 约 90 到 180 秒内涨幅增量大于等于 4%
