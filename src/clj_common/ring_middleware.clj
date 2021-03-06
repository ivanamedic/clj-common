(ns clj-common.ring-middleware
  (:require
    ring.util.request
    ring.middleware.params
    ring.middleware.keyword-params
    ring.middleware.json
    ring.middleware.file
    [incanter.charts :as incanter-charts]
    [incanter.core :as incanter-core]
    [clj-common.path :as path]
    [clj-common.2d :as draw]
    [clj-common.io :as io]
    [clj-common.json :as json]
    [clj-common.logging :as logging]))

(defn wrap-only-query-params [handler]
  (fn [request]
    (let [encoding (ring.util.request/character-encoding request)]
      (handler
        (ring.middleware.params/assoc-query-params request encoding)))))
(def wrap-only-query-params-middleware wrap-only-query-params)

(defn post-body-as-string [handler]
  (fn [request]
    (let [line (slurp (:body request))]
      (handler
        (assoc
          request
          :body line)))))
(def post-body-as-string-middleware post-body-as-string)

(defn post-body-as-json [handler]
  (fn [request]
    (let [line (slurp (:body request))]
      (handler
        (assoc
          request
          :body (json/read-keyworded line))))))
(def post-body-as-json-middleware post-body-as-json)

(defn static-file
  "Serves static files from root-path by looking rest of uri when prefix is removed,
  useful when same server will serve both api and web, /api/stats and /web/stats"
  [prefix root-path]
  (fn [request]
    (if-let [response (ring.middleware.file/file-request
                        (assoc
                          request
                          :uri
                          (.substring (:uri request) (.length prefix)))
                        (path/path->string root-path))]
      response
      {:status 404})))
(def static-file-middleware static-file)

(defn wrap-exception-to-logging [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (logging/report-throwable (select-keys request [:uri]) t)
        {:status 500}))))


(defn expose-variable []
  (ring.middleware.json/wrap-json-response
    (ring.middleware.params/wrap-params
      (ring.middleware.keyword-params/wrap-keyword-params
        (fn [request]
          (let [namespace (or (:namespace (:params request)) "user")
                name (:name (:params request))]
            (if-let [var (ns-resolve (symbol namespace) (symbol name))]
              {
                :status 200
                :body (deref var)}
              {
                :status 404})))))))

(defn expose-image []
  (ring.middleware.params/wrap-params
    (ring.middleware.keyword-params/wrap-keyword-params
      (fn [request]
        (let [namespace (or (:namespace (:params request)) "user")
              name (:name (:params request))]
          (if-let [var (ns-resolve (symbol namespace) (symbol name))]
            {
              :status 200
              :headers {
                         "Content-Type" "image/png"}
              :body (let [buffer-output-stream (io/buffer-output-stream)]
                      (draw/write-png-to-stream
                        (deref var)
                        buffer-output-stream)
                      (io/buffer-output-stream->input-stream buffer-output-stream))}
            {
              :status 404}))))))

(defn expose-plot []
  (ring.middleware.params/wrap-params
    (ring.middleware.keyword-params/wrap-keyword-params
      (fn [request]
        (println request)
        (let [namespace (or (:namespace (:params request)) "user")
              x-axis-var (:x-axis (:params request))
              y-axis-var (:y-axis (:params request))]
          (if (and (some? namespace) (some? x-axis-var) (some? y-axis-var))
            (if-let [x-axis (ns-resolve (symbol namespace) (symbol x-axis-var))]
              (if-let [y-axis (ns-resolve (symbol namespace) (symbol y-axis-var))]
                {

                  :body (let [buffer-output-stream (io/buffer-output-stream)]
                          (incanter-core/save
                            (incanter-charts/xy-plot (deref x-axis) (deref y-axis))
                            buffer-output-stream)
                          (io/buffer-output-stream->input-stream buffer-output-stream))}
                {
                  :status 404})
              {
                :status 404})
            {
              :status 404}))))))

(comment
  (require 'clj-common.http-server)

  (clj-common.http-server/create-server 7077 (fn [request] {:body "Hello" :status 200}))
  (clj-common.http-server/create-server 7077 (expose-plot))

  (clj-common.http-server/create-server 7077 (clj-common.ring-middleware/expose-plot))

  (require 'clj-common.localfs)
  (require 'clj-common.io)

  (with-open [output-stream (clj-common.localfs/output-stream ["tmp" "chart1"])]
    (clj-common.io/copy-input-to-output-stream
      (:body ((expose-plot) {:params {:namespace "user" :x-axis "x" :y-axis "y"}}))
      output-stream))


  ((expose-plot) {:params {:namespace "user" :x-axis "x" :y-axis "y"}}))

(comment
  (require 'clj-common.http-server)

  (clj-common.http-server/create-server
    7078
    (compojure.core/GET
      "/image"
      _
      (expose-image)))
  ; http://localhost:7078/image?name=image
  ; in user ns do
  (in-ns 'user)
  (def image (clj-common.2d/create-image-context 300 300))
  (clj-common.2d/write-background image clj-common.2d/color-green)
  (clj-common.http-server/stop-server 7078)


  (require 'clj-common.http-server)
  (clj-common.http-server/create-server
    7078
    (compojure.core/GET
      "/variable"
      _
      (expose-variable))))
