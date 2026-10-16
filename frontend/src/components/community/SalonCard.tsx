import React from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight, Users } from "lucide-react";
import { Community } from "../../types";
import { Avatar } from "../ui/Avatar";
export const SalonCard: React.FC<{ community: Community }> = ({
  community,
}) => (
  <Link
    className="panel salon-card"
    to={"/s/" + encodeURIComponent(community.name.replace(/^c\//, ""))}
  >
    <div className="flex items-center justify-between">
      <Avatar
        src={community.icon}
        name={community.displayName || community.name}
        size="lg"
      />
      {community.joined && (
        <span className="text-xs font-semibold text-terracotta">已加入</span>
      )}
    </div>
    <h2>{community.displayName || community.name}</h2>
    <p>{community.description || "一起聊聊这个主题，分享你的发现。"}</p>
    <footer>
      <span className="flex gap-1.5 items-center">
        <Users size={15} />
        {community.membersCount} 位成员
      </span>
      <ArrowUpRight size={20} />
    </footer>
  </Link>
);
