import type { EvidenceItem, PullRequestFields } from "@/lib/evidence";
import { ItemMeta } from "./ItemMeta";
import styles from "./developer.module.css";

type PullRequestGroup = { pullRequest: PullRequestFields; comments: EvidenceItem[] };

/** The Review comment written items, grouped per pull request in the order of the first comment. */
export function ReviewCommentSection({ items }: { items: EvidenceItem[] }) {
  const groups = groupPerPullRequest(items);
  return (
    <section className={`${styles.section} ${styles.reviewCommentWritten}`}>
      <h2 className={styles.sectionTitle}>Review comment written</h2>
      {groups.length === 0 ? (
        <p className={styles.none}>None</p>
      ) : (
        <ul className={styles.items}>
          {groups.map(({ pullRequest, comments }) => (
            <li key={pullRequest.number} className={styles.pullRequestGroup}>
              <h3 className={styles.groupTitle}>
                <a href={pullRequest.url}>
                  Pull request #{pullRequest.number} {pullRequest.title}
                </a>
              </h3>
              <p className={styles.commentCount}>
                {comments.length === 1 ? "1 comment" : `${comments.length} comments`}
              </p>
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
        </ul>
      )}
    </section>
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
