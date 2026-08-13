(ns advertising.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (advertising.operation -> advertising.governor
  -> advertising.store) through the SAME multi-disposition scenario as
  `advertising.sim` (real seed ids/ops -- see that ns for the narrative).
  No invented numbers, no timestamps, byte-identical across reruns."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [advertising.store :as store]
            [advertising.operation :as op]
            [advertising.phase :as phase]
            [advertising.facts :as facts]
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
    - BOTH always-escalate high-stakes ops (:actuation/place-campaign,
      :actuation/order-creator-tieup), each approved by a human
    - EVERY rule the Campaign Governor can raise, each reaching a hold
      without a human ever being asked. Jurisdiction-side:
      :no-spec-basis, :evidence-incomplete,
      :media-spend-exceeds-authorized-budget,
      :misleading-claim-risk-unresolved, :risk-screen-missing,
      :already-placed. Media-platform-side (ADR-0002/0003):
      :no-platform-policy-basis, :platform-check-incomplete,
      :platform-prohibited-category,
      :platform-restricted-category-unapproved,
      :platform-attestation-missing, :sensitive-placement-context.
      Creator-tie-up-side (ADR-0004/0005): :creator-ineligible,
      :creator-screen-missing, :tieup-evidence-incomplete,
      :creator-tieup-fee-exceeds-authorized-budget,
      :sponsorship-disclosure-missing (twice, from its two distinct
      causes), :already-ordered.

      A rule with no case here is a rule the operator console never
      shows and a reader never sees fire, which is why
      `advertising.drivers-test` asserts the full set rather than a
      sample of it."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    ;; campaign-1 (JPN): clean intake auto-commits at phase 3.
    (exec! actor "t1" {:op :campaign/intake :subject "campaign-1"
                       :patch {:id "campaign-1" :client-name "Sato Bakery"}})

    ;; campaign-1: media-plan verification -- escalates (phase-approval), approved.
    ;; campaign-1: placing before ANY assessment -> HARD hold. Several rules
    ;; are true of it at once (:evidence-incomplete, :platform-check-incomplete,
    ;; :risk-screen-missing) and the ledger says so -- a campaign with nothing
    ;; done to it has more than one thing wrong, and flattening that to a
    ;; single rule to tidy the demo would misreport the ledger.
    (exec! actor "t1b" {:op :actuation/place-campaign :subject "campaign-1"})

    (exec! actor "t2" {:op :media-plan/verify :subject "campaign-1"})
    (approve! actor "t2")

    ;; campaign-1: media-platform ad-policy conformance (chatgpt-ads,
    ;; :local-services permitted) -- escalates, approved.
    (exec! actor "t2b" {:op :platform/verify :subject "campaign-1"})
    (approve! actor "t2b")

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

    ;; campaign-3 (JPN): both verifications escalate, approved -- sets up
    ;; the budget-exceeded HARD hold below.
    (exec! actor "t6" {:op :media-plan/verify :subject "campaign-3"})
    (approve! actor "t6")
    (exec! actor "t6b" {:op :platform/verify :subject "campaign-3"})
    (approve! actor "t6b")

    ;; campaign-3: fully evidenced but never screened -> HARD hold,
    ;; :risk-screen-missing (ADR-0005). "We never looked" is not a clean state.
    (exec! actor "t6b" {:op :actuation/place-campaign :subject "campaign-3"})

    ;; campaign-3: screening runs clean, approved -- so the hold below is the
    ;; budget ceiling and nothing else.
    (exec! actor "t6c" {:op :risk/screen :subject "campaign-3"})
    (approve! actor "t6c")

    ;; campaign-3: proposed media spend 900000 > authorized budget 800000 ->
    ;; HARD hold, :media-spend-exceeds-authorized-budget -- never reaches a human.
    (exec! actor "t7" {:op :actuation/place-campaign :subject "campaign-3"})

    ;; campaign-4 (misleading-claim-risk-unresolved? true): HARD hold,
    ;; :misleading-claim-risk-unresolved -- never reaches a human.
    (exec! actor "t8" {:op :risk/screen :subject "campaign-4"})

    ;; campaign-1 AGAIN: already placed in t4 -> HARD hold, :already-placed --
    ;; never reaches a human.
    (exec! actor "t9" {:op :actuation/place-campaign :subject "campaign-1"})

    ;; ---- creator tie-up (YouTube / influencer) lifecycle, ADR-0002 ----

    ;; campaign-21 (JPN, @sato-bakery-review on youtube): creator screening and
    ;; tie-up brief both escalate, both approved.
    (exec! actor "u1" {:op :creator/screen :subject "campaign-21"})
    (approve! actor "u1")
    ;; campaign-21: creator screened and eligible, but no tie-up evidence
    ;; brief yet -> HARD hold, :tieup-evidence-incomplete on its own.
    (exec! actor "u1b" {:op :actuation/order-creator-tieup :subject "campaign-21"})

    (exec! actor "u2" {:op :tieup/verify :subject "campaign-21"})
    (approve! actor "u2")

    ;; campaign-5: tie-up order -- ALWAYS escalates (the second member of
    ;; advertising.governor/high-stakes), approved.
    (exec! actor "u3" {:op :actuation/order-creator-tieup :subject "campaign-21"})
    (approve! actor "u3")

    ;; campaign-6: briefed but the creator was never screened -> HARD hold,
    ;; :creator-screen-missing (ADR-0005, the tie-up half).
    (exec! actor "u4" {:op :tieup/verify :subject "campaign-22"})
    (approve! actor "u4")
    (exec! actor "u4b" {:op :actuation/order-creator-tieup :subject "campaign-22"})

    ;; campaign-6: creator screens clean, approved -- so the hold below is the
    ;; combined-spend ceiling and nothing else: media 500000 + fee 400000 >
    ;; authorized 800000 -> :creator-tieup-fee-exceeds-authorized-budget.
    (exec! actor "u4c" {:op :creator/screen :subject "campaign-22"})
    (approve! actor "u4c")
    (exec! actor "u5" {:op :actuation/order-creator-tieup :subject "campaign-22"})

    ;; campaign-23 (creator-eligibility-issue? true): HARD hold,
    ;; :creator-ineligible -- never reaches a human.
    (exec! actor "u6" {:op :creator/screen :subject "campaign-23"})

    ;; campaign-8: fully briefed and screened, but no disclosure label recorded
    ;; at all -> HARD hold, :sponsorship-disclosure-missing.
    (exec! actor "u7" {:op :tieup/verify :subject "campaign-24"})
    (approve! actor "u7")
    (exec! actor "u7b" {:op :creator/screen :subject "campaign-24"})
    (approve! actor "u7b")
    (exec! actor "u8" {:op :actuation/order-creator-tieup :subject "campaign-24"})

    ;; campaign-9: 「タイアップ」 IS recorded, but is not among the 消費者庁's
    ;; own published examples -> the SAME HARD hold, different cause.
    (exec! actor "u9" {:op :tieup/verify :subject "campaign-25"})
    (approve! actor "u9")
    (exec! actor "u9b" {:op :creator/screen :subject "campaign-25"})
    (approve! actor "u9b")
    (exec! actor "u10" {:op :actuation/order-creator-tieup :subject "campaign-25"})

    ;; campaign-21 AGAIN: already ordered in u3 -> HARD hold, :already-ordered.
    (exec! actor "u11" {:op :actuation/order-creator-tieup :subject "campaign-21"})
    ;; ---- media-platform HARD holds (ADR-0002). Every campaign below is
    ;; CLEAN on every jurisdiction-side check and is held purely by the
    ;; media platform's own published ad policy.

    ;; campaign-5: platform "acme-adnet" has no transcribed policy ->
    ;; HARD hold, :no-platform-policy-basis.
    (exec! actor "t10" {:op :platform/verify :subject "campaign-5"})

    ;; campaign-6: :gambling is prohibited by the platform's own policy ->
    ;; HARD hold, :platform-prohibited-category.
    (exec! actor "t11" {:op :platform/verify :subject "campaign-6"})

    ;; campaign-7: :financial-services is restricted -- unapproved advertiser,
    ;; and JPN is outside the platform's restricted-category jurisdictions ->
    ;; HARD hold, :platform-restricted-category-unapproved.
    (exec! actor "t12" {:op :platform/verify :subject "campaign-7"})

    ;; campaign-8: generative surface, ad/answer distinguishability never
    ;; attested -> HARD hold, :platform-attestation-missing.
    (exec! actor "t13" {:op :platform/verify :subject "campaign-8"})

    ;; campaign-9: placement requested against a context the platform refuses
    ;; to serve ads near -> HARD hold, :sensitive-placement-context.
    (exec! actor "t14" {:op :platform/verify :subject "campaign-9"})

    ;; ---- the four seeded platforms disagree (ADR-0003) ----

    ;; campaign-10: google-ads, :local-services -- same category and
    ;; jurisdiction as campaign-1, different platform, and still clean
    ;; because Google's category set is OPEN. Escalates, approved.
    (exec! actor "t18" {:op :platform/verify :subject "campaign-10"})
    (approve! actor "t18")

    ;; campaign-11: meta-ads in DEU -- the EU DSA beneficiary/payer disclosure
    ;; applies there and was never attested -> HARD hold,
    ;; :platform-attestation-missing. The same facts in JPN would pass.
    (exec! actor "t19" {:op :platform/verify :subject "campaign-11"})

    ;; campaign-12: microsoft-advertising, :travel-experiences -- RESTRICTED
    ;; there while PERMITTED on chatgpt-ads, and the per-category country
    ;; table is untranscribed -> HARD hold,
    ;; :platform-restricted-category-unapproved.
    (exec! actor "t20" {:op :platform/verify :subject "campaign-12"})

    ;; campaign-13: line-yahoo-ads, :lifestyle-household -- a category every
    ;; open-set platform here PERMITS. It holds anyway, and for a different
    ;; reason than any hold above: that operator's enumerated 掲載基準 renders
    ;; via JavaScript and has not been read, so the entry is :policy-read
    ;; :partial and the category resolves :not-transcribed. The detail says
    ;; 'we did not read this', not 'the platform refused it'.
    (exec! actor "t21" {:op :platform/verify :subject "campaign-13"})

    ;; campaign-14: youtube-ads, :gambling -- youtube-ads names no category of
    ;; its own; YouTube's overview says the Google Ads policies apply to it, so
    ;; the restriction AND the untranscribed country table are incorporated
    ;; from google-ads -> HARD hold. Buying YouTube is not 'we support Google'.
    (exec! actor "t22" {:op :platform/verify :subject "campaign-14"})

    ;; campaign-6: jurisdiction evidence cleared, then placement attempted.
    ;; TWO platform-side rules accumulate in one decision --
    ;; :platform-check-incomplete (no conformance assessment on file, because
    ;; t11 above HELD instead of writing one) and :platform-prohibited-category
    ;; -- showing that clearing the jurisdiction side clears nothing here.
    (exec! actor "t15" {:op :media-plan/verify :subject "campaign-6"})
    (approve! actor "t15")
    (exec! actor "t16" {:op :actuation/place-campaign :subject "campaign-6"})

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

(defn- tieup-rows
  "One row per campaign that actually names a creator -- campaigns with
  no `:creator-handle` have no tie-up lifecycle to report, and padding
  the table with them would imply a tie-up was considered and cleared."
  [db]
  (for [c (store/all-campaigns db)
        :when (:creator-handle c)]
    (let [label (:disclosure-label c)
          ok-label? (facts/disclosure-acceptable? (:jurisdiction c) label)]
      (str "<tr>"
           "<td><code>" (esc (:id c)) "</code></td>"
           "<td>" (esc (:client-name c)) "</td>"
           "<td><code>" (esc (:creator-handle c)) "</code></td>"
           "<td>" (esc (some-> (:creator-platform c) name)) "</td>"
           "<td>" (esc (:creator-tieup-fee c)) "</td>"
           "<td>" (esc (+ (or (:proposed-media-spend c) 0) (or (:creator-tieup-fee c) 0)))
           " / " (esc (:authorized-budget c)) "</td>"
           "<td class=\"" (if (:creator-eligibility-issue? c) "err" "muted") "\">"
           (if (:creator-eligibility-issue? c) "ineligible" "no issue") "</td>"
           "<td class=\"" (if ok-label? "ok" "err") "\">"
           (cond
             (nil? label) "none recorded"
             ok-label? (esc label)
             :else (str (esc label) " -- not a published label")) "</td>"
           "<td class=\"" (if (:tieup-ordered? c) "ok" "muted") "\">"
           (if (:tieup-ordered? c) (str "ordered (" (esc (:tieup-order-number c)) ")") "not ordered") "</td>"
           "</tr>"))))

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

(defn- ledger-rows
  "One row per ledger fact. Most facts are op decisions; a
  `:placement-dispatch` receipt is not -- it is the statement of
  whether a committed placement actually reached a media network, and
  it is rendered as its own row rather than folded into the placement
  above it. An auditor scanning this table has to be able to see
  'committed' and 'sent nothing' as two facts, because they are."
  [db]
  (for [[i fact] (map-indexed vector (store/ledger db))]
    (let [receipt? (= :placement-dispatch (:t fact))
          [dcls dlabel] (if receipt?
                          (if (:sent? fact)
                            ["commit" "SENT"]
                            ["hold" (str "NOT SENT (" (name (:mode fact)) ")")])
                          (disposition-cell fact))]
      (str "<tr>"
           "<td>" (inc i) "</td>"
           "<td><code>" (esc (name (or (:op fact) (:t fact)))) "</code></td>"
           "<td><code>" (esc (or (:subject fact) (:campaign-id fact))) "</code></td>"
           "<td>" (esc (or (:actor fact)
                           (when receipt? (or (:platform fact) "--")))) "</td>"
           "<td class=\"" dcls "\">" (esc dlabel) "</td>"
           "<td>" (cond
                    (seq (:basis fact)) (esc (str/join ", " (map name (:basis fact))))
                    receipt? (esc (or (:note fact) (:error fact) ""))
                    :else "<span class=\"muted\">--</span>") "</td>"
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

(defn- tieup-order-rows [db]
  (for [r (store/tieup-order-history db)]
    (str "<tr>"
         "<td><code>" (esc (get r "record_id")) "</code></td>"
         "<td><code>" (esc (get r "campaign_id")) "</code></td>"
         "<td>" (esc (get r "jurisdiction")) "</td>"
         "<td>" (esc (get r "platform")) "</td>"
         "<td><code>" (esc (get r "creator_handle")) "</code></td>"
         "<td>" (esc (get r "kind")) "</td>"
         "</tr>")))

;; ----------------------------- page -----------------------------

(defn- render [db]
  (str/join
   "\n"
   ["<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\">"
    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
    "<title>advertising operator console (generated)</title>"
    (str "<style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>")
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

    "<h2>Creator tie-up (YouTube / influencer) directory (post-scenario)</h2>"
    (str "<p class=\"lede\">Sponsorship-disclosure labels are checked against "
         "<code>advertising.facts/accepted-disclosure-labels</code> -- the wordings the "
         "jurisdiction&#39;s OWN authority publishes. A recorded-but-unpublished label "
         "(e.g. 「タイアップ」 in JPN) is the same HARD hold as none at all.</p>")
    (str "<table><thead><tr><th>id</th><th>client</th><th>creator</th><th>platform</th>"
         "<th>tie-up fee</th><th>combined spend / authorized</th>"
         "<th>creator eligibility</th><th>disclosure label</th><th>tie-up order</th></tr></thead><tbody>")
    (str/join "\n" (tieup-rows db))
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

    "<h2>Draft creator-tie-up order records (advertising.registry, unsigned)</h2>"
    (str "<table><thead><tr><th>record_id</th><th>campaign_id</th><th>jurisdiction</th>"
         "<th>platform</th><th>creator_handle</th><th>kind</th></tr></thead><tbody>")
    (str/join "\n" (tieup-order-rows db))
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
