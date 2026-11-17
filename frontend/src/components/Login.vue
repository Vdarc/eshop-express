<template>
  <div class="max-w-md mx-auto card">
    <h2 class="text-xl font-bold mb-4">{{ t.login.title }}</h2>
    <input v-model="loginForm.username" :placeholder="t.login.username" class="mb-3"/>
    <input v-model="loginForm.password" type="password" :placeholder="t.login.password" class="mb-4"/>
    <button @click="login" :disabled="loading.login" class="btn btn-primary w-full mb-2">
      <span v-if="loading.login" class="loader"></span>
      {{ t.login.button }}
    </button>
    <button @click="changeRoute('register')" class="btn btn-secondary w-full">
      {{ t.login.toRegister }}
    </button>
  </div>
</template>

<script>
export default {
  name: 'Login',
  props: {
    t: Object,
    loginForm: Object,
    loading: Object,
    changeRoute: Function,
    login: Function
  }
}
</script>