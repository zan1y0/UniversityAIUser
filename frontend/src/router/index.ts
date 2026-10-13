import { createRouter, createWebHistory } from 'vue-router';
import { useAppStore } from '../stores/app';
export const modules = [
  {
    path: '/dashboard',
    title: '学习工作台',
    short: '学习数据',
    module: 'M10',
    icon: 'dashboard',
    group: '概览',
    subtitle: '看见每一步积累，让进步有迹可循。',
  },
  {
    path: '/companion',
    title: '数字人陪伴督学',
    short: '陪伴督学',
    module: 'M01',
    icon: 'companion',
    group: '学习',
    subtitle: '有人陪你坚持，也有人提醒你适时休息。',
  },
  {
    path: '/schools',
    title: '智能择校与专业分析',
    short: '智能择校',
    module: 'M02',
    icon: 'school',
    group: '学习',
    subtitle: '从自身基础出发，找到适合你的备考目标。',
  },
  {
    path: '/public-courses',
    title: '公共课智能辅导',
    short: '公共课学习',
    module: 'M03',
    icon: 'book',
    group: '学习',
    subtitle: '政治、英语、数学，把知识学成自己的。',
  },
  {
    path: '/professional-courses',
    title: '专业课学习支持',
    short: '专业课学习',
    module: 'M04',
    icon: 'layers',
    group: '学习',
    subtitle: '沿着知识体系，掌握重点与解题思路。',
  },
  {
    path: '/qa',
    title: 'AI 答疑与解题',
    short: 'AI 答疑',
    module: 'M05',
    icon: 'chat',
    group: '学习',
    subtitle: '不止知道答案，更要理解每一步为什么。',
  },
  {
    path: '/practice',
    title: '刷题与错题专项训练',
    short: '刷题与错题',
    module: 'M06',
    icon: 'pen',
    group: '提升',
    subtitle: '每一道错题，都能成为下一次的进步。',
  },
  {
    path: '/planning',
    title: '个性化学习规划',
    short: '学习计划',
    module: 'M07',
    icon: 'calendar',
    group: '提升',
    subtitle: '把大目标拆成今天能完成的小任务。',
  },
  {
    path: '/exams',
    title: '模考与估分系统',
    short: '模考与估分',
    module: 'M08',
    icon: 'exam',
    group: '提升',
    subtitle: '在限时练习中找到节奏，查漏补缺。',
  },
  {
    path: '/interview',
    title: '复试 AI 训练',
    short: '复试训练',
    module: 'M09',
    icon: 'mic',
    group: '提升',
    subtitle: '练习表达与思考，从容走进下一段旅程。',
  },
  {
    path: '/news',
    title: '资讯与政策提醒',
    short: '资讯与政策',
    module: 'M11',
    icon: 'bell',
    group: '资讯',
    subtitle: '及时核对关键事项，让备考有条不紊。',
  },
];
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/dashboard', component: () => import('../views/DashboardView.vue') },
    { path: '/companion', component: () => import('../views/CompanionView.vue') },
    { path: '/schools', component: () => import('../views/SchoolsView.vue') },
    { path: '/public-courses', component: () => import('../views/PublicCoursesView.vue') },
    {
      path: '/professional-courses',
      component: () => import('../views/ProfessionalCoursesView.vue'),
    },
    { path: '/qa', component: () => import('../views/QaView.vue') },
    { path: '/practice', component: () => import('../views/PracticeView.vue') },
    { path: '/planning', component: () => import('../views/PlanningView.vue') },
    { path: '/exams', component: () => import('../views/ExamsView.vue') },
    { path: '/interview', component: () => import('../views/InterviewView.vue') },
    { path: '/news', component: () => import('../views/NewsView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') },
  ],
});
router.beforeEach((to) => {
  const app = useAppStore();
  if (app.mode === 'live' && !app.user && to.path !== '/login')
    return { path: '/login', query: { redirect: to.fullPath } };
});
router.afterEach((to) => {
  document.title = `${modules.find((m) => m.path === to.path)?.title || to.meta.title || '页面'} · 研途 AI`;
});
export default router;
