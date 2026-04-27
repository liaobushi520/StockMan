# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

StockMan is an Android application for Chinese stock market analysis and tracking. The app provides real-time stock data, market analysis, trading strategies, and unusual movement detection through notifications.

## Build Commands

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Installing Debug APK to Device
```bash
./gradlew installDebug
```

### Clean Build
```bash
./gradlew clean build
```

## Architecture

### Core Components

**Dependency Injection (Injector.kt)**
- Singleton object that initializes and manages app-wide dependencies
- Initializes Room database, Retrofit API service, SharedPreferences, and OkHttpClient
- Manages real-time stock data map (`realTimeStockMap`) for tracking current prices
- Handles lifecycle callbacks to trigger data fetching when specific activities are created
- Manages background jobs for auto-refresh and stock tracking

**Database Layer (Room)**
- Database file: `AppDatabase` in `db/beans.kt` and `db/dao.kt`
- Main entities: `Stock`, `HistoryStock`, `BK` (板块/sectors), `HistoryBK`, `PopularityRank`, `DragonTigerRank`, `GDRS` (shareholder data), `DIYBk` (custom sectors), `ZTReplayBean` (limit-up replay data), `ExpectHot`, `UnusualActionHistory`
- DAO interfaces provide query methods for each entity
- Database version managed via Room migrations

**Repository Pattern (StockRepo.kt)**
- Central data repository handling all stock-related network requests and database operations
- Fetches real-time data from multiple sources (East Money, Sina, Baidu, Tencent)
- Manages historical data synchronization
- Handles popularity rankings from multiple platforms (DFCF, THS, TGB, DZH, CLS)
- Coordinates Dragon Tiger List (龙虎榜) data fetching

**Foreground Service (StockService.kt)**
- Runs as a foreground service to maintain real-time data updates
- Uses notification channel "股票超人" for service notification

**Real-time Stock Tracking System**
- Two tracking modes controlled by `trackerType` flag:
  - Mode 1: Active polling-based tracking (uses `tracking()` function)
  - Mode 2: Map-based tracking (monitors `realTimeStockMap` changes)
- Tracker class monitors stock price changes over time windows (10s, 30s, configurable)
- Detects unusual movements: rapid price increases, board breaks (炸板), and turnarounds (翘板)
- Sends Android notifications for detected unusual activity
- Can focus on yesterday's limit-up stocks or popularity-ranked stocks based on settings

### Application Structure

**Entry Point**
- Main launcher activity: `HomeActivity`
- Application class: `App.kt` - calls `Injector.inject()` on startup
- Password protection using stock price (000601's previous close price)

**Strategy Activities**
- Multiple strategy-based analysis activities: `Strategy1Activity` through `Strategy9Activity`
- `BKStrategyActivity`: Sector-based strategy analysis
- Each strategy represents different stock screening/analysis approaches

**Key Features**
- **FPActivity**: "涨停复盘" (Limit-up replay) analysis
- **DPActivity**: "大盘分析" (Market index analysis)
- **AnalysisActivity**: Market statistics and analysis
- **FollowListActivity**: Tracked stocks/sectors management
- **WillZTActivity**: Stocks approaching limit-up

**API Integration (api/)**
- `StockService.kt`: Retrofit service interface for stock data APIs
- `client.kt`: OkHttpClient configuration with custom SSL settings (allows all certificates for development)
- `beans.kt`: Data classes for API responses

**Data Sources**
- Multiple real-time data sources with fallback support
- Configurable data source selection via SharedPreferences
- East Money (东方财富), Tonghuashun (同花顺), Taoguba (淘股吧), Dazhihui (大智慧), Cailianshe (财联社)

## Key Domain Concepts

**Stock Market Terms**
- ZT (涨停): Limit-up - maximum allowed price increase (10% main board, 20% ChiNext/STAR)
- DT (跌停): Limit-down - maximum allowed price decrease
- 炸板 (Zha Ban): A stock hits limit-up then falls back
- 翘板 (Qiao Ban): A stock hits limit-down then recovers
- 连板 (Lian Ban): Consecutive limit-up days
- 龙虎榜 (Dragon Tiger List): Daily trading activity leaderboard

**Board Types**
- Main Board: Codes starting with 600, 601, 603, 605 (Shanghai), 000, 002, 001, 003 (Shenzhen)
- ChiNext (创业板): Codes starting with 300, 301
- STAR Market (科创板): Codes starting with 688, 689
- Beijing Stock Exchange: Codes starting with 82, 83, 87, 88, 43, 92
- ST stocks: Special treatment stocks (prefix ST or *)

**Data Refresh Strategy**
- BK stocks refresh: Every 12 hours
- GDRS (shareholder data) refresh: Every 5 days
- Real-time data: During trading hours with configurable intervals
- Popularity rankings: Every 10 minutes when auto-refresh enabled

## Development Notes

**Database Schema Location**
- Room schema files should be exported to: `app/schemas/`
- Current schema version is managed in `AppDatabase` class

**ViewBinding**
- Project uses ViewBinding (enabled in app/build.gradle)
- Activity bindings follow pattern: `ActivityNameBinding`

**Coroutines Usage**
- Main scope managed by `Injector.scope` (MainScope)
- IO operations use `Dispatchers.IO`
- UI updates use `Dispatchers.Main`

**Color Conventions**
- Red: Price increase (defined in App.kt as `STOCK_RED`)
- Green: Price decrease (defined in App.kt as `STOCK_GREEN`)
- Color constants include alpha variants for backgrounds

**Deep Linking**
- Opens East Money app: `dfcf18://stock?market={market}&code={code}`
- Opens sector details: `dfcft://stock?market={market}&code={code}`
- Opens Dragon Tiger List: Custom scheme to Tonghuashun app

**SharedPreferences Keys**
- `auto_refresh`: Auto-refresh enabled state
- `fetch_bk_stocks_time`: Last BK stocks fetch timestamp
- `fetch_gdrs_time`: Last GDRS fetch timestamp
- `diy_bk_code`: Counter for custom sector codes (starts at 100000)

## Network Configuration

**SSL Certificate Handling**
- Custom SSL configuration in `api/client.kt` accepts all certificates (development only)
- Uses Apache HttpClient's `ALLOW_ALL_HOSTNAME_VERIFIER`

**API Timeouts**
- Connect timeout: 10 seconds
- Read timeout: 10 seconds

**Cleartext Traffic**
- Enabled in AndroidManifest.xml for HTTP connections