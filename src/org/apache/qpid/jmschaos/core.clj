(ns org.apache.qpid.jmschaos.core
  (:import 
    (org.apache.qpid.server Broker BrokerOptions)
    (org.apache.qpid.client AMQConnectionFactory)
    (javax.jms Connection ConnectionFactory Session Message))
  
  (:require [clj-http.client :as client]
            [clojure.contrib.java-utils :as java-utils])
  
  (:use clojure.tools.logging))


(def duration 10000)

(def broker (new Broker))

(def connection-factory (new AMQConnectionFactory "amqp://guest:guest@clientid/?brokerlist='tcp://localhost:5672'"))
  
(def queue-name "chaos-queue")

(def queue-url (format "http://localhost:8080/rest/queue/default/%s" queue-name))
(def binding-url (format "http://localhost:8080/rest/binding/default/amq.direct/%1$s/%1$s" queue-name))


(defn create-queue []
  (info "About to create queue")
  (client/put queue-url {:body "{\"durable\":true}"} )
  (client/put binding-url {:body "{}"} ))

(defn delete-queue []
  (client/delete binding-url)
  (client/delete queue-url)
  (info "Deleted queue"))

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


(defn produce-message
  "Produce one message and return its JMSMessageID"
  [session producer]
  (let [message (.createTextMessage session "message body")]
    (.send producer message)
    (.getJMSMessageID message)))


(defn produce [connection]
  "Creates a session, produces some messages, then closes the session"
  
  (Thread/sleep (rand-int (/ duration 10)))
  (with-open [session (.createSession connection false Session/AUTO_ACKNOWLEDGE)]
    (do-for-at-most
      duration 
      (fn [] 
        (with-open [producer (.createProducer session (.createQueue session queue-name))]
          (do-for-at-most
            (/ duration 3)
            (fn [] (produce-message session producer))))))))

(defn consume [connection]
  "Creates a session, consumes some messages, then closes the session (TODO reduced duplication)"
  
  (Thread/sleep (rand-int (/ duration 10)))
  (with-open [session (.createSession connection false Session/AUTO_ACKNOWLEDGE)]
    (do-for-at-most
      duration 
      (fn [] 
        (with-open [consumer (.createConsumer session (.createQueue session queue-name))]
          (do-for-at-most
            (/ duration 3)
            (fn [] (.getJMSMessageID (.receive consumer duration)))))))))



(defn -main [& args]
  (info "Starting...")
  (java-utils/delete-file-recursively (java-utils/get-system-property "QPID_WORK") true)
  
  (let [options (new BrokerOptions)
        broker (new Broker)]
    (try
      (.setInitialConfigurationLocation options (.toExternalForm (clojure.java.io/resource "config.json")))
      (.startup broker options)
      (create-queue)
      
      (with-open [connection (.createConnection connection-factory)]
        (.start connection)
        
        (let [producer-futures (doall (take 5 (repeatedly (fn [] (future (produce connection))))))
              consumer-futures (doall (take 5 (repeatedly (fn [] (future (consume connection))))))]
          (letfn [(get-messages
                    [futures]
                    ((comp sort flatten (partial map deref)) futures))]
            (info "Messages produced: " (get-messages producer-futures))
            (info "Messages consumed: " (get-messages consumer-futures)))))
      
      (finally
        (delete-queue)
        (info "About to shut down broker")
        (.shutdown broker)))))

(defn set-up-repl []
  (System/setProperty "QPID_WORK" "target/work")
  (System/setProperty "QPID_HOME" "qpd-home")
  (let [options (new BrokerOptions)]
    (.setInitialConfigurationLocation options (.toExternalForm (clojure.java.io/resource "config.json")))
    (.startup broker options)
    (create-queue)
    (def connection (.createConnection connection-factory))))
  