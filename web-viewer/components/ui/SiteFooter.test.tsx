import { render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LEGAL_PAGES_LINKED, SiteFooter } from "./SiteFooter";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("SiteFooter (design update #5 B4)", () => {
  it("links Contact to the configured address", () => {
    vi.stubEnv("NEXT_PUBLIC_CONTACT_EMAIL", "hello@crichere.com");
    render(<SiteFooter />);

    const nav = screen.getByRole("navigation", { name: "Footer" });
    expect(within(nav).getByRole("link", { name: "Contact" })).toHaveAttribute("href", "mailto:hello@crichere.com");
  });

  it("drops Contact without an address, and nothing replaces it", () => {
    vi.stubEnv("NEXT_PUBLIC_CONTACT_EMAIL", "");
    render(<SiteFooter />);

    expect(screen.queryByRole("link", { name: "Contact" })).not.toBeInTheDocument();
    expect(screen.getByText("© 2026 Crichere")).toBeInTheDocument();
  });

  it("never renders a '#' link", () => {
    vi.stubEnv("NEXT_PUBLIC_CONTACT_EMAIL", "hello@crichere.com");
    const { container } = render(<SiteFooter current="privacy" />);

    expect(container.querySelector('a[href="#"]')).toBeNull();
  });

  it("holds Privacy / Terms back until the legal text is real", () => {
    vi.stubEnv("NEXT_PUBLIC_CONTACT_EMAIL", "hello@crichere.com");
    render(<SiteFooter current="terms" />);

    if (LEGAL_PAGES_LINKED) {
      expect(screen.getByRole("link", { name: "Terms" })).toHaveAttribute("aria-current", "page");
    } else {
      expect(screen.queryByRole("link", { name: "Privacy" })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Terms" })).not.toBeInTheDocument();
    }
  });
});
