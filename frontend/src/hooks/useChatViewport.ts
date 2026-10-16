import { RefObject, useLayoutEffect } from "react";

/** 根据真实可视区域收缩聊天面板；移动键盘出现时给输入区让出底部导航的位置。 */
export function useChatViewport(panel: RefObject<HTMLElement>) {
  useLayoutEffect(() => {
    const viewport = window.visualViewport; let frame = 0; let baseline = viewport?.height ?? innerHeight;
    const layout = () => {
      cancelAnimationFrame(frame); frame = requestAnimationFrame(() => {
        const node = panel.current; if (!node) return;
        const mobile = matchMedia("(max-width:767px)").matches;
        const focused = document.activeElement?.tagName === "TEXTAREA" && node.contains(document.activeElement);
        if (!focused) baseline = viewport?.height ?? innerHeight;
        const keyboard = mobile && !!viewport && (window.innerHeight - viewport.height > 120 || focused && baseline - viewport.height > 120);
        document.documentElement.classList.toggle("lumi-keyboard", keyboard);
        const bottom = (viewport?.height ?? innerHeight) + (viewport?.offsetTop ?? 0);
        const nav = mobile && !keyboard ? document.querySelector(".mobile-nav")?.getBoundingClientRect().height ?? 68 : 0;
        const height = Math.max(180, bottom - node.getBoundingClientRect().top - nav - (mobile ? 10 : 16));
        node.style.setProperty("--chat-height", `${height}px`);
      });
    };
    layout(); window.addEventListener("resize", layout); document.addEventListener("focusin", layout); document.addEventListener("focusout", layout); viewport?.addEventListener("resize", layout); viewport?.addEventListener("scroll", layout);
    return () => { cancelAnimationFrame(frame); window.removeEventListener("resize", layout); document.removeEventListener("focusin", layout); document.removeEventListener("focusout", layout); viewport?.removeEventListener("resize", layout); viewport?.removeEventListener("scroll", layout); document.documentElement.classList.remove("lumi-keyboard"); };
  }, [panel]);
}
