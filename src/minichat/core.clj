(ns minichat.core
  (:gen-class main true)
  (:require
   [compojure.core :refer :all]
   [compojure.route :as route]
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
   [org.httpkit.server :as httpkit]
   [ring.middleware.params :refer (wrap-params)]
   [ring.middleware.keyword-params :refer (wrap-keyword-params)]
   [ring.middleware.session :refer (wrap-session)]
   [ring.util.response :as response]
   [clojure.core.async :as async]
   [minichat.view :as view]
   [minichat.message :as message]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; init
(let [{:keys [ch-recv
              send-fn
              connected-uids
              ajax-post-fn
              ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter))]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; state
(def uid-count (atom 0))
(def user-map (atom {}))
(def messages (atom []))
(def message-id-count (atom 0))

;;debug
;; (add-watch user-map :watcher
;;            (fn [key atom old-state new-state]
;;              (prn new-state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; reoute

(defn top [req]
  (response/response (view/top req)))

(defn error [req]
  (response/response "Error..."))

(defn login? [session]
  (boolean (get-in session [:uid] nil)))

(defn join [{:keys [session] :as req}]
  (let [uid (swap! uid-count inc)
        username (get-in req [:form-params "username"] (str "user-" uid))
        session (assoc session :uid uid)]
    (swap! user-map assoc uid username)
    (-> (response/redirect "/chat")
        (assoc :session session))))

(defn chat [{:keys [session] :as req}]
  (if-not (login? session)
    (response/redirect "/top")
    (let [uid (:uid session)
          name (get @user-map uid)]
      (prn name)
      (response/response (view/chat name)))))

(defn to-top []
  (response/redirect "/top"))

(defroutes my-app-routes
  (route/resources "/")
  (GET "/" req (to-top))
  (GET "/top" req (top req))
  (GET "/chat" req (chat req))
  (POST "/join" req (join req))
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req)))

(def my-app
  (-> my-app-routes
      wrap-session
      wrap-keyword-params
      wrap-params))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; event handler
(defmulti -event-msg-handler
  :id)

(defmethod -event-msg-handler
  :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (clojure.pprint/pprint id))

(defn event-msg-handler
  [{:as ev-msg :keys [id ?data event ring-req]}]
  (-event-msg-handler ev-msg))

(defn record->map [record]
  (into {} record))

(declare add-message!)
(declare add-admin-message!)
(declare send-new-user-map!)

(defmethod -event-msg-handler
  :message/add
  [{:as ev-msg :keys [event id uid ?data ring-req ?reply-fn send-fn]}]
  (if-let [data ?data]
    (let [message-text (:message data)]
      (do
        (println message-text)
        (let [message (add-message! uid message-text)]
          (doseq [send-uid (:any @connected-uids)]
            (send-fn send-uid [:message/added (record->map message)])))))))

(defmethod -event-msg-handler
  :room/join
  [{:as ev-msg :keys [event id uid ?data ring-req ?reply-fn send-fn]}]
  (do
    (send-new-user-map! send-fn uid)
    (send-fn uid [:message/added (record->map (add-admin-message! "ようこそ！ minichatへ！！"))])
    (doseq [send-uid (filter #(not= % uid) (:any @connected-uids))]
      (let [message (add-admin-message! (str (get @user-map uid) "さんが入室しました。"))]
        (send-new-user-map! send-fn send-uid)
        (send-fn send-uid [:message/added (record->map message)])))))

(defmethod -event-msg-handler
  :chsk/uidport-close
  [{:as ev-msg :keys [event id uid ?data ring-req ?reply-fn send-fn]}]
  (do
    (let [username (get @user-map uid)]
      (swap! user-map dissoc uid)
      (doseq [send-uid (:any @connected-uids)]
        (let [message (add-admin-message! (str username "さんが退室しました。"))]
          (send-new-user-map! send-fn send-uid)
          (send-fn send-uid [:message/added (record->map message)]))))))

(defn send-new-user-map! [send-fn uid]
  (let [usernames (vals @user-map)]
    (send-fn uid [:room/new-user-map {:usernames usernames}])))

(defn add-message! [uid message-text]
  (let [id (swap! message-id-count inc)
        message (message/map->Message {:id id
                                       :type :user
                                       :message message-text
                                       :uid uid
                                       :username (get @user-map uid)})]
    (swap! messages conj message)
    message))

(defn add-admin-message! [message-text]
  (let [id (swap! message-id-count inc)
        message (message/map->Message {:id id
                                       :type :admin
                                       :message message-text
                                       :uid nil
                                       :username nil})]
    (swap! messages conj message)
    message))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; start

(defn -main [& args]
  (println "server start... port 5000")
  (httpkit/run-server my-app {:port 5000})
  (sente/start-server-chsk-router! ch-chsk event-msg-handler))
