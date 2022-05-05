import java.io.* ;
import java.net.* ;
import java.util.* ;


/*
    Every time a client opens a connection, the server creates a job which must process this request and sends
    it to the ThreadPool. HttpRequests will parse the output and answer the client.
    It must be able to keep the connection alive.
    Adapted from : https://www2.seas.gwu.edu/~cheng/6431/Projects/Project1WebServer/webserver.html

 */

public class HttpRequests implements Runnable{
    private final static String CRLF = "\r\n";                          // According to the HTTP specification, every line is terminated by this
    private final static int TIMEOUT = 5000;                            // Time out every 5s
    private final static int MAX = 5;                                   // Max number of requests


    private Socket socket;                                              // Socket connected to the client
    private Map<String, String> headers = new HashMap<>();              // HTTP headers / Only keep-alive is relevant
    private String version = "1.0";                                     // Default 1.0
    private HashMap<String, byte[]> RESOURCE_MAP;                       // List of resources
    private int cpt = 0;                                                // Current number of requests

    BufferedReader br = null;                                           // Input reader
    DataOutputStream os = null;                                         // Output writer

    public HttpRequests(Socket socket, HashMap<String, byte[]> resource_map) throws ExceptionInInitializerError, IOException{
        /*
            Constructor of HttpRequests
            Params :
                - socket : TCP socket retrieved from ServerSocket.accept()
                - resource_map : Map of (filename => file_content)

         */
        this.socket = socket;                                           // Socket
        if ((this.socket == null) || (this.socket.isClosed())){
            System.err.println("A null or closed socket was provided");
            throw new ExceptionInInitializerError("Bruh");
        }
        socket.setSoTimeout(TIMEOUT);
        RESOURCE_MAP = resource_map;
        // Opening BufferedReader and DataOutputStream
        try {
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            os = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e){
            System.err.println("An error occured when initializing the input and output streams");
            closeStreams();
            throw e;
        }
    }

    public void run() throws ExceptionInInitializerError{
        /*
            Processes every request for a given client

         */
        if (socket != null || socket.isClosed()){

            boolean keep_alive = true;                                  // default in HTTP/1.1 is keep-alive

            do {
                String error_message = "";
                String PATH_URI = "";

                try {
                    final String URI = readAndParse();                  // Read and parse the data
                    keep_alive = keep_alive && !URI.isEmpty();          // if URI is empty, the socket must be closed
                    PATH_URI = getPathUri(URI);                         // Find the resource path
                }
                catch (SocketTimeoutException e){
                    keep_alive = false;                                 // close the connection
                }
                catch (IOException e){
                    keep_alive = false;                                 // close the connection
                }
                catch (Exception e) {
                    // Handles bad requests and answers the client
                    error_message = e.getMessage();
                }


                if (keep_alive) {
                    // If there is no timeout, send the message
                    try {
                        sendResponse(PATH_URI, error_message);
                    } catch (IOException e){
                        keep_alive = false;
                    }
                    this.cpt++;
                    // if keep-alive, loop
                    keep_alive = keep_alive && keepConnection();
                }
            } while(keep_alive);

            closeStreams();

        } else {
            throw new ExceptionInInitializerError("No socket");
        }
    }

    private void closeStreams(){
        System.out.println("Closing client socket");
        // Closing InputStream, OutputStream and Socket
        try {
            br.close();
        } catch (IOException e) {
            System.err.println("An error occured while trying to close the input stream");
        }
        try {
            os.close();
        } catch (IOException e) {
            System.err.println("An error occured while trying to close the output stream");
        }
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("An error occured while trying to close the socket");
        }
    }

    private String readAndParse() throws Exception{
        /*
            Reads the InputStream of the socket.
            Sets the HTTP version
            Gets the URI and the headers and loads them in the variable `headers`
            in the instance variables.

            returns :
                - URI
         */


        /* ---- Parse the requestLine ---- */

        String request_line = "";
        String header_line = "";

        try {
            request_line = br.readLine();


            socket.setSoTimeout(TIMEOUT);

            System.out.println(request_line);

            // This server supports only GET requests
            if (!request_line.startsWith("GET")) {
                throw new Exception("Request must start with GET : " + request_line);
            }
            // GET / HTTP/1.0 -> ["GET", "/", "HTTP/1.0]
            String[] fields = request_line.split(" ");
            if (fields.length != 3) {
                throw new Exception("Incorrect request :" + request_line);
            }

            // Verify that the protocol is HTTP and version is "1.0" or "1.1"
            String[] protocol_and_version = fields[2].split("/");
            if (protocol_and_version.length != 2) {
                throw new Exception("Wrong Protocol or Version :" + protocol_and_version);
            }

            final String protocol = protocol_and_version[0];
            version = protocol_and_version[1];

            if (!protocol.equals("HTTP")) {
                throw new Exception("Protocol not supported :" + protocol);
            }
            if (!(version.equals("1.0") || version.equals("1.1"))) {
                throw new Exception("Version not supported :" + version);
            }

            /* ---- Parse the headers ---- */


            while ((header_line = br.readLine()).length() != 0) {
                String[] header_fields = header_line.split(": ");
                if (header_fields.length < 2) {
                    throw new Exception("Incorrect header " + header_line);
                }
                // Joins all the elements except for the first one. Could be optimized with a custom join function that starts from index 1.
                headers.put(header_fields[0], String.join(" ", Arrays.copyOfRange(header_fields, 1, header_fields.length)).toLowerCase(Locale.ROOT));
            }
            return fields[1];

        } catch (SocketTimeoutException e){
            // Not really an exceptional behavior as it happens when a client is too slow or when a thread must be stopped
            throw e;
        } catch (IOException e){
            System.err.println("Impossible to read from the socket" + e);
            throw e;
        }
    }

    private boolean keepConnection(){
        /*
            Returns true if we can close the socket.
         */

        // Check the number of requests
        if (cpt >= MAX){
            return false;
        }

        // Check the keep alive header
        if (headers.containsKey("Connection")){
            return headers.get("Connection").contains("keep-alive");
        }

        // Default behavior of HTTP 1.1 is keep-alive
        return version.equals("1.1");
    }

    private String getPathUri(String uri){
        /*
            Finds the resource if it exists in "../pages/". By default returns index.html.
            If no match is found, it returns an empty string.
         */
        if (uri.equals("/")){
            return "./pages/index.html";
        }
        else {
            String uri_path = "./pages" + uri;
            if (RESOURCE_MAP.containsKey(uri_path)){
                return uri_path;
            }
        }
        return "";
    }

    private void sendResponse(String path_uri, String error_message) throws IOException{

        // Construct the response message.
        StringBuilder status_line = new StringBuilder("HTTP/");
        status_line.append(version);
        StringBuilder content_type_line = new StringBuilder("Content-type: text/html");
        content_type_line.append(CRLF);

        StringBuilder content_length_line = new StringBuilder("Content-length: ");

        StringBuilder entity_body = new StringBuilder("<html><head><title>");

        if (error_message.isEmpty()) {
            // If there is no error message and the path uri is defined then we send the resource
            if (!path_uri.isEmpty()) {
                status_line.append(" 200 OK");
                status_line.append(CRLF);

                content_length_line.append(RESOURCE_MAP.get(path_uri).length);
                content_length_line.append(CRLF);

            } else {
                // If there is no error message and no path uri, the resource was not found
                status_line.append(" 404 Not Found");
                status_line.append(CRLF);

                entity_body.append("Not Found</title></head><body>Not Found</body></html>");

                content_length_line.append(entity_body.length());
                content_length_line.append(CRLF);
            }

        } else {
            // If there is an error message, answer bad request
            status_line.append(" 400 Bad Request");
            status_line.append(CRLF);

            entity_body.append("Bad Request</title></head><body>");
            entity_body.append(error_message);
            entity_body.append("</body></html>");

            content_length_line.append(entity_body.length());
            content_length_line.append(CRLF);
        }


        try {
            /* ---- Send headers ---- */

            os.writeBytes(status_line.toString());
            os.writeBytes(content_type_line.toString());
            os.writeBytes(content_length_line.toString());

            if (headers.containsKey("Connection")) {
                if (headers.get("Connection").contains("keep-alive")) {
                    os.writeBytes("Connection: Keep-Alive" + CRLF);
                    os.writeBytes("Keep-Alive: timeout=" + TIMEOUT + ", max=" + MAX + CRLF);
                }
            } else {
                if (version.equals("1.1")) {
                    os.writeBytes("Connection: Keep-Alive" + CRLF);
                    os.writeBytes("Keep-Alive: timeout=" + TIMEOUT + ", max=" + MAX + CRLF);
                }
            }
            os.writeBytes(CRLF); // Empty line

            /* ---- Send body ---- */

            if (entity_body.length() < 20) {
                os.write(RESOURCE_MAP.get(path_uri));
            } else {
                os.writeBytes(entity_body.toString());
            }

        } catch (IOException e){
            System.err.println("An error occured while sending the answer");
            throw e;
        }

    }

}
