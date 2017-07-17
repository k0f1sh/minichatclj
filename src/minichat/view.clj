(ns minichat.view
  (:require [hiccup.core :as hc]
            [hiccup.page :as hp]))

(defn base [body]
  (hp/html5
   {:ng-app "minichat"
    :lang "ja"}
   [:head
    [:meta {:charset "utf-8"}]
    [:title "minichat"]
    (hp/include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css")
    (hp/include-css "css/main.css")
    (hp/include-js "https://code.jquery.com/jquery-3.2.1.min.js")
    (hp/include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")]
   [:body body]))

(defn top [req]
  (base [:div {:class "jumbotron"}
         [:h1 "Mini Chat"]
         [:p "シンプルなチャットです。"]
         [:div {:class "col-lg-3"}
          [:form {:action "/join"
                  :method "POST"}
           [:div {:class "input-group"}
            [:input {:class "form-control"
                     :type "text"
                     :name "username"
                     :placeholder "あなたの名前を入力..."}]
            [:span {:class "input-group-btn"}
             [:button {:class "btn btn-primary" :type "submit"} "参加!"]]]]]]))

(defn join []
  (base [:h1 "join"]))

(defn chat [name]
  (base [:div {:class "container"}
         [:div {:class "row"}
          [:div {:class "alert alert-info"}
           [:div (str "[" name "でログイン中]")]]
          [:div {:id "app"}]
          (hp/include-js "js/main.js")
          [:script {:src ""} "goog.require(\"main.core\")"]]]))


