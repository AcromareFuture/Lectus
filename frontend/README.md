# 前端工作区

三个应用按业务入口划分，独立构建、独立发布；共用 npm workspaces 的依赖安装和锁文件。

| 应用 | 包名 | 使用者 | 技术 | 开发端口 | 构建目录 |
| --- | --- | --- | --- | --- | --- |
| `apps/storefront` | `@lectus/storefront` | 消费者 | 现有 React + Vite | 4206 | `apps/storefront/dist-web` |
| `apps/merchant` | `@lectus/merchant` | 入驻商家、官方自营店员工 | Vue + TypeScript + Vite | 4207 | `apps/merchant/dist` |
| `apps/platform` | `@lectus/platform` | 平台运营人员 | Vue + TypeScript + Vite | 4208 | `apps/platform/dist` |

商家端已提供概览、订单、商品、营销、对账、设置页面；平台端已提供概览、商家、商品审核、订单、售后、结算、设置页面。商家和平台工作台已接入 `/api/mall/workspace/**`，刷新后通过后端快照恢复数据；用户商城和订单页已接入 `/api/mall/public/**`、`/api/mall/orders/**`。当前本地环境使用测试账号和 H2 文件库，支付、退款和物流均为本地模拟流程。目录划分不代表生产环境已完成租户隔离或真实资金结算。

## 统一命令

全部命令在 `frontend/` 执行：

```powershell
npm ci
npm run dev:storefront
npm run dev:merchant
npm run dev:platform
npm test
npm run typecheck
npm run build
```

三个 dev 命令需分别运行在不同终端。`build:storefront`、`build:merchant`、`build:platform` 可单独构建。`preview`、`preview:merchant`、`preview:platform` 用于静态产物预览；预览端口以命令输出为准。

给单个应用安装依赖时，在本目录执行，例如 `npm install 包名 --workspace=@lectus/merchant`。提交根 `package-lock.json`；应用目录不维护单独锁文件。`npm ci` 会安装全部应用依赖。

## 模块边界

- 应用自己的页面、路由、状态、API 适配保留在自己的 `src/`。
- 不通过相对路径导入另一个应用的源码，不把三个应用混进同一套路由。
- `packages/` 记录共享约定，等有真实复用再建带 `package.json` 的包，并将 `packages/*` 加入根 workspaces。优先共享接口类型、纯工具和设计变量；React 与 Vue 组件各自维护。
- 当前沿用用户端原有文件布局；商家和平台预留 `api`、`router`、`views`、`layouts`、`components`、`stores` 边界，后续随真实功能填充。
- 浏览器隐藏菜单不能实现数据隔离。商家权限与数据范围必须由后端验证，后续按业务实现。

## 网关与发布

开发 `/api` 默认指向 `http://127.0.0.1:8080`，通过 `LECTUS_API_ORIGIN` 可调整。三端均不携带数据库连接凭据。

用户端保留 `/lectus/` 生产前缀，发布整个 `apps/storefront/dist-web/`，并由部署代理将 `/lectus/api/` 转发到网关 `/api/`。商家与平台当前使用相对静态资源路径，域名、入口路径和生产 API 转发随部署方案配置。三个产物分别部署，不相互覆盖。

原始素材、历史实验、本地工具环境均在本工作区之外，不会随正常前端构建发布。各端说明见各自 README。
