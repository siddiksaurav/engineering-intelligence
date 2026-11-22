import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { Avatar, initialsOf } from "./Avatar";

describe("initialsOf", () => {
  it("shows the first two letters of a name in upper case", () => {
    expect(initialsOf("oliverguenther")).toBe("OL");
  });

  it("takes the first letter of each of the first two parts of a name with a separator", () => {
    expect(initialsOf("as-op")).toBe("AO");
    expect(initialsOf("Ada Lovelace King")).toBe("AL");
    expect(initialsOf("cguiot_OP")).toBe("CO");
  });

  it("gives one letter for a name of one letter and a question mark for no letters", () => {
    expect(initialsOf("x")).toBe("X");
    expect(initialsOf("--")).toBe("?");
  });
});

describe("Avatar", () => {
  it("hides the initials from a screen reader, which reads the name next to them", () => {
    expect(renderToStaticMarkup(<Avatar name="ada" />)).toMatch(/aria-hidden="true"[^>]*>AD</);
  });

  it("gives the same name the same tint every time", () => {
    expect(renderToStaticMarkup(<Avatar name="ada" />)).toBe(renderToStaticMarkup(<Avatar name="ada" />));
  });
});
