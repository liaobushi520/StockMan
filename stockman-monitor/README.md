# StockMan Compose Monitor

从原 Android 项目的异动监控逻辑拆出来的本地 Web 测试项目。

- `server`: Ktor + SQLite 服务，负责同步行情、落库、监控异动、WebSocket 推送。
- `web`: Kotlin Compose for Web 前端，负责行情表、异动列表和浏览器通知。
- 数据库: `server/data/stockman-monitor.db`

## 环境要求

- JDK 17
- IntelliJ IDEA 2024+ 或命令行终端
- 网络可访问行情源

本项目使用 Gradle Wrapper。因为 wrapper 在父 Android 项目根目录，所以命令都从 `stockman-monitor` 目录里用 `../gradlew` 执行。

## 数据同步策略

同步入口是 `StockSync`，内部使用策略模式：

- `StockSyncStrategy`: 单个行情源策略接口。
- `FallbackStockSyncStrategy`: 按顺序尝试策略，前一个失败或数据不完整时切到下一个。
- `PagedRequestConfig`: 配置页码类接口的 `baseUrl`、`path`、`headers`、固定查询参数、`page`、`pageSize` 参数名。
- `RangeRequestConfig`: 配置范围类接口的 `baseUrl`、`path`、`headers`、固定查询参数、`begin`、`end` 参数名。

当前顺序：

```text
EastMoney -> SSE -> Baidu+Caixin
```

东方财富必须返回超过 5000 只才算完整；如果最终返回数据不完整，本次 `stock` 同步会失败并保留旧数据，不执行清空写入。

网络层使用 OkHttp + Retrofit。Retrofit API 使用 `@Url`、`@HeaderMap`、`@QueryMap` 组合请求，不在策略里手写整段 URL；响应解析使用 Gson 的 `JsonObject` / `JsonArray`，便于兼容不同数据源返回结构。

手动同步当天： 

```bash
curl -X POST http://localhost:8080/api/sync/stocks
```

手动同步当天历史日 K：

```bash
curl -X POST "http://localhost:8080/api/sync/history"
```

手动回补历史日 K：

```bash
curl -X POST "http://localhost:8080/api/sync/history?limit=120"
```

小批量测试可以加 `stockLimit`：

```bash
curl -X POST "http://localhost:8080/api/sync/history?limit=20&stockLimit=50"
```

单只或多只重试：

```bash
curl -X POST "http://localhost:8080/api/sync/history?limit=120&code=302132"
curl -X POST "http://localhost:8080/api/sync/history?limit=120&codes=302132,920156"
```

同步状态：

```bash
curl http://localhost:8080/api/sync/status
curl http://localhost:8080/api/sync/history/status
```

股票池同步会在交易日 `09:25`、`12:10`、`15:10` 触发，使用 `clear + insert all` 重建 `stock`。数据不完整时不会清表写入。`historystock` 在交易日 `15:10` 后自动触发，只拉最近 1 条日 K，按 `(code, date)` 做 `insert or update`。

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
../gradlew :server:build
../gradlew :web:jsBrowserDevelopmentWebpack
```

一次性构建服务端和前端：

```bash
../gradlew :server:build :web:jsBrowserDevelopmentWebpack
```

## 本地运行

终端 1，启动服务端：

```bash
../gradlew :server:run
```

终端 2，启动前端开发服务器：

```bash
../gradlew :web:jsBrowserDevelopmentRun
```

浏览器打开：

```text
http://localhost:8081
```

## 生产打包

服务端分发包：

```bash
../gradlew :server:installDist
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
../gradlew :web:jsBrowserProductionWebpack
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
POST http://localhost:8080/api/sync/stocks
POST http://localhost:8080/api/sync/history
POST http://localhost:8080/api/sync/history?limit=120
GET  http://localhost:8080/api/history/688233?limit=120
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
