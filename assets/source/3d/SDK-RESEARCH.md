# 莱择：鞋类 3D 展示与 AR 试穿选型

核实日期：2026-10-17。依据公开官网和开发文档；未注册账户、购买服务、联系厂商或进行手机真机试穿测试。

## 建议

当前项目是 React H5。验证阶段可先使用 Google model-viewer 加载下方许可明确的 GLB，完成旋转、缩放及换色。需要实时脚部试穿时，H5 优先评估 Vyking；如果微信小程序是首发平台，优先向弥知 Kivisense 核实专用鞋类试穿 SDK 的小程序支持。

3D 模型本身不提供脚部识别。model-viewer 的 AR 空间放置也不等于脚部试穿。示例鞋模不是现有 Nike、ASICS 等商品的数字模型，不能把它当成这些 SKU 的实物效果。

## Vyking

- 官网：https://www.vyking.io/
- Web SDK：https://docs.vyking.io/docs/vyking-apparel
- 官方示例：https://sneaker-window.vyking.io/vyking-examples/index.html
- 资产准备：https://docs.vyking.io/docs/getting-started
- 提供 Web 自定义元素 vyking-apparel；官网及文档提供网页、iOS、Android 与 WebView 接入信息。
- 追踪功能需要厂商配置文件、client key、授权域名；配置有到期时间。公网 IP 地址是否能授权需询问厂商。
- 文档定位高端移动设备；需测试目标手机的首次模型加载、跟踪、遮挡、内存和帧率。
- React 弹层卸载时需要调用 dispose()，仅移除 DOM 不足以释放资源。
- 未在本次查阅页面发现公开 Web SDK 商业报价，需申请测试许可与报价；未确认微信原生小程序支持。

## 弥知 Kivisense

- 官网：https://www.kivisense.com/
- 鞋类产品：https://tryon.kivisense.com/blog/ar-solution-shoes/
- 小程序通用 AR 接入文档：https://www.yuque.com/kivicube/manual/mp-ar
- 定价页：https://tryon.kivisense.com/blog/subscription-plans/
- 官网同时介绍 AR 试鞋和微信小程序 AR 产品。两者是否可直接组合、鞋类跟踪是否包含在通用插件内，公开资料不足以确认。
- 定价页包含 credits 套餐和商业许可 FAQ，口径混合。FAQ 列出商业许可 $888/月或 $10,000/年（税前）；不能据此认定已包含莱择的小程序 SDK、建模与所有流量费用。
- 定制电商使用需取得对应商业许可；应要求报价分别说明追踪 SDK、平台授权、使用量、SKU 数、模型制作和托管。

## 可用的鞋模

- 文件：MaterialsVariantsShoe/MaterialsVariantsShoe.glb，7,833,592 字节，已下载并通过 GLB 结构校验。模型包含内嵌纹理，无外部 buffer 或图片依赖；2026-10-18 已接入本地鞋类详情，浏览器确认模型渲染、视角按钮及三种材质切换。未进行手机真机或 AR 上脚验证。
- 上游：https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/MaterialsVariantsShoe
- 作者/版权：© 2021 Shopify。
- 许可：Creative Commons Attribution 4.0 International，https://creativecommons.org/licenses/by/4.0/
- 原始说明保存在 README.upstream.md；SHA-256、材质变体和结构校验结果保存在 verification.json。
- 内含 Beach、Midnight、Street 三种材质变体，采用 KHR_materials_variants。
- 使用时在演示/产品的素材署名处保留 Shopify、来源、许可链接；修改后说明修改内容。下载目标为原始模型，不做修改。
- 展示组件：https://modelviewer.dev/ （Apache-2.0；网页组件，非微信原生小程序组件）。

## 申请厂商试用时需确认

1. 是否支持 iOS/Android 浏览器、微信内 H5，以及微信原生小程序；提供各端最低版本与真机演示。
2. 试用 key、域名绑定、过期时间，以及是否允许当前 IP 站点或需要独立域名。
3. 一款鞋的模型制作费用、格式、左右脚、真实单位、遮挡体和定位参数要求。
4. 一次性费用、月费、按次/时长费用、SKU 数和数据/素材托管位置。
