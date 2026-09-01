import React, { useEffect, useRef } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

// 记录各路由路径的滚动视口位置
const scrollPositions = new Map<string, number>();

export const ScrollToTop: React.FC = () => {
  const location = useLocation();
  const navType = useNavigationType();
  const prevKeyRef = useRef(location.pathname + location.search);

  // 监听并实时记录当前页面的滚动高度
  useEffect(() => {
    const handleScroll = () => {
      scrollPositions.set(location.pathname + location.search, window.scrollY);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [location.pathname, location.search]);

  useEffect(() => {
    const currentKey = location.pathname + location.search;

    // 处理锚点跳转（例如 #responses）
    if (location.hash) {
      const targetId = location.hash.replace('#', '');
      const element = document.getElementById(targetId);
      if (element) {
        element.scrollIntoView({ behavior: 'smooth' });
        return;
      }
    }

    if (navType === 'POP') {
      // 后退/返回上一级导航：恢复上一次浏览该页面的精确高度
      const savedY = scrollPositions.get(currentKey) ?? 0;
      window.scrollTo({ top: savedY, left: 0, behavior: 'instant' });
      requestAnimationFrame(() => {
        window.scrollTo({ top: savedY, left: 0, behavior: 'instant' });
      });
    } else {
      // 前进导航（点击新文章、新页面）：重置到页面顶端
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    }

    prevKeyRef.current = currentKey;
  }, [location.pathname, location.search, location.hash, navType]);

  return null;
};
