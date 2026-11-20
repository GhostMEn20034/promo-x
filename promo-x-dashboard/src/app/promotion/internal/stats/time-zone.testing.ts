// Test-only: imported by specs, never by the app.

/** Sets the process's time zone (Node reads `TZ` again whenever it's set); returns what puts it back. */
export function setTimeZone(zone: string): () => void {
  const env = (globalThis as unknown as { process: { env: Record<string, string | undefined> } })
    .process.env;
  const previous = env['TZ'];
  env['TZ'] = zone;
  return () => {
    if (previous === undefined) {
      delete env['TZ'];
    } else {
      env['TZ'] = previous;
    }
  };
}
