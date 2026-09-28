// Port of engine/.../template/Trigger.kt and TimeSaved.kt.

/** A trigger being typed right before the cursor, e.g. `/ong`. */
export interface TriggerMatch {
  /** Text after the trigger character, possibly empty. */
  query: string;
  /** How many characters before the cursor the trigger occupies (trigger character included). */
  length: number;
}

export const DEFAULT_TRIGGER = "/";
export const MAX_QUERY_LENGTH = 30;

const QUERY_CHAR = /[\p{L}\p{Nd}_-]/u;
const WHITESPACE = /\s/;

/**
 * Finds a trigger at the end of `textBeforeCursor`. The trigger must start the text or follow
 * whitespace, so `http://`, `dan/atau`, and `1/2` do not count.
 */
export function findTrigger(textBeforeCursor: string, trigger: string = DEFAULT_TRIGGER): TriggerMatch | null {
  let i = textBeforeCursor.length - 1;
  while (i >= 0 && QUERY_CHAR.test(textBeforeCursor[i])) i--;
  if (i < 0 || textBeforeCursor[i] !== trigger) return null;
  if (i > 0 && !WHITESPACE.test(textBeforeCursor[i - 1])) return null;
  const query = textBeforeCursor.slice(i + 1);
  if (query.length > MAX_QUERY_LENGTH) return null;
  return { query, length: query.length + 1 };
}

/** What the suggestion list needs to rank a template. */
export interface TemplateEntry {
  id: string;
  shortcut: string;
  title: string;
  usageCount: number;
}

/**
 * Templates matching `query`, best first. Shortcut prefix beats a word prefix in the title, which
 * beats a substring anywhere, which beats the query's letters appearing in order in the shortcut.
 * Ties go to the more used template. An empty query lists everything by usage.
 */
export function rankTemplates<T extends TemplateEntry>(query: string, templates: readonly T[], limit = 10): T[] {
  const q = query.toLowerCase();
  return templates
    .map((t) => ({ t, score: score(q, t) }))
    .filter((x): x is { t: T; score: number } => x.score !== null)
    .sort(
      (a, b) =>
        a.score - b.score ||
        b.t.usageCount - a.t.usageCount ||
        (a.t.shortcut < b.t.shortcut ? -1 : a.t.shortcut > b.t.shortcut ? 1 : 0),
    )
    .slice(0, limit)
    .map((x) => x.t);
}

function score(q: string, t: TemplateEntry): number | null {
  if (!q) return 0;
  const shortcut = t.shortcut.toLowerCase();
  const title = t.title.toLowerCase();
  if (shortcut === q) return 0;
  if (shortcut.startsWith(q)) return 1;
  if (title.split(/[ \-_]/).some((w) => w.startsWith(q))) return 2;
  if (shortcut.includes(q) || title.includes(q)) return 3;
  if (isSubsequence(q, shortcut)) return 4;
  return null;
}

function isSubsequence(q: string, s: string): boolean {
  let j = 0;
  for (const c of s) if (j < q.length && c === q[j]) j++;
  return j === q.length;
}

/** Roughly 36 words per minute; the same assumption as the keyboard. */
export const DEFAULT_CHARS_PER_SECOND = 3.0;

export function secondsSaved(charsInserted: number, charsPerSecond = DEFAULT_CHARS_PER_SECOND): number {
  return charsInserted <= 0 || charsPerSecond <= 0 ? 0 : Math.round(charsInserted / charsPerSecond);
}

/** Indonesian duration: "45 detik", "12 menit", "3 jam 20 menit". */
export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds} detik`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} menit`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} jam` : `${hours} jam ${rest} menit`;
}
