import { HeaderFigure, PageContent, PageHeader } from "@/components/Page";
import { Notice, Panel } from "@/components/Panel";
import { fetchBlindCheck, type BlindCheckNumbers } from "@/lib/blindCheck";
import { ConfusionTable } from "./ConfusionTable";
import { Disagreements } from "./Disagreements";
import { GetTheFile } from "./GetTheFile";
import { ProblemVerdicts } from "./ProblemVerdicts";
import { ReasonsToMark } from "./ReasonsToMark";
import { TargetNumbers } from "./TargetNumbers";
import styles from "./blindCheck.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

const CRUMBS = [{ href: "/", label: "Overview" }];

export default async function BlindCheckPage() {
  const result = await fetchBlindCheck(process.env.BACKEND_URL);
  if (result.status !== "ok") {
    return (
      <Page>
        <Notice>backend down</Notice>
      </Page>
    );
  }
  const numbers = result.data;
  return (
    <Page numbers={numbers}>
      <GetTheFile />
      {numbers.sampled > 0 && <Measurements numbers={numbers} />}
    </Page>
  );
}

function Page({ numbers, children }: { numbers?: BlindCheckNumbers; children: React.ReactNode }) {
  return (
    <main>
      <PageHeader
        crumbs={CRUMBS}
        title="Blind check"
        figures={
          numbers && (
            <>
              <HeaderFigure value={numbers.sampled} label="sampled" />
              <HeaderFigure value={numbers.labeled} label="labeled" />
              <HeaderFigure value={numbers.reasonMarked} label="reasons marked" />
            </>
          )
        }
      >
        Your complexity labels against the AI&apos;s, made without seeing its answer.
      </PageHeader>
      <PageContent>
        <div className={styles.stack}>{children}</div>
      </PageContent>
    </main>
  );
}

function Measurements({ numbers }: { numbers: BlindCheckNumbers }) {
  return (
    <>
      <TargetNumbers numbers={numbers} />
      <Panel title="Your label against the AI" note="Rows are your label, columns the AI's complexity.">
        <ConfusionTable confusion={numbers.confusion} />
      </Panel>
      <ProblemVerdicts numbers={numbers} />
      <ReasonsToMark items={numbers.reasonsToMark} />
      <Disagreements numbers={numbers} />
    </>
  );
}
