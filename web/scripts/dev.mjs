// Starts the Next.js dev server on WEB_PORT. package.json runs this file with
// `node --env-file-if-exists=../.env`, so the .env at the repository root is loaded and a value
// already set in the environment wins.
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { webPort } from "./web-port.mjs";

const port = webPort(process.env);
const next = fileURLToPath(new URL("../node_modules/next/dist/bin/next", import.meta.url));

const child = spawn(process.execPath, [next, "dev", "-p", port], { stdio: "inherit" });
child.on("exit", (code, signal) => process.exit(code ?? (signal ? 1 : 0)));
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => child.kill(signal));
}
