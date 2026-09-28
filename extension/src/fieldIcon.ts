// Filled field icons (Phosphor, see fieldIconPaths.ts) as inline SVG, and the field's badge.

import { FIELD_ICON_PATHS } from "./fieldIconPaths";
import { h } from "./ui";

const SVG = "http://www.w3.org/2000/svg";

/** The icon called name (a text icon for an unknown name), filled with the current colour. */
export function fieldIcon(name: string, size = 20): SVGSVGElement {
  const svg = document.createElementNS(SVG, "svg");
  svg.setAttribute("viewBox", "0 0 256 256");
  svg.setAttribute("width", String(size));
  svg.setAttribute("height", String(size));
  svg.setAttribute("fill", "currentColor");
  svg.setAttribute("aria-hidden", "true");
  for (const d of FIELD_ICON_PATHS[name] ?? FIELD_ICON_PATHS["text-aa"]) {
    const path = document.createElementNS(SVG, "path");
    path.setAttribute("d", d);
    svg.append(path);
  }
  return svg;
}

/** A field's icon on the gradient tile (the app's IconBadge); big for the editor header. */
export function fieldBadge(name: string, big = false): HTMLElement {
  return h("span", { class: big ? "letter big field-badge" : "letter field-badge", "data-icon": name }, fieldIcon(name, big ? 26 : 20));
}
