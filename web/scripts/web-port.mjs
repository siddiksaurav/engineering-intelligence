const DEFAULT_PORT = "3000";
const HIGHEST_PORT = 65535;

/**
 * The port the web app listens on: WEB_PORT, or 3000 when it is not set.
 *
 * @param {Record<string, string | undefined>} env
 * @returns {string}
 */
export function webPort(env) {
  const value = env.WEB_PORT;
  if (!value) {
    return DEFAULT_PORT;
  }
  const port = /^[0-9]+$/.test(value) ? Number(value) : Number.NaN;
  if (!(port >= 1 && port <= HIGHEST_PORT)) {
    throw new Error(`WEB_PORT must be a port number from 1 to ${HIGHEST_PORT}, got ${JSON.stringify(value)}`);
  }
  return value;
}
