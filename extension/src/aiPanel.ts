// The AI box in the template editor (Kelola), like the app's AiTemplateSheet: an empty template gets
// "Buat dengan AI" (a description in, a whole template out), one with text gets "Rapikan dengan AI"
// (a style in, the text rewritten, shown next to the original). Nothing changes in the editor until
// Pakai. The background worker makes the call (ai.ts has the wording and checks).

import { AI_DESCRIPTION_MAX, AI_REPORT_REASONS, AI_STYLES, quotaEmptyText, quotaText, type AiInput, type AiQuota, type AiStyle, type AiTemplate } from "./ai";
import { icon } from "./icons";
import { send } from "./store";
import { h } from "./ui";

export interface AiPanelOptions {
  /** The template as it is now; an empty body means "Buat". */
  current: () => AiTemplate;
  /** Pakai: the chosen result. */
  onUse: (result: AiTemplate, created: boolean) => void;
}

export interface AiPanel {
  /** The button that opens and closes the box. */
  button: HTMLElement;
  /** The box, empty while closed. */
  box: HTMLElement;
  /** Call when the body changes, so the button says Buat or Rapikan. */
  refresh(): void;
}

export function aiPanel(opts: AiPanelOptions): AiPanel {
  const label = h("span", {});
  const button = h("button", { type: "button", class: "outline small ai-button", "aria-expanded": "false" }, icon("sparkles", 16), label);
  const box = h("div", { class: "ai-box", hidden: true });
  let quota: AiQuota | null = null;

  const rewriting = () => opts.current().body.trim() !== "";
  const refresh = () => {
    label.textContent = rewriting() ? "Rapikan dengan AI" : "Buat dengan AI";
  };

  const close = () => {
    box.hidden = true;
    box.replaceChildren();
    button.setAttribute("aria-expanded", "false");
  };

  const open = () => {
    const before = opts.current();
    const create = before.body.trim() === "";
    let description = "";
    let style: AiStyle = AI_STYLES[0][0];
    let busy = false;
    let error = "";
    let result: AiTemplate | null = null;
    /** What produced [result], for a report. */
    let asked: AiInput | null = null;
    /** "Laporkan hasil ini": closed, choosing a reason, or its outcome. */
    let report: "closed" | "open" | "sending" | "sent" | string = "closed";

    const run = async () => {
      busy = true;
      error = "";
      report = "closed";
      render();
      const input: AiInput = create ? { mode: "create", description } : { mode: "rewrite", style, title: before.title, body: before.body };
      asked = input;
      const reply = await send({ type: "aiTemplate", input }).catch(() => ({ ok: false as const, error: "Terjadi kesalahan", quota: null }));
      busy = false;
      if (reply.quota) quota = reply.quota;
      if (reply.ok && reply.template) result = reply.template;
      else error = reply.ok ? "Terjadi kesalahan" : reply.error;
      render();
    };

    const header = () =>
      h(
        "div",
        { class: "ai-head" },
        h("span", { class: "ai-badge" }, icon("sparkles", 18)),
        h("div", {}, h("b", {}, create ? "Buat dengan AI" : "Rapikan dengan AI"), ...(quota ? [h("span", { class: "hint ai-quota" }, quotaText(quota))] : [])),
        h("button", { type: "button", class: "icon-button", "aria-label": "Tutup AI", click: close }, icon("x", 18)),
      );

    const resultView = (r: AiTemplate) => [
      ...(create
        ? []
        : [h("span", { class: "label-strong" }, "Sebelum"), h("div", { class: "bubble muted" }, before.body), h("span", { class: "label-strong" }, "Sesudah")]),
      h(
        "div",
        { class: "bubble ai-result" },
        ...(create ? [h("b", {}, r.title), h("span", { class: "chip static" }, "/" + r.shortcut)] : []),
        h("div", {}, r.body),
      ),
      h(
        "div",
        { class: "actions" },
        h("button", { type: "button", class: "outline small", click: () => ((result = null), render()) }, "Coba lagi"),
        h("button", { type: "button", class: "primary small", click: () => (opts.onUse(r, create), close()) }, icon("check", 16), "Pakai"),
      ),
      reportView(r),
    ];

    // Google Play asks apps with generative AI to let users report offensive output; same here.
    const reportView = (r: AiTemplate): HTMLElement => {
      if (report === "sent") return h("p", { class: "hint", role: "status" }, "Terima kasih, laporanmu terkirim. Kami pakai untuk memperbaiki AI.");
      if (report === "closed") return h("button", { type: "button", class: "text small ai-report", click: () => ((report = "open"), render()) }, "Laporkan hasil ini");
      const failed = report !== "open" && report !== "sending" ? report : "";
      return h(
        "div",
        { class: "stack ai-report-box" },
        h("span", { class: "label-strong" }, "Apa masalahnya?"),
        h(
          "div",
          { class: "chips" },
          ...AI_REPORT_REASONS.map(([reason, label]) =>
            h(
              "button",
              {
                type: "button",
                class: "chip",
                disabled: report === "sending",
                click: async () => {
                  report = "sending";
                  render();
                  const reply = await send({ type: "aiReport", input: asked!, result: r.body, reason }).catch(() => ({ ok: false as const, error: "Terjadi kesalahan" }));
                  report = reply.ok ? "sent" : `Laporan belum terkirim. ${reply.error}`;
                  render();
                },
              },
              label,
            ),
          ),
        ),
        ...(failed ? [h("p", { class: "error", role: "alert" }, failed)] : []),
      );
    };

    const inputView = () => {
      const empty = quota?.remaining === 0;
      const go = h(
        "button",
        { type: "button", class: "primary small", disabled: busy || empty || (create && !description.trim()), click: () => void run() },
        busy ? "AI sedang menulis…" : create ? "Buat" : "Rapikan",
      );
      let control: HTMLElement;
      if (create) {
        const area = h("textarea", {
          rows: "3",
          maxlength: String(AI_DESCRIPTION_MAX),
          "aria-label": "Mau template untuk apa?",
          placeholder: "Contoh: balas pembeli yang tanya ongkir ke kotanya, sebut kurir dan perkiraan sampai",
          disabled: busy,
        });
        area.value = description;
        area.addEventListener("input", () => {
          description = area.value;
          (go as HTMLButtonElement).disabled = busy || empty || !description.trim();
        });
        control = h("label", { class: "field" }, h("span", { class: "label-strong" }, "Mau template untuk apa?"), area);
      } else {
        control = h(
          "div",
          { class: "chips", role: "radiogroup", "aria-label": "Gaya" },
          ...AI_STYLES.map(([key, text]) =>
            h(
              "button",
              { type: "button", class: `chip${style === key ? " selected" : ""}`, role: "radio", "aria-checked": String(style === key), disabled: busy, click: () => ((style = key), render()) },
              text,
            ),
          ),
        );
      }
      return [
        control,
        ...(empty && quota ? [h("p", { class: "error" }, quotaEmptyText(quota))] : []),
        ...(error ? [h("p", { class: "error", role: "alert" }, error)] : []),
        h("div", { class: "actions" }, h("span", { class: "grow" }), go),
        h("p", { class: "hint" }, "Yang dikirim ke AI hanya teks di sini dan nama field. Jangan tulis data pribadi pelanggan."),
      ];
    };

    const render = () => {
      const focused = box.querySelector("textarea") === document.activeElement;
      box.replaceChildren(header(), ...(result ? resultView(result) : inputView()));
      if (focused) box.querySelector("textarea")?.focus();
    };

    box.hidden = false;
    button.setAttribute("aria-expanded", "true");
    render();
    box.querySelector<HTMLElement>("textarea, .chip")?.focus();
    void send({ type: "aiQuota" })
      .then((reply) => {
        if (reply.quota) quota = reply.quota;
        if (!box.hidden && !busy) render();
      })
      .catch(() => undefined);
  };

  button.addEventListener("click", () => (box.hidden ? open() : close()));
  refresh();
  return { button, box, refresh };
}
