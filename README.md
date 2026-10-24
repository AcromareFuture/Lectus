# Lectus｜莱择

国内电商项目：用户商城、商家工作台、平台运营后台三个独立前端，共用后端微服务。现有用户端使用 React；商家端与平台端使用 Vue + TypeScript。商城核心链路已完成三端联调：商家商品保存/提审、平台审核、用户下单与本地模拟付款、商家发货、用户收货、平台售后处理。

## 目录

```text
lectus/
├── frontend/                 # npm workspaces，统一依赖锁与命令
│   ├── apps/
│   │   ├── storefront/       # 用户商城：现有 React 工程
│   │   ├── merchant/         # 商家工作台：Vue + TypeScript
│   │   └── platform/         # 平台运营后台：Vue + TypeScript
│   ├── packages/             # 共享包约定，按实际复用需要建设
│   ├── package.json
│   └── package-lock.json
├── backend/                  # 全部后端服务、迁移、部署与后端工具
├── docs/                     # 产品、数据、素材说明与历史文档
├── tools/                    # 开发启动与素材处理工具
├── assets/source/            # 原始素材及生成说明
├── archive/                  # 旧实验页面和预览资料
├── .local/                   # 本地中间产物与旧文件归档，忽略提交
└── .runtime/                 # 已有本地工具环境及私有配置，忽略提交
```

## 前端启动

需要 Node.js 20.19+（20.x）或 22.13+，npm 10+。在 `frontend/` 统一安装依赖，不在三个应用中分别生成锁文件。

```powershell
cd frontend
npm ci
npm run dev:storefront       # 用户端，http://127.0.0.1:4206/
# 另外两个终端同样进入 frontend/ 后执行：
npm run dev:merchant         # 商家端，http://127.0.0.1:4207/
npm run dev:platform         # 平台端，http://127.0.0.1:4208/
```

`npm run dev` 默认启动用户端。从项目根目录也可执行 `./tools/dev/start-web-local.ps1 -App merchant`，`-App` 支持三端名称。

```powershell
# 在 frontend/ 下
npm test                    # 现有测试
npm run typecheck           # Vue 应用类型检查
npm run build               # 构建三个应用
npm run build:storefront    # 也可单独构建 merchant / platform
```

各端独立发布：用户端产物为 `frontend/apps/storefront/dist-web/`；商家与平台产物各在其应用的 `dist/` 下。用户端默认生产构建前缀为 `/lectus/`，部署到域名根路径时，在 `frontend/` 执行 `npm run build --workspace=@lectus/storefront -- --base=/`。开发环境的 `/api` 默认代理本机 8080 网关，可设置 `LECTUS_API_ORIGIN`。

## 后端启动

```powershell
cd backend
./run.ps1 -Package
./scripts/start-local.ps1
# 停止本次启动器管理的服务
./scripts/stop-local.ps1
```

网关 8080，会员 8081，认证 8082，商品目录 8083，交易 8084。默认数据库为 PostgreSQL；本地启动器显式使用隔离的 H2 开发库。详见 [后端说明](backend/README.md)。

## 三端联调账号

以下为显式 `local` 开发配置的初始账号，不用于生产。生产凭据由独立部署环境管理，不提交到仓库。

平台后台：`platform / Lectus@2026`；官方商家：`merchant / Lectus@2026`；云野商家：`yunye / Lectus@2026`。三个前端分别访问 `4206`、`4207`、`4208`。本地联调中的付款、退款和物流只改变本地订单状态并记录测试运单，不会产生真实资金或快递操作。

## 文档入口

- [完整目录边界与迁移映射](docs/PROJECT-STRUCTURE.md)
- [前端工作区说明](frontend/README.md)
- [文档索引](docs/README.md)
- [开发与素材工具](tools/README.md)
- [技术建设路线](docs/product/TECHNOLOGY-ROADMAP.md)
- [性能约束与验收](docs/product/PERFORMANCE.md)

仓库不包含数据库、用户上传、运行配置、依赖目录或构建产物；包含账号、订单或收货信息的线上验收截图也仅在本地保留。部署时需独立提供环境变量和持久化数据，不能以源码仓库替代数据备份。

历史记录位于 `docs/history/`，不代表当前部署状态。旧本地数据库和 `.runtime/` 工具环境保留；临时资料集中在 `.local/`，不作为生产源码。
