// 从 contracts/openapi.json 生成；禁止手工修改。
export const CONTRACT_VERSION = '1.0.0';
export type Source = {
  title: string;
  url: string;
  publisher: string;
  publishedAt: string | null;
  retrievedAt: string;
  version: string;
};
export type CurrentUser = { id: string; loginName: string; displayName: string };
export type Baseline = { subjectId: string; score: number; maxScore: number };
export type Profile = {
  user: CurrentUser;
  undergraduateMajor: string;
  targetProgramId: string | null;
  targetDate: string | null;
  dailyMinutes: number;
  timezone: 'Asia/Shanghai';
  baseline: Baseline[];
  activePlanId: string | null;
  version: number;
};
export type ProfileInput = {
  undergraduateMajor: string;
  targetProgramId: string | null;
  targetDate: string | null;
  dailyMinutes: number;
  timezone: 'Asia/Shanghai';
  baseline: Baseline[];
  version: number;
};
export type Csrf = { token: string; headerName: 'X-CSRF-Token' };
export type LoginInput = { loginName: string; password: string };
export type File = {
  id: string;
  purpose: 'qaImage' | 'avatarAudio' | 'interviewAudio';
  mimeType:
    | 'image/jpeg'
    | 'image/png'
    | 'image/webp'
    | 'audio/wav'
    | 'audio/mpeg'
    | 'audio/webm'
    | 'audio/ogg';
  sizeBytes: number;
  durationSeconds: number | null;
  createdAt: string;
};
export type FileInput = { file: string; purpose: 'qaImage' | 'avatarAudio' | 'interviewAudio' };
export type Option = { id: string; text: string };
export type Question = {
  id: string;
  versionId: string;
  subjectId: string;
  knowledgeId: string;
  kind: 'singleChoice' | 'multipleChoice' | 'text' | 'numeric';
  stem: string;
  options: Option[];
  difficulty: 'easy' | 'medium' | 'hard';
  maxScore: number;
  sources: Source[];
};
export type Answer =
  | { kind: 'singleChoice'; optionId: string }
  | { kind: 'multipleChoice'; optionIds: string[] }
  | { kind: 'text'; content: string }
  | { kind: 'numeric'; value: string };
export type KnowledgeNode = {
  id: string;
  parentId: string | null;
  name: string;
  depth: number;
  order: number;
  subjectId: string;
};
export type KnowledgeTree = {
  subjectId: string;
  examType: string;
  year: number;
  edition: string;
  nodes: KnowledgeNode[];
  sources: Source[];
};
export type Explanation = {
  kind: 'explanation';
  title: string;
  summary: string;
  steps: string[];
  hints: string[];
  method: string;
  sources: Source[];
  modelVersion: string;
};
export type StudyFeedback = {
  kind: 'studyFeedback';
  taskType:
    | 'vocabulary'
    | 'sentence'
    | 'composition'
    | 'syllabus'
    | 'prediction'
    | 'recitation'
    | 'currentAffairs'
    | 'derivation'
    | 'classification'
    | 'guidance';
  content: string;
  corrections: string[];
  suggestions: string[];
  sources: Source[];
  modelVersion: string;
};
export type AvatarAsset = {
  shapeId: string;
  name: string;
  previewUrl: string;
  assetUrl: string;
  actionCodes: ('idle' | 'speak' | 'encourage' | 'remind' | 'celebrate')[];
  voices: string[];
};
export type AvatarPreferences = {
  shapeId: string;
  mode: 'scholar' | 'gentle' | 'strict';
  voice: string;
  reminderTime: string;
  version: number;
};
export type AvatarInteractionInput =
  { text: string; contextId: string } | { fileId: string; contextId: string };
export type AvatarInteraction = {
  kind: 'avatarInteraction';
  text: string;
  transcript: string | null;
  audioFileId: string | null;
  actionCode: 'idle' | 'speak' | 'encourage' | 'remind' | 'celebrate';
  sources: Source[];
  modelVersion: string;
};
export type Checkin = { id: string; localDate: string; streakDays: number; createdAt: string };
export type ReminderPreferences = {
  enabled: boolean;
  time: string;
  mode: 'scholar' | 'gentle' | 'strict';
  version: number;
};
export type Supervision = {
  date: string;
  plannedTasks: number;
  completedTasks: number;
  overdueTaskIds: string[];
  message: string;
  mode: 'scholar' | 'gentle' | 'strict';
};
export type Program = {
  id: string;
  schoolName: string;
  programName: string;
  major: string;
  region: string;
  year: number;
  difficulty: 'easy' | 'medium' | 'hard';
  scoreLine: number | null;
  enrollment: number | null;
  admissionRatio: number | null;
  sources: Source[];
  snapshotId: string;
};
export type ProgramFacts = {
  program: Program;
  metrics: {
    type: 'scoreLine' | 'enrollment' | 'admissionRatio';
    year: number;
    value: number | null;
    unit: 'points' | 'persons' | 'ratio';
    sources: Source[];
  }[];
  missingFields: string[];
};
export type RecommendationInput = {
  undergraduateMajor: string;
  regions: string[];
  year: number;
  estimatedScore: number;
  difficulty: 'easy' | 'medium' | 'hard';
};
export type ProgramRecommendation = {
  program: Program;
  matchScore: number;
  reasons: string[];
  risks: string[];
};
export type ProgramComparison = { year: number; programs: ProgramFacts[] };
export type SchoolEvaluation = {
  kind: 'schoolEvaluation';
  id: string;
  programId: string;
  status: 'completed' | 'insufficient_data';
  probability: number | null;
  reason: string;
  missingFields: string[];
  risks: string[];
  sources: Source[];
  modelVersion: string;
  calibrationVersion: string | null;
  snapshotId: string;
  createdAt: string;
};
export type Subject = {
  id: string;
  name: string;
  discipline: 'computer' | 'education' | 'economicsManagement' | 'medicine';
  programId: string;
  year: number;
  bookIds: string[];
};
export type ExamPoint = {
  knowledgeId: string;
  title: string;
  type: 'highFrequency' | 'errorProne' | 'memorize';
  content: string;
  sources: Source[];
};
export type Prediction = {
  kind: 'prediction';
  subjectId: string;
  targetYear: number;
  points: { knowledgeId: string; reason: string; frequency: number; confidence: number }[];
  disclaimer: string;
  sources: Source[];
  modelVersion: string;
};
export type Recognition = {
  kind: 'recognition';
  id: string;
  fileId: string;
  subjectId: string;
  text: string;
  confidence: number;
  requiresConfirmation: true;
  createdAt: string;
};
export type Conversation = {
  id: string;
  subjectId: string;
  contextVersion: number;
  createdAt: string;
  updatedAt: string;
};
export type QaMessageInput =
  | { subjectId: string; text: string; requestId: string; contextVersion: number }
  | {
      subjectId: string;
      recognitionId: string;
      confirmedText: string;
      requestId: string;
      contextVersion: number;
    };
export type QaGuidance = {
  steps: string[];
  hints: string[];
  method: string;
  sources: Source[];
  modelVersion: string;
};
export type QaMessage = {
  id: string;
  conversationId: string;
  role: 'user' | 'assistant';
  inputText: string;
  status: 'queued' | 'running' | 'completed' | 'failed_retryable' | 'failed' | 'cancelled';
  guidance: QaGuidance | null;
  contextVersion: number;
  version: number;
  createdAt: string;
};
export type QaResult = { kind: 'qa'; message: QaMessage };
export type PracticeSession = {
  id: string;
  type: 'daily' | 'weekly' | 'special';
  subjectId: string;
  knowledgeId: string | null;
  difficulty: 'easy' | 'medium' | 'hard';
  questions: Question[];
  createdAt: string;
};
export type PracticeAttempt = {
  id: string;
  sessionId: string;
  questionVersionId: string;
  answer: Answer;
  correct: boolean;
  score: number;
  maxScore: number;
  correctAnswer: Answer;
  explanation: Explanation;
  wrongItemId: string | null;
  createdAt: string;
};
export type WrongItem = {
  id: string;
  question: Question;
  attemptCount: number;
  lastAttemptAt: string;
  mastered: boolean;
  knowledgeId: string;
  version: number;
};
export type KnowledgeProgress = {
  subjectId: string;
  timeRange: 'week' | 'month' | 'all';
  items: {
    knowledgeId: string;
    attemptCount: number;
    correctCount: number;
    mastery: number | null;
  }[];
  algorithmVersion: string;
};
export type PlanTask = {
  id: string;
  subjectId: string;
  knowledgeId: string | null;
  scheduledDate: string;
  minutes: number;
  content: string;
  status: 'pending' | 'completed' | 'overdue';
  completedAt: string | null;
  version: number;
};
export type Plan = {
  id: string;
  status: 'draft' | 'active' | 'superseded';
  targetDate: string;
  dailyMinutes: number;
  profileVersion: number;
  tasks: PlanTask[];
  view: 'day' | 'week' | 'month';
  viewDate: string;
  version: number;
  createdAt: string;
};
export type PlanGenerated = {
  kind: 'plan';
  plan: Plan;
  reason: string;
  sources: Source[];
  modelVersion: string;
};
export type PlanProgress = {
  planId: string;
  timeRange: 'week' | 'month' | 'all';
  plannedMinutes: number;
  completedMinutes: number;
  plannedTasks: number;
  completedTasks: number;
  completionRate: number | null;
  version: number;
};
export type Paper = {
  id: string;
  versionId: string;
  title: string;
  subject: 'politics' | 'english' | 'math';
  examType: string;
  year: number;
  durationMinutes: number;
  maxScore: number;
  questionCount: number;
  sources: Source[];
};
export type SavedAnswer = { questionId: string; answer: Answer; version: number; savedAt: string };
export type ExamSession = {
  id: string;
  paperVersionId: string;
  status: 'in_progress' | 'submitted';
  startedAt: string;
  deadlineAt: string;
  serverTime: string;
  questions: Question[];
  answers: SavedAnswer[];
  version: number;
};
export type Submission = {
  submissionId: string;
  sessionId: string;
  jobId: string;
  status: 'queued' | 'running' | 'completed' | 'failed_retryable' | 'failed';
  scoringStatus: 'queued' | 'running' | 'completed' | 'failed_retryable' | 'failed';
  submittedAt: string;
  serverTime: string;
  resultUrl: string;
};
export type ScoreDimension = { name: string; score: number; maxScore: number; feedback: string };
export type ExamResult = {
  kind: 'examScore';
  sessionId: string;
  paperVersionId: string;
  scoringStatus: 'queued' | 'running' | 'completed' | 'failed_retryable' | 'failed';
  objectiveScore: number | null;
  subjectiveEstimate: number | null;
  totalScore: number | null;
  maxScore: number;
  dimensions: ScoreDimension[];
  weakSubjectIds: string[];
  rubricVersion: string;
  modelVersion: string | null;
  scoreSnapshotId: string | null;
  version: number;
};
export type RankingEntry = {
  rank: number;
  anonymousName: string;
  score: number;
  submittedAt: string;
};
export type Ranking = {
  paperVersionId: string;
  sampleCount: number;
  myRank: number | null;
  items: RankingEntry[];
  page: number;
  pageSize: number;
  total: number;
};
export type ScoreEstimate = {
  kind: 'scoreEstimate';
  estimatedScore: number;
  lowerBound: number;
  upperBound: number;
  strategy: string[];
  scoreSnapshotIds: string[];
  methodVersion: string;
  sources: Source[];
  modelVersion: string;
};
export type InterviewQuestion = { id: string; text: string; audioFileId: string | null };
export type Interview = {
  id: string;
  programId: string;
  mode: 'professional' | 'english' | 'pressure';
  language: 'zh-CN' | 'en-US';
  duration: number;
  status: 'in_progress' | 'finished';
  firstQuestion: InterviewQuestion;
  version: number;
  createdAt: string;
};
export type InterviewTurnInput =
  { text: string; requestId: string } | { audioFileId: string; requestId: string };
export type InterviewTurn = {
  kind: 'interviewTurn';
  id: string;
  interviewId: string;
  transcript: string;
  followup: InterviewQuestion;
  dimensions: ScoreDimension[];
  suggestions: string[];
  modelVersion: string;
};
export type InterviewReport = {
  kind: 'interviewReport';
  interviewId: string;
  status: 'queued' | 'running' | 'completed' | 'failed_retryable' | 'failed';
  turns: InterviewTurn[];
  dimensions: ScoreDimension[];
  summary: string | null;
  englishSuggestions: string[];
  rubricVersion: string;
  version: number;
};
export type IntroductionReview = {
  kind: 'introductionReview';
  original: string;
  revised: string;
  language: 'zh-CN' | 'en-US';
  dimensions: ScoreDimension[];
  suggestions: string[];
  modelVersion: string;
};
export type InterviewGuidance = {
  type: 'etiquette' | 'process';
  programId: string;
  title: string;
  content: string;
  sources: Source[];
};
export type StudyEvent = {
  eventId: string;
  type: 'reading' | 'practice' | 'qa' | 'mockExam' | 'interview';
  startAt: string;
  endAt: string;
  taskId: string | null;
  focusSelfReport: number | null;
};
export type StudyEventSaved = {
  id: string;
  event: StudyEvent;
  effectiveMinutes: number;
  createdAt: string;
};
export type Dashboard = {
  from: string;
  to: string;
  timezone: 'Asia/Shanghai';
  studyMinutes: number;
  focus: number | null;
  correctRate: number | null;
  attemptCount: number;
  curve: { date: string; studyMinutes: number; correctRate: number | null; attemptCount: number }[];
  algorithmVersion: string;
};
export type WeaknessRadar = {
  subjectId: string;
  from: string;
  to: string;
  items: { knowledgeId: string; name: string; mastery: number | null; sampleCount: number }[];
  algorithmVersion: string;
};
export type WeeklyReport = {
  weekStart: string;
  timezone: 'Asia/Shanghai';
  status: 'pending' | 'completed' | 'failed_retryable';
  studyMinutes: number;
  correctRate: number | null;
  summary: string | null;
  suggestions: string[];
  algorithmVersion: string;
  generatedAt: string | null;
};
export type Notice = {
  id: string;
  title: string;
  content: string;
  programId: string | null;
  region: string | null;
  year: number;
  category:
    'policy' | 'schoolNotice' | 'registration' | 'confirmation' | 'admissionTicket' | 'exam';
  publishedAt: string;
  sources: Source[];
  version: number;
};
export type NoticeChanges = {
  noticeId: string;
  fromVersion: number;
  toVersion: number;
  changes: {
    field: 'title' | 'content' | 'publishedAt' | 'sources';
    before: string | null;
    after: string | null;
  }[];
  sources: Source[];
};
export type NewsFollows = {
  programIds: string[];
  region: string | null;
  year: number;
  version: number;
};
export type Milestone = {
  id: string;
  type: 'registration' | 'confirmation' | 'admissionTicket' | 'exam';
  year: number;
  region: string | null;
  programId: string | null;
  startsAt: string | null;
  endsAt: string | null;
  verified: boolean;
  sources: Source[];
  version: number;
};
export type Notification = {
  id: string;
  eventId: string;
  type: 'policy.updated' | 'task.reminder' | 'exam.scored';
  title: string;
  content: string;
  resourceId: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
  version: number;
};
export type FieldError = { field: string; reason: string };
export type JobError = {
  code: 'DEPENDENCY_UNAVAILABLE' | 'PROVIDER_TIMEOUT' | 'VALIDATION_FAILED' | 'INTERNAL_ERROR';
  message: string;
  retryable: boolean;
};
export type JobResult =
  | AvatarInteraction
  | SchoolEvaluation
  | Explanation
  | StudyFeedback
  | Prediction
  | Recognition
  | QaResult
  | PlanGenerated
  | ExamResult
  | ScoreEstimate
  | InterviewTurn
  | InterviewReport
  | IntroductionReview;
export type Job = {
  id: string;
  kind:
    | 'avatarInteraction'
    | 'schoolEvaluation'
    | 'explanation'
    | 'studyFeedback'
    | 'prediction'
    | 'recognition'
    | 'qa'
    | 'plan'
    | 'examScore'
    | 'scoreEstimate'
    | 'interviewTurn'
    | 'interviewReport'
    | 'introductionReview';
  status: 'queued' | 'running' | 'completed' | 'failed_retryable' | 'failed' | 'cancelled';
  version: number;
  progress: number;
  resourceId: string | null;
  resultUrl: string;
  result: JobResult | null;
  error: JobError | null;
  createdAt: string;
  updatedAt: string;
};
export type Accepted = {
  jobId: string;
  status: 'queued';
  resultUrl: string;
  resourceId: string | null;
};
export type MessageAccepted = {
  jobId: string;
  messageId: string;
  status: 'queued';
  resultUrl: string;
  contextVersion: number;
};
export type EventAiDelta = {
  eventId: string;
  type: 'ai.delta';
  resourceId: string;
  version: number;
  occurredAt: string;
  data: { jobId: string; messageId: string | null; sequence: number; text: string };
};
export type EventAiCompleted = {
  eventId: string;
  type: 'ai.completed';
  resourceId: string;
  version: number;
  occurredAt: string;
  data: { jobId: string; messageId: string | null; resultUrl: string };
};
export type EventJobFailed = {
  eventId: string;
  type: 'job.failed';
  resourceId: string;
  version: number;
  occurredAt: string;
  data: { jobId: string; error: JobError };
};
export type EventTaskReminder = {
  eventId: string;
  type: 'task.reminder';
  resourceId: string;
  version: number;
  occurredAt: string;
  data: { planId: string; taskId: string; scheduledDate: string; message: string };
};
export type EventExamScored = {
  eventId: string;
  type: 'exam.scored';
  resourceId: string;
  version: number;
  occurredAt: string;
  data: { sessionId: string; scoreSnapshotId: string; resultUrl: string };
};
export type EventPolicyUpdated = {
  eventId: string;
  type: 'policy.updated';
  resourceId: string;
  version: number;
  occurredAt: string;
  data: { noticeId: string; notificationId: string; noticeVersion: number };
};
export type EventPayload =
  | EventAiDelta
  | EventAiCompleted
  | EventJobFailed
  | EventTaskReminder
  | EventExamScored
  | EventPolicyUpdated;
export type Error400 = {
  code: 'INVALID_REQUEST';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error401 = {
  code: 'UNAUTHENTICATED' | 'INVALID_CREDENTIALS';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error403 = {
  code: 'FORBIDDEN' | 'CSRF_REJECTED';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error404 = {
  code: 'RESOURCE_NOT_FOUND';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error409 = {
  code:
    | 'VERSION_CONFLICT'
    | 'INVALID_STATE'
    | 'IDEMPOTENCY_MISMATCH'
    | 'PLAN_NOT_DRAFT'
    | 'EXAM_CLOSED';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error422 = {
  code: 'VALIDATION_FAILED' | 'INSUFFICIENT_INPUT';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error429 = {
  code: 'RATE_LIMITED';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error500 = {
  code: 'INTERNAL_ERROR';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error503 = {
  code: 'DEPENDENCY_UNAVAILABLE';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Error504 = {
  code: 'PROVIDER_TIMEOUT';
  message: string;
  data: null;
  traceId: string;
  details: FieldError[];
};
export type Response001 = { code: 'OK'; message: string; data: Csrf; traceId: string };
export type Response002 = { code: 'OK'; message: string; data: CurrentUser; traceId: string };
export type Response004 = { code: 'OK'; message: string; data: Profile; traceId: string };
export type Response005 = { code: 'OK'; message: string; data: Profile; traceId: string };
export type Response006 = { code: 'OK'; message: string; data: File; traceId: string };
export type Response007 = { code: 'OK'; message: string; data: File; traceId: string };
export type Response009 = { code: 'OK'; message: string; data: Job; traceId: string };
export type Response010 = { code: 'OK'; message: string; data: EventPayload; traceId: string };
export type AvatarAssets = { items: AvatarAsset[] };
export type Response011 = { code: 'OK'; message: string; data: AvatarAssets; traceId: string };
export type Response012 = { code: 'OK'; message: string; data: AvatarPreferences; traceId: string };
export type Response013 = { code: 'OK'; message: string; data: AvatarPreferences; traceId: string };
export type Response014 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type CheckinInput = { localDate: string };
export type Response015 = { code: 'OK'; message: string; data: Checkin; traceId: string };
export type Response016 = { code: 'OK'; message: string; data: Supervision; traceId: string };
export type Response017 = {
  code: 'OK';
  message: string;
  data: ReminderPreferences;
  traceId: string;
};
export type Response018 = {
  code: 'OK';
  message: string;
  data: ReminderPreferences;
  traceId: string;
};
export type ProgramPage = { items: Program[]; page: number; pageSize: number; total: number };
export type Response019 = { code: 'OK'; message: string; data: ProgramPage; traceId: string };
export type ProgramRecommendations = { items: ProgramRecommendation[] };
export type Response020 = {
  code: 'OK';
  message: string;
  data: ProgramRecommendations;
  traceId: string;
};
export type Response021 = { code: 'OK'; message: string; data: ProgramFacts; traceId: string };
export type ComparisonInput = { programIds: string[]; year: number };
export type Response022 = { code: 'OK'; message: string; data: ProgramComparison; traceId: string };
export type EvaluationInput = { programId: string; estimatedScore: number; profileVersion: number };
export type Response023 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type Response024 = { code: 'OK'; message: string; data: SchoolEvaluation; traceId: string };
export type Response025 = { code: 'OK'; message: string; data: KnowledgeTree; traceId: string };
export type PublicExplanationInput = {
  knowledgeId: string;
  level: 'basic' | 'standard' | 'advanced';
  version: number;
};
export type Response026 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type ExamPoints = { items: ExamPoint[] };
export type Response027 = { code: 'OK'; message: string; data: ExamPoints; traceId: string };
export type EnglishTaskInput = { type: 'vocabulary' | 'sentence' | 'composition'; content: string };
export type Response028 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type PoliticsTaskInput = {
  type: 'syllabus' | 'prediction' | 'recitation' | 'currentAffairs';
  year: number;
  content?: string;
};
export type Response029 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type MathTaskInput = { type: 'derivation' | 'classification' | 'guidance'; content: string };
export type Response030 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type Subjects = { items: Subject[] };
export type Response031 = { code: 'OK'; message: string; data: Subjects; traceId: string };
export type Response032 = { code: 'OK'; message: string; data: KnowledgeTree; traceId: string };
export type BookExplanationInput = {
  bookId: string;
  edition: string;
  chapterId: string;
  question: string;
};
export type Response033 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type PastQuestionPage = { items: Question[]; page: number; pageSize: number; total: number };
export type Response034 = { code: 'OK'; message: string; data: PastQuestionPage; traceId: string };
export type ProfessionalExplanationInput = {
  questionVersionId: string;
  level: 'basic' | 'standard' | 'advanced';
};
export type Response035 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type PredictionInput = {
  subjectId: string;
  targetYear: number;
  historyRange: { fromYear: number; toYear: number };
};
export type Response036 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type ConversationInput = { subjectId: string };
export type Response037 = { code: 'OK'; message: string; data: Conversation; traceId: string };
export type RecognitionInput = { fileId: string; subjectId: string };
export type Response038 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type Response039 = { code: 'OK'; message: string; data: MessageAccepted; traceId: string };
export type MessagePage = { items: QaMessage[]; page: number; pageSize: number; total: number };
export type Response040 = { code: 'OK'; message: string; data: MessagePage; traceId: string };
export type Response041 = { code: 'OK'; message: string; data: QaMessage; traceId: string };
export type CancelMessageInput = { version: number };
export type Response042 = { code: 'OK'; message: string; data: QaMessage; traceId: string };
export type PracticeSessionInput = {
  type: 'daily' | 'weekly' | 'special';
  subjectId: string;
  knowledgeId: string | null;
  difficulty: 'easy' | 'medium' | 'hard';
};
export type Response043 = { code: 'OK'; message: string; data: PracticeSession; traceId: string };
export type Response044 = { code: 'OK'; message: string; data: PracticeSession; traceId: string };
export type PracticeAttemptInput = { questionVersionId: string; answer: Answer; requestId: string };
export type Response045 = { code: 'OK'; message: string; data: PracticeAttempt; traceId: string };
export type WrongItemPage = { items: WrongItem[]; page: number; pageSize: number; total: number };
export type Response046 = { code: 'OK'; message: string; data: WrongItemPage; traceId: string };
export type AttemptPage = {
  items: PracticeAttempt[];
  page: number;
  pageSize: number;
  total: number;
};
export type Response047 = { code: 'OK'; message: string; data: AttemptPage; traceId: string };
export type SimilarQuestions = { items: Question[] };
export type Response048 = { code: 'OK'; message: string; data: SimilarQuestions; traceId: string };
export type Response049 = { code: 'OK'; message: string; data: KnowledgeProgress; traceId: string };
export type PlanInput = {
  profileVersion: number;
  targetDate: string;
  dailyMinutes: number;
  baseline: Baseline[];
};
export type Response050 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type Response051 = { code: 'OK'; message: string; data: Plan; traceId: string };
export type PlanTaskPatch = {
  scheduledDate?: string;
  minutes?: number;
  content?: string;
  version: number;
};
export type Response052 = { code: 'OK'; message: string; data: PlanTask; traceId: string };
export type PlanConfirmInput = { version: number };
export type Response053 = { code: 'OK'; message: string; data: Plan; traceId: string };
export type PlanAdjustmentInput = { reason: string; timeBudget: number; progressVersion: number };
export type Response054 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type CompleteTaskInput = { version: number; eventId: string };
export type Response055 = { code: 'OK'; message: string; data: PlanTask; traceId: string };
export type Response056 = { code: 'OK'; message: string; data: PlanProgress; traceId: string };
export type PaperPage = { items: Paper[]; page: number; pageSize: number; total: number };
export type Response057 = { code: 'OK'; message: string; data: PaperPage; traceId: string };
export type ExamStartInput = { paperVersionId: string; requestId: string };
export type Response058 = { code: 'OK'; message: string; data: ExamSession; traceId: string };
export type Response059 = { code: 'OK'; message: string; data: ExamSession; traceId: string };
export type ExamAnswerInput = { answer: Answer; version: number; requestId: string };
export type Response060 = { code: 'OK'; message: string; data: SavedAnswer; traceId: string };
export type ExamSubmitInput = { version: number };
export type Response061 = { code: 'OK'; message: string; data: Submission; traceId: string };
export type Response062 = { code: 'OK'; message: string; data: ExamResult; traceId: string };
export type Response063 = { code: 'OK'; message: string; data: Ranking; traceId: string };
export type ScoreEstimateInput = { scoreSnapshotIds: string[]; profileVersion: number };
export type Response064 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type InterviewInput = {
  programId: string;
  mode: 'professional' | 'english' | 'pressure';
  language: 'zh-CN' | 'en-US';
  duration: number;
  consent: true;
};
export type Response065 = { code: 'OK'; message: string; data: Interview; traceId: string };
export type Response066 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type InterviewFinishInput = { version: number };
export type Response067 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type Response068 = { code: 'OK'; message: string; data: InterviewReport; traceId: string };
export type IntroductionReviewInput = {
  content: string;
  language: 'zh-CN' | 'en-US';
  programId: string;
};
export type Response069 = { code: 'OK'; message: string; data: Accepted; traceId: string };
export type Response070 = { code: 'OK'; message: string; data: InterviewGuidance; traceId: string };
export type Response071 = { code: 'OK'; message: string; data: StudyEventSaved; traceId: string };
export type Response072 = { code: 'OK'; message: string; data: Dashboard; traceId: string };
export type Response073 = { code: 'OK'; message: string; data: WeaknessRadar; traceId: string };
export type Response074 = { code: 'OK'; message: string; data: WeeklyReport; traceId: string };
export type NoticePage = { items: Notice[]; page: number; pageSize: number; total: number };
export type Response075 = { code: 'OK'; message: string; data: NoticePage; traceId: string };
export type Response076 = { code: 'OK'; message: string; data: NoticeChanges; traceId: string };
export type Response077 = { code: 'OK'; message: string; data: NewsFollows; traceId: string };
export type Response078 = { code: 'OK'; message: string; data: NewsFollows; traceId: string };
export type Milestones = { items: Milestone[] };
export type Response079 = { code: 'OK'; message: string; data: Milestones; traceId: string };
export type NotificationPage = {
  items: Notification[];
  page: number;
  pageSize: number;
  total: number;
};
export type Response080 = { code: 'OK'; message: string; data: NotificationPage; traceId: string };
export type NotificationReadInput = { version: number };
export type Response081 = { code: 'OK'; message: string; data: Notification; traceId: string };
export interface Operations {
  op001: { input: {}; output: Csrf };
  op002: { input: { body: LoginInput }; output: CurrentUser };
  op003: { input: {}; output: undefined };
  op004: { input: {}; output: Profile };
  op005: { input: { body: ProfileInput }; output: Profile };
  op006: { input: { body: FormData }; output: File };
  op007: { input: { path: { id: string } }; output: Blob };
  op008: { input: { path: { id: string } }; output: undefined };
  op009: { input: { path: { id: string } }; output: Job };
  op010: { input: {}; output: EventPayload };
  op011: { input: { query?: { shapeId?: string } }; output: AvatarAssets };
  op012: { input: {}; output: AvatarPreferences };
  op013: { input: { body: AvatarPreferences }; output: AvatarPreferences };
  op014: { input: { body: AvatarInteractionInput }; output: Accepted };
  op015: { input: { body: CheckinInput }; output: Checkin };
  op016: { input: { query: { date: string } }; output: Supervision };
  op017: { input: {}; output: ReminderPreferences };
  op018: { input: { body: ReminderPreferences }; output: ReminderPreferences };
  op019: {
    input: {
      query: {
        major?: string;
        region?: string;
        year: number;
        score?: number;
        difficulty?: 'easy' | 'medium' | 'hard';
        page?: number;
        pageSize?: number;
      };
    };
    output: ProgramPage;
  };
  op020: { input: { body: RecommendationInput }; output: ProgramRecommendations };
  op021: {
    input: {
      path: { id: string };
      query: { year: number; metricTypes?: ('scoreLine' | 'enrollment' | 'admissionRatio')[] };
    };
    output: ProgramFacts;
  };
  op022: { input: { body: ComparisonInput }; output: ProgramComparison };
  op023: { input: { body: EvaluationInput }; output: Accepted };
  op024: { input: { path: { id: string } }; output: SchoolEvaluation };
  op025: {
    input: { query: { subject: 'politics' | 'english' | 'math'; examType: string; year: number } };
    output: KnowledgeTree;
  };
  op026: { input: { body: PublicExplanationInput }; output: Accepted };
  op027: {
    input: {
      query: {
        subject: 'politics' | 'english' | 'math';
        type: 'highFrequency' | 'errorProne' | 'memorize';
      };
    };
    output: ExamPoints;
  };
  op028: { input: { body: EnglishTaskInput }; output: Accepted };
  op029: { input: { body: PoliticsTaskInput }; output: Accepted };
  op030: { input: { body: MathTaskInput }; output: Accepted };
  op031: {
    input: {
      query: {
        discipline: 'computer' | 'education' | 'economicsManagement' | 'medicine';
        programId: string;
        year: number;
      };
    };
    output: Subjects;
  };
  op032: { input: { query: { subjectId: string; bookEdition: string } }; output: KnowledgeTree };
  op033: { input: { body: BookExplanationInput }; output: Accepted };
  op034: {
    input: {
      query: {
        subjectId: string;
        year?: number;
        knowledgeId?: string;
        page?: number;
        pageSize?: number;
      };
    };
    output: PastQuestionPage;
  };
  op035: { input: { body: ProfessionalExplanationInput }; output: Accepted };
  op036: { input: { body: PredictionInput }; output: Accepted };
  op037: { input: { body: ConversationInput }; output: Conversation };
  op038: { input: { body: RecognitionInput }; output: Accepted };
  op039: { input: { path: { id: string }; body: QaMessageInput }; output: MessageAccepted };
  op040: {
    input: { path: { id: string }; query?: { page?: number; pageSize?: number } };
    output: MessagePage;
  };
  op041: { input: { path: { id: string } }; output: QaMessage };
  op042: { input: { path: { id: string }; body: CancelMessageInput }; output: QaMessage };
  op043: { input: { body: PracticeSessionInput }; output: PracticeSession };
  op044: { input: { path: { id: string } }; output: PracticeSession };
  op045: { input: { path: { id: string }; body: PracticeAttemptInput }; output: PracticeAttempt };
  op046: {
    input: {
      query?: { subjectId?: string; knowledgeId?: string; page?: number; pageSize?: number };
    };
    output: WrongItemPage;
  };
  op047: {
    input: { path: { id: string }; query?: { page?: number; pageSize?: number } };
    output: AttemptPage;
  };
  op048: {
    input: {
      query: { questionId: string; knowledgeId: string; difficulty: 'easy' | 'medium' | 'hard' };
    };
    output: SimilarQuestions;
  };
  op049: {
    input: { query: { subjectId: string; timeRange: 'week' | 'month' | 'all' } };
    output: KnowledgeProgress;
  };
  op050: { input: { body: PlanInput }; output: Accepted };
  op051: {
    input: { path: { id: string }; query: { view: 'day' | 'week' | 'month'; date: string } };
    output: Plan;
  };
  op052: { input: { path: { id: string; taskId: string }; body: PlanTaskPatch }; output: PlanTask };
  op053: { input: { path: { id: string }; body: PlanConfirmInput }; output: Plan };
  op054: { input: { path: { id: string }; body: PlanAdjustmentInput }; output: Accepted };
  op055: {
    input: { path: { id: string; taskId: string }; body: CompleteTaskInput };
    output: PlanTask;
  };
  op056: {
    input: { path: { id: string }; query: { timeRange: 'week' | 'month' | 'all' } };
    output: PlanProgress;
  };
  op057: {
    input: {
      query: {
        subject: 'politics' | 'english' | 'math';
        examType: string;
        year?: number;
        page?: number;
        pageSize?: number;
      };
    };
    output: PaperPage;
  };
  op058: { input: { body: ExamStartInput }; output: ExamSession };
  op059: { input: { path: { id: string } }; output: ExamSession };
  op060: {
    input: { path: { id: string; qid: string }; body: ExamAnswerInput };
    output: SavedAnswer;
  };
  op061: { input: { path: { id: string }; body: ExamSubmitInput }; output: Submission };
  op062: { input: { path: { id: string } }; output: ExamResult };
  op063: {
    input: {
      path: { id: string };
      query: { paperVersionId: string; page?: number; pageSize?: number };
    };
    output: Ranking;
  };
  op064: { input: { body: ScoreEstimateInput }; output: Accepted };
  op065: { input: { body: InterviewInput }; output: Interview };
  op066: { input: { path: { id: string }; body: InterviewTurnInput }; output: Accepted };
  op067: { input: { path: { id: string }; body: InterviewFinishInput }; output: Accepted };
  op068: { input: { path: { id: string } }; output: InterviewReport };
  op069: { input: { body: IntroductionReviewInput }; output: Accepted };
  op070: {
    input: { query: { type: 'etiquette' | 'process'; programId: string } };
    output: InterviewGuidance;
  };
  op071: { input: { body: StudyEvent }; output: StudyEventSaved };
  op072: {
    input: { query: { from: string; to: string; subjectId?: string; timezone: 'Asia/Shanghai' } };
    output: Dashboard;
  };
  op073: {
    input: { query: { subjectId: string; from: string; to: string } };
    output: WeaknessRadar;
  };
  op074: {
    input: { query: { weekStart: string; timezone: 'Asia/Shanghai' } };
    output: WeeklyReport;
  };
  op075: {
    input: {
      query: {
        programId?: string;
        region?: string;
        year: number;
        page?: number;
        pageSize?: number;
      };
    };
    output: NoticePage;
  };
  op076: {
    input: { path: { id: string }; query: { fromVersion: number; toVersion: number } };
    output: NoticeChanges;
  };
  op077: { input: {}; output: NewsFollows };
  op078: { input: { body: NewsFollows }; output: NewsFollows };
  op079: {
    input: { query: { year: number; region?: string; programId?: string } };
    output: Milestones;
  };
  op080: {
    input: {
      query?: { afterEventId?: string; unreadOnly?: boolean; page?: number; pageSize?: number };
    };
    output: NotificationPage;
  };
  op081: { input: { path: { id: string }; body: NotificationReadInput }; output: Notification };
}
