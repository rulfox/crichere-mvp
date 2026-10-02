import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { GetAppPopover } from "./GetAppPopover";
import { StoreBadge } from "./StoreBadge";

describe("StoreBadge", () => {
  it("links to the store, in a new tab without an opener, once a listing URL exists", () => {
    render(<StoreBadge kind="play" url="https://play.google.com/store/apps/details?id=com.crichere.app" />);

    const link = screen.getByRole("link", { name: "Get it on Google Play" });
    expect(link).toHaveAttribute("href", "https://play.google.com/store/apps/details?id=com.crichere.app");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("renders the coming-soon variant, not a link, while the app isn't published", () => {
    render(
      <>
        <StoreBadge kind="play" url={null} />
        <StoreBadge kind="appstore" url={null} />
      </>,
    );

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Google Play, coming soon" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "App Store, coming soon" })).toHaveTextContent("iOS SOON");
  });

  it("renders no QR card until a QR image exists", () => {
    const { container } = render(<StoreBadge kind="qr" qrSrc={null} />);

    expect(container).toBeEmptyDOMElement();
  });
});

describe("GetAppPopover", () => {
  it("opens with both badges and closes on Escape", () => {
    render(<GetAppPopover />);
    const button = screen.getByRole("button", { name: "Get the app" });

    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("dialog", { name: "Get the Crichere app" })).toBeInTheDocument();

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
