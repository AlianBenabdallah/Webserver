import java.io.* ;
import java.net.* ;
import java.nio.file.Files;
import java.util.* ;
import java.nio.file.Paths;
import java.util.concurrent.* ;



public class WebServer {
    public static final int NB_THREADS = 10;

    private static void loadFiles(String path, HashMap<String, byte[]> map){
        /*
            Finds files within a given directory (and optionally its subdirectories) which match an array of extensions.
            Loads them into a map (path -> content)
            Adapted from https://stackoverflow.com/a/30209664/17052562.
         */

        File f = new File(path);
        if (f.isDirectory()){
            for (String child : f.list()){
                loadFiles(path + "/" + child, map);                                              // File.separator is '\' on Windows thus we need '/'
            }
        } else {
            System.out.println("File :");
            System.out.println(path);
            try {
                map.put(path, Files.readAllBytes(Paths.get(path)));
            }
            catch (Exception e){
                System.err.println("Unable to read " + path);
            }
        }
    }

    public static void main(String argv []) throws Exception{
        final int port = 8080;                                                                           // Listening port
        final long SHUTDOWN_TIME = 6000;                                                                 // Shutdown after 6s
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NB_THREADS);     // Thread Pool
        ServerSocket serverSocket = new ServerSocket(port);
        HashMap<String, byte[]> resource_map = new HashMap<String, byte[]>();                            // Every file that the server can send
        loadFiles("../pages", resource_map);                                                         // Loading those files
        boolean running = true;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            // Adapted from https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
            public void run() {
                // Taken from https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
                executor.shutdown();
                try {
                    System.out.println("Closing server socket");
                    serverSocket.close();
                } catch (Exception e){}

                try {
                    System.out.println("Closing threads and shutting down connections.");
                    if (!executor.awaitTermination(SHUTDOWN_TIME, TimeUnit.MILLISECONDS)) {
                        System.err.println("Executor did not terminate in the specified time.");
                        List<Runnable> droppedTasks = executor.shutdownNow();
                        System.err.println("Executor was abruptly shut down. " + droppedTasks.size() + " tasks will not be executed.");
                    }
                } catch (Exception e){
                    System.err.println("Current thread is also interrupted");
                    executor.shutdownNow();
                }
            }
        });

        while(true){
            try {
                Socket socket = serverSocket.accept();
                System.out.println("Connection accepted");
                HttpRequests request = new HttpRequests(socket, resource_map);
                executor.execute(request);
            } catch (SocketException e){
                // Shutting down the application closes the socket
            }
        }

    }
}
