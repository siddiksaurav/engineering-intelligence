import { describe, expect, it } from "vitest";
import { sortedByName } from "./people";

describe("sortedByName", () => {
  it("orders by display name and leaves the given list as it was", () => {
    const people = [{ displayName: "grace" }, { displayName: "ada" }];

    expect(sortedByName(people)).toEqual([{ displayName: "ada" }, { displayName: "grace" }]);
    expect(people[0]).toEqual({ displayName: "grace" });
  });
});
