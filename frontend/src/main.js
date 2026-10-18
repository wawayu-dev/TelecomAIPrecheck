import { createApp } from 'vue'
import App from './App.vue'
import { createHostRouter } from './router'
import { createDemoHost } from './host/session'
import { precheckHostKey } from './modules/precheck'
import './style.css'

createApp(App).provide(precheckHostKey, createDemoHost()).use(createHostRouter()).mount('#app')
