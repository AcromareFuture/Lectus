# 用户商城

`@lectus/storefront` 是前端工作区内的 React 19 + Vite 应用。原用户端源码与静态资源整体迁入，入口仍是 `index.html` → `src/WebStorefront.jsx`，hash 路由仍在 `src/web-route.js`。

## 命令

在 `frontend/` 统一执行：

```powershell
npm ci
npm run dev:storefront
npm test
npm run build:storefront
npm run preview
```

开发地址 `http://127.0.0.1:4206/`，保留局域网访问；生产预览 `http://127.0.0.1:4206/lectus/`。开发和预览不能同时占用 4206。不要在本应用单独维护锁文件。

`vite.config.js` 导出 `vite.web.config.js`，默认和显式指定 Web 配置的行为一致。构建输出 `dist-web/`，生产路径前缀 `/lectus/`，发布时包含整个输出目录及 `public/` 静态文件。`dist/` 仅保留早期产物。

## 目录

- `src/`：现有页面、业务模块、样式与单元测试。
- `public/`：字体、图片、模型、图标。
- `src/main.jsx`、`index.mobile-source.html`：早期移动端参考。

共享工具、后端、私有配置与实验资料均位于项目根目录的对应分类文件夹内，不属于本应用 Web 根目录。

## API 与旧版入口

`/api` 默认代理本机 8080 网关，可设置 `LECTUS_API_ORIGIN`。后端启动见 [后端说明](../../../backend/README.md)。生产部署需要将 `/lectus/api/` 转发至网关 `/api/`，静态 preview 不提供后端代理。

在 `frontend/` 执行 `npm run dev:legacy`，访问 `http://127.0.0.1:4200/index.mobile-source.html#account` 可使用早期入口；旧配置将 `/api` 改写至 `/api/legacy`，与 Web 会员账号、地址分开。
