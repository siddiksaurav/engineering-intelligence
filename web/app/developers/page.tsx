import Link from "next/link";
import { fetchDevelopers } from "@/lib/evidence";
import { CountBadges } from "./CountBadges";
import styles from "./developers.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

export default async function DevelopersPage() {
  const result = await fetchDevelopers(process.env.BACKEND_URL);
  return (
    <main>
      <header className={styles.header}>
        <Link href="/" className={styles.back}>
          Home
        </Link>
        <h1>Developers</h1>
      </header>
      <div className={styles.content}>
        {result.status !== "ok" ? (
          <p className={styles.notice}>backend down</p>
        ) : result.data.length === 0 ? (
          <p className={styles.notice}>No developers</p>
        ) : (
          <ul className={styles.developerList}>
            {result.data.map((developer) => (
              <li key={developer.personId}>
                <div className={styles.developerCard}>
                  <Link href={`/developers/${developer.personId}/profile`} className={styles.developerName}>
                    {developer.displayName}
                  </Link>
                  <CountBadges counts={developer.counts} />
                  <Link href={`/developers/${developer.personId}`}>Evidence list</Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
