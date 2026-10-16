import React, { forwardRef, useEffect, useId, useImperativeHandle, useLayoutEffect, useRef } from "react";
import { CatEmote } from "../../emotes/catEmotes";
import { caretAt, emoteFragment, fragmentHtml, readEmoteText } from "../../emotes/emoteEditorDom";
import { cn } from "../../utils";

export interface EmoteEditorHandle { focus: () => void; insertEmote: (emote: CatEmote) => void; insertText: (text: string) => void }
interface Props {
  value: string; onChange: (value: string) => void; maxLength: number;
  label: string; placeholder?: string; hint?: string; error?: string; rows?: number;
  disabled?: boolean; required?: boolean; size?: "normal" | "reply";
  onPaste?: (event: React.ClipboardEvent<HTMLDivElement>) => void;
  onError: (message: string) => void;
}

/** 原生编辑区保留浏览器输入和撤销能力，接口仍接收纯文本表情标记。 */
export const EmoteEditor = forwardRef<EmoteEditorHandle, Props>(({
  value, onChange, maxLength, label, placeholder, hint, error, rows = 3,
  disabled, required, size = "normal", onPaste, onError,
}, ref) => {
  const id = useId();
  const editor = useRef<HTMLDivElement>(null);
  const selection = useRef<Range | null>(null);
  const composing = useRef(false);
  const accepted = useRef(value);
  const compositionStart = useRef({ offset: value.length });

  const remember = () => {
    const current = window.getSelection();
    if (current?.rangeCount && editor.current?.contains(current.anchorNode) && editor.current.contains(current.focusNode)) {
      selection.current = current.getRangeAt(0).cloneRange();
    }
  };
  const restore = (range: Range) => {
    const current = window.getSelection();
    current?.removeAllRanges(); current?.addRange(range); selection.current = range.cloneRange();
  };
  const selectedRange = () => {
    const root = editor.current!;
    const saved = selection.current;
    if (saved && root.contains(saved.startContainer) && root.contains(saved.endContainer)) {
      const range = saved.cloneRange();
      const tokenAt = (node: Node) => (node instanceof HTMLElement ? node : node.parentElement)?.closest<HTMLElement>("[data-emote]");
      const startToken = tokenAt(range.startContainer), endToken = tokenAt(range.endContainer);
      // 浏览器可能把插入后的光标留在不可编辑节点内，下一次插入需移到整个表情之后。
      if (range.collapsed && startToken) { range.setStartAfter(startToken); range.collapse(true); }
      else { if (startToken) range.setStartBefore(startToken); if (endToken) range.setEndAfter(endToken); }
      return range;
    }
    const range = document.createRange(); range.selectNodeContents(root); range.collapse(false); return range;
  };
  const rebuild = (text: string, offset?: number) => {
    const root = editor.current!;
    root.replaceChildren(emoteFragment(text, size));
    root.dataset.empty = String(!text);
    selection.current = null;
    if (offset !== undefined) restore(caretAt(root, offset));
  };
  useLayoutEffect(() => {
    accepted.current = value;
    if (!composing.current && readEmoteText(editor.current!) !== value) rebuild(value);
  }, [value, size]);
  useEffect(() => {
    document.addEventListener("selectionchange", remember);
    return () => document.removeEventListener("selectionchange", remember);
  }, []);
  useEffect(() => {
    const root = editor.current!;
    const beforeInput = (event: InputEvent) => {
      if (event.isComposing || composing.current || !event.inputType.startsWith("insert")) return;
      remember();
      const range = selectedRange();
      const length = readEmoteText(root).length - readEmoteText(range.cloneContents()).length;
      const addition = event.data ?? (event.inputType === "insertParagraph" || event.inputType === "insertLineBreak" ? "\n" : "");
      if (length + addition.length > maxLength) {
        event.preventDefault(); onError(`最多 ${maxLength} 个字符，请删掉一些文字后再输入。`);
      }
    };
    root.addEventListener("beforeinput", beforeInput);
    return () => root.removeEventListener("beforeinput", beforeInput);
  }, [maxLength, onError]);

  const publish = () => {
    const root = editor.current!;
    const text = readEmoteText(root);
    root.dataset.empty = String(!text);
    remember();
    if (composing.current) return;
    if (text.length > maxLength) {
      rebuild(accepted.current, Math.min(compositionStart.current.offset, accepted.current.length));
      onError(`最多 ${maxLength} 个字符，请删掉一些文字后再输入。`);
      return;
    }
    accepted.current = text; onChange(text);
  };

  const insert = (text: string, emote = false) => {
    if (disabled || composing.current) return;
    const root = editor.current!;
    const range = selectedRange();
    const current = readEmoteText(root);
    const nextLength = current.length - readEmoteText(range.cloneContents()).length + text.length;
    if (nextLength > maxLength) {
      onError(emote ? "剩余字数不足，请删掉一些文字后再添加表情。" : `最多 ${maxLength} 个字符，请缩短粘贴内容。`);
      root.focus({ preventScroll: true }); restore(range); return;
    }
    root.focus({ preventScroll: true }); restore(range);
    // 使用浏览器编辑命令将操作加入原生撤销栈；HTML 仅由转义文字和固定表情构成。
    document.execCommand("insertHTML", false, fragmentHtml(text, size));
    remember(); restore(selectedRange());
    publish();
  };
  useImperativeHandle(ref, () => ({
    focus: () => editor.current?.focus(), insertEmote: emote => insert(emote.token, true), insertText: text => insert(text, false),
  }));

  const copy = (event: React.ClipboardEvent<HTMLDivElement>, cut: boolean) => {
    remember();
    const range = selectedRange();
    if (range.collapsed) return;
    event.preventDefault(); event.clipboardData.setData("text/plain", readEmoteText(range.cloneContents()));
    if (cut && !disabled) { restore(range); document.execCommand("delete"); publish(); }
  };

  return <div className="w-full space-y-2 text-left">
    <label id={id + "-label"} className="block text-sm font-semibold" onClick={() => editor.current?.focus()}>{label}</label>
    <div ref={editor} role="textbox" tabIndex={disabled ? -1 : 0} contentEditable={!disabled}
      suppressContentEditableWarning aria-multiline="true" aria-labelledby={id + "-label"}
      aria-required={required} aria-disabled={disabled} aria-invalid={!!error}
      aria-describedby={error || hint ? id + "-note" : undefined}
      data-placeholder={placeholder} data-empty={!value} spellCheck
      className={cn("emote-editor w-full rounded-md border-[1.5px] border-ink-primary bg-paper-50 px-3.5 py-3 text-ink-primary dark:bg-night-surface dark:text-night-ink dark:border-night-border", error && "border-red-600")}
      style={{ minHeight: `${rows * 1.625 + 1.5}rem` }}
      onInput={publish} onBlur={remember} onKeyUp={remember} onMouseUp={remember}
      onCompositionStart={() => {
        composing.current = true; remember();
        const prefix = selectedRange(); prefix.setStart(editor.current!, 0);
        compositionStart.current = { offset: readEmoteText(prefix.cloneContents()).length };
      }}
      onCompositionEnd={() => { composing.current = false; publish(); }}
      onPaste={event => {
        onPaste?.(event);
        if (event.defaultPrevented) return;
        event.preventDefault(); remember(); insert(event.clipboardData.getData("text/plain"));
      }}
      onCopy={event => copy(event, false)} onCut={event => copy(event, true)}
      onDragOver={event => event.preventDefault()} onDrop={event => event.preventDefault()}
      onKeyDown={remember} />
    {(error || hint) && <p id={id + "-note"} className={cn("text-xs", error ? "text-red-600 dark:text-red-400" : "text-ink-muted dark:text-night-muted")}>{error || hint}</p>}
  </div>;
});
EmoteEditor.displayName = "EmoteEditor";
