"use client";

import { useEffect, useRef } from "react";
import { Bold, Heading2, Italic, Link2, List, ListOrdered, Quote } from "lucide-react";

const TOOLS = [
  { name: "bold", icon: Bold, label: "Bold", command: "bold", arg: undefined },
  { name: "italic", icon: Italic, label: "Italic", command: "italic", arg: undefined },
  { name: "h2", icon: Heading2, label: "Heading", command: "formatBlock", arg: "h2" },
  { name: "ul", icon: List, label: "Bulleted list", command: "insertUnorderedList", arg: undefined },
  { name: "ol", icon: ListOrdered, label: "Numbered list", command: "insertOrderedList", arg: undefined },
  { name: "quote", icon: Quote, label: "Quote", command: "formatBlock", arg: "blockquote" },
  { name: "link", icon: Link2, label: "Link", command: "createLink", arg: undefined },
] as const;

/**
 * Lightweight rich-text editor (contentEditable + a small toolbar). The output is HTML that the
 * backend sanitises with an allow-list, so formatting is limited to safe tags.
 */
export function RichText({ value, onChange }: { value: string; onChange: (html: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const last = useRef(value);

  useEffect(() => {
    if (ref.current && value !== last.current) {
      ref.current.innerHTML = value;
      last.current = value;
    }
  }, [value]);
  useEffect(() => {
    if (ref.current) ref.current.innerHTML = value;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function emit() {
    const html = ref.current?.innerHTML ?? "";
    last.current = html;
    onChange(html);
  }

  function cmd(name: string, arg?: string) {
    ref.current?.focus();
    document.execCommand(name, false, arg);
    emit();
  }

  function onTool(name: string) {
    if (name === "link") {
      const url = window.prompt("Link address (https://… or /page)");
      if (url && /^(https:\/\/|\/|mailto:|tel:)/.test(url)) cmd("createLink", url);
      return;
    }
    const t = TOOLS.find((x) => x.name === name);
    if (t) cmd(t.command, t.arg);
  }

  return (
    <div className="overflow-hidden rounded-xl border border-black/10 bg-white focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/30">
      <div className="flex flex-wrap gap-0.5 border-b border-black/5 bg-black/[0.02] px-1.5 py-1">
        {TOOLS.map((t) => (
          <button
            key={t.name}
            type="button"
            title={t.label}
            aria-label={t.label}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => onTool(t.name)}
            className="text-ink-muted hover:text-ink grid size-8 place-items-center rounded-lg hover:bg-black/5"
          >
            <t.icon className="size-4" aria-hidden />
          </button>
        ))}
        <button
          type="button"
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => cmd("formatBlock", "p")}
          className="text-ink-muted hover:text-ink rounded-lg px-2 text-xs font-medium hover:bg-black/5"
        >
          Normal text
        </button>
      </div>
      <div
        ref={ref}
        contentEditable
        suppressContentEditableWarning
        role="textbox"
        aria-multiline="true"
        onInput={emit}
        onBlur={emit}
        className="rt-content min-h-36 px-4 py-3 text-sm leading-relaxed outline-none"
      />
    </div>
  );
}
