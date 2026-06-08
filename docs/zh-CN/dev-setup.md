# 开发环境说明

## 预期工具

- JDK 17
- Maven 3.9+
- Node.js 20+

## 后端

在仓库根目录执行：

```bash
mvn -pl apps/server -am spring-boot:run
```

这个仓库会通过下面两个文件覆盖机器级 Maven 配置：

```text
.mvn/maven.config
.mvn/settings.xml
```

目的是强制 Maven 使用仓库内本地缓存，而不是机器上的全局仓库路径。

健康检查地址：

```text
http://localhost:8080/api/health
```

SQLite 数据库文件：

```text
./data/java-review-graph.db
```

仓库内 Maven 本地仓库：

```text
./.m2/repository
```

## 浏览器前端

在 `apps/web` 目录执行：

```bash
npm install
npm run dev
```

开发地址：

```text
http://localhost:5173
```

浏览器模式下，如果前端设置里的 API 地址留空，就会继续使用 Vite 的 `/api` 代理。

## 桌面端

在 `apps/desktop` 目录执行：

```bash
npm install
npm run dev
```

桌面端基于 Electron，复用 `apps/web` 的 React 界面，并通过 preload 持久化保存语言和后端 API 地址等设置。

桌面端默认连接地址：

```text
http://127.0.0.1:8080
```

## 当前阶段说明

- `apps/analyzer-jdt` 已经具备本地 AST 提取能力，但 binding 解析和更深层语义分析还没实现
- SQLite 已接入 project、snapshot、source_file、symbol、relation 和 symbol_change
- 浏览器端和桌面端都已经可以构建，桌面端支持中英文切换和设置面板
