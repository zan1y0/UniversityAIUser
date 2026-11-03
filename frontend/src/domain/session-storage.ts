export function sessionValue(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}
export function saveSessionValue(key: string, value: string): void {
  try {
    sessionStorage.setItem(key, value);
  } catch {
    /* 存储被禁用时仍允许本次页面会话运行。 */
  }
}
export function removeSessionValue(key: string): void {
  try {
    sessionStorage.removeItem(key);
  } catch {
    /* 无持久游标可删除。 */
  }
}
