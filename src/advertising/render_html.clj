(ns advertising.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (advertising.operation -> advertising.governor
  -> advertising.store) through the SAME multi-disposition scenario as
  `advertising.sim` (real seed ids/ops -- see that ns for the narrative).
  No invented numbers, no timestamps, byte-identical across reruns."
  (:require [clojure.string :as str]
            [advertising.store :as store]
            [advertising.operation :as op]
            [advertising.phase :as phase]
            [advertising.governor :as governor]
            [langgraph.graph :as g]))

(def ^:private operator {:actor-id "op-1" :actor-role :agency-operator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn run-demo!
  "Drives the real OperationActor through a genuine multi-disposition
  scenario built from THIS repo's own seed data (advertising.store/
  seed-db) and rules (advertising.governor) -- the same scenario
  `advertising.sim` walks, exercised here for HTML capture instead of
  stdout printing. Covers:
    - one op that auto-commits clean at phase 3 (:campaign/intake)
    - the one always-escalate high-stakes op (:actuation/place-campaign),
      approved by a human
    - four DISTINCT HARD-hold reasons that never reach a human:
      :no-spec-basis, :media-spend-exceeds-authorized-budget,
      :misleading-claim-risk-unresolved, :already-placed."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    ;; campaign-1 (JPN): clean intake auto-commits at phase 3.
    (exec! actor "t1" {:op :campaign/intake :subject "campaign-1"
                       :patch {:id "campaign-1" :client-name "Sato Bakery"}})

    ;; campaign-1: media-plan verification -- escalates (phase-approval), approved.
    (exec! actor "t2" {:op :media-plan/verify :subject "campaign-1"})
    (approve! actor "t2")

    ;; campaign-1: misleading-claim-risk screening -- clean, escalates, approved.
    (exec! actor "t3" {:op :risk/screen :subject "campaign-1"})
    (approve! actor "t3")

    ;; campaign-1: campaign placement -- ALWAYS escalates (governor high-stakes
    ;; set, advertising.phase never auto-eligible at any phase), approved.
    (exec! actor "t4" {:op :actuation/place-campaign :subject "campaign-1"})
    (approve! actor "t4")

    ;; campaign-2 (ATL, no facts/catalog entry): HARD hold, :no-spec-basis --
    ;; never reaches a human.
    (exec! actor "t5" {:op :media-plan/verify :subject "campaign-2" :no-spec? true})

    ;; campaign-3 (JPN): media-plan verification escalates, approved -- sets up
    ;; the budget-exceeded HARD hold below.
    (exec! actor "t6" {:op :media-plan/verify :subject "campaign-3"})
    (approve! actor "t6")

    ;; campaign-3: proposed media spend 900000 > authorized budget 800000 ->
    ;; HARD hold, :media-spend-exceeds-authorized-budget -- never reaches a human.
    (exec! actor "t7" {:op :actuation/place-campaign :subject "campaign-3"})

    ;; campaign-4 (misleading-claim-risk-unresolved? true): HARD hold,
    ;; :misleading-claim-risk-unresolved -- never reaches a human.
    (exec! actor "t8" {:op :risk/screen :subject "campaign-4"})

    ;; campaign-1 AGAIN: already placed in t4 -> HARD hold, :already-placed --
    ;; never reaches a human.
    (exec! actor "t9" {:op :actuation/place-campaign :subject "campaign-1"})

    db))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- last-fact-for
  "Most recent ledger fact for `campaign-id`. The real store/ledger! facts
  in this actor all key the subject under `:subject` (advertising.governor/
  hold-fact and advertising.operation's commit-fact/hold node both build
  facts with a `:subject` key, verified against the running ledger below)."
  [ledger campaign-id]
  (last (filter #(= campaign-id (:subject %)) ledger)))

(defn- status-cell [fact]
  (cond
    (nil? fact) ["muted" "in progress"]
    (= :committed (:t fact)) ["ok" "committed"]
    (= :approval-granted (:t fact)) ["ok" "approval-granted"]
    (= :governor-hold (:t fact)) ["err" (str "governor-hold: " (str/join "," (map name (:basis fact))))]
    (= :approval-rejected (:t fact)) ["err" "approval-rejected"]
    (= :approval-requested (:t fact)) ["warn" "approval-requested"]
    :else ["muted" "in progress"]))

(defn- disposition-cell [fact]
  (cond
    (nil? fact) ["muted" "--"]
    (= :hold (:disposition fact)) ["err" "hold"]
    (= :commit (:disposition fact)) ["ok" "commit"]
    :else ["muted" (str (:disposition fact))]))

;; ----------------------------- tables -----------------------------

(defn- campaign-rows [db]
  (let [ledger (store/ledger db)]
    (for [c (store/all-campaigns db)]
      (let [fact (last-fact-for ledger (:id c))
            [cls label] (status-cell fact)]
        (str "<tr>"
             "<td><code>" (esc (:id c)) "</code></td>"
             "<td>" (esc (:client-name c)) "</td>"
             "<td>" (esc (:jurisdiction c)) "</td>"
             "<td>" (esc (:proposed-media-spend c)) "</td>"
             "<td>" (esc (:authorized-budget c)) "</td>"
             "<td class=\"" (if (:misleading-claim-risk-unresolved? c) "err" "muted") "\">"
             (if (:misleading-claim-risk-unresolved? c) "unresolved" "none") "</td>"
             "<td class=\"" (if (:campaign-placed? c) "ok" "muted") "\">"
             (if (:campaign-placed? c) (str "placed (" (esc (:placement-number c)) ")") "not placed") "</td>"
             "<td class=\"" cls "\">" (esc label) "</td>"
             "</tr>")))))

(defn- action-gate-rows []
  (for [[phase-n {:keys [label writes auto]}] (sort-by key phase/phases)]
    (str "<tr>"
         "<td>" phase-n "</td>"
         "<td>" (esc label) "</td>"
         "<td>" (if (seq writes) (esc (str/join ", " (sort (map name writes)))) "<span class=\"muted\">none</span>") "</td>"
         "<td>" (if (seq auto) (esc (str/join ", " (sort (map name auto)))) "<span class=\"muted\">none -- human approval required</span>") "</td>"
         "<td>" (str/join ", " (map (comp esc name) (sort governor/high-stakes)))
         " <span class=\"muted\">(never auto-eligible, at any phase)</span></td>"
         "</tr>")))

(defn- ledger-rows [db]
  (for [[i fact] (map-indexed vector (store/ledger db))]
    (let [[dcls dlabel] (disposition-cell fact)]
      (str "<tr>"
           "<td>" (inc i) "</td>"
           "<td><code>" (esc (name (:op fact))) "</code></td>"
           "<td><code>" (esc (:subject fact)) "</code></td>"
           "<td>" (esc (:actor fact)) "</td>"
           "<td class=\"" dcls "\">" (esc dlabel) "</td>"
           "<td>" (if (seq (:basis fact)) (esc (str/join ", " (map name (:basis fact)))) "<span class=\"muted\">--</span>") "</td>"
           "<td>" (if-let [c (:confidence fact)] (esc c) "<span class=\"muted\">--</span>") "</td>"
           "</tr>"))))

(defn- placement-rows [db]
  (for [r (store/placement-history db)]
    (str "<tr>"
         "<td><code>" (esc (get r "record_id")) "</code></td>"
         "<td><code>" (esc (get r "campaign_id")) "</code></td>"
         "<td>" (esc (get r "jurisdiction")) "</td>"
         "<td>" (esc (get r "kind")) "</td>"
         "</tr>")))

;; ----------------------------- page -----------------------------

(def ^:private css
  "body{font:14px/1.5 -apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;margin:0;padding:2rem;background:#0b0d10;color:#e6e8eb}
  h1{font-size:1.4rem;margin:0 0 .25rem}
  h2{font-size:1.05rem;margin:2rem 0 .5rem;color:#9fb3c8}
  p.lede{color:#9aa4ae;margin:0 0 1.5rem}
  table{border-collapse:collapse;width:100%;margin-bottom:1rem;font-size:.85rem}
  th,td{border:1px solid #2a2f36;padding:.4rem .6rem;text-align:left;vertical-align:top}
  th{background:#161a1f;color:#9fb3c8;font-weight:600}
  tr:nth-child(even) td{background:#101317}
  code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.82em}
  .ok{color:#5fd68a}
  .warn{color:#e8c46b}
  .err{color:#e8756b}
  .critical{color:#ff5c5c;font-weight:700}
  .muted{color:#6b7480}
  footer{margin-top:2rem;color:#6b7480;font-size:.8rem}")

(defn- render [db]
  (str/join
   "\n"
   ["<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\">"
    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
    "<title>advertising operator console (generated)</title>"
    (str "<style>" css "</style></head><body>")
    "<h1>cloud-itonami-isic-7310 -- Advertising Operator Console</h1>"
    (str "<p class=\"lede\">Generated at build time by <code>advertising.render-html</code>, "
         "driving the real <code>advertising.operation</code> StateGraph (intake -&gt; advise -&gt; "
         "govern -&gt; decide -&gt; request-approval -&gt; commit/hold) against "
         "<code>advertising.store/seed-db</code>. No hand-typed numbers.</p>")

    "<h2>Campaign directory (post-scenario)</h2>"
    (str "<table><thead><tr><th>id</th><th>client</th><th>jurisdiction</th>"
         "<th>proposed media spend</th><th>authorized budget</th>"
         "<th>misleading-claim risk</th><th>placement</th><th>latest ledger status</th></tr></thead><tbody>")
    (str/join "\n" (campaign-rows db))
    "</tbody></table>"

    "<h2>Action gate (advertising.phase x advertising.governor/high-stakes)</h2>"
    (str "<table><thead><tr><th>phase</th><th>label</th><th>writes allowed</th>"
         "<th>auto-commit eligible (governor-clean)</th><th>always-escalate (high-stakes)</th></tr></thead><tbody>")
    (str/join "\n" (action-gate-rows))
    "</tbody></table>"

    "<h2>Audit ledger (append-only, advertising.store/ledger)</h2>"
    (str "<table><thead><tr><th>#</th><th>op</th><th>subject</th><th>actor</th>"
         "<th>disposition</th><th>basis / hold rules</th><th>confidence</th></tr></thead><tbody>")
    (str/join "\n" (ledger-rows db))
    "</tbody></table>"

    "<h2>Draft campaign-placement records (advertising.registry, unsigned)</h2>"
    "<table><thead><tr><th>record_id</th><th>campaign_id</th><th>jurisdiction</th><th>kind</th></tr></thead><tbody>"
    (str/join "\n" (placement-rows db))
    "</tbody></table>"

    (str "<footer>Source: <code>src/advertising/render_html.clj</code> via <code>clojure -M:dev:render-html</code>. "
         "Nightly regeneration: <code>.github/workflows/regenerate.yml</code>.</footer>")
    "</body></html>"]))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out)))
