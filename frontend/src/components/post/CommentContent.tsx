import React, { useId, useLayoutEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Comment } from "../../types";
import { CatEmoteText } from "../emotes/CatEmoteText";

/** 按实际排版高度折叠正文，长换行和窄屏内容也不会占满评论区。 */
export const CommentContent: React.FC<{ comment: Comment }> = ({ comment }) => {
  const id = useId();
  const text = useRef<HTMLParagraphElement>(null);
  const [expanded, setExpanded] = useState(false);
  const [canExpand, setCanExpand] = useState(false);

  useLayoutEffect(() => {
    setExpanded(false);
  }, [comment.id, comment.content]);

  useLayoutEffect(() => {
    const element = text.current;
    if (!element) return;
    const measure = () => {
      // 表情可能撑高行盒，按实际裁切判断，展开时保留收起入口。
      if (!expanded) setCanExpand(element.scrollHeight > element.clientHeight + 1);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [comment.content, comment.replyTo, expanded]);

  return (
    <div className="mt-3">
      <p ref={text} id={id} data-comment-text
        className={`text-sm sm:text-base leading-relaxed whitespace-pre-wrap [overflow-wrap:anywhere]${expanded ? "" : " line-clamp-4"}`}>
        {comment.parentId && comment.replyToCommentId !== comment.parentId && <>
          <span className="text-ink-muted dark:text-night-muted">回复 </span>
          {comment.replyTo ? <Link to={"/u/" + encodeURIComponent(comment.replyTo.username)} className="font-semibold text-terracotta dark:text-terracotta-dark">
            @{comment.replyTo.displayName || comment.replyTo.username}
          </Link> : <span className="text-ink-muted dark:text-night-muted">已删除的回复</span>}
          {"："}
        </>}
        {comment.author.accountType === "BOT" ? comment.content.split(/(\/post\/[0-9]+)/g).map((part,index) => index % 2
          ? <Link key={index} to={part} className="font-semibold text-terracotta dark:text-terracotta-dark hover:underline">相关帖子</Link>
          : <React.Fragment key={index}>{part}</React.Fragment>) : <CatEmoteText text={comment.content} size={comment.parentId ? "reply" : "normal"} />}
      </p>
      {canExpand && <button type="button" aria-expanded={expanded} aria-controls={id}
        aria-label={expanded ? "收起评论全文" : "展开评论全文"}
        onClick={() => setExpanded(value => !value)}
        className="mt-1 inline-flex min-h-9 items-center text-sm font-semibold text-terracotta dark:text-terracotta-dark hover:underline underline-offset-4">
        {expanded ? "收起" : "展开全文"}
      </button>}
    </div>
  );
};
