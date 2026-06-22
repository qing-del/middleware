import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import ArcoVue from '@arco-design/web-vue'
import '@arco-design/web-vue/dist/arco.css'

import './style.css'
import './styles/tokens.css'
import './styles/base.css'
import './styles/components.css'
import './styles/forms.css'
import './styles/tables.css'
import './styles/article.css'
import './styles/arco-overrides.css'
import './styles/motion.css'
import App from './App.vue'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ArcoVue)

app.mount('#app')
