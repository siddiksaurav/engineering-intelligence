import { COMPLEXITIES, type BlindCheckNumbers } from "@/lib/blindCheck";
import { capitalised, spaced } from "@/lib/words";
import styles from "./blindCheck.module.css";

/** Rows are the user's label, columns the AI's complexity; a pair that never happened is a zero. */
export function ConfusionTable({ confusion }: { confusion: BlindCheckNumbers["confusion"] }) {
  const countOf = (label: string, ai: string) =>
    confusion.find((pair) => pair.label === label && pair.ai === ai)?.count ?? 0;
  return (
    <div className={styles.scroll}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">Your label</th>
            {COMPLEXITIES.map((ai) => (
              <th key={ai} scope="col">
                {`AI ${spaced(ai)}`}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {COMPLEXITIES.map((label) => (
            <tr key={label}>
              <th scope="row">{capitalised(spaced(label))}</th>
              {COMPLEXITIES.map((ai) => (
                <td key={ai} data-diagonal={label === ai}>
                  {countOf(label, ai)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
