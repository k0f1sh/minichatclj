(ns main.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [reagent.core :as r]))

(enable-console-print!)

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; state
(def messages (r/atom []))

(def current-input (r/atom ""))

(def usernames (r/atom []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ws


(defmulti -event-msg-handler
  :id)

(defn event-msg-handler
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default
  [{:as ev-msg :keys [event]}]
  )

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (chsk-send! [:room/join {}])))

(declare recv-msg-handler)
(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (if-let [[id data] ?data]
    (do
      (recv-msg-handler {:id id
                         :data data}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; recv

(defmulti recv-msg-handler
  :id)

(defmethod recv-msg-handler
  :default
  [{:keys [data]}])

(defmethod recv-msg-handler
  :message/added
  [{:keys [data]}]
  (swap! messages conj data))

(defmethod recv-msg-handler
  :room/new-user-map
  [{:keys [data]}]
  (reset! usernames (:usernames data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; handler
(defn change-message-handler [message]
  (reset! current-input message))

(defn click-message-handler []
  (if-not (empty? @current-input)
    (do (chsk-send! [:message/add {:message @current-input}])
        (reset! current-input ""))))

(defn keypress-message-handler [e]
  (if (= 13 (-> e .-charCode))
    (click-message-handler)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; component
(defn input-box []
  [:div {:class "input-group"}
   [:input {:type "text"
            :class "form-control"
            :on-change #(change-message-handler (-> % .-target .-value))
            :on-key-press keypress-message-handler
            :value @current-input}]
   [:span {:class "input-group-btn"}
    [:button {:class "btn btn-primary"
              :type "button"
              :on-click click-message-handler}
     "送信"]]])

(defn admin-message-item [message]
  [:li {:class "list-group-item"
        :key (:id message)}
   [:span {:class "message-content admin-message"}
    (:message message)]])

(defn message-item [message]
  (if (= :admin (:type message))
    (admin-message-item message)
    [:li {:class "list-group-item"
          :key (:id message)}
     [:span {:class "message-name"}
      (str (:username message) " : ")]
     [:span {:class "message-content"}
      (:message message)]]))

(defn message-box []
  (let [this (r/current-component)]
    (r/create-class
     {:display-name "message-box"
      :component-did-update (fn [_ _]
                              (let [el (.getElementById js/document "message-box")]
                                (set! (.-scrollTop el) (.-scrollHeight el))))
      :reagent-render (fn []
                        [:div {:id "message-box"
                               :class "flex-container-box"}
                         [:div {:class "flex-containier"}
                          [:ul {:class "list-group"}
                           (for [message @messages]
                             (message-item message))]]])})))

(defn username-box []
  [:div
   [:div {:class "userlisthead"} "ユーザ一覧"]
   [:ul {:class "list-group"}
    (for [name @usernames]
      [:li {:class "list-group-item"
            :key name}
       name])]])

(defn main-view []
  [:div
   [:div {:class "col-lg-10 col-md-8 col-sm-8"}
    [message-box]
    (input-box)]
   [:div {:class "col-lg-2 col-md-4 col-sm-4"}
    (username-box)]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; start
(r/render [main-view] (js/document.getElementById "app"))
(sente/start-client-chsk-router! ch-chsk event-msg-handler)
