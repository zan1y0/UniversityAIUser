export type AppMode = 'demo' | 'live';
export type Subject = '数学' | '英语' | '政治' | '专业课';
export type LoadState = 'idle' | 'loading' | 'success' | 'empty' | 'error' | 'cancelled';
export interface User {
  id: string;
  nickname: string;
  targetSchool?: string;
  targetMajor?: string;
}
export interface School {
  id: string;
  name: string;
  region: string;
  major: string;
  difficulty: string;
  line: number;
  enrollment: number;
  ratio: string;
  scoreRange: string;
  risk: string;
  source: string;
}
export interface Question {
  id: string;
  subject: Subject;
  topic: string;
  stem: string;
  options: string[];
  answer: number;
  explanation: string;
}
export interface Attempt {
  questionId: string;
  selected: number;
  correct: boolean;
  time: string;
  kind: 'practice' | 'exam';
}
export interface PlanTask {
  id: string;
  title: string;
  subject: Subject;
  minutes: number;
  date: string;
  done: boolean;
}
export interface Plan {
  id: string;
  version: number;
  cycle: string;
  target: string;
  tasks: PlanTask[];
  createdAt: string;
}
export interface ExamSession {
  id: string;
  questionIds: string[];
  answers: Record<string, number>;
  startedAt: number;
  durationSeconds: number;
  submittedAt?: number;
}
export interface Notice {
  id: string;
  category: string;
  title: string;
  date: string;
  source: string;
  content: string;
  important: boolean;
}
export interface LearningEvent {
  subject: Subject;
  minutes: number;
  date: string;
  focused: boolean;
}
export interface Message {
  id: string;
  role: 'assistant' | 'user';
  content: string;
}
export interface CourseUnit {
  id: string;
  title: string;
  tag: string;
  duration: number;
  content: string;
  points: string[];
}
