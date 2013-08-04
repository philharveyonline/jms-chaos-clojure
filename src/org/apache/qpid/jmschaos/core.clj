(ns org.apache.qpid.jmschaos.core
  (:import 
    (org.apache.qpid.server Broker BrokerOptions)
    (org.apache.qpid.client AMQConnectionFactory)
    (javax.jms Connection ConnectionFactory Session Message))
  (:require [clj-http.client :as client])
  (:use [clojure.contrib.java-utils ]))


(def duration 10000)

(def connection-factory (new AMQConnectionFactory "amqp://guest:guest@clientid/?brokerlist='tcp://localhost:5672'"))
  
(def queue-name "chaos-queue")

(def queue-url (format "http://localhost:8080/rest/queue/default/%s" queue-name))
(def binding-url (format "http://localhost:8080/rest/binding/default/amq.direct/%1$s/%1$s" queue-name))


(defn create-queue []
  (println "About to create queue")
  (client/put queue-url {:body "{\"durable\":true}"} )
  (client/put binding-url {:body "{}"} ))

(defn delete-queue []
  (client/delete binding-url)
  (client/delete queue-url)
  (println "Deleted queue"))

(defn do-for-at-most
  "Synchronously participates until at most duration ms have passed, returning sent/received messages"
  [duration jms-fn]

  (let [real-duration (+ (/ duration 2) (rand-int (/ duration 2)))
        deadline (+ real-duration (System/currentTimeMillis))]
    
    (loop [messages (list)]
      (let [new-messages (cons (jms-fn) messages)]
        (java.lang.Thread/sleep (rand-int 400))
        (if (> (System/currentTimeMillis) deadline)
          new-messages
          (recur new-messages))))))

(defn run-consumer [session queue]
  (future
    (let [consumer (.createConsumer session queue)]
      (.receive consumer 1000))))

(defn produce-message
  [session producer]
  (let [message (.createTextMessage session "message body")]
    (.send producer message)
    (.getJMSMessageID message)))

(defn produce [connection] 
  (Thread/sleep (rand-int (/ duration 10)))
  (with-open [session (.createSession connection false Session/AUTO_ACKNOWLEDGE)]
      (do-for-at-most
        duration 
        (fn [] 
          (with-open [producer (.createProducer session (.createQueue session queue-name))]
            (do-for-at-most
              (/ duration 3)
              (fn [] (produce-message session producer))))))))

(defn -main [& args]
  (println "Starting...")
  (delete-file-recursively (get-system-property "QPID_WORK") true)
  
  (let [options (new BrokerOptions)
        broker (new Broker)]
    (try
      (.setInitialConfigurationLocation options (.toExternalForm (clojure.java.io/resource "config.json")))
      (.startup broker options)
      (create-queue)
      
      (with-open [connection (.createConnection connection-factory)]
        (.start connection)
        
        (time
        (doseq [producer-future (doall (take 5 (repeatedly
                                                 (fn [] (future (produce connection))))))]
          (println "Summary:"
                   (count @producer-future)
                   "producers produced"
                   (count (flatten @producer-future)) "messages"))))
      
      (finally
        (delete-queue)
        (println "About to shut down broker")
        (.shutdown broker)))))
