(ns advertising.buy-test
  "The refusals in the one command that can spend money, tested without a
  network and without a credential -- which is the point: every one of
  them is a decision made BEFORE anything could be opened or charged."
  (:require [clojure.test :refer [deftest is testing]]
            [advertising.buy :as buy]))

(def ^:private full-env
  {"GOOGLE_ADS_DEVELOPER_TOKEN" "d" "GOOGLE_ADS_ACCESS_TOKEN" "a"
   "GOOGLE_ADS_CUSTOMER_ID" "c" "GOOGLE_ADS_LOGIN_CUSTOMER_ID" "l"})

(deftest the-default-invocation-is-a-dry-run
  (is (= :dry-run (:action (buy/plan ["--campaign" "campaign-10"] full-env)))
      "no --live, no socket, whatever the environment happens to hold"))

(deftest spending-flags-are-refused-rather-than-silently-downgraded
  (testing "--spend without --live"
    (let [p (buy/plan ["--campaign" "campaign-10" "--spend"] full-env)]
      (is (= :refuse (:action p)))
      (is (re-find #"silently downgraded" (:reason p)))))
  (testing "--spend without a ceiling"
    (let [p (buy/plan ["--campaign" "campaign-10" "--live" "--spend"] full-env)]
      (is (= :refuse (:action p)))
      (is (re-find #"max-spend-jpy" (:reason p)))))
  (testing "a zero or negative ceiling is not a ceiling"
    (is (= :refuse (:action (buy/plan ["--campaign" "c" "--live" "--spend" "--max-spend-jpy" "0"] full-env))))))

(deftest a-missing-credential-names-itself-and-stops
  (testing "--live with an incomplete environment refuses, and says which names are missing rather than failing at the API"
    (let [p (buy/plan ["--campaign" "campaign-10" "--live"]
                      (dissoc full-env "GOOGLE_ADS_CUSTOMER_ID"))]
      (is (= :refuse (:action p)))
      (is (re-find #"GOOGLE_ADS_CUSTOMER_ID" (:reason p)))
      (is (re-find #"a human must create" (:reason p))
          "and says who has to get it, because no agent can")))
  (testing "an empty environment refuses for the same reason, not a different one"
    (is (= :refuse (:action (buy/plan ["--campaign" "c" "--live"] {}))))))

(deftest a-complete-live-invocation-plans-the-real-thing
  (let [p (buy/plan ["--campaign" "campaign-10" "--live"] full-env)]
    (is (= :live (:action p)))
    (is (false? (:spend? p)) "live alone never spends -- the campaign is created PAUSED"))
  (let [p (buy/plan ["--campaign" "campaign-10" "--live" "--spend" "--max-spend-jpy" "50000"] full-env)]
    (is (= :live (:action p)))
    (is (true? (:spend? p)))
    (is (= 50000000000 (:max-spend-micros p)) "JPY on the command line, micros at the boundary")))

(deftest a-campaign-id-is-required
  (is (= :refuse (:action (buy/plan ["--live"] full-env)))))
