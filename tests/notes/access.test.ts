import { setupTeamWithSubmittedLog, makeLeadWithTeam } from "../util/team";
import { addNote, getDevNotes } from "@/lib/team";

test("lead can add and read a private note about a team developer", async () => {
  const { leadSb, leadId, devId } = await setupTeamWithSubmittedLog();

  const note = await addNote(leadSb, devId, "strong week — owned the Kafka work");
  expect(note.body).toBe("strong week — owned the Kafka work");
  expect(note.author_id).toBe(leadId);
  expect(note.developer_id).toBe(devId);

  const notes = await getDevNotes(leadSb, devId);
  expect(notes.some((n) => n.id === note.id)).toBe(true);
});

test("a lead of a different team sees no notes about the developer", async () => {
  const { leadSb, devId } = await setupTeamWithSubmittedLog();
  await addNote(leadSb, devId, "confidential");

  const outsider = await makeLeadWithTeam("outsider");
  const notes = await getDevNotes(outsider.leadSb, devId);
  expect(notes.length).toBe(0);
});

test("the developer can never read notes written about themselves", async () => {
  const { leadSb, devSb, devId } = await setupTeamWithSubmittedLog();
  await addNote(leadSb, devId, "hidden from the developer");

  const notes = await getDevNotes(devSb, devId);
  expect(notes.length).toBe(0);

  const { data } = await devSb.from("dev_notes").select("*").eq("developer_id", devId);
  expect(data?.length ?? 0).toBe(0);
});
