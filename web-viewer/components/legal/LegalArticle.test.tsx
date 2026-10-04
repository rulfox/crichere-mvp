import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LegalArticle } from "./LegalArticle";

const sections = [
  { id: "one", title: "1. Using Crichere", body: <p>First.</p> },
  { id: "two", title: "2. Accounts", body: <p>Second.</p> },
];

describe("LegalArticle (design update #5 B3)", () => {
  it("renders the title block and a contents list that links every section", () => {
    render(<LegalArticle page="terms" title="Terms of Service" updated="2026-10-04" intro="Intro." sections={sections} />);

    expect(screen.getByRole("heading", { level: 1, name: "Terms of Service" })).toBeInTheDocument();
    expect(screen.getByText("Legal")).toBeInTheDocument();
    expect(screen.getByText("4 October 2026")).toHaveAttribute("datetime", "2026-10-04");
    expect(screen.getByRole("heading", { level: 2, name: "2. Accounts" })).toHaveAttribute("id", "two");

    // Sidebar and disclosure both render; CSS shows one.
    const tocs = screen.getAllByRole("navigation", { name: "On this page" });
    expect(tocs).toHaveLength(2);
    for (const toc of tocs) {
      expect(within(toc).getByRole("link", { name: "2. Accounts" })).toHaveAttribute("href", "#two");
      // Before any scrolling the first section is current.
      expect(within(toc).getByRole("link", { name: "1. Using Crichere" })).toHaveAttribute("aria-current", "location");
    }
  });
});
