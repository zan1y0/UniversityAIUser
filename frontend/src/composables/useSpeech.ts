import { onBeforeUnmount, ref } from 'vue';
interface RecognitionEvent {
  results: {
    [index: number]: { [index: number]: { transcript: string }; isFinal: boolean };
    length: number;
  };
  resultIndex: number;
}
interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: RecognitionEvent) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}
type SpeechWindow = Window & {
  SpeechRecognition?: new () => Recognition;
  webkitSpeechRecognition?: new () => Recognition;
};
export function useSpeech(onText?: (text: string) => void) {
  const listening = ref(false);
  const speaking = ref(false);
  const error = ref('');
  let recognition: Recognition | undefined;
  const ctor =
    (window as SpeechWindow).SpeechRecognition || (window as SpeechWindow).webkitSpeechRecognition;
  function stop() {
    recognition?.abort();
    listening.value = false;
    if ('speechSynthesis' in window) window.speechSynthesis.cancel();
    speaking.value = false;
  }
  function listen(lang = 'zh-CN') {
    error.value = '';
    if (listening.value) {
      stop();
      return;
    }
    if (!ctor) {
      error.value = '当前浏览器不支持语音识别，请使用文字输入。';
      return;
    }
    recognition = new ctor();
    recognition.lang = lang;
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onresult = (e) => {
      for (let i = e.resultIndex; i < e.results.length; i++)
        if (e.results[i]?.isFinal) onText?.(e.results[i]?.[0]?.transcript || '');
    };
    recognition.onerror = (e) => {
      error.value =
        e.error === 'not-allowed'
          ? '麦克风权限被拒绝，请检查浏览器设置。'
          : '语音识别失败，请使用文字输入。';
      listening.value = false;
    };
    recognition.onend = () => (listening.value = false);
    try {
      recognition.start();
      listening.value = true;
    } catch {
      error.value = '麦克风无法启动，请重试。';
    }
  }
  function speak(text: string, lang = 'zh-CN') {
    error.value = '';
    if (!('speechSynthesis' in window)) {
      error.value = '当前浏览器不支持语音播放。';
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;
    utterance.onend = () => (speaking.value = false);
    utterance.onerror = () => {
      speaking.value = false;
      error.value = '语音播放失败，请阅读文字。';
    };
    speaking.value = true;
    window.speechSynthesis.speak(utterance);
  }
  onBeforeUnmount(stop);
  return { listening, speaking, error, listen, speak, stop, supported: Boolean(ctor) };
}
