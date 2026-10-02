"use client";

import { useEffect, useRef } from "react";
import { formatCount } from "@/lib/auction";
import { prefersReducedMotion, springSoft } from "@/lib/motion";

/**
 * Scroll-driven motion for the server-rendered landing page (handoff README "Motion"), applied to
 * the markup by data attributes so the sections themselves stay server components:
 *
 * - `[data-reveal]` (+ optional `data-delay` ms): fades and rises 28px once 12% is in view.
 * - `[data-count]`: counts up to its value (1400ms expo-out) once 40% is in view.
 * - `[data-depth]`: parallax, translated by -scrollY x depth while near the top.
 * - `[data-step]` inside `[data-how]`: activated one by one as the section scrolls through.
 *
 * Reduced motion: reveals are opacity only, counts show the final value, no parallax, every step shown.
 */
export function LandingMotion({ rootId, readyClass, stepOnClass }: { rootId: string; readyClass: string; stepOnClass: string }) {
  const activeStep = useRef(-2);

  useEffect(() => {
    const root = document.getElementById(rootId);
    if (!root) return;
    const reduced = prefersReducedMotion();
    const ease = springSoft();
    const cleanups: (() => void)[] = [];

    // Reveals
    const reveals = Array.from(root.querySelectorAll<HTMLElement>("[data-reveal]"));
    if (reduced) reveals.forEach((el) => (el.style.translate = "none"));
    root.classList.add(readyClass);
    if (typeof IntersectionObserver === "undefined") {
      reveals.forEach((el) => el.setAttribute("data-shown", ""));
    } else {
      const revealObserver = new IntersectionObserver(
        (entries) =>
          entries.forEach((entry) => {
            if (!entry.isIntersecting) return;
            const el = entry.target as HTMLElement;
            const delay = Number(el.dataset.delay) || 0;
            const existing = getComputedStyle(el).transition;
            el.style.transition = `opacity 600ms ease ${delay}ms, translate 800ms ${ease} ${delay}ms` + (existing && existing !== "all 0s ease 0s" ? `, ${existing}` : "");
            el.setAttribute("data-shown", "");
            revealObserver.unobserve(el);
          }),
        { threshold: 0.12, rootMargin: "0px 0px -6% 0px" },
      );
      reveals.forEach((el) => revealObserver.observe(el));
      cleanups.push(() => revealObserver.disconnect());

      // Count-ups
      const counters = Array.from(root.querySelectorAll<HTMLElement>("[data-count]"));
      const countObserver = new IntersectionObserver(
        (entries) =>
          entries.forEach((entry) => {
            if (!entry.isIntersecting) return;
            const el = entry.target as HTMLElement;
            const to = Number(el.dataset.count);
            countObserver.unobserve(el);
            if (reduced) return;
            const start = performance.now();
            const frame = (now: number) => {
              const k = Math.min(1, (now - start) / 1400);
              const value = k === 1 ? to : to * (1 - Math.pow(2, -10 * k));
              el.textContent = formatCount(Math.round(value));
              if (k < 1) requestAnimationFrame(frame);
            };
            requestAnimationFrame(frame);
          }),
        { threshold: 0.4 },
      );
      counters.forEach((el) => countObserver.observe(el));
      cleanups.push(() => countObserver.disconnect());
    }

    // Parallax + how-it-works progress
    const depthEls = Array.from(root.querySelectorAll<HTMLElement>("[data-depth]"));
    const how = root.querySelector<HTMLElement>("[data-how]");
    const steps = how ? Array.from(how.querySelectorAll<HTMLElement>("[data-step]")) : [];
    const setSteps = (active: number) => {
      steps.forEach((el) => el.classList.toggle(stepOnClass, reduced || Number(el.dataset.step) <= active));
    };
    let raf = 0;
    const frame = () => {
      raf = 0;
      const y = window.scrollY;
      if (!reduced && y < 1400) {
        depthEls.forEach((el) => {
          el.style.transform = `translate3d(0,${(-y * Number(el.dataset.depth)).toFixed(1)}px,0)`;
        });
      }
      if (how) {
        const rect = how.getBoundingClientRect();
        const progress = Math.max(0, Math.min(1, (window.innerHeight * 0.75 - rect.top) / (rect.height * 0.6)));
        const active = progress <= 0.05 ? -1 : Math.min(3, Math.floor(progress * 4.4));
        if (active !== activeStep.current) {
          activeStep.current = active;
          setSteps(active);
        }
      }
    };
    const schedule = () => {
      if (!raf) raf = requestAnimationFrame(frame);
    };
    frame();
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule);
    cleanups.push(() => {
      cancelAnimationFrame(raf);
      window.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
    });

    return () => cleanups.forEach((cleanup) => cleanup());
  }, [rootId, readyClass, stepOnClass]);

  return null;
}
