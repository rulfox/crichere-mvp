"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";

/**
 * Motion primitives shared by the landing page and the live auction viewer (docs/PHASE11.md,
 * handoff README "Motion"). Everything animates transform/opacity only. Every event-driven
 * animation goes through [animate], which drops to a plain fade when the user prefers reduced
 * motion -- the global CSS rule already neutralises CSS transitions/keyframes, this is the JS branch.
 */

const SPRING_SOFT_RAW =
  "linear(0, 0.006, 0.025 2.8%, 0.101 6.1%, 0.539 18.9%, 0.721 25.3%, 0.849 31.5%, 0.937 38.1%, 0.968 41.8%, 0.991 45.7%, 1.006 50.1%, 1.015 55%, 1.017 63.9%, 1.001 85.9%, 1)";
const BOUNCE_RAW =
  "linear(0, 0.009, 0.035 2.1%, 0.141, 0.281 6.7%, 0.723 12.9%, 0.938 16.7%, 1.017, 1.077 20.4%, 1.121, 1.149 24.3%, 1.159, 1.163 27.4%, 1.154, 1.129 32.8%, 1.051 39.6%, 1.017 43.1%, 0.991, 0.977 51%, 0.975 57.1%, 0.997 69.8%, 1.003 76.9%, 1)";

export const SPRING_SNAPPY = "cubic-bezier(.34,1.56,.64,1)";
export const EXPO_OUT = "cubic-bezier(.22,1,.36,1)";

let linearSupport: boolean | undefined;

/** Whether WAAPI accepts `linear()` easing -- feature-detected once, the same way the design files do. */
export function supportsLinear(): boolean {
  if (linearSupport !== undefined) return linearSupport;
  if (typeof document === "undefined") return false;
  try {
    document.createElement("div").animate([], { easing: "linear(0, 1)" });
    linearSupport = true;
  } catch {
    linearSupport = false;
  }
  return linearSupport;
}

export const springSoft = () => (supportsLinear() ? SPRING_SOFT_RAW : EXPO_OUT);
export const bounce = () => (supportsLinear() ? BOUNCE_RAW : SPRING_SNAPPY);

const REDUCED_QUERY = "(prefers-reduced-motion: reduce)";

export function prefersReducedMotion(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function" && window.matchMedia(REDUCED_QUERY).matches;
}

function subscribeReduced(callback: () => void) {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return () => {};
  const query = window.matchMedia(REDUCED_QUERY);
  query.addEventListener("change", callback);
  return () => query.removeEventListener("change", callback);
}

/** Reactive [prefersReducedMotion] -- `false` during SSR, so the server markup is the full-motion one. */
export function useReducedMotion(): boolean {
  return useSyncExternalStore(subscribeReduced, prefersReducedMotion, () => false);
}

/**
 * `el.animate()` with the reduced-motion branch built in: under reduced motion the keyframes are
 * replaced by a plain opacity fade of [reducedDuration] ms (or skipped when that's 0).
 * A no-op where WAAPI doesn't exist (jsdom).
 */
export function animate(
  el: Element | null | undefined,
  keyframes: Keyframe[],
  options: KeyframeAnimationOptions,
  reducedDuration = 200,
): Animation | null {
  if (!el || typeof (el as HTMLElement).animate !== "function") return null;
  if (prefersReducedMotion()) {
    if (reducedDuration <= 0) return null;
    return el.animate([{ opacity: 0 }, { opacity: 1 }], { duration: reducedDuration, easing: "ease-out" });
  }
  return el.animate(keyframes, options);
}

/** True once the element has scrolled [threshold] into view -- fires once, then disconnects. */
export function useInView<T extends Element>(threshold: number, rootMargin = "0px") {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || inView) return;
    if (typeof IntersectionObserver === "undefined") {
      setInView(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setInView(true);
          observer.disconnect();
        }
      },
      { threshold, rootMargin },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [threshold, rootMargin, inView]);
  return [ref, inView] as const;
}

/** Calls [onFrame] at most once per animation frame while the page scrolls or resizes, and once on mount. */
export function useScrollFrame(onFrame: () => void) {
  const callback = useRef(onFrame);
  useEffect(() => {
    callback.current = onFrame;
  });
  useEffect(() => {
    let raf = 0;
    const schedule = () => {
      if (!raf) {
        raf = requestAnimationFrame(() => {
          raf = 0;
          callback.current();
        });
      }
    };
    callback.current();
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
    };
  }, []);
}
