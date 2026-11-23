/**
 * Built-in template type ids. Plain string so plugin authors can introduce
 * new ids server-side and the UI cope (it falls back to a generic message
 * when a renderer isn't registered).
 */
export type TemplateType =
  | 'MULTIPLE_CHOICE'
  | 'FREE_TEXT'
  | 'IMAGE_CLASSIFICATION'
  | 'SEARCH_RELEVANCE'
  | 'SPAN_ANNOTATION'
  | 'BOUNDING_BOX'
  | (string & {});

export type EndOfWorkMessageCode = 'COME_BACK_LATER' | 'COMPLETE';

export interface Job {
  id: number;
  name: string;
  description?: string;
  /** Optional Mustache-style template for rendering the unit payload. */
  displayTemplate?: string;
  templateType: TemplateType;
  templateConfig: any;
  /** 1 gold mixed in per N non-gold tasks. */
  goldCadence: number;
  /** Tasks the worker UI fetches per page. */
  tasksPerPage: number;
  /** Copy shown when the worker has no more work. */
  endOfWorkMessage: EndOfWorkMessageCode;
  /** Minimum judgments per unit before it can be marked complete. */
  minJudgments: number;
  /**
   * Maximum judgments per unit. Equal to minJudgments → fixed-N consensus.
   * Larger → escalate to additional reviewers when workers disagree.
   */
  maxJudgments: number;
  trustFloor: string | number;
  promoteAgreementMin: string | number;
  autoPromoteToGold: boolean;
  status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
}

/** The shape returned by the worker feed — a page of tasks plus optional end-of-work copy. */
export interface TaskPage {
  tasks: WorkerUnit[];
  /** Set when tasks is empty — what state the worker is in. */
  code?: EndOfWorkMessageCode;
  /** Human copy paired with code. */
  message?: string;
}

/** A single configurable knob for a template type — drives the variant form. */
export interface VariantField {
  name: string;
  label: string;
  kind: 'string' | 'number' | 'boolean' | 'string-list' | 'scale' | 'json' | string;
  required?: boolean;
  default?: any;
  help?: string;
}

/** Optional payload-shape hints surfaced by a template — used by the dataset format helper. */
export interface PayloadSchema {
  description?: string;
  requiredFields?: string[];
  exampleUnit?: any;
}

export interface VariantSchema {
  type: string;
  variantSchema: { fields: VariantField[] };
  payloadSchema?: PayloadSchema;
}

/** Result of POST /api/templates/{type}/validate-dataset — surfaces row-level problems. */
export interface DatasetValidation {
  ok: boolean;
  summary: { total: number; valid: number; invalid: number; withGold: number };
  errors: { index?: number; at?: string; error: string }[];
}

export interface WorkerUnit {
  id: number;
  jobId: number;
  templateType: TemplateType;
  templateConfig: any;
  payload: any;
  /** Optional Mustache-style template the renderer interpolates against the payload. */
  displayTemplate?: string;
}

export interface AdminUnit {
  id: number;
  jobId: number;
  externalId?: string;
  payload: any;
  goldAnswer?: any;
  goldExplanation?: string;
  /** When true the unit shows up on the worker's job-instructions page as study material. */
  exemplar: boolean;
  source: 'UPLOADED' | 'PROMOTED';
  status: 'OPEN' | 'COMPLETED';
  judgmentCount: number;
  /** Worker (admin or labeler) credited for this unit's gold answer. */
  promotedByUserId?: number;
  promotedByUsername?: string;
  promotedFromJudgmentId?: number;
  /** Counts of judgments that matched / didn't match this unit's gold answer. */
  goldMatches?: number;
  goldMismatches?: number;
  /** Distinct answers workers picked for this gold unit, with counts and correctness. */
  goldJudgmentBreakdown?: GoldJudgmentBreakdown[];
}

/** One row in {@link AdminUnit#goldJudgmentBreakdown}. */
export interface GoldJudgmentBreakdown {
  answer: any;
  count: number;
  correct: boolean;
}

export type ConcernStatus = 'OPEN' | 'RESOLVED';
export type ConcernResolution = 'DISMISS' | 'EDIT' | 'DELETE';

/** A worker's concern about a unit, with denormalized unit + raiser info for the admin inbox. */
export interface ConcernRow {
  id: number;
  unitId: number;
  jobId?: number;
  raisedByUserId: number;
  raisedByUsername?: string;
  raisedAt: string;
  body: string;
  status: ConcernStatus;
  resolution?: ConcernResolution;
  resolutionNote?: string;
  resolvedByUserId?: number;
  resolvedByUsername?: string;
  resolvedAt?: string;
  payload?: any;
  unitStatus?: 'OPEN' | 'COMPLETED' | 'ARCHIVED';
  judgmentCount: number;
}

export interface JobInstructions {
  jobId: number;
  name: string;
  description?: string;
  templateType: string;
  templateConfig: any;
  exemplars: {
    unitId: number;
    payload: any;
    goldAnswer: any;
    goldExplanation?: string;
  }[];
}

export interface MonitorReport {
  jobId: number;
  totalUnits: number;
  goldUnits: number;
  completedUnits: number;
  openNonGoldUnits: number;
  judgmentsCollected: number;
  judgmentsNeeded: number;
  percentComplete: number;
  workers: MonitorWorker[];
}

export interface MonitorWorker {
  userId: number;
  judgmentsTotal: number;
  goldAttempts: number;
  goldCorrect: number;
  accuracy: string | null;
  status: 'OK' | 'QUARANTINED' | string;
  meanTimeMs: number;
  throughputPerHour: number;
}

export interface Me {
  authenticated: boolean;
  debug: boolean;
  id?: number;
  username?: string;
  role?: 'ADMIN' | 'WORKER';
  /** The login service's role names for this app: admin, labeler. */
  roles?: string[];
  name?: string;
  email?: string;
}

export interface DebugUser { id: number; username: string; role: 'ADMIN' | 'WORKER' }

export interface AgreementReport {
  jobId: number;
  totalUnits: number;
  goldUnits: number;
  completedUnits: number;
  meanPairwiseAgreement: number;
  agreementMethod: string;
  workerTrust: { userId: number; goldAttempts: number; goldCorrect: number; accuracy: string; status: string }[];
  ndcg?: { perQuery: { query: string; ndcg: number; results: number }[]; meanNdcg: number; queries: number };
}
