export interface CatEmote {
  id: string;
  label: string;
  token: string;
  src: string;
}

export const CAT_EMOTES: readonly CatEmote[] = [
  { id: "happy", label: "开心", token: "[猫猫开心]", src: "/emotes/cats/happy.webp" },
  { id: "like", label: "点赞", token: "[猫猫点赞]", src: "/emotes/cats/like.webp" },
  { id: "laugh", label: "大笑", token: "[猫猫大笑]", src: "/emotes/cats/laugh.webp" },
  { id: "confused", label: "疑惑", token: "[猫猫疑惑]", src: "/emotes/cats/confused.webp" },
  { id: "sad", label: "委屈", token: "[猫猫委屈]", src: "/emotes/cats/sad.webp" },
  { id: "love", label: "喜欢", token: "[猫猫喜欢]", src: "/emotes/cats/love.webp" },
];

const byToken = new Map(CAT_EMOTES.map(emote => [emote.token, emote]));

/** 只识别内置表情，未知标记保留为文字，不解析用户提供的图片地址或 HTML。 */
export function splitCatEmotes(text: string): (string | CatEmote)[] {
  const parts: (string | CatEmote)[] = [];
  let position = 0;
  for (const match of text.matchAll(/\[猫猫[^\]\r\n]{1,8}\]/g)) {
    const emote = byToken.get(match[0]);
    if (!emote) continue;
    if (match.index! > position) parts.push(text.slice(position, match.index));
    parts.push(emote);
    position = match.index! + match[0].length;
  }
  if (position < text.length) parts.push(text.slice(position));
  return parts;
}

export function containsCatEmote(text: string): boolean {
  return splitCatEmotes(text).some(part => typeof part !== "string");
}
