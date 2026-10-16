import { Comment } from "../../types";

export interface CommentFloor {
  id: string;
  root?: Comment;
  replies: Comment[];
}

/** 只按服务端主评论编号分组，回复他人的楼内评论仍归入同一楼。 */
export function groupCommentFloors(comments: Comment[]): CommentFloor[] {
  const floors = new Map<string, CommentFloor>();
  for (const comment of comments) {
    const id = comment.parentId || comment.id;
    const floor = floors.get(id) || { id, replies: [] };
    if (comment.parentId) floor.replies.push(comment);
    else floor.root = comment;
    floors.set(id, floor);
  }
  return [...floors.values()];
}
