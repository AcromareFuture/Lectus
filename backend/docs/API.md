> 微服务版本：先阅读同目录 ARCHITECTURE.md。以下旧交易地址经过网关时使用 /api/legacy/addresses；会员地址仍为 /api/addresses。两套账号 Token 不通用。

# API v0.1

交易接口金额为整数人民币分（CNY）；来源目录的销售金额与币种允许为空。时间使用带时区ISO 8601。错误统一为 `{"code":"...","message":"..."}`。私有接口需要 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| POST | `/api/auth/register` | 注册，201，返回会话 |
| POST | `/api/auth/login` | 登录，返回会话 |
| GET | `/api/auth/me` | 当前账号 |
| POST | `/api/auth/logout` | 当前会话失效，204 |
| GET | `/api/products` | 分页商品与规格 |
| GET | `/api/products/{id}` | 商品详情和规格库存 |
| GET | `/api/cart` | 本账号购物袋 |
| PUT | `/api/cart/items` | 设置规格的数量和选中状态 |
| DELETE | `/api/cart/items/{skuId}` | 删除规格，204 |
| GET | `/api/addresses` | 本账号地址 |
| POST | `/api/addresses` | 创建地址，201 |
| PUT | `/api/addresses/{id}` | 修改地址 |
| DELETE | `/api/addresses/{id}` | 删除地址，204 |
| POST | `/api/orders` | 事务下单，201，必须提供Idempotency-Key |
| GET | `/api/orders` | 本账号订单，page默认0，size默认20，最大100 |
| GET | `/api/orders/{id}` | 本账号订单详情 |
| POST | `/api/orders/{id}/cancel` | 取消待付款订单，幂等 |

## 注册

```json
{"email":"guest@example.test","password":"Example-only-123!","displayName":"莱择访客"}
```

密码10–64字符，UTF-8不超过72字节。邮箱归一化为小写并唯一。登录请求仅发送email/password。响应包含 `token`、`expiresAt`、`user`。没有公开固定测试账号或管理员口令。

## 商品筛选

`GET /api/products?category=鞋履&q=Nike&sort=price_asc&page=0&size=20`

参数：`category`、`q`（多个词同时匹配）、`sort`（recommended / price_asc / price_desc / popular）、`minPriceCents`、`maxPriceCents`、`page`、`size`。响应 `{items,total,page,size,currency}`。商品包含 `skus:[{id,productId,size,color,stock}]`。商品image为资源名，例如 `product-kobe`，前端使用 `/lectus/images/product-kobe.webp`。

## 购物袋

```json
{"skuId":"从商品响应选择规格ID","quantity":2,"checked":true}
```

PUT为覆盖数量1–99；库存不足409。响应为完整购物袋列表。需要删除时使用DELETE，不能设置为0。

## 地址

```json
{"recipient":"测试访客","phone":"13800000000","region":"上海市 浦东新区","detail":"示例路100号","isDefault":true}
```

支持国际电话字符，第一条地址自动默认，删除默认地址后自动选择余下地址。每账号最多20条。创建/修改响应包含地址id。

## 下单

请求头 `Idempotency-Key: <客户端生成的UUID>`；网络重试使用相同key和body，不要每次自动换新key。

```json
{
  "addressId":"本账号的地址ID",
  "items":[{"skuId":"商品规格ID","quantity":2}],
  "fromCart":true
}
```

`fromCart:true`要求购物袋内有同样数量的规格，下单后仅删除本次购买项。`false`允许直接购买。禁止同一规格重复出现在列表，最多50个规格。价格、折扣、订单状态等不得从客户端提交；未知JSON字段返回400。

响应含id、status、totalCents、currency、address快照、createdAt和items。例：149900分×2=299800分。没有真实支付功能，不能由客户端把状态改为已支付。

## 错误

400 参数/格式错误；401 会话缺失或失效；404 当前账号中不存在该资源；409 库存不足、购物袋变化、幂等冲突；429 请求过于频繁。前端显示错误，并保留同一笔订单的提交key，以便安全重试。


## 来源商品目录（本轮）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/catalog/meta` | 可见 SPU、SKU 总量、分类与品牌 |
| GET | `/api/catalog/products` | category、brand（来源品牌 ID）、q、page、size；默认 24，最大 60 |
| GET | `/api/catalog/products/{SPU-ID}` | 图片、真实 SKU 组合、配色图片关联和已审核模型 |
| GET | `/api/catalog/assets/catalog/v1/{sha256}/{file}` | original.jpg / thumb.webp / detail.webp |
| GET | `/api/catalog/assets/models/{sha256}.glb` | 有效内容哈希 GLB |

此组接口公开只读。搜索按字面关键词匹配，不把百分号/下划线当 SQL 通配符。隐藏商品不计数也不可访问。模型与图片只接受固定路径形状，禁止外部任意 URL。

列表响应 `{items,total,page,size,hasMore}`。`priceCents:null`、`currency:null`、`purchasable:false` 表示尚未完成自有定价/库存，不可进入交易接口。来源报价与来源可用标记保留在数据库原始字段，不能解释成莱择的售价、库存或人民币金额。

详情包含 `images:[{id,thumbnail,image}]`、`skus:[{id,colorwayId,sourceSku,specs,imageIds,priceCents,currency,stock,purchasable}]` 和 `colorways:[{id,name,explicit,styleCode,mainImageId,imageIds,model,modelStatus}]`。以真实 SKU 的 `specs` 组合选择，不构造规格笛卡尔积。跨配色待审图片不进入可见图库。

`model:null` 表示该配色没有已审核文件；不可回退公共鞋模。已审核描述含 productId / colorwayId、asset、sha256、variantName、署名与许可。每款每配色绑定校验见 `backend/tools/catalog/bind_models.py`；大于 25 MiB、超过 20 万三角面、外链贴图或缺少许可的 GLB 不接受。AI 生成试验目前不自动发布至目录。

目录 JSON 使用 `lectus:catalog:v1:<revision>:<filter-hash>` Redis 缓存，TTL 5 分钟；导入/绑定推进 revision。Redis 故障回源数据库，30 秒后再尝试缓存。哈希素材响应 ETag、304 和一年 immutable 缓存；只在用户打开 3D 时请求模型。
