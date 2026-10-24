# Meshy 用户下载模型的本地预览

用户提供 `C:/Users/heng/Downloads/model.glb`。截图中的单图输入为蓝色 Kobe；候选演示商品 `wp-kobe` / 道奇蓝。没有据此猜测正式目录的 SPU/配色 ID，没有写入远程数据库或对象存储。

## 处理结果

| 文件 | 字节 | 三角面 |
| --- | ---: | ---: |
| 原始 GLB | 32,235,552 | 999,162 |
| 手机预览 GLB | 6,622,232 | 99,910 |

两份文件保存在 `.local/work/3d-preview/meshy-kobe/`。原件 SHA256 为 `7ac7a2cf6a4df1996f9e68a30321df4d91c18c25fcf4cb0dd61414dac6fea60f`；衍生文件哈希与过程参数见同目录 `review.json`。2048×2048 JPEG 贴图字节未改变。没有新增收费生成或图片上传。

运行 `node tools/assets/optimize_meshy_model.mjs <原件路径> <新的输出目录>` 可复现减面。依赖安装方式在脚本头部，工具仅装在被忽略的 `.runtime/gltf-tools`。使用 glTF Transform 4.5.1 和 meshoptimizer 1.3.0，ratio=0.1、error=0.001、lockBorder=true；这个误差值不是实物尺寸精度。

## 查看

启动 Vite：`npm run dev -- --port 4205 --strictPort`。

- 商品布局：`http://127.0.0.1:4205/#product?id=wp-kobe&preview3d=meshy`
- 原件/减面版与视角对照：`http://127.0.0.1:4205/experiments/meshy-lab.html`

商品入口同时要求开发模式、显式 preview3d 参数及精确 wp-kobe ID。普通商品入口和生产构建均不启用该实验模型。实验页不在 Vite 生产入口中，模型留在 work 目录。

## 验收范围

技术结构通过现有 mobile GLB 验证器（小于 25 MiB、小于 200,000 三角面、内嵌纹理）。前端 17 项测试和构建通过；构建仍提示 model-viewer 分包体积较大。

本地浏览器已加载商品预览、操作旋转与重置，检查减面版斜侧、鞋底、鞋口及原件鞋口；在测试显示尺寸下未观察到鞋口减面的明显差别。鞋带存在融合感，鞋底不能视为准确商品纹路。最终扩展浏览器检查超时，未完成所有角度的交互验收，也没有测量物理手机帧率。截图保存在 previews/meshy-kobe-*.jpg。

模型没有可靠实物尺寸，不支持脚部追踪或尺码匹配。用户账户方案的输出授权及来源图片权利尚未核验，保持 publishable=false、catalogApproved=false；本轮只交付本地预览。
