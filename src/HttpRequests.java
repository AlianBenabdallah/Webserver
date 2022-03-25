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

    public HttpRequests(Socket socket, HashMap<String, byte[]> resource_map) throws Exception{
        /*
            Constructor
         */
        this.socket = socket;                                           // Socket
        socket.setSoTimeout(TIMEOUT);
        RESOURCE_MAP = resource_map;
        // Opening BufferedReader and DataOutputStream
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        os = new DataOutputStream(socket.getOutputStream());
    }

    public void run(){
        try {
            processRequest();
        } catch (Exception e) {
            System.out.println("Socket closed");
            System.out.println(e);
        }
    }

    private void processRequest() throws Exception{
        if (socket != null){
            boolean keep_alive = true;                                  // default in HTTP/1.1 is keep-alive
            while (keep_alive) {

                String error_message = "";
                String PATH_URI = "";

                try {
                    final String URI = readAndParse();                  // Read and parse the data
                    keep_alive = keep_alive && !URI.isEmpty();          // if URI is empty, the socket must be closed
                    PATH_URI = getPathUri(URI);                         // Find the resource path
                }
                catch (SocketTimeoutException e){
                    keep_alive = false;                                 // if time_out -> close the connection
                }
                catch (Exception e) {
                    error_message = e.getMessage();                     // Sets the error message as the answer
                }


                if (keep_alive) {
                    // If there is no timeout, send the message
                    try {
                        sendResponse(PATH_URI, error_message);
                    } catch (Exception e){
                        throw e;
                    }
                    this.cpt++;
                    // if keep-alive, loop
                    keep_alive = keep_alive && keepConnection();
                }

            }

            System.out.println("Closing socket");
            // Closing InputStream, OutputStream and Socket
            br.close();
            os.close();
            socket.close();
        } else {
            throw new Exception("No socket");
        }
    }

    private String readAndParse() throws Exception{
        /*
            Reads the InputStream of the socket. Set the HTTP version, gets the URI and the headers and load them
            in the instance variables.
         */


        /* ---- Parse the requestLine ---- */

        String request_line = "";

        try {
            request_line = br.readLine();

            if (request_line == null){
                return "";
            }

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

            String header_line = "";

            while ((header_line = br.readLine()).length() != 0) {
                String[] header_fields = header_line.split(": ");
                if (header_fields.length < 2) {
                    throw new Exception("Incorrect header " + header_line);
                }
                // Joins all the elements except for the first one. Could be optimized.
                headers.put(header_fields[0], String.join(" ", Arrays.copyOfRange(header_fields, 1, header_fields.length)).toLowerCase(Locale.ROOT));
            }
            return fields[1];
        } catch (SocketTimeoutException e){
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
            return "../pages/index.html";
        }
        else {
            String uri_path = "../pages" + uri;
            if (RESOURCE_MAP.containsKey(uri_path)){
                return uri_path;
            }
        }
        return "";
    }

    private void sendResponse(String path_uri, String error_message) throws Exception{

        // Construct the response message.

        String status_line = "";
        String content_type_line = "Content-type: text/html"+ CRLF;
        String content_length_line = "Content-length: ";
        String entity_body = "";

        boolean error = false;
        if (error_message.isEmpty()) {
            // If there is no error message and the path uri is defined then we send the resource
            if (!path_uri.isEmpty()) {
                status_line = "HTTP/" + version + " 200 OK" + CRLF;
                content_length_line = content_length_line + RESOURCE_MAP.get(path_uri).length + CRLF;

            } else {
                // If there is no error message and no path uri, the resource was not found
                status_line = "HTTP/"+version+" 404 Not Found" + CRLF;
                entity_body = "<HTML>" +
                        "<HEAD><TITLE>Not Found</TITLE></HEAD>" +
                        "<BODY>Not Found</BODY></HTML>";
                content_length_line = content_length_line + entity_body.length() + CRLF;
            }

        } else {
            // If there is an error message, answer bad request
            status_line = "HTTP/"+version+" 400 Bad Request" + CRLF;
            entity_body = "<HTML>" +
                    "<HEAD><TITLE>Bad Request</TITLE></HEAD>" +
                    "<BODY>" +
                        error_message +
                    "  </BODY></HTML>";
            content_length_line = content_length_line + entity_body.length() + CRLF;
        }


        try {
            /* ---- Send headers ---- */

            os.writeBytes(status_line);
            os.writeBytes(content_type_line);
            os.writeBytes(content_length_line);

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

            if (entity_body.isEmpty()) {
                os.write(RESOURCE_MAP.get(path_uri));
            } else {
                os.writeBytes(entity_body);
            }
        } catch (Exception e){
            throw e;
        }

    }

}
