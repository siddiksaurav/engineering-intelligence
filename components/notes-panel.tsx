"use client";

import * as React from "react";
import { PencilIcon, Trash2Icon } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import {
  addNoteAction,
  deleteNoteAction,
  listNotesAction,
  updateNoteAction,
} from "@/app/actions/notes";
import type { DevNote, Profile } from "@/lib/types";

// Private, running notes about a developer — visible only to leads/managers, never
// to the developer. Pick a developer, then add / edit / delete notes.
export function NotesPanel({ developers }: { developers: Profile[] }) {
  const [devId, setDevId] = React.useState<string>("");
  const [notes, setNotes] = React.useState<DevNote[]>([]);
  const [draft, setDraft] = React.useState("");
  const [editingId, setEditingId] = React.useState<string | null>(null);
  const [editingText, setEditingText] = React.useState("");
  const [pending, startTransition] = React.useTransition();

  const devLabel = (id: string) => {
    const d = developers.find((x) => x.id === id);
    return d ? d.full_name || d.email : "Select a developer";
  };

  function selectDev(id: string) {
    setDevId(id);
    setNotes([]);
    setEditingId(null);
    startTransition(async () => {
      setNotes(await listNotesAction(id));
    });
  }

  function add() {
    const body = draft.trim();
    if (!body || !devId) return;
    startTransition(async () => {
      const note = await addNoteAction(devId, body);
      setNotes((prev) => [note, ...prev]);
      setDraft("");
    });
  }

  function beginEdit(note: DevNote) {
    setEditingId(note.id);
    setEditingText(note.body);
  }

  function saveEdit(id: string) {
    const body = editingText.trim();
    if (!body) return;
    startTransition(async () => {
      await updateNoteAction(id, body);
      setNotes((prev) =>
        prev.map((n) =>
          n.id === id
            ? { ...n, body, updated_at: new Date().toISOString() }
            : n
        )
      );
      setEditingId(null);
    });
  }

  function remove(id: string) {
    startTransition(async () => {
      await deleteNoteAction(id);
      setNotes((prev) => prev.filter((n) => n.id !== id));
    });
  }

  const fmt = (iso: string) =>
    new Date(iso).toLocaleDateString(undefined, {
      month: "short",
      day: "numeric",
      year: "numeric",
    });

  return (
    <div className="flex flex-col gap-3">
      <Select value={devId} onValueChange={(v) => v && selectDev(v)}>
        <SelectTrigger className="w-full" aria-label="Developer">
          <SelectValue placeholder="Select a developer">
            {(v) => devLabel(v as string)}
          </SelectValue>
        </SelectTrigger>
        <SelectContent>
          {developers.map((d) => (
            <SelectItem key={d.id} value={d.id}>
              {d.full_name || d.email}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {devId && (
        <>
          <div className="flex flex-col gap-2">
            <Textarea
              placeholder="Add a private note…"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              aria-label="New note"
            />
            <div>
              <Button
                type="button"
                size="sm"
                onClick={add}
                disabled={pending || !draft.trim()}
              >
                Add note
              </Button>
            </div>
          </div>

          {notes.length === 0 ? (
            <p className="text-sm text-muted-foreground">No notes yet.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {notes.map((note) => (
                <li
                  key={note.id}
                  className="rounded-lg border border-border bg-card p-3"
                >
                  {editingId === note.id ? (
                    <div className="flex flex-col gap-2">
                      <Textarea
                        value={editingText}
                        onChange={(e) => setEditingText(e.target.value)}
                        aria-label="Edit note"
                      />
                      <div className="flex gap-2">
                        <Button
                          type="button"
                          size="sm"
                          onClick={() => saveEdit(note.id)}
                          disabled={pending || !editingText.trim()}
                        >
                          Save
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => setEditingId(null)}
                        >
                          Cancel
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <div className="flex items-start justify-between gap-2">
                        <p className="whitespace-pre-wrap text-sm">{note.body}</p>
                        <div className="flex shrink-0 gap-1">
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="text-muted-foreground"
                            aria-label="Edit note"
                            onClick={() => beginEdit(note)}
                          >
                            <PencilIcon className="size-4" />
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="text-muted-foreground"
                            aria-label="Delete note"
                            onClick={() => remove(note.id)}
                          >
                            <Trash2Icon className="size-4" />
                          </Button>
                        </div>
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {fmt(note.created_at)}
                        {note.updated_at !== note.created_at && " · edited"}
                      </p>
                    </>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
