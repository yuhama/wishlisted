(ns wishlistd.client.main
  (:require [crate.core :as crate]
            [fetch.remotes :as remotes])
  (:use [jayq.core :only [$ remove append empty find val delegate data]])
  (:use-macros [crate.macros :only [defpartial]]
               [fetch.macros :only [letrem]]))

; ACCESS VARIABLES FOR IMPORTANT ELEMENTS

(def $create  ($ "#create-wishlist"))
(def $content ($ "#box"))
(def $header  ($ "#title-box"))
(def $body    ($ "body"))

; HTML PARTIALS

(defpartial wish-li [{:keys [id description url]}]
  [:li {:class "wish" :id id}
   [:div.delete]
   [:input {:type "text" :class "description" :value description}]
   [:input {:type "text" :class "url" :value url}]])

(defpartial wish-li-last []
  [:li.wish.last
   [:div.delete]
   [:input {:type "text" :class "description"}]
   [:input {:type "text" :class "url"}]])

(defpartial wish-ul [wishes]
  [:ul.wishes
    (for [wish wishes]
      (wish-li wish))
    (wish-li-last)])

(defpartial wishlist-title [title]
  [:input {:type "text" :class "title" :value title}])

(defpartial arrow []
  [:div#startinfo-container
   [:div#startinfo-text
    [:p "Create your wishlist by clicking the icon above. Add wishes and URLs to your wishlist. Save the unique link in your 
          browser address field to return to and update the wishlist. Share it with your friends so that they can get you all the things
          you wish for!"]]])

(defpartial info-box []
  [:div 
   [:div#info-box
    [:div#info-close]
    [:div#info-text [:p "Wishlisted.org gives you a single place for all your wishlists, no signup required. Simply create a wishlist and add some wishes and optionally URLs. You will be automatically assigned your own unique URL that you can use to return to your wishlist at any time. Anyone else with the link will also be able to view and update it."]
                    [:p "The site was built using a combination of open source technologies by Erik Kronberg and Stina Qvarnström."]]]
   [:div#info-container]])

; GETTING AND SETTING

(defn get-path []
  (.pop (.split js/window.location.pathname "/")))

(defn set-path! [code]
  (when-not (= code (get-path))
    (.pushState js/window.history nil "" code)))

(defn show-wishlist! [{:keys [wish title code] :as wishlist}]
  "Clears the areas used for displaying wishlists, uses given wishlist to 
  generate the html to fill them again and sets the path."
  (empty $content)
  (empty $header)
  (hide-arrow!)
  (append $content (wish-ul wish))
  (append $header  (wishlist-title title))
  (set-path! code))

(defn show-arrow! []
  (append $body (arrow)))

(defn hide-arrow! []
  (remove ($ arrow)))

(defn show-info! []
  (hide-arrow!)
  (append $body (info-box)))

(defn hide-info! []
  (show-arrow!)
  (remove ($ info-box)))

(defn title-from-html []
  "Gets the current page title."
  (val (find $header ".title")))

(defn wish-from-elem [$elem]
  "Extracts a wish object from the given element."
  {:id (.attr $elem "id")
   :description (val (find $elem ".description"))
   :url (val (find $elem ".url"))
   :wishlist_code (get-path)})

(defn update-last! [wish $elem]
  "Adds the id attribute to the formerly last wish and appends a new last wish to list."
  (.attr $elem "id" (:id wish))
  (.removeClass $elem "last")
  (append ($ wish-ul) (wish-li-last)))

(defn value-changed?! [$this]
  "Keeps track of changes. Returns true if the key :old-val in the JQuery data object on the element
  has changed and updates :old-val. Otherwise returns false."
  (if (= (data $this :old-val) (val $this))
    false
    (do
      (data $this :old-val (val $this))
      true)))

; REMOTES

(defn create-wishlist-rem []
  (letrem [neu (create-wishlist {:title "Name your wishlist!"})]
    (show-wishlist! neu)))

(defn read-wishlist-rem [code]
  (letrem [neu (read-wishlist code)]
    (when-not (nil? neu) 
      (show-wishlist! neu))))

(defn update-wishlist-title-rem []
  (let [wishlist {:code (get-path) :title (title-from-html)}]
    (letrem [neu (update-wishlist wishlist)]
      (fn [e] nil))))

(defn create-wish-rem [$elem]
  (let [wish (wish-from-elem $elem)
        clean (dissoc wish :id)]
    (letrem [neu (create-wish clean)]
      (when (.hasClass $elem "last") 
        (update-last! neu $elem)))))

(defn update-wish-rem [$elem]
  (let [wish (wish-from-elem $elem)]
    (letrem [neu (update-wish wish)]
      (fn [e] nil))))

(defn delete-wish-rem [$elem]
  (let [id (.attr $elem "id")]
    (letrem [neu (delete-wish id)]
      (fn [e] nil))))

; DELEGATES

(delegate $body "#create-wishlist" :click
  (fn [e]
    (.preventDefault e)
    (create-wishlist-rem)))

(delegate $header wishlist-title :change
  (fn [e]
    (this-as me
      (when-not (empty? (val ($ me)))
        (update-wishlist-title-rem)))))

(delegate $content ".description" :change
  (fn [e]
    (this-as me
      (let [$me ($ me)
            parent (.parent $me)]
        (when (value-changed?! $me)
          (if (empty? (val $me))
            (when-not (.hasClass parent "last")
              (remove parent)
              (delete-wish-rem parent))
            (if (.hasClass parent "last")
              (create-wish-rem parent)
              (update-wish-rem parent))))))))

(delegate $content ".url" :change
  (fn [e]
    (this-as me
      (let [$parent (.parent ($ me))]
        (when-not (empty? (.attr $parent "id"))
          (update-wish-rem $parent))))))

(delegate $content ".delete" :click
  (fn [e]
    (this-as me
      (let [$parent (.parent ($ me))]
        (when-not (.hasClass $parent "last")
          (remove $parent)
          (delete-wish-rem $parent))))))

(delegate $body "#icon-about" :click
  (fn [e]
    (when (empty? ($ info-box))
      (show-info!))))

(delegate $body "#info-close" :click
  (fn [e]
    (hide-info!)))

(delegate $body "#info-container" :click
  (fn [e]
    (hide-info!)))

(delegate $body "#icon-refresh" :click
  (fn [e]
    (let [code (get-path)]
      (when-not (empty? code)
        (read-wishlist-rem code)))))

; START IT UP

(defn start-up []
  (let [code (get-path)]
    (if-not (empty? code)
      (read-wishlist-rem code)
      (show-arrow!))))

(set! js/window.onpopstate
  (fn [e]
    (start-up)))

