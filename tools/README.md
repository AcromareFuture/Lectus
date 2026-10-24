# 开发与素材工具

## 开发入口

先在 `frontend/` 执行 `npm ci`，再从项目根目录启动：

```powershell
./tools/dev/start-web-local.ps1 -App storefront
./tools/dev/start-web-local.ps1 -App merchant
./tools/dev/start-web-local.ps1 -App platform
```

每条命令独占一个终端。`start-member-local.ps1` 是兼容入口，委托 `backend/scripts/start-local.ps1` 启动本地后端；后端专用脚本统一位于 `backend/scripts/`。旧远程启动入口已停用。

## 素材处理

从项目根目录执行 `python tools/assets/generate_app_icon.py` 等工具。脚本已改为从新目录定位项目根；图标输出到 `frontend/apps/storefront/public/`，模型试验和中间结果集中到 `.local/work/`。

3D、AI 素材工具有各自 Python 环境、模型和外部服务依赖；重构不自动运行生成、上传或收费 API。详见 `assets/source/3d/`。商品导入、模型绑定等后端专用工具位于 `backend/tools/catalog/`。
