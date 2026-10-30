import { createApp } from 'vue'
import { vReveal } from './reveal'
import App from './App.vue'
import './style.css'

createApp(App).directive('reveal', vReveal).mount('#app')
