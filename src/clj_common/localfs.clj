(ns clj-common.localfs
  (:require [clj-common.path :as path]))

(defn exists?
  "Checks if path exists on local fs"
  [path]
  (.exists (new java.io.File (path/path->string path))))

(defn mkdirs
  "Ensures given path exists, making all non existing dirs"
  [path]
  (.mkdirs (new java.io.File (path/path->string path))))

(defn move
  "Performs move of file"
  [src-path dest-path]
  (when-not (.renameTo
    (new java.io.File (path/path->string src-path))
    (new java.io.File (path/path->string dest-path)))
    (throw (ex-info
            "Unable to move file"
            {
             :source src-path
             :destination dest-path}))))

(defn delete
  "Removes file or directory"
  [path]
  (org.apache.commons.io.FileUtils/forceDelete
   (new java.io.File (path/path->string path))))

(defn input-stream
  "Creates input stream for given path"
  [path]
  (new java.io.FileInputStream (path/path->string path)))

(defn output-stream
  "Creates output stream for given path"
  [path]
  (mkdirs (path/parent path))
  (new java.io.FileOutputStream ^String (path/path->string path)))

(defn output-stream-by-appending
  "Creates output stream for given path by appending"
  [path]
  (new java.io.FileOutputStream (path/path->string path) true))

(defn is-directory
  "Checks if given path represents directory"
  [path]
  (let [path-string (path/path->string path)
        file-object (new java.io.File path-string)]
    (.isDirectory file-object)))

(defn relative-path->path [relative-path]
  (.getCanonicalPath (new java.io.File relative-path)))

(defn list
  "List paths on given path if directory, if file or doesn't exist empty list is returned"
  [path]
  (if
    (is-directory path)
    (map
      (partial path/child path)
      (.list (new java.io.File (path/path->string path))))
    '()))

