(defproject jms-chaos-clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  
  :main org.apache.qpid.jmschaos.core

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1"]
                 [org.apache.qpid/qpid-client "0.22"]
                 [org.apache.qpid/qpid-broker "0.22"]
                 [clj-http "0.7.6"]]

  ; The Broker assumes that QPID_HOME contains etc/log4j.xml
  :jvm-opts ["-DQPID_HOME=./qpid-home" "-DQPID_WORK=./target/work"])  