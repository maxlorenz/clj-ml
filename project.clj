(defproject clj-ml "0.0.4-SNAPSHOT"
  :description "Machine Learning library for Clojure built around Weka and friends"
  :java-source-paths ["src/java"]
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [incanter/incanter "1.5.7"]
                 [nz.ac.waikato.cms.weka/weka-stable "3.6.6"]]
  :dev-dependencies [[autodoc "1.0.0"]]
  :autodoc {:name      "clj-ml"
            :page-title "clj-ml machine learning Clojure's style"
            :author    "Antonio Garrote <antoniogarrote@gmail.com>"
            :copyright "2010 (c) Antonio Garrote, under the MIT license"})
