# 线上部署记录

- 访问地址：https://39.107.112.161/lectus/
- 发布日期：2026-10-19（北京时间）
- 发布版本：20261019T083722Z
- 前端当前链接：/opt/lectus/current → /opt/lectus/releases/20261019T083722Z/web
- 后端当前链接：/opt/lectus/api-current → /opt/lectus/releases/20261019T083722Z/api
- 后端：Java 21 / Spring Boot，systemd 服务 lectus-api，127.0.0.1:18085；Nginx 将 /lectus/api/ 代理到该服务。
- 中间件：服务器本机 MySQL、Redis、MinIO。运行不依赖开发电脑和 SSH 隧道。
- 上一版前端：/opt/lectus/releases/20261017T034725Z
- Nginx 配置与服务备份：/opt/lectus/deploy-backups/20261019T083722Z
- 上传包 SHA256：3eae9afd7be2feb45aa71205b9ebd7c4eb9d4c55c023d8bb7bbecb3f7650bcfc

首页增加“3D 好物体验”入口，Kobe 与 Samba 保留 AI 生成预览说明。模型文件采用 SHA256 地址，Nginx 支持 Range 206 与一年 immutable 缓存；正式商品库配色绑定保持独立。模型只在打开对应预览时加载。

## 验证

前端 17 项测试及生产构建通过；后端 18 项集成测试及打包通过。公网 HTTPS 证书验证通过，首页、脚本、样式、250 款商品目录、详情、图片和两个完整模型均可访问；模型 SHA256 与本地产物一致，Range 206 已验证。既有服务器根路径首页哈希保持不变。

## 手机访问

- 首页：https://39.107.112.161/lectus/#home
- Kobe：https://39.107.112.161/lectus/#product?id=wp-kobe&preview3d=meshy
- Samba：https://39.107.112.161/lectus/#product?id=wp-samba&preview3d=meshy

首次模型下载约 6–7 MiB；公网校验期间各约 16–17 秒，仅代表当时测试网络。手机实际表现待用户验收。当前支持外观旋转与缩放，尚不支持上脚试穿。

服务设置为开机启动。服务器环境配置存放于 /opt/lectus/config/api.env，仅 root 可读；未打包入前端或发布文件。
