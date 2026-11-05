<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { NButton, NSwitch, NTag } from 'naive-ui';
import { fetchAdminUsers, fetchSetElderMode, type AdminAppUser } from '@/service/api';
import { useAuth } from '@/hooks/business/auth';

const { hasAuth } = useAuth();

const loading = ref(false);
const keyword = ref('');
const rows = ref<AdminAppUser[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);

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

let requestId = 0;

async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  try {
    const { data, error } = await fetchAdminUsers({
      query: keyword.value || undefined,
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
          <NInput v-model:value="keyword" placeholder="昵称 / 手机号 / ID" clearable style="width: 220px" @keyup.enter="search" />
          <NButton type="primary" @click="search">搜索</NButton>
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
