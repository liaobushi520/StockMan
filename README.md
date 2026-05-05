# 股票超人（StockMan）

面向 A 股行情与复盘的 Android 应用，整合多路行情与资讯源，提供自选盯盘、板块与个股策略筛选、涨停复盘与异动提醒等能力。应用名为 **股票超人 5.0**（包名：`com.liaobusi.stockman5`）。

> 本软件仅用于技术分析与数据整理，不构成任何投资建议。市场有风险，决策需谨慎。

## 功能概览

### 首页与入口

- **盯盘 / 复盘**：大盘盯盘（`DPActivity`）、涨停复盘（`FPActivity`）
- **快捷链接**：韭研公社、财联社看盘等（内置 `WebViewActivity`）
- **市场分析**：统计分析入口（`AnalysisActivity`，首页图标）
- **初始化**：连点入口可拉取东方财富实时行情、板块、历史板块、板块成分、股东户数、历史 K 线等本地基础数据

### 自选与设置

- **自选股 / 自选板块**（`FollowListActivity`）：管理关注标的；支持排序（顺序、涨跌、分组色等）
- **设置**（`SettingActivity`）：应用选项；**数据库与设置备份 / 还原**（zip，还原后重启应用）
- **即将涨停**（`WillZTActivity`，工具栏菜单）：临近涨停标的辅助查看

### 选股与策略

首页按模块划分：

| 分类 | 说明 |
|------|------|
| **强势选股** | 板块强势（`BKStrategyActivity`）、均线强势（`Strategy4Activity`） |
| **经典策略** | 策略一（涨停洗盘）、策略二（涨停揉搓）、策略四（`Strategy3Activity`，界面文案为「策略四」） |
| **更多策略** | 超跌底部横盘、活跃度、涨停强势、底部起爆堆量（`Strategy6/5/7/9Activity`） |

另有 **策略一**（`Strategy1Activity`）等在 Manifest 中独立注册，与多页面策略体系配套使用。

### 数据与扩展能力

- **盘口异动 / 历史**（`YDHistoryActivity`）：异动资讯与相关展示（含热度排名等文案资源）
- **关联股票**（`RelatedStocksActivity`）
- **走势拟合（实验）**（`StockPairFitActivity`）：双股日 K 对数收益相关性与 OLS 等统计
- **拟合度排行**（`StockFitRankingActivity`）：以某标的为锚，在本地日 K 上扫描与其他标的的走势相似度（Pearson 等）
- **调试**（`DebugActivity`）：数据库表分页浏览与简单筛选（开发/排障用）

### 后台与提醒

- **前台服务**（`StockService`）：保持行情更新链路
- **异动追踪**：可配置轮询或基于内存行情映射的追踪逻辑；检测急拉、炸板、翘板等并触发系统通知（需通知权限）

### 安全与隐私

- 可选 **密码遮罩**：通过对比标的 `000601` 前收盘价与输入值解锁界面（`HomeActivity` 内逻辑）

## 技术栈

- **语言**：Kotlin
- **UI**：AndroidX、Material 组件、**ViewBinding**
- **异步**：Kotlin Coroutines（主线程 `Injector.scope`，IO 使用 `Dispatchers.IO`）
- **本地存储**：**Room**（股票、历史 K、板块、人气榜、龙虎榜、股东户数、自建板块、涨停复盘等实体，见 `db/`）
- **网络**：Retrofit + OkHttp（多数据源聚合，见 `StockRepo`、`api/`）
- **最低系统版本**：`minSdk 30`，`targetSdk` / `compileSdk` 34（以工程 `app/build.gradle` 为准）

## 构建与运行

```bash
# 编译
./gradlew build

# 单元测试
./gradlew test

# 安装 Debug 到已连接设备
./gradlew installDebug

# 清理后全量编译
./gradlew clean build
```

使用 **Android Studio** 打开工程根目录，选择 `app` 模块运行即可。

## 工程结构（简要）

| 路径 | 说明 |
|------|------|
| `app/src/main/java/.../Injector.kt` | 依赖注入：数据库、网络、缓存、`realTimeStockMap`、自动刷新与追踪任务等 |
| `app/src/main/java/.../repo/StockRepo.kt` | 行情、历史、榜单、板块等仓库层 |
| `app/src/main/java/.../StockService.kt` | 前台服务与后台刷新 |
| `app/src/main/java/.../db/` | Room 实体与 DAO |
| `app/schemas/` | Room 导出的数据库 schema 版本快照 |

## 数据源说明

应用会从多个公开接口获取行情与资讯（如东方财富、新浪、百度、腾讯等），具体 URL 与字段解析以 `StockRepo`、`api/` 为准。网络层开发配置可能包含宽松 SSL 设置，**切勿用于生产安全模型参考**。

## 许可证

若仓库未包含 `LICENSE` 文件，默认版权与许可以仓库所有者声明为准；开源前请自行补充许可证文本。
