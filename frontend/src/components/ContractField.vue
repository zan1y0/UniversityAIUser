<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { displaySchema, type Schema } from '../api/schema';
import { isRecord } from '../api/client';
import { label, enumLabel, defaultValue, technicalFields } from '../domain/contract-ui';
import { useCatalogStore } from '../stores/catalog';
const props = defineProps<{
  name: string;
  schema: Schema;
  modelValue: unknown;
  required?: boolean;
  questionId?: string;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>();
const catalog = useCatalogStore();
const variant = ref(0);
const definition = computed(() => displaySchema(props.schema));
const current = computed(() =>
  definition.value.oneOf ? displaySchema(definition.value.oneOf[variant.value]!) : definition.value,
);
const objectValue = computed(() => (isRecord(props.modelValue) ? props.modelValue : {}));
const arrayValue = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []));
const choices = computed(() => catalog.options(props.name, props.questionId));
watch(
  () => props.questionId,
  (id) => {
    if (props.name !== 'answer' || !id || !definition.value.oneOf) return;
    const kind = catalog.options('questionId').find((v) => v.value === id)?.record.kind;
    const index = definition.value.oneOf.findIndex(
      (v) => displaySchema(v).properties?.kind?.const === kind,
    );
    if (index >= 0) {
      variant.value = index;
      emit('update:modelValue', defaultValue(definition.value.oneOf[index]!, props.name));
    }
  },
);
const nullable = computed(() => props.schema.anyOf?.some((v) => v.type === 'null'));
watch(
  () => props.modelValue,
  (value) => {
    if (isRecord(value) && definition.value.oneOf) {
      const index = definition.value.oneOf.findIndex((branch) => {
        const fields = displaySchema(branch).properties || {};
        if (value.kind) return fields.kind?.const === value.kind;
        return (
          Object.keys(value).every((key) => key in fields) &&
          (displaySchema(branch).required || []).every((key) => key in value)
        );
      });
      if (index >= 0) variant.value = index;
    }
  },
  { immediate: true },
);
function update(value: unknown) {
  emit('update:modelValue', value);
}
function updateField(name: string, value: unknown) {
  const next = { ...objectValue.value, [name]: value };
  if (name === 'mode' && value === 'english' && 'language' in next) next.language = 'en-US';
  update(next);
}
function removeField(name: string) {
  const next = { ...objectValue.value };
  delete next[name];
  update(next);
}
function selectVariant() {
  const s = definition.value.oneOf![variant.value]!;
  const value = defaultValue(s, props.name);
  if (isRecord(value) && isRecord(props.modelValue)) {
    for (const k of Object.keys(value))
      if (k in props.modelValue && k !== 'kind') value[k] = props.modelValue[k];
  }
  update(value);
}
function changeText(event: Event) {
  const text = (event.target as HTMLInputElement).value;
  update(
    current.value.format === 'date-time'
      ? text
        ? new Date(text).toISOString()
        : null
      : text === '' && nullable.value
        ? null
        : text,
  );
}
function datetime(value: unknown) {
  if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) return '';
  const d = new Date(value);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
function setArray(index: number, value: unknown) {
  const result = [...arrayValue.value];
  result[index] = value;
  update(result);
}
function selectMany(event: Event) {
  update(Array.from((event.target as HTMLSelectElement).selectedOptions).map((o) => o.value));
}
</script>
<template>
  <div v-if="!technicalFields.has(name)" class="contract-field">
    <template v-if="definition.oneOf">
      <label class="field"
        >{{ name === 'answer' ? '作答形式' : '输入方式' }}
        <select
          v-model="variant"
          :aria-label="name === 'answer' ? '作答形式' : '输入方式'"
          @change="selectVariant"
        >
          <option v-for="(branch, i) in definition.oneOf" :key="i" :value="i">
            {{
              displaySchema(branch).properties?.kind?.const
                ? enumLabel(displaySchema(branch).properties?.kind?.const)
                : displaySchema(branch).properties?.audioFileId
                  ? '上传录音回答'
                  : displaySchema(branch).properties?.recognitionId
                    ? '已确认的图片题目'
                    : displaySchema(branch).properties?.fileId
                      ? '已上传语音'
                      : '文字输入'
            }}
          </option>
        </select>
      </label>
    </template>
    <template v-if="current.type === 'object'">
      <fieldset class="contract-group">
        <legend v-if="name !== 'body'">{{ label(name) }}</legend>
        <template v-for="(field, key) in current.properties" :key="key">
          <label
            v-if="!technicalFields.has(String(key)) && !current.required?.includes(String(key))"
            class="optional-toggle"
            ><input
              type="checkbox"
              :checked="String(key) in objectValue"
              @change="
                (event) =>
                  (event.target as HTMLInputElement).checked
                    ? updateField(String(key), defaultValue(field, String(key)))
                    : removeField(String(key))
              "
            />填写{{ label(String(key)) }}</label
          >
          <ContractField
            v-if="current.required?.includes(String(key)) || String(key) in objectValue"
            :name="String(key)"
            :schema="field"
            :model-value="objectValue[String(key)]"
            :required="current.required?.includes(String(key))"
            :question-id="questionId"
            @update:model-value="updateField(String(key), $event)"
          />
        </template>
      </fieldset>
    </template>
    <template v-else-if="current.type === 'array'">
      <label class="field"
        >{{ label(name) }}<span v-if="required" class="muted small">必填</span></label
      >
      <select
        v-if="current.items && displaySchema(current.items).format === 'uuid'"
        multiple
        :value="arrayValue"
        :aria-label="label(name)"
        @change="selectMany"
      >
        <option v-for="choice in choices" :key="choice.value" :value="choice.value">
          {{ choice.text }}
        </option>
      </select>
      <div v-else>
        <div v-for="(item, i) in arrayValue" :key="i" class="contract-array-item">
          <ContractField
            v-if="current.items"
            :name="name === 'optionIds' ? 'optionId' : name"
            :schema="current.items"
            :model-value="item"
            :question-id="questionId"
            @update:model-value="setArray(i, $event)"
          /><button
            type="button"
            class="text-btn"
            @click="update(arrayValue.filter((_, j) => j !== i))"
          >
            移除
          </button>
        </div>
        <button
          type="button"
          class="btn secondary small"
          :disabled="arrayValue.length >= (current.maxItems || 100)"
          @click="update([...arrayValue, defaultValue(current.items || {}, name)])"
        >
          添加{{ label(name) }}
        </button>
      </div>
      <p
        v-if="current.items && displaySchema(current.items).format === 'uuid' && !choices.length"
        class="muted small"
      >
        请先查询并加载可选择的{{ label(name) }}。
      </p>
    </template>
    <label v-else-if="current.type === 'boolean'" class="reminder-row"
      ><span>{{ label(name) }}{{ required ? '（必填）' : '' }}</span
      ><input
        type="checkbox"
        :checked="modelValue === true"
        @change="update(($event.target as HTMLInputElement).checked)"
    /></label>
    <label v-else class="field"
      >{{ label(name) }}<span v-if="required" class="muted small">必填</span>
      <select
        v-if="current.enum"
        :value="modelValue"
        @change="update(($event.target as HTMLSelectElement).value)"
      >
        <option v-for="choice in current.enum" :key="String(choice)" :value="choice">
          {{ enumLabel(choice) }}
        </option>
      </select>
      <input v-else-if="'const' in current" :value="enumLabel(current.const)" readonly />
      <select
        v-else-if="(current.format === 'uuid' && name !== 'chapterId') || choices.length"
        :value="modelValue"
        @change="update(($event.target as HTMLSelectElement).value || (nullable ? null : ''))"
      >
        <option value="">{{ nullable ? '暂不关联' : '请选择已加载记录' }}</option>
        <option v-for="choice in choices" :key="choice.value" :value="choice.value">
          {{ choice.text }}
        </option>
      </select>
      <input
        v-else-if="current.type === 'integer' || current.type === 'number'"
        type="number"
        :value="modelValue"
        :min="current.minimum"
        :max="current.maximum"
        :step="current.type === 'integer' ? 1 : 0.0001"
        @input="
          update(
            ($event.target as HTMLInputElement).value === ''
              ? nullable
                ? null
                : undefined
              : Number(($event.target as HTMLInputElement).value),
          )
        "
      />
      <input
        v-else-if="current.format === 'date' || current.format === 'date-time'"
        :type="current.format === 'date' ? 'date' : 'datetime-local'"
        :value="current.format === 'date' ? String(modelValue || '') : datetime(modelValue)"
        @input="changeText"
      />
      <textarea
        v-else-if="(current.maxLength || 0) > 1000"
        rows="4"
        :value="String(modelValue || '')"
        :maxlength="current.maxLength"
        @input="changeText"
      />
      <input
        v-else
        :type="name === 'time' || name === 'reminderTime' ? 'time' : 'text'"
        :value="modelValue"
        :maxlength="current.maxLength"
        @input="changeText"
      />
      <span v-if="current.format === 'uuid' && !choices.length" class="muted small">{{
        name === 'chapterId'
          ? '请填写教材资料提供的章节编号。'
          : '尚未加载可关联资料，请先使用本模块的查询功能。'
      }}</span>
    </label>
  </div>
</template>
