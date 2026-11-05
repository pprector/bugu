<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { NButton, NPopconfirm, NTag } from 'naive-ui';
import { fetchAdminBonds, fetchAdminUnbind, type AdminBond } from '@/service/api';
import { useAuth } from '@/hooks/business/auth';

const { hasAuth } = useAuth();

const loading = ref(false);
const keyword = ref('');
const rows = ref<AdminBond[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);

const canWrite = hasAuth('admin:write');

function statusTag(status: string) {
  const type = status === 'ACTIVE' ? 'success' : status === 'PENDING' ? 'warning' : 'error';
  return h(NTag, { size: 'small', type }, () => status);
}

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '状态', key: 'status', width: 100, render: (row: AdminBond) => statusTag(row.status) },
  { title: '发起方', key: 'requesterName', minWidth: 120 },
  {
    title: '接收方',
    key: 'receiverName',
    minWidth: 120,
    render: (row: AdminBond) => row.receiverName ?? '-'
  },
  {
    title: '创建时间',
    key: 'createdAt',
    minWidth: 170,
    render: (row: AdminBond) => row.createdAt.slice(0, 19).replace('T', ' ')
  },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    render: (row: AdminBond) =>
      h(
        NPopconfirm,
        { onPositiveClick: () => unbind(row) },
        {
          trigger: () =>
            h(
              NButton,
              { size: 'small', type: 'error', ghost: true, disabled: !canWrite || row.status !== 'ACTIVE' },
              () => '解绑'
            ),
          default: () => '确认解绑该绑定？双方提醒将即时隔离。'
        }
      )
  }
];

let requestId = 0;

async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  try {
    const { data, error } = await fetchAdminBonds({
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

async function unbind(row: AdminBond) {
  const { error } = await fetchAdminUnbind(row.id);
  if (!error) {
    window.$message?.success(`绑定 ${row.id} 已解绑`);
    load();
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="绑定管理" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NInput v-model:value="keyword" placeholder="ID / 邀请码 / 用户 ID" clearable style="width: 220px" @keyup.enter="search" />
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
