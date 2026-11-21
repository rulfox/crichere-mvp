import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ChartPanel, Funnel, Heat, LineChart } from "@/components/charts";
import { Dialog, Status, Words } from "@/components/ui";

describe("charts", () => {
  it("funnel shows each step against the one before", () => {
    render(
      <Funnel
        rows={[
          { key: "Code sent", value: 200 },
          { key: "Signed in", value: 150 },
        ]}
      />,
    );
    expect(screen.getByText("Everyone who asked for a code")).toBeInTheDocument();
    expect(screen.getByText("75% of previous step")).toBeInTheDocument();
  });

  it("every chart can switch to a table", () => {
    render(
      <ChartPanel title="New users a day" table={{ head: ["Day", "New users"], rows: [["21 Nov", 4]] }}>
        <span>chart</span>
      </ChartPanel>,
    );
    expect(screen.getByText("chart")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /table/i }));
    expect(screen.getByRole("cell", { name: "21 Nov" })).toBeInTheDocument();
    expect(screen.queryByText("chart")).not.toBeInTheDocument();
  });

  it("line chart labels its series for screen readers", () => {
    render(<LineChart days={["2026-11-20", "2026-11-21"]} series={[{ label: "New users", color: "red", values: [1, 2] }]} />);
    expect(screen.getByRole("img", { name: "New users" })).toBeInTheDocument();
  });

  it("retention leaves future weeks empty", () => {
    const { container } = render(<Heat cohorts={[{ label: "16 Nov", pct: [100, null, null, null, null, null] }]} />);
    expect(container.querySelectorAll(".hc.empty")).toHaveLength(5);
    expect(screen.getByText("100%")).toBeInTheDocument();
  });
});

describe("ui", () => {
  it("summary sentences bold only the marked parts", () => {
    const { container } = render(<Words parts={[{ t: "3 open", b: true }, { t: " now." }]} />);
    expect(container.querySelector("b")?.textContent).toBe("3 open");
  });

  it("dialog closes on Escape and focuses its first field", () => {
    const onClose = vi.fn();
    render(
      <Dialog title="Suspend?" onClose={onClose} actions={<button>Cancel</button>}>
        <textarea aria-label="note" />
      </Dialog>,
    );
    expect(screen.getByLabelText("note")).toHaveFocus();
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });

  it("status chips carry their tone", () => {
    const { container } = render(<Status tone="danger">Suspended</Status>);
    expect(container.querySelector(".st.danger")).toHaveTextContent("Suspended");
  });
});
