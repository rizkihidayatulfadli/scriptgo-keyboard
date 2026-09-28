// Content script: detects `/shortcut` while typing, shows template suggestions, and inserts the
// chosen template. Templates with variables open the form (form.html) in an iframe first.
// Nothing typed here leaves the browser: this script makes no network calls.

import { phoneFromWhatsAppId, type ChatHint } from "./engine/whatsapp";
import { localNow, parse, render, type RenderResult } from "./engine/template";
import { findTrigger, formatDuration, secondsSaved, type TriggerMatch } from "./engine/trigger";
import { extensionAllowed, KEY_PROFILE, KEY_SESSION, loadData, send, suggestTemplates, type LocalData, type Profile, type Template } from "./store";

let data: LocalData | null = null;
let signedIn = false;

/**
 * False once the extension was updated, reloaded, or removed: this copy of the script is then cut
 * off from storage and would keep using old settings (e.g. the previous trigger character). The
 * background worker injects the new copy into open tabs, so an orphan simply goes quiet.
 */
function alive(): boolean {
  try {
    return chrome.runtime?.id !== undefined;
  } catch {
    return false;
  }
}

/** The account's profile, for its plan: on the Free plan the extension offers no templates. */
let profile: Profile | undefined;

async function refresh(): Promise<void> {
  if (!alive()) return;
  data = await loadData();
  const stored = await chrome.storage.local.get([KEY_SESSION, KEY_PROFILE]);
  signedIn = !!stored[KEY_SESSION];
  profile = stored[KEY_PROFILE] as Profile | undefined;
}
void refresh();
chrome.storage.onChanged.addListener((_changes, area) => {
  if (area === "local") void refresh();
});

// --- Editors ---------------------------------------------------------------------------------

type Editor =
  | { kind: "field"; el: HTMLInputElement | HTMLTextAreaElement }
  | { kind: "rich"; el: HTMLElement };

const TEXT_INPUT_TYPES = new Set(["text", "search", "url", "email", "tel", ""]);

function editorFrom(target: EventTarget | null): Editor | null {
  if (target instanceof HTMLTextAreaElement) return { kind: "field", el: target };
  if (target instanceof HTMLInputElement) return TEXT_INPUT_TYPES.has(target.type) ? { kind: "field", el: target } : null;
  if (target instanceof HTMLElement && target.isContentEditable) {
    let root = target;
    while (root.parentElement?.isContentEditable) root = root.parentElement;
    return { kind: "rich", el: root };
  }
  return null;
}

/** Up to 64 characters before the caret, or null when there is a selection or no caret. */
function textBeforeCaret(editor: Editor): string | null {
  if (editor.kind === "field") {
    const { selectionStart: start, selectionEnd: end, value } = editor.el;
    if (start === null || start !== end) return null;
    return value.slice(Math.max(0, start - 64), start);
  }
  const sel = window.getSelection();
  if (!sel || !sel.isCollapsed || !sel.anchorNode || !editor.el.contains(sel.anchorNode)) return null;
  const node = sel.anchorNode;
  let text: string | null = null;
  if (node.nodeType === Node.TEXT_NODE) text = (node as Text).data.slice(0, sel.anchorOffset);
  else if (sel.anchorOffset > 0) {
    const before = node.childNodes[sel.anchorOffset - 1];
    if (before?.nodeType === Node.TEXT_NODE) text = (before as Text).data;
  }
  return text === null ? null : text.slice(-64);
}

function caretRect(editor: Editor): DOMRect {
  if (editor.kind === "rich") {
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const rect = sel.getRangeAt(0).getClientRects()[0];
      if (rect) return rect;
    }
  }
  return editor.el.getBoundingClientRect();
}

// --- Suggestions -----------------------------------------------------------------------------

interface Suggesting {
  editor: Editor;
  match: TriggerMatch;
  items: Template[];
  selected: number;
}

let suggesting: Suggesting | null = null;
/**
 * The trigger text the user closed with Escape (e.g. `#` when they meant a hashtag). That word gets
 * no suggestions while they keep typing or editing it; typing a new trigger suggests again.
 */
let dismissed: string | null = null;
let ui: { host: HTMLElement; list: HTMLElement } | null = null;

function ensureUi(): { host: HTMLElement; list: HTMLElement } {
  if (ui?.host.isConnected) return ui;
  const host = document.createElement("magic-keyboard-suggestions");
  host.style.cssText = "all: initial; position: fixed; z-index: 2147483647; display: none;";
  const shadow = host.attachShadow({ mode: "closed" });
  shadow.innerHTML = `
    <style>
      :host { --bg: #fff; --ink: #0e1116; --muted: #5b6675; --line: #e3e8ef; --field: #f5f7fa; --accent: #0284c7; --accent-ink: #0369a1; --accent-soft: #e0f2fe; }
      @media (prefers-color-scheme: dark) {
        :host { --bg: #1a2028; --ink: #eef2f6; --muted: #98a4b3; --line: #2a323d; --field: #0f1318; --accent: #38bdf8; --accent-ink: #7dd3fc; --accent-soft: #0c3350; }
      }
      .list { font: 500 14px/1.35 "Urbanist", system-ui, sans-serif; background: var(--bg); color: var(--ink); width: 330px;
        border-radius: 22px; box-shadow: 0 16px 40px rgba(14,17,22,.22), inset 0 0 0 1px var(--line); overflow: hidden; padding: 6px; }
      .item { padding: 10px 12px; cursor: pointer; display: grid; gap: 2px; border-radius: 16px; }
      .item.sel { background: var(--field); box-shadow: inset 0 0 0 1.5px var(--accent); }
      .top { display: flex; justify-content: space-between; gap: 8px; }
      .title { font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .preview { color: var(--muted); font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .hint { display: flex; gap: 12px; padding: 8px 12px 4px; color: var(--muted); font-size: 12px; }
      kbd { font: inherit; font-weight: 700; font-size: 11px; background: var(--field); color: var(--ink); border-radius: 6px; padding: 0 6px; }
      .signin { display: flex; gap: 10px; align-items: flex-start; padding: 12px; }
      .signin .lock { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 12px; background: var(--accent-soft); color: var(--accent-ink); flex: 0 0 auto; font-size: 16px; }
      .signin b { display: block; }
      .signin span { color: var(--muted); font-size: 12.5px; }
    </style>
    <div class="list" role="listbox"></div>`;
  const list = shadow.querySelector<HTMLElement>(".list")!;
  // Keep focus (and the caret) in the editor while clicking a suggestion.
  list.addEventListener("mousedown", (e) => e.preventDefault());
  document.documentElement.append(host);
  ui = { host, list };
  return ui;
}

function showSuggestions(state: Suggesting): void {
  suggesting = state;
  const { host, list } = ensureUi();
  list.replaceChildren(
    ...state.items.map((t, i) => {
      const item = document.createElement("div");
      item.className = i === state.selected ? "item sel" : "item";
      item.setAttribute("role", "option");
      const top = document.createElement("div");
      top.className = "top";
      const title = document.createElement("span");
      title.className = "title";
      title.textContent = t.title;
      top.append(title); // the name only: the user already typed the trigger and shortcut
      const preview = document.createElement("div");
      preview.className = "preview";
      preview.textContent = t.body.replace(/\s+/g, " ").slice(0, 90);
      item.append(top, preview);
      item.addEventListener("mouseenter", () => {
        if (suggesting) showSuggestions({ ...suggesting, selected: i });
      });
      item.addEventListener("click", () => void choose(i));
      return item;
    }),
  );
  const hint = document.createElement("div");
  hint.className = "hint";
  for (const [key, label] of [["↑↓", "pilih"], ["Enter", "sisipkan"], ["Esc", "tutup"]]) {
    const part = document.createElement("span");
    const kbd = document.createElement("kbd");
    kbd.textContent = key;
    part.append(kbd, ` ${label}`);
    hint.append(part);
  }
  list.append(hint);
  place(host, list, caretRect(state.editor));
}

/** Positions the popup below the caret, or above it when there is no room. */
function place(host: HTMLElement, list: HTMLElement, rect: DOMRect): void {
  host.style.display = "block";
  const height = list.offsetHeight;
  const width = list.offsetWidth;
  const below = rect.bottom + 6;
  const top = below + height > window.innerHeight && rect.top - height - 6 > 0 ? rect.top - height - 6 : below;
  host.style.top = `${Math.max(4, top)}px`;
  host.style.left = `${Math.max(4, Math.min(rect.left, window.innerWidth - width - 4))}px`;
}

/** Whether the hint to sign in was shown on this page; once is enough (mockup 6h). */
let signInHintShown = false;

function showSignInHint(editor: Editor): void {
  signInHintShown = true;
  suggesting = null;
  const { host, list } = ensureUi();
  const row = document.createElement("div");
  row.className = "signin";
  const lock = document.createElement("span");
  lock.className = "lock";
  lock.textContent = "🔒";
  const text = document.createElement("div");
  const title = document.createElement("b");
  title.textContent = "Masuk untuk pakai template";
  const body = document.createElement("span");
  body.textContent = "Klik ikon ScriptGo di toolbar Chrome, lalu masuk dengan Google atau email.";
  text.append(title, body);
  row.append(lock, text);
  list.replaceChildren(row);
  place(host, list, caretRect(editor));
  setTimeout(() => {
    if (!suggesting) hideSuggestions();
  }, 4000);
}

function hideSuggestions(): void {
  suggesting = null;
  if (ui) ui.host.style.display = "none";
}

function update(target: EventTarget | null): void {
  if (!alive()) {
    data = null;
    return hideSuggestions();
  }
  const editor = editorFrom(target);
  if (data?.settings.disabledSites.includes(location.hostname)) return hideSuggestions();
  if (!extensionAllowed(profile, Date.now())) return hideSuggestions();
  const trigger = data?.settings.trigger ?? "/";
  if (editor && data && !signedIn && data.templates.length === 0 && !signInHintShown) {
    const before = textBeforeCaret(editor);
    const match = before === null ? null : findTrigger(before, trigger);
    if (match && match.query.length >= 2) return showSignInHint(editor);
  }
  const before = editor && data?.templates.length ? textBeforeCaret(editor) : null;
  const match = before === null ? null : findTrigger(before, trigger);
  if (!editor || !match || !data) {
    dismissed = null;
    return hideSuggestions();
  }
  const key = trigger + match.query;
  if (dismissed !== null && !key.startsWith(dismissed) && !dismissed.startsWith(key)) dismissed = null;
  if (dismissed !== null) return hideSuggestions();
  const items = suggestTemplates(data, match.query);
  if (items.length === 0) return hideSuggestions();
  showSuggestions({ editor, match, items, selected: 0 });
}

document.addEventListener("input", (e) => update(e.target), true);
// Rich editors like Lexical (WhatsApp Web) handle some keystrokes themselves (the first character
// in an empty chat, Backspace) and fire no input event for them. The caret still moves, so look
// again on selectionchange, which fires after the editor has updated the text: otherwise `#` alone
// would show nothing and a deleted trigger would keep its list.
document.addEventListener("selectionchange", () => {
  const el = document.activeElement;
  if (el instanceof HTMLElement && el.isContentEditable) update(el);
});
document.addEventListener("focusout", () => hideSuggestions(), true);
window.addEventListener("resize", () => hideSuggestions());
document.addEventListener("scroll", () => hideSuggestions(), true);

// Capture phase on window runs before the page's own handlers (e.g. Enter to send in WhatsApp).
window.addEventListener(
  "keydown",
  (e) => {
    if (!suggesting || e.isComposing) return;
    if (!alive()) return hideSuggestions();
    const count = suggesting.items.length;
    let handled = true;
    switch (e.key) {
      case "ArrowDown":
        showSuggestions({ ...suggesting, selected: (suggesting.selected + 1) % count });
        break;
      case "ArrowUp":
        showSuggestions({ ...suggesting, selected: (suggesting.selected - 1 + count) % count });
        break;
      case "Enter":
      case "Tab":
        if (e.shiftKey) handled = false;
        else void choose(suggesting.selected);
        break;
      case "Escape":
        dismissed = (data?.settings.trigger ?? "/") + suggesting.match.query;
        hideSuggestions();
        break;
      default:
        handled = false;
    }
    if (handled) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  },
  true,
);

// --- Choosing and inserting ------------------------------------------------------------------

async function choose(index: number): Promise<void> {
  const state = suggesting;
  if (!state) return;
  hideSuggestions();
  const template = state.items[index];
  const parsed = parse(template.body);
  if (parsed.variables.length === 0) {
    await insert(state.editor, null, state.match.length, render(parsed, null, {}, localNow()), template.id);
    return;
  }
  const saved = saveCaret(state.editor);
  const result = await openForm(template.id);
  if (result) await insert(state.editor, saved, state.match.length, result, template.id);
  else restoreCaret(state.editor, saved);
}

function saveCaret(editor: Editor): Range | null {
  if (editor.kind === "field") return null; // inputs keep their selection while unfocused
  const sel = window.getSelection();
  return sel && sel.rangeCount > 0 ? sel.getRangeAt(0).cloneRange() : null;
}

function restoreCaret(editor: Editor, saved: Range | null): void {
  editor.el.focus();
  if (saved && editor.kind === "rich") {
    const sel = window.getSelection();
    sel?.removeAllRanges();
    sel?.addRange(saved);
  }
}

/**
 * Tells the page's editor about a selection we changed. Browsers fire selectionchange
 * asynchronously, and editors like Lexical (WhatsApp Web) only read the DOM selection then, so
 * without this a paste lands at the old caret. Dispatching it ourselves keeps insertion synchronous.
 */
function announceSelection(): void {
  document.dispatchEvent(new Event("selectionchange"));
}

/** Waits for editors that apply changes in a microtask (Lexical) to update the DOM. */
async function afterEditorUpdate(): Promise<void> {
  for (let i = 0; i < 3; i++) await new Promise<void>((resolve) => queueMicrotask(resolve));
}

const segmenter = new Intl.Segmenter();
const graphemes = (s: string) => [...segmenter.segment(s)].length;

/** Replaces the trigger (the `deleteLength` characters before the caret) with the rendered template. */
async function insert(
  editor: Editor,
  saved: Range | null,
  deleteLength: number,
  result: RenderResult,
  templateId: string,
): Promise<void> {
  const { text, cursor } = result;
  if (editor.kind === "field") {
    const el = editor.el;
    el.focus();
    const end = el.selectionEnd ?? el.value.length;
    const start = Math.max(0, end - deleteLength);
    el.setSelectionRange(start, end);
    // execCommand keeps the page's undo history and framework state (React etc.) in sync.
    if (!document.execCommand("insertText", false, text)) {
      el.setRangeText(text, start, end, "end");
      el.dispatchEvent(new Event("input", { bubbles: true }));
    }
    if (cursor !== null) el.setSelectionRange(start + cursor, start + cursor);
  } else {
    restoreCaret(editor, saved);
    const sel = window.getSelection();
    if (!sel) return;
    for (let i = 0; i < deleteLength; i++) sel.modify("extend", "backward", "character");
    announceSelection();
    // Rich editors (WhatsApp's Lexical, X's Draft.js) handle paste, including line breaks, reliably.
    // Plain contenteditable ignores a synthetic paste, so fall back to insertText.
    if (!dispatchPaste(sel, editor.el, text)) document.execCommand("insertText", false, text);
    if (cursor !== null) {
      await afterEditorUpdate();
      const back = graphemes(text.slice(cursor));
      for (let i = 0; i < back; i++) window.getSelection()?.modify("move", "backward", "character");
    }
  }
  void send({ type: "recordUse", templateId, chars: text.length }).catch(() => undefined);
  showToast(`Tersisip · hemat ${formatDuration(secondsSaved(text.length))}`);
}

/** Small confirmation at the bottom of the page that fades by itself (mockup 6f). */
function showToast(message: string): void {
  const host = document.createElement("magic-keyboard-toast");
  host.style.cssText = "all: initial; position: fixed; left: 50%; bottom: 84px; transform: translateX(-50%); z-index: 2147483647;";
  const shadow = host.attachShadow({ mode: "closed" });
  shadow.innerHTML = `
    <style>
      .toast { font: 700 14px "Urbanist", system-ui, sans-serif; display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px;
        border-radius: 999px; background: #0e1116; color: #fff; box-shadow: 0 10px 24px rgba(14,17,22,.25);
        animation: in .2s ease-out, out .3s ease-in 2.2s forwards; white-space: nowrap; }
      .check { color: #0ea5e9; }
      @keyframes in { from { opacity: 0; transform: translateY(8px); } }
      @keyframes out { to { opacity: 0; transform: translateY(8px); } }
      @media (prefers-color-scheme: dark) { .toast { background: #eef2f6; color: #0e1116; } .check { color: #0369a1; } }
      @media (prefers-reduced-motion: reduce) { .toast { animation: out 0s linear 2.5s forwards; } }
    </style>
    <div class="toast" role="status"><span class="check">✓</span><span></span></div>`;
  shadow.querySelector(".toast span:last-child")!.textContent = message;
  document.documentElement.append(host);
  setTimeout(() => host.remove(), 2700);
}

function dispatchPaste(sel: Selection, fallback: HTMLElement, text: string): boolean {
  const target = sel.anchorNode instanceof Element ? sel.anchorNode : (sel.anchorNode?.parentElement ?? fallback);
  const clipboardData = new DataTransfer();
  clipboardData.setData("text/plain", text);
  const event = new ClipboardEvent("paste", { clipboardData, bubbles: true, cancelable: true, composed: true });
  target.dispatchEvent(event);
  return event.defaultPrevented;
}

// --- Variable form ---------------------------------------------------------------------------

/**
 * Shows form.html in an iframe over the page and resolves with the rendered text, or null when
 * cancelled. The iframe keeps the page's keyboard shortcuts away from the form's inputs.
 */
async function openForm(templateId: string): Promise<RenderResult | null> {
  const nonce = crypto.randomUUID();
  const opened = await send({ type: "openForm", nonce, request: { templateId, hint: chatHint() } }).catch(() => null);
  if (!opened?.ok) return null;

  const frame = document.createElement("iframe");
  frame.src = chrome.runtime.getURL(`form.html#${nonce}`);
  frame.style.cssText =
    "all: initial; position: fixed; inset: 0; width: 100vw; height: 100vh; border: 0; z-index: 2147483647; color-scheme: normal;";
  frame.setAttribute("allowtransparency", "true");
  document.documentElement.append(frame);

  return new Promise((resolve) => {
    const channel = new MessageChannel();
    channel.port1.onmessage = (e: MessageEvent) => {
      const msg = e.data as { type: "done"; text: string; cursor: number | null } | { type: "cancel" };
      channel.port1.close();
      frame.remove();
      resolve(msg.type === "done" ? { text: msg.text, cursor: msg.cursor } : null);
    };
    frame.addEventListener(
      "load",
      () => {
        // Only the extension's own page can receive the port.
        frame.contentWindow?.postMessage({ type: "magic-keyboard-port" }, new URL(frame.src).origin, [channel.port2]);
      },
      { once: true },
    );
  });
}

/** Who the open WhatsApp Web chat is with, used to suggest a contact. Null on other sites. */
function chatHint(): ChatHint | null {
  if (location.hostname !== "web.whatsapp.com") return null;
  const header = document.querySelector("#main header");
  const title =
    header?.querySelector("span[dir='auto']")?.textContent?.trim() ||
    header?.querySelector("[title]")?.getAttribute("title")?.trim() ||
    null;
  let phone: string | null = null;
  const ids = document.querySelectorAll("#main [data-id]");
  for (let i = ids.length - 1; i >= 0 && !phone; i--) phone = phoneFromWhatsAppId(ids[i].getAttribute("data-id") ?? "");
  return { title, phone };
}
