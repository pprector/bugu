<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchAdminAuditLogs, type AdminAuditLog } from '@/service/api';

const loading = ref(false);
const adminUserId = ref<string>('');
const rows = ref<AdminAuditLog[]>([]);

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '操作人(admin)', key: 'adminUserId', width: 130 },
  { title: '动作', key: 'action', width: 160 },
  {
    title: '目标',
    key: 'target',
    minWidth: 140,
    render: (row: AdminAuditLog) => (row.targetType ? `${row.targetType}#${row.targetId ?? '-'}` : '-')
  },
  {
    title: '详情',
    key: 'detail',
    minWidth: 180,
    render: (row: AdminAuditLog) => row.detail ?? '-'
  },
  {
    title: '时间',
    key: 'createdAt',
    minWidth: 170,
    render: (row: AdminAuditLog) => row.createdAt.slice(0, 19).replace('T', ' ')
  }
];

async function load() {
  loading.value = true;
  try {
    const id = adminUserId.value.trim();
    const { data, error } = await fetchAdminAuditLogs(id ? Number(id) : undefined);
    if (!error && data) rows.value = data;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="审计日志" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NInput v-model:value="adminUserId" placeholder="按操作人 ID 过滤" clearable style="width: 200px" @keyup.enter="load" />
          <NButton type="primary" @click="load">查询</NButton>
        </NSpace>
      </template>
      <NDataTable :columns="columns" :data="rows" :loading="loading" :bordered="false" />
    </NCard>
  </div>
</template>
