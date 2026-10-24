# Lectus 后端微服务

统一 Maven reactor，Java 21；网关和每个业务服务均可独立打包、运行与部署，源码和后端工具全部位于 `backend/`。默认数据库是 PostgreSQL；`local` 是显式选择的 H2 开发配置，不用于生产。

```text
backend/
├── pom.xml                      # Maven 聚合工程
├── common/lectus-common/         # DTO、错误处理、Servlet 鉴权与令牌摘要工具
├── gateway/                     # Spring Cloud Gateway，8080
├── services/
│   ├── identity-service/        # 旧交易账号、登录会话，8082
│   ├── catalog-service/         # 导入商品目录、图片/模型读取、Redis，8083
│   ├── trade-service/           # 原型商品、库存、购物车、地址、订单，8084
│   └── member-service/          # Web 会员及原有兼容业务，8081
├── deploy/                      # PostgreSQL 17、Redis、服务 Compose
├── scripts/                     # 启停与隔离进程联调
├── tools/catalog/               # PostgreSQL 商品导入、模型绑定
├── docs/                        # 架构、路由、迁移边界
└── legacy/tools/                # 已停用的远程 MySQL 历史工具
```

## 本地启动

需要 JDK 21、Maven 3.9+。`run.ps1` 支持 PATH 中的 Maven，或 `$USERPROFILE/.jdks/apache-maven-3.9.9`；JDK 支持 `JAVA_HOME` 和 `$USERPROFILE/.jdks/jdk21`。

```powershell
# 在 backend/ 下
./run.ps1 -Package             # 全工程 verify，包含测试与可执行 jar
./scripts/start-local.ps1      # 启动 5 个服务，各用独立 H2 文件
# 另一个终端在 frontend/ 下运行 npm run dev:storefront
./scripts/stop-local.ps1       # 仅停止此启动器登记的本地进程树

# 单服务前台运行（其他服务另开终端）
./run.ps1 -Service identity-service -Profile local
./run.ps1 -Service gateway -Profile local
./run.ps1 -Test

# 随机端口、内存数据库，结束自动停止本次子进程
python ./scripts/smoke-local.py
```

网关：`http://127.0.0.1:8080/api/health`。三端前端分别为 `4206`（用户）、`4207`（商家）、`4208`（平台）。商城联调数据由 member-service 的 local 配置初始化，工作台接口位于 `/api/mall/workspace/**`，用户商城接口位于 `/api/mall/**`。服务工作目录为 backend 时，H2 位于 `backend/data/`，启动日志位于 `backend/.runtime/local/`。旧 `backend/data/lectus*` 与旧 `backend/target/` 保留，不作为新服务数据和运行产物使用。

启动器在最多 120 秒的就绪检查窗口内等待五个服务的 readiness，通过后才报告成功；失败时停止本次登记的进程。已在 Windows PowerShell 下验证启动、网关健康和完整停止流程。

局域网扫码可设置 `FRONTEND_PUBLIC_ORIGIN=http://你的局域网IP:4206`；网关也会尝试从无线网卡推导地址。二维码仍是已有演示流程，不是真实支付。短信、真实收款、AI 外部调用默认关闭，启动器不会读取桌面凭据。

## PostgreSQL / Compose

```powershell
cd deploy
Copy-Item .env.example .env
# 修改 .env：管理员与四个业务库分别设置不同随机密码
docker compose --env-file .env config --quiet
docker compose --env-file .env up --build -d
```

Compose 只发布本机 8080 网关端口，业务服务、数据库和 Redis 使用内部网络。数据库、会员上传各自使用命名卷。修改密码不会重新执行已有 PostgreSQL 卷的初始化脚本，已有卷需要由管理员显式 ALTER ROLE。不要用删除卷代替数据迁移。当前环境 Docker 引擎未运行，Compose 已通过配置解析，容器构建、真实 PostgreSQL 初始化和运行验收尚未执行。

裸机 PostgreSQL：identity/catalog/trade 分别配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，逐个终端执行 `./run.ps1 -Service 服务名 -Profile postgres`。member 使用 `VIRIDAC_DB_URL`、`VIRIDAC_DB_USERNAME`、`VIRIDAC_DB_PASSWORD`。网关配置 `IDENTITY_URL`、`CATALOG_URL`、`TRADE_URL`、`MEMBER_URL`。各服务默认仅监听本机；容器配置显式监听 `0.0.0.0`。

实际数据库名为 `lectus_identity`、`lectus_catalog`、`lectus_trade`、`lectus_member`。四个独立账号、独立迁移，不跨库 JOIN，不共享数据访问层；Compose 撤销 PUBLIC 的跨库连接权限。新空库由各服务 Flyway 初始化。

## 已实现与边界

- Spring Boot 3.5.13 + Spring Cloud 2025.0.1 的网关/认证/目录/交易；会员兼容服务保留其 Boot 4.1.1、MyBatis-Plus 版本，独立父 POM，Java 统一为 21。避免在本次目录拆分中改写既有 SDK 和框架行为。
- 交易服务通过带超时的 HTTP 请求向认证服务校验原始 Bearer 会话，失效返回 401，上游故障返回 503；不会信任客户端的用户/租户头。网关去除伪造身份和转发头，下游仍独立鉴权。无认证结果缓存，注销立即生效。
- 订单和库存共用交易服务本地事务，保留库存行锁、用户操作锁、幂等键、订单价格/地址快照与取消补库存；没有引入未经实现的跨服务分布式事务。
- health/readiness 可用；目录 Redis 缓存可选。使用环境变量、Compose DNS 定位服务，当前没有 Nacos/Eureka/配置中心；Prometheus 依赖存在于三个核心服务，端点未对外暴露，需配置访问边界后另行开启。
- 现有 Web 会员有独立账号和 Token，未与旧交易邮箱账号合并；`member-service` 仍含原有会员资产、演示订单、支付适配与社区等兼容逻辑，尚未按业务域全部拆开。
- 商品目录是来源商品读模型；交易里的示例可售商品有本地库存。二者未自动建立可售映射，导入商品不会因拆分获得价格、库存或真实支付能力。
- 当前已实现官方店与入驻商家的工作台、平台审核、订单发货、用户收货和本地售后联调。真实支付、真实物流、平台结算、营销和生产级租户隔离仍需继续建设，不能把本地模拟流程当作生产资金或履约能力。

详见 [架构和接口路由](docs/ARCHITECTURE.md)、[数据迁移](docs/MIGRATION.md)。原单体接口说明保留在 [API.md](docs/API.md)，其中旧地址接口通过 `/api/legacy/addresses` 访问。

## 三端交易体验（本地测试）

- 用户从导航「商城」选购，填写测试收货信息，提交后进入收银台。付款期限 30 分钟，可选择微信或支付宝测试通道；确认后生成 `TEST-P-` 交易回执。
- 商家在「订单管理」打开待发货订单，填写物流公司及运单号，核对后发货。在详情内可逐步推进测试轨迹：揽收 → 运输 → 派送，由用户确认收货完成交易。
- 用户可在未发货时申请退款，平台在「售后处理」核对金额与退回方式后审核。通过会生成 `TEST-R-` 凭证并返还库存，驳回恢复待发货。
- 三端读取同一订单和事件记录；页面每 5 秒同步。待付款订单超时后在下次商品、订单或工作台请求中关闭并释放库存；当前没有独立定时关单任务。
- 测试支付、物流推进与退款仅在 local 配置开放，不会真实扣款、联系快递或向支付平台发送请求。页面提供测试环境标识。
- 重复付款复用凭证，重复收货/取消不重复返库存，物流推进校验当前阶段，退款处理只允许一次。

local 配置默认允许 4206、4207、4208 的 localhost / 127.0.0.1 浏览器来源；复测脚本携带对应 Origin，覆盖三端浏览器来源校验。生产来源仍需单独配置。

本地链路复测：在项目根目录执行 `python backend/scripts/smoke-mall-local.py`，需要五服务已启动。脚本仅允许 `localSimulation=true`，使用独立 `workflow_demo` 体验账号（本地密码 `Lectus@2026`），保留标记的测试订单，会改变本地测试库存。

验收范围：三端构建、前端 31 项测试、会员服务 111 项测试，以及网关 HTTP 全链路。PostgreSQL 实例与真实支付/物流渠道仍未进行运行验收。
