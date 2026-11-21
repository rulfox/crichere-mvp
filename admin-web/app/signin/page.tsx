"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { ApiError, post } from "@/lib/api";
import { useTheme } from "@/lib/hooks";
import { safeNext, tokenEmail } from "@/lib/signin";
import { Banner, Btn, Icon } from "@/components/ui";

/*
 * Board S1/S2/S6 (direction A, floodlit ground). Google draws the sign-in button itself (Google
 * Identity Services), as its branding rules require; the backend checks the ID token and sets the
 * session cookie. Nothing is stored in the browser.
 */

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (options: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: string; auto_select?: boolean; itp_support?: boolean }) => void;
          renderButton: (el: HTMLElement, options: Record<string, unknown>) => void;
          disableAutoSelect: () => void;
        };
      };
    };
  }
}

const CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "";
const ENV_LABEL = process.env.NEXT_PUBLIC_ENV_LABEL ?? "Production · api.crichere.com";

function SignIn() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const button = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<{ kind: "idle" } | { kind: "waiting" } | { kind: "refused"; email: string | null } | { kind: "error"; message: string }>({ kind: "idle" });
  useTheme();

  const onCredential = useCallback(
    async ({ credential }: { credential: string }) => {
      setState({ kind: "waiting" });
      try {
        await post("/api/v1/admin/session", { credential });
        router.replace(next);
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) setState({ kind: "refused", email: tokenEmail(credential) });
        else if (e instanceof ApiError && e.status === 429) setState({ kind: "error", message: "Too many sign-in attempts. Wait 15 minutes and try again." });
        else setState({ kind: "error", message: "Couldn't reach Crichere. Nothing was saved. Try again in a moment." });
      }
    },
    [next, router],
  );

  useEffect(() => {
    const render = () => {
      if (!window.google || !button.current) return;
      window.google.accounts.id.initialize({ client_id: CLIENT_ID, callback: onCredential, auto_select: false, itp_support: true });
      window.google.accounts.id.renderButton(button.current, { theme: "outline", size: "large", text: "signin_with", shape: "rectangular", width: 384, logo_alignment: "left" });
    };
    if (window.google) {
      render();
      return;
    }
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = render;
    document.head.appendChild(script);
  }, [onCredential, state.kind]);

  return (
    <div className="signin">
      <div className="scene" aria-hidden="true">
        <div className="sky" />
        <div className="glow g1" />
        <div className="glow g2" />
        <div className="beam b1" />
        <div className="beam b2" />
        <div className="fld">
          <div className="bnd" />
          <div className="ring" />
          <div className="pitch">
            <i />
            <i />
          </div>
        </div>
        <div className="pylon p1">
          <i className="lamp" />
        </div>
        <div className="pylon p2">
          <i className="lamp" />
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className="stumps" src="/brand/splash-stumps.svg" alt="" />
        <div className="scopy">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/crichere-lockup-reverse.svg" alt="crichere" />
          <h1>From the pavilion.</h1>
          <p>Reports, live auctions and every league, for the Crichere team.</p>
        </div>
      </div>
      <div className="spanel">
        <a className="back toplink" href="https://crichere.com" rel="noopener">
          crichere.com
          <Icon name="arrow_outward" />
        </a>
        <h2>Sign in</h2>
        {state.kind === "refused" ? (
          <Banner tone="danger" icon="cancel">
            <b>{state.email ?? "This Google account"} isn&apos;t on the admin list.</b> Nothing was opened or saved. Choose a different Google account, or ask the owner to add this one.
          </Banner>
        ) : (
          <p className="say">For the Crichere team only. Use the Google account that is on the admin list.</p>
        )}
        {state.kind === "error" && (
          <Banner tone="warn" icon="error">
            {state.message}
          </Banner>
        )}
        {state.kind === "waiting" ? (
          <Btn full>
            <i className="spin" />
            Signing in…
          </Btn>
        ) : (
          <div ref={button} className="gis" aria-label="Sign in with Google" />
        )}
        <div className="snote">
          <Icon name="mail" />
          <span>Every sign-in sends an email to the account owner, so a sign-in you didn&apos;t make shows up straight away.</span>
        </div>
        <div className="sfoot">
          <span className="env">
            <Icon name="dns" />
            {ENV_LABEL}
          </span>
          <span>Sessions last 30 minutes and end at once when you sign out everywhere.</span>
        </div>
      </div>
    </div>
  );
}

export default function SignInPage() {
  return (
    <Suspense>
      <SignIn />
    </Suspense>
  );
}
