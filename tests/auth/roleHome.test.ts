import { roleHome } from "@/lib/auth";

test("role home routes", () => {
  expect(roleHome("developer")).toBe("/today");
  expect(roleHome("lead")).toBe("/team");
  expect(roleHome("manager")).toBe("/org");
});
