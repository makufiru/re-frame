(ns re-frame.interceptor-test
  (:require [cljs.test :refer-macros [is deftest]]
            [reagent.ratom  :refer [atom]]
            [re-frame.interceptor :refer [context get-coeffect assoc-effect assoc-coeffect get-effect
                                          trim-v path on-changes
                                          db-handler->interceptor fx-handler->interceptor]]))

(enable-console-print!)

(deftest test-trim-v
  (let [c  (-> (context [:a :b :c] [])
               ((:before trim-v)))]
    (is (= (get-coeffect c :event)
           [:b :c]))))


(deftest test-one-level-path
    (let [db   {:showing true :another 1}
          p1   (path [:showing])]   ;; a simple one level path

      (let [b4 (-> (context [] [] db)
                  ((:before p1)))         ;; before
            a (-> b4
                  (assoc-effect :db false)
                  ((:after p1)))]         ;; after

        (is (= (get-coeffect b4 :db)      ;; test before
               true))
        (is (= (get-effect a :db)         ;; test after
               {:showing false :another 1})))))


(deftest test-two-level-path
  (let [db  {:1 {:2 :target}}
        p  (path [:1 :2])]    ;; a two level path

    (let [b4 (-> (context [] [] db)
                ((:before p)))           ;; before
          a (-> b4
                (assoc-effect :db :4)
                ((:after p)))]           ;; after

      (is (= (get-coeffect b4 :db))      ;; test before
             :target)
      (is (= (get-effect a :db)          ;; test after
             {:1 {:2 :4}})))))


(deftest test-db-handler-interceptor
  (let [event   [:a :b]

        handler (fn [db v]
                  ;; make sure it was given the right arguements
                  (is (= db :original-db-val))
                  (is (= v event))
                  ;; return a specific value for later checking
                  :new-db-val)

        i1      (db-handler->interceptor handler)
        db      (-> (context event [] :original-db-val)
                    ((:before i1))            ;; calls handler - causing :db in :effects to change
                    (get-effect :db))]
    (is (= db :new-db-val))))



(deftest test-fx-handler-interceptor
  (let [event   [:a :b]
        coeffect {:db 4 :event event}
        effect   {:db 5 :dispatch [:a]}

        handler (fn [world v]
                  ;; make sure it was given the right arguements
                  (is (= world coeffect))
                  (is (= v event))

                  ;; return a specific value for later checking
                  effect)

        i1      (fx-handler->interceptor handler)
        e       (-> (context event [] (:db coeffect))
                    ((:before i1))            ;; call the handler
                    (get-effect))]
    (is (= e {:db 5 :dispatch [:a]}))))



(deftest test-on-changes
  (let [change-handler-i  (->  (fn [db v] (assoc db :a 10))
                               db-handler->interceptor)

        no-change-handler-i  (->  (fn [db v] db)
                               db-handler->interceptor)

        change-i   (on-changes + [:c] [:a] [:b])
        orig-db    {:a 0 :b 2}]

    (is (=  {:a 0 :b 2}
            (-> (context [] [] orig-db)
                ((:before no-change-handler-i))   ;; no change to :a and :b
                ((:after change-i))
                (get-effect :db))))
    (is (=  {:a 10 :b 2 :c 12}
            (-> (context [] [] orig-db)
                ((:before change-handler-i))       ;; cause change to :a
                ((:after change-i))
                (get-effect :db))))))



