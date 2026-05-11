import { createApp } from 'vue'
import App from './App.vue'
import './assets/style.css' // Make sure this path is correct
import { serial } from 'web-serial-polyfill'

if (!navigator.serial) {
    navigator.serial = serial
}

const app = createApp(App)
app.mount('#app')