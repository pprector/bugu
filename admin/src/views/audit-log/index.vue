<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchAdminAuditLogs, type AdminAuditLog } from '@/service/api';

const loading = ref(false);
const adminUserId = ref<string>('');
const rows = ref<AdminAuditLog[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);

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

let requestId = 0;

async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  try {
    const id = adminUserId.value.trim();
    const { data, error } = await fetchAdminAuditLogs({
      adminUserId: id ? Number(id) : undefined,
      page: page.value,
      size: size.value
    });
    if (currentRequest !== requestId) return;
    if (!error && data) {
      const lastPage = Math.max(1, Math.ceil(data.total / data.size));
      if (data.page > lastPage) {
        page.value = lastPage;
        await load();
        return;
      }
      rows.value = data.items;
      total.value = data.total;
      page.value = data.page;
      size.value = data.size;
    }
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}

function search() {
  page.value = 1;
  load();
}

function changePage(value: number) {
  page.value = value;
  load();
}

function changeSize(value: number) {
  size.value = value;
  page.value = 1;
  load();
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="审计日志" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NInput v-model:value="adminUserId" placeholder="按操作人 ID 过滤" clearable style="width: 200px" @keyup.enter="search" />
          <NButton type="primary" @click="search">查询</NButton>
        </NSpace>
      </template>
      <NDataTable
        remote
        :columns="columns"
        :data="rows"
        :row-key="row => row.id"
        :loading="loading"
        :bordered="false"
        :pagination="{
          page,
          pageSize: size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50, 100]
        }"
        @update:page="changePage"
        @update:page-size="changeSize"
      />
    </NCard>
  </div>
</template>
