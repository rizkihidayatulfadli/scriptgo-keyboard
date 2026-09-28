// WhatsApp Web helpers for the content script. Kept free of libphonenumber so the script that
// runs on every page stays small.

/** What the open WhatsApp chat tells us about the person: the header title and, if known, the number. */
export interface ChatHint {
  title: string | null;
  /** E.164 */
  phone: string | null;
}

/**
 * The chat partner's number in a WhatsApp Web message id such as `false_6281234567890@c.us_3EB0…`,
 * as E.164. Group chats (`@g.us`, whose ids end with the sender) and hidden-number ids (`@lid`) give null.
 */
export function phoneFromWhatsAppId(dataId: string): string | null {
  const m = /^(?:true|false)_([1-9]\d{5,14})@c\.us_/.exec(dataId);
  return m ? `+${m[1]}` : null;
}
