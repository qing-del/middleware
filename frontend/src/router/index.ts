import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/dashboard',
      redirect: '/user/dashboard'
    },
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/user',
      component: () => import('@/layouts/UserLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/user/dashboard'
        },
        {
          path: 'dashboard',
          name: 'UserDashboard',
          component: () => import('@/views/user/Dashboard.vue')
        },
        {
          path: 'notes',
          name: 'UserNotes',
          component: () => import('@/views/user/Notes.vue')
        },
        {
          path: 'topics',
          name: 'UserTopics',
          component: () => import('@/views/user/Topics.vue')
        },
        {
          path: 'tags',
          name: 'UserTags',
          component: () => import('@/views/user/Tags.vue')
        },
        {
          path: 'images',
          name: 'UserImages',
          component: () => import('@/views/user/Images.vue')
        }
      ]
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/admin/dashboard'
        },
        {
          path: 'dashboard',
          name: 'AdminDashboard',
          component: () => import('@/views/admin/Dashboard.vue')
        },
        {
          path: 'audit',
          name: 'AdminAudit',
          component: () => import('@/views/admin/Audit.vue')
        },
        {
          path: 'users',
          name: 'AdminUsers',
          component: () => import('@/views/admin/Users.vue')
        },
        {
          path: 'notes',
          name: 'AdminNotes',
          component: () => import('@/views/admin/Notes.vue')
        },
        {
          path: 'topics',
          name: 'AdminTopics',
          component: () => import('@/views/admin/Topics.vue')
        },
        {
          path: 'tags',
          name: 'AdminTags',
          component: () => import('@/views/admin/Tags.vue')
        },
        {
          path: 'images',
          name: 'AdminImages',
          component: () => import('@/views/admin/Images.vue')
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !token) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/') && token) {
    // 如果访问 login 或根路径且有 token，去 dashboard
    next('/dashboard')
  } else {
    next()
  }
})

export default router
