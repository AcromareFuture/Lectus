# Lectus Java API · 第一版

Java 21 / Spring Boot 3.5 / Spring Security / JDBC / Flyway。

已实现邮箱密码注册、登录、退出、商品与规格库存、购物袋、地址、下单和取消。金额单位为人民币分，服务端重新计算价格。订单只包含 `PENDING_PAYMENT` 和 `CANCELLED`；没有伪造付款成功接口。

## 启动

需要 JDK 21+、Maven 3.9+。Windows 在本目录运行：

```powershell
.\run.ps1
.\run.ps1 -Test
.\run.ps1 -Package
```

标准 Maven 同样可用：`mvn test`、`mvn spring-boot:run`、`mvn verify`。

默认监听 `127.0.0.1:8080`。H2 文件数据库保存在本目录 `data/lectus.mv.db`，重启保留；不与自动化测试共享数据库。测试使用 H2 内存数据库，不修改本地账号或订单。

前端已独立到 `frontend/`。当前响应式 Web 商城从项目根目录运行 `./scripts/start-web-local.ps1`，打开 `http://127.0.0.1:4206/`；其会员服务和商品服务代理见 `frontend/README.md`，不全部指向本目录的 8080 服务。

若要验证本目录第一版 Java API 的旧账号页面，在 `frontend/` 运行 `npm run dev:legacy`，打开 `http://127.0.0.1:4200/index.mobile-source.html#account`。该配置将 `/api` 转发到本机 8080。用虚构邮箱即可测试，第一版不验证邮箱所有权。账号内有选购、购物袋、地址和订单；旧商城仍是原有本机演示，尚未整体迁移到后端。

## 数据与边界

- 初次启动导入前端现有 20 款商品；每个尺码/颜色组合初始库存20，均为演示值。
- 再次启动跳过已存在商品，不覆盖价格和库存。
- BCrypt 密码摘要；随机32字节会话令牌，数据库仅保存 SHA-256 摘要；默认有效期7天，退出立即失效。
- 账号页使用 sessionStorage 保存当前标签页的令牌；站点必须部署在 HTTPS 下。没有保存密码。
- 私有资源均按当前登录账号查询。其他账号的地址和订单返回404。
- 购物袋 `PUT` 是覆盖指定规格的数量，不是累加。前端点击加购前读取本账号购物袋。
- 下单使用事务、账号锁、按规格ID排序的库存锁，减少库存；失败整体回滚。取消幂等，最多补回一次库存。
- `Idempotency-Key` 在同一账号下唯一，相同请求重试返回原订单，复用到不同请求返回409。
- 下单保存价格、名称、规格、收货地址快照；之后修改地址或商品不会改写已有订单。
- 购物袋不会保留库存。待付款订单保留库存，当前需要主动取消释放；未实现自动过期取消。
- 登录/注册有每个来源IP 15分钟20次的单实例限流。反向代理下需由可信代理传递客户端IP或在网关补充限流；多实例需共享限流。
- 尚未实现邮箱验证、找回密码、真实支付/退款、管理员上架/库存编辑、物流、优惠券、社区和鉴别服务。适合本地开发与验收，不宣称已达到正式交易上线条件。

## 配置

| 环境变量 | 默认/说明 |
| --- | --- |
| `PORT` | `8080` |
| `SERVER_ADDRESS` | `127.0.0.1` |
| `DB_URL` | 本地H2文件连接，见application.yml |
| `DB_USER` / `DB_PASSWORD` | 通过环境提供数据库凭证 |
| `CORS_ORIGINS` | 默认允许localhost/127.0.0.1开发端口，不支持任意来源 |
| `SESSION_HOURS` | `168` |
| `SEED_CATALOG` | 本地默认true；Docker默认false |
| `VITE_API_BASE_URL` | 前端构建变量，可选，例如 `/lectus/api` |

PostgreSQL 驱动和 Flyway 支持已配置，可通过 `DB_URL=jdbc:postgresql://127.0.0.1:5432/lectus` 切换。数据库用户、口令通过环境提供；本次测试使用H2，尚未在PostgreSQL上验收。

## 部署准备

`mvn verify` 产出 `target/lectus-api-0.1.0.jar`，用 `java -jar` 运行。Dockerfile也可构建，默认不导入样例商品；使用时提供外部数据库。没有在此次工作中修改线上服务器。

Nginx 将同源 `/lectus/api/` 转发到只监听本机的Java服务，保持原有 `/lectus/` 静态路径：

```nginx
location ^~ /lectus/api/ {
    proxy_pass http://127.0.0.1:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 64k;
    proxy_read_timeout 30s;
}
```

上线前选定并实测生产数据库、邮件/支付服务以及自动关单策略，再迁移旧商城入口。API清单及请求例子见 [API.md](API.md)。


## 本轮：服务器中间件 + 本地代码

MySQL 8 已通过 Flyway V1–V3 初始化，并导入 250 SPU / 2,732 SKU；Redis 和 MinIO 复用服务器实例。切换到 MySQL 必须使用 `mysql` profile，它会选取 MySQL 交易迁移及共享的来源目录迁移，不要直接把 H2 的默认迁移套到 MySQL。

项目根目录先运行 `python scripts/remote_dev.py --bootstrap` 保持 SSH 隧道，再运行 `./scripts/start-remote-backend.ps1`。专用连接信息从忽略提交的 `.runtime/remote.json` 读入进程环境；root 密码只交互输入，不保存在配置中。开发端口只绑定 127.0.0.1，不应公开 MySQL / Redis / MinIO 的管理端口。

已在真实远程 MySQL 上验证注册、登录、读取当前用户、地址保存与读取、空购物袋/订单、退出令牌失效。交易扣库存与取消等集成测试使用 H2；真实来源商品没有自有定价与库存，保持不参与下单。

Redis 专用 ACL 只允许 `lectus:*`；服务器当前无 ACL 文件，重启后需要重新 bootstrap 恢复该 ACL。缓存不可用不影响数据库回源。MinIO 专用上传账号只可访问 lectus-assets；匿名仅可读 catalog/ 和 models/，不可列举/写入。未修改其他业务的 Redis 全局策略或应用部署。
