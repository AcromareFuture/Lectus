# 项目结构与目录边界

## 当前结构

```text
lectus/
├── README.md
├── .gitignore
├── frontend/
│   ├── package.json                  # npm workspaces 与统一命令
│   ├── package-lock.json             # 三端统一锁文件
│   ├── README.md
│   ├── apps/
│   │   ├── storefront/               # 现有 React 用户端
│   │   │   ├── src/
│   │   │   ├── public/
│   │   │   ├── index.html
│   │   │   ├── vite.config.js
│   │   │   ├── vite.web.config.js
│   │   │   └── package.json
│   │   ├── merchant/                 # Vue 商家端交互预览（内存示例数据）
│   │   │   ├── src/
│   │   │   │   ├── api/
│   │   │   │   ├── router/
│   │   │   │   ├── views/
│   │   │   │   ├── layouts/
│   │   │   │   ├── components/
│   │   │   │   └── stores/
│   │   │   ├── index.html
│   │   │   ├── vite.config.ts
│   │   │   ├── tsconfig.json
│   │   │   └── package.json
│   │   └── platform/                 # Vue 平台运营端，七页业务交互预览
│   └── packages/                     # 共享包约定，尚无实际共享包
├── backend/
│   ├── common/lectus-common/
│   ├── gateway/
│   ├── services/
│   │   ├── identity-service/
│   │   ├── member-service/
│   │   ├── catalog-service/
│   │   └── trade-service/
│   ├── deploy/                       # PostgreSQL、Redis、服务 Compose
│   ├── scripts/                      # 后端启停与联调
│   ├── tools/catalog/                # 商品导入与模型绑定
│   ├── docs/                         # 架构、API、迁移
│   └── legacy/                       # 停用历史工具
├── docs/
│   ├── product/OVERVIEW.md
│   ├── assets/CREDITS.md
│   ├── data/IMPORT-ASSESSMENT.md
│   └── history/                      # 历史文档，保留原文
├── tools/
│   ├── dev/                          # 前端及兼容启动入口
│   └── assets/                       # 图标、模型处理脚本
├── assets/source/                    # 素材来源与模型生成说明
├── archive/
│   ├── experiments/                 # 旧实验 HTML
│   └── previews/                    # 历史截图
├── .local/
│   ├── work/                        # 中间数据、模型试验、旧工作资料
│   └── archive/                     # 旧锁文件等本地备份
└── .runtime/                        # 原有工具环境和私有配置
```

## 归属规则

新页面放入对应应用；应用自己拥有路由、状态和构建入口。官方自营店经营后续复用商家端；平台运营后台负责平台管理。不会为旗舰店另起第四个前端。

后端源码、后端数据库迁移、后端部署和后端工具全部放入 `backend/`。根 `tools/` 仅保留跨项目开发入口与素材处理，不混入后端业务。

根目录只保留总 README、仓库配置和用途明确的文件夹。产品及调研说明放 `docs/`；素材原件放 `assets/`；实验和旧预览放 `archive/`；本地中间结果放 `.local/work/`。

`.runtime/` 内已有 Python 环境等可能依赖绝对路径，原位保留；`.local/` 与 `.runtime/` 均忽略提交。已有 `backend/data/` 数据库保留，构建产物在各应用与各 Maven 模块下生成，不混进源码。

## 本次迁移映射

| 原位置 | 现位置 |
| --- | --- |
| `frontend/src`、`public`、入口与 Vite 配置 | `frontend/apps/storefront/` 下对应位置 |
| 用户端 `frontend/package.json` | `frontend/apps/storefront/package.json` |
| 原 `frontend/package-lock.json` | `.local/archive/frontend-package-lock.before-workspaces.json` |
| 新统一前端 manifest 与 lock | `frontend/package.json`、`frontend/package-lock.json` |
| `scripts/` 素材脚本 | `tools/assets/` |
| `scripts/` 启动脚本 | `tools/dev/` |
| `resources/` | `assets/source/` |
| `experiments/`、`previews/` | `archive/experiments/`、`archive/previews/` |
| `work/` | `.local/work/` |
| `PRODUCT.md` | `docs/product/OVERVIEW.md` |
| `CREDITS.md` | `docs/assets/CREDITS.md` |
| `DATA-IMPORT-ASSESSMENT.md` | `docs/data/IMPORT-ASSESSMENT.md` |
| `DEPLOYMENT.md`、`VALIDATION.md` | `docs/history/*.before-workspaces.md` |
| `backend/API.md` | `backend/docs/API.md` |
| `backend/*-run.log` | `backend/.runtime/history/` |

历史文档和 `.local/work/` 内旧脚本可能保留旧路径、旧服务器地址，仅作追溯，不应直接按旧命令执行。当前使用方式以根 README、前端 README 和后端 README 为准。

本次仅重构工程目录与前端工作区；商家权限、租户隔离、结算及既有业务兼容问题需另行实现、验收。
