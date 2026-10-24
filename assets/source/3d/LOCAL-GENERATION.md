# 零采购预算的本地 3D 试验

用户模型预算为零。先在本机 RTX 5060 Laptop（8GB 显存）试跑 TripoSR，不购买模型、不调用收费接口、不上传商品图到外部生成服务。

单张商品图生成的是 AI 外观近似：另一侧、鞋底、内衬、商标、材质和尺寸无法据此验证。不可作为精准实物模型、鉴别依据或 AR 上脚试穿。模型逐款逐配色审核，不能拿一个公共模型映射全部商品。

## 独立环境

Python 3.12 虚拟环境位于 `.runtime/3d-venv`，CUDA 12.8 版 PyTorch 支持本机 Blackwell GPU。与商城的 Java、Node 运行环境分开。

上游代码：https://github.com/VAST-AI-Research/TripoSR （MIT）。权重原始发布：https://huggingface.co/stabilityai/TripoSR 。直连超时时，下载 AI-ModelScope 镜像固定版本 `7c62c4ba333b60958888708f9f5f40a4810b6a02`，下载后核对 SHA256：

| 文件 | SHA256 |
| --- | --- |
| config.yaml | 74ca708ce086bf68e97709ea6b3d91f14717921c04691e84043f0eb8fcc68e62 |
| model.ckpt | 429e2c6b22a0923967459de24d67f05962b235f79cde6b032aa7ed2ffcd970ee |

依赖安装（PowerShell）：

```powershell
uv pip install --python .runtime/3d-venv/Scripts/python.exe torch torchvision --index-url https://download.pytorch.org/whl/cu128
uv pip install --python .runtime/3d-venv/Scripts/python.exe "numpy<3" omegaconf==2.3.0 einops==0.8.1 transformers==4.45.2 "trimesh>=4.6,<5" "rembg[cpu]" imageio scikit-image
```

下载、安装耗时不等同于模型推理耗时。只有产出 GLB、实际查看并核对配色之后，才记录完成数量。

## 生成与审阅

```powershell
.runtime/3d-venv/Scripts/python.exe tools/assets/generate_shoe_preview.py <商品图片路径> --product-id <SPU-ID> --colorway-id <CLR-ID>
```

输出 `.local/work/3d-preview/<SPU-ID>/<CLR-ID>/` 下的抠图、`preview.glb`、`generation.json`。脚本不自动绑定、不导入商城。报告记录源图和模型哈希、面数、字节、耗时、显存峰值及 AI 来源。

为避免 Windows 自编译 CUDA 扩展，使用 scikit-image 在 CPU 提取真实等值面，保留上游坐标约定；DINO ViT-B/16 按架构实例化，权重仍从 TripoSR checkpoint 加载，不额外下载另一套 DINO 权重。默认 192 分辨率、顶点颜色，先检验形状，再考虑贴图烘焙。

验收应检查正面、背面、鞋底、旋转方向、漂浮碎片、手机帧率与加载字节。失败结果留在实验目录，不应以“专属精准模型”发布。全量需求清单见 `.local/work/catalog-import/shoe-model-requirements.json`（40 款 / 109 配色）。


CUDA 运行库较大且当前下载速度偏低；可先用 PyPI 的 CPU 版 PyTorch 在 `.runtime/3d-cpu-venv` 验证质量，运行时显式传 `--device cpu --resolution 128`。此时显存峰值记为 0，不宣称 GPU 推理结果；CPU 耗时以 generation.json 实测为准。


商品包是白底图，默认 `--background white` 使用连通轮廓和内部填充去背景，不依赖另一个抠图权重。抠图会写入 cutout.png 便于检查，阴影与白色边缘仍需人工核对。`--background u2netp` 为可选方式，需要先下载并校验 rembg 官方权重（当前 GitHub 下载超时，未作为本轮依赖）。

本地对照页：`http://127.0.0.1:4205/experiments/3d-lab.html`。这是开发用独立页面，不会进入生产构建或自动绑定目录；页面并列原图与生成物，提供另一侧、鞋底与重置操作。


## 首次实测结果

ASICS Gel-Kayano 14 / Lime Zest-Pure Silver 已成功产出真实可旋转 GLB：CPU 84.39 秒，22,596 三角面，约 0.43 MiB，192 分辨率之前先用 128 验证。已修正 TripoSR Z-up 到 glTF Y-up 的朝向。

技术格式与手机体积校验通过；视觉未达到商城验收：纹理模糊、鞋底推断变形且有漂浮裙边，单张图另一侧无法验证。结果保留在独立实验页供查看，未绑定商品，未启动其余 108 配色批量生成。GPU 只能提升推理速度，不能消除本次模型与单张输入的精度限制。后续若要求接近首个成品鞋模，应选择合法现成精细资产，或取得实物多视角扫描后修模。

## 来源视角补查与轮廓投影实测（2026-10-18）

商品包采集器把每配色图片限制为最多两张，这不代表来源图库只有两张。只读审计：

```powershell
.runtime/3d-cpu-venv/Scripts/python.exe tools/assets/audit_shoe_source_views.py <商品库_第1批目录>
```

109 组鞋配色中，101 组下载两张、8 组下载一张；104 组对应来源图库有额外候选记录。候选尚需检查配色、姿态、重复图与相机视角，不等于 104 组都能直接重建。

ASICS / 1203A537.301 已补取来源图库全部六张 800×800 原图，存放于 `.local/work/3d-preview/source-views/ASICS-KAYANO14-1203A537-301/`。其中两张是成对摆拍；四张单鞋分别为两个侧面、鞋面、鞋底。鞋面、鞋底并非四方位模型的标准水平机位，不能简单标成 front/left/back/right。

本轮做了独立的轮廓交集与原图投影验证，不是深度学习多视图重建：

```powershell
.runtime/3d-cpu-venv/Scripts/python.exe tools/assets/generate_multiview_hull.py --resolution 128
```

输出 `.local/work/3d-preview/multiview-hull/ASICS-KAYANO14-1203A537-301/`，包含有贴图 GLB、无贴图 GLB、四张抠图、图集、来源与限制记录。试验页为 `http://127.0.0.1:4205/experiments/multiview-lab.html`；可切换原图贴图、无贴图形状、上一轮 TripoSR，检查侧面、斜侧、另一侧、鞋面和鞋底。

实测 49,224 三角面，约 5.45 MiB，CPU 约 0.89 秒。侧面照片纹理较 TripoSR 顶点色清晰，但斜侧出现明显的投影拉伸、接缝与不真实体积；鞋口、鞋带和细小结构没有还原。**视觉验收未通过，publishable=false，不绑定目录，不批量生产。** 提高轮廓网格分辨率无法恢复这些结构。

### 真正多图模型的可用性核查

- [TRELLIS](https://github.com/microsoft/TRELLIS)：官方代码存在 `run_multi_image`，README 说明它是免训练多图条件算法；官方要求 Linux、至少 16GB 显存。本机为 8GB；未安装、未推理，不能宣称已用它生成。
- [Hunyuan3D-2mv](https://huggingface.co/tencent/Hunyuan3D-2mv)：有专门多视图形状模型，四方位输入，形状路径官方约需 6GB 显存；标准 fp16 safetensors 约 4.93GB。许可排除欧盟、英国、韩国，限制也涉及输出，不可直接当莱择全球分发方案。本轮仅核查文档/源码与下载可达性，未下载完整权重、未推理。
- [TripoSG](https://github.com/VAST-AI-Research/TripoSG)：源码 MIT、官方标注至少 8GB 显存；本轮没有确认能将这组真实照片作为多视角共同约束，不作为“已接入多图重建”记录。

上述核查不能推出免费多图模型必然失败。本轮能确认的是：已有 TripoSR 和轮廓投影两个具体结果仍不达商品模型标准，照片数量增加也不等于完成相机标定和几何还原。
