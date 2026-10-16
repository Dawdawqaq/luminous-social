import React from "react";
import { splitCatEmotes } from "../../emotes/catEmotes";

export const CatEmoteText: React.FC<{ text: string; size?: "normal" | "reply" | "compact" }> = ({ text, size = "normal" }) => (
  <>{splitCatEmotes(text).map((part, index) => typeof part === "string" ? part : (
    <img key={index} src={part.src} alt={part.token} title={part.label} width={40} height={40}
      draggable={false} className={`cat-emote cat-emote--${size}`} />
  ))}</>
);
