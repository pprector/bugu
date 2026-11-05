<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import { NButton, NForm, NFormItem, NInput, NSelect, NSwitch, NTag } from 'naive-ui';
import {
  fetchAdminCreateTemplate,
  fetchAdminTemplates,
  fetchAdminUpdateTemplate,
  type AdminTemplate
} from '@/service/api';
import { useAuth } from '@/hooks/business/auth';

const { hasAuth } = useAuth();

const canWrite = hasAuth('admin:write');

const TONE_OPTIONS = ['GENTLE', 'NAG', 'STRICT'].map(v => ({ label: v, value: v }));
const SCENE_OPTIONS = ['CREATE', 'REPUSH', 'WAKEUP'].map(v => ({ label: v, value: v }));

const loading = ref(false);
const rows = ref<AdminTemplate[]>([]);
const filterTone = ref<string | null>(null);
const filterScene = ref<string | null>(null);
const page = ref(1);
const size = ref(20);
const total = ref(0);

const columns = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '语气', key: 'tone', width: 90 },
  { title: '场景', key: 'scene', width: 90 },
  { title: '文案', key: 'content', minWidth: 200 },
  {
    title: '启用',
    key: 'enabled',
    width: 90,
    render: (row: AdminTemplate) =>
      h(NSwitch, {
        value: row.enabled,
        disabled: !canWrite,
        size: 'small',
        onUpdateValue: (value: boolean) => toggle(row, { enabled: value })
      })
  },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (row: AdminTemplate) =>
      h(
        NButton,
        { size: 'small', onClick: () => openEdit(row), disabled: !canWrite },
        () => '编辑'
      )
  }
];

const modalVisible = ref(false);
const saving = ref(false);
const editing = ref<AdminTemplate | null>(null);
const form = ref({ tone: 'GENTLE', scene: 'CREATE', content: '' });

let requestId = 0;

async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  try {
    const { data, error } = await fetchAdminTemplates({
      tone: filterTone.value ?? undefined,
      scene: filterScene.value ?? undefined,
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

function openCreate() {
  editing.value = null;
  form.value = { tone: 'GENTLE', scene: 'CREATE', content: '' };
  modalVisible.value = true;
}

function openEdit(row: AdminTemplate) {
  editing.value = row;
  form.value = { tone: row.tone, scene: row.scene, content: row.content };
  modalVisible.value = true;
}

async function save() {
  if (!form.value.content.trim()) {
    window.$message?.error('文案不能为空');
    return;
  }
  saving.value = true;
  try {
    const { error } = editing.value
      ? await fetchAdminUpdateTemplate(editing.value.id, { content: form.value.content.trim() })
      : await fetchAdminCreateTemplate({
          tone: form.value.tone,
          scene: form.value.scene,
          content: form.value.content.trim()
        });
    if (!error) {
      window.$message?.success(editing.value ? '模板已更新' : '模板已创建');
      modalVisible.value = false;
      load();
    }
  } finally {
    saving.value = false;
  }
}

async function toggle(row: AdminTemplate, patch: { enabled?: boolean; content?: string }) {
  const { error } = await fetchAdminUpdateTemplate(row.id, patch);
  if (!error) {
    window.$message?.success('模板已更新');
    load();
  }
}

onMounted(load);
</script>

<template>
  <div class="min-h-500px">
    <NCard title="AI 文案模板" :bordered="false" class="card-wrapper">
      <template #header-extra>
        <NSpace>
          <NSelect v-model:value="filterTone" :options="TONE_OPTIONS" placeholder="全部语气" clearable style="width: 130px" />
          <NSelect v-model:value="filterScene" :options="SCENE_OPTIONS" placeholder="全部场景" clearable style="width: 130px" />
          <NButton type="primary" @click="search">查询</NButton>
          <NButton v-if="canWrite" type="primary" secondary @click="openCreate">新增模板</NButton>
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

    <NModal v-model:show="modalVisible" preset="card" :title="editing ? '编辑模板' : '新增模板'" class="w-480px">
      <NForm label-placement="left" :label-width="70">
        <NFormItem label="语气">
          <NSelect v-model:value="form.tone" :options="TONE_OPTIONS" :disabled="!!editing" />
        </NFormItem>
        <NFormItem v-if="!editing" label="场景">
          <NSelect v-model:value="form.scene" :options="SCENE_OPTIONS" />
        </NFormItem>
        <NFormItem label="文案">
          <NInput v-model:value="form.content" type="textarea" :maxlength="40" show-count placeholder="不超过 40 字" />
        </NFormItem>
        <NSpace justify="end">
          <NButton @click="modalVisible = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="save">保存</NButton>
        </NSpace>
      </NForm>
    </NModal>
  </div>
</template>
