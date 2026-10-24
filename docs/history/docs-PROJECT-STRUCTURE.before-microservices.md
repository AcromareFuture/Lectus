# 前端目录拆分说明

2026-10-22：将根目录混放的前端独立为 `frontend/`，保留 `backend/` 与共享工具。未改变业务数据、接口契约或部署服务器。

| 原路径 | 当前路径 |
| --- | --- |
| `src/` | `frontend/src/` |
| `public/` | `frontend/public/` |
| `package.json`、`package-lock.json` | `frontend/` 下同名文件 |
| `index.html`、`index.mobile-source.html` | `frontend/` 下同名文件 |
| `vite.web.config.js` | `frontend/vite.web.config.js` |
| 原 `vite.config.js` | `frontend/vite.legacy.config.js` |
| 新默认 Vite 配置 | `frontend/vite.config.js`，导出 Web 配置 |
| `dist/`、`dist-web/` | `frontend/` 下同名目录 |
| `node_modules/` | `frontend/node_modules/`，可用 npm ci 重建 |
| 原 `README.md` | 原文保存在 `docs/history/README.before-frontend-split.md` |

源码与静态资源保持原样移动。组件内相对导入及浏览器 URL 无需随磁盘目录变化而加上 `/frontend`。开发 URL 仍为根路径，生产 URL 仍以 `/lectus/` 开头。

默认 npm 命令统一使用当前 Web 配置；生产构建会把 `public/` 的字体、图片与模型一并复制到 `dist-web/`。启动脚本切换到 `frontend/` 工作目录；图标生成脚本写入 `frontend/public/`。

`scripts/`、`resources/`、`experiments/`、`previews/`、`work/`、`.runtime/` 保留在根目录，维持资源处理、数据导入与本地配置的路径。实验页面不作为用户端生产构建入口。

后续商家工作台和平台管理端可作为与 `frontend/` 并列的独立应用，分别安装和构建。此次未创建空业务项目，也未引入微前端框架。

历史部署、验证、素材与实验文档保留当时路径，查阅时按此表映射。此次未执行远程导入、数据库迁移或部署。

## 本次验证

- `frontend/` 内 `npm test`：31 项通过。
- `frontend/` 内 `npm run build`：通过，输出 `dist-web/`。
- 原 `src/` 与 `public/` 共 167 个文件：移动前后 SHA-256 一致。
- `public/` 共 114 个文件：全部复制到生产构建，哈希一致。
- 开发服务：HTML、React 模块、字体 CSS/TTF、图标、旧 HTML 入口和本地扫码配置 HTTP 检查通过。
- 生产静态预览：`/lectus/` 下首页、构建 JS/CSS、字体与图标 HTTP 检查通过。
- 根目录启动脚本语法、前端 package/lock 名称一致性与旧前端路径清理检查通过。

保留原有 model-viewer 大分块提示（约 1.04 MB，按需加载）；未进行体积优化。此次未运行依赖真实账户的完整端到端业务流程，未重新验收后端或 PostgreSQL，未发布线上版本。验证使用的临时开发与预览进程已停止。
