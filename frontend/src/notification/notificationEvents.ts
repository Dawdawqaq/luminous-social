export const NOTIFICATIONS_CHANGED = "luminous:notifications-changed";
/** 只在通知操作成功提交后广播，所有未读数使用同一个刷新入口。 */
export function notificationsChanged() {
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));
}
