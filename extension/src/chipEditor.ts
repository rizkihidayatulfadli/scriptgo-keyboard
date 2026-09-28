// The template body editor on the manage page: variables show as chips instead of {braces}.
// The value is always template source (docs/template-syntax.md); each chip is a view of one `{...}`
// piece from sourceSegments and keeps that source in data-raw, so text round-trips unchanged.

import { sourceSegments, type SourceSegment } from "./engine/template";
import { h } from "./ui";

export type VarSegment = Extract<SourceSegment, { kind: "var" }>;

export interface ChipEditorOptions {
  initial: string;
  ariaLabel: string;
  placeholder: string;
  /** The chip's text, e.g. "Kota". */
  label: (segment: VarSegment) => string;
  /** A chip was clicked; call `replace` with new source for it, or null to remove it. */
  onChipClick: (chip: HTMLElement, segment: VarSegment, replace: (raw: string | null) => void) => void;
  onChange: (value: string) => void;
}

export interface ChipEditor {
  el: HTMLElement;
  value(): string;
  /** Inserts template source (e.g. `{kota|Bandung}`) where the caret was last, or at the end. */
  insert(raw: string): void;
  /** Replaces the whole text (e.g. with a template from AI). */
  set(raw: string): void;
  focus(): void;
}

/** The source text of a node's children: text as is, chips by their data-raw, line breaks as "\n". */
function serialize(root: Node): string {
  let out = "";
  root.childNodes.forEach((n) => {
    if (n.nodeType === Node.TEXT_NODE) out += (n.textContent ?? "").replace(/ /g, " ");
    else if (n instanceof HTMLElement) {
      if (n.dataset.raw !== undefined) out += n.dataset.raw;
      else if (n.tagName === "BR") out += "\n";
      else {
        // A block the browser added (Enter in some cases): it starts a new line.
        if (out && !out.endsWith("\n")) out += "\n";
        out += serialize(n);
      }
    }
  });
  return out;
}

/** A short hint after the chip label: its fallback, or its choices. */
function detail(segment: VarSegment): string {
  if (segment.options?.length) return segment.options.join(", ");
  return segment.default ?? "";
}

export function chipEditor(opts: ChipEditorOptions): ChipEditor {
  const root = h("div", {
    class: "chip-editor",
    contenteditable: "plaintext-only",
    role: "textbox",
    "aria-multiline": "true",
    "aria-label": opts.ariaLabel,
    "data-placeholder": opts.placeholder,
    spellcheck: "false",
  });
  let value = opts.initial;
  /** Caret position in `value`, remembered while the editor has no focus (e.g. a popover is open). */
  let lastCaret: number | null = null;
  let composing = false;

  const chip = (segment: VarSegment) => {
    const hint = detail(segment);
    const el = h(
      "span",
      { class: `var-chip${segment.builtin ? " builtin" : ""}`, contenteditable: "false", "data-raw": segment.raw, title: "Klik untuk mengatur" },
      opts.label(segment),
      ...(hint ? [h("small", {}, hint)] : []),
    );
    // Keep the caret where it is; the click opens the chip's settings.
    el.addEventListener("mousedown", (e) => e.preventDefault());
    el.addEventListener("click", () => opts.onChipClick(el, segment, (raw) => replaceChip(el, raw)));
    return el;
  };

  const render = () => {
    const nodes: Node[] = sourceSegments(value).map((s) => (s.kind === "text" ? document.createTextNode(s.raw) : chip(s)));
    // A trailing line break needs something after it to show; this counts as no text.
    nodes.push(h("br", { "data-raw": "" }));
    root.replaceChildren(...nodes);
    root.classList.toggle("is-empty", value === "");
  };

  /** The position in `value` of a point inside the editor. */
  const offsetOf = (node: Node, offset: number) => {
    const range = document.createRange();
    range.setStart(root, 0);
    range.setEnd(node, offset);
    const box = document.createElement("div");
    box.append(range.cloneContents());
    return serialize(box).length;
  };

  const caret = (): number | null => {
    const sel = document.getSelection();
    if (!sel || !sel.rangeCount || !root.contains(sel.focusNode)) return null;
    return offsetOf(sel.focusNode!, sel.focusOffset);
  };

  /** Puts the caret at a position in `value`; a position inside a chip's source goes after the chip. */
  const setCaret = (offset: number) => {
    const sel = document.getSelection();
    if (!sel) return;
    let left = offset;
    const nodes = Array.from(root.childNodes);
    for (let i = 0; i < nodes.length; i++) {
      const n = nodes[i];
      if (n.nodeType === Node.TEXT_NODE) {
        const length = (n.textContent ?? "").length;
        if (left <= length) return void sel.collapse(n, left);
        left -= length;
      } else {
        const length = ((n as HTMLElement).dataset.raw ?? "").length;
        if (left === 0) return void sel.collapse(root, i);
        if (left <= length) return void sel.collapse(root, i + 1);
        left -= length;
      }
    }
    sel.collapse(root, Math.max(0, nodes.length - 1));
  };

  const update = (next: string, caretAt: number | null) => {
    value = next;
    render();
    if (caretAt !== null && document.activeElement === root) setCaret(caretAt);
    lastCaret = caretAt;
    opts.onChange(value);
  };

  const splice = (at: number, removed: number, text: string) => {
    update(value.slice(0, at) + text + value.slice(at + removed), at + text.length);
  };

  const replaceChip = (el: HTMLElement, raw: string | null) => {
    const start = offsetOf(root, Array.from(root.childNodes).indexOf(el));
    root.focus();
    splice(start, (el.dataset.raw ?? "").length, raw ?? "");
  };

  root.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.isComposing) {
      e.preventDefault();
      const at = caret() ?? value.length;
      const sel = document.getSelection();
      const end = sel && root.contains(sel.anchorNode) ? offsetOf(sel.anchorNode!, sel.anchorOffset) : at;
      splice(Math.min(at, end), Math.abs(end - at), "\n");
    }
  });
  root.addEventListener("paste", (e) => {
    e.preventDefault();
    const text = e.clipboardData?.getData("text/plain") ?? "";
    const sel = document.getSelection();
    const a = caret() ?? value.length;
    const b = sel && root.contains(sel.anchorNode) ? offsetOf(sel.anchorNode!, sel.anchorOffset) : a;
    splice(Math.min(a, b), Math.abs(a - b), text.replace(/\r\n?/g, "\n"));
  });
  root.addEventListener("compositionstart", () => (composing = true));
  root.addEventListener("compositionend", () => {
    composing = false;
    root.dispatchEvent(new Event("input"));
  });
  root.addEventListener("input", () => {
    if (composing) return;
    const next = serialize(root);
    const at = caret();
    // Re-render when a typed `{...}` became a variable, a chip was removed, or the browser added markup.
    const chips = root.querySelectorAll(".var-chip").length;
    const vars = sourceSegments(next).filter((s) => s.kind === "var").length;
    const foreign = Array.from(root.children).some((c) => !(c as HTMLElement).classList.contains("var-chip") && (c as HTMLElement).dataset.raw === undefined);
    if (chips !== vars || foreign) update(next, at);
    else {
      value = next;
      lastCaret = at;
      root.classList.toggle("is-empty", value === "");
      opts.onChange(value);
    }
  });
  const remember = () => {
    // The drawer is rebuilt for every edit; stop listening once this editor is gone.
    if (!root.isConnected && root.dataset.mounted) return document.removeEventListener("selectionchange", remember);
    if (root.isConnected) root.dataset.mounted = "1";
    const at = caret();
    if (at !== null) lastCaret = at;
  };
  root.addEventListener("keyup", remember);
  root.addEventListener("mouseup", remember);
  document.addEventListener("selectionchange", remember);

  render();
  return {
    el: root,
    value: () => value,
    insert: (raw) => {
      root.focus();
      splice(Math.min(lastCaret ?? value.length, value.length), 0, raw);
    },
    set: (raw) => update(raw, raw.length),
    focus: () => root.focus(),
  };
}
