<script setup lang="ts">
import { computed, ref } from 'vue';
import ChatPanel from '../components/ChatPanel.vue';
import AppIcon from '../components/AppIcon.vue';
import { useSpeech } from '../composables/useSpeech';
import { useAppStore } from '../stores/app';
const app = useAppStore();
const mode = ref('专业问答');
const name = ref('');
const major = ref('');
const experience = ref('');
const introduction = ref('');
const step = ref(0);
const answer = ref('');
const feedback = ref('');
const speech = useSpeech((text) => (answer.value += text));
const questions = computed(() =>
  mode.value === '英语口语'
    ? [
        'Could you introduce your academic interests?',
        'Why do you want to pursue graduate studies?',
        'Could you describe a challenge you overcame?',
      ]
    : mode.value === '压力面试'
      ? [
          '你的基础仍有不足，为什么认为自己能适应研究生阶段？',
          '如果导师指出你的方案完全不可行，你会如何回应？',
          '当多个任务同时截止，你将如何安排？',
        ]
      : [
          '请介绍一个你最熟悉的专业知识点。',
          '你如何将这个知识点应用到实际问题？',
          '如果得到与预期不同的结果，你会如何验证？',
        ],
);
function buildIntro() {
  if (app.mode === 'live') {
    app.notify('自我介绍生成尚未提供结构化接口，可在对话中请求辅助');
    return;
  }
  introduction.value = `Hello, my name is ${name.value.trim() || '[your name]'}. My academic interest is ${major.value.trim() || '[your major]'}. During my studies, I ${experience.value.trim() || '[describe one relevant experience]'}. I hope to deepen my understanding through graduate study. Thank you for your time.`;
}
function evaluate() {
  if (!answer.value.trim()) return;
  feedback.value =
    '【本地表达检查】回答共 ' +
    answer.value.trim().length +
    ' 个字符。建议检查是否有明确观点、具体例子和清晰总结；此处不评估发音或学术正确性，也不提供虚构评分。';
}
function changeMode(value: string) {
  mode.value = value;
  step.value = 0;
  answer.value = '';
  feedback.value = '';
  speech.stop();
}
</script>
<template>
  <div class="tabs">
    <button
      v-for="m in ['专业问答', '英语口语', '压力面试', '自我介绍', '礼仪与流程']"
      :key="m"
      class="tab"
      :class="{ active: mode === m }"
      @click="changeMode(m)"
    >
      {{ m }}
    </button>
  </div>
  <template v-if="['专业问答', '英语口语', '压力面试'].includes(mode)"
    ><div class="split">
      <div class="stack">
        <section class="card">
          <div class="card-heading">
            <h2>{{ mode }} · 练习 {{ step + 1 }}</h2>
            <span class="tag">表达与思考</span>
          </div>
          <div class="interview-prompt">{{ questions[step] }}</div>
          <div class="row wrap">
            <button
              class="btn secondary"
              @click="speech.speak(questions[step]!, mode === '英语口语' ? 'en-US' : 'zh-CN')"
            >
              <AppIcon name="volume" :size="16" />朗读问题</button
            ><button
              class="btn secondary"
              @click="
                step = (step + 1) % questions.length;
                answer = '';
                feedback = '';
                speech.stop();
              "
            >
              下一问
            </button>
          </div>
          <label class="field space-top"
            >你的回答<textarea
              v-model="answer"
              maxlength="4000"
              placeholder="先组织观点，再用一个具体例子展开。"
            ></textarea>
          </label>
          <div class="row wrap space-top">
            <button
              class="btn secondary"
              @click="speech.listen(mode === '英语口语' ? 'en-US' : 'zh-CN')"
            >
              <AppIcon name="mic" :size="16" />{{
                speech.listening.value ? '停止录音' : '语音回答'
              }}</button
            ><button
              class="btn"
              :disabled="!answer.trim() || app.mode === 'live'"
              @click="evaluate"
            >
              本地表达检查</button
            ><button
              v-if="speech.listening.value || speech.speaking.value"
              class="text-btn"
              @click="speech.stop"
            >
              停止采集与播放
            </button>
          </div>
          <p class="muted small space-top">
            语音转写由浏览器提供，可能使用服务商网络；不会保存音频。专业评分与发音评价需后端 ASR
            和评分服务。
          </p>
          <p v-if="speech.error.value" class="inline-error">{{ speech.error.value }}</p>
          <div v-if="feedback" class="answer-feedback">{{ feedback }}</div>
        </section>
        <ChatPanel
          :key="mode"
          purpose="复试训练"
          :greeting="
            mode === '英语口语'
              ? 'Let us practise expressing your ideas clearly. What is your academic interest?'
              : '请用清晰的结构介绍观点。你可以提交上方问题的回答，我会继续追问。'
          "
          :english="mode === '英语口语'"
          :context="`训练模式：${mode}，问题：${questions[step]}。给出反馈并继续追问。`"
          :prompts="
            answer.trim()
              ? [`请针对我的回答追问：${answer}`]
              : ['我想练习开场介绍', '怎样组织专业问题的回答？']
          "
        />
      </div>
      <section class="card" style="align-self: start">
        <h2>从容表达的小步骤</h2>
        <ul class="point-list">
          <li>听清问题，必要时确认范围</li>
          <li>先说核心观点，再说明依据</li>
          <li>不确定的内容坦诚说明</li>
          <li>压力问题先稳定节奏</li>
          <li>结束时用一句话总结</li>
        </ul>
        <p class="muted small">当前为训练辅助，不代表正式复试题库或录取评价。</p>
      </section>
    </div></template
  >
  <section v-else-if="mode === '自我介绍'" class="card">
    <h2>搭建英文自我介绍</h2>
    <form class="space-top" @submit.prevent="buildIntro">
      <div class="form-grid">
        <label class="field"
          >姓名（英文）<input
            v-model="name"
            maxlength="50"
            placeholder="例如 your English name" /></label
        ><label class="field"
          >专业 / 研究兴趣（英文）<input
            v-model="major"
            maxlength="100"
            placeholder="例如 computer science"
        /></label>
      </div>
      <label class="field space-top"
        >相关经历（英文动词短语）<textarea
          v-model="experience"
          maxlength="500"
          placeholder="例如 developed a small application with my classmates"
        ></textarea></label
      ><button class="btn space-top">生成本地模板</button>
    </form>
    <div v-if="introduction" class="answer-feedback">
      <p class="reading">{{ introduction }}</p>
      <button class="text-btn space-top" @click="speech.speak(introduction, 'en-US')">
        练习朗读
      </button>
    </div>
    <p class="muted small space-top">该模板是可编辑的前端示例，输入只在当前页面内存中保留。</p>
  </section>
  <div v-else class="grid three">
    <section
      v-for="item in [
        {
          title: '进入面试',
          points: [
            '提前核对设备或到场路线',
            '简洁问候，遵循老师安排',
            '保持自然坐姿与适度目光交流',
          ],
        },
        {
          title: '交流过程',
          points: ['完整听完问题再回答', '观点与实例相结合', '不理解时礼貌请求澄清'],
        },
        {
          title: '结束面试',
          points: ['按要求补充必要信息', '表达感谢，平稳结束', '及时复盘可改进之处'],
        },
      ]"
      :key="item.title"
      class="card"
    >
      <span class="module-icon"><AppIcon name="mic" /></span>
      <h2>{{ item.title }}</h2>
      <ul class="point-list">
        <li v-for="p in item.points" :key="p">{{ p }}</li>
      </ul>
      <p class="muted small">具体安排以目标院校复试通知为准。</p>
    </section>
  </div>
</template>
