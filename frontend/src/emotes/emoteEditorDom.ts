import { splitCatEmotes } from "./catEmotes";

/** 将浏览器产生的换行和不可编辑表情还原为接口使用的纯文本。 */
export function readEmoteText(root: Node): string {
  if (root.nodeType === Node.TEXT_NODE) return root.textContent || "";
  if (root instanceof HTMLElement) {
    if (root.dataset.emote) return root.dataset.emote;
    if (root.tagName === "BR") return "\n";
  }
  const nodes = Array.from(root.childNodes);
  if (nodes.length === 1 && nodes[0] instanceof HTMLBRElement) return "";
  let text = "";
  let previousBlock = false;
  nodes.forEach((node, index) => {
    const block = node instanceof HTMLElement && /^(DIV|P)$/.test(node.tagName);
    if (index > 0 && (previousBlock || (block && !text.endsWith("\n")))) text += "\n";
    text += readEmoteText(node);
    previousBlock = block;
  });
  return text;
}

/** 只生成注册表中的图片，其余输入通过文本节点转义，禁止粘贴任意 HTML。 */
export function emoteFragment(text: string, size: "normal" | "reply"): DocumentFragment {
  const fragment = document.createDocumentFragment();
  for (const part of splitCatEmotes(text)) {
    if (typeof part === "string") fragment.append(document.createTextNode(part));
    else {
      const wrapper = document.createElement("span");
      wrapper.contentEditable = "false";
      wrapper.dataset.emote = part.token;
      wrapper.className = "emote-editor-token";
      const image = document.createElement("img");
      image.src = part.src; image.alt = part.token; image.title = part.label;
      image.className = `cat-emote cat-emote--${size}`;
      image.draggable = false;
      wrapper.append(image); fragment.append(wrapper);
    }
  }
  return fragment;
}

export function fragmentHtml(text: string, size: "normal" | "reply"): string {
  const container = document.createElement("div");
  container.append(emoteFragment(text, size));
  return container.innerHTML;
}

/** 在已知纯文本偏移处恢复光标；一个表情作为整体，光标不会进入标记内部。 */
export function caretAt(root: HTMLElement, offset: number): Range {
  const range = document.createRange();
  let remaining = offset;
  const visit = (node: Node): boolean => {
    const token = node instanceof HTMLElement ? node.dataset.emote : undefined;
    if (token) {
      if (remaining <= token.length) {
        if (remaining === 0) range.setStartBefore(node); else range.setStartAfter(node);
        return true;
      }
      remaining -= token.length;
    } else if (node.nodeType === Node.TEXT_NODE) {
      const length = node.textContent?.length || 0;
      if (remaining <= length) { range.setStart(node, remaining); return true; }
      remaining -= length;
    } else {
      for (const child of Array.from(node.childNodes)) if (visit(child)) return true;
    }
    return false;
  };
  if (!visit(root)) { range.selectNodeContents(root); range.collapse(false); }
  range.collapse(true);
  return range;
}
