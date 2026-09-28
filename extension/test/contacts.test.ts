import { describe, expect, it } from "vitest";
import {
  CHANNEL_DEFAULT_OPTIONS,
  changedFields,
  channelOptionFor,
  fallbackKeys,
  matchChatContact,
  newContactPrefill,
  nextUpdatedAt,
  normalizePhone,
  prefillForm,
  renderForm,
  shouldApplyRemote,
  WHATSAPP_WEB,
  type ContactData,
} from "../src/engine/contacts";
import { parse, type FieldType } from "../src/engine/template";
import { phoneFromWhatsAppId } from "../src/engine/whatsapp";

describe("normalizePhone", () => {
  it("normalizes common Indonesian forms to the same E.164", () => {
    for (const raw of ["081234567890", "0812-3456-7890", "+62 812 3456 7890", "6281234567890", "(0812) 3456 7890"]) {
      expect(normalizePhone(raw), raw).toBe("+6281234567890");
    }
  });

  it("keeps foreign numbers", () => expect(normalizePhone("+65 9123 4567")).toBe("+6591234567"));

  it("rejects values that are not phone numbers", () => {
    for (const raw of ["", "abc", "12", "Budi"]) expect(normalizePhone(raw), raw).toBeNull();
  });
});

describe("template form", () => {
  const parsed = parse("Halo {nama|Kak}, total {total} ke {kota} via {kurir:JNE,J&T}. WA {nomor}. Catatan: {catatan}");
  const fieldTypes: Record<string, FieldType> = { total: "currency", kota: "text" };
  const budi: ContactData = { name: "Budi", phones: ["+6281234567890"], fields: { kota: "Bandung", total: "" } };
  const now = { year: 2026, month: 9, day: 26, hour: 10, minute: 0 };

  it("prefills from the contact, then defaults and options", () => {
    expect(prefillForm(parsed.variables, budi, fieldTypes)).toEqual({
      nama: "Budi", total: "", kota: "Bandung", kurir: "JNE", nomor: "081234567890", catatan: "",
    });
    expect(prefillForm(parsed.variables, null, fieldTypes)).toEqual({
      nama: "Kak", total: "", kota: "", kurir: "JNE", nomor: "", catatan: "",
    });
  });

  it("renders raw field values with their type format", () => {
    const values = { ...prefillForm(parsed.variables, budi, fieldTypes), total: "150000", catatan: "cepat ya" };
    expect(renderForm(parsed, values, fieldTypes, now).text).toBe(
      "Halo Budi, total Rp150.000 ke Bandung via JNE. WA 081234567890. Catatan: cepat ya",
    );
  });

  it("formats currency fields in their own currency", () => {
    const values = { ...prefillForm(parsed.variables, budi, fieldTypes), total: "1500.5" };
    expect(renderForm(parsed, values, fieldTypes, now, { total: "USD" }).text).toBe(
      "Halo Budi, total US$1.500,50 ke Bandung via JNE. WA 081234567890. Catatan: ",
    );
  });

  it("marks values that come from the fallback because the contact has none", () => {
    const template = parse("{nama|Kak} {kota|kotamu} {total|0} {catatan|-} {nomor}");
    expect([...fallbackKeys(template.variables, budi, fieldTypes)]).toEqual(["total"]);
    expect([...fallbackKeys(template.variables, { name: "", phones: [], fields: {} }, fieldTypes)]).toEqual(["nama", "kota", "total"]);
    expect([...fallbackKeys(template.variables, null, fieldTypes)]).toEqual([]);
  });

  it("saves back only changed custom fields", () => {
    const values = { nama: "Pak Budi", total: "150000", kota: "Bandung", catatan: "x", kurir: "J&T" };
    expect(changedFields(values, budi, fieldTypes)).toEqual({ total: "150000" });
  });

  it("uses a field's fallback when neither the contact nor the template has a value", () => {
    const template = parse("{kota} {total|0} {kurir:JNE,J&T} {nama}");
    const types: Record<string, FieldType> = { ...fieldTypes, kurir: "choice" };
    const fallbacks = { kota: "Jakarta", total: "5000", kurir: "SiCepat" };
    expect(prefillForm(template.variables, null, types, fallbacks)).toEqual({ kota: "Jakarta", total: "0", kurir: "JNE", nama: "" });
    expect(prefillForm(template.variables, budi, types, fallbacks).kota).toBe("Bandung");
    expect([...fallbackKeys(template.variables, { name: "Sari", phones: [], fields: {} }, types, fallbacks)]).toEqual(["kota", "total"]);
    expect([...fallbackKeys(template.variables, budi, types, fallbacks)]).toEqual(["total"]);
  });

  it("does not save a field's fallback to a contact without a value", () => {
    const fallbacks = { kota: "Jakarta", total: "0" };
    const sari: ContactData = { name: "Sari", phones: [], fields: { total: "5000" } };
    expect(changedFields({ kota: "Jakarta" }, sari, fieldTypes, fallbacks)).toEqual({});
    expect(changedFields({ kota: "Solo" }, sari, fieldTypes, fallbacks)).toEqual({ kota: "Solo" });
    expect(changedFields({ total: "0" }, sari, fieldTypes, fallbacks)).toEqual({ total: "0" });
  });

  it("ignores inherited object keys", () => {
    const tricky = parse("{constructor} {tostring}");
    expect(prefillForm(tricky.variables, budi, fieldTypes)).toEqual({ constructor: "", tostring: "" });
    expect(changedFields({ constructor: "x" }, budi, fieldTypes)).toEqual({});
  });
});

describe("sync merge", () => {
  it("applies remote when missing locally or newer", () => {
    expect(shouldApplyRemote(null, 5)).toBe(true);
    expect(shouldApplyRemote({ updatedAt: 4, dirty: true }, 5)).toBe(true);
  });

  it("keeps newer local edits", () => {
    expect(shouldApplyRemote({ updatedAt: 6, dirty: true }, 5)).toBe(false);
    expect(shouldApplyRemote({ updatedAt: 6, dirty: false }, 5)).toBe(false);
  });

  it("keeps unsynced local edits on ties", () => {
    expect(shouldApplyRemote({ updatedAt: 5, dirty: true }, 5)).toBe(false);
    expect(shouldApplyRemote({ updatedAt: 5, dirty: false }, 5)).toBe(true);
  });

  it("moves edit timestamps forward", () => {
    expect(nextUpdatedAt(null, 100)).toBe(100);
    expect(nextUpdatedAt(50, 100)).toBe(100);
    expect(nextUpdatedAt(200, 100)).toBe(201);
  });
});

describe("WhatsApp Web contact detection", () => {
  const budi = { name: "Budi Santoso", phones: ["+6281234567890"] };
  const ani = { name: "Ani", phones: ["+6285600001111"] };
  const ani2 = { name: "ani", phones: [] };
  const contacts = [budi, ani, ani2];

  it("reads the number from a message id", () => {
    expect(phoneFromWhatsAppId("false_6281234567890@c.us_3EB0ABCDEF")).toBe("+6281234567890");
    expect(phoneFromWhatsAppId("true_6281234567890@c.us_3EB0")).toBe("+6281234567890");
    expect(phoneFromWhatsAppId("false_120363000000000000@g.us_3EB0_6281234567890@c.us")).toBeNull();
    expect(phoneFromWhatsAppId("false_185000000000000@lid_3EB0")).toBeNull();
    expect(phoneFromWhatsAppId("hello")).toBeNull();
  });

  it("matches by number first, including a title that is a number", () => {
    expect(matchChatContact(contacts, { title: "Pak Budi", phone: "+6281234567890" })).toBe(budi);
    expect(matchChatContact(contacts, { title: "+62 812-3456-7890", phone: null })).toBe(budi);
  });

  it("falls back to a unique name match", () => {
    expect(matchChatContact(contacts, { title: "  budi   santoso ", phone: null })).toBe(budi);
    expect(matchChatContact(contacts, { title: "Ani", phone: null })).toBeNull();
    expect(matchChatContact(contacts, { title: "Citra", phone: null })).toBeNull();
    expect(matchChatContact(contacts, { title: null, phone: null })).toBeNull();
  });
});

describe("new contact from the form", () => {
  it("maps WhatsApp Web to the user's own spelling of the option", () => {
    expect(channelOptionFor(WHATSAPP_WEB, CHANNEL_DEFAULT_OPTIONS)).toBe("WhatsApp");
    expect(channelOptionFor(WHATSAPP_WEB, ["whatsapp ", "IG"])).toBe("whatsapp ");
    expect(channelOptionFor("com.whatsapp.w4b", CHANNEL_DEFAULT_OPTIONS)).toBe("WA Business");
  });

  it("gives null for unknown sites or a removed option", () => {
    expect(channelOptionFor("example.com", CHANNEL_DEFAULT_OPTIONS)).toBeNull();
    expect(channelOptionFor(null, CHANNEL_DEFAULT_OPTIONS)).toBeNull();
    expect(channelOptionFor(WHATSAPP_WEB, ["Instagram"])).toBeNull();
    expect(channelOptionFor("constructor", CHANNEL_DEFAULT_OPTIONS)).toBeNull();
  });

  it("starts from the chat's name and number", () => {
    expect(newContactPrefill({ title: "Rina Hijab", phone: "+6281234567890" }, "")).toEqual({ name: "Rina Hijab", phone: "+6281234567890" });
  });

  it("uses a chat title that is a number as the number, and the search text as the name", () => {
    expect(newContactPrefill({ title: "+62 812-3456-7890", phone: null }, "Rina")).toEqual({ name: "Rina", phone: "+6281234567890" });
  });

  it("without a chat, takes a typed name or number from the search box", () => {
    expect(newContactPrefill(null, "Budi")).toEqual({ name: "Budi", phone: "" });
    expect(newContactPrefill(null, "0812 3456 7890")).toEqual({ name: "", phone: "+6281234567890" });
    expect(newContactPrefill(null, "")).toEqual({ name: "", phone: "" });
  });
});
