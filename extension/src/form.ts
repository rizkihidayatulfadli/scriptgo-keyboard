// Variable form, shown by the content script in an iframe over the page: "Untuk siapa?" contact
// picker, then the template's variables. Mirrors TemplateFormActivity on Android.

import {
  CHANNEL_DEFAULT_OPTIONS,
  CHANNEL_KEY,
  WHATSAPP_WEB,
  changedFields,
  channelOptionFor,
  fallbackKeys,
  matchChatContact,
  newContactPrefill,
  normalizePhone,
  prefillForm,
  renderForm,
  type ChatHint,
} from "./engine/contacts";
import { currencyFromCode, formatFieldValue, localNow, parse, type FieldType, type ParsedTemplate, type Variable } from "./engine/template";
import { icon } from "./icons";
import { loadData, send, type Contact, type FieldDefinition, type FormRequest, type LocalData, type Template } from "./store";
import { followTheme } from "./theme";

followTheme();

const app = document.getElementById("app")!;
let port: MessagePort | null = null;

function finish(message: { type: "done"; text: string; cursor: number | null } | { type: "cancel" }): void {
  port?.postMessage(message);
  port = null;
}
const cancel = () => finish({ type: "cancel" });

/** Waits for the port from the content script that opened this frame. */
function receivePort(): Promise<MessagePort> {
  return new Promise((resolve) => {
    const onMessage = (e: MessageEvent) => {
      if (e.source !== window.parent || e.data?.type !== "magic-keyboard-port" || !e.ports[0]) return;
      window.removeEventListener("message", onMessage);
      resolve(e.ports[0]);
    };
    window.addEventListener("message", onMessage);
  });
}

async function main(): Promise<void> {
  port = await receivePort();
  const reply = await send({ type: "claimForm", nonce: location.hash.slice(1) }).catch(() => null);
  const request: FormRequest | undefined = reply?.ok ? reply.request : undefined;
  const data = await loadData();
  const template = request && data.templates.find((t) => t.id === request.templateId);
  if (!request || !template) return cancel();

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") cancel();
  });
  document.getElementById("backdrop")!.addEventListener("mousedown", (e) => {
    if (e.target === e.currentTarget) cancel();
  });

  const parsed = parse(template.body);
  // Without contacts the picker is only useful on WhatsApp Web, to add the open chat's person.
  if (data.contacts.length === 0 && !request.hint) return showForm(data, template, parsed, null, false);
  const suggested = request.hint ? matchChatContact(data.contacts, request.hint) : null;
  const pick = (): void =>
    showPicker(data, template, suggested, (contact) => showForm(data, template, parsed, contact, false, pick), (search) =>
      showNewContact(data, template, request.hint, search, (contact) => showForm(data, template, parsed, contact, false, pick), pick),
    );
  // The open chat's contact is recognised: go straight to the variables, with "Ganti" to pick another.
  if (suggested) showForm(data, template, parsed, suggested, true, pick);
  else pick();
}

/** Template badge, title, shortcut chip, and a close button (mockup 6e). */
function header(data: LocalData, template: Template, subtitle?: string): HTMLElement {
  return h(
    "div",
    { class: "row" },
    h("span", { class: "badge-letter", "aria-hidden": "true" }, (template.title.trim()[0] ?? "T").toUpperCase()),
    h(
      "div",
      { class: "grow tight" },
      h("h1", {}, template.title),
      subtitle ? h("span", { class: "small" }, subtitle) : h("span", {}, h("span", { class: "shortcut-chip" }, data.settings.trigger + template.shortcut)),
    ),
    h("button", { type: "button", class: "icon-button", "aria-label": "Tutup", click: cancel }, icon("x", 18)),
  );
}

const initial = (name: string) => (name.trim()[0] ?? "?").toUpperCase();

// --- DOM helpers -----------------------------------------------------------------------------

type Attrs = Record<string, string | boolean | ((e: Event) => void)>;

function h<K extends keyof HTMLElementTagNameMap>(tag: K, attrs: Attrs = {}, ...children: (Node | string)[]) {
  const el = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (typeof value === "function") el.addEventListener(name, value);
    else if (typeof value === "boolean") (el as unknown as Record<string, boolean>)[name] = value;
    else el.setAttribute(name, value);
  }
  el.append(...children);
  return el;
}

const displayPhone = (e164: string) => formatFieldValue("phone", e164);

// --- Step 1: who is this for? ----------------------------------------------------------------

function showPicker(
  data: LocalData,
  template: Template,
  suggested: Contact | null,
  onPick: (c: Contact | null) => void,
  onNew: (search: string) => void,
): void {
  let highlighted = 0;
  let shown: Contact[] = [];
  const search = h("input", { type: "search", placeholder: "Cari nama atau nomor", "aria-label": "Cari kontak" });
  const list = h("div", { class: "contacts", role: "listbox" });

  const renderList = () => {
    const q = search.value.trim().toLowerCase();
    const digits = q.replace(/\D/g, "");
    const matches = (c: Contact) =>
      !q ||
      c.name.toLowerCase().includes(q) ||
      (digits.length >= 3 && c.phones.some((p) => p.includes(digits) || displayPhone(p).includes(digits)));
    shown = data.contacts.filter(matches);
    if (suggested && shown.includes(suggested)) shown = [suggested, ...shown.filter((c) => c !== suggested)];
    shown = shown.slice(0, 50);
    highlighted = Math.min(highlighted, Math.max(0, shown.length - 1));
    list.replaceChildren(
      ...shown.map((c, i) =>
        h(
          "button",
          { type: "button", class: i === highlighted ? "contact sel" : "contact", role: "option", click: () => onPick(c) },
          h("span", { class: "avatar", "aria-hidden": "true" }, initial(c.name)),
          h(
            "span",
            { class: "who" },
            h("span", { class: "name" }, c.name, ...(c === suggested ? [h("span", { class: "badge" }, "Chat ini")] : [])),
            h("span", { class: "phone" }, c.phones[0] ? displayPhone(c.phones[0]) : ""),
          ),
        ),
      ),
      ...(shown.length === 0 ? [h("p", { class: "empty" }, data.contacts.length ? "Tidak ada kontak yang cocok." : "Belum ada kontak. Tambahkan orang di chat ini.")] : []),
    );
  };

  search.addEventListener("input", () => {
    highlighted = 0;
    renderList();
  });
  search.addEventListener("keydown", (e) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      const step = e.key === "ArrowDown" ? 1 : -1;
      highlighted = shown.length ? (highlighted + step + shown.length) % shown.length : 0;
      renderList();
      list.querySelector(".sel")?.scrollIntoView({ block: "nearest" });
    } else if (e.key === "Enter") {
      e.preventDefault();
      onPick(shown[highlighted] ?? null);
    }
  });

  app.replaceChildren(
    header(data, template),
    h("div", { class: "label" }, "Untuk siapa?"),
    search,
    list,
    h("button", { type: "button", class: "outline new-contact", click: () => onNew(search.value) }, icon("plus", 18), "Kontak baru"),
    h(
      "div",
      { class: "actions" },
      h("button", { type: "button", class: "text", click: cancel }, "Batal"),
      h("button", { type: "button", class: "outline", click: () => onPick(null) }, "Lanjut tanpa kontak"),
    ),
  );
  renderList();
  search.focus();
}

// --- Step 1b: new contact (mirrors NewContactForm on Android) ---------------------------------

/**
 * Adds the person to the contacts: name and number from the open WhatsApp chat (or the search
 * text), and the Channel field set to WhatsApp. Saving picks the new contact for the template.
 */
function showNewContact(
  data: LocalData,
  template: Template,
  hint: ChatHint | null,
  search: string,
  onCreated: (c: Contact) => void,
  back: () => void,
): void {
  const start = newContactPrefill(hint, search);
  const channelField = data.fields.find((f) => f.key === CHANNEL_KEY);
  const channelOptions = channelField?.type === "choice" && channelField.options.length ? channelField.options : CHANNEL_DEFAULT_OPTIONS;
  const name = h("input", { type: "text", id: "new-name", maxlength: "120", autocomplete: "off" });
  name.value = start.name;
  const phone = h("input", { type: "tel", id: "new-phone", placeholder: "0812 3456 7890", autocomplete: "off" });
  phone.value = start.phone ? displayPhone(start.phone) : "";
  const channel = select([["", "(kosong)"], ...channelOptions.map((o) => [o, o])], channelOptionFor(hint ? WHATSAPP_WEB : null, channelOptions) ?? "");
  channel.id = "new-channel";
  const error = h("p", { class: "error", role: "alert" });

  let saving = false;
  const save = async () => {
    if (saving) return;
    const rawPhone = phone.value.trim();
    const e164 = rawPhone ? normalizePhone(rawPhone) : "";
    if (e164 === null) {
      error.textContent = "Nomor belum benar. Contoh: 0812 3456 7890";
      phone.focus();
      return;
    }
    if (!name.value.trim() && !e164) {
      error.textContent = "Isi nama atau nomor dulu.";
      name.focus();
      return;
    }
    saving = true;
    const fields: Record<string, string> = channel.value ? { [CHANNEL_KEY]: channel.value } : {};
    const reply = await send({ type: "createContact", name: name.value, phone: e164, fields }).catch(() => null);
    saving = false;
    if (!reply?.ok || !reply.contactId) {
      error.textContent = reply && !reply.ok ? reply.error : "Gagal menyimpan kontak. Coba lagi.";
      return;
    }
    // The same number may already belong to a contact; then that contact is used.
    let contact = data.contacts.find((c) => c.id === reply.contactId);
    if (!contact) {
      contact = { id: reply.contactId, name: name.value.trim() || e164, phones: e164 ? [e164] : [], fields };
      data.contacts.push(contact); // so "Ganti" lists it too
    }
    // The background created the Channel field if it was missing; the form needs it for {channel}.
    if (!channelField) data.fields.push({ key: CHANNEL_KEY, label: "Channel", type: "choice", options: [...CHANNEL_DEFAULT_OPTIONS], fallback: "" });
    onCreated(contact);
  };

  const form = h("form", { class: "form-scroll", novalidate: "" });
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    void save();
  });
  form.append(
    h("div", { class: "field" }, h("label", { for: "new-name" }, "Nama"), name),
    h("div", { class: "field" }, h("label", { for: "new-phone" }, "Nomor HP (boleh kosong)"), phone),
    h("div", { class: "field" }, h("label", { for: "new-channel" }, channelField?.label ?? "Channel"), channel),
    error,
    h("button", { type: "submit", hidden: true }),
  );
  app.replaceChildren(
    header(data, template, "Kontak baru"),
    form,
    h(
      "div",
      { class: "actions" },
      h("button", { type: "button", class: "text", click: back }, "Kembali"),
      h("button", { type: "button", class: "primary", click: () => void save() }, icon("check", 18), "Simpan & pakai"),
    ),
  );
  (name.value ? phone : name).focus();
}

// --- Step 2: variables -----------------------------------------------------------------------

function showForm(
  data: LocalData,
  template: Template,
  parsed: ParsedTemplate,
  contact: Contact | null,
  recognised: boolean,
  changeContact?: () => void,
): void {
  const fieldsByKey = new Map(data.fields.map((f) => [f.key, f]));
  const fieldTypes: Record<string, FieldType> = Object.fromEntries(data.fields.map((f) => [f.key, f.type]));
  const currencies: Record<string, string> = Object.fromEntries(
    data.fields.filter((f) => f.type === "currency" && f.options.length).map((f) => [f.key, f.options[0]]),
  );
  const fallbacks: Record<string, string> = Object.fromEntries(data.fields.filter((f) => f.fallback).map((f) => [f.key, f.fallback]));
  const values = prefillForm(parsed.variables, contact, fieldTypes, fallbacks);
  // Prefilled from a fallback (the template's or the field's) because this contact has nothing for them.
  const fromFallback = fallbackKeys(parsed.variables, contact, fieldTypes, fallbacks);

  const preview = h("div", { class: "preview", "aria-live": "polite" });
  const saveBox = h("input", { type: "checkbox", checked: true });
  const saveRow = h("label", { class: "save" }, saveBox, `Simpan ke kontak ${contact?.name ?? ""}`);
  const refresh = () => {
    preview.textContent = renderForm(parsed, values, fieldTypes, localNow(), currencies).text;
    saveRow.hidden = !contact || Object.keys(changedFields(values, contact, fieldTypes, fallbacks)).length === 0;
  };

  let submitting = false;
  const submit = async () => {
    if (submitting) return;
    submitting = true;
    const result = renderForm(parsed, values, fieldTypes, localNow(), currencies);
    if (contact && saveBox.checked) {
      const changes = changedFields(values, contact, fieldTypes, fallbacks);
      // Wait for the local save: closing the form destroys this frame, and a pending message with it.
      if (Object.keys(changes).length > 0) {
        await send({ type: "saveContactFields", contactId: contact.id, fields: changes }).catch(() => undefined);
      }
    }
    finish({ type: "done", text: result.text, cursor: result.cursor });
  };

  const form = h("form", { class: "fields", novalidate: "" });
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    void submit();
  });
  form.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      void submit();
    }
  });
  let first: HTMLElement | null = null;
  for (const variable of parsed.variables) {
    const fallback = fromFallback.has(variable.key) ? (variable.default ?? fallbacks[variable.key]) : null;
    const control = fieldControl(variable, fieldsByKey.get(variable.key), values, refresh, fallback);
    first ??= control.input;
    form.append(control.row);
  }
  // A hidden submit button lets Enter in a single-line input submit the form.
  form.append(h("button", { type: "submit", hidden: true }));

  const city = contact?.fields.kota || contact?.fields.city || "";
  const picked = contact
    ? [
        h(
          "div",
          { class: "picked" },
          h("span", { class: "avatar", "aria-hidden": "true" }, initial(contact.name)),
          h(
            "div",
            { class: "grow tight" },
            h("b", {}, contact.name),
            h("span", { class: "small" }, [recognised ? "Dikenali dari chat ini" : contact.phones[0] ? displayPhone(contact.phones[0]) : "", city].filter(Boolean).join(" · ")),
          ),
          ...(changeContact ? [h("button", { type: "button", class: "text small", click: changeContact }, "Ganti")] : []),
        ),
      ]
    : [];
  app.replaceChildren(
    header(data, template),
    // Only the middle scrolls, so the buttons below always stay in view.
    h("div", { class: "form-scroll" }, ...picked, form, h("div", { class: "label" }, "Pratinjau"), preview, saveRow),
    h(
      "div",
      { class: "actions" },
      h("button", { type: "button", class: "text", click: cancel }, "Batal"),
      h("button", { type: "button", class: "primary", click: () => void submit() }, icon("corner-down-left", 18), "Sisipkan"),
    ),
  );
  refresh();
  first?.focus();
}

/** An input suited to the variable's field type, editing the raw stored value in `values`. */
function fieldControl(
  variable: Variable,
  field: FieldDefinition | undefined,
  values: Record<string, string>,
  onChange: () => void,
  /** The fallback the value was prefilled with, or null when it did not come from one. */
  fallback: string | null = null,
): { row: HTMLElement; input: HTMLElement } {
  const key = variable.key;
  const type = field?.type;
  const hint = h("small", { class: "hint" });
  // Shown while the value is still the fallback; typing something else removes it.
  const fallbackNote = h("small", { class: "fallback-note" }, "Dari fallback, kontak ini belum punya data ini");
  const currency = type === "currency" ? currencyFromCode(field!.options[0]) : null;
  const set = (raw: string) => {
    values[key] = raw;
    // Only when formatting changes something (a fallback like "belum dihitung" shows as typed).
    const shown = raw && (type === "number" || type === "currency") ? formatFieldValue(type, raw, currency?.code) : raw;
    hint.textContent = shown !== raw ? `Tampil sebagai: ${shown}` : "";
    fallbackNote.hidden = fallback === null || raw !== fallback;
    onChange();
  };

  let input: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  const choices = type === "choice" ? ["", ...field!.options] : !field ? variable.options : undefined;
  if (choices) {
    input = select(choices.map((o) => [o, o || "(kosong)"]), values[key]);
  } else if (type === "boolean") {
    input = select([["", "(kosong)"], ["true", "Ya"], ["false", "Tidak"]], values[key]);
  } else if (type === "longtext") {
    input = h("textarea", { rows: "3" });
  } else {
    const inputType = type === "date" ? "date" : type === "email" ? "email" : type === "url" ? "url" : type === "phone" ? "tel" : "text";
    input = h("input", { type: inputType });
    if (type === "number") input.setAttribute("inputmode", "decimal");
    if (type === "currency") input.setAttribute("inputmode", currency!.cents ? "decimal" : "numeric");
  }
  input.value = values[key] ?? "";
  input.id = `field-${key}`;
  input.addEventListener(input instanceof HTMLSelectElement ? "change" : "input", () => {
    if (type === "currency") {
      input.value = currency!.cents ? input.value.replace(",", ".").replace(/[^\d.-]/g, "") : input.value.replace(/[^\d-]/g, "");
    }
    if (type === "number") input.value = input.value.replace(",", ".");
    set(input.value);
  });
  set(input.value);

  const row = h("div", { class: "field" }, h("label", { for: input.id }, field?.label ?? key), input, fallbackNote, hint);
  return { row, input };
}

function select(options: string[][], value: string | undefined): HTMLSelectElement {
  const el = h("select");
  for (const [optionValue, label] of options) el.append(h("option", { value: optionValue }, label));
  el.value = value ?? "";
  return el;
}

void main();
