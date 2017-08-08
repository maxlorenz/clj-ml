;;
;; Manipulation of datasets and instances
;; @author Antonio Garrote
;;

(ns #^{:author "Antonio Garrote <antoniogarrote@gmail.com>"}
      clj-ml.data
  "This namespace contains several functions for
   building creating and manipulating data sets and instances. The formats of
   these data sets as well as their classes can be modified and assigned to
   the instances. Finally data sets can be transformed into Clojure sequences
   that can be transformed using usual Clojure functions like map, reduce, etc."
  (:use [clj-ml utils])
  (:import (weka.core Instance Instances FastVector Attribute)
           (cljml ClojureInstances)))

;; Common functions

(defn is-instance? [instance]
  "Checks if the provided object is an instance"
  (if (= weka.core.Instance
         (class instance))
    true
    false))

(defn is-dataset? [dataset]
  "Checks if the provided object is a dataset"
  (not (is-instance? dataset)))

;; Construction of individual data and datasets

(defn attribute-name-at [dataset-or-instance pos]
  "Returns the name of an attribute situated at the provided position in
   the attributes definition of an instance or class"
  (let [class-attr (.attribute dataset-or-instance pos)]
    (.name class-attr)))

(defn index-attr [dataset-or-instance attr]
  "Returns the index of an attribute in the attributes definition of an
   instance or dataset"
  (let [max (.numAttributes dataset-or-instance)
        attrs (key-to-str attr)]
    (loop [c 0]
      (if (= c max)
        (throw (.Exception (str "Attribute " attrs " not found")))
        (if (= attrs (attribute-name-at dataset-or-instance c))
          c
          (recur (+ c 1)))))))

(defn dataset-index-attr [dataset attr]
  (index-attr dataset attr))

(defn instance-index-attr [instance attr]
  (index-attr instance attr))

(defn make-instance
  "Creates a new dataset instance from a vector"
  ([dataset vector]
   (make-instance dataset 1 vector))
  ([dataset weight vector]
   (let [inst (new Instance
                   (count vector))]
     (do (.setDataset inst dataset)
         (loop [vs vector
                c 0]
           (if (empty? vs)
             (do
               (.setWeight inst (double weight))
               inst)
             (do
               (if (or (keyword? (first vs)) (string? (first vs)))
                   ;; this is a nominal entry in keyword or string form
                 (.setValue inst c (key-to-str (first vs)))
                 (if (sequential? (first vs))
                     ;; this is a map of values
                   (let [k (key-to-str (nth (first vs) 0))
                         val (nth (first vs) 1)
                         ik  (index-attr inst k)]
                     (if (or (keyword? val) (string? val))
                         ;; this is a nominal entry in keyword or string form
                       (.setValue inst ik (key-to-str val))
                       (.setValue inst ik (double val))))
                     ;; A double value for the entry
                   (.setValue inst c (double (first vs)))))
               (recur (rest vs)
                      (+ c 1)))))))))

(defn- parse-attributes
  "Builds a set of attributes for a dataset parsed from the given array"
  ([attributes]
   (loop [atts attributes
          fv (new FastVector (count attributes))]
     (if (empty? atts)
       fv
       (do
         (let [att (first atts)]
           (.addElement fv
                        (if (map? att)
                          (if (sequential? (first (vals att)))
                            (let [v (first (vals att))
                                  vfa (reduce (fn [a i] (.addElement a (key-to-str i)) a)
                                              (new FastVector) v)]
                              (new Attribute (key-to-str (first (keys att))) vfa))
                            (new Attribute (key-to-str (first (keys att))) (first (vals att))))
                          (new Attribute (key-to-str att)))))
         (recur (rest atts)
                fv))))))

(defn make-dataset
  "Creates a new dataset, empty or with the provided instances and options"
  ([name attributes capacity-or-values & opts]
   (let [options         (first-or-default opts {})
         weight          (get options :weight 1)
         class-attribute (get options :class)
         ds              (if (sequential? capacity-or-values)
                           ;; we have received a sequence instead of a number, so we initialize data
                           ;; instances in the dataset
                           (let [dataset (new ClojureInstances (key-to-str name) (parse-attributes attributes) (count capacity-or-values))]
                             (loop [vs capacity-or-values]
                               (if (empty? vs)
                                 dataset
                                 (do
                                   (let [inst (make-instance dataset weight (first vs))]
                                     (.add dataset inst))
                                   (recur (rest vs))))))
                           ;; we haven't received a vector so we create an empty dataset
                           (new Instances (key-to-str name) (parse-attributes attributes) capacity-or-values))]
       ;; we try to setup the class attribute if :class with a attribute name or
       ;; integer value is provided
     (when (not (nil? class-attribute))
       (let [index-class-attribute (if (keyword? class-attribute)
                                     (loop [c    0
                                            acum attributes]
                                       (if (= (let [at (first acum)]
                                                (if (map? at)
                                                  (first (keys at))
                                                  at))
                                              class-attribute)
                                         c
                                         (if (= c (count attributes))
                                           (throw (new Exception "provided class attribute not found"))
                                           (recur (+ c 1)
                                                  (rest acum)))))
                                     class-attribute)]
         (.setClassIndex ds index-class-attribute)))
     ds)))

;; dataset information

(defn dataset-name [dataset]
  "Returns the name of this dataset"
  (.relationName dataset))

(defn dataset-class-values [dataset]
  "Returns the possible values for the class attribute"
  (let [class-attr (.classAttribute dataset)
        values (.enumerateValues class-attr)]
    (loop [continue (.hasMoreElements values)
           acum {}]
      (if continue
        (let [val (.nextElement values)
              index (.indexOfValue class-attr val)]
          (recur (.hasMoreElements values)
                 (conj acum {(keyword val) index})))
        acum))))

(defn dataset-values-at [dataset-or-instance pos]
  "Returns the possible values for a nominal attribute at the provided position"
  (let [class-attr (.attribute dataset-or-instance pos)
        values (.enumerateValues class-attr)]
    (if (nil? values)
      :not-nominal
      (loop [continue (.hasMoreElements values)
             acum {}]
        (if continue
          (let [val (.nextElement values)
                index (.indexOfValue class-attr val)]
            (recur (.hasMoreElements values)
                   (conj acum {(keyword val) index})))
          acum)))))

(defn dataset-format [dataset]
  "Returns the definition of the attributes of this dataset"
  (let [max (.numAttributes dataset)]
    (loop [acum []
           c 0]
      (if (< c max)
        (let [attr (.attribute dataset c)
              index c
              name (keyword (.name attr))
              nominal? (.isNominal attr)
              to-add (if nominal?
                       (let [vals (dataset-values-at dataset index)]
                         {name (keys vals)})
                       name)]
          (recur (conj acum to-add)
                 (+ c 1)))
        acum))))

(defn dataset-get-class [dataset]
  "Returns the index of the class attribute for this dataset"
  (.classIndex dataset))

;; manipulation of instances

(defn instance-set-class [instance pos]
  "Sets the index of the class attribute for this instance"
  (do (.setClassValue instance pos)
      instance))

(defn instance-get-class [instance]
  "Get the index of the class attribute for this instance"
  (.classValue instance))

(defn instance-value-at [instance pos]
  "Returns the value of an instance attribute"
  (let [attr (.attribute instance pos)]
    (if (.isNominal attr)
      (let [val (.value instance pos)
            key-vals (dataset-values-at instance pos)
            key-val (loop [ks (keys key-vals)]
                      (if (= (get key-vals (first ks))
                             val)
                        (first ks)
                        (recur (rest ks))))]
        key-val)
      (.value instance pos))))

(defn instance-to-vector
  "Builds a vector with the values of the instance"
  [instance]
  (let [max (.numValues instance)]
    (loop [c 0
           acum []]
      (if (= c max)
        acum
        (recur (+ c 1)
               (conj acum (instance-value-at instance c)))))))

(defn instance-to-map
  "Builds a vector with the values of the instance"
  [instance]
  (let [max (.numValues instance)]
    (loop [c 0
           acum {}]
      (if (= c max)
        acum
        (recur (+ c 1)
               (conj acum {(keyword (. (.attribute instance c) name))
                           (instance-value-at instance c)}))))))

;; manipulation of datasets

(defn dataset-seq [dataset]
  "Builds a new clojure sequence from this dataset"
  (if (= (class dataset)
         ClojureInstances)
    (seq dataset)
    (seq (new ClojureInstances dataset))))

(defn dataset-set-class [dataset pos]
  "Sets the index of the attribute of the dataset that is the class of the dataset"
  (do (.setClassIndex dataset pos)
      dataset))

(defn dataset-remove-class [dataset]
  "Removes the class attribute from the dataset"
  (do
    (.setClassIndex dataset -1)
    dataset))

(defn dataset-count [dataset]
  "Returns the number of elements in a dataset"
  (.numInstances dataset))

(defn dataset-add
  "Adds a new instance to a dataset. A clojure vector or an Instance
   can be passed as arguments"
  ([dataset vector]
   (dataset-add dataset 1 vector))
  ([dataset weight vector]
   (do
     (if (= (class vector) weka.core.Instance)
       (.add dataset vector)
       (let [instance (make-instance dataset weight vector)]
         (.add dataset instance)))
     dataset)))

(defn dataset-extract-at
  "Removes and returns the instance at a certain position from the dataset"
  [dataset pos]
  (let [inst (.instance dataset pos)]
    (do
      (.delete dataset pos)
      inst)))

(defn dataset-at
  "Returns the instance at a certain position from the dataset"
  [dataset pos]
  (.instance dataset pos))

(defn dataset-pop
  "Removes and returns the first instance in the dataset"
  [dataset]
  (dataset-extract-at dataset 0))
