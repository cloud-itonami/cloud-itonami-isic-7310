(ns advertising.advertisingadvisor
  "AdOps-LLM client -- the *contained intelligence node* for the
  advertising actor (README: \"AdOps-LLM\").

  It normalizes campaign-intake, drafts a per-jurisdiction
  advertising-standards evidence checklist, screens campaigns for an
  unresolved misleading-claim risk, screens the YouTube channel /
  influencer a campaign wants to commission for eligibility, drafts
  the creator-tie-up evidence checklist, and drafts BOTH real-world
  actions (campaign placement; creator-tie-up order). CRITICAL: it is
  a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record, a real
  campaign placement or a real order sent to a creator. Every output
  is censored downstream by `advertising.governor` before anything
  touches the SSoT, and neither `:actuation/place-campaign` nor
  `:actuation/order-creator-tieup` proposals EVER auto-commit at any
  phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/place-campaign |
                                ; :actuation/order-creator-tieup | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [advertising.facts :as facts]
            [advertising.registry :as registry]
            [advertising.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the campaign, jurisdiction or budget. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "案件記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :campaign/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-media-plan
  "Per-jurisdiction advertising-standards evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `advertising.facts` -- the Campaign Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [c (store/campaign db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction c))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "advertising.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :media-plan/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :media-plan/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-misleading-claim-risk
  "Misleading-claim-risk screening draft. `:misleading-claim-risk-
  unresolved?` on the campaign record injects the failure mode: the
  Campaign Governor must HOLD, un-overridably, on any unresolved
  risk."
  [db {:keys [subject]}]
  (let [c (store/campaign db subject)]
    (cond
      (nil? c)
      {:summary "対象案件記録が見つかりません" :rationale "no campaign record"
       :cites [] :effect :risk-screen/set :value {:campaign-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:misleading-claim-risk-unresolved? c))
      {:summary    (str (:client-name c) ": 未解決の誤認表示リスクを検出")
       :rationale  "スクリーニングが未解決の誤認表示リスクを検出。人手確認とホールドが必須。"
       :cites      [:misleading-claim-check]
       :effect     :risk-screen/set
       :value      {:campaign-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:client-name c) ": 未解決の誤認表示リスクなし")
       :rationale  "誤認表示リスクスクリーニング完了。"
       :cites      [:misleading-claim-check]
       :effect     :risk-screen/set
       :value      {:campaign-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-campaign-placement
  "Draft the actual CAMPAIGN-PLACEMENT action -- placing/publishing a
  real campaign on the client's behalf. ALWAYS `:stake :actuation/
  place-campaign` -- this is a REAL-WORLD advertising act, never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`advertising.phase`); the
  governor also always escalates on `:actuation/place-campaign`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [c (store/campaign db subject)]
    {:summary    (str subject " 向けキャンペーン出稿提案"
                      (when c (str " (client=" (:client-name c) ")")))
     :rationale  (if c
                   (str "proposed-media-spend=" (:proposed-media-spend c)
                        " authorized-budget=" (:authorized-budget c))
                   "案件記録が見つかりません")
     :cites      (if c [subject] [])
     :effect     :campaign/mark-placed
     :value      {:campaign-id subject}
     :stake      :actuation/place-campaign
     :confidence (if (and c (not (registry/media-spend-exceeds-authorized-budget? c))) 0.9 0.3)}))

;; -------------------- creator tie-up (YouTube / influencer) --------------------

(defn- screen-creator
  "Creator-eligibility screening draft for the YouTube channel /
  influencer a campaign wants to commission. `:creator-eligibility-
  issue?` on the campaign record injects the failure mode: the
  Campaign Governor must HOLD, un-overridably, on any unresolved
  eligibility issue -- the tie-up analog of `screen-misleading-claim-
  risk`."
  [db {:keys [subject]}]
  (let [c (store/campaign db subject)]
    (cond
      (nil? c)
      {:summary "対象案件記録が見つかりません" :rationale "no campaign record"
       :cites [] :effect :creator-screen/set :value {:campaign-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (nil? (:creator-handle c))
      {:summary (str subject " に起用予定クリエイターが記録されていません")
       :rationale "creator-handle 未記録の案件は適格性審査の対象にできない。"
       :cites [] :effect :creator-screen/set
       :value {:campaign-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:creator-eligibility-issue? c))
      {:summary    (str (:creator-handle c) " (" (name (:creator-platform c))
                        "): 未解決の適格性問題を検出")
       :rationale  "適格性スクリーニングが未解決の問題を検出。人手確認とホールドが必須。"
       :cites      [:creator-eligibility-check (:creator-handle c)]
       :effect     :creator-screen/set
       :value      {:campaign-id subject :verdict :ineligible
                    :creator-handle (:creator-handle c)
                    :platform (:creator-platform c)}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:creator-handle c) " (" (name (:creator-platform c))
                        "): 未解決の適格性問題なし")
       :rationale  "クリエイター適格性スクリーニング完了。"
       :cites      [:creator-eligibility-check (:creator-handle c)]
       :effect     :creator-screen/set
       :value      {:campaign-id subject :verdict :eligible
                    :creator-handle (:creator-handle c)
                    :platform (:creator-platform c)}
       :stake      nil
       :confidence 0.9})))

(defn- verify-tieup-brief
  "Per-jurisdiction creator-tie-up evidence checklist draft, citing the
  jurisdiction's SPONSORSHIP-DISCLOSURE basis specifically (not the
  general advertising-standards one) -- an operator disputing a tie-up
  order needs that citation. `:no-spec?` injects the same failure mode
  `verify-media-plan` defends against: proposing a checklist for a
  jurisdiction with NO official basis in `advertising.facts`.

  Note what this proposal does NOT do: it reports the campaign's
  recorded disclosure label verbatim and never proposes one. Choosing
  a disclosure wording for a client is a legal act; the governor
  checks the recorded label against the authority's own published
  examples, and an advisor that invented a plausible-looking label
  would defeat exactly that check."
  [db {:keys [subject no-spec?]}]
  (let [c (store/campaign db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction c))
        disc (facts/disclosure-basis iso3)]
    (if (nil? disc)
      {:summary    (str iso3 " の公式な開示表示(スポンサーシップ開示)基準が見つかりません")
       :rationale  "advertising.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :tieup-brief/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority disc) ") 向けタイアップ必要書類 "
                        (count (facts/tieup-evidence-checklist iso3)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance disc) " / 法的根拠: " (:legal-basis disc)
                        " / 記録済み開示表示: " (pr-str (:disclosure-label c)))
       :cites      [(:legal-basis disc) (:provenance disc)]
       :effect     :tieup-brief/set
       :value      {:jurisdiction iso3
                    :checklist (facts/tieup-evidence-checklist iso3)
                    :spec-basis (:provenance disc)
                    :legal-basis (:legal-basis disc)
                    :recorded-disclosure-label (:disclosure-label c)
                    :accepted-disclosure-labels (facts/accepted-disclosure-labels iso3)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-creator-tieup-order
  "Draft the actual CREATOR-TIE-UP ORDER action -- commissioning a paid
  post from a named YouTube channel / influencer on the client's
  behalf. ALWAYS `:stake :actuation/order-creator-tieup` -- this is a
  REAL-WORLD act against a third party, never a draft the actor may
  auto-run. See README `Actuation`: no phase ever adds this op to a
  phase's `:auto` set (`advertising.phase`); the governor also always
  escalates on it. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [c (store/campaign db subject)]
    {:summary    (str subject " 向けクリエイタータイアップ発注提案"
                      (when c (str " (" (:creator-handle c)
                                   " / " (some-> (:creator-platform c) name) ")")))
     :rationale  (if c
                   (str "media-spend=" (:proposed-media-spend c)
                        " tieup-fee=" (:creator-tieup-fee c)
                        " authorized-budget=" (:authorized-budget c)
                        " disclosure-label=" (pr-str (:disclosure-label c)))
                   "案件記録が見つかりません")
     :cites      (if c [subject (:creator-handle c)] [])
     :effect     :tieup/mark-ordered
     :value      {:campaign-id subject
                  :creator-handle (:creator-handle c)
                  :platform (:creator-platform c)}
     :stake      :actuation/order-creator-tieup
     :confidence (if (and c (not (registry/creator-tieup-fee-exceeds-authorized-budget? c))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :campaign/intake                  (normalize-intake db request)
    :media-plan/verify                (verify-media-plan db request)
    :risk/screen                      (screen-misleading-claim-risk db request)
    :actuation/place-campaign          (propose-campaign-placement db request)
    :creator/screen                   (screen-creator db request)
    :tieup/verify                     (verify-tieup-brief db request)
    :actuation/order-creator-tieup     (propose-creator-tieup-order db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは広告代理店のキャンペーン出稿エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:campaign/upsert|:media-plan/set|:risk-screen/set|"
       ":campaign/mark-placed) "
       ":stake(:actuation/place-campaign か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :media-plan/verify                {:campaign (store/campaign st subject)}
    :risk/screen                      {:campaign (store/campaign st subject)}
    :actuation/place-campaign          {:campaign (store/campaign st subject)}
    {:campaign (store/campaign st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Campaign Governor
  escalates/holds -- an LLM hiccup can never auto-place a campaign."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :advertisingadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
