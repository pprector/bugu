<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue';
import { NSwitch, NTag } from 'naive-ui';
import {
  fetchAdminAccounts,
  fetchAdminCreateAccount,
  fetchAdminSetAccountEnabled,
  type AdminAccount
} from '@/service/api';

const loading = ref(false);
const rows = ref<AdminAccount[]>([]);

const ROLE_OPTIONS = ['SUPER', 'OPS'].map(v => ({ label: v, value: v }));

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '用户名', key: 'username', minWidth: 120 },
  { title: '昵称', key: 'nickname', minWidth: 120, render: (row: AdminAccount) => row.nickname || '-' },
  {
    title: '角色',
    key: 'role',
    width: 100,
    render: (row: AdminAccount) =>
      h(NTag, { size: 'small', type: row.role === 'SUPER' ? 'success' : 'info' }, () => row.role)
  },
  {
    title: '启用',
    key: 'enabled',
    width: 90,
    render: (row: AdminAccount) =>
      h(NSwitch, {
        value: row.enabled,
        size: 'small',
        onUpdateValue: (value: boolean) => toggleEnabled(row, value)
      })
  },
  { title: '创建时间', key: 'createdAt', minWidth: 170 }
];

const modalVisible = ref(false);
const saving = ref(false);
const form = reactive({ username: '', password: '', nickname: '', role: 'OPS' });

async function load() {
  loading.value = true;
  try {
    const { data, error } = await fetchAdminAccounts();
    if (!error && data) rows.value = data;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  form.username = '';
  form.password = '';
  form.nickname = '';
  form.role = 'OPS';
  modalVisible.value = true;
}

async function save() {
  if (!form.username.trim() || !form.password.trim()) {
    window.$message?.error('用户名和密码不能为空');
    return;
  }
  saving.value = true;
  try {
    const { error } = await fetchAdminCreateAccount({
      username: form.username.trim(),
      password: form.password.trim(),
      nickname: form.nickname.trim(),
      role: form.role
    });
    if (!error) {
      window.$message?.success(`账号 ${form.username.trim()} 已创建`);
      modalVisible.value = false;
      load();
    }
  } finally {
    saving.value = false;
  }
}

async function toggleEnabled(row: AdminAccount, enabled: boolean) {
  // OPS 角色执行写操作会被后端拒绝,错误提示由 request 封装统一弹出
  const { error } = await fetchAdminSetAccountEnabled(row.id, enabled);
  if (!error) {
    row.enabled = enabled;
    window.$message?.success(`账号 ${row.username} 已${enabled ? '启用' : '停用'}`);
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="后台账号" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NButton type="primary" secondary @click="openCreate">新建账号</NButton>
      </template>
      <NDataTable :columns="columns" :data="rows" :loading="loading" :bordered="false" />
    </NCard>

    <NModal v-model:show="modalVisible" preset="card" title="新建账号" class="w-480px">
      <NForm label-placement="left" :label-width="70">
        <NFormItem label="用户名">
          <NInput v-model:value="form.username" placeholder="登录用户名" />
        </NFormItem>
        <NFormItem label="密码">
          <NInput v-model:value="form.password" type="password" show-password-on="click" placeholder="登录密码" />
        </NFormItem>
        <NFormItem label="昵称">
          <NInput v-model:value="form.nickname" placeholder="显示昵称" />
        </NFormItem>
        <NFormItem label="角色">
          <NSelect v-model:value="form.role" :options="ROLE_OPTIONS" />
        </NFormItem>
        <NSpace justify="end">
          <NButton @click="modalVisible = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="save">保存</NButton>
        </NSpace>
      </NForm>
    </NModal>
  </div>
</template>
