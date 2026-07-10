// Dashed placeholder shown when a section has no data yet.
export function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex items-center justify-center rounded-xl border border-dashed border-border bg-card/40 px-6 py-10 text-center text-sm text-muted-foreground">
      {message}
    </div>
  );
}
