import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "./button";

describe("Button", () => {
  it("renders an accessible button", () => {
    render(<Button>Apply now</Button>);
    expect(screen.getByRole("button", { name: "Apply now" })).toBeDefined();
  });
});
