import type { User } from './types';

export function roleHome(user: User): string {
  if (user.activeRole === 'ADMIN') return '/admin';
  return user.activeRole === 'TEACHER' ? '/teacher' : '/dashboard';
}

export function allowedRolePath(user: User, path: string): boolean {
  if (['/login', '/activate', '/forbidden'].includes(path)) return true;
  if (path === '/account') return Boolean(user.activeRole);
  if (path === '/admin' || path.startsWith('/admin/')) return user.activeRole === 'ADMIN';
  if (user.activeRole === 'ADMIN') return false;
  if (user.activeRole === 'TEACHER') return path === '/teacher';
  return path !== '/teacher';
}
