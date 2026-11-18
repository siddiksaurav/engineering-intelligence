import { describe, expect, it } from "vitest";
import { webPort } from "./web-port.mjs";

describe("webPort", () => {
  it("is 3000 when WEB_PORT is not set", () => {
    expect(webPort({})).toBe("3000");
    expect(webPort({ WEB_PORT: "" })).toBe("3000");
  });

  it("is the value of WEB_PORT", () => {
    expect(webPort({ WEB_PORT: "3100" })).toBe("3100");
  });

  it.each(["abc", "0", "65536", "30.5", "-1", " 3000"])("refuses %j and names the variable", (value) => {
    expect(() => webPort({ WEB_PORT: value })).toThrow(
      `WEB_PORT must be a port number from 1 to 65535, got "${value}"`,
    );
  });
});
