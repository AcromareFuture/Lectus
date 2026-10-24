# Lectus｜莱择

> 本文保留用户端历史产品约定与当时接入状态；其中远程 MySQL、商品数量和旧部署描述不代表当前新工程环境。当前项目面向国内，前端分用户商城、商家工作台、平台运营后台，后端默认 PostgreSQL；实际实现与启动方式以项目根 README 和后端 README 为准。

## Platform

Web-first storefront. The active product surface is the responsive Lectus website under `/lectus/`; the former mobile prototype is retained only as an internal reference while the project moves away from a mini-program direction.

## Users and purpose

Consumers discover fashion and lifestyle products, explore community posts, choose variants, maintain a shopping bag and place orders. The user asked to benchmark Dewu's breadth over time.

## Stack

The user accepted React and explicitly requested Java for the backend. First-party API: Java 21 and Spring Boot. The user explicitly chose email/password for the first account flow.

## Confirmed commitments

- Brand: Lectus｜莱择.
- Responsive desktop and phone web layouts, polished feedback, purposeful motion and translucent surfaces matter.
- Current interface has Chinese, English and Japanese settings. Community content and personal information retain their source language.
- User supplied the original white-background images and a new catalog package. Main storefront now uses 250 source SPUs and 2,732 real variants; selling prices and own stock are unknown and purchasing stays disabled. Legacy demo commerce remains separate.
- New external logo reference: black rounded square and white Lectus wordmark; final production exports are pending.
- First backend flow: registration, login, catalog, bag, delivery addresses, order creation and cancellation.
- No real payment, shipment or authentication appraisal integration yet.
- The preexisting local prototype and its stored data should remain usable during integration. The new account flow uses server data.

## 3D

The user approved progressing from an openly licensed shoe model toward 3D viewing and later AR shoe try-on. The user then identified that every product appeared to use the same model and raised per-product modeling workload. Only an exact product-id model binding may enable product 3D viewing; no current catalog item has a dedicated model. Unmapped shoes retain photography and explicitly say no dedicated model is available. The shared Shopify sample lives on the standalone `#shoe-demo` route, without product information or purchase actions. It offers on-demand loading, rotation, zoom, reset, three materials, visible attribution and CC BY4.0 links. This sample is not a digital twin of any listed product. Foot tracking is not implemented. Commercial tracking SDK licensing is unresolved; The user now requests coverage for all 40 shoe products / 109 colorways with zero procurement budget. A local TripoSR trial produced one 0.43 MiB GLB in 84.39 seconds on CPU. It failed visual acceptance because of blurred texture and distorted outsole geometry; it remains on an isolated experiment page and is not bound to the catalog. Batch generation has not started. AI reconstructions must be disclosed as approximations, never exact digital twins; no paid assets or vendor contact is authorized by the zero-budget constraint.

## Open decisions

- Email verification/password recovery provider, payment provider, WeChat credentials and AR SDK licensing.
- Server middleware is MySQL / Redis / MinIO, reached through SSH tunnels; application code runs locally. Redis scoped ACL currently needs restoring after restart. No application deployment in this iteration.
