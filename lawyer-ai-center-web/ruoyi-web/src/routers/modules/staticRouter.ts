import type { RouteRecordRaw } from 'vue-router';
import { HOME_URL } from '@/config';

export const layoutRouter: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: HOME_URL,
    component: () => import('@/layouts/index.vue'),
    children: [
      {
        path: HOME_URL,
        name: 'chat',
        component: () => import('@/pages/chat/index.vue'),
        meta: {
          title: '通用聊天',
          isDefaultChat: true,
          icon: 'HomeFilled',
        },
      },
      {
        path: '/chat/:id',
        name: 'chatWithId',
        component: () => import('@/pages/chat/index.vue'),
        meta: {
          title: '聊天详情',
          isDefaultChat: false,
        },
      },
      {
        path: '/app-market',
        name: 'appMarket',
        component: () => import('@/pages/app-market/index.vue'),
        meta: {
          title: '应用市场',
          icon: 'Grid',
        },
      },
      {
        path: '/contract-review',
        name: 'contractReview',
        component: () => import('@/pages/contract-review/index.vue'),
        meta: {
          title: '合同审查',
          icon: 'DocumentChecked',
          requiresAuth: true,
        },
      },
      {
        path: '/media',
        name: 'mediaWorkbench',
        component: () => import('@/pages/media/index.vue'),
        meta: { title: '媒体工作台', stayOnAuthExpired: true },
      },
      { path: '/profile', name: 'profile', component: () => import('@/pages/profile/index.vue'), meta: { title: '个人中心', requiresAuth: true } },
      { path: '/help', name: 'help', component: () => import('@/pages/help/index.vue'), meta: { title: '帮助与数据来源' } },
      { path: '/billing', redirect: '/profile' },
      { path: '/drafting', name: 'drafting', component: () => import('@/pages/drafting/index.vue'), meta: { title: '文书起草', requiresAuth: true } },
      { path: '/templates', name: 'templateManagement', component: () => import('@/pages/templates/index.vue'), meta: { title: '模板管理', requiresAuth: true, stayOnAuthExpired: true } },
      { path: '/templates/categories', name: 'templateCategories', component: () => import('@/pages/templates/index.vue'), meta: { title: '模板分类', requiresAuth: true, stayOnAuthExpired: true } },
      { path: '/legal-opinion', name: 'legalOpinion', component: () => import('@/pages/legal-opinion/index.vue'), meta: { title: '法律意见书', requiresAuth: true } },
      { path: '/fact-search', name: 'factSearch', component: () => import('@/pages/fact-search/index.vue'), meta: { title: '案情检索', requiresAuth: true } },
      { path: '/my-cases', name: 'myCases', component: () => import('@/pages/cases/index.vue'), meta: { title: '我的案件', requiresAuth: true } },
      { path: '/my-cases/review', name: 'caseSourceReview', component: () => import('@/pages/cases/review.vue'), meta: { title: '原页复核', requiresAuth: true } },
      { path: '/my-cases/events', name: 'caseEventTimeline', component: () => import('@/pages/cases/events.vue'), meta: { title: '事实事件时间线', requiresAuth: true } },
      { path: '/my-cases/claims', name: 'caseClaimWorkbench', component: () => import('@/pages/cases/claims.vue'), meta: { title: '事实与摘录', requiresAuth: true } },
      { path: '/my-cases/bank', name: 'caseBankWorkbench', component: () => import('@/pages/cases/bank.vue'), meta: { title: '银行流水', requiresAuth: true } },
      { path: '/my-cases/materials', name: 'caseMaterialsWorkbench', component: () => import('@/pages/cases/materials.vue'), meta: { title: '材料溯源', requiresAuth: true } },
      { path: '/notifications', name: 'notifications', component: () => import('@/pages/notifications/index.vue'), meta: { title: '通知', requiresAuth: true } },
      { path: '/search-reports', name: 'searchReports', component: () => import('@/pages/search-reports/index.vue'), meta: { title: '检索报告', requiresAuth: true } },
      { path: '/search-reports/:id', name: 'searchReportWorkbench', component: () => import('@/pages/search-reports/workbench.vue'), meta: { title: '检索报告工作台', requiresAuth: true } },
    ],
  },
];

export const staticRouter: RouteRecordRaw[] = [];

export const errorRouter = [
  {
    path: '/403',
    name: '403',
    component: () => import('@/pages/error/403.vue'),
    meta: {
      title: '403页面',
      enName: '403 Page',
      icon: 'QuestionFilled',
      isHide: '1',
      isLink: '1',
      isKeepAlive: '0',
      isFull: '1',
      isAffix: '1',
    },
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/pages/error/404.vue'),
    meta: {
      title: '404页面',
      enName: '404 Page',
      icon: 'CircleCloseFilled',
      isHide: '1',
      isLink: '1',
      isKeepAlive: '0',
      isFull: '1',
      isAffix: '1',
    },
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/pages/error/404.vue'),
  },
];
