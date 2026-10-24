import React, { useEffect, useLayoutEffect, useRef, useState } from "react";

const reducedMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

// A single press system also covers keyboard activation and newly rendered controls.
export function usePressFeedback() {
  const root = useRef(null);
  useEffect(() => {
    const surface = root.current;
    if (!surface) return;
    let held = null;
    let press = null;
    let origin = null;
    const running = new Map();
    const animate = (button, frames, options) => {
      running.get(button)?.cancel();
      const animation = button.animate(frames, options);
      running.set(button, animation);
      animation.onfinish = () => {
        if (running.get(button) === animation) running.delete(button);
      };
      return animation;
    };
    const down = (event) => {
      const button = event.target.closest?.("button,summary,a[href]");
      if (reducedMotion() || event.button !== 0 || !button || button.disabled) return;
      held = button;
      origin = [event.clientX, event.clientY];
      press = animate(button, [{ scale: "1" }, { scale: ".96" }], {
        duration: 100, fill: "forwards", easing: "ease-out",
      });
    };
    const release = (cancelled = false) => {
      if (!held) return;
      const button = held;
      const start = getComputedStyle(button).scale;
      press?.cancel();
      held = null;
      if (!button.isConnected || reducedMotion()) return;
      animate(button, cancelled
        ? [{ scale: start }, { scale: "1" }]
        : [{ scale: start }, { scale: "1.015", offset: .55 }, { scale: "1" }], {
        duration: cancelled ? 120 : 310, easing: "cubic-bezier(.22,1,.36,1)",
      });
    };
    const up = () => release(false);
    const cancel = () => release(true);
    const move = (event) => {
      if (held && Math.hypot(event.clientX - origin[0], event.clientY - origin[1]) > 9) cancel();
    };
    const keyboardClick = (event) => {
      const button = event.target.closest?.("button,summary,a[href]");
      if (event.detail !== 0 || reducedMotion() || !button || button.disabled) return;
      animate(button, [{ scale: ".96" }, { scale: "1.015", offset: .55 }, { scale: "1" }], {
        duration: 300, easing: "cubic-bezier(.22,1,.36,1)",
      });
    };
    surface.addEventListener("pointerdown", down, { passive: true });
    surface.addEventListener("click", keyboardClick);
    window.addEventListener("pointerup", up);
    window.addEventListener("pointercancel", cancel);
    window.addEventListener("pointermove", move, { passive: true });
    window.addEventListener("blur", cancel);
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const onPreference = () => {
      if (!preference.matches) return;
      held = null;
      running.forEach((animation) => animation.cancel());
      running.clear();
    };
    preference.addEventListener("change", onPreference);
    return () => {
      surface.removeEventListener("pointerdown", down);
      surface.removeEventListener("click", keyboardClick);
      window.removeEventListener("pointerup", up);
      window.removeEventListener("pointercancel", cancel);
      window.removeEventListener("pointermove", move);
      window.removeEventListener("blur", cancel);
      preference.removeEventListener("change", onPreference);
      press?.cancel();
      running.forEach((animation) => animation.cancel());
    };
  }, []);
  return root;
}

// The active surface moves continuously, including during rapid repeated selection.
export function ChoiceRail({ selected, className = "", as: Tag = "div", children, ...rest }) {
  const rail = useRef(null);
  const [position, setPosition] = useState(null);
  useLayoutEffect(() => {
    const element = rail.current;
    const measure = () => {
      const active = element.querySelector('button[aria-pressed="true"],button[aria-current="page"]');
      if (!active) return;
      const next = { x: active.offsetLeft, y: active.offsetTop, w: active.offsetWidth, h: active.offsetHeight };
      setPosition((old) => old && Object.keys(next).every((key) => old[key] === next[key]) ? old : next);
      if (element.scrollWidth > element.clientWidth) {
        element.scrollTo({ left: Math.max(0, next.x - (element.clientWidth - next.w) / 2), behavior: reducedMotion() ? "instant" : "smooth" });
      }
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [selected]);
  return <Tag ref={rail} className={`choice-rail ${className}`} data-ready={!!position} {...rest}>
    {position && <span aria-hidden="true" className="choice-indicator" style={{ width: position.w, height: position.h, transform: `translate3d(${position.x}px,${position.y}px,0)` }} />}
    {children}
  </Tag>;
}

// Retain a noninteractive snapshot briefly so sheets and notices can leave gracefully.
export function Presence({ show, className = "", children }) {
  const snapshot = useRef(children);
  if (show) snapshot.current = children;
  const [retained, setRetained] = useState(show);
  useLayoutEffect(() => {
    if (show) { setRetained(true); return; }
    if (reducedMotion()) { setRetained(false); return; }
    const timer = setTimeout(() => setRetained(false), 180);
    return () => clearTimeout(timer);
  }, [show]);
  if (!show && !retained) return null;
  return <div className={`motion-presence ${className} ${show ? "is-present" : "is-leaving"}`} inert={!show || undefined} aria-hidden={!show || undefined}>
    {show ? children : snapshot.current}
  </div>;
}
