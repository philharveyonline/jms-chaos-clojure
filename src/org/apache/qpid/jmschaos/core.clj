(ns org.apache.qpid.jmschaos.core
  (:import 
    (org.apache.qpid.server Broker BrokerOptions)
    (org.apache.qpid.client AMQConnectionFactory)
    (javax.jms Connection ConnectionFactory Session Message))
  (:require [clj-http.client :as client])
  (:use [clojure.contrib.java-utils]))

(def queue-name "chaos-queue")

(def queue-url (format "http://localhost:8080/rest/queue/default/%s" queue-name))
(def binding-url (format "http://localhost:8080/rest/binding/default/amq.direct/%1$s/%1$s" queue-name))

(defn create-queue []
  (println "About to create queue")
  (client/put queue-url {:body "{\"durable\":true}"} )
  (client/put binding-url {:body "{}"} ))

(defn delete-queue []
  (println "About to delete queue")
  (client/delete binding-url)
  (client/delete queue-url)
  (println "Deleted queue"))
  
(defn -main [& args]
  (println "Starting...")
  (delete-file-recursively (get-system-property "QPID_WORK") true)
  
  (let [options (new BrokerOptions)
        broker (new Broker)]
    (try
      (.setInitialConfigurationLocation options (.toExternalForm (clojure.java.io/resource "config.json")))
      (.startup broker options)
      
      ; TODO how to set up auto-closeable resources?
      
      (let [connection-factory (new AMQConnectionFactory "amqp://guest:guest@clientid/?brokerlist='tcp://localhost:5672'")
            connection (.createConnection connection-factory "guest" "guest")
            session (.createSession connection false Session/AUTO_ACKNOWLEDGE)
            queue (.createQueue session queue-name)]
        (try
          (create-queue)
          (def producer-future (future
                                 (let [producer (.createProducer session queue)
                                       message (.createTextMessage session "hello world")]
                                   (.send producer message)
                                   (println "Message sent inside future")
                                   message)))
          (def consumer-future (future
                                 (let [consumer (.createConsumer session queue)]
                                   (.start connection)
                                   (.receive consumer 1000))))
          (println "Producer produced: " (.getJMSMessageID @producer-future))
          (println "Consumer received: " (.getJMSMessageID @consumer-future))
          (finally (delete-queue))))
      (finally
        (println "About to shut down broker")
        (.shutdown broker)))))
