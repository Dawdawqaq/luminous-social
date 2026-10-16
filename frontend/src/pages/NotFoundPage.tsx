import React from "react";
import { Link } from "react-router-dom";
import { PageBackButton } from "../components/common/PageBackButton";
import { PageMascot } from "../components/common/PageMascot";

export const NotFoundPage: React.FC = () => {
  return (
    <div className="py-12 sm:py-16 text-center space-y-6 max-w-md mx-auto">
      <PageMascot pose="lost" className="lost-mascot" />
      <span className="font-sans text-6xl font-medium text-terracotta dark:text-terracotta-dark block">
        404
      </span>
      <div className="space-y-2">
        <h1 className="font-sans text-2xl font-extrabold text-ink-primary dark:text-night-ink">
          纸页未在此处被装订
        </h1>
        <p className="font-sans text-sm text-ink-muted dark:text-night-muted leading-relaxed">
          这个页面不存在，或内容已经被删除。
        </p>
      </div>
      <div className="flex flex-wrap items-center justify-center gap-4 pt-4">
        <PageBackButton />
        <Link to="/" className="ink-button small">前往首页</Link>
      </div>
    </div>
  );
};
