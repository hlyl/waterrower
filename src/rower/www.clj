(ns rower.www
  (:use
   [compojure.core :only [defroutes GET]]
   [compojure.route :only [resources]]
   [lamina.core :only [receive enqueue channel permanent-channel siphon map*]])
  (:require
   [cheshire.core :as json]
   [rower
    [s4           :as s4]
    [views        :as views]]
   [aleph.http    :as http])
  (:import
   [java.io FileWriter PrintWriter]
   [java.util Date]
   [java.text SimpleDateFormat]))

(defn time-str
  []
  (.format (SimpleDateFormat. "yyyy-MM-dd_H_m") (Date.)))

(defn build-filename
  [{value :value units :units}]
  (str "data/" (time-str) "_" value "_" (name units) ".cap"))

(defn ->workout
  [m]
  (-> m
      (update-in [:units] keyword)
      (update-in [:type] keyword)))

(defmulti handle (fn [_ data _] (:type data)))

(defn on-event
  [ch file event]
  (let [event (json/encode event)]
    (.println file event)
    (.flush file)
    (enqueue ch event)))

(defn ws-handler
  [ch {s4-mon :s4}]
  (receive
   ch
   (fn [s]
     (let [msg (json/decode s true)]
       (s4/clear-handlers s4-mon)
       (let [workout (->workout (:data msg))
             file (-> (build-filename workout) FileWriter. PrintWriter.)]
         (s4/add-handler s4-mon (partial on-event ch file))
         (s4/start-workout s4-mon workout))))))

(defroutes routes
  (GET "/analysis"      []     (views/analysis))
  (GET "/session/:file" [file] (views/session file))
  (GET "/ws"            []     (http/wrap-aleph-handler ws-handler))
  (GET "/"              []     (views/dashboard))
  (resources "/"))

(defn wrap-s4
  [handler s4-mon]
  (fn [req]
    (handler (assoc req :s4 s4-mon))))

(defn start
  [s4-mon]
  (http/start-http-server (-> routes
                              (wrap-s4 s4-mon)
                              http/wrap-ring-handler)
                          {:port 3000 :websocket true}))
