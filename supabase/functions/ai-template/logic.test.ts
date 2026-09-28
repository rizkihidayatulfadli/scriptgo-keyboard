// Run: node --test supabase/functions/ai-template/logic.test.ts (Node 22.18+ strips the types).
import { test } from "node:test";
import assert from "node:assert/strict";
import { buildMessages, parseRequest, parseResult, variablesOf } from "./logic.ts";

test("variablesOf lists keys, lowercased, skipping escaped braces and invalid keys", () => {
  assert.deepEqual(
    variablesOf("Halo {Nama|Kak}, ongkir ke {kota} via {kurir:JNE,J&T}. \\{bukan} {ada spasi} {@tanggal} {@kursor}"),
    ["nama", "kota", "kurir", "@tanggal", "@kursor"],
  );
  assert.deepEqual(variablesOf("tanpa variabel"), []);
});

test("parseRequest accepts create and rewrite, and trims inputs", () => {
  assert.deepEqual(parseRequest({ mode: "create", description: "  balasan ongkir  ", fields: ["kota", "Bad Key", "ukuran"] }), {
    mode: "create",
    description: "balasan ongkir",
    fields: ["kota", "ukuran"],
  });
  assert.deepEqual(parseRequest({ mode: "rewrite", style: "shorter", title: "Ongkir", body: "Halo {nama}" }), {
    mode: "rewrite",
    style: "shorter",
    title: "Ongkir",
    body: "Halo {nama}",
    fields: [],
  });
});

test("parseRequest rejects missing, too long, or unknown input", () => {
  assert.equal(parseRequest({ mode: "create", description: "" }), null);
  assert.equal(parseRequest({ mode: "create", description: "x".repeat(501) }), null);
  assert.equal(parseRequest({ mode: "rewrite", style: "rude", title: "a", body: "b" }), null);
  assert.equal(parseRequest({ mode: "rewrite", style: "polite", title: "a", body: "x".repeat(4001) }), null);
  assert.equal(parseRequest({ mode: "delete" }), null);
  assert.equal(parseRequest(null), null);
});

test("buildMessages puts the syntax rules in the system prompt and only the user's text in the user turn", () => {
  const messages = buildMessages({ mode: "create", description: "balasan ongkir ramah", fields: ["kota"] });
  assert.equal(messages[0].role, "system");
  assert.match(messages[0].content, /\{nama\}/);
  assert.match(messages[0].content, /JSON/);
  assert.equal(messages[1].role, "user");
  assert.match(messages[1].content, /balasan ongkir ramah/);
  assert.match(messages[1].content, /kota/);
});

test("parseResult reads JSON, even inside a code fence, and normalizes the shortcut", () => {
  const text = '```json\n{"title": "Info Ongkir", "shortcut": "Ongkir Bdg", "body": "Halo {nama}, ongkir ke {kota} Rp{ongkir}."}\n```';
  assert.deepEqual(parseResult(text, { mode: "create", description: "x", fields: [] }), {
    title: "Info Ongkir",
    shortcut: "ongkir_bdg",
    body: "Halo {nama}, ongkir ke {kota} Rp{ongkir}.",
  });
});

test("parseResult rejects unusable output", () => {
  const create = { mode: "create" as const, description: "x", fields: [] };
  assert.equal(parseResult("maaf, saya tidak bisa", create), null);
  assert.equal(parseResult('{"title": "", "shortcut": "a", "body": "b"}', create), null);
  assert.equal(parseResult(`{"title": "a", "shortcut": "a", "body": "${"x".repeat(2001)}"}`, create), null);
});

test("a rewrite must keep every variable of the original", () => {
  const rewrite = { mode: "rewrite" as const, style: "shorter" as const, title: "Ongkir", body: "Halo {nama}, ongkir ke {kota}.", fields: [] };
  assert.deepEqual(parseResult('{"title": "Ongkir", "shortcut": "ongkir", "body": "Kak {nama}, ongkir {kota}."}', rewrite), {
    title: "Ongkir",
    shortcut: "ongkir",
    body: "Kak {nama}, ongkir {kota}.",
  });
  assert.equal(parseResult('{"title": "Ongkir", "shortcut": "ongkir", "body": "Kak, ongkirnya segini."}', rewrite), null);
});
