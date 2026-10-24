import React, { useEffect, useRef, useState } from "react";
import { ArrowLeft, ArrowRight, Box, Minus, Plus, RotateCcw } from "lucide-react";
import { useI18n } from "./i18n";
import "./shoe-viewer.css";

const initialOrbit = "-35deg 72deg 105%";
export default function ShoeViewer({ model, layout }) {
  const { t, language } = useI18n();
  const materials = model.materials || [];
  const viewer = useRef(null);
  const [attempt, setAttempt] = useState(0);
  const [ready, setReady] = useState(false);
  const [status, setStatus] = useState("loading");
  const [progress, setProgress] = useState(0);
  const [variant, setVariant] = useState(model.variantName || materials[0]?.id || null);
  const [available, setAvailable] = useState([]);
  const a11y = Object.fromEntries([
    ["left", "左侧视角"], ["right", "右侧视角"], ["front", "正面视角"], ["back", "背面视角"],
    ["upper-left", "左上方视角"], ["upper-right", "右上方视角"], ["upper-front", "前上方视角"], ["upper-back", "后上方视角"],
    ["lower-left", "左下方视角"], ["lower-right", "右下方视角"], ["lower-front", "前下方视角"], ["lower-back", "后下方视角"],
    ["interaction-prompt", "使用触摸、鼠标或方向键旋转鞋模。"],
  ].map(([key, text]) => [key, t(text)]));
  const reducedMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  useEffect(() => {
    let active = true;
    setStatus("loading"); setProgress(0); setReady(false);
    // The viewer library and asset are requested only after opening 3D.
    import("@google/model-viewer")
      .then(() => { if (active) setReady(true); })
      .catch(() => { if (active) setStatus("error"); });
    return () => { active = false; };
  }, [attempt]);

  useEffect(() => {
    if (!ready) return;
    const element = viewer.current;
    let active = true;
    const loaded = () => {
      if (!active) return;
      clearTimeout(timeout);
      setAvailable(element.availableVariants);
      setStatus("loaded"); setProgress(100);
    };
    const failed = () => { if (active) { clearTimeout(timeout); setStatus("error"); } };
    const loading = (event) => { if (active) setProgress(Math.round(event.detail.totalProgress * 100)); };
    element.addEventListener("load", loaded);
    element.addEventListener("error", failed);
    element.addEventListener("progress", loading);
    const timeout = setTimeout(failed, 45000);
    // A rejected GLTF request may be cached by the renderer. An explicit retry
    // needs a fresh resource key so a recovered connection can actually reload.
    const url = new URL(model.src, new URL(import.meta.env.BASE_URL, document.baseURI));
    if (attempt) url.searchParams.set("retry", Date.now());
    element.src = url.href;
    if (element.loaded) loaded();
    return () => {
      active = false; clearTimeout(timeout);
      element.removeEventListener("load", loaded);
      element.removeEventListener("error", failed);
      element.removeEventListener("progress", loading);
    };
  }, [ready, attempt, model.src]);

  useEffect(() => {
    if (ready && status === "loaded" && variant) viewer.current.variantName = variant;
  }, [variant, ready, status]);

  function rotate(direction) {
    const element = viewer.current;
    const orbit = element.getCameraOrbit();
    element.cameraOrbit = `${orbit.theta + direction * Math.PI / 4}rad ${orbit.phi}rad ${orbit.radius}m`;
    if (reducedMotion()) element.jumpCameraToGoal();
  }
  function zoom(direction) {
    viewer.current.zoom(direction);
    if (reducedMotion()) viewer.current.jumpCameraToGoal();
  }
  function reset() {
    viewer.current.cameraOrbit = initialOrbit;
    viewer.current.fieldOfView = "auto";
    viewer.current.cameraTarget = "auto auto auto";
    if (reducedMotion()) viewer.current.jumpCameraToGoal();
  }
  return <section className={`shoe-preview ${layout === 'fill' ? 'shoe-preview-fill' : ''}`} aria-label={t("3D鞋模体验")}>
    <p className="shoe-disclaimer">{t(model.experimental ? "AI 生成的模型仅供外观参考，不支持虚拟试穿。" : model.sample ? "独立鞋款模型，仅供外观参考，不支持虚拟试穿。" : "商品3D模型仅供外观参考；暂不支持上脚试穿。")}</p>
    <div className="shoe-stage" aria-busy={status === "loading"}>
      <span className="shoe-stage-label"><Box size={14}/>{t(model.experimental ? "3D 看细节" : model.sample ? "3D 看细节" : "商品3D展示")}</span>
      {ready && <model-viewer
        key={attempt}
        ref={viewer}
        inert={status !== "loaded"}
        lang={language === "zh" ? "zh-CN" : language}
        alt={t(model.alt)}
        a11y={JSON.stringify(a11y)}
        camera-controls=""
        disable-pan=""
        disable-tap=""
        touch-action="pan-y"
        camera-orbit={initialOrbit}
        min-camera-orbit="auto 15deg 55%"
        max-camera-orbit="auto 160deg 180%"
        field-of-view="auto"
        interaction-prompt="none"
        shadow-intensity="1"
        shadow-softness=".85"
        exposure="1.1"
        environment-image="neutral"
        loading="eager"
      ><div slot="progress-bar"/></model-viewer>}
      {status === "loading" && <div className="shoe-loading" role="status">
        <Box size={25} strokeWidth={1.2}/><b>{t("正在准备3D鞋模")}</b>
        <span>{t(model.sample ? "首次加载约8MB，请稍候。" : "正在加载模型，请稍候。")}</span>
        <progress value={progress} max="100" aria-label={t("模型加载进度")}/>
      </div>}
      {status === "error" && <div className="shoe-loading shoe-load-error" role="alert">
        <Box size={25} strokeWidth={1.2}/><b>{t("暂时无法显示3D模型")}</b>
        <span>{t("请检查网络连接，或更新浏览器后重试。")}</span>
        <button className="outline-button" onClick={() => { setReady(false); setAttempt((value) => value + 1); }}>{t("重新加载")}</button>
      </div>}
      {status === "loaded" && <p className="shoe-gesture">{t("拖动旋转 · 双指缩放")}</p>}
    </div>
    <div className="shoe-controls" aria-label={t("模型视角控制")}>
      <button disabled={status !== "loaded"} aria-label={t("向左旋转鞋模")} onClick={() => rotate(-1)}><ArrowLeft size={18}/></button>
      <button disabled={status !== "loaded"} aria-label={t("向右旋转鞋模")} onClick={() => rotate(1)}><ArrowRight size={18}/></button>
      <span aria-hidden="true"/>
      <button disabled={status !== "loaded"} aria-label={t("缩小鞋模")} onClick={() => zoom(-1)}><Minus size={18}/></button>
      <button disabled={status !== "loaded"} aria-label={t("放大鞋模")} onClick={() => zoom(1)}><Plus size={18}/></button>
      <button className="shoe-reset" aria-label={t("重置视角")} title={t("重置视角")} disabled={status !== "loaded"} onClick={reset}><RotateCcw size={15}/><span>{t("重置视角")}</span></button>
    </div>
    {materials.length > 0 && <div className="shoe-materials" role="group" aria-label={t(model.sample ? "模型配色" : "模型配色")}>
      {materials.map((item) => <button key={item.id} disabled={status !== "loaded" || !available.includes(item.id)} aria-pressed={variant === item.id} onClick={() => setVariant(item.id)}><i style={{ background: item.color }}/>{t(item.label)}</button>)}
    </div>}
    {model.attribution && <p className="shoe-attribution">{t(model.sample ? "模型来源" : "模型来源")} {model.attribution.copyright} · <a href={model.attribution.source} target="_blank" rel="noreferrer">{t("来源")}</a> · <a href={model.attribution.licenseUrl} target="_blank" rel="noreferrer">{model.attribution.license}</a>{model.attribution.note && <><br/>{t(model.attribution.note)}</>}</p>}
  </section>;
}
