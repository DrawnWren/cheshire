(ns cheshire.test.core
  (:use [clojure.test]
        [clojure.java.io :only [file reader]])
  (:require [cheshire.core :as json]
            [cheshire.generate :as gen]
            [cheshire.factory :as fact]
            [cheshire.parse :as parse])
  (:import (com.fasterxml.jackson.core JsonGenerationException)
           (java.io FileInputStream StringReader StringWriter
                    BufferedReader BufferedWriter)
           (java.sql Timestamp)
           (java.util Date UUID)))

(def test-obj {"int" 3 "long" (long -2147483647) "boolean" true
               "LongObj" (Long/parseLong "2147483647") "double" 1.23
               "nil" nil "string" "string" "vec" [1 2 3] "map" {"a" "b"}
               "list" (list "a" "b") "short" (short 21) "byte" (byte 3)})

(deftest t-ratio
  (let [n 1/2]
    (is (= (double n) (:num (json/decode (json/encode {:num n}) true))))))

(deftest t-long-wrap-around
  (is (= 2147483648 (json/decode (json/encode 2147483648)))))

(deftest t-bigint
  (let [n 9223372036854775808]
    (is (= n (:num (json/decode (json/encode {:num n}) true))))))

(deftest t-biginteger
  (let [n (BigInteger. "42")]
    (is (= n (:num (json/decode (json/encode {:num n}) true))))))

(deftest t-bigdecimal
  (let [n (BigDecimal. "42.5")]
    (is (= (.doubleValue n) (:num (json/decode (json/encode {:num n}) true))))
    (binding [parse/*use-bigdecimals?* true]
      (is (= n (:num (json/decode (json/encode {:num n}) true)))))))

(deftest test-string-round-trip
  (is (= test-obj (json/decode (json/encode test-obj)))))

(deftest test-generate-accepts-float
  (is (= "3.14" (json/encode 3.14))))

(deftest test-keyword-encode
  (is (= {"key" "val"}
         (json/decode (json/encode {:key "val"})))))

(deftest test-generate-set
  (is (= {"set" ["a" "b"]}
         (json/decode (json/encode {"set" #{"a" "b"}})))))

(deftest test-generate-empty-set
  (is (= {"set" []}
         (json/decode (json/encode {"set" #{}})))))

(deftest test-generate-empty-array
  (is (= {"array" []}
         (json/decode (json/encode {"array" []})))))

(deftest test-key-coercion
  (is (= {"foo" "bar" "1" "bat" "2" "bang" "3" "biz"}
         (json/decode
          (json/encode
           {:foo "bar" 1 "bat" (long 2) "bang" (bigint 3) "biz"})))))

(deftest test-keywords
  (is (= {:foo "bar" :bat 1}
         (json/decode (json/encode {:foo "bar" :bat 1}) true))))

(deftest test-symbols
  (is (= {"foo" "clojure.core/map"}
         (json/decode (json/encode {"foo" 'clojure.core/map})))))

(deftest test-accepts-java-map
  (is (= {"foo" 1}
         (json/decode
          (json/encode (doto (java.util.HashMap.) (.put "foo" 1)))))))

(deftest test-accepts-java-list
  (is (= [1 2 3]
         (json/decode (json/encode (doto (java.util.ArrayList. 3)
                                     (.add 1)
                                     (.add 2)
                                     (.add 3)))))))

(deftest test-accepts-java-set
  (is (= {"set" [1 2 3]}
         (json/decode (json/encode {"set" (doto (java.util.HashSet. 3)
                                            (.add 1)
                                            (.add 2)
                                            (.add 3))})))))

(deftest test-accepts-empty-java-set
  (is (= {"set" []}
         (json/decode (json/encode {"set" (java.util.HashSet. 3)})))))

(deftest test-nil
  (is (nil? (json/decode nil true))))

(deftest test-parsed-seq
  (let [br (BufferedReader. (StringReader. "1\n2\n3\n"))]
    (is (= (list 1 2 3) (json/parsed-seq br)))))

(deftest test-smile-round-trip
  (is (= test-obj (json/parse-smile (json/generate-smile test-obj)))))

(def bin-obj {"byte-array" (byte-array (map byte [1 2 3]))})

(deftest test-round-trip-binary
  (for [[p g] {json/parse-string json/generate-string
               json/parse-smile  json/generate-smile
               json/parse-cbor   json/generate-cbor}]
    (is (let [roundtripped (p (g bin-obj))]
          ;; test value equality
          (= (->> bin-obj (get "byte-array") seq)
             (->> roundtripped (get "byte-array") seq))))))

(deftest test-aliases
  (is (= {"foo" "bar" "1" "bat" "2" "bang" "3" "biz"}
         (json/decode
          (json/encode
           {:foo "bar" 1 "bat" (long 2) "bang" (bigint 3) "biz"})))))

(deftest test-date
  (is (= {"foo" "1970-01-01T00:00:00Z"}
         (json/decode (json/encode {:foo (Date. (long 0))}))))
  (is (= {"foo" "1970-01-01"}
         (json/decode (json/encode {:foo (Date. (long 0))}
                                   {:date-format "yyyy-MM-dd"})))
      "encode with given date format"))

(deftest test-sql-timestamp
  (is (= {"foo" "1970-01-01T00:00:00Z"}
         (json/decode (json/encode {:foo (Timestamp. (long 0))}))))
  (is (= {"foo" "1970-01-01"}
         (json/decode (json/encode {:foo (Timestamp. (long 0))}
                                   {:date-format "yyyy-MM-dd"})))
      "encode with given date format"))

(deftest test-uuid
  (let [id (UUID/randomUUID)
        id-str (str id)]
    (is (= {"foo" id-str} (json/decode (json/encode {:foo id}))))))

(deftest test-char-literal
  (is (= "{\"foo\":\"a\"}" (json/encode {:foo \a}))))

(deftest test-streams
  (is (= {"foo" "bar"}
         (json/parse-stream
          (BufferedReader. (StringReader. "{\"foo\":\"bar\"}\n")))))
  (let [sw (StringWriter.)
        bw (BufferedWriter. sw)]
    (json/generate-stream {"foo" "bar"} bw)
    (is (= "{\"foo\":\"bar\"}" (.toString sw))))
  (is (= {(keyword "foo baz") "bar"}
         (with-open [rdr (StringReader. "{\"foo baz\":\"bar\"}\n")]
           (json/parse-stream rdr true)))))

(deftest serial-writing
  (is (= "[\"foo\",\"bar\"]"
         (.toString
          (json/with-writer [(StringWriter.) nil]
            (json/write [] :start)
            (json/write "foo")
            (json/write "bar")
            (json/write [] :end)))))
  (is (= "[1,[2,3],4]"
         (.toString
          (json/with-writer [(StringWriter.) nil]
            (json/write [1 [2]] :start-inner)
            (json/write 3)
            (json/write [] :end)
            (json/write 4)
            (json/write [] :end)))))
  (is (= "{\"a\":1,\"b\":2,\"c\":3}"
         (.toString
          (json/with-writer [(StringWriter.) nil]
            (json/write {:a 1} :start)
            (json/write {:b 2} :bare)
            (json/write {:c 3} :end)))))
  (is (= (str "[\"start\",\"continue\",[\"implicitly-nested\"],"
              "[\"explicitly-nested\"],\"flatten\",\"end\"]")
         (.toString
          (json/with-writer [(StringWriter.) nil]
            (json/write ["start"] :start)
            (json/write "continue")
            (json/write ["implicitly-nested"])
            (json/write ["explicitly-nested"] :all)
            (json/write ["flatten"] :bare)
            (json/write ["end"] :end)))))
  (is (= "{\"head\":\"head info\",\"data\":[1,2,3],\"tail\":\"tail info\"}"
         (.toString
          (json/with-writer [(StringWriter.) nil]
            (json/write {:head "head info" :data []} :start-inner)
            (json/write 1)
            (json/write 2)
            (json/write 3)
            (json/write [] :end)
            (json/write {:tail "tail info"} :end))))))

(deftest test-multiple-objs-in-file
  (is (= {"one" 1, "foo" "bar"}
         (first (json/parsed-seq (reader "test/multi.json")))))
  (is (= {"two" 2, "foo" "bar"}
         (second (json/parsed-seq (reader "test/multi.json")))))
  (with-open [s (FileInputStream. (file "test/multi.json"))]
    (let [r (reader s)]
      (is (= [{"one" 1, "foo" "bar"} {"two" 2, "foo" "bar"}]
             (json/parsed-seq r))))))

(deftest test-jsondotorg-pass1
  (let [string (slurp "test/pass1.json")
        decoded-json (json/decode string)
        encoded-json (json/encode decoded-json)
        re-decoded-json (json/decode encoded-json)]
    (is (= decoded-json re-decoded-json))))

(deftest test-namespaced-keywords
  (is (= "{\"foo\":\"user/bar\"}"
         (json/encode {:foo :user/bar})))
  (is (= {:foo/bar "baz/eggplant"}
         (json/decode (json/encode {:foo/bar :baz/eggplant}) true))))

(deftest test-array-coerce-fn
  (is (= {"set" #{"a" "b"} "array" ["a" "b"] "map" {"a" 1}}
         (json/decode
          (json/encode {"set" #{"a" "b"} "array" ["a" "b"] "map" {"a" 1}}) false
          (fn [field-name] (if (= "set" field-name) #{} []))))))

(deftest t-symbol-encoding-for-non-resolvable-symbols
  (is (= "{\"bar\":\"clojure.core/pam\",\"foo\":\"clojure.core/map\"}"
         (json/encode (sorted-map :foo 'clojure.core/map :bar 'clojure.core/pam))))
  (is (= "{\"bar\":\"clojure.core/pam\",\"foo\":\"foo.bar/baz\"}"
         (json/encode (sorted-map :foo 'foo.bar/baz :bar 'clojure.core/pam)))))

(deftest t-bindable-factories
  (binding [fact/*json-factory* (fact/make-json-factory
                                 {:allow-non-numeric-numbers true})]
    (is (= (type Double/NaN)
           (type (:foo (json/decode "{\"foo\":NaN}" true)))))))

(deftest t-persistent-queue
  (let [q (conj (clojure.lang.PersistentQueue/EMPTY) 1 2 3)]
    (is (= q (json/decode (json/encode q))))))

(deftest t-pretty-print
  (is (= (str "{\n  \"bar\" : [ {\n    \"baz\" : 2\n  }, "
              "\"quux\", [ 1, 2, 3 ] ],\n  \"foo\" : 1\n}")
         (json/encode (sorted-map :foo 1 :bar [{:baz 2} :quux [1 2 3]])
                      {:pretty true}))))

(deftest t-unicode-escaping
  (is (= "{\"foo\":\"It costs \\u00A3100\"}"
         (json/encode {:foo "It costs £100"} {:escape-non-ascii true}))))

(deftest t-custom-keyword-fn
  (is (= {:FOO "bar"} (json/decode "{\"foo\": \"bar\"}"
                                   (fn [k] (keyword (.toUpperCase k))))))
  (is (= {"foo" "bar"} (json/decode "{\"foo\": \"bar\"}" nil)))
  (is (= {"foo" "bar"} (json/decode "{\"foo\": \"bar\"}" false)))
  (is (= {:foo "bar"} (json/decode "{\"foo\": \"bar\"}" true))))

(deftest t-custom-encode-key-fn
  (is (= "{\"FOO\":\"bar\"}"
         (json/encode {:foo :bar}
                      {:key-fn (fn [k] (.toUpperCase (name k)))}))))

(deftest test-add-remove-encoder
  (gen/remove-encoder java.net.URL)
  (gen/add-encoder java.net.URL gen/encode-str)
  (is (= "\"http://foo.com\""
         (json/encode (java.net.URL. "http://foo.com"))))
  (gen/remove-encoder java.net.URL)
  (is (thrown? JsonGenerationException
               (json/encode (java.net.URL. "http://foo.com")))))

(defprotocol TestP
  (foo [this] "foo method"))

(defrecord TestR [state])

(extend TestR
  TestP
  {:foo (constantly "bar")})

(deftest t-custom-protocol-encoder
  (let [rec (TestR. :quux)]
    (is (= {:state "quux"} (json/decode (json/encode rec) true)))
    (gen/add-encoder cheshire.test.core.TestR
                     (fn [obj jg]
                       (.writeString jg (foo obj))))
    (is (= "bar" (json/decode (json/encode rec))))
    (gen/remove-encoder cheshire.test.core.TestR)
    (is (= {:state "quux"} (json/decode (json/encode rec) true)))))

(defprotocol CTestP
  (thing [this] "thing method"))
(defrecord CTestR [state])
(extend CTestR
  CTestP
  {:thing (constantly "thing")})

(deftest t-custom-helpers
  (let [thing (CTestR. :state)
        remove #(gen/remove-encoder CTestR)]
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-nil nil jg)))
    (is (= nil (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-str "foo" jg)))
    (is (= "foo" (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-number 5 jg)))
    (is (= 5 (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-long 4 jg)))
    (is (= 4 (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-int 3 jg)))
    (is (= 3 (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-ratio 1/2 jg)))
    (is (= 0.5 (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-seq [:foo :bar] jg)))
    (is (= ["foo" "bar"] (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-date (Date. (long 0)) jg)))
    (binding [gen/*date-format* "yyyy-MM-dd'T'HH:mm:ss'Z'"]
      (is (= "1970-01-01T00:00:00Z" (json/decode (json/encode thing) true))))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-bool true jg)))
    (is (= true (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-named :foo jg)))
    (is (= "foo" (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-map {:foo "bar"} jg)))
    (is (= {:foo "bar"} (json/decode (json/encode thing) true)))
    (remove)
    (gen/add-encoder CTestR (fn [obj jg] (gen/encode-symbol 'foo jg)))
    (is (= "foo" (json/decode (json/encode thing) true)))
    (remove)))
