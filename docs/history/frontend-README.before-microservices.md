# Lectus 用户端

独立的 React 19 + Vite npm 项目。当前入口为 `index.html` → `src/WebStorefront.jsx`，hash 路由位于 `src/web-route.js`。此次拆分目录与构建边界，页面和业务源码保持原样。

## 常用命令

在本目录运行：

```powershell
npm ci
npm run dev
npm test
npm run build
npm run preview
```

需要 Node.js 20.19+ 或 22.12+。开发地址为 `http://127.0.0.1:4206/`，保留原有局域网访问能力；静态预览为 `http://127.0.0.1:4206/lectus/`。开发和预览不能同时占用 4206 端口。

`vite.config.js` 导出 `vite.web.config.js`；直接执行 Vite 与 npm 命令使用同一套 Web 配置。仍兼容显式指定 `--config vite.web.config.js`。

## 源码与资源

- `src/WebStorefront.jsx`：当前 Web 用户端。
- `src/web-route.js`：hash 路由。
- `src/main.jsx`、`index.mobile-source.html`：早期移动端参考。
- `src/*.test.js`：现有业务与接口单元测试。
- `public/`：原样迁入的字体、图片、模型、图标。
- `dist-web/`：当前构建输出，生产路径前缀 `/lectus/`。

生产构建包含 `public/` 的全部静态文件。相较旧 Web 配置，不再跳过这些文件；发布目录应使用整个 `dist-web/`。后端、共享工具、私有配置不包含在前端目录或构建产物中。原有 `dist/` 仅保留旧产物，不作为当前发布目录。

## 开发代理与服务依赖

| 前端路径 | 开发服务目标 |
| --- | --- |
| `/api/member`、`/api/lectus-demo`、`/api/addresses` | 本机 `http://127.0.0.1:8081` |
| 其他 `/api` | 既有 `https://39.107.112.161/lectus/api` |
| `/api/lectus-local/config` | Vite 开发中间件，生成局域网扫码地址 |

代理规则保持原样。8081 会员服务由根目录 `scripts/start-member-local.ps1` 启动，仍引用本机项目外部的会员后端，尚未纳入本项目。根目录 `backend/` 是另一套 8080 API，不能直接替代会员服务。

静态部署仍需服务器将 `/lectus/api/` 转发到对应后端。`npm run preview` 不提供业务后端或局域网扫码配置。前端环境文件放在本目录；只有可公开的配置才使用 `VITE_` 前缀。

## 旧版 API 联调

```powershell
npm run dev:legacy
```

启动根目录 `backend/` 后，打开 `http://127.0.0.1:4200/index.mobile-source.html#account`。旧配置将 `/api` 转发至 `http://127.0.0.1:8080`。默认 Web 入口继续使用 4206。

根目录 `experiments/` 与 `work/` 中的历史实验资料不再属于前端 Web 根目录，不会由当前开发服务器自动公开；引用这些目录的实验页面需另行配置实验服务。
