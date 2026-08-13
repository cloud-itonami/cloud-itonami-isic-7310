(ns advertising.buy
  "The operator's entry point for a REAL media buy: the one command that
  can charge a client, and the only place in this repository that opens
  a socket.

  Everything it does before that is refusal. It reads credentials from
  the environment (never from source, never from a file in this repo),
  refuses to run at all if the set is incomplete, defaults to creating
  the campaign PAUSED even when live, and requires two more explicit
  flags before a campaign can be created in a state that spends money.

  ## Usage

      # 1. dry run -- builds both requests, opens no socket, needs nothing
      clojure -M:dev:buy --campaign campaign-10

      # 2. live, PAUSED -- real account, real campaign object, zero charge
      export GOOGLE_ADS_DEVELOPER_TOKEN=... GOOGLE_ADS_ACCESS_TOKEN=...
      export GOOGLE_ADS_CUSTOMER_ID=... GOOGLE_ADS_LOGIN_CUSTOMER_ID=...
      clojure -M:dev:buy --campaign campaign-10 --live

      # 3. live and SPENDING -- the campaign is created ENABLED
      clojure -M:dev:buy --campaign campaign-10 --live --spend --max-spend-jpy 50000

  Step 2 is not optional politeness. Run it first against any new
  account: it exercises the credentials, the two-step budget/campaign
  creation and the governor path end to end, and the only thing it does
  not do is charge. If step 2 fails, step 3 would have failed after
  taking the client's money.

  ## What this does NOT do

  It does not create the Google Ads account, apply for the developer
  token, complete OAuth, or attach a payment method. Those need a human
  with the company's identity and payment details, and no agent may
  stand in for that."
  (:require [clojure.string :as str]
            [advertising.operation :as op]
            [advertising.placer :as placer]
            [advertising.store :as store])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]))

(defn- json-str [x]
  ;; A dependency-free encoder for the small, closed shape the Google Ads
  ;; mutate bodies actually are (maps, vectors, strings, numbers, bools).
  ;; Anything outside that shape is a bug in the request builder, and it
  ;; throws here rather than being encoded into something the API will
  ;; misread.
  (cond
    (map? x) (str "{" (str/join "," (map (fn [[k v]] (str (json-str (name k)) ":" (json-str v))) x)) "}")
    (sequential? x) (str "[" (str/join "," (map json-str x)) "]")
    (string? x) (str \" (-> x (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \")
    (number? x) (str x)
    (boolean? x) (str x)
    (nil? x) "null"
    :else (throw (ex-info "unencodable value in a buy request" {:value x :type (type x)}))))

(defn http-fn
  "The real socket. Kept here, in the operator entry point, and not in
  `advertising.placer` -- the placer stays pure and portable, and the
  only file that can reach a network is the one a human runs on
  purpose."
  [{:keys [method url headers body]}]
  (let [req (-> (HttpRequest/newBuilder (URI/create url))
                (as-> b (reduce (fn [acc [k v]] (.header acc k v)) b headers))
                (.method (str/upper-case (name method))
                         (HttpRequest$BodyPublishers/ofString (json-str body)))
                (.build))
        resp (.send (HttpClient/newHttpClient) req (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode resp) :raw-body (.body resp)}))

(defn- arg-value [args flag]
  (second (drop-while #(not= flag %) args)))

(defn plan
  "Decide what this invocation would do, WITHOUT doing it. Pure, so the
  refusals are testable without a network or a credential.

  -> {:action :dry-run|:live|:refuse, ...}"
  [args getenv]
  (let [campaign-id (arg-value args "--campaign")
        live? (boolean (some #{"--live"} args))
        spend? (boolean (some #{"--spend"} args))
        jpy (some-> (arg-value args "--max-spend-jpy") (Long/parseLong))]
    (cond
      (str/blank? campaign-id)
      {:action :refuse :reason "--campaign <id> is required"}

      (and spend? (not live?))
      {:action :refuse
       :reason "--spend without --live is meaningless and is refused rather than silently downgraded"}

      (and spend? (not (and jpy (pos? jpy))))
      {:action :refuse
       :reason (str "--spend requires --max-spend-jpy <positive amount>: the campaign's own "
                    "authorized budget is the governor's number, and this is the operator's "
                    "own ceiling, checked again at the network boundary")}

      (not live?)
      {:action :dry-run :campaign-id campaign-id}

      :else
      (let [c (placer/credentials-from-env getenv)]
        (if-let [missing (:missing c)]
          {:action :refuse
           :reason (str "--live needs a complete credential set; missing: "
                        (str/join ", " missing)
                        ". These come from a Google Ads account and OAuth client that a human "
                        "must create; nothing in this repository can stand in for them.")}
          {:action :live :campaign-id campaign-id :spend? spend?
           :max-spend-micros (when jpy (* 1000000 jpy))
           :credentials (:ok c)})))))

(defn -main [& args]
  (let [p (plan args #(System/getenv %))]
    (case (:action p)
      :refuse (do (println "REFUSED:" (:reason p))
                  (System/exit 2))
      (let [db (store/seed-db)
            placer (if (= :live (:action p))
                     (placer/live-placer (cond-> {:http-fn http-fn
                                                  :credentials (:credentials p)}
                                           (:spend? p) (assoc :spend? true
                                                              :max-spend-micros (:max-spend-micros p))))
                     (placer/dry-run-placer))
            actor (op/build db {:placer placer})
            ctx {:actor-id "operator" :actor-role :agency-operator :phase 3}
            run (fn [tid req]
                  (require 'langgraph.graph)
                  ((resolve 'langgraph.graph/run*) actor {:request req :context ctx} {:thread-id tid}))
            approve (fn [tid]
                      ((resolve 'langgraph.graph/run*) actor {:approval {:status :approved :by "operator"}}
                       {:thread-id tid :resume? true}))
            cid (:campaign-id p)]
        (doseq [[tid o] [["b1" :media-plan/verify] ["b2" :platform/verify] ["b3" :risk/screen]
                         ["b4" :actuation/place-campaign]]]
          (run tid {:op o :subject cid})
          (approve tid))
        (println "== ledger ==")
        (doseq [f (store/ledger db)] (println (pr-str f)))
        (let [receipt (last (filter #(= :placement-dispatch (:t %)) (store/ledger db)))]
          (println "== receipt ==")
          (println (pr-str receipt))
          (when-not receipt
            (println "NO RECEIPT -- the placement did not commit (see the ledger above for the hold)")
            (System/exit 1)))))))
