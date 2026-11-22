import type { EvidenceItem, PullRequestFields } from "@/lib/evidence";
import { ItemMeta } from "./ItemMeta";
import { SectionFrame } from "./SectionFrame";
import styles from "./developer.module.css";

type PullRequestGroup = { pullRequest: PullRequestFields; comments: EvidenceItem[] };

/** The Review comment written items, grouped per pull request in the order of the first comment. */
export function ReviewCommentSection({ items }: { items: EvidenceItem[] }) {
  const groups = groupPerPullRequest(items);
  return (
    <SectionFrame type="review_comment_written" title="Review comment written" count={items.length}>
      {groups.map(({ pullRequest, comments }) => (
        <li key={pullRequest.number} className={styles.card}>
          <h3 className={styles.cardTitle}>
            <a href={pullRequest.url}>
              Pull request #{pullRequest.number} {pullRequest.title}
            </a>
          </h3>
          <p className={styles.commentCount}>{comments.length === 1 ? "1 comment" : `${comments.length} comments`}</p>
          <ul className={styles.comments}>
            {comments.map((item) => (
              <li key={item.id} className={styles.comment}>
                <p className={styles.commentPath}>{item.fields.comment?.path}</p>
                <ItemMeta item={item} />
                <a href={item.fields.comment?.url ?? item.sourceUrl} className={styles.link}>
                  Comment on GitHub
                </a>
              </li>
            ))}
          </ul>
        </li>
      ))}
    </SectionFrame>
  );
}

function groupPerPullRequest(items: EvidenceItem[]): PullRequestGroup[] {
  const groups = new Map<number, PullRequestGroup>();
  for (const item of items) {
    const pullRequest = item.fields.pullRequest;
    if (!pullRequest) {
      throw new Error(`Review comment item ${item.id} has no pull request in its fields`);
    }
    const group = groups.get(pullRequest.number) ?? { pullRequest, comments: [] };
    group.comments.push(item);
    groups.set(pullRequest.number, group);
  }
  return [...groups.values()];
}
