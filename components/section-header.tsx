// Section heading with an accent bar + mono eyebrow above the title — the
// telemetry pattern used to open every dashboard section.
export function SectionHeader({
  eyebrow,
  title,
}: {
  eyebrow: string;
  title: string;
}) {
  return (
    <div className="mb-5 flex items-center gap-3">
      <span aria-hidden className="h-4 w-1 rounded-full bg-primary" />
      <div>
        <p className="eyebrow leading-none">{eyebrow}</p>
        <h2 className="mt-1 text-lg font-semibold tracking-tight text-foreground">
          {title}
        </h2>
      </div>
    </div>
  );
}
