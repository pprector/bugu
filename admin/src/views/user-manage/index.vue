<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { NButton, NSwitch, NTag } from 'naive-ui';
import { fetchAdminUsers, fetchSetElderMode, type AdminAppUser } from '@/service/api';
import { useAuth } from '@/hooks/business/auth';

const { hasAuth } = useAuth();

const loading = ref(false);
const keyword = ref('');
const rows = ref<AdminAppUser[]>([]);

const canWrite = hasAuth('admin:write');

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '昵称', key: 'nickname', minWidth: 120 },
  { title: '手机号', key: 'phone', minWidth: 130, render: (row: AdminAppUser) => row.phone ?? '-' },
  {
    title: '账号类型',
    key: 'accountType',
    width: 100,
    render: (row: AdminAppUser) =>
      h(NTag, { size: 'small', type: row.accountType === 'ELDER' ? 'warning' : 'default' }, () => row.accountType)
  },
  {
    title: '长辈模式',
    key: 'elderMode',
    width: 110,
    render: (row: AdminAppUser) =>
      h(NSwitch, {
        value: row.elderMode,
        disabled: !canWrite,
        size: 'small',
        onUpdateValue: (value: boolean) => toggleElderMode(row, value)
      })
  }
];

async function load() {
  loading.value = true;
  try {
    const { data, error } = await fetchAdminUsers(keyword.value || undefined);
    if (!error && data) rows.value = data;
  } finally {
    loading.value = false;
  }
}

async function toggleElderMode(row: AdminAppUser, enabled: boolean) {
  const { error } = await fetchSetElderMode(row.id, enabled);
  if (!error) {
    row.elderMode = enabled;
    window.$message?.success(`用户 ${row.id} 长辈模式已${enabled ? '开启' : '关闭'}`);
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="用户管理" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NInput v-model:value="keyword" placeholder="昵称 / 手机号 / ID" clearable style="width: 220px" @keyup.enter="load" />
          <NButton type="primary" @click="load">搜索</NButton>
        </NSpace>
      </template>
      <NDataTable :columns="columns" :data="rows" :loading="loading" :bordered="false" />
    </NCard>
  </div>
</template>
