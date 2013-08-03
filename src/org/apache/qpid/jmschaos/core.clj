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
  
(defn run-consumer [session queue]
  (future
    (let [consumer (.createConsumer session queue)]
      (.receive consumer 1000))))

(defn produce-message
  [session producer]
  (let [message (.createTextMessage session "message body")]
    (.send producer message)
    message))

(defn do-participant
  "Synchronously participates, collecting messages, until the deadline"
  [participate-fn deadline]
  (loop [messages (list)]
    (let [new-messages (cons (participate-fn) messages)]
      (java.lang.Thread/sleep (rand-int 400))
      (if (> (java.lang.System/currentTimeMillis) deadline)
        new-messages
        (recur new-messages))))) 


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
            connection (.createConnection connection-factory)]
        (try
          (create-queue)
          (.start connection)
          
          (let [deadline (+ (java.lang.System/currentTimeMillis) 3000)]
            (doseq [producer-future (take 3
              (repeatedly
                #(future
                  (let [session (.createSession connection false Session/AUTO_ACKNOWLEDGE)
                        producer (.createProducer session (.createQueue session queue-name))]
                    (do-participant (fn [] (produce-message session producer)) deadline)))))]
              (println @producer-future)))
            
          (finally (delete-queue))))
      
      (finally
        (println "About to shut down broker")
        (.shutdown broker)))))
