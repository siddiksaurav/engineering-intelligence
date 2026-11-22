import Link from "next/link";
import { SectionLinks } from "./SectionLinks";
import styles from "./AppBar.module.css";

/** The bar above every page: the product mark and the links to the two sections. */
export function AppBar() {
  return (
    <header className={styles.bar}>
      <div className={styles.inner}>
        <Link href="/" className={styles.brand}>
          <ProductMark />
          <span className={styles.name}>Engineering Intelligence</span>
          <span className={styles.stage}>PoC</span>
        </Link>
        <SectionLinks />
      </div>
    </header>
  );
}

/** Three rising steps, the tiers T1 to T3 that every judgment ends in. */
function ProductMark() {
  return (
    <svg className={styles.mark} viewBox="0 0 28 28" aria-hidden="true">
      <rect x="2" y="17" width="7" height="9" rx="2" fill="var(--sky-300)" />
      <rect x="10.5" y="10" width="7" height="16" rx="2" fill="var(--azure-500)" />
      <rect x="19" y="3" width="7" height="23" rx="2" fill="var(--sun)" />
    </svg>
  );
}
