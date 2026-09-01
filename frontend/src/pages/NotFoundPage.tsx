import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="py-32 text-center space-y-6 max-w-md mx-auto">
      <span className="font-serif text-6xl font-medium text-terracotta dark:text-terracotta-dark block">
        404
      </span>
      <div className="space-y-2">
        <h1 className="font-serif text-2xl font-medium text-ink-primary dark:text-night-ink">
          纸页未在此处被装订
        </h1>
        <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted leading-relaxed">
          您所追寻的思想手稿或沙龙房间，可能已隐入历史的书卷之中。
        </p>
      </div>
      <Link to="/" className="inline-block pt-4">
        <Button size="sm">
          返回沙龙议题大厅 (Back to Hall)
        </Button>
      </Link>
    </div>
  );
};
