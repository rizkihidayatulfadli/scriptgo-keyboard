// A real Lexical rich-text editor, the editor WhatsApp Web uses.
import { $getRoot, createEditor } from "lexical";
import { HeadingNode, QuoteNode, registerRichText } from "@lexical/rich-text";

const editor = createEditor({
  namespace: "e2e",
  nodes: [HeadingNode, QuoteNode],
  onError: (e) => {
    throw e;
  },
});
editor.setRootElement(document.getElementById("lexical"));
registerRichText(editor);
(window as unknown as { lexicalText: () => string }).lexicalText = () =>
  editor.getEditorState().read(() => $getRoot().getTextContent());
