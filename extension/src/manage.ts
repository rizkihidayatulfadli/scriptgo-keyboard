// Manage page (manage.html): add, edit, and delete templates and custom fields. Lists fill the
// page; an item opens in a drawer from the right. Edits go to the background worker, which stores
// them and syncs them to the phone.

import {
  BUILT_IN_VARIABLES,
  buildVariableSnippet,
  EDITABLE_FIELD_TYPES,
  FIELD_TYPE_LABELS,
  fieldKeyFromLabel,
  MAX_TAGS,
  normalizeShortcut,
  normalizeTags,
  parseOptions,
  parseTags,
  TAG_MAX_LENGTH,
  variableLabel,
} from "./engine/manage";
import { CURRENCIES, currencyFromCode, formatFieldValue, type FieldType } from "./engine/template";
import { CHANNEL_DEFAULT_OPTIONS, CHANNEL_FIELD_ID, CHANNEL_KEY } from "./engine/contacts";
import { aiPanel } from "./aiPanel";
import { chipEditor, type VarSegment } from "./chipEditor";
import { icon } from "./icons";
import { fieldBadge, fieldIcon } from "./fieldIcon";
import { FIELD_ICON_NAMES, pickFieldIcon, resolveFieldIcon } from "./engine/fieldIcons";
import {
  DEFAULT_SETTINGS,
  KEY_SESSION,
  KEY_SETTINGS,
  readTable,
  send,
  tableKey,
  type FieldRow,
  type Reply,
  type Session,
  type Settings,
  type TemplateRow,
} from "./store";
import { brand, h, mountSignIn } from "./ui";

type Tab = "templates" | "fields";
/** The open drawer: a template or field by id, or a new one (id null). */
type Editing = { kind: Tab; id: string | null } | null;

const app = document.getElementById("app")!;
let tab: Tab = location.hash === "#fields" ? "fields" : "templates";
let editing: Editing = null;
let query = "";
let toast = "";
let toastTimer: number | undefined;

let templates: TemplateRow[] = [];
let fields: FieldRow[] = [];
let settings: Settings = DEFAULT_SETTINGS;

const fieldLabels = () => Object.fromEntries(fields.map((f) => [f.field_key, f.label]));

async function load(): Promise<void> {
  const live = <T extends { deleted: boolean }>(rows: Record<string, T>) => Object.values(rows).filter((r) => !r.deleted);
  templates = live(await readTable("templates")).sort((a, b) => a.shortcut.localeCompare(b.shortcut));
  fields = live(await readTable("field_definitions")).sort((a, b) => a.position - b.position || a.label.localeCompare(b.label));
  settings = { ...DEFAULT_SETTINGS, ...((await chrome.storage.local.get(KEY_SETTINGS))[KEY_SETTINGS] as Partial<Settings> | undefined) };
}

async function draw(): Promise<void> {
  const session = (await chrome.storage.local.get(KEY_SESSION))[KEY_SESSION] as Session | undefined;
  if (!session) {
    const body = h("div", { class: "stack" });
    app.replaceChildren(h("section", { class: "page-card" }, brand(), body));
    await mountSignIn(body, () => void draw(), "Masuk dulu untuk mengatur template dan field. Perubahan ikut tersimpan ke app di HP.");
    return;
  }
  await load();
  app.replaceChildren(
    h("header", { class: "row between" }, brand(36), h("span", { class: "small" }, session.email)),
    h(
      "div",
      { class: "hero" },
      h("h1", {}, "Kelola"),
      h("p", { class: "lead" }, "Template dan field kontak ikut tersimpan ke app ScriptGo di HP."),
    ),
    toolbar(),
    h("section", { class: "content", "aria-live": "polite" }, ...(tab === "templates" ? templateList() : fieldList())),
    ...(editing ? [drawer(editing)] : []),
    ...(toast ? [h("div", { class: "toast", role: "status" }, icon("check", 18), toast)] : []),
  );
}

function showToast(text: string): void {
  toast = text;
  clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toast = "";
    app.querySelector(".toast")?.remove();
  }, 2600);
}

// --- Toolbar and lists -----------------------------------------------------------------------

function toolbar(): HTMLElement {
  const tabButton = (value: Tab, label: string, iconName: string, count: number) =>
    h(
      "button",
      {
        class: tab === value ? "tab sel" : "tab",
        role: "tab",
        "aria-selected": String(tab === value),
        click: () => {
          tab = value;
          editing = null;
          query = "";
          history.replaceState(null, "", value === "fields" ? "#fields" : "#templates");
          void draw();
        },
      },
      icon(iconName, 18),
      label,
      h("span", { class: "count" }, String(count)),
    );
  const search = h("input", { type: "search", placeholder: tab === "templates" ? "Cari template" : "Cari field", "aria-label": "Cari" });
  search.value = query;
  search.addEventListener("input", () => {
    query = search.value;
    const content = app.querySelector(".content");
    content?.replaceChildren(...(tab === "templates" ? templateList() : fieldList()));
  });
  return h(
    "div",
    { class: "toolbar" },
    h(
      "div",
      { class: "tabs", role: "tablist" },
      tabButton("templates", "Template", "message-square-text", templates.length),
      tabButton("fields", "Field kontak", "tag", fields.length),
    ),
    h("label", { class: "search grow" }, icon("search", 18), search),
    h(
      "button",
      { class: "primary", click: () => open({ kind: tab, id: null }) },
      icon("plus", 18),
      tab === "templates" ? "Template baru" : "Field baru",
    ),
  );
}

/** Message text with each `{variable}` shown as a named badge. */
function withBadges(text: string): Node[] {
  const labels = fieldLabels();
  const out: Node[] = [];
  let last = 0;
  for (const m of text.matchAll(/\{([^{}\n]+)\}/g)) {
    if (m.index! > last) out.push(document.createTextNode(text.slice(last, m.index)));
    out.push(h("span", { class: "var" }, variableLabel(m[1], labels)));
    last = m.index! + m[0].length;
  }
  if (last < text.length) out.push(document.createTextNode(text.slice(last)));
  return out;
}

function templateList(): HTMLElement[] {
  const q = query.trim().toLowerCase();
  const shown = templates.filter((t) => !q || t.shortcut.includes(q) || t.title.toLowerCase().includes(q) || t.body.toLowerCase().includes(q));
  if (!shown.length) return [empty(q ? "Tidak ada template yang cocok." : "Belum ada template. Buat yang pertama, misalnya salam pembuka.")];
  return [
    h(
      "div",
      { class: "cards" },
      ...shown.map((t) =>
        h(
          "button",
          { class: "card-item", click: () => open({ kind: "templates", id: t.id }) },
          h("span", { class: "row" }, h("span", { class: "shortcut-chip" }, settings.trigger + t.shortcut), h("b", { class: "grow ellipsis" }, t.title)),
          h("span", { class: "preview-text" }, ...withBadges(t.body)),
        ),
      ),
    ),
  ];
}

function fieldList(): HTMLElement[] {
  const q = query.trim().toLowerCase();
  const matches = (label: string, key: string) => !q || label.toLowerCase().includes(q) || key.includes(q);
  // Channel is built in (as in the app): always listed under Bawaan, its choices editable, never deleted.
  const channel = fields.find(isChannel);
  const shown = fields.filter((f) => !isChannel(f) && matches(f.label, f.field_key));
  const builtIn = BUILT_IN_VARIABLES.filter((v) => matches(v.label, v.key));
  const channelShown = matches(channel?.label ?? "Channel", CHANNEL_KEY);
  const channelChoices = channel ? parseOptions(channel.options) : CHANNEL_DEFAULT_OPTIONS;
  const typeDetail = (f: FieldRow) => {
    const type = f.type as FieldType;
    const label = FIELD_TYPE_LABELS[type] ?? f.type;
    return type === "currency" ? `${label} · ${currencyFromCode(f.options.split("\n")[0]).code}` : label;
  };
  return [
    ...(builtIn.length || channelShown
      ? [
          h("h2", { class: "section-label" }, "Bawaan"),
          h(
            "div",
            { class: "cards" },
            ...builtIn.map((v) =>
              h(
                "div",
                { class: "card-item locked", title: "Selalu ada di setiap kontak" },
                h(
                  "span",
                  { class: "row" },
                  fieldBadge(v.key === "nomor" ? "phone" : "user"),
                  fieldText(v.label, v.description, v.key),
                  h("span", { class: "muted-icon", "aria-label": "Bawaan" }, icon("lock", 16)),
                ),
              ),
            ),
            ...(channelShown
              ? [
                  h(
                    "button",
                    { class: "card-item", title: "Pilihannya bisa diubah", click: () => open({ kind: "fields", id: channel?.id ?? CHANNEL_FIELD_ID }) },
                    h(
                      "span",
                      { class: "row" },
                      fieldBadge(resolveFieldIcon(channel?.icon, channel?.label ?? "Channel", CHANNEL_KEY, "choice")),
                      fieldText(
                        channel?.label ?? "Channel",
                        `Pilihan · ${channelChoices.slice(0, 3).join(", ")}${channelChoices.length > 3 ? ", …" : ""}`,
                        CHANNEL_KEY,
                      ),
                    ),
                  ),
                ]
              : []),
          ),
        ]
      : []),
    h("h2", { class: "section-label" }, "Buatanmu"),
    shown.length
      ? h(
          "div",
          { class: "cards" },
          ...shown.map((f) =>
            h(
              "button",
              { class: "card-item", click: () => open({ kind: "fields", id: f.id }) },
              h(
                "span",
                { class: "row" },
                fieldBadge(resolveFieldIcon(f.icon, f.label, f.field_key, f.type)),
                fieldText(f.label, typeDetail(f), f.field_key),
              ),
            ),
          ),
        )
      : empty(q ? "Tidak ada field yang cocok." : "Belum ada field. Tambah field seperti Kota, Ukuran, atau Total belanja."),
  ];
}

/**
 * A field card's text: name, what it holds, and the variable on its own line (cut short when long),
 * so a long variable never squeezes the name as a trailing chip did.
 */
function fieldText(label: string, detail: string, key: string): HTMLElement {
  return h(
    "span",
    { class: "grow tight field-text" },
    h("b", { class: "ellipsis" }, label),
    h("span", { class: "small ellipsis" }, detail),
    h("span", { class: "var ellipsis", title: `{${key}}` }, `{${key}}`),
  );
}

function empty(text: string): HTMLElement {
  return h("div", { class: "empty-card" }, h("span", { class: "tint" }, icon("sparkles", 18)), h("p", { class: "small" }, text));
}

/** The built-in Channel field (fixed id in the app; older copies are matched by their key). */
function isChannel(f: FieldRow): boolean {
  return f.id === CHANNEL_FIELD_ID || f.field_key === CHANNEL_KEY;
}

// --- Drawer ----------------------------------------------------------------------------------

function open(next: Editing): void {
  editing = next;
  void draw().then(() => app.querySelector<HTMLElement>(".drawer input, .drawer textarea")?.focus());
}

function close(): void {
  editing = null;
  void draw();
}

document.addEventListener("keydown", (e) => {
  if (e.key !== "Escape" || !editing) return;
  // Escape closes an open fallback popover first.
  const pop = app.querySelector(".popover");
  if (pop) pop.remove();
  else close();
});

function drawer(target: NonNullable<Editing>): HTMLElement {
  const panel = target.kind === "templates" ? templateEditor(target.id) : fieldEditor(target.id);
  return h(
    "div",
    { class: "drawer-root" },
    h("div", { class: "scrim", click: close }),
    h("aside", { class: "drawer", role: "dialog", "aria-modal": "true", "aria-label": panel.title }, panel.body),
  );
}

interface Panel {
  title: string;
  body: HTMLElement;
}

async function submit(action: () => Promise<Reply>, error: HTMLElement): Promise<void> {
  let reply: Reply;
  try {
    reply = await action();
  } catch {
    reply = { ok: false, error: "Terjadi kesalahan" };
  }
  if (!reply.ok) {
    error.textContent = reply.error;
    return;
  }
  editing = null;
  showToast(reply.message ?? "Tersimpan.");
  await draw();
}

/** A labelled control; [input] is the element the label points to when [control] wraps it. */
function field(label: string, control: HTMLElement, hint?: Node | string, input: HTMLElement = control): HTMLElement {
  if (!input.id) input.id = `f-${label.toLowerCase().replace(/\W+/g, "-")}`;
  return h("div", { class: "field" }, h("label", { for: input.id }, label), control, ...(hint ? [h("span", { class: "hint" }, hint)] : []));
}

function form(title: string, onSubmit: () => void, error: HTMLElement, content: HTMLElement[], onDelete: (() => void) | null, deleteWhat: string): Panel {
  const body = h(
    "form",
    { class: "drawer-form", novalidate: true, submit: (e: Event) => (e.preventDefault(), onSubmit()) },
    h(
      "div",
      { class: "drawer-head" },
      h("h2", {}, title),
      h("button", { type: "button", class: "icon-button", "aria-label": "Tutup", click: close }, icon("x", 20)),
    ),
    h("div", { class: "drawer-body stack" }, ...content, error),
    h(
      "div",
      { class: "drawer-foot" },
      ...(onDelete
        ? [
            h(
              "button",
              { type: "button", class: "outline danger-outline", click: () => confirm(`Hapus ${deleteWhat}?`) && onDelete() },
              icon("trash-2", 18),
              "Hapus",
            ),
          ]
        : []),
      h("span", { class: "grow" }),
      h("button", { type: "button", class: "outline", click: close }, "Batal"),
      h("button", { type: "submit", class: "primary" }, icon("check", 18), "Simpan"),
    ),
  );
  return { title, body };
}

/**
 * The settings of one variable chip, below the editor: its fallback (or its choices, for a choice
 * variable), or remove it. Built-ins like {@tanggal} have nothing to set, only remove.
 */
function editChip(box: HTMLElement, chipEl: HTMLElement, v: VarSegment, label: string, replace: (raw: string | null) => void) {
  box.querySelector(".popover")?.remove();
  box.querySelectorAll(".var-chip.active").forEach((c) => c.classList.remove("active"));
  chipEl.classList.add("active");
  const close = () => (pop.remove(), chipEl.classList.remove("active"));
  const input = h("input", { type: "text", maxlength: "200" });
  const choice = !!v.options?.length;
  input.value = choice ? v.options!.join(", ") : (v.default ?? "");
  const save = () => {
    const text = input.value.trim();
    close();
    replace(choice ? buildVariableSnippet(v.key, null, parseOptions(text.replace(/\s*,\s*/g, "\n"))) : buildVariableSnippet(v.key, text || null));
  };
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      save();
    } else if (e.key === "Escape") close();
  });
  const pop = h(
    "div",
    { class: "popover", role: "dialog", "aria-label": `Atur ${label}` },
    h("b", {}, label),
    ...(v.builtin
      ? [h("p", { class: "hint" }, "Diisi otomatis saat template dipakai. Tidak ada yang perlu diatur.")]
      : [
          field(
            choice ? "Pilihan (pisahkan dengan koma)" : "Kalau kosong, pakai (opsional)",
            input,
            choice ? "Pilihan pertama jadi isian awal di form." : "Dipakai kalau kontak belum punya data ini.",
          ),
        ]),
    h(
      "div",
      { class: "actions" },
      h("button", { type: "button", class: "text small danger", click: () => (close(), replace(null)) }, icon("trash-2", 16), "Hapus"),
      h("span", { class: "grow" }),
      h("button", { type: "button", class: "text small", click: close }, v.builtin ? "Tutup" : "Batal"),
      ...(v.builtin ? [] : [h("button", { type: "button", class: "primary small", click: save }, "Simpan")]),
    ),
  );
  box.append(pop);
  pop.scrollIntoView({ block: "nearest" });
  if (!v.builtin) {
    input.focus({ preventScroll: true });
    input.select();
  }
}

function templateEditor(id: string | null): Panel {
  const current = id ? templates.find((t) => t.id === id) : undefined;
  const title = h("input", { type: "text", maxlength: "80", placeholder: "Contoh: Info ongkir" });
  title.value = current?.title ?? "";
  const shortcut = h("input", { type: "text", maxlength: "30", autocapitalize: "off", spellcheck: "false", placeholder: "ongkir" });
  shortcut.value = current?.shortcut ?? "";
  const preview = h("div", { class: "bubble" });
  // The saved text, kept in sync with the chip editor (also read by the e2e test).
  const source = h("textarea", { name: "body", hidden: "", tabindex: "-1", "aria-hidden": "true" });
  source.value = current?.body ?? "";
  const refreshPreview = () =>
    preview.replaceChildren(...(source.value.trim() ? withBadges(source.value) : [h("span", { class: "placeholder" }, "Pratinjau pesan muncul di sini.")]));
  const labels = fieldLabels();
  // Variables in the message are chips; clicking one sets its fallback or choices, or removes it.
  const editorBox = h("div", { class: "chip-editor-wrap" });
  const editor = chipEditor({
    initial: source.value,
    ariaLabel: "Isi pesan",
    placeholder: "Tulis pesannya, lalu sisipkan variabel dari tombol di bawah.",
    label: (v) => variableLabel(v.key, labels),
    onChipClick: (chipEl, v, replace) => editChip(editorBox, chipEl, v, variableLabel(v.key, labels), replace),
    onChange: (value) => {
      source.value = value;
      refreshPreview();
      ai.refresh();
    },
  });
  editorBox.append(editor.el);
  refreshPreview();

  // AI: make the template from a description, or rewrite the text (as in the app).
  const ai = aiPanel({
    current: () => ({ title: title.value, shortcut: shortcut.value, body: editor.value() }),
    onUse: (result, created) => {
      if (created) {
        title.value = result.title;
        shortcut.value = normalizeShortcut(result.shortcut.replace(/_/g, "-"), settings.trigger);
      }
      editor.set(result.body);
    },
  });

  const insert = (text: string) => editor.insert(text);

  // Contact variables can be empty for a contact, so ask for a fallback first (as in the app).
  const chips = h("div", { class: "chips" });
  const askFallback = (anchor: HTMLElement, key: string, label: string) => {
    chips.querySelector(".popover")?.remove();
    const input = h("input", { type: "text", maxlength: "80", placeholder: key === "nama" ? "Contoh: Kak" : "Contoh: -" });
    const pop = h(
      "div",
      { class: "popover", role: "dialog", "aria-label": `Sisipkan ${label}` },
      h("b", {}, `Sisipkan ${label}`),
      field("Kalau kosong, pakai (opsional)", input, "Dipakai kalau kontak belum punya data ini."),
      h(
        "div",
        { class: "actions" },
        h("button", { type: "button", class: "text small", click: () => pop.remove() }, "Batal"),
        h("button", { type: "button", class: "primary small", click: () => (pop.remove(), insert(buildVariableSnippet(key, input.value.trim()))) }, "Sisipkan"),
      ),
    );
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        pop.remove();
        insert(buildVariableSnippet(key, input.value.trim()));
      }
    });
    // Below all chips, so the chips keep their places.
    chips.append(pop);
    anchor.setAttribute("aria-expanded", "true");
    input.focus();
  };
  const chip = (label: string, onClick: (el: HTMLElement) => void) => {
    const el = h("button", { type: "button", class: "chip" }, icon("plus", 14), label);
    el.addEventListener("click", () => onClick(el));
    return el;
  };
  chips.append(
    ...BUILT_IN_VARIABLES.map((v) => chip(v.label, (el) => askFallback(el, v.key, v.label))),
    ...fields.map((f) => chip(f.label, (el) => askFallback(el, f.field_key, f.label))),
    ...[
      ["@tanggal", "Tanggal"],
      ["@hari", "Hari"],
      ["@jam", "Jam"],
      ["@kursor", "Kursor"],
    ].map(([key, label]) => chip(label, () => insert(`{${key}}`))),
  );

  // Tags: chips with ×, and an input that adds one on Enter or comma (Backspace on empty removes the last).
  let tags = parseTags(current?.tags ?? "");
  const tagInput = h("input", { type: "text", maxlength: String(TAG_MAX_LENGTH), placeholder: "Tambah tag, lalu Enter", "aria-label": "Tag baru" });
  const tagBox = h("div", { class: "tag-input" });
  const renderTags = () => {
    tagBox.replaceChildren(
      ...tags.map((tag) =>
        h(
          "span",
          { class: "tag" },
          tag,
          h("button", { type: "button", "aria-label": `Hapus tag ${tag}`, click: () => ((tags = tags.filter((t) => t !== tag)), renderTags(), tagInput.focus()) }, icon("x", 12)),
        ),
      ),
      tagInput,
    );
    tagInput.hidden = tags.length >= MAX_TAGS;
  };
  const addPendingTag = () => {
    if (!tagInput.value.trim()) return;
    tags = normalizeTags([...tags, tagInput.value]);
    tagInput.value = "";
    renderTags();
  };
  tagInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === ",") {
      e.preventDefault();
      addPendingTag();
      tagInput.focus();
    } else if (e.key === "Backspace" && !tagInput.value && tags.length) {
      tags = tags.slice(0, -1);
      renderTags();
      tagInput.focus();
    }
  });
  tagInput.addEventListener("blur", addPendingTag);
  tagBox.addEventListener("click", (e) => e.target === tagBox && tagInput.focus());
  renderTags();

  const error = h("p", { class: "error", role: "alert" });
  return form(
    current ? "Ubah template" : "Template baru",
    () =>
      void submit(
        () => {
          addPendingTag();
          return send({
            type: "saveTemplate",
            template: { id, title: title.value, shortcut: normalizeShortcut(shortcut.value, settings.trigger), body: editor.value(), tags },
          });
        },
        error,
      ),
    error,
    [
      field("Judul", title),
      field(
        "Shortcut",
        h("div", { class: "prefixed" }, h("span", { "aria-hidden": "true" }, settings.trigger), shortcut),
        "Huruf kecil, angka, - dan _. Diketik setelah karakter pemicu.",
        shortcut,
      ),
      h(
        "div",
        { class: "field" },
        h("div", { class: "row between" }, h("span", { class: "label-strong" }, "Isi pesan"), ai.button),
        ai.box,
        editorBox,
        source,
        h("span", { class: "hint" }, "Klik variabel untuk mengatur isian cadangan atau pilihannya."),
      ),
      h("div", { class: "field" }, h("span", { class: "label-strong" }, "Sisipkan variabel"), chips),
      h("div", { class: "field" }, h("span", { class: "label-strong" }, "Pratinjau"), preview),
      field("Tag", tagBox, "Untuk mengelompokkan template, misalnya promo atau pengiriman. Maks. 10.", tagInput),
    ],
    current ? () => void submit(() => send({ type: "deleteTemplate", id: current.id }), error) : null,
    `template "${current?.title ?? ""}"`,
  );
}

const TYPE_ICONS: Partial<Record<FieldType, string>> = {
  text: "type",
  longtext: "message-square-text",
  number: "hash",
  currency: "credit-card",
  date: "calendar",
  choice: "list",
};

function typeSample(type: FieldType, currency: string): string {
  switch (type) {
    case "text":
      return "Bandung";
    case "longtext":
      return "Jl. Merdeka 1, Bandung";
    case "number":
      return formatFieldValue("number", "1500");
    case "currency":
      return formatFieldValue("currency", currencyFromCode(currency).cents ? "1500.5" : "150000", currency);
    case "date":
      return formatFieldValue("date", "2026-09-26");
    case "choice":
      return "JNE, J&T";
    default:
      return "";
  }
}

function fieldEditor(id: string | null): Panel {
  const current = id ? fields.find((f) => f.id === id) : undefined;
  // The built-in Channel field: only its choices (and fallback) change; it may not exist here yet.
  const builtInChannel = id === CHANNEL_FIELD_ID || (current ? isChannel(current) : false);
  const label = h("input", { type: "text", maxlength: "40", placeholder: "Contoh: Kota" });
  label.value = current?.label ?? (builtInChannel ? "Channel" : "");
  if (builtInChannel) label.readOnly = true;
  let type: FieldType = builtInChannel ? "choice" : ((current?.type as FieldType | undefined) ?? "text");
  let currency = type === "currency" ? currencyFromCode(current?.options.split("\n")[0]).code : CURRENCIES[0].code;
  const choices: string[] = type === "choice" ? (current ? parseOptions(current.options) : builtInChannel ? [...CHANNEL_DEFAULT_OPTIONS] : []) : [];
  let fallback = current?.fallback ?? "";
  // Empty = automatic: picked from the name and type as they are typed.
  let chosenIcon = current?.icon ?? "";

  const letter = h("span", { class: "icon-head", role: "button", tabindex: "0", "aria-label": "Ikon field", title: "Klik untuk ganti ikon" });
  const keyBadge = h("span", { class: "var" });
  const otherKeys = fields.filter((f) => f.id !== id).map((f) => f.field_key);
  const currentKey = () => current?.field_key ?? (builtInChannel ? CHANNEL_KEY : fieldKeyFromLabel(label.value, otherKeys));
  const autoIcon = () => pickFieldIcon(label.value, currentKey(), type);
  // Icon: automatic or one from the gallery (drawn under the name, opened from the header too).
  const iconBox = h("div", { class: "field" });
  let galleryOpen = false;
  const drawIcon = () => {
    const shown = chosenIcon || autoIcon();
    letter.replaceChildren(fieldBadge(shown, true));
    const tile = (name: string, selected: boolean, text?: string) =>
      h(
        "button",
        {
          type: "button",
          class: selected ? "icon-tile sel" : "icon-tile",
          role: "radio",
          "aria-checked": String(selected),
          "aria-label": text ?? name,
          title: text ?? name,
          click: () => {
            chosenIcon = text ? "" : name;
            drawIcon();
          },
        },
        fieldIcon(name, 22),
        ...(text ? [h("span", {}, text)] : []),
      );
    iconBox.replaceChildren(
      h("span", { class: "label-strong" }, "Ikon"),
      h(
        "div",
        { class: "row" },
        fieldBadge(shown),
        h("span", { class: "small grow" }, chosenIcon ? "Dipilih sendiri" : "Otomatis, dipilih dari nama field"),
        h("button", { type: "button", class: "outline small", click: () => ((galleryOpen = !galleryOpen), drawIcon()) }, galleryOpen ? "Selesai" : "Ganti ikon"),
      ),
      ...(galleryOpen
        ? [
            h(
              "div",
              { class: "icon-grid", role: "radiogroup", "aria-label": "Ikon field" },
              tile(autoIcon(), !chosenIcon, "Otomatis"),
              ...FIELD_ICON_NAMES.map((name) => tile(name, chosenIcon === name)),
            ),
          ]
        : []),
    );
  };
  const toggleGallery = () => ((galleryOpen = !galleryOpen), drawIcon());
  letter.addEventListener("click", toggleGallery);
  letter.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      toggleGallery();
    }
  });
  const refreshHead = () => {
    keyBadge.textContent = `{${currentKey()}}`;
    drawIcon();
  };
  label.addEventListener("input", refreshHead);
  refreshHead();

  const types = EDITABLE_FIELD_TYPES.includes(type) ? EDITABLE_FIELD_TYPES : [...EDITABLE_FIELD_TYPES, type];
  const typeGrid = h("div", { class: "type-grid", role: "radiogroup", "aria-label": "Tipe data" });
  const choiceBox = h("div", { class: "field" });
  const currencyBox = h("div", { class: "field" });
  const fallbackBox = h("div", { class: "field" });

  const drawTypes = () => {
    typeGrid.replaceChildren(
      ...types.map((t) =>
        h(
          "button",
          {
            type: "button",
            class: t === type ? "type-card sel" : "type-card",
            role: "radio",
            "aria-checked": String(t === type),
            click: () => {
              // A value typed for another type may not fit this one.
              if (t !== type) fallback = "";
              type = t;
              drawTypes();
              drawIcon();
            },
          },
          h("span", { class: "row between" }, h("span", { class: "tint" }, icon(TYPE_ICONS[t] ?? "type", 18)), ...(t === type ? [icon("check", 18)] : [])),
          h("b", {}, FIELD_TYPE_LABELS[t] ?? t),
          h("span", { class: "small ellipsis" }, typeSample(t, currency)),
        ),
      ),
    );
    choiceBox.hidden = type !== "choice";
    currencyBox.hidden = type !== "currency";
    drawFallback();
  };

  // The fallback input follows the type: a list of the choices, a date picker, or a text box.
  const drawFallback = () => {
    let input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
    if (type === "choice") {
      if (!choices.includes(fallback)) fallback = "";
      input = h("select", {});
      for (const [value, text] of [["", "Tanpa fallback"], ...choices.map((c) => [c, c])]) input.append(h("option", { value }, text));
    } else if (type === "longtext") {
      input = h("textarea", { rows: "2", maxlength: "200" });
    } else {
      input = h("input", { type: type === "date" ? "date" : "text", maxlength: "200", placeholder: type === "text" ? "Contoh: Bandung" : "" });
      if (type === "number" || type === "currency") input.setAttribute("inputmode", "decimal");
    }
    input.value = fallback;
    input.id = "field-fallback";
    input.addEventListener(input instanceof HTMLSelectElement ? "change" : "input", () => {
      // Numbers are stored with a dot for decimals, like contact values.
      if (type === "number" || type === "currency") input.value = input.value.replace(",", ".").replace(/[^\d.-]/g, "");
      fallback = input.value;
    });
    fallbackBox.replaceChildren(
      h("label", { class: "label-strong", for: input.id }, "Kalau kosong, isi dengan (opsional)"),
      input,
      h("small", { class: "hint" }, "Dipakai di semua template kalau kontak belum punya data ini. Fallback yang ditulis langsung di template tetap didahulukan."),
    );
  };

  const choiceInput = h("input", { type: "text", maxlength: "40", placeholder: "Tambah pilihan, misalnya JNE", "aria-label": "Pilihan baru" });
  const choiceList = h("div", { class: "chips" });
  const drawChoices = () => {
    choiceList.replaceChildren(
      ...choices.map((c) =>
        h(
          "span",
          { class: "pill" },
          c,
          h("button", { type: "button", class: "pill-x", "aria-label": `Hapus pilihan ${c}`, click: () => (choices.splice(choices.indexOf(c), 1), drawChoices()) }, icon("x", 14)),
        ),
      ),
    );
    // The fallback list offers the choices, so it follows them.
    drawFallback();
  };
  const addChoice = () => {
    const text = choiceInput.value.trim();
    if (text && !choices.includes(text)) choices.push(text);
    choiceInput.value = "";
    drawChoices();
    choiceInput.focus();
  };
  choiceInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      addChoice();
    }
  });
  choiceBox.append(
    h("span", { class: "label-strong" }, "Pilihan"),
    choiceList,
    h("div", { class: "row" }, h("div", { class: "grow" }, choiceInput), h("button", { type: "button", class: "primary square", "aria-label": "Tambah pilihan", click: addChoice }, icon("plus", 18))),
  );
  drawChoices();

  const currencyPills = h("div", { class: "chips", role: "radiogroup", "aria-label": "Mata uang" });
  const drawCurrencies = () =>
    currencyPills.replaceChildren(
      ...CURRENCIES.map((c) =>
        h(
          "button",
          {
            type: "button",
            class: c.code === currency ? "pill-choice sel" : "pill-choice",
            role: "radio",
            "aria-checked": String(c.code === currency),
            click: () => {
              currency = c.code;
              drawCurrencies();
              drawTypes();
            },
          },
          h("b", {}, c.code),
          h("span", {}, c.symbol.trim()),
        ),
      ),
    );
  currencyBox.append(h("span", { class: "label-strong" }, "Mata uang"), currencyPills);
  drawCurrencies();
  drawTypes();

  const error = h("p", { class: "error", role: "alert" });
  const options = () => {
    if (choiceInput.value.trim()) addChoice();
    if (type === "choice") return [...choices];
    if (type === "currency") return currency === CURRENCIES[0].code ? [] : [currency];
    return [];
  };
  return form(
    current ? "Ubah field" : "Field baru",
    () => {
      const stored = options();
      const kept = type === "choice" && !stored.includes(fallback) ? "" : fallback;
      void submit(() => send({ type: "saveField", field: { id: current?.id ?? (builtInChannel ? CHANNEL_FIELD_ID : null), label: label.value, type, options: stored, fallback: kept, icon: chosenIcon } }), error);
    },
    error,
    [
      h("div", { class: "row" }, letter, h("div", { class: "tight grow" }, h("span", { class: "small" }, "Variabel di template"), keyBadge)),
      field(
        "Nama field",
        label,
        builtInChannel
          ? "Field bawaan: terisi otomatis sesuai app chat yang dipakai. Pilihannya bisa kamu ubah, tapi field ini tidak bisa dihapus."
          : current
            ? "Nama variabel tetap walau nama field diganti, supaya template lama tetap jalan."
            : undefined,
      ),
      iconBox,
      ...(builtInChannel ? [] : [h("div", { class: "field" }, h("span", { class: "label-strong" }, "Tipe data"), typeGrid)]),
      choiceBox,
      currencyBox,
      fallbackBox,
      ...(current && !builtInChannel ? [h("p", { class: "small" }, "Menghapus field tidak menghapus isinya di kontak. Tambah lagi field dengan nama yang sama untuk memunculkannya kembali.")] : []),
    ],
    current && !builtInChannel ? () => void submit(() => send({ type: "deleteField", id: current.id }), error) : null,
    `field "${current?.label ?? ""}"`,
  );
}

// Redraw when a sync brings changes, but leave an open drawer alone so typing is not lost.
chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== "local") return;
  if (KEY_SESSION in changes) return void draw();
  const watched = [tableKey("templates"), tableKey("field_definitions"), KEY_SETTINGS];
  if (!editing && watched.some((k) => k in changes)) void draw();
});
void draw();
