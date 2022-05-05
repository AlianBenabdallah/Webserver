# Webserver

A multi-threaded file-based web server with thread-pooling implemented in Java which supports Keep-Alive connections.

The server can deliver any `.html` file in the `../pages` directiory which is inside the folder when the server starts.

It can currently display pages :
- `http://localhost:8080/`
- `http://localhost:8080/index.html`
- `http://localhost:8080/foo/bar.html`

Adapted from : https://www2.seas.gwu.edu/~cheng/6431/Projects/Project1WebServer/webserver.html


## Classes

- **WebServer** : Reads all the files and loads them in a map (filename, filecontent). Launches the Thread Pool and the server socket (listening on port 8080). Accepts connections and submits HttpRequests to the pool.

- **HttpRequests** : Parses and answers the requests. Handles keep-alive connections up to 5 requests and times out after 5s.

## Installation
Installation requires sbt.

**Command** : `sbt package` 

## Usage
From the route directory : `java -cp ./target/classes/WebServer`

Shuts down with Ctrl + C

## TODO :

Add tests

## Status:
On hold
